# Architecture Refactoring: Foundation First (Incremental)

## 📋 Executive Summary

This issue documents the comprehensive architecture refactoring plan for the doc-architect project, addressing critical technical debt identified after Phase 6 completion. The refactoring follows an **incremental "Foundation First"** approach over 3-4 weeks with minimal risk and parallel feature development capability.

## 🎯 Decision: Option 1 - Incremental "Foundation First"

**Rationale:**
- ✅ Balances risk and reward (LOW risk, HIGH value)
- ✅ Allows parallel feature development
- ✅ Delivers value incrementally
- ✅ Tests guide informed refactoring decisions
- ✅ Industry best practice for legacy system improvements

## 🔍 Current State Analysis

### Project Statistics (as of 2025-12-14)
- **19 scanners** implemented across 5 technology ecosystems
- **~5,100 LOC** in scanner implementations
- **54 regex patterns** across 13 scanners
- **145 tests** (but only metadata tests - zero functional tests)
- **~15% test coverage** (target: 80%)
- **~900 LOC code duplication** identified

### Critical Issues Identified

#### 1. CI/CD Failures ⚠️ CRITICAL
**Issue:** Tests set to `continue-on-error: true` in `.github/workflows/build.yml`
**Impact:** Broken scanners can be merged to main without detection
**Evidence:**
```yaml
# .github/workflows/build.yml:43
- name: Run tests
  if: inputs.run-tests
  run: ./mvnw test
  continue-on-error: true  # ⚠️ ALLOWS TESTS TO FAIL!
```

#### 2. Zero Functional Test Coverage ⚠️ CRITICAL
**Issue:** 19 scanners with zero parsing validation tests
**Impact:** High risk of bugs in production, especially in regex patterns
**Evidence:**
- 54 regex patterns with ZERO validation tests
- No tests for dependency extraction (Maven, Gradle, NuGet, npm, Go)
- No tests for API endpoint extraction (Spring, FastAPI, Flask, ASP.NET, Express)
- No tests for entity/schema extraction (JPA, SQLAlchemy, EF, GraphQL, Avro, SQL)

#### 3. No SPI Registration Validation ⚠️ HIGH
**Issue:** No automated test to verify all scanners are registered
**Impact:** Could deploy with scanners that aren't discoverable via ServiceLoader
**Evidence:** No integration test for SPI discovery mechanism

#### 4. SOLID Compliance Issues ⚠️ MEDIUM

**Single Responsibility Principle Violations:**
- Scanners mix file I/O, parsing, and domain model creation
- Some scanners handle 4+ file formats (e.g., PipPoetryDependencyScanner)
- Inconsistent error handling strategies

**Interface Segregation Principle:**
- Scanner interface might be too broad (metadata + execution methods combined)

**Example Violation:**
```java
// NuGetDependencyScanner handles 4 different file formats:
// - .csproj (SDK-style)
// - .csproj (Legacy .NET Framework)
// - packages.config
// - Directory.Build.props
```

#### 5. Code Duplication (900+ LOC) ⚠️ MEDIUM

**Common patterns duplicated across all scanners:**
- File reading: 19 instances (~38 LOC)
- Regex matching: 54 instances (~540 LOC)
- Jackson initialization: 5 instances (~15 LOC)
- ScanResult creation: 19 instances (~190 LOC)
- Logger initialization: 19 instances (~19 LOC)
- Empty result handling: 19 instances (~95 LOC)

**Total:** ~897 LOC of boilerplate (potential 78% reduction)

#### 6. Flat Package Structure ⚠️ LOW
**Issue:** All 19 scanners in single package `scanner/impl/`
**Impact:** Hard to navigate, unclear organization
**Recommendation:** Organize by technology (java/, python/, dotnet/, javascript/, go/, schema/)

#### 7. Suboptimal Library Usage ⚠️ MEDIUM

**GraphQL Parsing:**
- Current: Regex-based (brittle, incomplete)
- Recommended: **graphql-java** library (proper AST parsing)

**Avro Parsing:**
- Current: Regex-based (insufficient for complex schemas)
- Recommended: **Apache Avro** library (official parser)

**SQL Parsing:**
- Current: Basic regex for CREATE TABLE
- Recommended: **JSqlParser** (comprehensive DDL parsing)

## 📅 Implementation Plan (4 Weeks)

### Week 1: Fix Critical Issues ⚠️ PRIORITY 1

#### Task 1.1: Fix CI/CD (1 hour)
**Files to modify:**
- `.github/workflows/build.yml`

