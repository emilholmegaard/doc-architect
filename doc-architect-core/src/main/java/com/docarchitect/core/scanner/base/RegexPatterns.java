package com.docarchitect.core.scanner.base;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared regex patterns for fallback parsing across scanners.
 *
 * <p>This utility class provides pre-compiled regex patterns for common Java
 * code extraction tasks. Patterns are compiled once at class loading time for
 * optimal performance.
 *
 * <h2>Design Rationale</h2>
 * <ul>
 *   <li><b>Performance:</b> Pre-compiled patterns avoid repeated compilation overhead</li>
 *   <li><b>Consistency:</b> Ensures all scanners use the same extraction logic</li>
 *   <li><b>Maintainability:</b> Centralized location for pattern updates</li>
 *   <li><b>Testability:</b> Patterns can be tested independently</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * String content = Files.readString(javaFile);
 * String className = RegexPatterns.extractClassName(content, javaFile);
 * String packageName = RegexPatterns.extractPackageName(content);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class RegexPatterns {

    // Java language patterns
    public static final Pattern CLASS_NAME_PATTERN =
        Pattern.compile("(?:public\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");

    public static final Pattern PACKAGE_PATTERN =
        Pattern.compile("package\\s+([\\w.]+)\\s*;");

    public static final Pattern FIELD_PATTERN =
        Pattern.compile("private\\s+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*;");

    // Private constructor to prevent instantiation
    private RegexPatterns() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Extracts class name from Java file content.
     *
     * <p>Matches class declarations with optional public/abstract modifiers.
     * If no class is found, falls back to the filename (without .java extension).
     *
     * @param content Java source file content
     * @param file path to the file (used as fallback)
     * @return class name
     */
    public static String extractClassName(String content, Path file) {
        Matcher matcher = CLASS_NAME_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Fallback to filename
        String fileName = file.getFileName().toString();
        return fileName.replace(".java", "");
    }

    /**
     * Extracts package name from Java file content.
     *
     * <p>Matches standard package declarations (e.g., {@code package com.example.app;}).
     * Returns empty string if no package declaration is found (default package).
     *
     * @param content Java source file content
     * @return package name or empty string if not found
     */
    public static String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Builds fully qualified class name from package and class name.
     *
     * @param packageName package name (may be empty)
     * @param className simple class name
     * @return fully qualified class name
     */
    public static String buildFullyQualifiedName(String packageName, String className) {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }
}
