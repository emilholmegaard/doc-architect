# ADR-0004: Logback Logging Configuration

**Status:** Accepted
**Date:** 2025-12-14
**Context:** Week 3 Architecture Refactoring

---

## Context

During Week 3 refactoring, we extracted `AbstractScanner` base classes that initialize SLF4J loggers for all scanner implementations. However, the project only had `slf4j-api` as a dependency with no concrete logging implementation, resulting in:

- SLF4J warnings during test execution: "No SLF4J providers were found"
- No actual logging output in production or tests
- No centralized way to control log levels across the application
- No file logging capability for debugging production issues

With 19 scanners now using SLF4J loggers, we needed a production-ready logging solution that supports:
- Configurable log levels (per package or globally)
- Different behavior for production vs. test environments
- Optional file logging for production debugging
- Minimal noise during test execution

---

## Decision

We have decided to adopt **Logback Classic** as the SLF4J implementation with dual configuration files:

### 1. Dependency Management

Add `logback-classic` as a runtime dependency in `doc-architect-core/pom.xml`:

```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <scope>runtime</scope>
</dependency>
```

**Rationale:** Runtime scope ensures Logback is available when the application runs but doesn't leak into compile-time dependencies.

### 2. Production Configuration (`src/main/resources/logback.xml`)

- **Default Level:** INFO (overridable via `LOGBACK_LEVEL` environment variable)
- **Console Appender:** Always enabled with timestamp format
- **File Appender:** Optional, enabled via `-Ddoc-architect.log.file=true`
- **Log Location:** `~/.doc-architect/logs/doc-architect.log`
- **Rolling Policy:** 7-day retention, 100MB total size cap

**Rationale:** INFO level provides useful feedback without overwhelming users. File logging is opt-in to avoid filesystem clutter.

### 3. Test Configuration (`src/test/resources/logback-test.xml`)

- **Default Level:** WARN (overridable via `LOGBACK_LEVEL` environment variable)
- **Console Appender:** Simple format without timestamps
- **Scanner Package:** ERROR level (only critical failures shown)
- **3rd Party Libraries:** ERROR level (Jackson, JavaParser silenced)

**Rationale:** Tests should be quiet by default. Developers can enable DEBUG logging when troubleshooting specific test failures.

### 4. Environment Variable Controls

Support fine-grained log level control via environment variables:
- `LOGBACK_LEVEL` - Global log level
- `SCANNER_LOG_LEVEL` - Scanner package log level
- `GENERATOR_LOG_LEVEL` - Generator package log level
- `RENDERER_LOG_LEVEL` - Renderer package log level

**Rationale:** Allows users to increase verbosity for specific subsystems without flooding logs from other components.

---

## Consequences

### Positive

✅ **Eliminates SLF4J Warnings**: No more "No SLF4J providers were found" in tests
✅ **Consistent Logging**: All scanners use same configuration and format
✅ **Environment-Specific Behavior**: Production logs are verbose, tests are quiet
✅ **Flexible Control**: Log levels adjustable via environment variables or system properties
✅ **Optional File Logging**: Production debugging without modifying configuration
✅ **Industry Standard**: Logback is widely used, well-documented, and actively maintained
✅ **Zero Code Changes**: Existing scanner code using SLF4J works immediately

### Negative

⚠️ **Additional Dependency**: Adds ~500KB to JAR size (logback-classic + logback-core)
⚠️ **Configuration Complexity**: Two separate XML files to maintain
⚠️ **Filesystem Usage**: File logging (when enabled) writes to user home directory

### Neutral

🔵 **Test Execution**: Logs are quieter by default, developers must enable DEBUG explicitly
🔵 **Production Default**: INFO level may be too verbose for some use cases (can override to WARN)

---

## Alternatives Considered

### Alternative 1: `slf4j-simple`

**Pros:**
- Minimal dependency (~10KB)
- No configuration files needed