**Changes:**
```yaml
# REMOVE this line:
continue-on-error: true

# ADD coverage enforcement:
- name: Check code coverage
  run: mvn verify  # Enforces 60% coverage threshold
```

**Acceptance Criteria:**
- ✅ Tests must pass for builds to succeed
- ✅ Coverage threshold (60%) enforced in CI
- ✅ Failed tests block PR merges

#### Task 1.2: Add SPI Registration Validation Test (2 hours)
**New file:** `doc-architect-core/src/test/java/com/docarchitect/core/scanner/integration/ScannerDiscoveryIntegrationTest.java`

**Test requirements:**
```java
@Test
void serviceLoader_discoversAllRegisteredScanners() {
    ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);
    List<Scanner> scanners = StreamSupport.stream(loader.spliterator(), false)
        .toList();

    // Verify all 19 scanners are discoverable
    assertThat(scanners).hasSize(19);

    // Verify each scanner by ID
    Set<String> scannerIds = scanners.stream()
        .map(Scanner::getId)
        .collect(Collectors.toSet());

    assertThat(scannerIds).containsExactlyInAnyOrder(
        "maven-dependencies", "gradle-dependencies",
        "spring-rest-api", "jpa-entities", "kafka-messaging",
        "python-dependencies", "fastapi", "flask",
        "sqlalchemy-entities", "django-orm",
        "nuget-dependencies", "aspnetcore-api", "entity-framework",
        "graphql-schema", "avro-schema", "sql-migrations",
        "npm-dependencies", "go-modules", "express-api"
    );
}
```

**Acceptance Criteria:**
- ✅ Test verifies ServiceLoader discovers all scanners
- ✅ Test fails if scanner registration is missing
- ✅ Test runs in CI pipeline

#### Task 1.3: Adopt graphql-java Library (4 hours)
**Files to modify:**
- `pom.xml` (add dependency)
- `GraphQLScanner.java` (refactor to use library)

**Dependency:**
```xml
<dependency>
    <groupId>com.graphql-java</groupId>
    <artifactId>graphql-java</artifactId>
    <version>21.3</version>
</dependency>
```

**Refactoring approach:**
- Replace regex patterns with `SchemaParser`
- Use `TypeDefinitionRegistry` for type extraction
- Extract queries/mutations from schema

**Acceptance Criteria:**
- ✅ GraphQL parsing uses graphql-java library
- ✅ All existing functionality maintained
- ✅ Tests added for GraphQL scanner
- ✅ Handles malformed schemas gracefully

#### Task 1.4: Adopt Apache Avro Library (4 hours)
**Files to modify:**
- `pom.xml` (add dependency)
- `AvroSchemaScanner.java` (refactor to use library)

**Dependency:**
```xml
<dependency>
    <groupId>org.apache.avro</groupId>
    <artifactId>avro</artifactId>
    <version>1.11.3</version>
</dependency>
```

**Refactoring approach:**
- Replace regex with `Schema.Parser`
- Extract record fields, enums, unions
- Validate schema correctness

**Acceptance Criteria:**
- ✅ Avro parsing uses official Avro library
- ✅ All existing functionality maintained
- ✅ Tests added for Avro scanner
- ✅ Handles schema validation errors

**Week 1 Deliverables:**
- ✅ Working CI/CD pipeline (tests block merges)
- ✅ SPI registration validation test
- ✅ 2 scanners refactored to use proper libraries
- ✅ Coverage enforcement active

---

### Week 2: Build Test Infrastructure ⚠️ PRIORITY 2

#### Task 2.1: Create ScannerTestBase Class (4 hours)
**New file:** `doc-architect-core/src/test/java/com/docarchitect/core/scanner/ScannerTestBase.java`

**Provides:**
```java
public abstract class ScannerTestBase<T extends Scanner> {
    protected abstract T createScanner();

    @Test
    void scanner_hasValidMetadata() {
        T scanner = createScanner();
        assertThat(scanner.getId()).isNotBlank().matches("[a-z0-9-]+");
        assertThat(scanner.getDisplayName()).isNotBlank();
        assertThat(scanner.getSupportedLanguages()).isNotEmpty();
        assertThat(scanner.getSupportedFilePatterns()).isNotEmpty();
        assertThat(scanner.getPriority()).isBetween(0, 100);
    }

    @Test
    void scanner_appliesTo_withRelevantFiles(@TempDir Path tempDir) {
        // Subclasses override to test appliesTo() logic
    }

    protected ScanContext createContext(Path root) {
        return new ScanContext(root, List.of(root), Map.of(), Map.of(), Map.of());
    }

    protected void createTestFile(Path dir, String filename, String content)
        throws IOException {
        Path file = dir.resolve(filename);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
```

