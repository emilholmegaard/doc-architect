# DocArchitect Real-World Testing Examples

This directory contains test scripts that validate DocArchitect against production-grade open source projects.

## Scripts

| Script | Purpose | Runtime |
| ------ | ------- | ------- |
| `test-spring-microservices.sh` | Tests Spring Boot microservices (PiggyMetrics) | ~60s |
| `test-dotnet-solution.sh` | Tests .NET Clean Architecture (eShopOnWeb) | ~55s |
| `test-python-fastapi.sh` | Tests Python FastAPI (Full-Stack) | ~40s |
| `test-python-saleor.sh` | Tests Python GraphQL e-commerce (Saleor) | ~90s |
| `test-java-keycloak.sh` | Tests Java identity management (Keycloak) | ~120s |
| `test-dotnet-eshoponcontainers.sh` | Tests .NET microservices reference (eShopOnContainers) | ~100s |
| `test-go-gitea.sh` | Tests Go Git service (Gitea) | ~80s |
| `test-go-linkerd2.sh` | Tests Go service mesh (Linkerd2) | ~90s |
| `test-dotnet-umbraco.sh` | Tests .NET CMS (Umbraco) | ~110s |
| `test-java-druid.sh` | Tests Java distributed database (Apache Druid) | ~130s |
| `run-all-tests.sh` | Executes all tests sequentially | ~15m |
| `validate-outputs.sh` | Validates generated documentation quality | ~30s |

## Quick Start

```bash
# Run all tests
./examples/run-all-tests.sh

# Or run individually
./examples/test-spring-microservices.sh
./examples/test-dotnet-solution.sh
./examples/test-python-fastapi.sh
./examples/test-python-saleor.sh
./examples/test-java-keycloak.sh
./examples/test-dotnet-eshoponcontainers.sh
./examples/test-go-gitea.sh
./examples/test-go-linkerd2.sh
./examples/test-dotnet-umbraco.sh
./examples/test-java-druid.sh

# Validate outputs
./examples/validate-outputs.sh
```

## Prerequisites

- **Docker** (for running DocArchitect image)
- **Git** (for cloning test projects)
- **Bash** (scripts are bash-compatible)

## What Gets Tested

### Spring Boot (PiggyMetrics)

- 7 microservices with REST APIs
- Maven multi-module project
- MongoDB entities
- RabbitMQ messaging
- Service discovery and configuration

### .NET (eShopOnWeb)

- ASP.NET Core Web API
- Entity Framework Core
- Clean Architecture pattern
- SDK-style .csproj files
- NuGet package management

### Python (Full-Stack FastAPI)

- FastAPI async endpoints
- SQLAlchemy ORM
- Poetry dependency management
- Pydantic schemas
- Celery background tasks

### Python GraphQL (Saleor)

- GraphQL schema and operations
- Django ORM models (100+ entities)
- Poetry dependency management
- E-commerce domain (products, orders, users)
- Tests GraphQLScanner and DjangoORMScanner

### Java Identity Management (Keycloak)

- Large Maven multi-module project (50+ modules)
- Extensive JPA entities (150+)
- Complex REST API (100+ endpoints)
- Quarkus framework
- Identity and access management domain

### .NET Microservices (eShopOnContainers)

- Reference microservices architecture (10+ services)
- gRPC and REST APIs (50+ endpoints)
- Entity Framework Core (30+ entities)
- Inter-service communication patterns
- Clean Architecture in microservices context

### Go Git Service (Gitea)

- Go module dependencies (100+)
- Monolithic Go application
- Git repository management
- REST API endpoints
- Tests Go dependency scanner

### Go Service Mesh (Linkerd2)

- Microservices architecture in Go
- gRPC service definitions
- Protobuf schemas
- Kubernetes integration
- Tests Go and Protobuf scanners

### .NET CMS (Umbraco)

- Large .NET CMS platform (20+ modules)
- Extensive ASP.NET Core APIs (100+)
- Complex EF Core schema (50+ entities)
- Content management domain
- Tests mature .NET codebase patterns

### Java Distributed Database (Apache Druid)

- Massive Maven multi-module project (100+ modules)
- Extensive Maven dependencies (200+)
- Distributed systems architecture
- REST APIs for query and ingestion
- Tests scalability of scanners on large projects

## Output Structure

After running tests, outputs are in `output/` directory:

```text
output/
├── piggymetrics/       # Spring Boot Microservices
├── eshopweb/          # .NET Clean Architecture
├── fastapi/           # Python FastAPI
├── saleor/            # Python GraphQL
├── keycloak/          # Java Identity Management
├── eshoponcontainers/ # .NET Microservices
├── gitea/             # Go Git Service
├── linkerd2/          # Go Service Mesh
├── umbraco/           # .NET CMS
└── druid/             # Java Distributed Database

Each project output contains:
├── index.md           # Navigation and summary
├── dependencies/      # Dependency graphs
├── api/               # API documentation
└── data/              # Entity diagrams
```

## Success Criteria

All tests pass when:

- ✅ All scripts complete without errors
- ✅ Output directories contain expected files
- ✅ Minimum component/endpoint/entity counts met
- ✅ Mermaid diagrams generated
- ✅ Documentation is human-readable

## CI/CD Integration

These scripts run automatically every Monday via GitHub Actions:

```bash
# Trigger manually
gh workflow run real-world-tests.yml

# Check status
gh run list --workflow=real-world-tests.yml
```

## Troubleshooting

**Docker image not found:**

```bash
docker build -t doc-architect:local .
# Update scripts to use local tag
```

**Git clone fails:**

```bash
# Clear and retry
rm -rf test-projects/
./examples/test-spring-microservices.sh
```

**Validation fails:**

```bash
# Check generated files
ls -la output/*/
cat output/*/index.md
```

## Documentation

See [Real-World Testing Guide](../docs/real-world-testing.md) for comprehensive documentation.

---

**Note:** These tests clone real-world projects (~100-500 MB each). Ensure you have adequate disk space and network bandwidth.
