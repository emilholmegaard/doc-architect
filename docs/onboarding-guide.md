---
id: onboarding-guide
title: Onboarding Guide
description: Guide for new team members to understand the architecture and documentation
tags:
  - onboarding
  - guide
  - getting-started
---

# Onboarding Guide

Welcome to the DocArchitect team! This guide will help you understand the project structure, development practices, and how to contribute effectively.

## What is DocArchitect?

DocArchitect is an **automated architecture documentation generator** that scans your codebase and produces:

- 📊 Dependency graphs and matrices
- 🏗️ C4 architecture diagrams (context, container, component)
- 📡 API catalogs (REST, GraphQL, gRPC)
- 🗄️ Entity relationship diagrams
- 📬 Message flow diagrams (Kafka, RabbitMQ, etc.)
- 📑 Complete markdown documentation

**Why it matters:** Architecture documentation stays out-of-sync with code. DocArchitect keeps it synchronized by scanning source code automatically.

## Project Structure at a Glance

```
doc-architect/
├── pom.xml                    # Parent Maven config
├── doc-architect-core/        # Core interfaces & scanners
│   └── src/main/java/com/docarchitect/core/
│       ├── scanner/           # Scanner SPI (19 implementations)
│       ├── generator/         # Generator SPI (Mermaid, PlantUML, etc.)
│       ├── renderer/          # Output rendering (Markdown)
│       └── model/             # Domain models (Component, Dependency, etc.)
└── doc-architect-cli/         # CLI application (Picocli)
```

**Key insight:** DocArchitect uses a **plugin architecture**. Scanners, generators, and renderers are discovered at runtime via Java SPI (ServiceLoader). This means new functionality can be added without modifying core code.

## The 5-Minute Architecture Tour

### 1. How It Works (Data Flow)

```
User runs: docker run doc-architect scan
                    ↓
        CLI loads docarchitect.yaml
                    ↓
        Initialize/clone repositories
                    ↓
    Run all enabled Scanners in parallel
        (Maven, Spring, Kafka, Django, etc.)
                    ↓
        Scanners produce ScanResult objects
                    ↓
    Merge results into ArchitectureModel
        (single intermediate representation)
                    ↓
    Run Generators (Mermaid, PlantUML, etc.)
                    ↓
    Run Renderers (output Markdown + diagrams)
                    ↓
    Write docs/architecture/ directory
```

### 2. The Scanner Ecosystem (19 Total)

Organized into 6 technology packages:

| Package | Scanners | Purpose |
|---------|----------|---------|
| **java/** | Maven, Gradle, Spring REST, JPA, Kafka | JVM ecosystem |
| **python/** | pip/poetry, FastAPI, Flask, SQLAlchemy, Django | Python ML/web apps |
| **dotnet/** | NuGet, ASP.NET Core, Entity Framework | .NET applications |
| **javascript/** | npm, Express.js | Node.js services |
| **go/** | go.mod | Go microservices |
| **schema/** | GraphQL, Avro, SQL Migrations | Data definitions |

**Design principle:** Each scanner extracts one concern (e.g., MavenDependencyScanner finds dependencies, SpringRestApiScanner finds API endpoints). This follows the Single Responsibility Principle.

### 3. Key Domain Models

All domain objects are **immutable Java records**:

```java
// Represents a microservice or module
record Component(String id, String name, String type, Set<String> tags)

// Represents a dependency between components
record Dependency(String sourceId, String targetId, String type, String strength)

// Represents a REST endpoint
record ApiEndpoint(String id, String method, String path, String component)

// Represents a Kafka topic consumer
record MessageFlow(String topic, String consumer, String handler, String type)
```

**Why records?** Immutability + compact constructor validation = safe, predictable code.

## Getting Started with Development

### Prerequisites

- **Java 21** (use `java -version` to verify)
- **Maven 3.9+** (use `mvn -v`)
- **Git**
- **Docker** (for testing the Docker image)

### First-Time Setup

```bash
# Clone the repository
git clone https://github.com/emilholmegaard/doc-architect.git
cd doc-architect

# Build the project
mvn clean package

# Run tests
mvn test

# You should see: BUILD SUCCESS
```

### Verify Your Setup

```bash
# Check Java version
java -version
# Expected: openjdk version "21" or later

# Check Maven version
mvn -v
# Expected: Apache Maven 3.9.0 or later

# Build and test
mvn clean package
# Expected: 232 tests passing, BUILD SUCCESS
```

## Understanding the Code

### Scanner Implementation Pattern

All scanners follow this pattern. Here's the **Spring REST Scanner** as an example:

```java
/**
 * Scans Spring @RestController and REST endpoints.
 * Implementation: Uses JavaParser AST for Java source code analysis
 */
public class SpringRestApiScanner implements Scanner {
    