#### Task 2.2: Add Unit Tests for Critical Scanners (24 hours)

**Priority 1 Scanners (Week 2):**
1. MavenDependencyScannerTest (4 hours)
   - Test property resolution (${project.version}, ${spring.version})
   - Test parent POM inheritance
   - Test dependency scopes (compile, test, provided, runtime)
   - Test malformed XML handling

2. GradleDependencyScannerTest (4 hours)
   - Test string notation: `'group:artifact:version'`
   - Test map notation: `group: 'x', name: 'y', version: 'z'`
   - Test Kotlin function notation: `implementation("group:artifact:version")`
   - Test configuration mapping (implementation→compile, compileOnly→provided)

3. SpringRestApiScannerTest (4 hours)
   - Test @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping
   - Test @RequestMapping with methods array
   - Test @PathVariable, @RequestParam, @RequestBody extraction
   - Test path combination (@RequestMapping on class + method)

4. NpmDependencyScannerTest (4 hours)
   - Test dependencies section (compile scope)
   - Test devDependencies section (test scope)
   - Test peerDependencies section (provided scope)
   - Test scoped packages (@angular/core → groupId=@angular, artifactId=core)

5. FastAPIScannerTest (4 hours)
   - Test @app.get, @app.post, @app.put, @app.delete, @app.patch
   - Test @router.* variants
   - Test Path(), Query(), Body() parameter extraction
   - Test path parameters {user_id}

6. NuGetDependencyScannerTest (4 hours)
   - Test SDK-style .csproj (PackageReference)
   - Test legacy .csproj (Reference with HintPath)
   - Test packages.config parsing
   - Test Directory.Build.props central package management

**Test Resources Needed:**
- Create realistic test files in `src/test/resources/test-projects/`
- Reuse existing test projects where possible
- Add malformed file examples for error handling tests

**Week 2 Deliverables:**
- ✅ ScannerTestBase class for common test patterns
- ✅ 6 critical scanners fully tested
- ✅ Coverage increase: ~15% → ~40%
- ✅ All regex patterns validated

---

### Week 3: Extract Base Classes & Utilities ⚠️ PRIORITY 3

#### Task 3.1: Create Base Scanner Classes (8 hours)

**New files:**
- `doc-architect-core/src/main/java/com/docarchitect/core/scanner/base/AbstractRegexScanner.java`
- `doc-architect-core/src/main/java/com/docarchitect/core/scanner/base/AbstractJavaParserScanner.java`
- `doc-architect-core/src/main/java/com/docarchitect/core/scanner/base/AbstractJacksonScanner.java`

**AbstractRegexScanner:**
```java
public abstract class AbstractRegexScanner implements Scanner {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public ScanResult scan(ScanContext context) {
        List<Path> files = findRelevantFiles(context);
        if (files.isEmpty()) {
            log.warn("No {} files found in project", getDisplayName());
            return ScanResult.empty(getId());
        }
        return processFiles(context, files);
    }

    protected abstract List<Path> findRelevantFiles(ScanContext context);
    protected abstract ScanResult processFiles(ScanContext context, List<Path> files);

    protected String readFile(Path path) throws IOException {
        return Files.readString(path);
    }

    protected List<String> extractMatches(Pattern pattern, String content, int group) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            matches.add(matcher.group(group));
        }
        return matches;
    }
}
```

**AbstractJavaParserScanner:**
```java
public abstract class AbstractJavaParserScanner implements Scanner {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected CompilationUnit parseJavaFile(Path file) throws IOException {
        return StaticJavaParser.parse(Files.readString(file));
    }

    protected boolean hasAnnotation(ClassOrInterfaceDeclaration cls, String... annotations) {
        return cls.getAnnotations().stream()
            .anyMatch(ann -> Arrays.asList(annotations).contains(ann.getNameAsString()));
    }

    protected Optional<AnnotationExpr> findAnnotation(NodeWithAnnotations<?> node,
                                                       String annotationName) {
        return node.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals(annotationName))
            .findFirst();
    }
}
```

#### Task 3.2: Extract Utility Classes (4 hours)

**New files:**
- `doc-architect-core/src/main/java/com/docarchitect/core/util/RegexUtils.java`
- `doc-architect-core/src/main/java/com/docarchitect/core/scanner/ScanResultBuilder.java`

