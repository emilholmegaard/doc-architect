# Documentation Architecture

# DocArchitect

**Automated Architecture Documentation Generator from Source Code**

DocArchitect scans your codebase and automatically generates architecture documentation including dependency graphs, API documentation, ER diagrams, message flow diagrams, and C4 models.

## Features

- 🔍 **Multi-language support**: Java, Kotlin, Python, C#/.NET, Node.js, Go
- 📊 **Multiple diagram formats**: Mermaid, PlantUML, D2, Structurizr DSL
- 🗄️ **Database support**: PostgreSQL, MSSQL, MongoDB
- 📡 **API detection**: REST, GraphQL, gRPC, Avro schemas
- 📬 **Messaging support**: Kafka, RabbitMQ, Azure Service Bus
- 🐳 **Docker packaged**: Run anywhere without dependencies
- 🔌 **Plugin architecture**: Easy to extend with custom scanners

## Quick Start

```bash
# Pull the Docker image
docker pull ghcr.io/emilholmegaard/doc-architect:latest

# Initialize configuration in your project
docker run -v $(pwd):/workspace doc-architect init

# Generate documentation
docker run -v $(pwd):/workspace -v $(pwd)/docs:/output doc-architect scan
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      DocArchitect CLI (Picocli)                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌───────────────────┐    ┌──────────────────┐  │
│  │   Scanners   │─ ─▶│ ArchitectureModel │───▶│    Generators   │  │
│  │  (Scanner)   │    │  (Intermediate)   │    │(DiagramGenerator)│  │
│  └──────────────┘    └───────────────────┘    └──────────────────┘  │
│         │                                              │            │
│         ▼                                              ▼            │
│  ┌──────────────┐                            ┌──────────────────┐   │
│  │ ServiceLoader│                            │  OutputRenderer  │   │
│  │   (SPI)      │                            │                  │   │
│  └──────────────┘                            └──────────────────┘   │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                          Scanner Categories                         │
├─────────────────────────────────────────────────────────────────────┤
│  Dependencies:        │  APIs:              │  Messaging:           │
│  • Maven (pom.xml)    │  • Spring MVC       │  • Kafka              │
│  • Gradle             │  • JAX-RS           │  • RabbitMQ           │
│  • npm/yarn           │  • FastAPI          │  • Azure Service Bus  │
│  • pip/poetry         │  • Flask            │  • Avro Schemas       │
│  • NuGet (.csproj)    │  • ASP.NET Core     │  • AsyncAPI specs     │
│  • Go modules         │  • Express.js       │                       │
│                       │  • GraphQL          │                       │
│                       │  • gRPC/Protobuf    │                       │
├───────────────────────┼─────────────────────┼───────────────────────┤
│  Database:            │  Structure:         │  Integration:         │
│  • JPA/Hibernate      │  • Module detection │  • Sokrates scope     │
│  • SQLAlchemy         │  • Service bounds   │    file generation    │
│  • Django ORM         │  • Layer analysis   │  • OpenAPI export     │
│  • Entity Framework   │                     │  • AsyncAPI export    │
│  • Mongoose           │                     │                       │
│  • SQL migrations     │                     │                       │
└───────────────────────┴─────────────────────┴───────────────────────┘
```

## Configuration

Create a `docarchitect.yaml` in your project root:

```yaml
project:
  name: "My Microservices"
  version: "1.0.0"

repositories:
  # Single repo (mono-repo mode)
  - name: "monorepo"
    path: "."
    
  # Or multiple repos
  # - name: "user-service"
  #   path: "./services/user-service"
  # - name: "order-service"
  #   url: "https://github.com/org/order-service"
  #   branch: "main"

scanners:
  enabled:
    - dependencies
    - rest-api
    - graphql
    - kafka
    - database
  
generators:
  default: mermaid
  enabled:
    - mermaid
    - markdown

output:
  directory: "./docs/architecture"
  generateIndex: true
```

## Output

DocArchitect generates a complete documentation site:

