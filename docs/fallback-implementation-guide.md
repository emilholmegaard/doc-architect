---
title: Fallback Implementation Guide
nav_order: 8
---

**Date:** 2025-12-30
**Purpose:** Guidelines for implementing regex-based fallback parsing in all scanners

## Lessons Learned from Initial Implementation

### Issues Identified in First Pass (3 scanners)

1. **Magic Strings** - Regex patterns defined inline as string literals
2. **Pattern Recompilation** - Patterns compiled inside lambda (every invocation)
3. **Code Duplication** - Same extraction logic repeated across scanners
4. **Inconsistent Naming** - Different method names for same operations

### Improvements Made

#### 1. Shared Utility Class: `RegexPatterns`

Created centralized regex pattern library with pre-compiled patterns:

```java
// Location: com.docarchitect.core.scanner.base.RegexPatterns

// Common patterns used across all Java scanners
public static final Pattern CLASS_NAME_PATTERN = ...
public static final Pattern PACKAGE_PATTERN = ...
public static final Pattern FIELD_PATTERN = ...

// Utility methods
public static String extractClassName(String content, Path file)
public static String extractPackageName(String content)
public static String buildFullyQualifiedName(String packageName, String className)
```

**Benefits:**
- ✅ Patterns compiled once (performance)
- ✅ Consistent extraction logic
- ✅ Single source of truth
- ✅ Easier to test and maintain

#### 2. Scanner-Specific Constants

Each scanner defines its own domain-specific patterns as static final fields:

```java
// Example from SpringRestApiScanner
private static final Pattern BASE_PATH_PATTERN =
    Pattern.compile("@RequestMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
private static final Pattern GET_MAPPING_PATTERN =
    Pattern.compile("@GetMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
```

**Benefits:**
- ✅ No magic strings
- ✅ Compile-time pattern validation
- ✅ Pattern reuse across invocations
- ✅ Clear documentation

#### 3. Simplified Fallback Strategy

```java
private FallbackParsingStrategy<T> createFallbackStrategy() {
    return (file, content) -> {
        // 1. Quick check (annotation presence)
        if (!content.contains("@TargetAnnotation")) {
            return List.of();
        }

        // 2. Extract metadata using shared utility
        String className = RegexPatterns.extractClassName(content, file);
        String packageName = RegexPatterns.extractPackageName(content);
        String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

        // 3. Extract domain-specific data using scanner patterns
        List<T> results = new ArrayList<>();
        Matcher matcher = SCANNER_SPECIFIC_PATTERN.matcher(content);
        while (matcher.find()) {
            results.add(createResult(matcher, fullyQualifiedName));
        }

        return results;
    };
}
```

## Implementation Template

### Step 1: Define Scanner-Specific Patterns

Add constants near the top of your scanner class:

```java
// Regex patterns for fallback parsing (compiled once for performance)
private static final Pattern TARGET_ANNOTATION_PATTERN =
    Pattern.compile("@TargetAnnotation\\s*\\(\\s*[\"']([^\"']+)[\"']");
private static final Pattern ANOTHER_PATTERN =
    Pattern.compile("@AnotherAnnotation\\s*\\([^)]*value\\s*=\\s*[\"']([^\"']+)[\"']");
```

### Step 2: Import RegexPatterns Utility

```java
import com.docarchitect.core.scanner.base.RegexPatterns;
```

### Step 3: Update scan() Method

```java
@Override
public ScanResult scan(ScanContext context) {
    log.info("Scanning {} in: {}", getDisplayName(), context.rootPath());

    List<T> results = new ArrayList<>();
    ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

    List<Path> files = context.findFiles(getFilePattern()).toList();
    statsBuilder.filesDiscovered(files.size());

    if (files.isEmpty()) {
        return emptyResult();
    }

    for (Path file : files) {
        if (!shouldScanFile(file)) {
            continue; // Pre-filtering
        }

        statsBuilder.incrementFilesScanned();

        // Three-tier parsing with fallback
        FileParseResult<T> result = parseWithFallback(
            file,
            cu -> extractFromAST(cu),
            createFallbackStrategy(),
            statsBuilder
        );

        if (result.isSuccess()) {
            results.addAll(result.getData());
        }
    }

    ScanStatistics statistics = statsBuilder.build();
    log.info("Found {} items (success rate: {:.1f}%, overall parse rate: {:.1f}%)",
        results.size(), statistics.getSuccessRate(), statistics.getOverallParseRate());

    return buildSuccessResult(..., statistics);
}
```

### Step 4: Implement AST Extraction

Refactor existing parsing logic into pure function:

```java
/**
 * Extracts data from a parsed CompilationUnit using AST analysis.
 * This is the Tier 1 (HIGH confidence) parsing strategy.
 *
 * @param cu the parsed CompilationUnit
 * @return list of discovered items
 */
private List<T> extractFromAST(CompilationUnit cu) {
    List<T> results = new ArrayList<>();

    // Your existing AST parsing logic here
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
        // ... extract data ...
        results.add(item);
    });

    return results;
}
```

### Step 5: Implement Fallback Strategy