**RegexUtils:**
```java
public class RegexUtils {
    public static List<String> extractAllMatches(Pattern pattern, String content, int group) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            matches.add(matcher.group(group));
        }
        return matches;
    }

    public static Optional<String> extractFirstMatch(Pattern pattern, String content, int group) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? Optional.of(matcher.group(group)) : Optional.empty();
    }

    public static Map<String, String> extractKeyValuePairs(Pattern pattern, String content,
                                                            int keyGroup, int valueGroup) {
        Map<String, String> pairs = new HashMap<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            pairs.put(matcher.group(keyGroup), matcher.group(valueGroup));
        }
        return pairs;
    }
}
```

**ScanResultBuilder:**
```java
public class ScanResultBuilder {
    private final String scannerId;
    private final List<Component> components = new ArrayList<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final List<ApiEndpoint> apiEndpoints = new ArrayList<>();
    private final List<MessageFlow> messageFlows = new ArrayList<>();
    private final List<DataEntity> dataEntities = new ArrayList<>();
    private final List<Relationship> relationships = new ArrayList<>();

    private ScanResultBuilder(String scannerId) {
        this.scannerId = scannerId;
    }

    public static ScanResultBuilder forScanner(String id) {
        return new ScanResultBuilder(id);
    }

    public ScanResultBuilder addComponent(Component c) {
        components.add(c);
        return this;
    }

    public ScanResultBuilder addDependency(Dependency d) {
        dependencies.add(d);
        return this;
    }

    public ScanResult build() {
        return new ScanResult(scannerId, true, components, dependencies,
            apiEndpoints, messageFlows, dataEntities, relationships,
            List.of(), List.of());
    }
}
```

#### Task 3.3: Migrate 5 Pilot Scanners (12 hours)

**Pilot scanners to migrate:**
1. FastAPIScanner → AbstractRegexScanner (2 hours)
2. FlaskScanner → AbstractRegexScanner (2 hours)
3. ExpressScanner → AbstractRegexScanner (2 hours)
4. SpringRestApiScanner → AbstractJavaParserScanner (3 hours)
5. JpaEntityScanner → AbstractJavaParserScanner (3 hours)

**Migration checklist per scanner:**
- [ ] Extend appropriate base class
- [ ] Remove duplicated boilerplate code
- [ ] Update tests to verify migration didn't break functionality
- [ ] Ensure code coverage remains stable

**Week 3 Deliverables:**
- ✅ 3 base scanner classes created
- ✅ 2 utility classes created
- ✅ 5 scanners migrated to new base classes
- ✅ Code duplication reduced by ~40%
- ✅ All tests still passing

---

### Week 4: Package Reorganization & Documentation ⚠️ PRIORITY 4

#### Task 4.1: Reorganize Package Structure (8 hours)

**New structure:**
```
doc-architect-core/src/main/java/com/docarchitect/core/scanner/
├── Scanner.java (interface)
├── ScanContext.java
├── ScanResult.java
├── base/
│   ├── AbstractRegexScanner.java
│   ├── AbstractJavaParserScanner.java
│   └── AbstractJacksonScanner.java
└── impl/
    ├── java/
    │   ├── MavenDependencyScanner.java
    │   ├── GradleDependencyScanner.java
    │   ├── SpringRestApiScanner.java
    │   ├── JpaEntityScanner.java
    │   └── KafkaScanner.java
    ├── python/
    │   ├── PipPoetryDependencyScanner.java
    │   ├── FastAPIScanner.java
    │   ├── FlaskScanner.java
    │   ├── SQLAlchemyScanner.java
    │   └── DjangoOrmScanner.java
    ├── dotnet/
    │   ├── NuGetDependencyScanner.java
    │   ├── AspNetCoreApiScanner.java
    │   └── EntityFrameworkScanner.java
    ├── javascript/
    │   ├── NpmDependencyScanner.java
    │   └── ExpressScanner.java
    ├── go/
    │   └── GoModScanner.java
    └── schema/
        ├── GraphQLScanner.java
        ├── AvroSchemaScanner.java
        └── SqlMigrationScanner.java
```

**Migration steps:**
1. Create new package directories
2. Move scanner files to appropriate packages
3. Update package declarations in scanner files
4. Update SPI registration file (META-INF/services)
5. Update all imports in test files
6. Run full test suite to verify nothing broke

#### Task 4.2: Reorganize Test Structure (4 hours)

**Mirror package structure in tests:**
```
doc-architect-core/src/test/java/com/docarchitect/core/scanner/
├── ScannerTestBase.java
├── impl/
│   ├── java/
│   │   ├── MavenDependencyScannerTest.java
│   │   ├── GradleDependencyScannerTest.java
│   │   └── ...
│   ├── python/
│   │   ├── PipPoetryDependencyScannerTest.java
│   │   └── ...
│   └── ...
└── integration/
    ├── ScannerDiscoveryIntegrationTest.java
    └── MultiScannerOrchestrationTest.java
```