```
docs/architecture/
├── index.md                    # Main entry point
├── overview/
│   ├── system-context.md       # C4 Level 1
│   └── container-diagram.md    # C4 Level 2
├── components/
│   └── [service-name].md       # Per-service documentation
├── dependencies/
│   ├── dependency-graph.md     # Visual dependency graph
│   └── dependency-matrix.md    # Tabular view
├── api/
│   ├── rest-endpoints.md       # REST API catalog
│   ├── graphql-schema.md       # GraphQL types and queries
│   └── grpc-services.md        # gRPC service definitions
├── data/
│   ├── er-diagram.md           # Entity relationship diagram
│   └── entity-catalog.md       # Entity documentation
├── messaging/
│   ├── kafka-topics.md         # Topic catalog
│   └── event-flows.md          # Message flow diagrams
└── integration/
    └── sokrates-scope.json     # Generated Sokrates config
```

## CI/CD Integration

DocArchitect supports lightweight CI/CD mode for detecting significant changes:

```yaml
# GitHub Actions example
- name: Check Architecture Changes
  run: |
    docker run -v $(pwd):/workspace doc-architect diff \
      --baseline docs/architecture/.baseline.json \
      --output docs/architecture \
      --fail-on-breaking-changes
```

For full CI/CD setup with security scanning, see [docs/ci-cd-setup.md](docs/ci-cd-setup.md).

## Code Quality Reports

[![Sokrates Analysis](https://img.shields.io/badge/Sokrates-View%20Report-blue)](https://emilholmegaard.github.io/doc-architect/latest/html/index.html)

Weekly automated code analysis is performed using [Sokrates](https://github.com/zeljkoobrenovic/sokrates), a polyglot source code examination tool.

- **[Latest Report](https://emilholmegaard.github.io/doc-architect/latest/html/index.html)** - Current week's comprehensive analysis
- **[Report Archive](https://emilholmegaard.github.io/doc-architect/archive/)** - Historical reports (last 4 weeks)

Reports include metrics on:

- Code volume and language breakdown
- Duplication analysis
- File/unit size distributions and conditional complexity
- Component decomposition and dependencies
- File age, change frequency, and contributor statistics
- Temporal trends and patterns

The analysis runs automatically every Monday at 2 AM UTC via GitHub Actions and publishes results to GitHub Pages.

## Extending DocArchitect

### Adding a Custom Scanner

1. Implement the `Scanner` interface
2. Register via `META-INF/services/com.docarchitect.core.scanner.Scanner`
3. Package as JAR and mount in Docker

See [docs/extending.md](docs/extending.md) for details.

## Development

```bash
# Build
./mvnw clean package

# Run tests
./mvnw test

# Build Docker image
docker build -t doc-architect .
```

See [docs/testing.md](docs/testing.md) for comprehensive testing guide.

## Logging Configuration

DocArchitect uses Logback for logging with the following defaults:

- **Log Level**: INFO (configurable via `LOGBACK_LEVEL` environment variable)
- **Output**: Console only
- **Package-specific levels**:
  - Scanners: `SCANNER_LOG_LEVEL` (default: INFO)
  - Generators: `GENERATOR_LOG_LEVEL` (default: INFO)
  - Renderers: `RENDERER_LOG_LEVEL` (default: INFO)

### Adjusting Log Levels

```bash
# Set global log level to DEBUG
docker run -e LOGBACK_LEVEL=DEBUG -v $(pwd):/workspace doc-architect scan

# Enable DEBUG logging for scanners only
docker run -e SCANNER_LOG_LEVEL=DEBUG -v $(pwd):/workspace doc-architect scan

# Maven: Set log level for tests
mvn test -Dlogback.level=DEBUG
```

### Custom Logback Configuration

For advanced logging needs, mount a custom `logback.xml`:

```bash
docker run -v $(pwd)/logback.xml:/app/logback.xml \
  -v $(pwd):/workspace \
  doc-architect scan
```

## License

MIT License - see [LICENSE](LICENSE)