    @Override
    public ScanResult scan(ScanContext context) {
        // 1. Find all .java files in source directory
        // 2. Parse using JavaParser AST
        // 3. Extract @RestController classes
        // 4. Extract @GetMapping, @PostMapping, etc. methods
        // 5. Extract parameter types (@RequestBody, @PathVariable, etc.)
        // 6. Return ApiEndpoint objects
        return ScanResult.of(apiEndpoints);
    }
}
```

**Three parsing strategies used across 19 scanners:**

| Strategy | Scanners | Use Case |
|----------|----------|----------|
| **JavaParser AST** | Spring, JPA, Kafka (Java/Kotlin) | Precise source code analysis |
| **Jackson XML/JSON** | Maven, Gradle, NuGet, Avro | Configuration file parsing |
| **Regex** | Python, Django, Flask, Express | Text pattern matching |

### Service Provider Interface (SPI) Registration

To add a new scanner, you register it in a metadata file:

```
META-INF/services/com.docarchitect.core.scanner.Scanner
```

The file contains one line per implementation:

```
com.docarchitect.core.scanner.impl.java.MavenDependencyScanner
com.docarchitect.core.scanner.impl.java.SpringRestApiScanner
com.docarchitect.core.scanner.impl.python.FastApiScanner
...
```

**How it works:** At runtime, `ServiceLoader.load(Scanner.class)` discovers all implementations automatically. No hardcoded lists!

### Base Classes for Code Reuse

To reduce duplication, we have 4 abstract base classes:

```java
// For regex-based parsing (Python, JavaScript, SQL)
abstract class AbstractRegexScanner implements Scanner {
    // Common file reading, regex execution, result collection
}

// For Jackson-based parsing (XML, JSON, TOML)
abstract class AbstractJacksonScanner<T> implements Scanner {
    // Common file reading, Jackson parsing, error handling
}

// For JavaParser AST parsing (Java/Kotlin)
abstract class AbstractJavaParserScanner implements Scanner {
    // Common file reading, AST navigation, Java parsing
}
```

**Rule:** If you're writing a 6th Python scanner, extend `AbstractRegexScanner` instead of reimplementing file I/O.

## Development Workflow

### 1. Pick an Issue

Look for issues labeled:

- `good-first-issue` - Great for your first PR
- `scanner: [language]` - New scanner for a language
- `enhancement` - Feature improvements

### 2. Create a Feature Branch

```bash
git checkout -b feature/ISSUE-123-short-description
# Example: feature/ISSUE-42-add-ruby-bundler-scanner
```

### 3. Write Tests First (TDD)

Before writing the scanner, write a test:

```java
@Test
void scan_withGemfile_returnsRubyDependencies() {
    // Arrange: Create sample Gemfile
    String gemfile = """
        gem 'rails', '7.0.0'
        gem 'pg', '>= 0.18'
        """;
    
    // Act: Run scanner
    ScanResult result = scanner.scan(context);
    
    // Assert: Verify dependencies found
    assertThat(result.dependencies())
        .extracting(Dependency::targetId)
        .containsExactly("rails", "pg");
}
```

**Test naming convention:** `methodName_withCondition_expectedResult`

### 4. Implement the Scanner

```java
public class BundlerDependencyScanner extends AbstractRegexScanner implements Scanner {
    
    private static final Pattern GEM_PATTERN = 
        Pattern.compile("gem\\s+['\"]([^'\"]+)['\"]");
    
    @Override
    public ScanResult scan(ScanContext context) {
        Set<Dependency> dependencies = new HashSet<>();
        
        for (Path gemfilePattern : context.findFiles("**/Gemfile")) {
            String content = FileUtils.readFile(gemfilePattern);
            Matcher matcher = GEM_PATTERN.matcher(content);
            
            while (matcher.find()) {
                String gemName = matcher.group(1);
                dependencies.add(new Dependency(
                    context.repositoryId(), 
                    gemName, 
                    "gem", 
                    "required"
                ));
            }
        }
        
        return ScanResult.of(dependencies);
    }
}
```

### 5. Add Complete Javadoc

```java
/**
 * Scans Ruby Bundler dependency files (Gemfile).
 * 
 * <p><b>Supported formats:</b>
 * <ul>
 *   <li>Gemfile (with version constraints)
 *   <li>Gemfile.lock (for exact versions)
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * gem 'rails', '7.0.0'
 * gem 'pg', '>= 0.18', '< 2.0'
 * }</pre>
 * 
 * <p><b>Parsing Strategy:</b> Regex-based (no Ruby runtime required)
 * 
 * @see AbstractRegexScanner
 * @see ScanContext
 * @see Dependency
 */
public class BundlerDependencyScanner extends AbstractRegexScanner implements Scanner {
    // ...
}
```

### 6. Register the Scanner

Add to `META-INF/services/com.docarchitect.core.scanner.Scanner`:

```
com.docarchitect.core.scanner.impl.ruby.BundlerDependencyScanner
```

### 7. Verify Coverage

```bash
# Run all tests
mvn test

