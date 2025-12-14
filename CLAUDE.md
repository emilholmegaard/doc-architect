# CLAUDE.md – Development Guidelines

## Tech Stack

- **Java 21**, Maven 3.9+, JUnit 5, AssertJ
- **Architecture:** Multi-module Maven project with SPI-based plugin system
- Docs managed via **Backstage TechDocs** (ADR + feature docs in `/docs`)

---

## Principles

- **KISS:** Simplest solution first
- **Clean Code & SOLID:** Small functions, clear names, single responsibility, composition over inheritance
- **Tests & Docs:** Every change must include unit tests + complete Javadoc
- **Security & Validation:** Validate inputs via compact constructors in records, no secrets in code

---

## Project Structure

```
doc-architect/
├── pom.xml                          # Parent POM (dependency & plugin management)
├── doc-architect-core/              # Core interfaces & domain models
│   ├── src/main/java/com/docarchitect/core/
│   │   ├── scanner/                 # Scanner SPI (Scanner, ScanContext, ScanResult)
│   │   ├── generator/               # Generator SPI (DiagramGenerator, GeneratorConfig)
│   │   ├── renderer/                # Renderer SPI (OutputRenderer, RenderContext)
│   │   ├── model/                   # Domain records (Component, Dependency, etc.)
│   │   └── util/                    # Utilities (FileUtils, IdGenerator)
│   └── src/main/resources/META-INF/services/  # SPI registration files
└── doc-architect-cli/               # CLI implementation (Picocli)
    └── src/main/java/com/docarchitect/
```

---

## Architecture Patterns

### Plugin System (SPI)

- All scanners, generators, and renderers use Java Service Provider Interface
- Register implementations in `META-INF/services/`
- Discovered at runtime via `ServiceLoader`

### Domain Models

- **Records only:** All domain objects are immutable Java records
- **Compact constructors:** Validation happens in compact constructors
- **No nulls:** Use `Objects.requireNonNull()` for mandatory fields, provide defaults for optional fields

### Intermediate Representation

- Scanners produce `ScanResult` → aggregated into `ArchitectureModel`
- Generators consume `ArchitectureModel` → produce `GeneratedDiagram`
- Renderers output `GeneratedOutput` → write to destinations

---

## Git & Review

- **Branches:** `feature/<issue-id>-<short-description>`, `hotfix/<issue-id>-<description>`
- **Commits:** Conventional Commits (feat, fix, docs, refactor, test)
- **PRs:** Small scope, include tests, link to GitHub issue, complete Javadoc

---

## Testing Standards

- **Unit tests:** For all utility classes and core logic (JUnit 5 + AssertJ)
- **Coverage:** ≥60% for core module (enforced by JaCoCo)
- **Test naming:** `methodName_withCondition_expectedResult`
- **Integration tests:** If there are any types of integration, there should be an integration tests, also to test the SPI

Example:

```java
@Test
void generate_withValidInput_returnsDeterministicId() {
    String id1 = IdGenerator.generate("user-service");
    String id2 = IdGenerator.generate("user-service");

    assertThat(id1).isEqualTo(id2);
    assertThat(id1).hasSize(16);
}
```

---

## Documentation Requirements

- **Complete Javadoc:** All public interfaces, classes, methods, and records
- **Interface documentation:** Explain purpose, usage, registration, and provide code examples
- **Record documentation:** Document each parameter in compact form
- **ADRs:** Add architectural decisions when there is a real decision, somehtin changes, new structures are made, the infrastructure or performance are affected.

---

## Build & Validation

```bash
# Build all modules
mvn clean compile

# Run tests
mvn test

# Package (creates JARs)
mvn clean package

# Skip tests (faster build)
mvn clean package -DskipTests
```

**CI checks (future):**

- Maven compile → JUnit tests → JaCoCo coverage → Javadoc generation → JAR packaging

---

## Phase 1 Complete ✅

**Core infrastructure established:**

- ✅ Java 21 with Maven multi-module structure
- ✅ SPI interfaces: Scanner, DiagramGenerator, OutputRenderer
- ✅ Domain models: Component, Dependency, ApiEndpoint, MessageFlow, DataEntity, Relationship, ArchitectureModel
- ✅ Context/result models with full validation
- ✅ Utilities: FileUtils (glob matching), IdGenerator (SHA-256 deterministic IDs)
- ✅ Complete unit test coverage (31 tests passing)

## Phase 2 Complete ✅

**CLI Application with Picocli:**

- ✅ Picocli-based CLI with 6 subcommands
- ✅ Global options: `--verbose`, `--quiet`
- ✅ `init` command: Project detection + YAML config generation (fully functional)
- ✅ `list` command: ServiceLoader discovery for scanners/generators/renderers
- ✅ `scan`, `generate`, `validate`, `diff` commands: Stubs for future phases
- ✅ Maven Shade Plugin: Fat JAR creation with SPI merging
- ✅ GitHub Actions: Updated to Java 21, fixed multi-module paths

## Phase 3 Complete ✅

