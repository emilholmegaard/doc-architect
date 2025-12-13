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

**Next:** Phase 6 - Implement diagram generators (Mermaid, PlantUML)
