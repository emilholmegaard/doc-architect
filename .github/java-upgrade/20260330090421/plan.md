

# Upgrade Plan: doc-architect (20260330090421)

- **Generated**: 2026-03-30 09:04
- **HEAD Branch**: fix/weekly-security-scan-exit-code
- **HEAD Commit ID**: d3a0a8f

## Available Tools

**JDKs**
- JDK 21.0.9: /opt/local/Library/Java/JavaVirtualMachines/jdk-21-oracle-java-se.jdk/Contents/Home (current project JDK, used by step 2)
- JDK 25: **<TO_BE_INSTALLED>** (required by steps 3 and 4)

**Build Tools**
- Maven 3.9.12: /opt/local/share/java/maven3/bin/mvn (system Maven)
- Maven Wrapper: 3.8.6 → **<TO_BE_UPGRADED>** to 4.0.0 (Maven 4.0+ required for Java 25)

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred. <!-- this note is for users, NEVER remove it -->

## Options

- Working branch: appmod/java-upgrade-20260330090421
- Run tests before and after the upgrade: true

## Upgrade Goals

- Upgrade Java from 21 to 25 (latest LTS)

### Technology Stack

| Technology/Dependency | Current | Min Compatible | Why Incompatible |
| --------------------- | ------- | -------------- | ---------------- |
| Java                   | 21      | 25             | User requested — Java 25 is the latest LTS (released September 2025) |
| Maven Wrapper          | 3.8.6   | 4.0.0          | Maven 4.0+ required for Java 25 |
| maven-compiler-plugin  | 3.15.0  | 3.11.0+        | Already compatible (3.15.0 supports Java 25) |
| maven-surefire-plugin  | 3.5.5   | 3.1.0+         | Already compatible |
| JUnit Jupiter          | 6.0.3   | 5.10.0+        | Already compatible |
| Mockito                | 5.23.0  | 5.0.0+         | Already compatible |
| ArchUnit               | 1.4.1   | 1.0.0+         | Already compatible |
| Jackson                | 2.18.6  | 2.14.0+        | Already compatible |
| Logback                | 1.5.32  | 1.4.0+         | Already compatible |
| picocli                | 4.7.7   | 4.7.0+         | Already compatible |
| ANTLR4                 | 4.13.2  | 4.13.0+        | Already compatible |

### Derived Upgrades

- Upgrade Maven Wrapper from 3.8.6 to 4.0.0 — Maven 4.0+ is required for Java 25; update `.mvn/wrapper/maven-wrapper.properties` (`distributionUrl` and `wrapperVersion`)
- Update `java.version`, `maven.compiler.source`, `maven.compiler.target` properties from `21` to `25` in root `pom.xml`
- Update `<release>21</release>` to `<release>25</release>` in `maven-compiler-plugin` configuration in root `pom.xml`

## Upgrade Steps

- **Step 1: Setup Environment**
  - **Rationale**: JDK 25 is not available on this system and must be installed before compilation with Java 25 is possible.
  - **Changes to Make**:
    - [ ] Install JDK 25 via `#appmod-install-jdk version=25`
  - **Verification**:
    - Command: `#appmod-list-jdks` to confirm JDK 25 is present
    - Expected: JDK 25 listed with a valid path

---

- **Step 2: Setup Baseline**
  - **Rationale**: Record pre-upgrade compilation and test results to measure upgrade success against.
  - **Changes to Make**:
    - [ ] Run baseline compilation with Java 21
    - [ ] Run baseline tests with Java 21
  - **Verification**:
    - Command: `JAVA_HOME=/opt/local/Library/Java/JavaVirtualMachines/jdk-21-oracle-java-se.jdk/Contents/Home ./mvnw clean test -q`
    - JDK: /opt/local/Library/Java/JavaVirtualMachines/jdk-21-oracle-java-se.jdk/Contents/Home
    - Expected: Document SUCCESS/FAILURE and test pass count (forms acceptance criteria)

---

- **Step 3: Upgrade to Java 25**
  - **Rationale**: Core upgrade — update Maven wrapper to 4.0.0 (required for Java 25) and update all Java version properties in pom.xml to 25.
  - **Changes to Make**:
    - [ ] Update `.mvn/wrapper/maven-wrapper.properties`: set `distributionUrl` to Maven 4.0.0 and `wrapperVersion` to latest compatible
    - [ ] Update root `pom.xml`: change `java.version`, `maven.compiler.source`, `maven.compiler.target` from `21` to `25`
    - [ ] Update root `pom.xml`: change `<release>21</release>` to `<release>25</release>` in `maven-compiler-plugin` configuration
  - **Verification**:
    - Command: `JAVA_HOME=<jdk25_path> ./mvnw clean test-compile -q`
    - JDK: JDK 25 (installed in step 1)
    - Expected: Compilation SUCCESS for both main and test sources

---

- **Step 4: Final Validation**
  - **Rationale**: Verify all upgrade goals are met — Java 25 used, project compiles, and all tests pass.
  - **Changes to Make**:
    - [ ] Verify all version properties in pom.xml show `25`
    - [ ] Resolve any remaining compilation errors
    - [ ] Run full test suite and fix ALL failures (iterative fix loop until 100% pass)
  - **Verification**:
    - Command: `JAVA_HOME=<jdk25_path> ./mvnw clean test -q`
    - JDK: JDK 25
    - Expected: Compilation SUCCESS + 100% tests pass

## Key Challenges

- **Maven 4.0 Wrapper Upgrade**
  - **Challenge**: The Maven wrapper targets Maven 3.8.6 which does not support Java 25. Switching to Maven 4.0 changes the distribution URL format and may expose minor build lifecycle differences.
  - **Strategy**: Update `.mvn/wrapper/maven-wrapper.properties` to the Maven 4.0.0 distribution URL. Maven 4.0 is backward compatible with `modelVersion 4.0.0` POM files so no POM format changes are required.

- **Java 25 Removed/Restricted APIs**
  - **Challenge**: Java 25 finalizes removal of some APIs that were deprecated for removal in Java 21-24 (e.g., `Thread.stop()`, legacy security manager, finalization). Dependencies like ArchUnit and ANTLR4 interact with JDK internals and could be affected.
  - **Strategy**: Compile with Java 25 and address any errors directly. The comprehensive test suite will surface behavioral regressions.

## Plan Review

- All sections fully populated; no placeholders remain.
- No intermediate Java version required — all declared dependencies are already compatible with Java 25. Direct jump from Java 21 → 25 is safe.
- No javax/jakarta migration needed (project does not use Java EE APIs).
- No unfixable limitations identified.
