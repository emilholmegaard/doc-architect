---
# Backstage TechDocs metadata
id: adr-013-renderer-output-destinations
title: ADR-013: Renderer Output Destinations and Configuration Strategy
description: SPI-based renderer architecture for flexible output destinations (filesystem, console)
tags:
  - adr
  - architecture
  - renderers
  - spi
---

# ADR-013: Renderer Output Destinations and Configuration Strategy

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-18 |
| **Deciders** | Development Team |
| **Technical Story** | [Issue #8](https://github.com/emilholmegaard/doc-architect/issues/8) |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

After implementing scanners (Phase 3-6) and generators (Phase 7), we need a way to output the generated documentation to various destinations. The system must support:

1. **Multiple output destinations**: Filesystem, console, and future integrations (Confluence, Notion)
2. **Flexible configuration**: Each renderer needs destination-specific settings
3. **Composability**: Multiple renderers should run sequentially (e.g., write to filesystem AND print to console)
4. **Extensibility**: Users should be able to add custom renderers without modifying core code

**Problem drivers:**
- Generators produce `GeneratedOutput` with multiple files, but have no opinion on where/how to output
- Different use cases require different outputs (CI/CD vs. local development vs. documentation publishing)
- Hard-coding output destinations in generators would violate Single Responsibility Principle

**Constraints:**
- Must integrate with existing SPI-based architecture (scanners, generators)
- Must support context-specific configuration via `RenderContext`
- Must handle I/O errors gracefully
- Must preserve file content encoding (UTF-8, Unicode)

---

## Decision

Implement a **SPI-based renderer architecture** with:

1. **OutputRenderer interface**: Common contract for all renderers
   - `String getId()`: Unique renderer identifier
   - `void render(GeneratedOutput output, RenderContext context)`: Render files to destination

2. **RenderContext**: Configuration container
   - `outputDirectory`: Base directory for filesystem renderers
   - `settings`: Map of renderer-specific settings (e.g., `console.colors`, `console.separator`)

3. **Two initial implementations**:
   - **FileSystemRenderer**: Writes files to disk with automatic directory creation
   - **ConsoleRenderer**: Prints to console with optional ANSI color formatting

4. **Configuration strategy**:
   - Settings passed via `RenderContext.settings` map
   - Convention: Use namespaced keys (e.g., `console.colors`, `filesystem.overwrite`)
   - Renderers validate required settings and throw `IllegalStateException` if missing

5. **SPI registration**: Renderers discovered via `ServiceLoader.load(OutputRenderer.class)`

---

## Rationale

### Why SPI-based architecture?
- **Extensibility**: Users can add custom renderers (S3, FTP, HTTP POST) without forking
- **Consistency**: Follows same pattern as scanners and generators
- **Loose coupling**: Core module doesn't depend on specific renderer implementations
- **Testability**: Easy to mock renderers in unit tests

### Why RenderContext for configuration?
- **Flexibility**: Each renderer can have different settings
- **Type safety**: Strongly-typed `outputDirectory`, flexible `settings` map
- **Immutability**: Record ensures thread-safety
- **Validation**: Renderers validate settings at render time

### Why FileSystemRenderer and ConsoleRenderer first?
- **Essential use cases**: Filesystem for production, console for development/CI
- **Reference implementations**: Show best practices for future renderers
- **Immediate value**: Cover 90% of use cases without external dependencies

### Why ANSI colors in ConsoleRenderer?
- **Developer experience**: Colored output improves readability in terminals
- **Configurability**: Can be disabled for CI/CD or when piping output
- **Standard practice**: Widely supported by modern terminals

---

## Alternatives Considered

### Alternative 1: Built-in File Writing in Generators

**Description:** Generators directly write files to filesystem without separate renderer abstraction

**Pros:**
- Simpler initial implementation
- No additional abstraction layer

**Cons:**
- Violates Single Responsibility Principle (generation + I/O)
- Cannot support multiple output destinations
- Hard to test (requires filesystem mocking)
- Inflexible for future requirements (Confluence, S3, etc.)

**Decision:** ❌ Rejected - Poor separation of concerns, not extensible

### Alternative 2: Strategy Pattern with Factory

**Description:** Use Strategy pattern with `RendererFactory` instead of SPI

**Pros:**
- More explicit registration
- Easier to control renderer lifecycle

**Cons:**
- Requires manual registration in factory
- Breaks consistency with scanner/generator architecture
- More boilerplate code
- Harder for users to add custom renderers

**Decision:** ❌ Rejected - Inconsistent with existing architecture, less flexible

### Alternative 3: Builder Pattern for Configuration

**Description:** Use Builder pattern instead of `RenderContext` record

**Pros:**
- More IDE-friendly API
- Explicit validation in builder

**Cons:**
- More boilerplate code
- Mutable during construction
- Overkill for simple configuration container
- Inconsistent with `ScanContext` and `GeneratorConfig` patterns

**Decision:** ❌ Rejected - Records provide sufficient immutability and simplicity

### Alternative 4: Annotation-based Configuration

**Description:** Use annotations (@RenderTo, @OutputDirectory) for configuration

**Pros:**
- Declarative configuration
- Compile-time validation (if using annotation processors)

**Cons:**
- Requires runtime annotation processing
- Less flexible for dynamic configuration
- Overkill for simple settings map
- Adds framework complexity

**Decision:** ❌ Rejected - Too complex for the benefit, reduces runtime flexibility

---

## Consequences

### Positive

✅ **Extensibility**: Users can add custom renderers (Confluence, S3, HTTP) without modifying core code
✅ **Consistency**: Follows same SPI pattern as scanners and generators
✅ **Testability**: Easy to test renderers in isolation with temp directories and stdout capture
✅ **Flexibility**: Multiple renderers can run sequentially (filesystem + console)
✅ **Configurability**: Each renderer has its own settings namespace

### Negative

⚠️ **Learning curve**: Users must understand SPI registration (`META-INF/services/`)
⚠️ **Error handling**: Renderers must handle I/O errors (no automatic retry logic)
⚠️ **Settings validation**: No schema validation for settings map (typos possible)

### Neutral

🔵 **Performance**: File I/O is synchronous (acceptable for batch documentation generation)
🔵 **Unicode support**: Java's `Files.writeString()` handles UTF-8 automatically
🔵 **ANSI colors**: May not display correctly in all terminals (configurable to disable)

---

## Implementation Notes

### FileSystemRenderer Usage

```java
RenderContext context = new RenderContext("./docs", Map.of());
GeneratedOutput output = generator.generate(model, config);
FileSystemRenderer renderer = new FileSystemRenderer();
renderer.render(output, context);
// Files written to ./docs/
```

### ConsoleRenderer Usage

```java
RenderContext context = new RenderContext("./output", Map.of(
    "console.colors", "false",           // Disable ANSI colors
    "console.separator", "===",          // Custom separator
    "console.showHeaders", "true"        // Show file metadata
));
ConsoleRenderer renderer = new ConsoleRenderer();
renderer.render(output, context);
// Output printed to System.out
```

### Adding Custom Renderer

1. Implement `OutputRenderer` interface
2. Register in `META-INF/services/com.docarchitect.core.renderer.OutputRenderer`
3. Use via `ServiceLoader.load(OutputRenderer.class)`

### Configuration Conventions

- Use namespaced keys: `{renderer-id}.{setting-name}`
- Document settings in class-level Javadoc
- Provide sensible defaults in `getSettingOrDefault()`
- Validate required settings in `render()` method

---

## Compliance

### Architecture Principles
- **Single Responsibility**: Generators create content, renderers output it
- **Open/Closed**: Open for extension (new renderers), closed for modification (core interface)
- **Dependency Inversion**: Core depends on `OutputRenderer` interface, not implementations

### Standards
- **SPI**: Uses Java ServiceLoader as specified in Java SE specification
- **UTF-8**: All file writing uses UTF-8 encoding (Unicode support)
- **ANSI colors**: Uses standard ANSI escape codes (widely supported)

### Security
- **Path traversal**: Renderers should validate relative paths don't escape output directory
- **Secrets**: Configuration settings should not contain secrets (use environment variables)
- **Permissions**: File system renderer respects OS file permissions

### Performance
- **Synchronous I/O**: Acceptable for batch documentation generation
- **Memory**: Entire file content loaded into memory (acceptable for documentation files <10MB)
- **Concurrency**: Renderers are stateless and thread-safe (can be used concurrently)

---

## References

- [Java ServiceLoader Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html)
- [ANSI Escape Codes](https://en.wikipedia.org/wiki/ANSI_escape_code)
- [Issue #8: Phase 8 - Renderers](https://github.com/emilholmegaard/doc-architect/issues/8)
- [ADR-001: Multi-Module Maven Structure](./001-multi-module-maven-structure.md)

---

## Metadata

- **Review Date:** 2026-06-18 (6 months from acceptance)
- **Last Updated:** 2025-12-18
- **Version:** 1.0
