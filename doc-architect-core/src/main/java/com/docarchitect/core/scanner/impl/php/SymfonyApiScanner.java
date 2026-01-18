package com.docarchitect.core.scanner.impl.php;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Symfony REST endpoints in PHP source files.
 *
 * <p>Parses Symfony route annotations and attributes to extract API endpoints.
 * Supports both PHP 8 attributes and legacy Doctrine annotations.
 *
 * <p><b>Supported Patterns - PHP 8 Attributes</b></p>
 * <ul>
 *   <li>{@code #[Route('/api/users', methods: ['GET'])]} - Route with methods</li>
 *   <li>{@code #[Route('/api/users/{id}', name: 'user_show')]} - Route with name</li>
 *   <li>{@code #[Route('/api/users', methods: ['GET', 'POST'])]} - Multiple methods</li>
 * </ul>
 *
 * <p><b>Supported Patterns - Doctrine Annotations (Legacy)</b></p>
 * <ul>
 *   <li>{@code @Route("/api/users", methods={"GET"})} - Route with methods</li>
 *   <li>{@code @Route("/api/users/{id}", name="user_show")} - Route with name</li>
 * </ul>
 *
 * <p><b>Path Parameter Extraction</b></p>
 * <ul>
 *   <li>Required parameters: {@code {id}}, {@code {slug}}</li>
 *   <li>Parameters with requirements: {@code {id<\d+>}}</li>
 * </ul>
 *
 * <p><b>Controller Detection</b></p>
 * <p>Extracts controller class name and method name for endpoint description.</p>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class SymfonyApiScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "symfony-api";
    private static final String SCANNER_DISPLAY_NAME = "Symfony API Scanner";
    private static final String PATTERN_PHP_FILES = "**/*.php";
    private static final String PATTERN_CONTROLLER_FILES = "**/*Controller.php";

    private static final int SCANNER_PRIORITY = 50;

    /**
     * Regex to match PHP 8 Route attribute.
     * Matches: #[Route('/path', methods: ['GET'])] or #[Route('/path')]
     * Captures: (1) path, (2) rest of attribute content (optional)
     *
     * Note: Uses .*? with DOTALL to match attribute content including nested brackets.
     */
    private static final Pattern PHP8_ROUTE_ATTRIBUTE = Pattern.compile(
        "#\\[Route\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*(?:,\\s*(.*?))?\\)\\s*\\]",
        Pattern.DOTALL
    );

    /**
     * Regex to match Doctrine Route annotation.
     * Matches: @Route("/path", methods={"GET"})
     * Captures: (1) path, (2) rest of annotation content
     */
    private static final Pattern DOCTRINE_ROUTE_ANNOTATION = Pattern.compile(
        "@Route\\s*\\(\\s*['\"]([^'\"]+)['\"]([^)]*?)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract methods from PHP 8 attribute.
     * Matches: methods: ['GET', 'POST'] or methods: ["GET"]
     * Captures: (1) method list content
     */
    private static final Pattern PHP8_METHODS_PATTERN = Pattern.compile(
        "methods\\s*:\\s*\\[([^\\]]+)\\]"
    );

    /**
     * Regex to extract methods from Doctrine annotation.
     * Matches: methods={"GET", "POST"} or methods={"GET"}
     * Captures: (1) method list content
     */
    private static final Pattern DOCTRINE_METHODS_PATTERN = Pattern.compile(
        "methods\\s*=\\s*\\{([^}]+)\\}"
    );

    /**
     * Regex to extract route name from attribute/annotation.
     * Matches: name: 'route_name' or name="route_name"
     * Captures: (1) route name
     */
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "name\\s*[=:]\\s*['\"]([^'\"]+)['\"]"
    );

    /**
     * Regex to extract path parameters from Symfony routes.
     * Matches: {id}, {slug}, {id<\d+>}
     * Captures: (1) parameter name (without requirements)
     */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(
        "\\{([a-zA-Z_][a-zA-Z0-9_]*)(?:<[^>]+>)?\\}"
    );

    /**
     * Regex to match PHP class definition.
     * Captures: (1) class name
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "class\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+"
    );

    /**
     * Regex to match PHP function definition.
     * Captures: (1) function name
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "(?:public|protected|private)?\\s*function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\("
    );

    /**
     * Regex to extract individual HTTP methods from method list.
     */
    private static final Pattern METHOD_EXTRACTOR = Pattern.compile(
        "['\"]([A-Z]+)['\"]"
    );

    private static final String PHP_FILE_EXTENSION = "\\.php$";
    private static final String SCHEMA_PATH_PREFIX = "Path: ";
    private static final String DEFAULT_METHOD = "GET";

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.PHP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_PHP_FILES, PATTERN_CONTROLLER_FILES);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasFiles("**/*.php")
            .and(ApplicabilityStrategies.hasDependency("symfony")
                .or(ApplicabilityStrategies.hasFileContaining(
                    "use Symfony\\Component\\Routing\\Annotation\\Route",
                    "use Symfony\\Component\\Routing\\Attribute\\Route",
                    "#[Route(",
                    "@Route("
                )));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Symfony API endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();

        // Scan controller files first (most likely to have routes)
        List<Path> controllerFiles = context.findFiles(PATTERN_CONTROLLER_FILES).toList();
        for (Path controllerFile : controllerFiles) {
            try {
                parseControllerFile(controllerFile, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse Symfony controller: {} - {}", controllerFile, e.getMessage());
            }
        }

        // Also scan other PHP files for routes
        List<Path> otherPhpFiles = context.findFiles(PATTERN_PHP_FILES)
            .filter(f -> !f.toString().endsWith("Controller.php"))
            .toList();

        for (Path phpFile : otherPhpFiles) {
            try {
                if (shouldScanFile(phpFile)) {
                    parseControllerFile(phpFile, apiEndpoints);
                }
            } catch (Exception e) {
                log.debug("Failed to parse PHP file for Symfony routes: {} - {}", phpFile, e.getMessage());
            }
        }

        log.info("Found {} Symfony API endpoints", apiEndpoints.size());

        return buildSuccessResult(
            List.of(),           // No components
            List.of(),           // No dependencies
            apiEndpoints,        // API endpoints
            List.of(),           // No message flows
            List.of(),           // No data entities
            List.of(),           // No relationships
            List.of()            // No warnings
        );
    }

    /**
     * Pre-filters files to only scan those likely to contain route definitions.
     */
    private boolean shouldScanFile(Path file) throws IOException {
        String content = readFileContent(file);
        return content.contains("#[Route") || content.contains("@Route");
    }

    /**
     * Parses a PHP controller file for Symfony route definitions.
     */
    private void parseControllerFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(file);
        List<String> lines = readFileLines(file);

        // Extract class name for component ID
        String className = extractClassName(content);
        String componentId = className != null ? className : extractComponentId(file);

        // Extract class-level route prefix (if any)
        String classPrefix = extractClassLevelPrefix(content);

        // Parse PHP 8 attributes
        parsePhp8Attributes(content, lines, componentId, classPrefix, apiEndpoints);

        // Parse Doctrine annotations
        parseDoctrineAnnotations(content, lines, componentId, classPrefix, apiEndpoints);
    }

    /**
     * Parses PHP 8 Route attributes.
     */
    private void parsePhp8Attributes(String content, List<String> lines, String componentId,
                                     String classPrefix, List<ApiEndpoint> apiEndpoints) {
        Matcher matcher = PHP8_ROUTE_ATTRIBUTE.matcher(content);

        while (matcher.find()) {
            String path = matcher.group(1);
            String attributeContent = matcher.group(2);

            // Skip class-level routes (detected separately)
            if (isClassLevelRoute(content, matcher.start())) {
                continue;
            }

            String fullPath = applyPrefix(classPrefix, path);
            List<String> methods = extractMethodsFromPhp8(attributeContent);
            String routeName = extractRouteName(attributeContent);
            String methodName = findNextFunctionName(content, matcher.end());

            String description = buildDescription(componentId, methodName, routeName);
            List<String> pathParams = extractPathParameters(path);
            String requestSchema = pathParams.isEmpty() ? null : SCHEMA_PATH_PREFIX + String.join(", ", pathParams);

            // Create endpoint for each HTTP method
            for (String method : methods) {
                ApiEndpoint endpoint = new ApiEndpoint(
                    componentId,
                    ApiType.REST,
                    fullPath,
                    method,
                    description,
                    requestSchema,
                    null,
                    null
                );
                apiEndpoints.add(endpoint);
                log.debug("Found Symfony route (PHP 8): {} {}", method, fullPath);
            }
        }
    }

    /**
     * Parses Doctrine Route annotations.
     */
    private void parseDoctrineAnnotations(String content, List<String> lines, String componentId,
                                          String classPrefix, List<ApiEndpoint> apiEndpoints) {
        Matcher matcher = DOCTRINE_ROUTE_ANNOTATION.matcher(content);

        while (matcher.find()) {
            String path = matcher.group(1);
            String annotationContent = matcher.group(2);

            // Skip class-level routes
            if (isClassLevelRoute(content, matcher.start())) {
                continue;
            }

            String fullPath = applyPrefix(classPrefix, path);
            List<String> methods = extractMethodsFromDoctrine(annotationContent);
            String routeName = extractRouteName(annotationContent);
            String methodName = findNextFunctionName(content, matcher.end());

            String description = buildDescription(componentId, methodName, routeName);
            List<String> pathParams = extractPathParameters(path);
            String requestSchema = pathParams.isEmpty() ? null : SCHEMA_PATH_PREFIX + String.join(", ", pathParams);

            // Create endpoint for each HTTP method
            for (String method : methods) {
                ApiEndpoint endpoint = new ApiEndpoint(
                    componentId,
                    ApiType.REST,
                    fullPath,
                    method,
                    description,
                    requestSchema,
                    null,
                    null
                );
                apiEndpoints.add(endpoint);
                log.debug("Found Symfony route (Doctrine): {} {}", method, fullPath);
            }
        }
    }

    /**
     * Checks if a route is at class level (before the class body, not a method annotation).
     *
     * <p>A class-level route in Symfony is an attribute placed directly above the class definition,
     * not inside the class body. Method-level routes are inside the class body, typically
     * right before a function definition.</p>
     */
    private boolean isClassLevelRoute(String content, int routePosition) {
        // Find the class definition
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) {
            return false;
        }

        int classStart = classMatcher.start();

        // Route is class-level if it appears before the class keyword
        if (routePosition < classStart) {
            // Verify it's not too far before (e.g., in a comment or another class)
            // It should be within ~200 chars before class definition
            String betweenRouteAndClass = content.substring(routePosition, classStart);
            // If there's another class definition between route and this class, it's not for this class
            if (betweenRouteAndClass.contains("class ")) {
                return false;
            }
            return true;
        }

        // Route is inside class body - it's a method-level route
        return false;
    }

    /**
     * Extracts class-level route prefix.
     */
    private String extractClassLevelPrefix(String content) {
        // Find class definition
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        if (!classMatcher.find()) {
            return "";
        }

        int classStart = classMatcher.start();

        // Look for route attribute/annotation before the class
        String beforeClass = content.substring(0, classStart);

        // Check for PHP 8 attribute
        Matcher php8Matcher = PHP8_ROUTE_ATTRIBUTE.matcher(beforeClass);
        int lastRouteEnd = -1;
        String lastPath = "";

        while (php8Matcher.find()) {
            lastRouteEnd = php8Matcher.end();
            lastPath = php8Matcher.group(1);
        }

        // Check for Doctrine annotation
        Matcher doctrineMatcher = DOCTRINE_ROUTE_ANNOTATION.matcher(beforeClass);
        while (doctrineMatcher.find()) {
            if (doctrineMatcher.end() > lastRouteEnd) {
                lastRouteEnd = doctrineMatcher.end();
                lastPath = doctrineMatcher.group(1);
            }
        }

        return lastPath;
    }

    /**
     * Extracts methods from PHP 8 attribute content.
     *
     * @param attributeContent the content after the path in the Route attribute (may be null)
     * @return list of HTTP methods, defaults to GET if none specified
     */
    private List<String> extractMethodsFromPhp8(String attributeContent) {
        if (attributeContent == null || attributeContent.isEmpty()) {
            return List.of(DEFAULT_METHOD);
        }

        List<String> methods = new ArrayList<>();
        Matcher matcher = PHP8_METHODS_PATTERN.matcher(attributeContent);

        if (matcher.find()) {
            String methodList = matcher.group(1);
            Matcher methodMatcher = METHOD_EXTRACTOR.matcher(methodList);
            while (methodMatcher.find()) {
                methods.add(methodMatcher.group(1));
            }
        }

        return methods.isEmpty() ? List.of(DEFAULT_METHOD) : methods;
    }

    /**
     * Extracts methods from Doctrine annotation content.
     */
    private List<String> extractMethodsFromDoctrine(String annotationContent) {
        List<String> methods = new ArrayList<>();
        Matcher matcher = DOCTRINE_METHODS_PATTERN.matcher(annotationContent);

        if (matcher.find()) {
            String methodList = matcher.group(1);
            Matcher methodMatcher = METHOD_EXTRACTOR.matcher(methodList);
            while (methodMatcher.find()) {
                methods.add(methodMatcher.group(1));
            }
        }

        return methods.isEmpty() ? List.of(DEFAULT_METHOD) : methods;
    }

    /**
     * Extracts route name from attribute/annotation content.
     *
     * @param content the attribute/annotation content (may be null)
     * @return the route name if found, null otherwise
     */
    private String extractRouteName(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        Matcher matcher = NAME_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Finds the next function name after a given position.
     */
    private String findNextFunctionName(String content, int startPos) {
        String remaining = content.substring(Math.min(startPos, content.length()));
        Matcher matcher = FUNCTION_PATTERN.matcher(remaining);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Builds endpoint description from controller, method, and route name.
     */
    private String buildDescription(String controller, String method, String routeName) {
        if (routeName != null) {
            return routeName;
        }
        if (method != null) {
            return controller + "::" + method;
        }
        return controller;
    }

    /**
     * Applies a prefix to a path, normalizing slashes.
     */
    private String applyPrefix(String prefix, String path) {
        if (prefix == null || prefix.isEmpty()) {
            return normalizePath(path);
        }

        String normalizedPrefix = prefix.startsWith("/") ? prefix : "/" + prefix;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;

        // Avoid double slashes
        if (normalizedPrefix.endsWith("/") && normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        return normalizePath(normalizedPrefix + normalizedPath);
    }

    /**
     * Normalizes a path to ensure it starts with / and has no trailing slash.
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }

        String normalized = path.startsWith("/") ? path : "/" + path;

        // Remove trailing slash unless it's the root path
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Replace multiple slashes with single slash
        normalized = normalized.replaceAll("/+", "/");

        return normalized;
    }

    /**
     * Extracts path parameters from a route path.
     */
    private List<String> extractPathParameters(String path) {
        List<String> params = new ArrayList<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    /**
     * Extracts the class name from file content.
     */
    private String extractClassName(String content) {
        Matcher matcher = CLASS_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts a component ID from the file path.
     */
    private String extractComponentId(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll(PHP_FILE_EXTENSION, "");
    }
}
