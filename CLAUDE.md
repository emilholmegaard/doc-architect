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
- **No integration tests yet:** Phase 1 focuses on core infrastructure

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
- **No ADRs yet:** Will be added when architectural decisions are made

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

**Next:** Phase 2 - Implement concrete scanners (Maven, Gradle) and generators (Mermaid, Markdown)
