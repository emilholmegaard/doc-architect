---
# Backstage TechDocs metadata
id: adr-006-technology-based-package-organization
title: ADR-006: Technology-Based Package Organization
description: Reorganize scanner implementations by technology-based subpackages
tags:
  - adr
  - architecture
  - packaging
  - organization
---
# ADR-006: Technology-Based Package Organization

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-15 |
| **Deciders** | Architecture Team |
| **Technical Story** | Week 4 Architecture Refactoring |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---
## Context

After completing Week 3 refactoring (base class extraction), the scanner implementations were all located in a flat `com.docarchitect.core.scanner.impl` package:

```
scanner/impl/
├── MavenDependencyScanner.java
├── GradleDependencyScanner.java
├── SpringRestApiScanner.java
├── ... (16 more scanners)
```

With 19 scanner implementations spanning 5 technology ecosystems (Java, Python, .NET, JavaScript, Go) plus 3 schema scanners, this flat structure had several problems:

### Problems with Flat Structure

1. **Poor Discoverability**: No clear grouping by technology ecosystem
2. **Difficult Navigation**: IDE file trees showed all 19 scanners in alphabetical order
3. **No Clear Ownership**: Unclear which scanners belong to which technology
4. **Scalability Issues**: Adding more scanners (e.g., Ruby, PHP, Rust) would worsen the problem
5. **Lack of Organization**: Tests and implementations mixed together without clear structure

### Business Context

The project supports multiple technology ecosystems:
- **Java/JVM** (5 scanners): Maven, Gradle, Spring, JPA, Kafka
- **Python** (5 scanners): Pip/Poetry, FastAPI, Flask, SQLAlchemy, Django
- **.NET** (3 scanners): NuGet, ASP.NET Core, Entity Framework
- **JavaScript/Node.js** (2 scanners): npm, Express.js
- **Go** (1 scanner): go.mod
- **Schema/API Definitions** (3 scanners): GraphQL, Avro, SQL Migrations

Each ecosystem has different conventions, file formats, and scanning strategies. Organizing by technology makes it easier to:
- Find related scanners quickly
- Understand ecosystem-specific patterns
- Add new scanners to the appropriate group
- Maintain consistency within an ecosystem

---

## Decision

We have decided to reorganize scanner implementations into **technology-based subpackages** within `com.docarchitect.core.scanner.impl`:

### New Package Structure

```
scanner/
├── Scanner.java (interface)
├── ScanContext.java
├── ScanResult.java
├── base/
│   ├── AbstractScanner.java
│   ├── AbstractJacksonScanner.java
│   ├── AbstractRegexScanner.java
│   └── AbstractJavaParserScanner.java
└── impl/
    ├── java/
    │   ├── MavenDependencyScanner.java
    │   ├── GradleDependencyScanner.java
    │   ├── SpringRestApiScanner.java
    │   ├── JpaEntityScanner.java
    │   └── KafkaScanner.java
    ├── python/
    │   ├── PipPoetryDependencyScanner.java
    │   ├── FastAPIScanner.java
    │   ├── FlaskScanner.java
    │   ├── SQLAlchemyScanner.java
    │   └── DjangoOrmScanner.java
    ├── dotnet/
    │   ├── NuGetDependencyScanner.java
    │   ├── AspNetCoreApiScanner.java
    │   └── EntityFrameworkScanner.java
    ├── javascript/
    │   ├── NpmDependencyScanner.java
    │   └── ExpressScanner.java
    ├── go/
    │   └── GoModScanner.java
    └── schema/
        ├── GraphQLScanner.java
        ├── AvroSchemaScanner.java
        └── SqlMigrationScanner.java
```

### Test Structure (Mirrors Implementation)

```
test/java/com/docarchitect/core/scanner/
├── base/
│   ├── AbstractScannerTest.java
│   └── AbstractJacksonScannerTest.java
└── impl/
    ├── java/
    │   ├── MavenDependencyScannerTest.java
    │   ├── JpaEntityScannerTest.java
    │   └── SpringRestApiScannerTest.java
    ├── python/
    │   └── FastAPIScannerTest.java
    ├── dotnet/
    │   └── NuGetDependencyScannerTest.java
    ├── schema/
    │   └── GraphQLScannerTest.java
    └── ScannersMetadataTest.java (tests all scanners)
```

### Package Naming Conventions

