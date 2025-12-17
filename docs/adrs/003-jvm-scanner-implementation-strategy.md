---
# Backstage TechDocs metadata
id: adr-003-jvm-scanner-implementation-strategy
title: ADR-003: JVM Scanner Implementation Strategy
description: Define parsing strategies for JVM ecosystem scanners using JavaParser, Jackson XML, and regex
tags:
  - adr
  - architecture
  - jvm
  - parsing
  - scanners
---
# ADR-003: JVM Scanner Implementation Strategy

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-12 |
| **Deciders** | Development Team |
| **Technical Story** | Phase 3 Implementation |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---
## Context

DocArchitect needs to extract architectural information from Java/JVM codebases, including:

- **Dependencies** - Maven and Gradle build files
- **REST APIs** - Spring MVC/Spring Boot controllers
- **Data models** - JPA entities with relationships
- **Message flows** - Kafka producers and consumers

The challenge is that we're running in Java and have access to powerful Java parsing libraries, but we need to handle multiple file formats (XML, Groovy DSL, Kotlin DSL, Java source code) across different ecosystems.

**Key Requirements:**
1. Parse Maven `pom.xml` files (XML format)
2. Parse Gradle build files (`build.gradle`, `build.gradle.kts`)
3. Extract Spring REST endpoints from Java source
4. Extract JPA entities and relationships from Java source
5. Extract Kafka message flows from Java source
6. No external process execution (all parsing in-JVM)
7. Handle property placeholders like `${project.version}`

---
## Decision

We will implement **5 specialized scanners** using different parsing strategies optimized for each file type:

### 1. MavenDependencyScanner - XML Parser (Jackson XML)

**Strategy:** Use Jackson XmlMapper for structured XML parsing

**Rationale:**
- Maven POMs are well-structured XML documents
- Jackson provides type-safe object mapping
- Built-in support for XML namespaces
- Can handle property placeholders with custom logic

**Implementation:**
```java
XmlMapper mapper = new XmlMapper();
PomModel pom = mapper.readValue(pomFile, PomModel.class);
String resolvedVersion = resolveProperties(dep.version, properties);
```

**Handles:**
- Direct dependencies in `<dependencies>`
- Dependency management in `<dependencyManagement>`
- Property placeholders: `${project.version}`, `${spring.version}`
- Parent POM inheritance
- Scope mapping (compile, test, provided, runtime)

### 2. GradleDependencyScanner - Regex Parser

**Strategy:** Use regex patterns for both Groovy and Kotlin DSL

**Rationale:**
- Gradle files are executable code, not structured data
- AST parsing would require embedding Groovy/Kotlin compilers
- Regex is lightweight and sufficient for dependency extraction
- Supports 3 common notation styles

**Implementation:**
```java
// String notation: implementation 'group:artifact:version'
Pattern.compile("(implementation|api|compileOnly)\\s*[\"']([^:]+):([^:]+):([^\"']+)[\"']");

// Kotlin function: implementation("group", "artifact", "version")
Pattern.compile("(implementation|api)\\(\\s*[\"']([^\"']+)[\"'],\\s*[\"']([^\"']+)[\"'],\\s*[\"']([^\"']+)[\"']");

// Map notation: implementation group: 'com.foo', name: 'bar', version: '1.0'
Pattern.compile("group:\\s*[\"'](.+?)[\"'],\\s*name:\\s*[\"'](.+?)[\"'],\\s*version:\\s*[\"'](.+?)[\"']");
```

**Configuration mapping:**
- `implementation` → compile
- `api` → compile
- `compileOnly` → provided
- `runtimeOnly` → runtime
- `testImplementation` → test

### 3. SpringRestApiScanner - JavaParser AST

**Strategy:** Use JavaParser for Abstract Syntax Tree (AST) analysis

**Rationale:**
- Spring annotations are only reliably extractable via AST
- JavaParser provides type-safe access to class/method structures
- Can extract method parameters and return types
- Handles both class-level and method-level annotations

**Implementation:**
```java
CompilationUnit cu = StaticJavaParser.parse(javaFile);
cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
    if (hasAnnotation(classDecl, "RestController", "Controller")) {
        String basePath = extractRequestMapping(classDecl);
        classDecl.getMethods().forEach(method -> {
            extractEndpoint(method, basePath);
        });
    }
});
```

**Extracts:**
- `@RestController` / `@Controller` classes
- `@RequestMapping` (class and method level)
- HTTP method annotations: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`
- Path parameters: `@PathVariable`
- Query parameters: `@RequestParam`
- Body parameters: `@RequestBody`

### 4. JpaEntityScanner - JavaParser AST

**Strategy:** Use JavaParser AST with annotation-based detection

**Rationale:**
- JPA annotations define entity metadata
- Field types and relationships require AST analysis
- Need to detect `@Entity`, `@Table`, `@Column`, `@Id` annotations
- Relationship annotations span multiple lines

**Implementation:**
```java
cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
    if (hasAnnotation(classDecl, "Entity")) {
        String tableName = extractTableName(classDecl);
        List<Field> fields = extractFields(classDecl);
        List<Relationship> relationships = extractRelationships(classDecl);
    }
});
```

**Extracts:**
- Entity classes (`@Entity`)
- Table names (`@Table(name="...")` or snake_case from class name)
- Fields with types and nullable constraints
- Primary keys (`@Id`)
- Relationships: `@OneToMany`, `@ManyToMany`, `@ManyToOne`, `@OneToOne`

### 5. KafkaScanner - JavaParser AST

**Strategy:** Use JavaParser for annotation and method call detection

**Rationale:**
- Kafka consumers use `@KafkaListener` annotations
- Kafka producers use `KafkaTemplate.send()` method calls
- Need AST traversal to find both patterns

**Implementation:**
```java
// Consumer pattern
method.getAnnotations().stream()
    .filter(ann -> "KafkaListener".equals(ann.getNameAsString()))
    .forEach(ann -> extractConsumer(ann, method));

