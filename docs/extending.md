# Extending DocArchitect

This guide explains how to extend DocArchitect with custom scanners, generators, and renderers.

## Architecture Overview

DocArchitect uses Java's Service Provider Interface (SPI) for plugin discovery. All extensions follow the same pattern:

1. Implement the appropriate interface
2. Register via `META-INF/services/`
3. Package as JAR
4. Mount in Docker container

```
┌─────────────┐     ┌───────────────────┐     ┌─────────────────┐
│  Scanners   │────▶│ ArchitectureModel │────▶│   Generators    │
│   (SPI)     │     │  (Intermediate)   │     │     (SPI)       │
└─────────────┘     └───────────────────┘     └─────────────────┘
                                                      │
                                                      ▼
                                              ┌─────────────────┐
                                              │   Renderers     │
                                              │     (SPI)       │
                                              └─────────────────┘
```

## Creating a Custom Scanner

### Step 1: Implement the Scanner Interface

```java
package com.example.scanner;

import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.model.*;
import java.util.*;

/**
 * Custom scanner for detecting Terraform infrastructure.
 * 
 * <p>Scans .tf files to extract cloud resources and relationships.</p>
 */
public class TerraformScanner implements Scanner {

    private static final String ID = "terraform";
    
    @Override
    public String getId() {
        return ID;
    }
    
    @Override
    public String getDisplayName() {
        return "Terraform Infrastructure Scanner";
    }
    
    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("hcl", "terraform");
    }
    
    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.tf", "**/terraform.tfvars");
    }
    
    @Override
    public int getPriority() {
        return 80;  // Run after code scanners
    }
    
    @Override
    public boolean appliesTo(ScanContext context) {
        // Only run if terraform files exist
        return context.findFiles("**/*.tf").findFirst().isPresent();
    }
    
    @Override
    public ScanResult scan(ScanContext context) {
        List<Component> components = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        for (Path tfFile : context.findFiles("**/*.tf").toList()) {
            try {
                extractResources(tfFile, components, relationships);
            } catch (Exception e) {
                warnings.add("Failed to parse " + tfFile + ": " + e.getMessage());
            }
        }
        
        return new ScanResult(
            ID,
            true,
            components,
            List.of(),      // dependencies
            List.of(),      // apiEndpoints
            List.of(),      // messageFlows
            List.of(),      // dataEntities
            relationships,
            warnings,
            List.of()       // errors
        );
    }
    
    private void extractResources(Path file, List<Component> components, 
                                   List<Relationship> relationships) {
        // Parse HCL and extract resources
        // Add Component for each resource (aws_lambda, aws_rds, etc.)
        // Add Relationship for dependencies between resources
    }
}
```

### Step 2: Register via SPI

Create file `src/main/resources/META-INF/services/com.docarchitect.core.scanner.Scanner`:

```
com.example.scanner.TerraformScanner
```

### Step 3: Package and Use

```bash
# Build your scanner
mvn clean package

# Use with Docker
docker run -v $(pwd):/workspace \
    -v /path/to/my-scanner.jar:/app/plugins/my-scanner.jar \
    doc-architect scan
```

## Creating a Custom Generator

### Step 1: Implement the Generator Interface

