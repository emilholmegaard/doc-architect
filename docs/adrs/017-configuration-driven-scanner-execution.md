---
# Backstage TechDocs metadata
id: adr-017-configuration-driven-scanner-execution
title: ADR-017: Configuration-Driven Scanner Execution
description: Fix critical bug where CLI discovered scanners but executed 0 scanners due to missing configuration loading
tags:
  - adr
  - configuration
  - scanners
  - bug-fix
  - yaml
---
# ADR-017: Configuration-Driven Scanner Execution

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-26 |
| **Deciders** | Development Team |
| **Technical Story** | Issue #104 - CLI discovers scanners but executes 0 scanners |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

The CLI was discovering all available scanners via SPI (ServiceLoader) but executing **0 scanners**, resulting in no components, dependencies, APIs, or entities being extracted from real-world projects like Keycloak. This completely broke the tool's core functionality.

### Root Cause Analysis

Two critical issues were identified:

1. **Missing Configuration Loading**: The `ScanCommand` accepted a `--config` parameter pointing to `docarchitect.yaml` but never actually loaded or used the configuration file. The config file was completely ignored.

2. **Scanner ID Mismatch**: Configuration files used scanner IDs like `spring-mvc-api`, but the actual scanner implementation used `spring-rest-api`. This mismatch meant even if config were loaded, scanners wouldn't match.

### Example of the Problem

**Keycloak config** (`docarchitect.yaml`):
```yaml
scanners:
  enabled:
    - maven-dependencies     # ✓ Correct
    - spring-mvc-api         # ✗ Wrong (should be spring-rest-api)
    - jpa-entities           # ✓ Correct
```

**Actual Scanner IDs**:
- `MavenDependencyScanner.java`: `SCANNER_ID = "maven-dependencies"`
- `SpringRestApiScanner.java`: `SCANNER_ID = "spring-rest-api"` (not spring-mvc-api!)
- `JpaEntityScanner.java`: `SCANNER_ID = "jpa-entities"`

**Result**: Scanner discovered 19 scanners, but executed **0** because:
1. Config was never loaded, so no filtering happened
2. Even if loaded, `spring-mvc-api` wouldn't match `spring-rest-api`

## Decision

Implement configuration-driven scanner execution with the following components:

### 1. Configuration Model

Create immutable `ProjectConfig` record hierarchy for YAML config:

```java
// doc-architect-core/src/main/java/com/docarchitect/core/config/ProjectConfig.java
public record ProjectConfig(
    ProjectInfo project,
    List<RepositoryConfig> repositories,
    ScannerConfig scanners,
    GeneratorConfigSettings generators,
    OutputConfig output
) {
    public record ScannerConfig(
        List<String> enabled,
        Map<String, Object> config
    ) {
        // Empty list = all scanners enabled
        public boolean isEnabled(String scannerId) {
            return enabled == null || enabled.isEmpty() || enabled.contains(scannerId);
        }
    }
}
```

### 2. Configuration Loader

Create `ConfigLoader` utility using Jackson YAML:

```java
// doc-architect-core/src/main/java/com/docarchitect/core/config/ConfigLoader.java
public class ConfigLoader {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static ProjectConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            log.warn("Config not found: {}. Using defaults (all scanners enabled).", configPath);
            return ProjectConfig.defaults();
        }
        // Parse YAML or return defaults on error
    }
}
```

### 3. Scanner Filtering in ScanCommand

Update `ScanCommand.executeScanners()` to:

1. **Load config** before executing scanners
2. **Filter scanners** based on `scanners.enabled` list
3. **Validate config** and warn about unknown scanner IDs
4. **Log debug info** showing which scanners were skipped/executed
5. **Warn user** if 0 scanners execute

```java
private Map<String, ScanResult> executeScanners(List<Scanner> scanners, ProjectConfig config) {
    validateScannerConfig(scanners, config); // Warn about unknown IDs

    for (Scanner scanner : scanners) {
        // Check if scanner is enabled in config
        if (config.scanners() != null && !config.scanners().isEnabled(scanner.getId())) {
            log.debug("Scanner {} disabled in config", scanner.getId());
            continue;
        }

        if (scanner.appliesTo(context)) {
            // Execute scanner
        }
    }

    // Warn if 0 scanners executed
}
```

### 4. Scanner ID Corrections

Fix all scanner ID mismatches in config files:
- `spring-mvc-api` → `spring-rest-api`

### 5. Comprehensive Validation

```java
private void validateScannerConfig(List<Scanner> scanners, ProjectConfig config) {
    Set<String> availableIds = scanners.stream()
        .map(Scanner::getId)
        .collect(Collectors.toSet());

    List<String> unknownIds = config.scanners().enabled().stream()
        .filter(id -> !availableIds.contains(id))
        .toList();

    if (!unknownIds.isEmpty()) {
        System.err.println("⚠ WARNING: Unknown scanner IDs:");
        unknownIds.forEach(id -> System.err.println("    - " + id));
        System.err.println("  Available scanner IDs:");
        availableIds.forEach(id -> System.err.println("    - " + id));
    }
}
```

## Consequences

### Positive

