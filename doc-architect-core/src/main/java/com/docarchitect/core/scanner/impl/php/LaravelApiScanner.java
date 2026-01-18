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
 * Scanner for Laravel REST endpoints in PHP source files.
 *
 * <p>Parses Laravel route definitions using regex patterns to extract API endpoints.
 * Supports both route files (routes/web.php, routes/api.php) and controller-based routing.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code Route::get('/users', [UserController::class, 'index'])} - Class-based routing</li>
 *   <li>{@code Route::post('/users', 'UserController@store')} - String-based routing</li>
 *   <li>{@code Route::resource('posts', PostController::class)} - Resource routes</li>
 *   <li>{@code Route::apiResource('comments', CommentController::class)} - API resource routes</li>
 *   <li>All HTTP methods: GET, POST, PUT, DELETE, PATCH, OPTIONS</li>
 * </ul>
 *
 * <p><b>Path Parameter Extraction</b></p>
 * <ul>
 *   <li>Required parameters: {@code {id}}, {@code {user}}</li>
 *   <li>Optional parameters: {@code {id?}}</li>
 *   <li>Constrained parameters: {@code {id}} with where clause</li>
 * </ul>
 *
 * <p><b>Route Groups</b></p>
 * <p>Detects route prefixes from group definitions where possible:</p>
 * <ul>
 *   <li>{@code Route::prefix('api/v1')->group(...)}</li>
 *   <li>{@code Route::group(['prefix' => 'admin'], ...)}</li>
 * </ul>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class LaravelApiScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "laravel-api";
    private static final String SCANNER_DISPLAY_NAME = "Laravel API Scanner";
    private static final String PATTERN_PHP_FILES = "**/*.php";
    private static final String PATTERN_ROUTES_ROOT = "routes/*.php";
    private static final String PATTERN_ROUTES_NESTED = "**/routes/*.php";

    private static final int SCANNER_PRIORITY = 50;

    /**
     * Regex to match Laravel route definitions.
     * Matches: Route::get('/path', ...), Route::post('/path', ...), etc.
     * Captures: (1) HTTP method, (2) path
     */
    private static final Pattern ROUTE_METHOD_PATTERN = Pattern.compile(
        "Route::(get|post|put|patch|delete|options)\\s*\\(\\s*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Regex to match Laravel resource routes.
     * Matches: Route::resource('posts', PostController::class)
     * Captures: (1) resource type (resource/apiResource), (2) resource name
     */
    private static final Pattern RESOURCE_ROUTE_PATTERN = Pattern.compile(
        "Route::(resource|apiResource)\\s*\\(\\s*['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Regex to match controller action from route definition.
     * Matches: [UserController::class, 'index'] or 'UserController@index'
     * Captures: (1) controller name, (2) action name
     */
    private static final Pattern CONTROLLER_ACTION_PATTERN = Pattern.compile(
        "(?:\\[\\s*([A-Za-z0-9_\\\\]+)::class\\s*,\\s*['\"]([^'\"]+)['\"]\\])|" +
        "(?:['\"]([A-Za-z0-9_\\\\]+)@([^'\"]+)['\"])"
    );

    /**
     * Regex to extract path parameters from Laravel routes.
     * Matches: {id}, {user?}, {post}
     * Captures: (1) parameter name (without optional marker)
     */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(
        "\\{([a-zA-Z_][a-zA-Z0-9_]*)\\??\\}"
    );

    /**
     * Regex to match route prefix definitions.
     * Matches: Route::prefix('api/v1') or ['prefix' => 'admin']
     * Captures: (1) prefix path
     */
    private static final Pattern PREFIX_PATTERN = Pattern.compile(
        "(?:Route::prefix\\s*\\(\\s*['\"]([^'\"]+)['\"])|" +
        "(?:['\"]prefix['\"]\\s*=>\\s*['\"]([^'\"]+)['\"])"
    );

    private static final String PHP_FILE_EXTENSION = "\\.php$";
    private static final String SCHEMA_PATH_PREFIX = "Path: ";

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
        return Set.of(PATTERN_PHP_FILES, PATTERN_ROUTES_ROOT, PATTERN_ROUTES_NESTED);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasFiles("**/*.php")
            .and(ApplicabilityStrategies.hasDependency("laravel")
                .or(ApplicabilityStrategies.hasFileContaining(
                    "use Illuminate\\Support\\Facades\\Route",
                    "Route::get",
                    "Route::post",
                    "Route::resource"
                )));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Laravel API endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();

        // First, scan route files specifically (both root and nested patterns)
        List<Path> routeFiles = new ArrayList<>();
        context.findFiles(PATTERN_ROUTES_ROOT).forEach(routeFiles::add);
        context.findFiles(PATTERN_ROUTES_NESTED).forEach(routeFiles::add);

        for (Path routeFile : routeFiles) {
            try {
                parseRouteFile(routeFile, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse Laravel route file: {} - {}", routeFile, e.getMessage());
            }
        }

        // Then scan PHP files for inline route definitions (less common but possible)
        List<Path> phpFiles = context.findFiles(PATTERN_PHP_FILES)
            .filter(f -> !isRouteFile(f))
            .toList();

        for (Path phpFile : phpFiles) {
            try {
                if (shouldScanFile(phpFile)) {
                    parseRouteFile(phpFile, apiEndpoints);
                }
            } catch (Exception e) {
                log.debug("Failed to parse PHP file for routes: {} - {}", phpFile, e.getMessage());
            }
        }

        log.info("Found {} Laravel API endpoints", apiEndpoints.size());

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
     * Checks if a file is a Laravel route file.
     */
    private boolean isRouteFile(Path file) {
        String pathStr = file.toString().replace('\\', '/');
        return pathStr.contains("/routes/");
    }

    /**
     * Pre-filters files to only scan those likely to contain route definitions.
     */
    private boolean shouldScanFile(Path file) throws IOException {
        String content = readFileContent(file);
        return content.contains("Route::") || content.contains("Illuminate\\Support\\Facades\\Route");
    }

    /**
     * Parses a PHP file for Laravel route definitions.
     */
    private void parseRouteFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(file);
        String componentId = extractComponentId(file);

        // Detect any global prefix from the file
        String globalPrefix = extractGlobalPrefix(content);

        // Extract individual route definitions
        extractMethodRoutes(content, componentId, globalPrefix, apiEndpoints);

        // Extract resource routes
        extractResourceRoutes(content, componentId, globalPrefix, apiEndpoints);
    }

    /**
     * Extracts method-based routes (Route::get, Route::post, etc.)
     */
    private void extractMethodRoutes(String content, String componentId, String globalPrefix,
                                     List<ApiEndpoint> apiEndpoints) {
        Matcher matcher = ROUTE_METHOD_PATTERN.matcher(content);

        while (matcher.find()) {
            String httpMethod = matcher.group(1).toUpperCase();
            String path = matcher.group(2);

            // Apply global prefix if present
            String fullPath = applyPrefix(globalPrefix, path);

            // Try to extract controller and action
            String description = extractControllerAction(content, matcher.end());

            // Extract path parameters
            List<String> pathParams = extractPathParameters(path);
            String requestSchema = pathParams.isEmpty() ? null : SCHEMA_PATH_PREFIX + String.join(", ", pathParams);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                httpMethod,
                description,
                requestSchema,
                null,  // Response schema not determinable from routes
                null   // Authentication from middleware, not extracted here
            );

            apiEndpoints.add(endpoint);
            log.debug("Found Laravel route: {} {}", httpMethod, fullPath);
        }
    }

    /**
     * Extracts resource routes (Route::resource, Route::apiResource).
     */
    private void extractResourceRoutes(String content, String componentId, String globalPrefix,
                                       List<ApiEndpoint> apiEndpoints) {
        Matcher matcher = RESOURCE_ROUTE_PATTERN.matcher(content);

        while (matcher.find()) {
            String resourceType = matcher.group(1).toLowerCase();
            String resourceName = matcher.group(2);

            String basePath = applyPrefix(globalPrefix, "/" + resourceName);

            // Generate standard REST endpoints for resource
            boolean isApiResource = "apiresource".equals(resourceType);
            generateResourceEndpoints(basePath, resourceName, componentId, isApiResource, apiEndpoints);
        }
    }

    /**
     * Generates standard REST endpoints for a Laravel resource.
     */
    private void generateResourceEndpoints(String basePath, String resourceName, String componentId,
                                           boolean isApiResource, List<ApiEndpoint> apiEndpoints) {
        String singularParam = "{" + getSingularForm(resourceName) + "}";

        // Standard resource routes
        // index - GET /resources
        apiEndpoints.add(createResourceEndpoint(componentId, basePath, "GET", resourceName + ".index"));

        // store - POST /resources
        apiEndpoints.add(createResourceEndpoint(componentId, basePath, "POST", resourceName + ".store"));

        // show - GET /resources/{id}
        apiEndpoints.add(createResourceEndpoint(componentId, basePath + "/" + singularParam, "GET",
            resourceName + ".show", singularParam));

        // update - PUT/PATCH /resources/{id}
        apiEndpoints.add(createResourceEndpoint(componentId, basePath + "/" + singularParam, "PUT",
            resourceName + ".update", singularParam));

        // destroy - DELETE /resources/{id}
        apiEndpoints.add(createResourceEndpoint(componentId, basePath + "/" + singularParam, "DELETE",
            resourceName + ".destroy", singularParam));

        // Non-API resources also include create and edit views
        if (!isApiResource) {
            // create - GET /resources/create
            apiEndpoints.add(createResourceEndpoint(componentId, basePath + "/create", "GET",
                resourceName + ".create"));

            // edit - GET /resources/{id}/edit
            apiEndpoints.add(createResourceEndpoint(componentId, basePath + "/" + singularParam + "/edit", "GET",
                resourceName + ".edit", singularParam));
        }
    }

    /**
     * Creates an ApiEndpoint for a resource route.
     */
    private ApiEndpoint createResourceEndpoint(String componentId, String path, String method,
                                               String description) {
        return createResourceEndpoint(componentId, path, method, description, null);
    }

    /**
     * Creates an ApiEndpoint for a resource route with path parameter.
     */
    private ApiEndpoint createResourceEndpoint(String componentId, String path, String method,
                                               String description, String pathParam) {
        String requestSchema = pathParam != null ? SCHEMA_PATH_PREFIX + pathParam : null;

        return new ApiEndpoint(
            componentId,
            ApiType.REST,
            path,
            method,
            description,
            requestSchema,
            null,
            null
        );
    }

    /**
     * Attempts to get a singular form of a resource name.
     *
     * <p>Simple heuristic implementation for common English pluralization patterns:</p>
     * <ul>
     *   <li>words ending in "ies" → replace with "y" (e.g., "categories" → "category")</li>
     *   <li>words ending in "es" after s/x/z/ch/sh → remove "es" (e.g., "boxes" → "box")</li>
     *   <li>words ending in "s" → remove "s" (e.g., "articles" → "article")</li>
     * </ul>
     */
    private String getSingularForm(String plural) {
        if (plural.endsWith("ies") && plural.length() > 3) {
            return plural.substring(0, plural.length() - 3) + "y";
        } else if (plural.endsWith("ses") || plural.endsWith("xes") || plural.endsWith("zes") ||
                   plural.endsWith("ches") || plural.endsWith("shes")) {
            return plural.substring(0, plural.length() - 2);
        } else if (plural.endsWith("s") && plural.length() > 1) {
            return plural.substring(0, plural.length() - 1);
        }
        return plural;
    }

    /**
     * Extracts controller@action from the route definition context.
     */
    private String extractControllerAction(String content, int searchStart) {
        // Look for controller action within the next 200 characters
        int endIndex = Math.min(searchStart + 200, content.length());
        String searchArea = content.substring(searchStart, endIndex);

        Matcher matcher = CONTROLLER_ACTION_PATTERN.matcher(searchArea);
        if (matcher.find()) {
            String controller = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String action = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);

            if (controller != null && action != null) {
                // Extract just the class name without namespace
                int lastBackslash = controller.lastIndexOf('\\');
                if (lastBackslash >= 0) {
                    controller = controller.substring(lastBackslash + 1);
                }
                return controller + "@" + action;
            }
        }
        return null;
    }

    /**
     * Extracts a global route prefix from the file content.
     */
    private String extractGlobalPrefix(String content) {
        Matcher matcher = PREFIX_PATTERN.matcher(content);
        if (matcher.find()) {
            String prefix = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return prefix != null ? prefix : "";
        }
        return "";
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
     * Extracts a component ID from the file path.
     */
    private String extractComponentId(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll(PHP_FILE_EXTENSION, "");
    }
}