```java
/**
 * Creates a regex-based fallback parsing strategy for when AST parsing fails.
 * This is the Tier 2 (MEDIUM confidence) parsing strategy.
 *
 * @return fallback parsing strategy
 */
private FallbackParsingStrategy<T> createFallbackStrategy() {
    return (file, content) -> {
        List<T> results = new ArrayList<>();

        // Quick check - does file contain target patterns?
        if (!content.contains("@TargetAnnotation")) {
            return results;
        }

        // Extract common metadata using shared utility
        String className = RegexPatterns.extractClassName(content, file);
        String packageName = RegexPatterns.extractPackageName(content);
        String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

        // Extract domain-specific data
        Matcher matcher = TARGET_ANNOTATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String value = matcher.group(1);
            T item = new T(fullyQualifiedName, value, /* other fields */);
            results.add(item);
        }

        log.debug("Fallback parsing found {} items in {}", results.size(), file.getFileName());
        return results;
    };
}
```

## Scanner Priority Order

Implement fallback in this order (highest impact first):

### High Priority (Core Java Scanners)
1. ✅ **SpringRestApiScanner** - Complete with optimizations
2. ✅ **JpaEntityScanner** - Complete
3. ✅ **KafkaScanner** - Complete
4. ⬜ **MavenDependencyScanner** - XML parsing (different approach)
5. ⬜ **GradleDependencyScanner** - Groovy/Kotlin DSL (different approach)

### Medium Priority (Extended Java Scanners)
6. ⬜ **SpringComponentScanner** - @Component, @Service, @Repository
7. ⬜ **GraphQLSchemaScanner** - .graphql files (already regex-based)
8. ⬜ **AvroSchemaScanner** - .avsc files (JSON-based)

### Medium Priority (Go Scanners)
9. ⬜ **GoModScanner** - go.mod files
10. ⬜ **GoHttpScanner** - HTTP routers

### Medium Priority (.NET Scanners)
11. ⬜ **NuGetDependencyScanner** - .csproj files (XML)
12. ⬜ **AspNetCoreScanner** - API controllers
13. ⬜ **EntityFrameworkScanner** - EF entities

### Low Priority (Python Scanners)
14. ⬜ **PipDependencyScanner** - requirements.txt
15. ⬜ **PoetryDependencyScanner** - pyproject.toml
16. ⬜ **FastApiScanner** - FastAPI routes
17. ⬜ **FlaskScanner** - Flask routes
18. ⬜ **DjangoScanner** - Django views
19. ⬜ **SQLAlchemyScanner** - ORM models

### Low Priority (JavaScript Scanners)
20. ⬜ **NpmDependencyScanner** - package.json
21. ⬜ **ExpressJsScanner** - Express routes

## Testing Strategy

For each scanner, add tests covering:

1. **AST Success** - Valid code parsed with HIGH confidence
2. **Fallback Success** - Broken code parsed with MEDIUM confidence
3. **Both Fail** - Ensure statistics track failure
4. **Mixed Scenario** - Combination of valid, broken, and empty files
5. **Real-World Scale** - 100+ files with realistic distribution

Example test structure:

```java
@Test
void fallback_brokenJavaFile_usesMediumConfidence() throws IOException {
    Path file = tempDir.resolve("Broken.java");
    Files.writeString(file, """
        @TargetAnnotation("value")
        public class Broken { // missing closing brace
        """);

    ScanStatistics.Builder stats = new ScanStatistics.Builder();
    FileParseResult<T> result = scanner.parseWithFallback(
        file,
        scanner::extractFromAST,
        scanner.createFallbackStrategy(),
        stats
    );

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.MEDIUM);
    assertThat(result.getData()).hasSize(1);
}
```

## Performance Considerations

### ✅ DO
- Pre-compile all regex patterns as static final fields
- Use quick content checks before regex matching (`contains()`)
- Limit matcher iterations (e.g., first 100 matches)
- Use possessive quantifiers where appropriate (`++`, `*+`)

### ❌ DON'T
- Create Pattern objects inside lambdas
- Use greedy quantifiers without limits (`.*` without constraints)
- Parse entire files when annotations can be skipped
- Apply complex regex to large files without bounds

## Code Quality Checklist

Before committing scanner fallback implementation:

- [ ] All patterns defined as static final constants
- [ ] Uses RegexPatterns utility for common extractions
- [ ] createFallbackStrategy() method properly documented
- [ ] extractFromAST() method refactored from existing code
- [ ] scan() method updated to use parseWithFallback()
- [ ] Statistics logged with success and overall parse rates
- [ ] Tests added for AST, fallback, and mixed scenarios
- [ ] Deprecated old parsing methods with @Deprecated annotation
- [ ] No magic strings in regex patterns
- [ ] Javadoc updated to mention three-tier approach

## Example: Complete Scanner Refactoring

See `SpringRestApiScanner.java` for the gold standard implementation:
- Lines 103-109: Pattern constants
- Lines 273-310: extractEndpointsFromAST() (Tier 1)
- Lines 326-367: createFallbackStrategy() (Tier 2)
- Lines 210-265: scan() method with statistics tracking

## Benefits Summary

### Before Refactoring
- ❌ 70+ lines of duplicate code per scanner
- ❌ Patterns recompiled on every invocation
- ❌ Inconsistent extraction logic
- ❌ Magic strings hard to maintain

### After Refactoring
- ✅ ~30 lines per scanner (50% reduction)
- ✅ Patterns compiled once
- ✅ Consistent extraction via RegexPatterns
- ✅ All patterns as named constants
- ✅ Better performance
- ✅ Easier to test and maintain

## Next Steps

1. Continue implementing fallback in remaining 36 scanners using this template
2. Extract more common patterns to RegexPatterns as needed
3. Consider language-specific pattern utilities (e.g., PythonRegexPatterns, GoRegexPatterns)
4. Add integration tests across multiple scanners
5. Benchmark performance improvements
6. Update IMPLEMENTATION_SUMMARY.md when complete
