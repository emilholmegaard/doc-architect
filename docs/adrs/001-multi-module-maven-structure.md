# ADR-001: Multi-Module Maven Structure

**Status:** Accepted
**Date:** 2025-12-12
**Deciders:** Development Team
**Related:** [Phase 1 Implementation](https://github.com/emilholmegaard/doc-architect/issues/1)

---

## Context

DocArchitect is designed as an extensible architecture documentation generator with:

- **Core interfaces** - Scanner, DiagramGenerator, OutputRenderer (SPI-based)
- **Domain models** - Component, Dependency, ApiEndpoint, etc.
- **CLI application** - Command-line interface for users
- **Future plugins** - Third-party scanners and generators

The initial single-module structure would mix core abstractions with CLI implementation, making it difficult to:

- Reuse core logic in other contexts (e.g., Maven plugin, Gradle plugin, IDE integrations)
- Test core domain logic independently from CLI
- Version and release core APIs separately from CLI
- Allow third-party developers to depend only on core interfaces

## Decision

We will use a **multi-module Maven structure** with the following modules:

```
doc-architect/
├── pom.xml                          # Parent POM (dependency & plugin management)
├── doc-architect-core/              # Core interfaces & domain models
│   ├── pom.xml
│   └── src/main/java/com/docarchitect/core/
│       ├── scanner/                 # Scanner SPI
│       ├── generator/               # Generator SPI
│       ├── renderer/                # Renderer SPI
│       ├── model/                   # Domain records
│       └── util/                    # Utilities
└── doc-architect-cli/               # CLI implementation (Picocli)
    ├── pom.xml
    └── src/main/java/com/docarchitect/
```

### Module Responsibilities

#### Parent POM (`doc-architect-parent`)

- Dependency management (`<dependencyManagement>`)
- Plugin management (`<pluginManagement>`)
- Common properties (Java version, encoding, versions)
- Reactor build order

#### `doc-architect-core`

**Purpose:** Core abstractions, domain models, and utilities

**Contains:**
- SPI interfaces: `Scanner`, `DiagramGenerator`, `OutputRenderer`
- Domain records: `Component`, `Dependency`, `ApiEndpoint`, `MessageFlow`, `DataEntity`, `Relationship`, `ArchitectureModel`
- Context/result models: `ScanContext`, `ScanResult`, `GeneratorConfig`, `GeneratedDiagram`, etc.
- Utilities: `FileUtils`, `IdGenerator`
- SPI registration: `META-INF/services/` files

**Dependencies:**
- Minimal: `snakeyaml`, `slf4j-api` only
- No CLI dependencies
- No implementation dependencies

**Consumers:**
- CLI module
- Future Maven/Gradle plugins
- Third-party scanner/generator implementations

#### `doc-architect-cli`

**Purpose:** Command-line interface application

**Contains:**
- Picocli commands: `InitCommand`, `ScanCommand`, `GenerateCommand`, `ListCommand`, `ValidateCommand`, `DiffCommand`
- CLI orchestration logic
- Fat JAR packaging with Maven Shade Plugin

**Dependencies:**
- `doc-architect-core` (internal dependency)
- `picocli` (CLI framework)
- `logback-classic` (logging implementation)

**Output:**
- `docarchitect.jar` - Executable fat JAR with all dependencies

## Alternatives Considered

### Single Module

**Pros:**
- Simpler initial setup
- Fewer POMs to maintain

**Cons:**
- CLI and core logic tightly coupled
- Cannot reuse core logic in other contexts
- Difficult to version APIs independently
- Third-party plugin developers must depend on entire CLI

**Verdict:** ❌ Rejected - Lacks separation of concerns

### Gradle Multi-Project Build

**Pros:**
- More flexible build scripts (Kotlin DSL)
- Faster incremental builds
- Better dependency resolution

**Cons:**
- Team has stronger Maven expertise
- Maven ecosystem is more mature for Java libraries
- Maven Central publishing is simpler with Maven
- Existing CI/CD tooling uses Maven

**Verdict:** ❌ Rejected - Maven aligns better with team expertise

### Monorepo with Separate Git Repositories

**Pros:**
- Complete module independence
- Independent versioning and release cycles

**Cons:**
- Coordination overhead for breaking changes
- Requires separate CI/CD pipelines
- More complex for contributors
- Overkill for current project size

**Verdict:** ❌ Rejected - Premature for project maturity

### Three Modules (core + spi + cli)

**Pros:**
- Even stronger separation between domain and SPI

**Cons:**
- Over-engineering for current needs
- More POMs to maintain
- Confusing for third-party developers (which module to depend on?)

**Verdict:** ❌ Rejected - YAGNI (You Aren't Gonna Need It)

## Consequences

### Positive

✅ **Clear separation of concerns** - Core logic isolated from CLI implementation
✅ **Reusability** - Core module can be used in Maven plugins, Gradle plugins, IDEs
✅ **Independent testing** - Core logic tested without CLI dependencies
✅ **Third-party extensibility** - Plugin developers depend only on `doc-architect-core`
✅ **Versioning flexibility** - Core APIs can be versioned separately (future)
✅ **Smaller dependencies** - Consumers of core module don't pull in CLI dependencies

### Negative

⚠️ **Additional complexity** - More POMs to maintain
⚠️ **Longer build times** - Multi-module reactor builds are slower
⚠️ **Coordination required** - Breaking changes in core affect CLI module

### Neutral

- **Dependency management** - Parent POM centralizes versions (reduces duplication)
- **Reactor builds** - Maven automatically builds modules in dependency order

## Implementation Details

### Parent POM Configuration

```xml
<modules>
    <module>doc-architect-core</module>
    <module>doc-architect-cli</module>
</modules>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <version>2.3</version>
        </dependency>
        <!-- ... more managed dependencies ... -->
    </dependencies>
</dependencyManagement>
```

### Core Module POM

```xml
<parent>
    <groupId>com.docarchitect</groupId>
    <artifactId>doc-architect-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>doc-architect-core</artifactId>
<packaging>jar</packaging>

<dependencies>
    <!-- Only minimal dependencies -->
    <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
    </dependency>
</dependencies>
```

### CLI Module POM

```xml
<parent>
    <groupId>com.docarchitect</groupId>
    <artifactId>doc-architect-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>doc-architect-cli</artifactId>
<packaging>jar</packaging>

<dependencies>
    <!-- Internal dependency -->
    <dependency>
        <groupId>com.docarchitect</groupId>
        <artifactId>doc-architect-core</artifactId>
        <version>${project.version}</version>
    </dependency>

    <!-- CLI framework -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli</artifactId>
    </dependency>

    <!-- Logging implementation -->
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
    </dependency>
</dependencies>
```

### Build Commands

```bash
# Build all modules from root
mvn clean install

# Build only core
mvn clean install -pl doc-architect-core

# Build CLI and its dependencies
mvn clean install -pl doc-architect-cli -am

# Skip tests for faster builds
mvn clean install -DskipTests
```

## Future Extensibility

This structure allows for future modules:

- `doc-architect-maven-plugin` - Maven plugin for build integration
- `doc-architect-gradle-plugin` - Gradle plugin for build integration
- `doc-architect-scanner-spring` - Spring Boot scanner implementation
- `doc-architect-scanner-kafka` - Kafka message flow scanner
- `doc-architect-generator-plantuml` - PlantUML diagram generator
- `doc-architect-web` - Web UI for architecture exploration

Each module can independently depend on `doc-architect-core` without pulling in CLI dependencies.

## References

- [Maven Multi-Module Projects](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Maven Dependency Management](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)
- [SPI (Service Provider Interface)](https://docs.oracle.com/javase/tutorial/ext/basics/spi.html)

---

**Decision:** Use multi-module Maven structure with `doc-architect-core` and `doc-architect-cli`
**Impact:** High - Affects project structure, build process, and extensibility
**Review Date:** After Phase 4 completion