**Java/JVM Scanners (5/5):**

- ✅ Maven Dependency Scanner: Jackson XML parsing, property resolution (${project.version})
- ✅ Gradle Dependency Scanner: Regex-based for Groovy & Kotlin DSL, 3 notation styles
- ✅ Spring REST API Scanner: JavaParser AST for @RestController, @GetMapping, parameter extraction
- ✅ JPA Entity Scanner: JavaParser AST for @Entity, field mapping, relationship detection
- ✅ Kafka Scanner: JavaParser AST for @KafkaListener, @SendTo, KafkaTemplate.send()
- ✅ JavaParser 3.25.8 + Jackson 2.18.2 integration
- ✅ SPI registration for all 5 scanners
- ✅ All tests passing (31 tests)
- ✅ GitHub Actions CI/CD ready

## Phase 4 Complete ✅

**Python Scanners (5/5):**

- ✅ Pip/Poetry Dependency Scanner: TOML/YAML parsing for requirements.txt, pyproject.toml, setup.py, Pipfile
- ✅ FastAPI Scanner: Regex-based route decorator extraction (@app.get, @router.post), parameter extraction
- ✅ Flask Scanner: Both legacy (@route with methods) and modern (@get, @post) decorator styles
- ✅ SQLAlchemy Scanner: Both Column() (1.x) and mapped_column() (2.0+), relationship detection
- ✅ Django ORM Scanner: models.Model classes, field mapping, ForeignKey/ManyToMany relationships
- ✅ Jackson TOML 2.18.2 integration
- ✅ Text-based Python parsing (regex patterns, no AST parser)
- ✅ SPI registration for all 5 scanners
- ✅ All scanners compile successfully
- ✅ Complete Javadoc with documented regex patterns

## Phase 5 Complete ✅

**.NET Scanners (3/3):**

- ✅ NuGet Dependency Scanner: Jackson XML for .csproj (SDK-style and legacy), packages.config, Directory.Build.props
- ✅ ASP.NET Core API Scanner: Regex-based attribute extraction ([HttpGet], [HttpPost], [FromBody], [FromQuery])
- ✅ Entity Framework Scanner: DbContext DbSet detection, navigation properties (ICollection, List), relationship mapping
- ✅ Hybrid parsing strategy: XML for project files, regex for C# source
- ✅ Support for both .NET Framework and .NET Core/.NET 5+
- ✅ SPI registration for all 3 scanners
- ✅ All scanners compile successfully
- ✅ Complete Javadoc with documented regex patterns

## Phase 6 Complete ✅

**Additional Scanners (6/6):**

- ✅ GraphQL Schema Scanner: Regex-based extraction of types, queries, mutations from .graphql/.gql files
- ✅ Avro Schema Scanner: Jackson JSON parsing for Avro schema definitions (.avsc), message flow detection
- ✅ SQL Migration Scanner: Regex-based CREATE TABLE extraction from .sql migration files (Flyway, golang-migrate)
- ✅ npm Dependency Scanner: Jackson JSON parsing for package.json, dependencies/devDependencies/peerDependencies
- ✅ Go Module Scanner: Regex-based go.mod parsing, require block extraction, semantic versioning support
- ✅ Express.js API Scanner: Regex-based route extraction (app.get, router.post, etc.) from JS/TS files
- ✅ SPI registration for all 6 scanners
- ✅ All scanners compile successfully
- ✅ 145 tests passing (36 new tests for Phase 6 scanners)
- ✅ Complete Javadoc with documented parsing strategies

---

## Lessons Learned & Prevention

After completing Phases 1-6 (19 scanners across 5 ecosystems), we discovered critical architectural issues that accumulated during rapid feature development. This section documents what went wrong and how to prevent it.

### What Went Wrong

#### 1. Test Coverage Neglect (Critical)

- **Issue:** Coverage dropped from 100% (Phase 1) → 1% (Phase 6)
- **Root Cause:** Added 145 tests but ZERO functional tests, only metadata validation
- **Impact:** 54 regex patterns with zero validation, no parser logic tested, silent failures possible
- **Evidence:** JaCoCo report showed 136/7,891 instructions covered (1%)

#### 2. CI/CD Not Enforcing Quality (Critical)

- **Issue:** `continue-on-error: true` in build.yml allowed broken tests to pass
- **Root Cause:** Workaround added during Phase 3, never removed
- **Impact:** PRs merged with failing tests, quality gates bypassed
- **Evidence:** GitHub Actions showing red icons but builds marked "successful"

#### 3. Missing Code Review Checklist (High)

- **Issue:** No Definition of Done enforcing test coverage
- **Root Cause:** PR template lacked test coverage verification step
- **Impact:** 13 scanners merged with zero functional tests
- **Evidence:** All Python scanners (Phase 4) have zero test coverage

#### 4. Incremental Technical Debt Accumulation (High)

