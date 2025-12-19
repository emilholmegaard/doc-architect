---
id: docker-usage
title: Docker Usage Guide
description: Guide for using DocArchitect with Docker for local development, testing, and CI/CD pipelines
tags:
  - docker
  - deployment
  - ci-cd
  - containerization
---

This guide explains how to use DocArchitect with Docker for local development, testing, and CI/CD pipelines.

## Quick Start

### Running DocArchitect with Docker

1. **Build the Docker image:**
   ```bash
   docker build -t doc-architect:local .
   ```

2. **Scan a project:**
   ```bash
   docker run --rm \
     -v /path/to/your/project:/workspace:ro \
     -v /path/to/output:/output \
     doc-architect:local scan /workspace --output /output
   ```

### Using Docker Compose

Docker Compose provides a complete environment for local development and testing with databases and message brokers.

1. **Start all services:**
   ```bash
   docker-compose up -d
   ```

2. **View logs:**
   ```bash
   docker-compose logs -f doc-architect
   ```

3. **Stop all services:**
   ```bash
   docker-compose down
   ```

4. **Clean up volumes:**
   ```bash
   docker-compose down -v
   ```

## Docker Compose Services

The `docker-compose.yml` includes the following services:

| Service | Purpose | Ports |
|---------|---------|-------|
| `doc-architect` | Main CLI application | N/A |
| `postgres` | PostgreSQL database for testing DB scanners | 5432 |
| `zookeeper` | Zookeeper for Kafka | 2181 |
| `kafka` | Kafka broker for testing message flow scanners | 9092 |

## Directory Structure

```
.
├── workspace/           # Mount your source code here (read-only)
├── output/             # Generated documentation will be saved here
└── docker-compose.yml  # Docker Compose configuration
```

## Environment Variables

Configure DocArchitect behavior with environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `LOGBACK_LEVEL` | Overall log level | `INFO` |
| `SCANNER_LOG_LEVEL` | Scanner-specific log level | `INFO` |
| `GENERATOR_LOG_LEVEL` | Generator-specific log level | `INFO` |
| `RENDERER_LOG_LEVEL` | Renderer-specific log level | `INFO` |

### Example with custom log levels:

```bash
docker run --rm \
  -e LOGBACK_LEVEL=DEBUG \
  -e SCANNER_LOG_LEVEL=TRACE \
  -v $(pwd):/workspace:ro \
  -v $(pwd)/output:/output \
  doc-architect:local scan /workspace --output /output
```

## Docker Image Details

### Multi-stage Build

The Dockerfile uses a multi-stage build to optimize image size:

1. **Build Stage**: Compiles the application using Maven and Eclipse Temurin JDK 21
2. **Runtime Stage**: Creates a minimal runtime image with JRE 21 and necessary tools (Git, Bash)

### Image Size

- Target size: **< 200MB**
- Base image: `eclipse-temurin:21-jre-alpine`
- Non-root user: `docarchitect`

### Security Features

- ✅ Non-root user (`docarchitect:docarchitect`)
- ✅ Minimal runtime dependencies (Alpine Linux)
- ✅ Health check configured
- ✅ Read-only workspace mount (best practice)
- ✅ Trivy security scanning in CI/CD

## CI/CD Integration

### GitHub Container Registry

Pull the latest image from GitHub Container Registry:

```bash
docker pull ghcr.io/emilholmegaard/doc-architect:latest
```

### Semantic Versioning

Images are tagged with:
- Branch names (e.g., `main`, `develop`)
- PR numbers (e.g., `pr-42`)
- Git SHA (e.g., `sha-a1b2c3d`)
- Semantic versions (e.g., `v1.0.0`)

### Example CI/CD Usage

```yaml
# .gitlab-ci.yml example
scan-architecture:
  image: ghcr.io/emilholmegaard/doc-architect:latest
  script:
    - java -jar /app/app.jar scan . --output ./architecture-docs
  artifacts:
    paths:
      - architecture-docs/
```

## Advanced Usage

### Custom Workspace and Output Directories

```bash
docker-compose run --rm \
  -v /custom/workspace:/workspace:ro \
  -v /custom/output:/output \
  doc-architect scan /workspace --output /output
```

### Interactive Shell

Debug or explore the container:

```bash
docker run --rm -it \
  -v $(pwd):/workspace:ro \
  --entrypoint /bin/bash \
  doc-architect:local
```

### Health Check

The Docker image includes a health check:

```bash
# Check container health
docker inspect --format='{{.State.Health.Status}}' doc-architect
```

## Troubleshooting

### Issue: Permission Denied

**Problem**: Cannot write to output directory

**Solution**: Ensure output directory is writable:
```bash
mkdir -p output
chmod 777 output  # Or set proper ownership
```

### Issue: Out of Memory

**Problem**: JVM runs out of memory on large projects

**Solution**: Increase Docker memory limit:
```bash
docker run --rm -m 4g \
  -v $(pwd):/workspace:ro \
  -v $(pwd)/output:/output \
  doc-architect:local scan /workspace --output /output
```

### Issue: Slow Performance

**Problem**: Scanning takes too long

**Solution**: Use CI mode for faster execution:
```bash
docker run --rm \
  -v $(pwd):/workspace:ro \
  -v $(pwd)/output:/output \
  doc-architect:local scan /workspace --output /output --ci-mode
```

## Performance Benchmarks

Expected performance on typical projects:

| Project Size | CI Mode | Full Scan |
|--------------|---------|-----------|
| Small (< 10 files) | < 5s | < 10s |
| Medium (10-100 files) | < 15s | < 30s |
| Large (100-1000 files) | < 30s | < 2min |
| Very Large (> 1000 files) | < 60s | < 5min |

## Support

For issues or questions:
- GitHub Issues: https://github.com/emilholmegaard/doc-architect/issues
- Documentation: https://emilholmegaard.github.io/doc-architect
