---
# Backstage TechDocs metadata
id: adr-011-ast-first-parsing-strategy
title: ADR-011: AST-First Parsing Strategy for Code Scanners
description: Establishes guidelines for choosing between AST, structured, and regex parsing strategies in DocArchitect scanners
tags:
  - adr
  - architecture
  - parsing
  - scanners
---

# ADR-011: AST-First Parsing Strategy for Code Scanners

| Property | Value |
|----------|-------|
| **Status** | Accepted |
| **Date** | 2025-12-16 |
| **Deciders** | Architecture Team |
| **Technical Story** | Week 3-6 Refactoring Initiative |
| **Supersedes** | N/A |
| **Superseded by** | N/A |

---

## Context

DocArchitect scanners extract architectural information from codebases across multiple languages and technologies. We need a consistent, maintainable strategy for parsing source code that balances:

- **Accuracy**: Correctly parsing complex language constructs
- **Maintainability**: Easy to understand and modify parsing logic
- **Performance**: Acceptable scan times for large codebases
- **Robustness**: Graceful handling of malformed or unusual code

Initially, all scanners used regex-based parsing. During Week 3-6 refactoring, we introduced ANTLR-based AST parsing for language-specific code analysis (Python, C#, JavaScript, Go). The issue we're addressing is the lack of clear guidelines for when to use each parsing strategy, leading to inconsistent scanner implementations.

**Constraints:**

- ANTLR grammars must be maintained and generated during build
- Performance must remain acceptable for large codebases (>10k files)
- Scanners must gracefully handle syntax errors and edge cases

**Why now?**
With 19 scanners implemented and more planned, we need standardized decision criteria to ensure consistency and maintainability across the codebase.

---

## Decision

**We adopt an AST-first parsing strategy with the following guidelines:**

### 1. Use AST Parsing (AbstractAstScanner or AbstractJavaParserScanner) When

- Language structure matters (classes, methods, decorators, annotations)
- Context-sensitive parsing is required (differentiating strings, comments, code)
- Complex syntax needs handling (nested structures, generic types, lambdas)
- Existing parser infrastructure is available (ANTLR grammars, JavaParser)

**Technologies:** Python, C#/.NET, JavaScript/TypeScript, Java

### 2. Use Structured File Parsing (AbstractJacksonScanner) When

- Files have well-defined schemas (JSON, XML, YAML, TOML)
- Parsing configuration files with stable structure
- Processing schema definitions

**Technologies:** Maven pom.xml, NuGet .csproj, npm package.json, pyproject.toml, Avro schemas

### 3. Use Regex Parsing (AbstractRegexScanner) When

- Simple single-line patterns suffice
- DSLs have complex grammars without available parsers
- Processing unstructured text
- ANTLR grammar is unavailable or too heavyweight

**Technologies:** Gradle build scripts, go.mod, GraphQL SDL, SQL migrations

### 4. Mandatory Fallback Strategy

All AST parsers MUST implement graceful fallback to regex when ANTLR runtime is unavailable, parsing fails, or performance is unacceptable.

---

## Rationale

### Key Benefits

- **Accuracy**: AST parsers handle edge cases that regex cannot (nested structures, multiline strings, comments)
- **Maintainability**: Grammar-based parsing is declarative and easier to extend than complex regex patterns
- **Type Safety**: AST nodes are strongly typed, reducing runtime errors
- **Future-Proof**: Supports advanced features like type inference and control flow analysis

### Why This Solution

- Balances accuracy with performance by using appropriate parsing strategy per use case
- Provides clear decision tree reducing guesswork for developers
- Maintains backward compatibility through mandatory fallback mechanism
- Leverages existing ANTLR infrastructure already in place for multiple languages

### Best Choice Because

- More maintainable than pure regex approach across all scanners
- More pragmatic than AST-only approach (which would hurt performance)
- Aligns with industry standards (most IDEs use AST-based analysis)

---

## Alternatives Considered

### Alternative 1: Pure Regex Parsing for All Scanners

**Description:** Continue using regex-based parsing exclusively, avoiding AST complexity

**Pros:**

- Simple implementation (~150 LOC per scanner)
- No ANTLR build dependencies
- Fast parsing performance
- No learning curve for regex

**Cons:**

- Inaccurate for complex constructs (nested classes, generics)
- Cannot distinguish code from strings/comments reliably
- Difficult to maintain as patterns grow complex
- False positives/negatives in edge cases

**Decision:** ❌ Rejected - Insufficient accuracy for production-grade architectural analysis

### Alternative 2: AST-Only Parsing

**Description:** Mandate AST parsing for all language-specific scanners, no regex fallback

**Pros:**

- Maximum accuracy and type safety
- Consistent architecture across all scanners
- Easier to add advanced features (control flow, type inference)
- Better error messages with parse tree context

**Cons:**

- 10-100x slower than regex for simple patterns
- Requires ANTLR grammars for all languages (some unavailable)
- Increased build complexity (grammar generation step)
- No graceful degradation when parsing fails

**Decision:** ❌ Rejected - Too rigid, unacceptable performance for some use cases (e.g., import scanning)

### Alternative 3: Language Service Protocol (LSP) Integration

**Description:** Use LSP servers (e.g., Pylance, TypeScript Language Server) for parsing instead of custom scanners

**Pros:**

- Industry-standard approach used by IDEs
- Full semantic analysis (type resolution, cross-file references)
- Maintained by language communities
- Zero grammar maintenance burden

**Cons:**

- Requires external process management (LSP server startup)
- 100-1000x slower than file-based parsing
- Complex IPC protocol integration
- Overkill for simple structural analysis

**Decision:** ❌ Rejected - Performance unacceptable for batch scanning, excessive complexity

---

## Consequences

### Positive

✅ Consistent decision tree reduces implementation inconsistency
✅ Better accuracy through AST parsing where it matters most
✅ Easier debugging with strongly-typed AST nodes vs. regex capture groups
✅ Extensibility: Adding new attributes (e.g., method visibility) easier with AST
✅ Better testability with parse tree validation

### Negative

⚠️ Build complexity: ANTLR grammar generation adds Maven plugin dependency
⚠️ Learning curve: Developers must understand ANTLR grammar syntax
⚠️ Performance overhead: Initial parsing slower than regex (mitigated by fallback)
⚠️ Grammar maintenance: Language updates require grammar updates

### Neutral

🔵 Code size: AST parsers are ~300-500 LOC vs. ~150 LOC for regex parsers
🔵 Dependencies: Adds ANTLR runtime (1.2 MB) to classpath
🔵 Testing burden: AST parsers require more test cases (happy path + fallback)

---

## Implementation Notes

### For New Scanners

1. Check existing AST infrastructure - if `*AstParser` exists for the language, use `AbstractAstScanner`
2. Evaluate structured file format - if JSON/XML/YAML/TOML, use `AbstractJacksonScanner`
3. Fallback to regex only if neither AST nor structured parsing applies
4. Document decision with comment explaining why regex was chosen

### For Existing Regex Scanners Requiring Migration

**Migration Candidates:**

| Scanner | Technology | Should Migrate? | Reason |
|---------|-----------|-----------------|--------|
| FlaskScanner | Python | ✅ Yes | Python AST available, extracts routes |
| FastAPIScanner | Python | ✅ Yes | Python AST available, extracts routes |
| GradleDependencyScanner | Groovy/Kotlin | ❌ No | Complex DSL, no stable grammar |
| GoModScanner | Go | ❌ No | Simple format, regex sufficient |
| GraphQLScanner | GraphQL SDL | ⚠️ Consider | graphql-java parser available |
| SqlMigrationScanner | SQL DDL | ❌ No | SQL dialects vary, regex pragmatic |

### Migration Process

```java
// Example: Migrate FlaskScanner to AST parsing
public class FlaskScanner extends AbstractAstScanner<PythonAst.PythonClass> {
    
    public static List<PythonClass> parseFile(Path filePath) throws IOException {
        if (ANTLR_AVAILABLE) {
            try {
                return parseWithAntlr(filePath);
            } catch (Exception e) {
                log.warn("ANTLR parsing failed, falling back to regex: {}", e.getMessage());
            }
        }
        return parseWithRegex(filePath); // Keep existing regex as fallback
    }
}
```

### Code Quality Standards

- **No Magic Strings**: All string literals must be private constants
- **Regex Patterns**: Document capture groups with Javadoc
- **Error Handling**: Log warnings, never fail silently
- **Fallback Transparency**: Log when falling back to regex

---

## Compliance

**Architecture Principles:**

- Separation of Concerns: Parser selection logic isolated from scanner business logic
- Fail-Safe Defaults: Mandatory fallback ensures robustness
- Performance-Conscious: Strategy optimizes for common case while maintaining accuracy

**Standards:**

- ANTLR 4.x grammar syntax
- Jackson 2.x for structured file parsing
- Java 17 language features

**Performance:**

- AST parsing acceptable if <2x slowdown vs. regex
- Large codebases (>10k files) must complete in <5 minutes

---

## References

- ANTLR 4 Documentation: <https://github.com/antlr/antlr4/blob/master/doc/index.md>
- JavaParser Documentation: <https://javaparser.org/>
- Jackson Dataformat Modules: <https://github.com/FasterXML/jackson-dataformats-text>
- grammars-v4 Repository: <https://github.com/antlr/grammars-v4> (650+ language grammars)
- ADR-0004: Logging Configuration (DEBUG level for parser diagnostics)
- ADR-0005: Package Organization (technology-based structure)

---

## Metadata

- **Review Date:** 2026-12-16
- **Last Updated:** 2025-12-16
- **Version:** 1.0