```java
package com.example.generator;

import com.docarchitect.core.generator.*;
import com.docarchitect.core.model.*;
import java.util.*;

/**
 * Generator for Structurizr DSL output.
 */
public class StructurizrGenerator implements DiagramGenerator {

    @Override
    public String getId() {
        return "structurizr";
    }
    
    @Override
    public String getDisplayName() {
        return "Structurizr DSL Generator";
    }
    
    @Override
    public String getFileExtension() {
        return "dsl";
    }
    
    @Override
    public Set<DiagramType> getSupportedDiagramTypes() {
        return Set.of(
            DiagramType.C4_CONTEXT,
            DiagramType.C4_CONTAINER,
            DiagramType.C4_COMPONENT
        );
    }
    
    @Override
    public GeneratedDiagram generate(ArchitectureModel model, 
                                      DiagramType type, 
                                      GeneratorConfig config) {
        StringBuilder dsl = new StringBuilder();
        
        dsl.append("workspace {\n\n");
        dsl.append("    model {\n");
        
        // Generate people, systems, containers based on model
        for (Component comp : model.components()) {
            if (comp.type() == ComponentType.SERVICE) {
                dsl.append("        ").append(sanitizeId(comp.name()))
                   .append(" = softwareSystem \"")
                   .append(comp.name()).append("\" {\n");
                dsl.append("            description \"")
                   .append(comp.description() != null ? comp.description() : "")
                   .append("\"\n");
                dsl.append("        }\n");
            }
        }
        
        // Generate relationships
        for (Relationship rel : model.relationships()) {
            dsl.append("        ")
               .append(sanitizeId(rel.sourceId()))
               .append(" -> ")
               .append(sanitizeId(rel.targetId()))
               .append(" \"").append(rel.description() != null ? rel.description() : "uses")
               .append("\"\n");
        }
        
        dsl.append("    }\n\n");
        
        // Generate views
        dsl.append("    views {\n");
        dsl.append("        systemLandscape \"SystemLandscape\" {\n");
        dsl.append("            include *\n");
        dsl.append("            autoLayout\n");
        dsl.append("        }\n");
        dsl.append("    }\n");
        
        dsl.append("}\n");
        
        return new GeneratedDiagram(
            "workspace",
            dsl.toString(),
            "dsl"
        );
    }
    
    private String sanitizeId(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}
```

### Step 2: Register via SPI

Create file `src/main/resources/META-INF/services/com.docarchitect.core.generator.DiagramGenerator`:

```
com.example.generator.StructurizrGenerator
```

## Creating a Custom Renderer

Renderers handle output destinations (files, console, external services).

```java
package com.example.renderer;

import com.docarchitect.core.renderer.*;
import com.docarchitect.core.model.*;

/**
 * Renderer that publishes documentation to Confluence.
 */
public class ConfluenceRenderer implements OutputRenderer {

    @Override
    public String getId() {
        return "confluence";
    }
    
    @Override
    public void render(GeneratedOutput output, RenderContext context) {
        String baseUrl = context.getSetting("confluence.url");
        String spaceKey = context.getSetting("confluence.space");
        String apiToken = context.getSetting("confluence.token");
        
        // Create/update pages via Confluence REST API
        for (GeneratedFile file : output.files()) {
            if (file.relativePath().endsWith(".md")) {
                publishToConfluence(baseUrl, spaceKey, apiToken, file);
            }
        }
    }
    
    private void publishToConfluence(String baseUrl, String spaceKey, 
                                      String token, GeneratedFile file) {
        // Implementation using Confluence REST API
    }
}
```

## Model Reference

### Component Types

| Type | Description | Example |
|------|-------------|---------|
| `SERVICE` | Microservice or standalone app | User Service |
| `MODULE` | Module within larger app | Auth Module |
| `LIBRARY` | Shared library | Utils |
| `EXTERNAL` | External system | Payment Gateway |
| `DATABASE` | Database instance | PostgreSQL |
| `MESSAGE_BROKER` | Message system | Kafka |

### Relationship Types

| Type | Description |
|------|-------------|
| `CALLS` | Synchronous API call |
| `USES` | Uses as dependency |
| `PUBLISHES` | Publishes messages to |
| `SUBSCRIBES` | Subscribes to messages |
| `DEPENDS_ON` | Generic dependency |
| `READS_FROM` | Reads from database |
| `WRITES_TO` | Writes to database |

### API Types

