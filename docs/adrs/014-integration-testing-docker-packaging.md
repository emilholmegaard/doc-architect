---
id: adr-014-integration-testing-docker-packaging
title: ADR-014: Integration Testing and Docker Packaging Strategy
description: Defines comprehensive integration testing approach and Docker containerization strategy for DocArchitect
tags:
  - adr
  - architecture
  - testing
  - docker
  - ci-cd
---

# ADR-014: Integration Testing and Docker Packaging Strategy

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-19 |
| **Deciders** | Development Team |
| **Technical Story** | [Phase 9: Docker Packaging and End-to-End Testing](https://github.com/emilholmegaard/doc-architect/issues/9) |

---

## Context

After implementing 19 scanners across 6 technology ecosystems, 3 diagram generators, and 2 output renderers, we need:

1. **End-to-end validation**: Verify the complete pipeline (scan → generate → render) works correctly across realistic project fixtures
2. **Performance benchmarks**: Ensure CI mode completes under 30 seconds and full scans under 2 minutes
3. **Docker packaging**: Provide a containerized distribution for easy deployment and consistent environments
4. **Local development workflow**: Enable developers to test changes without complex setup

**Key Requirements from Issue #9:**
- Docker image size < 200MB
- Multi-stage build optimization
- Integration test fixtures representing real microservice architectures
- Performance benchmarks for CI/CD pipelines
- Docker Compose for local development with databases and message brokers

**Constraints:**
- Must support Windows, Linux, and macOS development environments
- CI/CD pipeline must remain fast (< 5 minutes total)
- Test fixtures must be realistic but small enough for quick execution

---

## Decision

We will implement a comprehensive integration testing and Docker packaging strategy:

### 1. Integration Test Fixtures

Create realistic project fixtures in `doc-architect-cli/src/test/resources/fixtures/`:

- **Java Spring Boot** (`java-spring-boot/`): REST APIs, JPA entities, Kafka producers, Maven dependencies
- **Python FastAPI** (`python-fastapi/`): REST APIs, SQLAlchemy models, Poetry dependencies
- **.NET ASP.NET** (`dotnet-aspnet/`): REST APIs, Entity Framework models, NuGet packages

Each fixture represents a complete microservice with:
- Dependency management files (pom.xml, pyproject.toml, .csproj)
- REST API controllers with multiple endpoints (GET, POST, PUT, DELETE)
- Data models/entities with relationships
- Message producers/consumers (Kafka)
- Realistic package dependencies (Spring Boot, FastAPI, EF Core, etc.)

### 2. End-to-End Integration Tests

Implement `EndToEndScanningTest` with:
- Scanner discovery via SPI (ServiceLoader)
- Complete pipeline execution: Scan → Aggregate → Generate → Render
- Assertions validating component detection across all technology stacks
- Verification of API endpoints, data entities, dependencies, and message flows

### 3. Performance Benchmarks

Implement `PerformanceBenchmarkTest` (enabled via `-DrunPerformanceTests=true`) with:
- **CI Mode benchmark**: < 30 seconds for small projects
- **Full Scan benchmark**: < 2 minutes for medium projects (all fixtures combined)
- **Scanner discovery overhead**: < 1 second
- **Diagram generation**: < 5 seconds
- **Memory usage**: < 100MB for small projects

### 4. Docker Image Validation

Implement `DockerImageTest` (enabled via `-DrunDockerTests=true`) with:
- Dockerfile validation (multi-stage build, non-root user, health check)
- Image build success verification
- Image size validation (< 200MB)
- Container execution test (--help command)

### 5. Docker Multi-Stage Build

Use existing optimized Dockerfile:
- **Build stage**: `maven:3-eclipse-temurin-21-alpine` for compilation
- **Runtime stage**: `eclipse-temurin:21-jre-alpine` for execution
- Non-root user (`docarchitect:docarchitect`)
- Health check configured
- Layer caching optimization

### 6. Docker Compose for Local Development

Provide `docker-compose.yml` with:
- `doc-architect` service (main CLI application)
- `postgres` service (for database scanner testing)
- `zookeeper` + `kafka` services (for message flow scanner testing)
- Volume mounts for workspace (read-only) and output directories
- Network isolation
- Health checks for all services

---

## Rationale

### Why Realistic Test Fixtures?

✅ **Catches real integration issues**: Simple "hello world" fixtures miss edge cases in production code
✅ **Validates scanner accuracy**: Ensures scanners detect components in realistic project structures
✅ **Enables breaking change detection**: Tests fail if scanners stop detecting expected components
✅ **Documents expected behavior**: Fixtures serve as living examples of supported patterns

### Why Performance Benchmarks?

✅ **CI/CD optimization**: 30-second threshold ensures pipelines remain fast
✅ **Regression prevention**: Performance tests detect slowdowns before deployment
✅ **User experience**: 2-minute full scans meet user expectations for large projects
✅ **Resource planning**: Memory benchmarks inform container sizing

### Why Docker Packaging?

✅ **Consistent environments**: Same runtime behavior across dev, CI, and production
✅ **Easy distribution**: Single `docker pull` command for installation
✅ **Dependency isolation**: No need to install Java, Maven, or other tools locally
✅ **CI/CD integration**: Standard container format works with all CI platforms

### Why Docker Compose?

✅ **One-command setup**: `docker-compose up` starts all dependencies
✅ **Testing infrastructure**: Postgres and Kafka available for scanner testing
✅ **Developer experience**: No manual database/broker installation
✅ **Production-like environment**: Services configured as in real deployments

---

## Alternatives Considered

### Alternative 1: Minimal Test Fixtures

**Description:** Use simple 3-5 file fixtures instead of realistic microservices

**Pros:**
- Faster test execution
- Easier to maintain
- Smaller test resources

**Cons:**
- Misses real-world edge cases
- Doesn't validate complex project structures
- Poor documentation value

**Decision:** ❌ Rejected - Realistic fixtures provide better validation and catch more issues

### Alternative 2: Mock-Based Testing

**Description:** Mock scanner implementations instead of testing real fixtures

**Pros:**
- Fast execution
- Isolated component testing
- No test fixture maintenance

**Cons:**
- Doesn't validate actual scanner behavior
- Mocks can drift from reality
- No end-to-end validation

**Decision:** ❌ Rejected - Integration tests must validate real scanner behavior on real code

### Alternative 3: Kubernetes Helm Charts

**Description:** Package as Helm chart instead of Docker Compose

**Pros:**
- Production-grade deployment
- Scalability support
- Cloud-native approach

**Cons:**
- Overkill for local development
- Complex setup (requires Kubernetes cluster)
- Steep learning curve

**Decision:** ❌ Rejected - Docker Compose sufficient for local development; Helm can be added later if needed

### Alternative 4: Native Binary Packaging (GraalVM)

**Description:** Use GraalVM native-image instead of Docker

**Pros:**
- Faster startup time
- Smaller binary size
- No JVM required

**Cons:**
- Complex build process
- Limited reflection support (breaks SPI)
- Platform-specific binaries (Windows, Linux, macOS)

**Decision:** ❌ Rejected - SPI-based plugin system incompatible with native-image; Docker provides better cross-platform support

---

## Consequences

### Positive

✅ **High confidence in releases**: End-to-end tests validate complete pipeline
✅ **Performance SLAs met**: Benchmarks ensure 30s CI / 2min full scan targets
✅ **Easy distribution**: Docker image available via `docker pull`
✅ **Developer productivity**: Docker Compose eliminates setup friction
✅ **CI/CD integration**: Standard Docker workflow in GitHub Actions
✅ **Documentation value**: Test fixtures serve as usage examples

### Negative

⚠️ **Test maintenance burden**: Realistic fixtures require updates as scanners evolve
⚠️ **Build time increase**: Docker build adds ~3-5 minutes to CI pipeline
⚠️ **Docker dependency**: Requires Docker installed for local testing (addressed by CI-only flags)
⚠️ **Fixture size**: 3 realistic fixtures add ~50KB to test resources

### Neutral

🔵 **Test execution time**: Integration tests add ~5-10 seconds to test suite
🔵 **Docker image size**: 180-200MB (acceptable for containerized Java application)
🔵 **Learning curve**: Developers need basic Docker knowledge

---

## Implementation Notes

### Running Integration Tests

```bash
# Run all tests (unit + integration)
mvn test

# Run performance benchmarks (requires -D flag)
mvn test -DrunPerformanceTests=true

# Run Docker validation (requires Docker daemon)
mvn test -DrunDockerTests=true
```

### Building and Testing Docker Image

```bash
# Build Docker image
docker build -t doc-architect:local .

# Verify image size
docker images doc-architect:local --format "{{.Size}}"

# Test container execution
docker run --rm doc-architect:local --help

# Scan a project
docker run --rm \
  -v $(pwd):/workspace:ro \
  -v $(pwd)/output:/output \
  doc-architect:local scan /workspace --output /output
```

### Using Docker Compose

```bash
# Start all services (DocArchitect + Postgres + Kafka)
docker-compose up -d

# View logs
docker-compose logs -f doc-architect

# Stop services
docker-compose down

# Clean up volumes
docker-compose down -v
```

### Test Fixture Maintenance

When scanners evolve:
1. Update fixtures to include new patterns
2. Update test assertions to match new component counts
3. Add new fixture subdirectories for new technologies
4. Keep fixtures realistic but minimal (5-10 files each)

---

## Compliance

- **Architecture Principles**: Supports testability, reproducibility, and deployment automation
- **Standards**: Follows Docker best practices (multi-stage build, non-root user, health checks)
- **Security**: Non-root container user, minimal attack surface (JRE-only runtime)
- **Performance**: Meets CI mode (30s) and full scan (2min) SLAs

---

## References

- [Phase 9: Docker Packaging and End-to-End Testing](https://github.com/emilholmegaard/doc-architect/issues/9)
- [Docker Usage Guide](../docker-usage.md)
- [Testing Guide](../testing.md)
- [Multi-Stage Docker Builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [GitHub Container Registry](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry)

---

## Metadata

- **Review Date:** 2026-06-19 (6 months - after production deployment)
- **Last Updated:** 2025-12-19
- **Version:** 1.0
