---
# Backstage TechDocs metadata
id: adr-015-real-world-scanner-validation
title: ADR-015: Real-World Scanner Validation Findings and Remediation Plan
description: Analysis of scanner effectiveness against production OSS projects and 3-week remediation plan
tags:
  - adr
  - architecture
  - testing
  - scanners
  - quality-assurance
---

# ADR-015: Real-World Scanner Validation Findings and Remediation Plan

## Status

Accepted

## Context

After completing Phase 10 (Docker packaging and integration testing), we executed DocArchitect against three production-grade open-source projects to validate scanner effectiveness in real-world scenarios:

1. **PiggyMetrics** - 7 Spring Boot microservices with MongoDB, Spring Cloud Netflix stack
2. **eShopOnWeb** - .NET Clean Architecture with EF Core, ASP.NET Core Web API, 20+ domain entities
3. **Full-Stack FastAPI** - Python FastAPI backend with SQLModel/SQLAlchemy ORM

The validation revealed critical gaps in scanner effectiveness that were not caught by our existing test suite or validation scripts.

### Scanner Performance Results

| Scanner | Test Project | Expected | Actual | Detection Rate |
|---------|-------------|----------|--------|----------------|
| NuGetDependencyScanner | eShopOnWeb | 30+ packages | 0 | **0%** ❌ |
| FastAPIScanner | FastAPI | 20+ endpoints | 0 | **0%** ❌ |
| SQLAlchemyScanner | FastAPI | 2 tables | 11 wrong entities | **-450%** ❌ |
| AspNetCoreApiScanner | eShopOnWeb | 15+ endpoints | 1 | **7%** ⚠️ |
| EntityFrameworkScanner | eShopOnWeb | 7 entities, 1 PK each | 7 entities, all fields PK | **Incorrect** ⚠️ |
| SpringRestApiScanner | PiggyMetrics | 20+ endpoints | 11 | **55%** ⚠️ |
| JpaEntityScanner | PiggyMetrics | 5 MongoDB entities | 0 | **0%** ⚠️ |
| MavenDependencyScanner | PiggyMetrics | 24 dependencies | 24 | **100%** ✅ |
| PipPoetryDependencyScanner | FastAPI | 59 dependencies | 59 | **100%** ✅ |

### Root Causes Identified

1. **Technology Variant Gaps**: Scanners don't handle production best practices
   - FastAPI uses `APIRouter()` with `@router.get()`, not `@app.get()`
   - Spring Data MongoDB uses `@Document`, not JPA `@Entity`
   - SQLModel marks entities with `table=True` parameter
   - Modern .NET uses SDK-style `.csproj` files

2. **Schema vs. Entity Confusion**: ORM scanners don't distinguish between database tables and API DTOs
   - SQLAlchemy scanner treats Pydantic schemas as database tables
   - Results in ER diagrams mixing API request/response models with actual tables

3. **Field Metadata Extraction Bugs**: Entity field detection logic broken
   - EntityFramework scanner marks ALL fields as primary keys
   - No distinction between `[Key]` attributes and regular fields

4. **Validation Script Gap**: Existing script only checked structure, not content
   - Checked "directory exists" but not "scanner found expected results"
   - Allowed critical bugs to pass validation despite 0% detection rates

## Decision

We will implement a three-phase remediation plan to fix critical scanner bugs and prevent future regressions.

### Phase 1: Critical Scanner Fixes (Week 1)

