# CLAUDE.md – Working Agreement (Java, Architecture-First)

## Role

- Operate as a senior software engineer (PhD-level rigor), pragmatic, standards-driven.
- Ask to clarify when requirements are ambiguous; otherwise proceed with a short plan, then changes.
- Favor incremental, low-risk changes aligned with ADRs and existing patterns.

## Architectural Principles

- Plugin architecture via Java SPI (ServiceLoader). Scanners/generators/renderers are discoverable and independently testable.
- Immutability by default (Java records). Validate in compact constructors; avoid nulls for required fields.
- Separation of concerns: scanners extract facts → aggregated model → generators → renderers.
- Prefer base classes for shared mechanisms:
  - AbstractRegexScanner, AbstractJacksonScanner, AbstractJavaParserScanner (all extend AbstractScanner).
- Align with ADRs in docs/adrs (C4 model, TechDocs, logging, testing, packaging, scanner pre-filtering).
- All documentation are placed in docs/ and decorated with yaml for mkdocs

## Project Structure

```
doc-architect/
├── pom.xml                          # Parent POM (dependency & plugin mgmt)
├── doc-architect-core/
│   ├── src/main/java/com/docarchitect/core/
│   │   ├── scanner/
│   │   │   ├── base/                # AbstractScanner + parsing base classes
│   │   │   └── impl/
│   │   │       ├── java/            # Maven, Gradle, Spring REST, JPA, Kafka
│   │   │       ├── python/          # Pip/Poetry, FastAPI, Flask, SQLAlchemy, Django
│   │   │       ├── dotnet/          # NuGet, ASP.NET Core, EF
│   │   │       ├── javascript/      # npm, Express.js
│   │   │       ├── go/              # go.mod
│   │   │       └── schema/          # GraphQL, Avro, SQL migrations
│   │   ├── generator/               # Diagram generators
│   │   ├── renderer/                # Output renderers
│   │   ├── model/                   # Domain records
│   │   └── util/                    # Utilities (e.g., FileUtils, IdGenerator)
│   └── src/main/resources/META-INF/services/  # SPI registration
└── doc-architect-cli/               # Picocli-based CLI
```

## Java Coding Standards & Javadoc

- Use SOLID and composition; keep functions small, names precise, and APIs minimal.
- Public APIs require complete Javadoc (purpose, params, returns, exceptions, examples).
- Prefer package-private for internals; keep constructors private for factories if applicable.
- Deterministic behavior; pure functions where reasonable; no hidden I/O.

Javadoc example (record + usage):

```java
/**
 * Represents a dependency between components within the architecture model.
 *
 * <p>Immutable record. Use the compact constructor to validate invariants.</p>
 *
 * @param sourceId the unique id of the source component (non-null)
 * @param targetId the unique id of the target component (non-null)
 * @param type a normalized dependency type (e.g., "maven", "nuget", "http")
 * @param strength an optional qualitative value (e.g., "required", "optional")
 */
public record Dependency(String sourceId, String targetId, String type, String strength) {
  public Dependency {
    Objects.requireNonNull(sourceId, "sourceId");
    Objects.requireNonNull(targetId, "targetId");
  }
}
```

## Testing & Quality Gates

- Unit tests (JUnit 5 + AssertJ) for all non-trivial logic.
- ArchUnit tests to enforce layering, package boundaries, SPI rules.
- SPI discovery tests when adding scanners/generators/renderers (ServiceLoader).
- Coverage:
  - ≥65% overall bundle (JaCoCo)
  - ≥80% for changed/new code
- No continue-on-error for critical workflows.

Test example:

```java
@Test
void generate_withValidInput_returnsDeterministicId() {
  String id1 = IdGenerator.generate("user-service");
  String id2 = IdGenerator.generate("user-service");
  assertThat(id1).isEqualTo(id2);
  assertThat(id1).hasSize(16);
}
```

## Logging & Observability

- SLF4J + Logback. Defaults: INFO in prod, WARN in tests.
- Env overrides: LOGBACK_LEVEL, SCANNER_LOG_LEVEL, GENERATOR_LOG_LEVEL, RENDERER_LOG_LEVEL.
- Optional file logging: -Ddoc-architect.log.file=true
- Log actionable context; do not swallow exceptions.

## Libraries & Parsers (prefer proven libs)

- AST from ANTLR when possible, to avoid regular expressions
- GraphQL → graphql-java (avoid regex for rich grammars)
- Avro → Apache Avro (avoid plain JSON parsing)
- SQL → JSqlParser where applicable
- Config: Jackson for XML/JSON/TOML/YAML

## Security

- No secrets in code or logs. Validate/sanitize inputs.
- Avoid unsafe file/process operations; respect read-only mounts.
- Keep dependencies current via central version management.

## Build & Tooling (quick reference)

- Build: mvn clean package
- Tests: mvn test
- Verify (coverage/style/bugs): mvn verify
- Coverage report: mvn jacoco:report
- Enable file logging: mvn test -Ddoc-architect.log.file=true
- GitHub Actions locally: act pull_request -j build

## Decision Process & When to Ask

- Large/cross-cutting changes: propose a short phased plan; confirm acceptance criteria.
- If conventions conflict, follow ADRs or most prevalent repository pattern.
- Ask when requirements or thresholds are unclear.

## Definition of Done (PRs)

- Tests added/passing locally and in CI; coverage thresholds met.
- Javadoc complete for public APIs; ADR/docs updated if architecture changes.
- No unnecessary duplication; aligns with base classes and package structure.
- SPI registration and discovery tests included when adding providers.