| Type | Description |
|------|-------------|
| `REST` | REST endpoint |
| `GRAPHQL_QUERY` | GraphQL query |
| `GRAPHQL_MUTATION` | GraphQL mutation |
| `GRPC` | gRPC service |
| `WEBSOCKET` | WebSocket endpoint |

## Best Practices

### Scanner Guidelines

1. **Fail fast with clear errors** - Don't silently return empty results
2. **Use established libraries** - Prefer Jackson, JavaParser, etc. over regex for complex parsing
3. **Check `appliesTo()` first** - Skip unnecessary work
4. **Set appropriate priority** - Dependencies (10-50), APIs (50-100), Others (100+)
5. **Document parsing strategy** - Explain how files are parsed in Javadoc

### Generator Guidelines

1. **Validate output** - Ensure generated diagrams have valid syntax
2. **Handle empty models** - Generate meaningful output even with no data
3. **Use consistent IDs** - Use `IdGenerator.generate()` for deterministic identifiers
4. **Support configuration** - Allow customization via `GeneratorConfig`

### Testing Your Extensions

```java
@Test
void scannerDetectsResources() {
    // Setup
    Path testProject = Path.of("src/test/resources/fixtures/terraform");
    ScanContext context = new ScanContext(testProject, List.of(testProject), 
        null, Map.of(), null);
    
    // Execute
    ScanResult result = new TerraformScanner().scan(context);
    
    // Verify
    assertThat(result.success()).isTrue();
    assertThat(result.components()).hasSize(5);
    assertThat(result.components()).anyMatch(c -> 
        c.name().equals("aws_lambda_function.api_handler"));
}
```

## Plugin Distribution

### Maven Dependency

Publish your scanner to Maven Central or a private repository:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>docarchitect-scanner-terraform</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Docker Image Extension

Create a custom Docker image with your plugins:

```dockerfile
FROM ghcr.io/emilholmegaard/doc-architect:latest

COPY target/my-scanner.jar /app/plugins/
```

### Runtime Plugin Loading

Mount plugins at runtime:

```bash
docker run -v $(pwd):/workspace \
    -v ./plugins:/app/plugins \
    doc-architect scan
```

## Common Patterns

### Multi-File Scanning

When entities span multiple files (like Terraform modules):

```java
// First pass: collect all files
Map<String, List<Path>> filesByModule = new HashMap<>();
for (Path file : context.findFiles("**/*.tf").toList()) {
    String module = file.getParent().getFileName().toString();
    filesByModule.computeIfAbsent(module, k -> new ArrayList<>()).add(file);
}

// Second pass: process each module
for (var entry : filesByModule.entrySet()) {
    processModule(entry.getKey(), entry.getValue(), components, relationships);
}
```

### Cross-Scanner Dependencies

Use `ScanContext.previousResults()` to access results from earlier scanners:

```java
@Override
public ScanResult scan(ScanContext context) {
    // Get components from dependency scanner
    ScanResult depResult = context.previousResults().get("maven-dependencies");
    if (depResult != null) {
        Set<String> springBootApps = depResult.dependencies().stream()
            .filter(d -> d.artifactId().startsWith("spring-boot"))
            .map(Dependency::sourceComponentId)
            .collect(Collectors.toSet());
        
        // Only scan Spring Boot applications
        // ...
    }
}
```

### Progress Reporting

Report progress for long-running scans:

```java
@Override
public ScanResult scan(ScanContext context) {
    List<Path> files = context.findFiles("**/*.tf").toList();
    int processed = 0;
    
    for (Path file : files) {
        processFile(file);
        processed++;
        context.progressReporter().progress(getId(), 
            (processed * 100) / files.size());
    }
    
    return result;
}
```

## Need Help?

- 📖 [Full API Documentation](https://emilholmegaard.github.io/doc-architect/api)
- 💬 [GitHub Discussions](https://github.com/emilholmegaard/doc-architect/discussions)
- 🐛 [Issue Tracker](https://github.com/emilholmegaard/doc-architect/issues)