✅ **Fixes Critical Bug** - Resolves issue #104, scanners now actually execute
✅ **User Control** - Users can enable/disable specific scanners via config
✅ **Clear Feedback** - Users get actionable warnings when config doesn't match available scanners
✅ **Performance** - Can skip irrelevant scanners (e.g., disable Python scanners for Java projects)
✅ **Debugging** - Debug logging shows exactly which scanners were considered and why
✅ **Defaults Work** - Empty or missing config enables all scanners (backward compatible)

### Negative

⚠️ **Config Required** - Users must use correct scanner IDs (but we validate and warn)
⚠️ **Breaking Change** - Old configs with `spring-mvc-api` need updating (but tool warns)

### Neutral

- **Additional Classes** - Added `ProjectConfig` and `ConfigLoader` (~200 LOC)
- **Test Coverage** - Added 21 comprehensive unit tests for config loading
- **Documentation** - Requires updating config examples and documentation

## Alternatives Considered

### Alternative 1: Auto-correct Scanner IDs
**Rejected** - Would hide misconfiguration and create unpredictable behavior. Better to warn users explicitly.

### Alternative 2: Execute All Scanners Regardless of Config
**Rejected** - Defeats purpose of configuration and wastes resources scanning irrelevant technologies.

### Alternative 3: Fail Fast on Unknown Scanner IDs
**Rejected** - Too strict; warning is better UX as it allows scan to continue with known scanners.

## Implementation Details

### Files Created

```
doc-architect-core/src/main/java/com/docarchitect/core/config/
├── ProjectConfig.java              # Config model (145 LOC)
└── ConfigLoader.java                # YAML loader (70 LOC)

doc-architect-core/src/test/java/com/docarchitect/core/config/
├── ProjectConfigTest.java           # 11 tests
└── ConfigLoaderTest.java            # 10 tests
```

### Files Modified

```
doc-architect-cli/src/main/java/com/docarchitect/cli/ScanCommand.java
  - Added loadConfiguration() method
  - Updated executeScanners() to accept and use ProjectConfig
  - Added validateScannerConfig() for validation
  - Added comprehensive warning messages

test-projects/keycloak/docarchitect.yaml
  - Fixed: spring-mvc-api → spring-rest-api

examples/test-java-keycloak.sh
  - Fixed: spring-mvc-api → spring-rest-api

test-projects/openhab-core/docarchitect.yaml
  - Fixed: spring-mvc-api → spring-rest-api
```

## Verification

### Test Results

| Test Type | Result |
|-----------|--------|
| Unit tests (680 total) | ✅ All passing |
| New config tests | ✅ 21 tests added |
| Maven compile | ✅ Success |
| Maven package | ✅ Success |
| Config class coverage | ✅ 100% |

### Verification Script

```bash
# Create a test config
cat > docarchitect.yaml << 'EOF'
project:
  name: "TestProject"
  version: "1.0.0"

scanners:
  enabled:
    - maven-dependencies
    - spring-rest-api
    - jpa-entities
EOF

# Run scan (should execute exactly 3 scanners)
java -jar doc-architect-cli/target/doc-architect-cli-1.0.0-SNAPSHOT.jar scan .

# Expected output:
# ✓ Discovered 19 scanners
# ✓ Executed 3 scanners
```

With wrong scanner ID:

```yaml
scanners:
  enabled:
    - spring-mvc-api  # Wrong ID
```

Expected warning:
```
⚠ WARNING: Unknown scanner IDs in configuration:
    - spring-mvc-api

  Available scanner IDs:
    - maven-dependencies
    - spring-rest-api
    ...
```

## Scanner ID Reference

For future config authoring, the correct scanner IDs are:

### Java
- `maven-dependencies` - Maven POM scanner
- `gradle-dependencies` - Gradle build scanner
- `spring-rest-api` - Spring MVC/REST scanner (NOT spring-mvc-api)
- `jpa-entities` - JPA entity scanner
- `kafka-messaging` - Kafka messaging scanner
- `jaxrs-api` - JAX-RS API scanner

### Python
- `pip-poetry-dependencies` - Python dependency scanner
- `fastapi-rest` - FastAPI scanner
- `flask-rest` - Flask scanner
- `django-orm` - Django ORM scanner
- `sqlalchemy-orm` - SQLAlchemy scanner

### .NET
- `nuget-dependencies` - NuGet dependency scanner
- `aspnetcore-rest` - ASP.NET Core API scanner
- `entity-framework` - Entity Framework scanner

### JavaScript
- `npm-dependencies` - NPM dependency scanner
- `express-api` - Express.js scanner

### Go
- `go-modules` - go.mod scanner

### Ruby
- `bundler-dependencies` - Bundler Gemfile scanner
- `rails-api` - Ruby on Rails API scanner

### Schema
- `graphql-schema` - GraphQL schema scanner
- `avro-schema` - Apache Avro schema scanner
- `sql-migration` - SQL migration scanner

## Related ADRs

- [ADR-002: CLI Framework (Picocli)](002-cli-framework-picocli.md) - Command structure
- [ADR-006: Technology-based Package Organization](006-technology-based-package-organization.md) - Scanner organization
- [ADR-016: Import-based Scanner Pre-filtering](016-import-based-scanner-pre-filtering.md) - Related optimization

## References

- [GitHub Issue #104](https://github.com/emilholmegaard/doc-architect/issues/104) - CLI discovers scanners but executes 0 scanners
- [Jackson YAML Documentation](https://github.com/FasterXML/jackson-dataformats-text)
- [Java ServiceLoader Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)