**Cons:**
- Fixed log level (System.err only)
- No file logging support
- No runtime configurability
- Not suitable for production use

**Rejected:** Too limited for a production library.

### Alternative 2: Log4j2

**Pros:**
- High performance (async logging)
- Rich feature set
- Flexible configuration

**Cons:**
- Recent security vulnerabilities (Log4Shell)
- More complex configuration
- Larger dependency footprint

**Rejected:** Security concerns and unnecessary complexity for this project.

### Alternative 3: Java Util Logging (JUL)

**Pros:**
- Built into Java platform
- Zero dependencies

**Cons:**
- Poor performance
- Awkward configuration (properties files)
- Limited adoption in modern projects
- Would require refactoring all SLF4J usage

**Rejected:** Not compatible with existing SLF4J usage, poor developer experience.

---

## Implementation Details

### File Structure

```
doc-architect-core/
├── pom.xml (logback-classic dependency)
├── src/main/resources/
│   └── logback.xml (production config)
└── src/test/resources/
    └── logback-test.xml (test config)
```

### Usage Examples

```bash
# Production: Enable file logging
mvn package -Ddoc-architect.log.file=true

# Production: Set global log level
export LOGBACK_LEVEL=DEBUG
mvn package

# Production: Set scanner-specific log level
export SCANNER_LOG_LEVEL=TRACE
mvn package

# Tests: Enable debug logging for troubleshooting
mvn test -DLOGBACK_LEVEL=DEBUG

# Tests: Debug specific scanner
mvn test -DSCANNER_LOG_LEVEL=TRACE -Dtest=MavenDependencyScannerTest
```

### Log Output Examples

**Production (INFO level):**
```
14:32:15.123 [main] INFO  c.d.c.scanner.impl.MavenDependencyScanner - Scanning Maven dependencies in: /path/to/project
14:32:15.456 [main] INFO  c.d.c.scanner.impl.MavenDependencyScanner - Found 12 Maven dependencies across 3 POM files
```

**Tests (WARN level):**
```
(silent unless warnings/errors occur)
```

**Tests (DEBUG enabled):**
```
DEBUG c.d.c.scanner.impl.MavenDependencyScanner - Parsing POM file: pom.xml
DEBUG c.d.c.scanner.impl.MavenDependencyScanner - Extracted dependency: com.example:library:1.0
```

---

## Migration Path

This ADR was implemented during Week 3 refactoring without requiring changes to existing scanner code:

1. ✅ Added `logback-classic` dependency to POM
2. ✅ Created `logback.xml` (production config)
3. ✅ Created `logback-test.xml` (test config)
4. ✅ Documented configuration in `CLAUDE.md`
5. ✅ Verified all tests pass without SLF4J warnings

**No scanner code changes required** - all scanners using `AbstractScanner.log` automatically benefit.

---

## Future Considerations

### Potential Enhancements (Future)

- **Structured Logging:** Add JSON formatter for log aggregation tools (ELK, Splunk)
- **Async Appenders:** Use async logging for high-throughput scenarios
- **Log Correlation:** Add MDC (Mapped Diagnostic Context) for request tracing
- **Metrics Integration:** Export log event counts to Prometheus/Micrometer

### Monitoring (Future)

- Track ERROR-level log frequency in production
- Alert on repeated ERROR patterns (parsing failures, I/O errors)
- Aggregate scanner execution times via DEBUG logs

---

## References

- Logback Documentation: https://logback.qos.ch/manual/
- SLF4J Documentation: https://www.slf4j.org/manual.html
- Week 3 Refactoring Plan: `.github/ISSUE_TEMPLATE/refactoring-proposal.md`
- CLAUDE.md Logging Section: Lines 100-146

---

## Approval

- **Proposed by:** Claude Sonnet 4.5 (AI Assistant)
- **Reviewed by:** (Pending)
- **Approved by:** (Pending)
- **Implementation Date:** 2025-12-14
