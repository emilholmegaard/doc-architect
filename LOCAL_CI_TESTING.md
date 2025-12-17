# Local CI Testing with Act

This guide explains how to test GitHub Actions workflows locally before pushing to GitHub.

## Prerequisites

### Install Act

**Windows (using Chocolatey):**
```bash
choco install act-cli
```

**Windows (using Scoop):**
```bash
scoop install act
```

**macOS:**
```bash
brew install act
```

**Linux:**
```bash
curl -s https://raw.githubusercontent.com/nektos/act/master/install.sh | sudo bash
```

### Install Docker Desktop

Act requires Docker to run containers. Download from [docker.com](https://www.docker.com/products/docker-desktop).

## Quick Start

### 1. Test the PR Workflow

Test what happens when you create a pull request:

```bash
# Test PR workflow (build + tests)
act pull_request

# Test PR workflow for specific event
act pull_request -e .github/workflows/test-event.json
```

### 2. Test the Build Workflow

Test the reusable build workflow:

```bash
# Test build workflow with tests enabled
act workflow_call -W .github/workflows/build.yml

# Test build workflow without tests
act workflow_call -W .github/workflows/build.yml -j build
```

### 3. Test the Docker Build

Test Docker image building (without pushing):

```bash
# Test docker build workflow
act workflow_call -W .github/workflows/docker-build.yml
```

### 4. Test TechDocs

Test documentation generation:

```bash
# Test techdocs on PR
act pull_request -W .github/workflows/techdocs.yml

# Test techdocs build job only
act pull_request -W .github/workflows/techdocs.yml -j build-techdocs
```

## Advanced Usage

### List Available Jobs

See all jobs that can be run:

```bash
# List jobs in PR workflow
act pull_request -l

# List all workflows
act -l
```

### Run Specific Jobs

Run only specific jobs from a workflow:

```bash
# Run only the build job
act pull_request -j build

# Run only the docker job
act pull_request -j docker
```

### Dry Run (Plan Mode)

See what would run without actually executing:

```bash
# Show what would run
act pull_request -n
```

### Use Secrets

For workflows requiring secrets (like GitHub tokens):

```bash
# Copy example secrets file
cp .github/workflows/act-secrets.example .secrets

# Edit .secrets and add your token
# Then run with secrets
act pull_request --secret-file .secrets
```

### Interactive Shell

Debug failed workflows by entering the container:

```bash
# Run and drop into shell on failure
act pull_request --shell
```

## Common Testing Scenarios

### Before Committing

Test your changes locally before pushing:

```bash
# 1. Test build and tests
act pull_request -j build

# 2. If tests pass, test docker build
act pull_request -j docker

# 3. Verify techdocs generation
act pull_request -W .github/workflows/techdocs.yml -j build-techdocs
```

### Debugging Failures

When a workflow fails in CI:

```bash
# 1. Pull the latest changes
git pull

# 2. Run the same workflow locally
act pull_request -v

# 3. Check logs for errors
# Fix the issue

# 4. Re-run to verify
act pull_request
```

### Testing Quality Gates

Verify that quality gates are enforced:

```bash
# Run build with all quality checks
act pull_request -j build

# This will verify:
# - JaCoCo coverage ≥60%
# - Checkstyle validation
# - SpotBugs static analysis
# - All unit tests pass
```

## Configuration

### .actrc File

The project includes an `.actrc` configuration file with sensible defaults:

```bash
# Use medium-sized container
-P ubuntu-latest=catthehacker/ubuntu:act-latest

# Container resource limits
--container-options="--cpus 2 --memory 4g"

# Verbose output
-v
```

You can override these by passing flags:

```bash
# Use different image
act pull_request -P ubuntu-latest=ubuntu:latest

# Less verbose
act pull_request --quiet
```

## Troubleshooting

### Act is Slow

Act downloads Docker images on first run. Subsequent runs are faster.

```bash
# Pre-download the image
docker pull catthehacker/ubuntu:act-latest
```

### Out of Memory

Increase Docker memory limits in `.actrc`:

```bash
--container-options="--cpus 4 --memory 8g"
```

### Workflow Not Found

Ensure you're in the repository root:

```bash
cd /path/to/doc-architect
act pull_request
```

### Docker Permission Denied

On Linux, add your user to the docker group:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

## Limitations

Act has some limitations compared to real GitHub Actions:

1. **No artifact persistence** - Artifacts are not saved between jobs
2. **No GitHub API** - Some GitHub-specific features may not work
3. **Different environment** - Container environment differs slightly from GitHub

## Quality Gates Enforced

When running locally with `act pull_request`, the following quality gates are verified:

### 1. Test Coverage (JaCoCo)
- **Minimum:** 60% line coverage per package
- **Command:** `mvn verify` (jacoco-check goal)
- **Report:** `target/site/jacoco/index.html`

### 2. Code Style (Checkstyle)
- **Standard:** Google Java Style Guide
- **Command:** `mvn checkstyle:check`
- **Config:** `google_checks.xml`

### 3. Static Analysis (SpotBugs)
- **Effort:** Max
- **Threshold:** Low
- **Command:** `mvn spotbugs:check`

### 4. Unit Tests
- **Framework:** JUnit 5 + AssertJ
- **Command:** `mvn test`
- **Required:** All tests must pass

## Continuous Integration Flow

The CI pipeline enforces these steps in order:

```
1. Compile    → mvn clean compile
2. Test       → mvn test (run all tests)
3. Verify     → mvn verify -DskipTests (quality gates)
4. Package    → mvn package -DskipTests
5. Docker     → Build image (if tests pass)
```

All steps must pass for the pipeline to succeed.

## Resources

- **Act Documentation:** https://nektosact.com/
- **GitHub Actions Docs:** https://docs.github.com/en/actions
- **Docker Documentation:** https://docs.docker.com/

## Support

If you encounter issues with local testing:

1. Check Act version: `act --version` (should be ≥0.2.40)
2. Verify Docker is running: `docker ps`
3. Clear Act cache: `rm -rf ~/.act`
4. Report issues at: https://github.com/emilholmegaard/doc-architect/issues
