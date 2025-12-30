package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.ConfidenceLevel;
import com.docarchitect.core.scanner.ScanStatistics;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for scanners that parse Java source code using JavaParser AST.
 *
 * <p>This class provides JavaParser integration and common AST traversal utilities:
 * <ul>
 *   <li>Parsing Java files into CompilationUnit AST</li>
 *   <li>Finding classes, methods, and fields</li>
 *   <li>Extracting annotations and their values</li>
 *   <li>Type resolution and import handling</li>
 * </ul>
 *
 * <p>This base class is used by scanners for Java frameworks:
 * <ul>
 *   <li>Spring REST API Scanner (RestController, GetMapping, etc.)</li>
 *   <li>JPA Entity Scanner (Entity, Table, Column, relationships)</li>
 *   <li>Kafka Scanner (KafkaListener, SendTo, KafkaTemplate)</li>
 * </ul>
 *
 * <p><b>When to Use This Base Class</b></p>
 * <p>Use AbstractJavaParserScanner when:</p>
 * <ul>
 *   <li>Parsing Java source code (not bytecode)</li>
 *   <li>Need to extract annotations, method signatures, or class structures</li>
 *   <li>Regex would be too fragile for complex Java syntax</li>
 *   <li>Type information or import resolution is needed</li>
 * </ul>
 *
 * @see AbstractScanner
 * @since 1.0.0
 */
public abstract class AbstractJavaParserScanner extends AbstractScanner {

    /**
     * JavaParser instance for parsing Java source files.
     * Thread-safe and reusable across parse operations.
     */
    protected final JavaParser javaParser;

    /**
     * Constructor that initializes the JavaParser instance.
     */
    protected AbstractJavaParserScanner() {
        super();
        this.javaParser = new JavaParser();
    }

    // ==================== Java File Parsing ====================

    /**
     * Determines if a file should be scanned by this scanner.
     *
     * <p>This pre-filtering hook allows scanners to check file content before
     * attempting expensive AST parsing. Override this method to implement
     * framework-specific detection (e.g., checking for specific imports or annotations).
     *
     * <p><b>Default Implementation:</b> Returns {@code true} for all files.
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * @Override
     * protected boolean shouldScanFile(Path file) {
     *     try {
     *         String content = readFileContent(file);
     *         // Only scan files with Kafka imports
     *         return content.contains("org.apache.kafka") ||
     *                content.contains("org.springframework.kafka") ||
     *                content.contains("@KafkaListener");
     *     } catch (IOException e) {
     *         return false;
     *     }
     * }
     * }</pre>
     *
     * @param file path to the file to check
     * @return true if this file should be parsed, false to skip
     */
    protected boolean shouldScanFile(Path file) {
        return true; // Default: scan all files
    }

    /**
     * Parses a Java source file into a CompilationUnit AST.
     *
     * <p><b>Pre-filtering:</b> Calls {@link #shouldScanFile(Path)} before parsing.
     * If the file should not be scanned, returns empty without attempting to parse.
     *
     * @param file path to Java source file
     * @return CompilationUnit if parsing succeeded, empty if failed or file should be skipped
     * @throws IOException if file cannot be read
     */
    protected Optional<CompilationUnit> parseJavaFile(Path file) throws IOException {
        // Layer 1: Pre-filtering
        if (!shouldScanFile(file)) {
            log.debug("Skipping file (pre-filter): {}", file);
            return Optional.empty();
        }

        // Layer 2: Parse with error handling
        String content = readFileContent(file);
        com.github.javaparser.ParseResult<CompilationUnit> result = javaParser.parse(content);

        if (result.isSuccessful() && result.getResult().isPresent()) {
            return result.getResult();
        } else {
            log.debug("Failed to parse Java file: {}", file);
            result.getProblems().forEach(problem -> log.debug("  - {}", problem));
            return Optional.empty();
        }
    }

    /**
     * Result of parsing a Java file, including metadata for statistics tracking.
     *
     * @param <T> type of extracted data (e.g., ApiEndpoint, DataEntity)
     */
    protected static class FileParseResult<T> {
        private final List<T> data;
        private final ConfidenceLevel confidence;
        private final boolean success;
        private final String errorType;
        private final String errorDetail;

        private FileParseResult(List<T> data, ConfidenceLevel confidence, boolean success,
                           String errorType, String errorDetail) {
            this.data = data != null ? data : List.of();
            this.confidence = confidence;
            this.success = success;
            this.errorType = errorType;
            this.errorDetail = errorDetail;
        }

        public static <T> FileParseResult<T> success(List<T> data, ConfidenceLevel confidence) {
            return new FileParseResult<>(data, confidence, true, null, null);
        }

        public static <T> FileParseResult<T> failure(String errorType, String errorDetail) {
            return new FileParseResult<>(List.of(), null, false, errorType, errorDetail);
        }

        public List<T> getData() {
            return data;
        }