#### Task 4.3: Update Documentation (4 hours)

**Files to update:**
1. **CLAUDE.md** - Add "Lessons Learned" section
2. **README.md** - Update architecture diagram
3. **ADR-005** - Document refactoring decisions (NEW)
4. **docs/architecture.md** - Update package structure (NEW)

**Week 4 Deliverables:**
- ✅ All scanners organized by technology
- ✅ Tests mirror source structure
- ✅ Documentation updated
- ✅ All tests passing
- ✅ Coverage target achieved (80%+)

---

## 📊 Success Metrics

### Quantitative Targets

| Metric | Before | After | Target Met? |
|--------|--------|-------|-------------|
| Test Coverage | ~15% | 80%+ | ✅ |
| Functional Tests | 0 | 100+ | ✅ |
| Code Duplication | ~900 LOC | ~200 LOC | ✅ |
| CI/CD Success | Unknown | 100% | ✅ |
| Regex Patterns Tested | 0/54 | 54/54 | ✅ |
| Libraries Integrated | 3 | 5+ | ✅ |
| SPI Tests | 0 | 100% | ✅ |

### Qualitative Goals

- ✅ **SOLID Compliance:** Base classes reduce SRP violations
- ✅ **Maintainability:** Clear package structure
- ✅ **Extensibility:** Easy to add new scanners
- ✅ **Confidence:** Comprehensive test coverage
- ✅ **Documentation:** Architecture clearly documented

## 🎓 Lessons Learned & Prevention

### What Went Wrong (to document in CLAUDE.md)

1. **Test Coverage Neglected:** Focused on features without sufficient testing
2. **CI/CD Not Enforcing Quality:** `continue-on-error` masked failing tests
3. **No Code Review Checklist:** Missing checklist for test requirements
4. **Incremental Debt:** Small violations accumulated into major issues
5. **Library Evaluation Delayed:** Should have adopted graphql-java/Avro sooner

### Prevention Measures

1. **Definition of Done:** Must include tests + >80% coverage
2. **PR Template:** Checklist includes test coverage verification
3. **CI/CD Guardrails:** No `continue-on-error` in critical workflows
4. **Architecture Reviews:** Quarterly review of SOLID compliance
5. **Library Evaluation:** Upfront research for parsing strategies

## 🔗 Related Issues & ADRs

- **Issue #7** - CLI Tests (to be created)
- **ADR-003** - JVM Scanner Implementation Strategy
- **ADR-004** - Python Scanner Text-Based Parsing
- **ADR-005** - Architecture Refactoring Decision (to be created)

## 📝 Implementation Checklist

### Week 1: Critical Fixes
- [ ] Remove `continue-on-error: true` from build.yml
- [ ] Add coverage enforcement (`mvn verify`)
- [ ] Add SPI registration validation test
- [ ] Integrate graphql-java library
- [ ] Integrate Apache Avro library
- [ ] Week 1 ADR documentation

### Week 2: Test Infrastructure
- [ ] Create ScannerTestBase class
- [ ] Add MavenDependencyScannerTest
- [ ] Add GradleDependencyScannerTest
- [ ] Add SpringRestApiScannerTest
- [ ] Add NpmDependencyScannerTest
- [ ] Add FastAPIScannerTest
- [ ] Add NuGetDependencyScannerTest
- [ ] Verify 40%+ coverage achieved

### Week 3: Base Classes
- [ ] Create AbstractRegexScanner
- [ ] Create AbstractJavaParserScanner
- [ ] Create AbstractJacksonScanner
- [ ] Create RegexUtils
- [ ] Create ScanResultBuilder
- [ ] Migrate 5 pilot scanners
- [ ] Verify no regression in functionality

### Week 4: Organization
- [ ] Create new package structure
- [ ] Move all 19 scanners to tech-specific packages
- [ ] Update SPI registration
- [ ] Reorganize tests
- [ ] Update CLAUDE.md with lessons learned
- [ ] Create ADR-005
- [ ] Update README.md
- [ ] Final verification: all tests pass, 80%+ coverage

## 🚀 Getting Started

```bash
# Create refactoring branch
git checkout main
git pull
git checkout -b refactor/architecture-improvements

# Start with Week 1 tasks
# 1. Fix CI/CD
# 2. Add SPI test
# 3. Integrate graphql-java
# 4. Integrate Apache Avro
```

---

**Created:** 2025-12-14
**Status:** Ready for Implementation
**Estimated Effort:** 3-4 weeks
**Risk Level:** LOW
**Parallel Development:** ✅ Enabled