// Producer patterns
method.findAll(MethodCallExpr.class).forEach(call -> {
    if ("send".equals(call.getNameAsString())) {
        call.getScope().ifPresent(scope -> {
            if (scope.toString().contains("kafkaTemplate")) {
                extractProducer(call);
            }
        });
    }
});
```

**Extracts:**
- Consumers: `@KafkaListener(topics = "...")`
- Producers: `@SendTo("topic")`
- Producers: `kafkaTemplate.send("topic", message)`
- Message types from method parameters

---
## Rationale

Selects fit-for-purpose parsers (AST, XML, regex) to balance accuracy, safety, and performance across JVM artifacts.

---
## Alternatives Considered

### Using Gradle Tooling API for Gradle Parsing

**Pros:**
- Official Gradle API
- Type-safe dependency resolution
- Handles complex build scripts

**Cons:**
- Requires Gradle installation or embedding Gradle daemon
- Heavy dependency (~50MB)
- Slow initialization (daemon startup)
- Cannot run in sandboxed environments

**Verdict:** ❌ Rejected - Too heavyweight for static analysis

### Using Groovy/Kotlin Compilers for Gradle

**Pros:**
- Accurate AST parsing
- Can evaluate dynamic build logic

**Cons:**
- Requires embedding full compilers (Groovy ~10MB, Kotlin ~20MB)
- Security risk (executes arbitrary code)
- Complex dependency trees
- Overkill for simple dependency extraction

**Verdict:** ❌ Rejected - Regex is sufficient and safer

### Using Maven Embedder for Maven Parsing

**Pros:**
- Official Maven API
- Handles parent POM resolution
- Full property interpolation

**Cons:**
- Heavy dependency (~15MB for Maven core)
- Requires Maven settings.xml
- Slow initialization
- Overkill for static analysis

**Verdict:** ❌ Rejected - Jackson XML is faster and lighter

### Using Reflection for Annotation Detection

**Pros:**
- Native Java mechanism
- Type-safe

**Cons:**
- Requires compiling source code first
- Needs classpath setup
- Cannot run on source-only projects
- Security implications of class loading

**Verdict:** ❌ Rejected - JavaParser works on source code directly

---
## Consequences

### Positive

✅ **Lightweight** - Only 2 external libraries (JavaParser, Jackson)
✅ **Fast** - No process spawning, no compilation required
✅ **Safe** - No arbitrary code execution
✅ **Accurate** - AST parsing for Java, structured XML for Maven
✅ **Flexible** - Regex for Gradle handles multiple notation styles
✅ **Maintainable** - Each scanner is independent and focused

### Negative

⚠️ **Gradle limitations** - Regex cannot handle dynamic Gradle logic (e.g., `if` statements in dependencies)
⚠️ **Property resolution** - Maven property resolution is custom logic, not full Maven semantics
⚠️ **Parent POM** - Does not resolve remote parent POMs, only local files

### Neutral

- **JavaParser version** - Locked to 3.25.8 (stable release)
- **Jackson version** - Locked to 2.18.2 (matches other Jackson modules)
- **Test coverage** - Integration tests would require fixture projects (deferred to Phase 5)

---
## Implementation Notes

### Dependency Versions

```xml
<javaparser.version>3.25.8</javaparser.version>
<jackson.version>2.18.2</jackson.version>
```

### Scanner Priorities

Scanners execute in priority order (lower = earlier):

```java
MavenDependencyScanner.getPriority()  = 10  // Run first
GradleDependencyScanner.getPriority() = 10  // Run first
SpringRestApiScanner.getPriority()    = 50  // After dependencies
JpaEntityScanner.getPriority()        = 60  // After Spring
KafkaScanner.getPriority()            = 70  // Last
```

### File Pattern Matching

Each scanner declares supported file patterns:

```java
MavenDependencyScanner: "**/pom.xml"
GradleDependencyScanner: "**/build.gradle", "**/build.gradle.kts"
SpringRestApiScanner: "**/*.java"
JpaEntityScanner: "**/*.java"
KafkaScanner: "**/*.java"
```

### Property Resolution (Maven)

Custom property resolver handles common patterns:

```java
private String resolveProperties(String value, Map<String, String> properties) {
    Matcher matcher = PROPERTY_PATTERN.matcher(value); // ${...}
    while (matcher.find()) {
        String propName = matcher.group(1);
        String propValue = properties.getOrDefault(propName, matcher.group(0));
        value = value.replace(matcher.group(0), propValue);
    }
    return value;
}
```

**Supported properties:**
- `${project.version}` → from `<version>` element
- `${project.groupId}` → from `<groupId>` element
- `${spring.version}` → from `<properties><spring.version>`
- Custom properties defined in `<properties>`

---
## Compliance

_TBD_

---
## References

- [JavaParser Documentation](https://javaparser.org/)
- [Jackson XML Module](https://github.com/FasterXML/jackson-dataformat-xml)
- [Maven POM Reference](https://maven.apache.org/pom.html)
- [Gradle Dependencies DSL](https://docs.gradle.org/current/userguide/declaring_dependencies.html)
- [Spring MVC Annotations](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [JPA Annotations](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html)
- [Kafka Spring Integration](https://docs.spring.io/spring-kafka/reference/html/)

---
**Decision:** Use JavaParser for Java source analysis, Jackson XML for Maven, and regex for Gradle
**Impact:** High - Defines parsing strategy for all JVM scanners
**Review Date:** After Phase 5 completion (diagram generators)