        public ConfidenceLevel getConfidence() {
            return confidence;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorType() {
            return errorType;
        }

        public String getErrorDetail() {
            return errorDetail;
        }
    }

    /**
     * Parses a Java file with fallback strategy support.
     *
     * <p>This method implements a three-tier parsing approach:
     * <ol>
     *   <li><b>Tier 1:</b> Full AST parsing via JavaParser (HIGH confidence)</li>
     *   <li><b>Tier 2:</b> Regex-based fallback parsing (MEDIUM confidence)</li>
     *   <li><b>Tier 3:</b> Return empty with error tracking (statistics only)</li>
     * </ol>
     *
     * <p><b>Statistics Tracking:</b></p>
     * <p>This method populates the statistics builder with:
     * <ul>
     *   <li>Success/fallback/failure counts</li>
     *   <li>Error types and details</li>
     *   <li>Confidence levels for results</li>
     * </ul>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * ScanStatistics.Builder stats = new ScanStatistics.Builder();
     * List<ApiEndpoint> allEndpoints = new ArrayList<>();
     *
     * for (Path file : javaFiles) {
     *     FileParseResult<ApiEndpoint> result = parseWithFallback(
     *         file,
     *         this::extractEndpointsFromAST,      // Tier 1: AST extraction
     *         this::extractEndpointsWithRegex,    // Tier 2: Regex fallback
     *         stats
     *     );
     *     allEndpoints.addAll(result.getData());
     * }
     * }</pre>
     *
     * @param <T> type of data extracted from file
     * @param file path to Java file
     * @param astExtractor function to extract data from CompilationUnit (Tier 1)
     * @param fallbackStrategy fallback parser for when AST fails (Tier 2)
     * @param statsBuilder statistics builder to track parse metrics
     * @return parse result with data, confidence level, and error info
     */
    protected <T> FileParseResult<T> parseWithFallback(
            Path file,
            java.util.function.Function<CompilationUnit, List<T>> astExtractor,
            FallbackParsingStrategy<T> fallbackStrategy,
            ScanStatistics.Builder statsBuilder) {

        statsBuilder.incrementFilesScanned();

        try {
            // Tier 1: Try full AST parsing
            Optional<CompilationUnit> cuOpt = parseJavaFile(file);

            if (cuOpt.isPresent()) {
                // AST parsing succeeded
                List<T> data = astExtractor.apply(cuOpt.get());
                statsBuilder.incrementFilesParsedSuccessfully();
                return FileParseResult.success(data, ConfidenceLevel.HIGH);
            }

            // Tier 2: AST failed, try fallback (regex)
            log.debug("AST parsing failed for {}, attempting fallback", file);
            String content = readFileContent(file);
            List<T> fallbackData = fallbackStrategy.parse(file, content);

            if (!fallbackData.isEmpty()) {
                statsBuilder.incrementFilesParsedWithFallback();
                return FileParseResult.success(fallbackData, ConfidenceLevel.MEDIUM);
            }

            // Tier 3: Both methods failed
            statsBuilder.incrementFilesFailed();
            String errorMsg = file.getFileName() + ": AST parsing failed, no fallback data extracted";
            statsBuilder.addError("AST parsing failure", errorMsg);
            return FileParseResult.failure("AST parsing failure", errorMsg);

        } catch (IOException e) {
            // File read error
            statsBuilder.incrementFilesFailed();
            String errorMsg = file.getFileName() + ": " + e.getMessage();
            statsBuilder.addError("File read error", errorMsg);
            return FileParseResult.failure("File read error", errorMsg);
        } catch (Exception e) {
            // Unexpected error
            statsBuilder.incrementFilesFailed();
            String errorMsg = file.getFileName() + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
            statsBuilder.addError("Unexpected error", errorMsg);
            log.warn("Unexpected error parsing file {}: {}", file, e.getMessage(), e);
            return FileParseResult.failure("Unexpected error", errorMsg);
        }
    }

    // ==================== Class and Type Utilities ====================

    /**
     * Finds all classes in a compilation unit.
     *
     * @param cu compilation unit
     * @return list of class declarations
     */
    protected List<ClassOrInterfaceDeclaration> findClasses(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class);
    }

    /**
     * Checks if a class has a specific annotation.
     *
     * @param clazz class declaration
     * @param annotationName simple name of annotation (e.g., "Entity", "RestController")
     * @return true if class has the annotation
     */
    protected boolean hasAnnotation(ClassOrInterfaceDeclaration clazz, String annotationName) {
        return clazz.getAnnotations().stream()
            .anyMatch(ann -> ann.getNameAsString().equals(annotationName));
    }

