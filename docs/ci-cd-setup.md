# CI/CD and Security Infrastructure

## Overview

This project now includes comprehensive CI/CD pipelines with Docker security scanning and test infrastructure.

## What Was Added

### 1. GitHub Actions Workflows

Workflows are modular and composable - use simple workflows for PRs, comprehensive for nightly builds.

#### Reusable Workflows
- **[build.yml](../.github/workflows/build.yml)** - Build and test application
- **[docker-build.yml](../.github/workflows/docker-build.yml)** - Build Docker images
- **[docker-security-scan.yml](../.github/workflows/docker-security-scan.yml)** - Security scanning

#### Main Workflows
- **[pr.yml](../.github/workflows/pr.yml)** - Fast PR checks (build + Docker build only)
- **[merge.yml](../.github/workflows/merge.yml)** - On merge: build, push Docker, security scan
- **[nightly.yml](../.github/workflows/nightly.yml)** - Comprehensive nightly checks:
  - Integration tests
  - Code quality analysis
  - Full security scanning (Trivy, Grype, CodeQL)
  - OWASP dependency check
  - SBOM generation
  - Automated vulnerability alerting

### 2. Dependency Management

#### [.github/dependabot.yml](.github/dependabot.yml)
Automated dependency updates:
- Maven dependencies (weekly)
- Docker base images (weekly)
- GitHub Actions (weekly)

### 3. Build Configuration

#### [pom.xml](pom.xml)
Maven configuration with:
- JUnit 5 for testing
- Mockito for mocking
- AssertJ for fluent assertions
- JaCoCo for code coverage (60% minimum)
- Checkstyle for code formatting
- SpotBugs for static analysis
- OWASP Dependency Check

#### [Dockerfile](Dockerfile)
Multi-stage Docker build:
- Stage 1: Maven build (compile and package)
- Stage 2: Lightweight runtime image
- Non-root user for security
- Health checks included
- Alpine-based for minimal footprint

### 4. Test Infrastructure

#### [src/test/java/com/docarchitect/](src/test/java/com/docarchitect/)
Test structure ready for implementation:
- `ExampleTest.java` - Unit test template
- `ExampleIntegrationTest.java` - Integration test template

#### [testing.md](testing.md)
Complete testing guide with:
- Test structure and conventions
- Running tests locally
- CI/CD integration details
- Best practices
- Coverage goals

### 5. Security Configuration

#### [.github/dependency-check-suppressions.xml](.github/dependency-check-suppressions.xml)
OWASP dependency check suppressions for false positives

#### [.dockerignore](.dockerignore)
Optimized Docker build context exclusions

### 6. Basic Application

#### [src/main/java/com/docarchitect/DocArchitectCLI.java](src/main/java/com/docarchitect/DocArchitectCLI.java)
Minimal Picocli-based CLI entry point

## Security Features

### Docker Image Scanning
- **Trivy**: Comprehensive vulnerability scanning
- **Grype**: Additional security analysis
- **Docker Scout**: CVE tracking and recommendations
- **SBOM**: Software Bill of Materials generation

### Dependency Scanning
- **OWASP Dependency Check**: Identifies vulnerable dependencies
- **Dependabot**: Automated security updates

### Code Analysis
- **CodeQL**: Security-focused code analysis
- **SpotBugs**: Bug pattern detection
- **Checkstyle**: Code quality enforcement

## How to Use

### Local Development

1. **Run tests**:
   ```bash
   ./mvnw test
   ```

2. **Build Docker image**:
   ```bash
   docker build -t doc-architect .
   ```

3. **Run security scan locally**:
   ```bash
   docker run --rm aquasec/trivy image doc-architect
   ```

### CI/CD Pipeline

Workflows run based on event:
- **PR**: Fast build and Docker build checks only
- **Merge**: Build, push Docker image, security scan
- **Nightly**: Full comprehensive suite with all checks
- **Manual**: Can trigger nightly workflow on-demand

### Security Monitoring

- View security results in the **Security** tab
- Critical vulnerabilities trigger automatic issues
- Daily scans keep you informed of new threats

## Next Steps

1. **Generate Maven Wrapper**:
   ```bash
   mvn wrapper:wrapper
   ```

2. **Implement actual scanner logic** in `src/main/java/`

3. **Add real tests** replacing the example tests

4. **Configure GitHub secrets** (if needed for private registries)

5. **Customize security thresholds** in workflows as needed

6. **Review and customize** Checkstyle and SpotBugs rules

## Badge Integration

Add to your README.md:

```markdown
![PR Check](https://github.com/YOUR_ORG/doc-architect/workflows/PR%20Check/badge.svg)
![Nightly Build](https://github.com/YOUR_ORG/doc-architect/workflows/Nightly%20Build/badge.svg)
```

## Notes

- Tests are set to `continue-on-error: true` since actual tests don't exist yet
- Remove this flag once real tests are implemented
- Workflows are modular - easy to customize or extend
- PR workflow is kept minimal for fast feedback
- Nightly workflow runs comprehensive checks
- Pipelines configured for GitHub Container Registry (ghcr.io)
- Adjust Docker image paths if using different registries
