package com.docarchitect.core.scanner.base;

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
 * <h3>When to Use This Base Class</h3>
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
     * Parses a Java source file into a CompilationUnit AST.
     *
     * @param file path to Java source file
     * @return CompilationUnit if parsing succeeded, empty if failed
     * @throws IOException if file cannot be read
     */
    protected Optional<CompilationUnit> parseJavaFile(Path file) throws IOException {
        String content = readFileContent(file);
        ParseResult<CompilationUnit> result = javaParser.parse(content);

        if (result.isSuccessful() && result.getResult().isPresent()) {
            return result.getResult();
        } else {
            log.warn("Failed to parse Java file: {}", file);
            result.getProblems().forEach(problem -> log.debug("  - {}", problem));
            return Optional.empty();
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