    /**
     * Gets the value of a class annotation attribute.
     *
     * @param clazz class declaration
     * @param annotationName simple name of annotation
     * @param attributeName name of the attribute
     * @return attribute value as string, or null if not found
     */
    protected String getAnnotationAttribute(ClassOrInterfaceDeclaration clazz, String annotationName, String attributeName) {
        return clazz.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals(annotationName))
            .findFirst()
            .flatMap(ann -> getAnnotationAttributeValue(ann, attributeName))
            .orElse(null);
    }

    /**
     * Extracts the package name from a compilation unit.
     *
     * @param cu compilation unit
     * @return package name, or empty string if no package
     */
    protected String getPackageName(CompilationUnit cu) {
        return cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");
    }

    /**
     * Gets the fully qualified name of a class.
     *
     * @param cu compilation unit containing the class
     * @param clazz class declaration
     * @return fully qualified class name (e.g., "com.example.User")
     */
    protected String getFullyQualifiedName(CompilationUnit cu, ClassOrInterfaceDeclaration clazz) {
        String packageName = getPackageName(cu);
        String className = clazz.getNameAsString();

        if (packageName.isEmpty()) {
            return className;
        }

        return packageName + "." + className;
    }

    // ==================== Method Utilities ====================

    /**
     * Finds all methods in a class that have a specific annotation.
     *
     * @param clazz class declaration
     * @param annotationName simple name of annotation
     * @return list of methods with the annotation
     */
    protected List<MethodDeclaration> findMethodsWithAnnotation(ClassOrInterfaceDeclaration clazz, String annotationName) {
        return clazz.getMethods().stream()
            .filter(method -> hasAnnotation(method, annotationName))
            .toList();
    }

    /**
     * Checks if a method has a specific annotation.
     *
     * @param method method declaration
     * @param annotationName simple name of annotation
     * @return true if method has the annotation
     */
    protected boolean hasAnnotation(MethodDeclaration method, String annotationName) {
        return method.getAnnotations().stream()
            .anyMatch(ann -> ann.getNameAsString().equals(annotationName));
    }

    /**
     * Gets the value of a method annotation attribute.
     *
     * @param method method declaration
     * @param annotationName simple name of annotation
     * @param attributeName name of the attribute
     * @return attribute value as string, or null if not found
     */
    protected String getAnnotationAttribute(MethodDeclaration method, String annotationName, String attributeName) {
        return method.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals(annotationName))
            .findFirst()
            .flatMap(ann -> getAnnotationAttributeValue(ann, attributeName))
            .orElse(null);
    }

    // ==================== Field Utilities ====================

    /**
     * Finds all fields in a class.
     *
     * @param clazz class declaration
     * @return list of field declarations
     */
    protected List<FieldDeclaration> findFields(ClassOrInterfaceDeclaration clazz) {
        return new ArrayList<>(clazz.getFields());
    }

    /**
     * Checks if a field has a specific annotation.
     *
     * @param field field declaration
     * @param annotationName simple name of annotation
     * @return true if field has the annotation
     */
    protected boolean hasAnnotation(FieldDeclaration field, String annotationName) {
        return field.getAnnotations().stream()
            .anyMatch(ann -> ann.getNameAsString().equals(annotationName));
    }

    /**
     * Gets the value of a field annotation attribute.
     *
     * @param field field declaration
     * @param annotationName simple name of annotation
     * @param attributeName name of the attribute
     * @return attribute value as string, or null if not found
     */
    protected String getAnnotationAttribute(FieldDeclaration field, String annotationName, String attributeName) {
        return field.getAnnotations().stream()
            .filter(ann -> ann.getNameAsString().equals(annotationName))
            .findFirst()
            .flatMap(ann -> getAnnotationAttributeValue(ann, attributeName))
            .orElse(null);
    }

    // ==================== Annotation Value Extraction ====================

    /**
     * Extracts an attribute value from an annotation.
     *
     * <p>Handles both explicit attributes (name = "value") and default value attributes.
     *
     * @param annotation annotation expression
     * @param attributeName name of the attribute
     * @return attribute value as string, or empty if not found
     */
    protected Optional<String> getAnnotationAttributeValue(AnnotationExpr annotation, String attributeName) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            // Single value: @RequestMapping("/api/users")
            if ("value".equals(attributeName)) {
                return Optional.of(cleanStringLiteral(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString()));
            }
        } else if (annotation.isNormalAnnotationExpr()) {
            // Named attributes: @RequestMapping(path = "/api/users", method = GET)
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                .filter(pair -> pair.getNameAsString().equals(attributeName))
                .findFirst()
                .map(pair -> cleanStringLiteral(pair.getValue().toString()));
        }

        return Optional.empty();
    }

    /**
     * Removes surrounding quotes from a string literal.
     *
     * <p>Converts "value" to value, 'value' to value.
     *
     * @param literal string literal from AST
     * @return cleaned string
     */
    protected String cleanStringLiteral(String literal) {
        if (literal == null) {
            return "";
        }

        String trimmed = literal.trim();

        // Remove surrounding quotes
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }

        return trimmed;
    }

    /**
     * Converts a CamelCase class name to snake_case.
     *
     * <p>Examples: UserAccount to user_account, OrderItem to order_item.
     *
     * @param camelCase CamelCase string
     * @return snake_case string
     */
    protected String toSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }

        return camelCase
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
            .toLowerCase();
    }
}
