# DocArchitect Real-World Testing Examples

This directory contains test scripts that validate DocArchitect against production-grade open source projects.

## Scripts

| Script | Purpose | Runtime |
|--------|---------|---------|
| `test-spring-microservices.sh` | Tests Spring Boot microservices (PiggyMetrics) | ~60s |
| `test-dotnet-solution.sh` | Tests .NET Clean Architecture (eShopOnWeb) | ~55s |
| `test-python-fastapi.sh` | Tests Python FastAPI (Full-Stack) | ~40s |
| `run-all-tests.sh` | Executes all tests sequentially | ~3m |
| `validate-outputs.sh` | Validates generated documentation quality | ~10s |

## Quick Start

```bash
# Run all tests
./examples/run-all-tests.sh

# Or run individually
./examples/test-spring-microservices.sh
./examples/test-dotnet-solution.sh
./examples/test-python-fastapi.sh

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

## Output Structure

After running tests, outputs are in `output/` directory:

```
output/
├── piggymetrics/
│   ├── index.md
│   ├── dependencies/
│   ├── api/
│   └── data/
├── eshopweb/
│   ├── index.md
│   ├── dependencies/
│   ├── api/
│   └── data/
└── fastapi/
    ├── index.md
    ├── dependencies/
    ├── api/
    └── data/
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
