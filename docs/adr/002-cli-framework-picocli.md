# ADR-002: CLI Framework - Picocli

**Status:** Accepted
**Date:** 2025-12-12
**Deciders:** Development Team
**Related:** [Phase 2 Implementation](https://github.com/emilholmegaard/doc-architect/issues/2)

---

## Context

DocArchitect needs a command-line interface to provide users with an intuitive way to:

- Initialize configuration files
- Scan codebases and generate architecture documentation
- List available plugins (scanners, generators, renderers)
- Generate specific diagram types
- Validate configurations
- Compare architecture models over time

The CLI must support:

- Multiple subcommands with distinct functionality
- Global options (verbose, quiet)
- Rich help documentation
- Argument validation and error messages
- Native GraalVM compilation (future requirement)

## Decision

We will use **Picocli** as the CLI framework for DocArchitect.

### Why Picocli?

1. **Annotation-based declarative syntax** - Clean, readable command definitions
2. **Subcommand support** - Natural hierarchical command structure
3. **Rich help generation** - Automatic ANSI-colored help text
4. **Strong typing** - Compile-time safety for options and parameters
5. **GraalVM native-image support** - Future-proofing for native executables
6. **Annotation processing** - Compile-time validation and reflection-free metadata
7. **Mature ecosystem** - Well-documented, actively maintained, widely adopted

### CLI Structure

```
docarchitect [global-options] <command> [command-options] [arguments]

Global Options:
  -v, --verbose    Enable DEBUG logging
  -q, --quiet      Suppress all output except errors
  -V, --version    Print version information

Commands:
  init             Initialize configuration file with project detection
  scan             Scan codebase and generate architecture documentation
  generate         Generate specific diagram types from saved model
  list             List available scanners, generators, or renderers
  validate         Validate configuration file and architecture model
  diff             Compare current architecture against baseline
```

### Implementation Approach

- **Root command**: `DocArchitectCLI` with global options and version info
- **Subcommands**: Separate classes for each command (`InitCommand`, `ScanCommand`, etc.)
- **Annotation processing**: Enable picocli-codegen for reflection-free metadata
- **Fat JAR**: Maven Shade Plugin with `ServicesResourceTransformer` for SPI merging

## Alternatives Considered

### JCommander

**Pros:**
- Simpler API for basic use cases
- Smaller library footprint

**Cons:**
- Limited subcommand support
- No annotation processing
- No native-image support
- Less rich help formatting

### Apache Commons CLI

**Pros:**
- Lightweight
- Standard Apache library

**Cons:**
- Low-level API (manual parsing)
- No subcommand support
- Poor help formatting
- Requires significant boilerplate

### Spring Shell

**Pros:**
- Interactive shell mode
- Extensive validation support
- Tab completion

**Cons:**
- Heavy Spring Framework dependency
- Overkill for batch CLI operations
- Slower startup time
- Not suitable for scripting

### Clikt (Kotlin)

**Pros:**
- Modern Kotlin DSL
- Excellent type safety
- Clean API

**Cons:**
- Requires Kotlin dependency
- Project is Java-first
- Less mature than Picocli
- Smaller community

## Consequences

### Positive

✅ **Clean command definitions** - Declarative annotations reduce boilerplate
✅ **Automatic help generation** - Users get rich, ANSI-colored help text
✅ **Type safety** - Compile-time validation of options and parameters
✅ **Future-proof** - GraalVM native-image support for instant startup
✅ **Low learning curve** - Well-documented with extensive examples
✅ **Extensibility** - Easy to add new commands and options

### Negative

⚠️ **Dependency size** - Picocli adds ~600KB to JAR (acceptable for CLI tool)
⚠️ **Annotation processing** - Requires build configuration (already set up)

### Neutral

- **Reflection-free metadata** - Annotation processing generates metadata at compile time
- **Fat JAR required** - Maven Shade Plugin bundles all dependencies (necessary anyway for CLI distribution)

## Implementation Notes

### Maven Configuration

```xml
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
    <version>4.7.7</version>
</dependency>

<!-- Annotation processor for reflection-free metadata -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>info.picocli</groupId>
                <artifactId>picocli-codegen</artifactId>
                <version>4.7.7</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### Fat JAR with SPI Support

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <configuration>
        <transformers>
            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                <mainClass>com.docarchitect.DocArchitectCLI</mainClass>
            </transformer>
            <!-- Merge SPI files from all JARs -->
            <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
        </transformers>
        <finalName>docarchitect</finalName>
    </configuration>
</plugin>
```

### Example Command

```java
@Command(
    name = "init",
    description = "Initialize configuration file with project-detected defaults",
    mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Project directory", defaultValue = ".")
    private Path projectPath;

    @Option(names = {"-f", "--force"}, description = "Overwrite existing configuration")
    private boolean force;

    @Override
    public Integer call() {
        // Implementation...
        return 0;
    }
}
```

## References

- [Picocli Documentation](https://picocli.info/)
- [Picocli GitHub](https://github.com/remkop/picocli)
- [GraalVM Native Image Guide](https://picocli.info/#_graalvm_native_image)
- [Maven Shade Plugin](https://maven.apache.org/plugins/maven-shade-plugin/)

---

**Decision:** Use Picocli as the CLI framework for DocArchitect
**Impact:** High - Affects all user interactions with the tool
**Review Date:** After Phase 3 completion
