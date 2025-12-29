---
status: accepted
date: 2025-12-29
decision-makers: System Architecture Team
---

# ADR 019: Build Performance Optimization

## Context

The doc-architect-core module build time was approximately 77 seconds for `mvn clean verify`, which impacts:
- Developer productivity during local development
- CI/CD pipeline efficiency
- Rapid iteration cycles during development

The baseline measurements (December 2025):
- Build time: 1:17 min (77 seconds)
- Test count: 998 tests
- Test execution time: ~25 seconds
- Quality gates: JaCoCo (65% coverage), Checkstyle, Javadoc, SpotBugs
- ANTLR grammars: 7 grammars compiled on every build

## Decision

We have implemented the following build optimizations:

### 1. ANTLR Grammar Compilation Optimization (Implemented)
**Impact**: Reduced ANTLR code generation overhead

Disabled unnecessary ANTLR features in `doc-architect-core/pom.xml`:
```xml
<configuration>
    <outputDirectory>${project.build.directory}/generated-sources/antlr4</outputDirectory>
    <listener>false</listener>        <!-- Disabled (not used) -->
    <visitor>false</visitor>          <!-- Disabled (not used) -->
    <treatWarningsAsErrors>false</treatWarningsAsErrors>
</configuration>
```

**Rationale**: The project does not use ANTLR listeners or visitors. Disabling these features reduces generated code and compilation time.

### 2. Incremental Compilation (Implemented)
**Impact**: Faster incremental builds (when not doing clean builds)

Enabled in parent `pom.xml`:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <release>21</release>
        <useIncrementalCompilation>true</useIncrementalCompilation>
    </configuration>
</plugin>
```

**Rationale**: Standard Maven feature that only recompiles changed classes on incremental builds.

### 3. ArchUnit Java 21 Compatibility Fix (Implemented)
**Impact**: Eliminated warnings, cleaner build output

Fixed both ArchUnit test files to exclude JDK internal classes:
```java
@BeforeAll
static void setUp() {
    scannerClasses = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
        .withImportOption(location -> !location.contains("/jrt:/"))  // Exclude JDK modules
        .importPackages("com.docarchitect.core.scanner");
}
```

**Rationale**: Prevents ArchUnit from attempting to parse Java 21 JDK internal classes (class file version 67), eliminating warnings and reducing import overhead.

### 4. Maven Surefire Fork Reuse (Implemented)
**Impact**: Reduced JVM startup overhead

Configured in parent `pom.xml`:
```xml
<configuration>
    <reuseForks>true</reuseForks>
    <forkCount>1</forkCount>
</configuration>
```

**Rationale**: Reusing JVM forks reduces the overhead of starting new JVMs for each test class.

### 5. JaCoCo ANTLR Exclusions (Already in place)
**Status**: Already configured in parent POM (lines 268-270, 280-282)

Generated ANTLR code in `com.docarchitect.parser.**` package is excluded from coverage analysis.

**Rationale**: Generated code doesn't need test coverage and excluding it improves analysis performance.

### 6. Parallel Test Execution (Evaluated and Rejected)
**Decision**: NOT implemented

We evaluated:
```xml
<parallel>classes</parallel>
<threadCount>4</threadCount>
<perCoreThreadCount>true</perCoreThreadCount>
```

**Result**: Build time increased from 77s to 82s (6.5% slower)

**Rationale**: The test suite consists of many small, fast tests (most <100ms). Parallel execution overhead (thread management, synchronization) outweighs benefits. This optimization works best for test suites with fewer, longer-running tests.

## Results

**Baseline** (before optimizations):
- Build time: 1:17 min (77 seconds)

**Optimized** (after optimizations):
- Build time: 1:12 min (72 seconds)

**Improvement**: 5 seconds saved (~6.5% faster)

**Additional Benefits**:
- Eliminated ArchUnit Java 21 warnings
- Cleaner build output
- Faster incremental builds (when not using `clean`)
- Reduced generated code from ANTLR

## Consequences

### Positive
- **6.5% faster clean builds** - Improved developer productivity
- **Cleaner build output** - No ArchUnit warnings
- **Faster incremental builds** - Incremental compilation helps during development
- **Reduced generated code** - Smaller artifact sizes
- **Maintained quality gates** - All 998 tests still passing, 65%+ coverage

### Negative
- **Minimal** - No significant downsides
- ANTLR listener/visitor features disabled (not used in project)

### Neutral
- Test parallelization not viable for this test suite
- Further optimization would require architectural changes (module splitting, test categorization)

## Alternatives Considered

### Test Parallelization
- **Status**: Evaluated and rejected
- **Reason**: Overhead > benefits for fast unit tests

### CI/CD Build Caching
- **Status**: Out of scope for this ADR
- **Reason**: Would be implemented in GitHub Actions, not Maven POM
- **Potential**: 30-50% reduction in CI build time

### Module Splitting
- **Status**: Deferred
- **Reason**: Would require significant refactoring
- **Potential**: Better incremental builds, but high implementation cost

### Test Categorization (Unit vs Integration)
- **Status**: Deferred
- **Reason**: Tests are already well-designed (fast unit tests)
- **Potential**: Minimal benefit given current test performance

## References

- Issue: #52 - Optimize doc-architect-core build time
- Original build time target: Reduce from 2 minutes to 1 minute
- Actual baseline: 1:17 min (faster than reported issue)
- Current result: 1:12 min (6.5% improvement)

## Notes

The 6.5% improvement is modest because the build was already well-optimized:
- Fast unit tests (998 tests in ~25s)
- Efficient quality gates
- Good separation of concerns

Further improvements would require:
1. CI/CD-level caching (GitHub Actions)
2. Selective test execution based on changed files
3. Module-level build parallelization (Maven reactor)

These are deferred to future optimization efforts as the current build time (72 seconds) is acceptable for developer workflow.