# Generate coverage report
mvn jacoco:report

# View report at: target/site/jacoco/index.html
```

**Quality gate:** Coverage must be ≥60% overall, ≥80% for your new code.

### 8. Push and Create PR

```bash
git add .
git commit -m "feat: add Ruby Bundler dependency scanner"
git push origin feature/ISSUE-123-short-description
```

**Include in PR:**

- Link to GitHub issue
- Screenshot of test coverage
- Brief description of parsing strategy
- Any edge cases handled

## Code Quality Standards

### 1. **SOLID Principles**

- **SRP (Single Responsibility):** Each scanner has one job (extract one concern)
- **OCP (Open/Closed):** Add scanners via SPI without modifying core
- **ISP (Interface Segregation):** Small focused interfaces (Scanner, DiagramGenerator, etc.)
- **DIP (Dependency Inversion):** Depend on abstractions (ScanContext), not concrete classes

### 2. **Testing Standards**

- **Unit tests:** All utility classes and core logic
- **Coverage:** ≥60% overall, ≥80% for new code
- **Integration tests:** For SPI discovery (verify ServiceLoader finds scanners)
- **Test structure:** Arrange → Act → Assert

### 3. **Documentation**

- Complete Javadoc for all public APIs
- Parameter documentation in records (compact constructor)
- Code examples in scanner documentation
- ADR (Architecture Decision Record) for significant changes

### 4. **No Code Duplication**

If you find >20 LOC of duplicated code across scanners:

1. Extract to abstract base class
2. Update affected scanners to extend base class
3. Add tests for base class
4. File ADR documenting the refactoring

### 5. **Logging**

Use SLF4J for logging. Configure via environment variables:

```bash
# Set global log level
export LOGBACK_LEVEL=DEBUG

# Set scanner-specific level
export SCANNER_LOG_LEVEL=TRACE

# Run scanner with debug logging
mvn test -DLOGBACK_LEVEL=DEBUG
```

## Common Tasks

### Adding a New Scanner

1. Create Java class extending appropriate base class
2. Write unit tests in `src/test/java` (mirror package structure)
3. Add Javadoc with parsing strategy
4. Register in `META-INF/services/`
5. Verify test coverage ≥80%

### Adding a New Generator

1. Implement `DiagramGenerator` interface
2. Consume `ArchitectureModel` to produce diagrams
3. Register in `META-INF/services/com.docarchitect.core.generator.DiagramGenerator`
4. Add tests

### Adding a New Output Format

1. Implement `OutputRenderer` interface
2. Write markdown or custom format
3. Register in `META-INF/services/com.docarchitect.core.renderer.OutputRenderer`

### Running a Specific Scanner

```bash
# List available scanners
mvn exec:java -Dexec.mainClass="com.docarchitect.cli.DocArchitectCli" -Dexec.args="list --type scanners"

# Run and inspect output
java -jar target/doc-architect-cli-*.jar scan --verbose
```

## Troubleshooting

### Tests Failing Locally

```bash
# Clean and rebuild
mvn clean compile

# Run tests with debug logging
mvn test -DLOGBACK_LEVEL=DEBUG

# Run specific test
mvn test -Dtest=SpringRestApiScannerTest
```

### SPI Not Discovering Scanners

```bash
# Verify META-INF/services file exists
find . -name "com.docarchitect.core.scanner.Scanner"

# Check file contents (no blank lines, exact package names)
cat doc-architect-core/src/main/resources/META-INF/services/com.docarchitect.core.scanner.Scanner

# Verify JAR contains service file
jar tf target/doc-architect-core-*.jar | grep META-INF/services
```

### Docker Image Build Fails

```bash
# Clean Maven cache
mvn clean

# Rebuild JAR
mvn package

# Check Docker image
docker build -t doc-architect .

# Test image
docker run doc-architect --help
```

## Resources

- **Architecture Overview:** [architecture-overview.md](architecture-overview.md)
- **API Documentation:** Generated Javadoc in `target/site/apidocs/`
- **Development Guidelines:** [claude-development-guide.md](claude-development-guide.md)
- **GitHub Issues:** [Issues](https://github.com/emilholmegaard/doc-architect/issues)

## Getting Help

- **Questions:** Open a GitHub Discussion
- **Bug Reports:** Create an Issue with reproduction steps
- **Code Review:** Ask in PR comments or team Slack
- **Architecture Decisions:** See ADRs in `docs/adr/`

## Next Steps

1. ✅ Read this guide (you are here!)
2. ⏭️ Read [claude-development-guide.md](claude-development-guide.md) for technical standards
3. ⏭️ Read [architecture-overview.md](architecture-overview.md) to understand components
4. ⏭️ Pick a `good-first-issue` and create your first PR
5. ⏭️ Attend code review to learn team practices

Welcome to the team! 🎉