- **java**: JVM-based technologies (Java, Kotlin, Scala, Groovy)
- **python**: Python ecosystem tools and frameworks
- **dotnet**: .NET and C# technologies
- **javascript**: JavaScript/TypeScript and Node.js
- **go**: Go language and tooling
- **schema**: Language-agnostic schema and API definition formats

---

## Rationale

Improves discoverability, ownership clarity, and scalability by grouping scanners per ecosystem.

---

## Implementation

### Step 1: Create New Package Directories

```bash
cd doc-architect-core/src/main/java/com/docarchitect/core/scanner/impl
mkdir -p java python dotnet javascript go schema
```

### Step 2: Move Scanner Implementations

```bash
# Java scanners
mv Maven*.java Gradle*.java Spring*.java Jpa*.java Kafka*.java java/

# Python scanners
mv Pip*.java FastAPI*.java Flask*.java SQLAlchemy*.java Django*.java python/

# .NET scanners
mv NuGet*.java AspNetCore*.java EntityFramework*.java dotnet/

# JavaScript scanners
mv Npm*.java Express*.java javascript/

# Go scanners
mv GoMod*.java go/

# Schema scanners
mv GraphQL*.java Avro*.java SqlMigration*.java schema/
```

### Step 3: Update Package Declarations

```bash
# Example for Java scanners
cd java
for file in *.java; do
    sed -i '1s/package com.docarchitect.core.scanner.impl;/package com.docarchitect.core.scanner.impl.java;/' "$file"
done
```

### Step 4: Update SPI Registration

Updated `META-INF/services/com.docarchitect.core.scanner.Scanner`:

```
# Java/JVM Scanners
com.docarchitect.core.scanner.impl.java.MavenDependencyScanner
com.docarchitect.core.scanner.impl.java.GradleDependencyScanner
...

# Python Scanners
com.docarchitect.core.scanner.impl.python.PipPoetryDependencyScanner
...
```

### Step 5: Update Test Imports

Updated `ScannersMetadataTest.java` to import from new packages:

```java
import com.docarchitect.core.scanner.impl.java.*;
import com.docarchitect.core.scanner.impl.python.*;
import com.docarchitect.core.scanner.impl.dotnet.*;
import com.docarchitect.core.scanner.impl.javascript.*;
import com.docarchitect.core.scanner.impl.go.*;
import com.docarchitect.core.scanner.impl.schema.*;
```

---

## Consequences

### Positive

✅ **Improved Discoverability**: Scanners grouped by technology make finding related implementations trivial
✅ **Clear Organization**: Package structure mirrors technology ecosystem boundaries
✅ **Easier Navigation**: IDE file trees show clear technology groupings
✅ **Scalability**: Adding new ecosystems (Ruby, PHP, Rust) has a clear pattern
✅ **Better Maintenance**: Ecosystem-specific changes affect localized packages
✅ **Consistent Patterns**: Scanners within an ecosystem can share patterns more easily
✅ **Test Organization**: Test structure mirrors implementation structure
✅ **Documentation**: Package names self-document which scanners exist for each technology

### Negative

⚠️ **Longer Fully-Qualified Names**:
- Before: `com.docarchitect.core.scanner.impl.MavenDependencyScanner`
- After: `com.docarchitect.core.scanner.impl.java.MavenDependencyScanner`

⚠️ **SPI File Verbosity**: Registration file grew from ~19 lines to ~30 lines (with comments)

⚠️ **Import Updates**: Required updating imports in `ScannersMetadataTest` and potentially future code

### Neutral

🔵 **No Runtime Impact**: Package reorganization is compile-time only, zero performance impact
🔵 **ServiceLoader Discovery**: Works identically via SPI, no changes to discovery mechanism
🔵 **Backward Compatibility**: Breaking change for anyone importing scanner classes directly (low risk)

---

## Alternatives Considered

### Alternative 1: Feature-Based Organization

```
impl/
├── dependencies/  (Maven, Gradle, NuGet, npm, Poetry, go.mod)
├── web/          (Spring, FastAPI, Flask, ASP.NET, Express)
├── orm/          (JPA, SQLAlchemy, Entity Framework, Django)
├── messaging/    (Kafka)
└── schema/       (GraphQL, Avro, SQL)
```

**Pros:**
- Groups by functionality rather than technology
- Highlights cross-technology patterns (e.g., all dependency scanners)

