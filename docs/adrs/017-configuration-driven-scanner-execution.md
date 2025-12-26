# ADR 017: Configuration-Driven Scanner Execution

**Status:** Accepted
**Date:** 2025-12-26
**Context:** Issue #104 - CLI discovers scanners but executes 0 scanners

## Context and Problem Statement

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

1. **Fixes Critical Bug**: Resolves issue #104 - scanners now actually execute
2. **User Control**: Users can enable/disable specific scanners via config
3. **Clear Feedback**: Users get actionable warnings when config doesn't match available scanners
4. **Performance**: Can skip irrelevant scanners (e.g., disable Python scanners for Java projects)
5. **Debugging**: Debug logging shows exactly which scanners were considered and why they were skipped
6. **Defaults Work**: Empty or missing config enables all scanners (backward compatible)

### Negative

1. **Config Required**: Users must use correct scanner IDs (but we validate and warn)
2. **Breaking Change**: Old configs with `spring-mvc-api` need updating (but tool warns)

### Neutral

1. **Additional Classes**: Added `ProjectConfig` and `ConfigLoader` (~200 LOC)
2. **Test Coverage**: Added 21 comprehensive unit tests for config loading
3. **Documentation**: Requires updating config examples and documentation

## Implementation

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

### Test Results

- **All 680 tests pass** (including 21 new config tests)
- **Build successful**: Maven compile and package succeed
- **Coverage**: Config classes have 100% test coverage

## Scanner ID Reference

For future config authoring, the correct scanner IDs are:

### Java
- `maven-dependencies` - Maven POM scanner
- `gradle-dependencies` - Gradle build scanner
- `spring-rest-api` - Spring MVC/REST scanner (NOT spring-mvc-api)
- `jpa-entities` - JPA entity scanner
- `kafka-messaging` - Kafka messaging scanner

### Python
- `pip-poetry-dependencies` - Python dependency scanner
- `fastapi-rest` - FastAPI scanner
- `flask-rest` - Flask scanner
- `django-orm` - Django ORM scanner

### .NET
- `nuget-dependencies` - NuGet dependency scanner
- `aspnetcore-rest` - ASP.NET Core API scanner
- `entity-framework` - Entity Framework scanner

### JavaScript
- `npm-dependencies` - NPM dependency scanner
- `express-api` - Express.js scanner

### Go
- `go-modules` - go.mod scanner

### Schema
- `graphql-schema` - GraphQL schema scanner
- `avro-schema` - Apache Avro schema scanner
- `sql-migration` - SQL migration scanner

## Related

- **Issue**: #104 - CLI discovers scanners but executes 0 scanners
- **ADR 002**: CLI Framework (Picocli) - Command structure
- **ADR 006**: Technology-based package organization - Scanner organization
- **ADR 016**: Import-based scanner pre-filtering - Related optimization

## Verification

To verify this works:

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