- **Issue:** ~900 LOC of boilerplate duplicated across 19 scanners
- **Root Cause:** No refactoring between phases, "ship features fast" mindset
- **Impact:** Violation of SOLID principles (SRP, ISP), maintenance burden
- **Evidence:** File reading, regex matching, Jackson initialization duplicated 19 times

#### 5. Delayed Library Evaluation (Medium)

- **Issue:** Regex-based GraphQL and Avro parsing when robust libraries exist
- **Root Cause:** No upfront research phase, implemented first solution
- **Impact:** Fragile parsing, missing edge cases, reinventing the wheel
- **Evidence:** graphql-java 21.3 and Apache Avro 1.11.3 available but unused

#### 6. Package Organization Debt (Medium)

- **Issue:** 19 scanners in flat `impl/` directory, no technology grouping
- **Root Cause:** Deferred organization "until later", never prioritized
- **Impact:** Hard to navigate, unclear ownership, no clear structure
- **Evidence:** All scanners in single directory regardless of ecosystem

#### 7. Missing SPI Validation (Medium)

- **Issue:** No integration test validating ServiceLoader discovers all scanners
- **Root Cause:** Assumed SPI registration "just works", no verification
- **Impact:** Silent failures if SPI file has typos, runtime discovery issues
- **Evidence:** Zero tests calling `ServiceLoader.load(Scanner.class)`

### Prevention Measures (Mandatory for All Future Work)

#### Definition of Done Checklist

Every PR MUST satisfy:

- ✅ Functional tests written covering happy path + edge cases
- ✅ Test coverage ≥80% for modified files (verified via `mvn jacoco:report`)
- ✅ No `continue-on-error: true` in critical CI workflows
- ✅ All tests passing locally AND in CI
- ✅ Complete Javadoc for new public APIs
- ✅ SPI registration validated if adding new implementations

#### PR Template Updates (Enforced)

Add to `.github/pull_request_template.md`:

```markdown
## Test Coverage
- [ ] Added functional tests (not just metadata tests)
- [ ] Coverage ≥80% for changed files (attach JaCoCo report screenshot)
- [ ] Integration tests added if touching SPI

## Quality Gates
- [ ] All tests passing (no continue-on-error bypasses)
- [ ] No new code duplication >20 LOC (check with SonarQube/manual review)
- [ ] Evaluated 3rd party libraries before implementing parsers
```

#### Quarterly Architecture Reviews (Scheduled)

Every 3 months OR after 5 scanners added:

- Review SOLID compliance (SRP, OCP, LSP, ISP, DIP)
- Identify code duplication >100 LOC and refactor
- Evaluate new 3rd party libraries (avoid reinventing wheels)
- Package reorganization if >10 classes in single directory
- Coverage audit (must be ≥60% overall, ≥80% for new code)

#### Upfront Library Evaluation (Required)

Before implementing ANY parser:

1. Research existing libraries (5-10 min search)
2. Evaluate: maturity, maintenance, license, API simplicity
3. Document decision in ADR if implementing custom solution
4. Prefer libraries for complex formats (GraphQL, Avro, SQL, etc.)

**Examples:**

- GraphQL → Use graphql-java, NOT regex
- Avro → Use Apache Avro, NOT Jackson JSON
- SQL → Use JSqlParser, NOT regex
- JSON/XML/TOML → Jackson is appropriate

#### CI/CD Quality Enforcement (Non-Negotiable)

- **NEVER** use `continue-on-error: true` in build.yml or pr.yml
- Coverage gate: `mvn verify` fails if coverage <60%
- Require `mvn test` to pass before merge (no bypasses)
- Add SPI validation test running in CI

#### Refactoring Trigger Points

Stop feature work and refactor when:

- Test coverage drops below 60%
- Code duplication >500 LOC total
- >10 classes in single directory
- SOLID violations identified in code review
- CI/CD bypasses added (continue-on-error, skip tests)

### Current State (Post-Phase 6)

**Status:** Architecture refactoring in progress (see refactoring-proposal.md)

**Metrics:**

- 19 scanners across 5 ecosystems (Java, Python, .NET, JavaScript, Go)
- 145 tests (all metadata, zero functional tests)
- Test coverage: 1% (136/7,891 instructions)
- Code duplication: ~900 LOC
- CI/CD: `continue-on-error: true` blocking quality enforcement

**Refactoring Plan:**

- Week 1: Fix CI/CD, add SPI validation, adopt graphql-java and Apache Avro
- Week 2: Build test infrastructure, add 6 critical scanner tests
- Week 3: Extract base classes (AbstractRegexScanner, AbstractJavaParserScanner, AbstractJacksonScanner)
- Week 4: Package reorganization by technology (java/, python/, dotnet/, javascript/, go/, schema/)

**Target Metrics (End of Refactoring):**

- Test coverage: 80%+
- Code duplication: <200 LOC
- CI/CD: All quality gates enforced
- Package structure: Technology-based hierarchy
- 100+ functional tests covering parsing logic

**Next:** Architecture refactoring (branch: refactor/architecture-improvements) - see `.github/ISSUE_TEMPLATE/refactoring-proposal.md` for complete plan
