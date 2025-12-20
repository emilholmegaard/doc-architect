# Real-World Project Testing

This guide explains how to test DocArchitect against real-world open source projects to validate scanner accuracy and output quality.

## Overview

DocArchitect includes test scripts for three production-grade open source projects representing different technology stacks:

| Project | Technology | Architecture | Key Features |
|---------|-----------|--------------|--------------|
| **PiggyMetrics** | Spring Boot | 7 microservices | REST APIs, MongoDB, RabbitMQ, Docker |
| **eShopOnWeb** | .NET Core | Clean Architecture | ASP.NET Core, EF Core, SQL Server |
| **Full-Stack FastAPI** | Python | API + Database | FastAPI, SQLAlchemy, PostgreSQL, Celery |

These tests validate that DocArchitect can accurately scan and document complex, multi-module production codebases.

## Quick Start

### Run All Tests

```bash
# Run all three tests
bash examples/run-all-tests.sh

# Validate outputs
bash examples/validate-outputs.sh
```

### Run Individual Tests

```bash
# Spring Boot microservices
bash examples/test-spring-microservices.sh

# .NET solution
bash examples/test-dotnet-solution.sh

# Python FastAPI
bash examples/test-python-fastapi.sh
```

## Test Projects

### 1. PiggyMetrics (Spring Boot)