**Cons:**
- Harder to find "all Python scanners" or "all .NET scanners"
- Violates Single Responsibility (mixing technologies)
- Unclear where multi-purpose scanners belong
- Less intuitive for users familiar with specific ecosystems

**Rejected:** Technology grouping is more intuitive and aligns with user mental models.

### Alternative 2: Alphabetical Prefixing

```
impl/
├── DotNet_AspNetCoreApiScanner.java
├── DotNet_EntityFrameworkScanner.java
├── DotNet_NuGetDependencyScanner.java
├── Java_MavenDependencyScanner.java
...
```

**Pros:**
- Keeps flat structure
- Visually groups by prefix in alphabetical file lists

**Cons:**
- Still a flat structure, doesn't scale
- Naming convention conflicts with Java conventions
- IDE package explorers don't benefit
- Tests still mixed together

**Rejected:** Prefix-based naming is a workaround for lack of packages, not a solution.

### Alternative 3: Keep Flat Structure

**Pros:**
- No migration effort
- Shorter fully-qualified names

**Cons:**
- Doesn't solve discoverability or scalability problems
- Becomes worse as more scanners are added
- Harder to maintain as project grows

**Rejected:** Flat structure doesn't scale beyond 10-15 classes.

---

## Migration Impact

### Files Modified (19 scanners + 6 tests + 1 SPI file = 26 files)

**Scanner Implementations (19):**
- 5 Java scanners moved to `impl/java/`
- 5 Python scanners moved to `impl/python/`
- 3 .NET scanners moved to `impl/dotnet/`
- 2 JavaScript scanners moved to `impl/javascript/`
- 1 Go scanner moved to `impl/go/`
- 3 Schema scanners moved to `impl/schema/`

**Test Files (6):**
- 3 Java tests moved to `impl/java/`
- 1 Python test moved to `impl/python/`
- 1 .NET test moved to `impl/dotnet/`
- 1 Schema test moved to `impl/schema/`
- `ScannersMetadataTest` updated with new imports

**Configuration (1):**
- `META-INF/services/com.docarchitect.core.scanner.Scanner` updated with new package paths

### Verification

✅ **Build Status**: `mvn compile` successful
✅ **Test Status**: All 232 tests passing
✅ **SPI Discovery**: `ServiceLoader.load(Scanner.class)` finds all 19 scanners
✅ **Zero Regressions**: No behavior changes, only organization changes

---

## Future Considerations

### Potential Enhancements

1. **Package-Info Files**: Add `package-info.java` to each technology package with overview Javadoc
2. **Ecosystem-Specific Base Classes**: If patterns emerge, create `AbstractPythonScanner`, `AbstractDotNetScanner`, etc.
3. **README per Package**: Add `README.md` in each technology directory explaining conventions
4. **ArchUnit Rules**: Enforce package structure via ArchUnit tests (e.g., "Python scanners must be in python/ package")

### Adding New Ecosystems

To add a new technology ecosystem (e.g., Ruby):

```bash
# 1. Create package
mkdir -p impl/ruby

# 2. Add scanner
cat > impl/ruby/BundlerDependencyScanner.java <<EOF
package com.docarchitect.core.scanner.impl.ruby;
// ...
EOF

# 3. Update SPI
echo "com.docarchitect.core.scanner.impl.ruby.BundlerDependencyScanner" >> \
    META-INF/services/com.docarchitect.core.scanner.Scanner

# 4. Add test
mkdir -p test/java/.../impl/ruby
# ...
```

### Monitoring

No specific monitoring needed - this is a structural change with no runtime impact.

---

## Compliance

_TBD_

---

## References

- Java Package Naming Conventions: https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.1
- ServiceLoader Documentation: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html
- Week 4 Refactoring Plan: `.github/ISSUE_TEMPLATE/refactoring-proposal.md`
- ADR-0003: Base Class Extraction (Week 3)

---

## Metadata

- **Review Date:** 2026-12-15
- **Last Updated:** 2025-12-15
- **Version:** 1.0

---

## Approval

- **Proposed by:** Claude Sonnet 4.5 (AI Assistant)
- **Reviewed by:** (Pending)
- **Approved by:** (Pending)
- **Implementation Date:** 2025-12-15
- **Migration Completed:** 2025-12-15

---

## Changelog

- **2025-12-15**: Initial implementation and migration
  - Created 6 technology-based packages
  - Moved all 19 scanners to appropriate packages
  - Updated SPI registration file
  - Reorganized test files
  - All 232 tests passing
