package com.docarchitect.core.scanner.impl.go;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Go HTTP router endpoints across multiple frameworks.
 *
 * <p>This scanner parses Go source files using regex patterns to extract REST API endpoint
 * information from popular Go HTTP frameworks: Gin, Echo, Chi, Gorilla Mux, Fiber, and net/http.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Go source files (*.go) using pattern matching</li>
 *   <li>Pre-filter files based on framework import detection</li>
 *   <li>Extract HTTP routes from framework-specific registration patterns</li>
 *   <li>Handle route groups and prefix concatenation</li>
 *   <li>Create ApiEndpoint records for each discovered endpoint</li>
 * </ol>
 *
 * <p><b>Supported Frameworks:</b>
 * <ul>
 *   <li><b>Gin:</b> {@code r.GET("/users/:id", handler)}</li>
 *   <li><b>Echo:</b> {@code e.GET("/users/:id", handler)}</li>
 *   <li><b>Chi:</b> {@code r.Get("/users/{id}", handler)}</li>
 *   <li><b>Gorilla Mux:</b> {@code r.HandleFunc("/users/{id}", handler).Methods("GET")}</li>
 *   <li><b>Fiber:</b> {@code app.Get("/users/:id", handler)}</li>
 *   <li><b>net/http:</b> {@code http.HandleFunc("/users", handler)}</li>
 * </ul>
 *
 * <p><b>Route Group Support:</b>
 * <pre>{@code
 * // Gin
 * v1 := r.Group("/api/v1")
 * v1.GET("/users", getUsers) // Detected as GET /api/v1/users
 *
 * // Chi
 * r.Route("/api", func(r chi.Router) {
 *     r.Get("/users", getUsers) // Detected as GET /api/users
 * })
 * }</pre>
 *
 * <p><b>Pre-filtering Strategy:</b>
 * Files are scanned only if they contain imports for supported frameworks:
 * <ul>
 *   <li>github.com/gin-gonic/gin</li>
 *   <li>github.com/labstack/echo</li>
 *   <li>github.com/go-chi/chi</li>
 *   <li>github.com/gorilla/mux</li>
 *   <li>github.com/gofiber/fiber</li>
 *   <li>net/http (stdlib)</li>
 * </ul>
 *
 * <p><b>Regex Patterns:</b>
 * <ul>
 *   <li>{@code GIN_ROUTE}: {@code (\w+)\.(?:GET|POST|PUT|DELETE|PATCH)\("([^"]+)",\s*(\w+)}
 *   <li>{@code ECHO_ROUTE}: {@code (\w+)\.(?:GET|POST|PUT|DELETE|PATCH)\("([^"]+)",\s*(\w+)}
 *   <li>{@code CHI_ROUTE}: {@code (\w+)\.(?:Get|Post|Put|Delete|Patch)\("([^"]+)",\s*(\w+)}
 *   <li>{@code MUX_ROUTE}: {@code (\w+)\.HandleFunc\("([^"]+)",\s*(\w+)\)\.Methods\("([^"]+)"}
 *   <li>{@code FIBER_ROUTE}: {@code (\w+)\.(?:Get|Post|Put|Delete|Patch)\("([^"]+)",\s*(\w+)}
 *   <li>{@code NET_HTTP_ROUTE}: {@code http\.HandleFunc\("([^"]+)",\s*(\w+)}
 *   <li>{@code GROUP_ASSIGNMENT}: {@code (\w+)\s*:=\s*(\w+)\.Group\("([^"]+)"}
 *   <li>{@code CHI_ROUTE_GROUP}: {@code (\w+)\.Route\("([^"]+)",\s*func}
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GoHttpRouterScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<ApiEndpoint> endpoints = result.apiEndpoints();
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class GoHttpRouterScanner extends AbstractRegexScanner {
    private static final String SCANNER_ID = "go-http-router";
    private static final String SCANNER_DISPLAY_NAME = "Go HTTP Router Scanner";
    private static final String GO_FILE_GLOB = "**/*.go";
    private static final Set<String> GO_FILE_PATTERNS = Set.of(GO_FILE_GLOB);
    private static final int PRIORITY = 50;

    // Framework import patterns
    private static final String GIN_IMPORT = "github.com/gin-gonic/gin";
    private static final String ECHO_IMPORT = "github.com/labstack/echo";
    private static final String CHI_IMPORT = "github.com/go-chi/chi";
    private static final String MUX_IMPORT = "github.com/gorilla/mux";
    private static final String FIBER_IMPORT = "github.com/gofiber/fiber";
    private static final String NET_HTTP_IMPORT = "net/http";

    /**
     * Gin route pattern: r.GET("/users/:id", getUser).
     * Captures: (1) router variable, (2) path, (3) handler.
     */
    private static final Pattern GIN_ROUTE = Pattern.compile(
        "(\\w+)\\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\(\"([^\"]+)\",\\s*(\\w+)"
    );

    /**
     * Echo route pattern: e.GET("/users/:id", getUser).
     * Captures: (1) router variable, (2) HTTP method, (3) path, (4) handler.
     */
    private static final Pattern ECHO_ROUTE = Pattern.compile(
        "(\\w+)\\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\(\"([^\"]+)\",\\s*(\\w+)"
    );

    /**
     * Chi route pattern: r.Get("/users/{id}", getUser).
     * Captures: (1) router variable, (2) HTTP method, (3) path, (4) handler.
     */
    private static final Pattern CHI_ROUTE = Pattern.compile(
        "(\\w+)\\.(Get|Post|Put|Delete|Patch|Head|Options)\\(\"([^\"]+)\",\\s*(\\w+)"
    );

    /**
     * Gorilla Mux route pattern: r.HandleFunc("/users/{id}", getUser).Methods("GET").
     * Captures: (1) router variable, (2) path, (3) handler, (4) HTTP method.
     */
    private static final Pattern MUX_ROUTE = Pattern.compile(
        "(\\w+)\\.HandleFunc\\(\"([^\"]+)\",\\s*(\\w+)\\)\\.Methods\\(\"([^\"]+)\""
    );

    /**
     * Fiber route pattern: app.Get("/users/:id", getUser).
     * Captures: (1) app variable, (2) HTTP method, (3) path, (4) handler.
     */
    private static final Pattern FIBER_ROUTE = Pattern.compile(
        "(\\w+)\\.(Get|Post|Put|Delete|Patch|Head|Options)\\(\"([^\"]+)\",\\s*(\\w+)"
    );

    /**
     * net/http route pattern: http.HandleFunc("/users", usersHandler).
     * Captures: (1) path, (2) handler.
     */
    private static final Pattern NET_HTTP_ROUTE = Pattern.compile(
        "http\\.HandleFunc\\(\"([^\"]+)\",\\s*(\\w+)"
    );

    /**
     * Route group assignment: v1 := r.Group("/api/v1").
     * Captures: (1) group variable, (2) parent router, (3) prefix.
     */
    private static final Pattern GROUP_ASSIGNMENT = Pattern.compile(
        "(\\w+)\\s*:=\\s*(\\w+)\\.Group\\(\"([^\"]+)\""
    );

    /**
     * Chi Route group: r.Route("/api", func(r chi.Router) { ... }).
     * Captures: (1) router variable, (2) prefix.
     */
    private static final Pattern CHI_ROUTE_GROUP = Pattern.compile(
        "(\\w+)\\.Route\\(\"([^\"]+)\",\\s*func"
    );

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
        return Set.of(Technologies.GO, Technologies.GOLANG);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return GO_FILE_PATTERNS;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, GO_FILE_PATTERNS.toArray(new String[0]));
    }

    /**
     * Pre-filter files to only scan those containing HTTP framework imports.
     *
     * <p>This avoids scanning Go files that don't contain HTTP routing code,
     * reducing unnecessary processing and improving performance.
     *
     * @param file path to Go source file
     * @return true if file contains HTTP framework imports, false otherwise
     */
    protected boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);

            // Check for framework imports
            boolean hasFrameworkImport =
                content.contains(GIN_IMPORT) ||
                content.contains(ECHO_IMPORT) ||
                content.contains(CHI_IMPORT) ||
                content.contains(MUX_IMPORT) ||
                content.contains(FIBER_IMPORT) ||
                (content.contains(NET_HTTP_IMPORT) && content.contains("HandleFunc"));

            if (hasFrameworkImport) {
                log.debug("Including file with HTTP framework imports: {}", file.getFileName());
            }

            return hasFrameworkImport;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Go HTTP routes in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        Set<Path> goFiles = new LinkedHashSet<>();

        GO_FILE_PATTERNS.forEach(pattern -> context.findFiles(pattern).forEach(goFiles::add));

        if (goFiles.isEmpty()) {
            log.warn("No Go files found in project");
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path goFile : goFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(goFile)) {
                skippedFiles++;
                continue;
            }

            try {
                parseGoRoutes(goFile, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                log.error("Failed to parse Go file: {}", goFile, e);
                // Continue processing other files instead of failing completely
            }
        }

        log.debug("Pre-filtered {} files (no HTTP framework imports)", skippedFiles);
        log.info("Found {} HTTP endpoints across {} Go files (parsed {}/{})",
            apiEndpoints.size(), goFiles.size(), parsedFiles, goFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single Go file and extracts HTTP routes.
     *
     * @param goFile path to Go file
     * @param apiEndpoints list to add discovered API endpoints
     * @throws IOException if file cannot be read
     */
    private void parseGoRoutes(Path goFile, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(goFile);
        String componentId = extractComponentId(goFile, content);

        // Track route group prefixes (variable name -> prefix)
        Map<String, String> groupPrefixes = new HashMap<>();

        // Extract route groups first
        extractRouteGroups(content, groupPrefixes);

        // Extract routes from each framework
        extractGinRoutes(content, componentId, apiEndpoints, groupPrefixes);
        extractEchoRoutes(content, componentId, apiEndpoints, groupPrefixes);
        extractChiRoutes(content, componentId, apiEndpoints, groupPrefixes);
        extractMuxRoutes(content, componentId, apiEndpoints, groupPrefixes);
        extractFiberRoutes(content, componentId, apiEndpoints, groupPrefixes);
        extractNetHttpRoutes(content, componentId, apiEndpoints);
    }

    /**
     * Extracts component ID from package name in Go file.
     *
     * @param goFile path to Go file
     * @param content file content
     * @return component ID (package name)
     */
    private String extractComponentId(Path goFile, String content) {
        // Extract package name from Go file
        Pattern packagePattern = Pattern.compile("^package\\s+(\\w+)", Pattern.MULTILINE);
        Matcher matcher = packagePattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        // Fallback to parent directory name
        return goFile.getParent().getFileName().toString();
    }

    /**
     * Extracts route group definitions and stores their prefixes.
     *
     * @param content file content
     * @param groupPrefixes map to store group variable -> prefix mappings
     */
    private void extractRouteGroups(String content, Map<String, String> groupPrefixes) {
        // Extract standard group assignments: v1 := r.Group("/api/v1")
        Matcher groupMatcher = GROUP_ASSIGNMENT.matcher(content);
        while (groupMatcher.find()) {
            String groupVar = groupMatcher.group(1);
            String parentVar = groupMatcher.group(2);
            String prefix = groupMatcher.group(3);

            // If parent has a prefix, concatenate them
            String parentPrefix = groupPrefixes.getOrDefault(parentVar, "");
            String fullPrefix = combinePaths(parentPrefix, prefix);

            groupPrefixes.put(groupVar, fullPrefix);
            log.debug("Found route group: {} with prefix {}", groupVar, fullPrefix);
        }

        // Extract Chi-style Route groups: r.Route("/api", func(r chi.Router) { ... })
        Matcher chiRouteMatcher = CHI_ROUTE_GROUP.matcher(content);
        while (chiRouteMatcher.find()) {
            String routerVar = chiRouteMatcher.group(1);
            String prefix = chiRouteMatcher.group(2);

            // Store the prefix for this router (Chi reuses variable names in nested scopes)
            groupPrefixes.put(routerVar + "_route", prefix);
            log.debug("Found Chi route group with prefix {}", prefix);
        }
    }

    /**
     * Extracts Gin framework routes.
     */
    private void extractGinRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                   Map<String, String> groupPrefixes) {
        if (!content.contains(GIN_IMPORT)) {
            return;
        }

        Matcher matcher = GIN_ROUTE.matcher(content);
        while (matcher.find()) {
            String routerVar = matcher.group(1);
            String method = matcher.group(2);
            String path = matcher.group(3);
            String handler = matcher.group(4);

            // Apply group prefix if router is a group
            String prefix = groupPrefixes.getOrDefault(routerVar, "");
            String fullPath = combinePaths(prefix, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                method,
                handler,
                null, // requestSchema
                null, // responseSchema
                null  // authentication
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Gin route: {} {} -> {}", method, fullPath, handler);
        }
    }

    /**
     * Extracts Echo framework routes.
     */
    private void extractEchoRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                    Map<String, String> groupPrefixes) {
        if (!content.contains(ECHO_IMPORT)) {
            return;
        }

        Matcher matcher = ECHO_ROUTE.matcher(content);
        while (matcher.find()) {
            String routerVar = matcher.group(1);
            String method = matcher.group(2);
            String path = matcher.group(3);
            String handler = matcher.group(4);

            String prefix = groupPrefixes.getOrDefault(routerVar, "");
            String fullPath = combinePaths(prefix, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                method,
                handler,
                null,
                null,
                null
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Echo route: {} {} -> {}", method, fullPath, handler);
        }
    }

    /**
     * Extracts Chi framework routes.
     */
    private void extractChiRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                   Map<String, String> groupPrefixes) {
        if (!content.contains(CHI_IMPORT)) {
            return;
        }

        Matcher matcher = CHI_ROUTE.matcher(content);
        while (matcher.find()) {
            String routerVar = matcher.group(1);
            String method = matcher.group(2).toUpperCase(); // Chi uses capitalized methods
            String path = matcher.group(3);
            String handler = matcher.group(4);

            // Check both direct prefix and Chi route group prefix
            String prefix = groupPrefixes.getOrDefault(routerVar,
                groupPrefixes.getOrDefault(routerVar + "_route", ""));
            String fullPath = combinePaths(prefix, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                method,
                handler,
                null,
                null,
                null
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Chi route: {} {} -> {}", method, fullPath, handler);
        }
    }

    /**
     * Extracts Gorilla Mux framework routes.
     */
    private void extractMuxRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                   Map<String, String> groupPrefixes) {
        if (!content.contains(MUX_IMPORT)) {
            return;
        }

        Matcher matcher = MUX_ROUTE.matcher(content);
        while (matcher.find()) {
            String routerVar = matcher.group(1);
            String path = matcher.group(2);
            String handler = matcher.group(3);
            String method = matcher.group(4);

            String prefix = groupPrefixes.getOrDefault(routerVar, "");
            String fullPath = combinePaths(prefix, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                method,
                handler,
                null,
                null,
                null
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Mux route: {} {} -> {}", method, fullPath, handler);
        }
    }

    /**
     * Extracts Fiber framework routes.
     */
    private void extractFiberRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                     Map<String, String> groupPrefixes) {
        if (!content.contains(FIBER_IMPORT)) {
            return;
        }

        Matcher matcher = FIBER_ROUTE.matcher(content);
        while (matcher.find()) {
            String appVar = matcher.group(1);
            String method = matcher.group(2).toUpperCase();
            String path = matcher.group(3);
            String handler = matcher.group(4);

            String prefix = groupPrefixes.getOrDefault(appVar, "");
            String fullPath = combinePaths(prefix, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                method,
                handler,
                null,
                null,
                null
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Fiber route: {} {} -> {}", method, fullPath, handler);
        }
    }

    /**
     * Extracts net/http standard library routes.
     */
    private void extractNetHttpRoutes(String content, String componentId, List<ApiEndpoint> apiEndpoints) {
        if (!content.contains(NET_HTTP_IMPORT) || !content.contains("HandleFunc")) {
            return;
        }

        Matcher matcher = NET_HTTP_ROUTE.matcher(content);
        while (matcher.find()) {
            String path = matcher.group(1);
            String handler = matcher.group(2);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                path,
                "GET", // net/http doesn't specify method in route registration
                handler,
                null,
                null,
                null
            );
            apiEndpoints.add(endpoint);
            log.debug("Found net/http route: {} -> {}", path, handler);
        }
    }

    /**
     * Combines base path and method path into full path.
     *
     * @param basePath base path (e.g., from route group)
     * @param methodPath method path
     * @return combined path
     */
    private String combinePaths(String basePath, String methodPath) {
        if (basePath == null || basePath.isEmpty()) {
            return methodPath.startsWith("/") ? methodPath : "/" + methodPath;
        }
        if (methodPath == null || methodPath.isEmpty()) {
            return basePath;
        }

        String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanMethod = methodPath.startsWith("/") ? methodPath : "/" + methodPath;

        return cleanBase + cleanMethod;
    }
}