**Repository:** [sqshq/piggymetrics](https://github.com/sqshq/piggymetrics)

**Architecture:**
- 7 Spring Boot microservices (account, statistics, notification, auth, gateway, registry, config)
- MongoDB databases
- RabbitMQ messaging
- Multi-module Maven build
- Docker Compose orchestration

**What DocArchitect Tests:**
- ✅ Maven dependency scanning across multiple modules
- ✅ Spring MVC REST endpoint detection (`@RestController`, `@RequestMapping`)
- ✅ JPA entity relationships (though project uses MongoDB)
- ✅ Service-to-service communication patterns
- ✅ Multi-module project structure

**Expected Output:**
- 7+ components (one per microservice)
- 20+ REST endpoints with paths and HTTP methods
- Service dependency graph showing gateway routing
- MongoDB entity documentation

**Success Criteria:**
```bash
# After running test
ls output/piggymetrics/
# Should contain:
#   - index.md (overview)
#   - dependencies/ (Maven dependencies)
#   - api/ (REST endpoints)
#   - architecture.md (Mermaid diagrams)
```

---

### 2. eShopOnWeb (.NET)

**Repository:** [dotnet-architecture/eShopOnWeb](https://github.com/dotnet-architecture/eShopOnWeb)

**Architecture:**
- ASP.NET Core Web API and Razor Pages
- Entity Framework Core with SQL Server
- Clean Architecture (Web, Infrastructure, ApplicationCore layers)
- SDK-style .csproj files
- Azure deployment ready

**What DocArchitect Tests:**
- ✅ NuGet dependency scanning (SDK-style .csproj parsing)
- ✅ ASP.NET Core API endpoints (`[HttpGet]`, `[HttpPost]`, route templates)
- ✅ EF Core entities from DbContext
- ✅ Project reference dependencies
- ✅ Clean Architecture layer validation

**Expected Output:**
- 5+ projects (Web, Infrastructure, ApplicationCore, PublicApi, UnitTests)
- 30+ NuGet dependencies
- 15+ API endpoints (Catalog, Basket, Order controllers)
- 20+ EF Core entities with relationships
- Architecture diagram showing layer dependencies

**Success Criteria:**
```bash
# After running test
ls output/eshopweb/
# Should contain:
#   - index.md (overview)
#   - dependencies/ (NuGet packages)
#   - api/ (ASP.NET Core endpoints)
#   - data/ (EF Core entities)
#   - architecture.md (Clean Architecture layers)
```

---

### 3. Full-Stack FastAPI (Python)

**Repository:** [tiangolo/full-stack-fastapi-postgresql](https://github.com/tiangolo/full-stack-fastapi-postgresql)

**Architecture:**
- FastAPI backend with async support
- SQLAlchemy ORM with PostgreSQL
- Celery for background tasks
- Docker Compose deployment
- Poetry for dependency management

**What DocArchitect Tests:**
- ✅ Poetry/pip dependency scanning (`pyproject.toml`, `requirements.txt`)
- ✅ FastAPI endpoint detection (`@app.get`, `@router.post`, path parameters)
- ✅ SQLAlchemy entity relationships (`relationship()`, foreign keys)
- ✅ Pydantic schema validation
- ✅ API versioning and router organization

**Expected Output:**
- 1 main component (FastAPI app)
- Dependencies from `pyproject.toml`/`requirements.txt`
- 20+ REST endpoints (users, items, login, etc.)
- 5+ SQLAlchemy entities
- API documentation with request/response schemas

**Success Criteria:**
```bash
# After running test
ls output/fastapi/
# Should contain:
#   - index.md (overview)
#   - dependencies/ (Python packages)
#   - api/ (FastAPI endpoints)
#   - data/ (SQLAlchemy models)
#   - architecture.md (API structure)
```

---

## Validation Process

The `validate-outputs.sh` script performs comprehensive quality checks:

### Validation Checks

1. **Structure Validation**
   - ✅ Output directory exists
   - ✅ `index.md` present
   - ✅ `dependencies/`, `api/`, `data/` directories created

2. **Content Validation**
   - ✅ Endpoint count meets minimum threshold
   - ✅ Entity count meets minimum threshold
   - ✅ Mermaid diagrams generated
   - ✅ No empty files

3. **Quality Validation**
   - ✅ Readable documentation format
   - ✅ Valid Mermaid syntax
   - ✅ Proper markdown structure

### Validation Output

```bash
$ bash examples/validate-outputs.sh

==========================================
Validating: PiggyMetrics (Spring Boot)
==========================================
✓ Output directory exists
✓ index.md exists
✓ dependencies/ directory exists
  Found 15 dependency files
✓ api/ directory exists
  Found approximately 25 endpoint references
✓ Endpoint count (25) meets minimum (10)
✓ data/ directory exists
  Found approximately 12 entity references
✓ Entity count (12) meets minimum (5)
✓ Found 8 Mermaid diagrams

✓ PiggyMetrics validation complete

==========================================
Validation Summary
==========================================
Total checks: 24
Passed: 24
Failed: 0

✓ All validations passed!
```

---

## CI/CD Integration

### GitHub Actions Workflow

Real-world tests run automatically on a weekly schedule (every Monday at 6 AM UTC):

```yaml
# .github/workflows/real-world-tests.yml
name: Real-World Project Tests

on:
  schedule:
    - cron: '0 6 * * 1'  # Weekly on Monday
  workflow_dispatch:     # Manual trigger
```

**Workflow Features:**
- ✅ Parallel execution (all 3 projects run simultaneously)
- ✅ 10-minute timeout per test
- ✅ Artifact upload (30-day retention)
- ✅ Comprehensive validation report
- ✅ GitHub Summary with test results

### Manual Trigger

Trigger tests manually via GitHub Actions UI:

1. Go to **Actions** tab
2. Select **Real-World Project Tests**
3. Click **Run workflow**
4. Choose projects to test: `all`, `spring`, `dotnet`, `python`, or combinations

### Viewing Results

After workflow completion:

1. **Artifacts:** Download generated documentation from the **Artifacts** section
2. **Summary:** View test results in the workflow run summary
3. **Logs:** Check individual job logs for detailed output

---

## Expected Performance

| Metric | Target | Typical |
|--------|--------|---------|
| **Scan Time (PiggyMetrics)** | < 90 seconds | ~60 seconds |
| **Scan Time (eShopOnWeb)** | < 90 seconds | ~55 seconds |
| **Scan Time (FastAPI)** | < 60 seconds | ~40 seconds |
| **Total Test Suite** | < 5 minutes | ~3 minutes |
| **Memory Usage** | < 512 MB | ~300 MB |

---

## Success Metrics

### Minimum Thresholds

| Project | Min Components | Min Endpoints | Min Entities |
|---------|---------------|---------------|--------------|
| PiggyMetrics | 7 | 10 | 5 |
| eShopOnWeb | 5 | 10 | 15 |
| FastAPI | 1 | 15 | 3 |

### Quality Criteria

- ✅ All tests complete without errors
- ✅ Generated documentation is human-readable
- ✅ Mermaid diagrams render correctly in GitHub/GitLab
- ✅ Entity relationships are correctly identified
- ✅ No major components are missed
- ✅ Scan completes within performance targets

---

## Troubleshooting

### Common Issues

#### 1. Docker Image Not Found

**Error:**
```
Error response from daemon: manifest for ghcr.io/emilholmegaard/doc-architect:latest not found
```

**Solution:**
```bash
# Build local image
docker build -t doc-architect:local .

# Update scripts to use local tag
sed -i 's|ghcr.io/emilholmegaard/doc-architect:latest|doc-architect:local|g' examples/*.sh
```

#### 2. Git Clone Timeout

**Error:**
```
fatal: unable to access 'https://github.com/...': Operation timed out
```

**Solution:**
```bash
# Increase Git timeout
git config --global http.postBuffer 524288000
git config --global http.lowSpeedLimit 0
git config --global http.lowSpeedTime 999999
```

#### 3. Validation Fails (Low Endpoint Count)

**Warning:**
```
⚠ WARN: Endpoint count (5) below minimum (10)
```

**Diagnosis:**
```bash
# Check scanner logs
grep -r "endpoint\|@.*Mapping" output/*/api/

# Verify scanners ran
grep "Scanner.*enabled" test-projects/*/docarchitect.yaml
```

#### 4. Missing Output Directories

**Error:**
```
✗ FAIL: Output directory missing: output/piggymetrics
```

**Solution:**
```bash
# Check Docker volume mounts
docker run --rm \
  -v "$(pwd)/test-projects/piggymetrics:/workspace:ro" \
  -v "$(pwd)/output/piggymetrics:/output" \
  doc-architect:local scan --verbose /workspace
```

---

## Extending Tests

### Adding New Test Projects

1. **Create test script** (`examples/test-new-project.sh`):

```bash
#!/bin/bash
set -e

PROJECT_DIR="test-projects/new-project"
echo "Cloning new-project..."
git clone --depth 1 https://github.com/org/new-project.git "$PROJECT_DIR"

cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "New Project"
  version: "1.0.0"
scanners:
  enabled:
    - maven-dependencies
    - spring-mvc-api
EOF

docker run --rm \
  -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
  -v "$(pwd)/output/new-project:/output" \
  ghcr.io/emilholmegaard/doc-architect:latest \
  scan --config /workspace/docarchitect.yaml --output /output
```

2. **Update master runner** (`examples/run-all-tests.sh`):

```bash
run_test "New Project" "./examples/test-new-project.sh"
```

3. **Update validation** (`examples/validate-outputs.sh`):

```bash
validate_output "New Project" "output/new-project" 5 3
```

4. **Add CI job** (`.github/workflows/real-world-tests.yml`):

```yaml
test-new-project:
  name: New Project
  runs-on: ubuntu-latest
  steps:
    # ... (follow existing pattern)
```

---

## Known Limitations

### Current Limitations (as of Phase 10)

1. **GraphQL Schema Parsing**
   - Limited to basic SDL detection
   - Complex directives not fully parsed
   - Consider migrating to `graphql-java` library

2. **MongoDB Entity Detection**
   - Relies on JPA annotations (not native MongoDB annotations)
   - May miss MongoDB-specific features (embedded documents, etc.)

3. **RabbitMQ vs Kafka**
   - PiggyMetrics uses RabbitMQ, but scanner expects Kafka
   - Message flow detection may be incomplete

4. **Multi-Language Monorepos**
   - Projects with mixed tech stacks (e.g., Java + Python) not fully tested
   - Scanner priority ordering may need tuning

### Future Enhancements

- [ ] Add Ruby on Rails test project
- [ ] Add Go microservices test project
- [ ] Add Rust project with Cargo
- [ ] Test multi-language monorepos
- [ ] Performance benchmarking suite
- [ ] Memory profiling during scans

---

## Support

For issues with real-world testing:

1. **Check CI Logs:** Review GitHub Actions workflow logs
2. **Run Locally:** Test scripts locally with `--verbose` flag
3. **Validate Manually:** Inspect generated output files
4. **Report Issues:** Open GitHub issue with:
   - Test project name
   - Error logs
   - Expected vs actual output
   - DocArchitect version

---

**Last Updated:** 2025-12-20
**Phase:** 10 - Real-World Open Source Project Testing
**Status:** ✅ Complete