**Priority:** Fix scanners with 0% detection rate (GitHub issues #60-#62)

1. **NuGetDependencyScanner** (#60)
   - Debug file discovery pattern for `.csproj` files
   - Verify Jackson XML extracts `<PackageReference>` elements
   - Handle SDK-style projects and central package management
   - **Acceptance:** Find 15+ packages in eShopOnWeb

2. **FastAPIScanner** (#61)
   - Add debug logging to identify failure point
   - Support both `@app.METHOD()` and `@router.METHOD()` patterns
   - Test against actual FastAPI files from test project
   - **Acceptance:** Find 15+ endpoints in FastAPI test project

3. **SQLAlchemyScanner** (#62)
   - Filter classes: only detect with `table=True` parameter
   - Ignore Pydantic schema classes (API DTOs)
   - Parse `Field(foreign_key="...")` for relationships
   - **Acceptance:** Find 2 entities (not 11) in FastAPI test project

### Phase 2: Partial Failure Fixes (Week 2)

**Priority:** Fix scanners with <60% detection or incorrect metadata (GitHub issues #63-#65)

4. **AspNetCoreApiScanner** (#63)
   - Debug .NET AST parser to identify skipped controllers
   - Compare working vs. non-working controller patterns
   - Fix AST parsing or regex patterns
   - **Acceptance:** Find 15+ endpoints in eShopOnWeb

5. **EntityFrameworkScanner** (#64)
   - Only mark fields with `[Key]` attribute or "Id" convention as PK
   - Extract field types and nullability correctly
   - Handle composite keys
   - **Acceptance:** ER diagrams show 1 PK per entity (not all fields)

6. **MongoDB Support** (#65)
   - Create `MongoEntityScanner` extending `AbstractJavaParserScanner`
   - Detect `@Document` annotation (Spring Data MongoDB)
   - Extract collection name from annotation
   - Register in SPI
   - **Acceptance:** Find 5+ MongoDB entities in PiggyMetrics

### Phase 3: Enhanced Validation (Completed ✅)

**Priority:** Prevent regression by automatically detecting scanner failures (GitHub issue #66)

Enhanced `examples/validate-outputs.sh` with:

1. **Minimum Count Validation** - Extracts counts from `index.md` and validates against thresholds
   ```bash
   validate_min_count "piggymetrics" "api_endpoints" 15
   validate_min_count "eshopweb" "dependencies" 15  # Catches NuGet bug
   ```

2. **Content Validation** - Checks expected endpoints/entities exist
   ```bash
   check_contains "output/piggymetrics/api-catalog.md" "GET /recipients/current"
   check_contains "output/eshopweb/dependency-graph.md" "MediatR|EntityFrameworkCore"
   ```

3. **Quality Validation** - Validates output correctness
   ```bash
   check_not_contains "fastapi/er-diagram.md" "USER_CREATE"  # Catches SQLAlchemy bug
   validate_er_diagram_quality "eshopweb"  # Catches EF PK bug
   ```

4. **CI/CD Integration** - Exit code 1 on failure, issue references in output

## Consequences

### Positive

1. **Production-Ready Scanners**
   - Scanners will work on real codebases, not just hand-crafted test fixtures
   - Validation against OSS projects ensures real-world compatibility
   - Confidence in output quality increases significantly

2. **Automated Quality Gates**
   - Enhanced validation script catches all 6 critical bugs automatically
   - ~40 validation checks across 3 test projects
   - CI/CD pipeline fails if scanners break (no silent regressions)
   - Exit code enforcement prevents bad releases

3. **Clear Remediation Roadmap**
   - 7 GitHub issues created with specific acceptance criteria
   - Unit tests define expected behavior
   - 3-week plan with measurable goals
   - Each issue includes general criteria (not tied to specific test projects)

4. **Improved Testing Strategy**
   - Real-world integration tests prevent fixture-only testing
   - Content-based validation catches actual bugs
   - Quality checks (PK percentage, DTO filtering) ensure correctness

### Negative

1. **3-Week Development Delay**
   - Fixing critical bugs takes priority over new features
   - Phase 11 (new scanners/generators) blocked until remediation complete
   - Sprint velocity temporarily reduced

2. **Increased Test Maintenance**
   - Integration tests against OSS projects require ongoing maintenance
   - External projects may update, breaking tests
   - Need to handle breaking changes in test projects

3. **Architectural Debt Exposed**
   - Some scanners need significant refactoring (ASP.NET, EF Core)
   - MongoDB support requires new scanner class (duplicate effort)
   - Technology variant handling not architected into base classes

### Risks

1. **External Dependency on OSS Projects**
   - If PiggyMetrics/eShopOnWeb/FastAPI repos are deleted, tests break
   - Mitigation: Fork projects or vendor test fixtures

2. **False Positives from Test Project Updates**
   - OSS projects may refactor, changing expected counts
   - Mitigation: Pin to specific commits in test scripts

## Implementation Plan

### Week 1: Critical Bugs (Mon-Fri)

**Monday-Tuesday:** NuGetDependencyScanner (#60)
- Add debug logging to file discovery
- Verify `.csproj` file pattern matching
- Test Jackson XML parsing of `<PackageReference>`
- Handle both versioned and unversioned packages
- Unit tests for SDK-style projects

**Wednesday-Thursday:** FastAPIScanner (#61)
- Add extensive logging to identify failure point
- Test regex patterns against actual FastAPI files
- Support both `@app` and `@router` decorators
- Handle multi-line decorator definitions
- Unit tests for APIRouter pattern

**Friday:** SQLAlchemyScanner (#62)
- Implement `table=True` parameter filtering
- Parse `Field(foreign_key="...")` for FK relationships
- Parse `Relationship(back_populates="...")` for navigation
- Unit tests for SQLModel vs Pydantic schema distinction

**Validation:** Run `examples/validate-outputs.sh` - critical checks should pass.

### Week 2: Partial Failures (Mon-Fri)

**Monday-Tuesday:** AspNetCoreApiScanner (#63)
- Add logging to .NET AST parser
- Debug OrderController (not working) vs ManageController (working)
- Fix AST parsing or regex patterns
- Unit tests for multi-controller detection

**Wednesday:** EntityFrameworkScanner (#64)
- Implement correct PK identification (`[Key]` or "Id" convention only)
- Fix field type extraction logic
- Test against eShopOnWeb aggregate entities
- Unit tests for PK vs regular field distinction

**Thursday-Friday:** MongoEntityScanner (#65)
- Create new scanner class extending `AbstractJavaParserScanner`
- Copy JpaEntityScanner structure, change `@Entity` → `@Document`
- Extract collection name from annotation
- Register in SPI (`META-INF/services`)
- Unit tests for MongoDB entity detection

**Validation:** Run `examples/validate-outputs.sh` - all checks should pass (100%).

### Week 3: Integration & Documentation (Mon-Fri)

**Monday-Tuesday:** Integration Tests
- Add `EndToEndScanningTest` for each OSS project
- Validate minimum counts and expected content
- Add to CI/CD pipeline

**Wednesday:** CI/CD Integration
- Update `.github/workflows/real-world-tests.yml`
- Run validation script in workflow
- Fail build if validation fails
- Add results to GitHub Step Summary

**Thursday-Friday:** Documentation
- Update scanner documentation with supported patterns
- Document known limitations
- Update CLAUDE.md with Phase 11 completion
- Verify ADR-015 is complete and accurate

## Validation Criteria

The remediation is complete when:

✅ **All GitHub Issues Closed**
- #60: NuGet finds ≥15 dependencies in eShopOnWeb
- #61: FastAPI finds ≥15 endpoints in FastAPI project
- #62: SQLAlchemy finds 2 entities (not 11) in FastAPI project
- #63: ASP.NET finds ≥10 endpoints in eShopOnWeb
- #64: EF Core shows <20% fields as PK in eShopOnWeb
- #65: MongoDB finds ≥5 entities in PiggyMetrics

✅ **Validation Script Passes**
- `examples/validate-outputs.sh` exits with code 0
- 100% pass rate across ~40 validation checks
- No critical failures

✅ **Test Coverage Maintained**
- JaCoCo coverage ≥65% overall (bundle level)
- All new scanner code has ≥80% coverage
- Integration tests pass for all 3 OSS projects

✅ **CI/CD Pipeline Green**
- `real-world-tests.yml` workflow passes
- Validation job shows 100% pass rate
- No `continue-on-error` bypasses

## Related Documents

- GitHub Issues: #60 (NuGet), #61 (FastAPI), #62 (SQLAlchemy), #63 (ASP.NET), #64 (EF Core), #65 (MongoDB), #66 (Validation)
- Enhanced validation script: `examples/validate-outputs.sh`
- Test projects: `test-projects/{piggymetrics,eShopOnWeb,fastapi-postgres}`
- Real-world testing guide: `docs/real-world-testing.md`
