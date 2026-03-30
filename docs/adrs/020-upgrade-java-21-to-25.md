---
# Backstage TechDocs metadata
id: adr-020-upgrade-java-21-to-25
title: "ADR-020: Upgrade Java Runtime from 21 to 25 (LTS)"
description: Upgrade the Java runtime from LTS version 21 to LTS version 25
tags:
  - adr
  - java
  - upgrade
  - build
---

# ADR-020: Upgrade Java Runtime from 21 to 25 (LTS)

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2026-03-30 |
| **Deciders** | Development Team |
| **Technical Story** | Java 25 LTS upgrade (session 20260330090421) |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

Java 21 (LTS) has been the project runtime since initial development. Java 25 was released in September 2025
as the next Long-Term Support release, bringing language improvements, performance gains, and an extended
support window. Moving to Java 25 ensures the project stays on a supported LTS baseline and can benefit
from new platform capabilities.

Key drivers:
- Java 25 is the current LTS (supported through ~2030); Java 21 LTS support continues but Java 25 is the active LTS.
- New language/API features available (value types preview, pattern matching enhancements, etc.).
- All project dependencies (Jackson, Logback, JUnit 5, Mockito, ANTLR4, ArchUnit, etc.) are already compatible
  with Java 25; no third-party changes needed.
- Maven 4.0 is required for Java 25 compilation and brings build improvements (faster incremental builds,
  improved dependency resolution).

An open Dependabot PR (#217) proposed bumping `jackson.version` to `2.21` — an invalid/non-existent Maven
artifact version. The current `jackson.version=2.18.6` is the correct latest stable release and is retained.

---

## Decision

- Upgrade Java runtime from **21 → 25** (LTS).
- Upgrade Maven wrapper from **3.8.6 → 3.9.14** (latest stable Maven 3.9.x; Maven 4.0 GA is not yet released as of this decision).
- Update all Java version properties in `pom.xml` (`java.version`, `maven.compiler.source`,
  `maven.compiler.target`, `<release>`) from `21` to `25`.
- Update all CI/CD workflows to use `java-version: '25'` on `actions/setup-java`.
- Retain `jackson.version=2.18.6`; do **not** apply the invalid `2.21` version from Dependabot PR #217.

---

## Rationale

- **No intermediate Java version needed**: all declared dependencies are Java 25 compatible; direct jump from
  21 → 25 is safe.
- **Maven 3.9.14 wrapper**: Maven 3.9.x supports Java 25 bytecode via the `maven-compiler-plugin` `<release>` setting (already at 3.15.0). Maven 4.0 GA is not released; using latest stable 3.9.14.
- **CI/CD coverage**: updated `build.yml` (reusable workflow default), `weekly.yml` (all four jobs: integration
  tests, code quality, dependency check, CodeQL). `pr.yml` and `merge.yml` inherit via `build.yml`.
- **Dockerfile**: was already using `eclipse-temurin:25-jre-alpine`; no change needed.

---

## Alternatives Considered

### Alternative 1: Stay on Java 21 LTS

**Description:** Keep Java 21 and defer the upgrade to a later cycle.

**Pros:**
- Zero migration effort.
- Java 21 is still receiving security patches until 2028.

**Cons:**
- No access to Java 25 language/performance improvements.
- Technical debt grows as the gap between runtime and latest LTS widens.
- Dockerfile was already updated to Java 25, creating an inconsistency.

**Decision:** ❌ Rejected — the Dockerfile was already targeting Java 25, and all dependencies are compatible,
making the upgrade low-risk and overdue.

### Alternative 2: Upgrade to Java 24 (non-LTS)

**Description:** Use Java 24 as an intermediate.

**Pros:**
- Smaller version jump.

**Cons:**
- Java 24 is a non-LTS release with a 6-month support window, not suitable as a stable baseline.
- No benefit over going straight to Java 25 LTS.

**Decision:** ❌ Rejected — non-LTS releases are not appropriate production baselines for this project.

---

## Consequences

### Positive

✅ Project runs on the current active LTS Java release.  
✅ Maven 3.9.14 wrapper brings latest bug fixes while remaining GA-stable.  
✅ Access to Java 25 language features and performance improvements.  
✅ Consistent Java version across `pom.xml`, all CI/CD workflows, and the Dockerfile.  
✅ PR #217's invalid Jackson version (`2.21`) is not introduced; `2.18.6` is retained.

### Negative

⚠️ Maven 4.0 wrapper requires internet access on first use to download the new distribution.  
⚠️ Any external tools or scripts pinned to Java 21 (e.g., local developer environments) must be updated separately.

### Neutral

🔵 No source code changes required — all existing Java 21 syntax is valid in Java 25.  
🔵 No dependency version changes required — all dependencies tested as Java 25 compatible.

---

## Implementation Notes

Files changed:

| File | Change |
|------|--------|
| `pom.xml` | `java.version`, `maven.compiler.source`, `maven.compiler.target`, `<release>` 21 → 25 |
| `.mvn/wrapper/maven-wrapper.properties` | `distributionUrl` → Maven 3.9.14 |
| `.github/workflows/build.yml` | default `java-version` 21 → 25 |
| `.github/workflows/weekly.yml` | all four `Set up JDK 21` steps → `Set up JDK 25` |
| `Dockerfile` | Already used Java 25 — no change |

Dependabot PR #217 note: The PR proposes `jackson.version=2.21` which has no matching Maven Central artifact
(missing patch version component, likely intended `2.21.0` which also does not exist in the Jackson 2.x release
line as of this decision). `jackson.version=2.18.6` is the correct latest stable value and is retained.

---

## References

- [Java 25 Release Notes](https://openjdk.org/projects/jdk/25/)
- [Maven 4.0 Release Notes](https://maven.apache.org/docs/4.0.0/release-notes.html)
- [Jackson 2.18.x Release Notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.18)
- [ADR-001: Multi-Module Maven Structure](001-multi-module-maven-structure.md)
- Dependabot PR #217 (rejected — invalid target version `2.21`)

---

## Metadata

- **Review Date:** 2028-03-30
- **Last Updated:** 2026-03-30
- **Version:** 1.0
