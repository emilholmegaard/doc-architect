package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

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

/**
 * Scanner for Rails routes defined in config/routes.rb files.
 *
 * <p>This scanner parses Rails routing DSL using regex patterns to extract REST API endpoint
 * information. Rails routes define the HTTP endpoints available in a Rails application.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate config/routes.rb files using pattern matching</li>
 *   <li>Extract HTTP routes from Rails routing DSL patterns</li>
 *   <li>Handle resourceful routes (resources/resource) that generate multiple endpoints</li>
 *   <li>Handle namespace and scope blocks with path prefixes</li>
 *   <li>Handle custom route definitions (get, post, put, patch, delete)</li>
 *   <li>Create ApiEndpoint records for each discovered endpoint</li>
 * </ol>
 *
 * <p><b>Supported Patterns:</b>
 * <ul>
 *   <li><b>Resourceful routes:</b> {@code resources :users} (generates index, show, create, update, destroy, new, edit)</li>
 *   <li><b>Singular resource:</b> {@code resource :profile} (generates show, create, update, destroy, new, edit)</li>
 *   <li><b>Custom GET:</b> {@code get '/custom', to: 'controller#action'}</li>
 *   <li><b>Custom POST:</b> {@code post '/submit', to: 'controller#action'}</li>
 *   <li><b>Custom PUT/PATCH:</b> {@code put '/update', to: 'controller#action'}</li>
 *   <li><b>Custom DELETE:</b> {@code delete '/remove', to: 'controller#action'}</li>
 *   <li><b>Match with method:</b> {@code match '/path', to: 'controller#action', via: :post}</li>
 *   <li><b>Namespace:</b> {@code namespace :api do ... end}</li>
 *   <li><b>Scope:</b> {@code scope '/admin' do ... end}</li>
 * </ul>
 *
 * <p><b>Route Expansion Examples:</b>
 * <pre>{@code
 * # Resourceful routes (generates 7 endpoints)
 * resources :users
 * # => GET    /users          -> users#index
 * # => GET    /users/new      -> users#new
 * # => POST   /users          -> users#create
 * # => GET    /users/:id      -> users#show
 * # => GET    /users/:id/edit -> users#edit
 * # => PATCH  /users/:id      -> users#update
 * # => DELETE /users/:id      -> users#destroy
 *
 * # Namespace with resources
 * namespace :api do
 *   resources :posts
 * end
 * # => GET /api/posts -> api/posts#index
 * # => ...
 *
 * # Custom routes
 * get '/custom', to: 'custom#action'
 * # => GET /custom -> custom#action
 * }</pre>
 *
 * <p><b>Regex Patterns:</b>
 * <ul>
 *   <li>{@code RESOURCES_PATTERN}: {@code resources\s+:(\w+)}</li>
 *   <li>{@code RESOURCE_PATTERN}: {@code resource\s+:(\w+)}</li>
 *   <li>{@code CUSTOM_ROUTE_PATTERN}: {@code (get|post|put|patch|delete)\s+['"]([^'"]+)['"],\s*to:\s*['"]([^'"]+)['"]}</li>
 *   <li>{@code MATCH_ROUTE_PATTERN}: {@code match\s+['"]([^'"]+)['"],\s*to:\s*['"]([^'"]+)['"],\s*via:\s*:(\w+)}</li>
 *   <li>{@code NAMESPACE_PATTERN}: {@code namespace\s+:(\w+)\s+do}</li>
 *   <li>{@code SCOPE_PATTERN}: {@code scope\s+['"]([^'"]+)['"](?:\s*,\s*module:\s*['"]([^'"]+)['"])?\s+do}</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new RailsRouteScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "rails-project",
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
public class RailsRouteScanner extends AbstractRegexScanner {
    private static final String SCANNER_ID = "rails-route";
    private static final String SCANNER_DISPLAY_NAME = "Rails Route Scanner";
    private static final String ROUTES_FILE_GLOB = "**/routes.rb";
    private static final String ROUTES_DIR_GLOB = "**/routes/*.rb";
    private static final Set<String> ROUTES_FILE_PATTERNS = Set.of(ROUTES_FILE_GLOB, ROUTES_DIR_GLOB);
    private static final int PRIORITY = 50;

    /**
     * Resourceful routes pattern: resources :users
     * Generates 7 RESTful routes (index, show, create, update, destroy, new, edit).
     */
    private static final Pattern RESOURCES_PATTERN = Pattern.compile(
        "resources\\s+:(\\w+)"
    );

    /**
     * Singular resource pattern: resource :profile
     * Generates 6 RESTful routes (show, create, update, destroy, new, edit) without index.
     */
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
        "resource\\s+:(\\w+)"
    );

    /**
     * Custom route pattern: get '/path', to: 'controller#action'
     * Captures: (1) HTTP method, (2) path, (3) controller#action.
     */
    private static final Pattern CUSTOM_ROUTE_PATTERN = Pattern.compile(
        "(get|post|put|patch|delete)\\s+['\"]([^'\"]+)['\"](?:,\\s*to:\\s*['\"]([^'\"]+)['\"])?"
    );

    /**
     * Match route pattern: match '/path', to: 'controller#action', via: :post
     * Captures: (1) path, (2) controller#action, (3) HTTP method.
     */
    private static final Pattern MATCH_ROUTE_PATTERN = Pattern.compile(
        "match\\s+['\"]([^'\"]+)['\"],\\s*to:\\s*['\"]([^'\"]+)['\"],\\s*via:\\s*:(\\w+)"
    );

    /**
     * Namespace pattern: namespace :api do
     * Captures: (1) namespace name.
     */
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile(
        "namespace\\s+:(\\w+)\\s+do"
    );

    /**
     * Scope pattern: scope '/admin' do or scope '/admin', module: 'admin' do
     * Captures: (1) scope path, (2) module name (optional).
     */
    private static final Pattern SCOPE_PATTERN = Pattern.compile(
        "scope\\s+['\"]([^'\"]+)['\"](?:\\s*,\\s*module:\\s*['\"]([^'\"]+)['\"])?\\s+do"
    );

    /**
     * End block pattern: end
     */
    private static final Pattern END_PATTERN = Pattern.compile(
        "^\\s*end\\s*$"
    );

    // Standard RESTful actions for resourceful routes
    private static final Map<String, RouteInfo> RESOURCES_ACTIONS = Map.of(
        "index", new RouteInfo("GET", "", "index"),
        "new", new RouteInfo("GET", "/new", "new"),
        "create", new RouteInfo("POST", "", "create"),
        "show", new RouteInfo("GET", "/:id", "show"),
        "edit", new RouteInfo("GET", "/:id/edit", "edit"),
        "update", new RouteInfo("PATCH", "/:id", "update"),
        "destroy", new RouteInfo("DELETE", "/:id", "destroy")
    );

    // Standard RESTful actions for singular resources (no index)
    private static final Map<String, RouteInfo> RESOURCE_ACTIONS = Map.of(
        "new", new RouteInfo("GET", "/new", "new"),
        "create", new RouteInfo("POST", "", "create"),
        "show", new RouteInfo("GET", "", "show"),
        "edit", new RouteInfo("GET", "/edit", "edit"),
        "update", new RouteInfo("PATCH", "", "update"),
        "destroy", new RouteInfo("DELETE", "", "destroy")
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
        return Set.of(Technologies.RUBY);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return ROUTES_FILE_PATTERNS;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, ROUTES_FILE_PATTERNS.toArray(new String[0]));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Rails routes in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        Set<Path> routeFiles = new LinkedHashSet<>();

        ROUTES_FILE_PATTERNS.forEach(pattern -> context.findFiles(pattern).forEach(routeFiles::add));

        if (routeFiles.isEmpty()) {
            log.warn("No Rails route files found in project");
            return emptyResult();
        }

        for (Path routeFile : routeFiles) {
            try {
                parseRouteFile(routeFile, apiEndpoints);
            } catch (Exception e) {
                log.error("Failed to parse Rails route file: {}", routeFile, e);
                // Continue processing other files instead of failing completely
            }
        }

        log.info("Found {} Rails routes across {} route files", apiEndpoints.size(), routeFiles.size());

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
     * Parses a single Rails route file and extracts HTTP routes.
     *
     * @param routeFile path to routes.rb file
     * @param apiEndpoints list to add discovered API endpoints
     * @throws IOException if file cannot be read
     */
    private void parseRouteFile(Path routeFile, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(routeFile);
        String componentId = extractComponentId(routeFile);

        // Parse routes with namespace/scope tracking
        parseRoutesWithContext(content, componentId, apiEndpoints, "", "");
    }

    /**
     * Recursively parses routes while tracking namespace and scope context.
     *
     * @param content route file content
     * @param componentId component identifier
     * @param apiEndpoints list to add discovered endpoints
     * @param pathPrefix current path prefix from scopes
     * @param namespacePrefix current namespace prefix
     */
    private void parseRoutesWithContext(String content, String componentId, List<ApiEndpoint> apiEndpoints,
                                        String pathPrefix, String namespacePrefix) {
        String[] lines = content.split("\n");
        List<ScopeContext> scopeStack = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Skip comments and empty lines
            if (line.isEmpty() || isComment(line)) {
                continue;
            }

            // Check for 'end' keyword to pop scope
            if (END_PATTERN.matcher(line).matches() && !scopeStack.isEmpty()) {
                scopeStack.remove(scopeStack.size() - 1);
                continue;
            }

            // Get current context from scope stack
            String currentPathPrefix = pathPrefix;
            String currentNamespacePrefix = namespacePrefix;
            if (!scopeStack.isEmpty()) {
                ScopeContext currentScope = scopeStack.get(scopeStack.size() - 1);
                currentPathPrefix = currentScope.pathPrefix;
                currentNamespacePrefix = currentScope.namespacePrefix;
            }

            // Check for namespace
            Matcher namespaceMatcher = NAMESPACE_PATTERN.matcher(line);
            if (namespaceMatcher.find()) {
                String namespace = namespaceMatcher.group(1);
                String newPathPrefix = combinePaths(currentPathPrefix, "/" + namespace);
                String newNamespacePrefix = combineNamespaces(currentNamespacePrefix, namespace);
                scopeStack.add(new ScopeContext(newPathPrefix, newNamespacePrefix));
                log.debug("Found namespace: {} (path prefix: {})", namespace, newPathPrefix);
                continue;
            }

            // Check for scope
            Matcher scopeMatcher = SCOPE_PATTERN.matcher(line);
            if (scopeMatcher.find()) {
                String scopePath = scopeMatcher.group(1);
                String scopeModule = scopeMatcher.group(2); // may be null
                String newPathPrefix = combinePaths(currentPathPrefix, scopePath);
                String newNamespacePrefix = scopeModule != null ?
                    combineNamespaces(currentNamespacePrefix, scopeModule) : currentNamespacePrefix;
                scopeStack.add(new ScopeContext(newPathPrefix, newNamespacePrefix));
                log.debug("Found scope: {} (path prefix: {}, module: {})", scopePath, newPathPrefix, scopeModule);
                continue;
            }

            // Check for resourceful routes
            Matcher resourcesMatcher = RESOURCES_PATTERN.matcher(line);
            if (resourcesMatcher.find()) {
                String resourceName = resourcesMatcher.group(1);
                addResourcefulRoutes(resourceName, currentPathPrefix, currentNamespacePrefix,
                    componentId, apiEndpoints, true);
                continue;
            }

            // Check for singular resource
            Matcher resourceMatcher = RESOURCE_PATTERN.matcher(line);
            if (resourceMatcher.find()) {
                String resourceName = resourceMatcher.group(1);
                addResourcefulRoutes(resourceName, currentPathPrefix, currentNamespacePrefix,
                    componentId, apiEndpoints, false);
                continue;
            }

            // Check for match route
            Matcher matchMatcher = MATCH_ROUTE_PATTERN.matcher(line);
            if (matchMatcher.find()) {
                String path = matchMatcher.group(1);
                String controllerAction = matchMatcher.group(2);
                String method = matchMatcher.group(3).toUpperCase();
                String fullPath = combinePaths(currentPathPrefix, path);
                addCustomRoute(method, fullPath, controllerAction, componentId, apiEndpoints);
                continue;
            }

            // Check for custom routes (get, post, put, patch, delete)
            Matcher customMatcher = CUSTOM_ROUTE_PATTERN.matcher(line);
            if (customMatcher.find()) {
                String method = customMatcher.group(1).toUpperCase();
                String path = customMatcher.group(2);
                String controllerAction = customMatcher.group(3); // may be null
                String fullPath = combinePaths(currentPathPrefix, path);
                addCustomRoute(method, fullPath, controllerAction, componentId, apiEndpoints);
            }
        }
    }

    /**
     * Adds all RESTful routes for a resourceful declaration.
     *
     * @param resourceName resource name (e.g., "users")
     * @param pathPrefix current path prefix
     * @param namespacePrefix current namespace prefix
     * @param componentId component identifier
     * @param apiEndpoints list to add endpoints
     * @param isPlural true for resources (plural), false for resource (singular)
     */
    private void addResourcefulRoutes(String resourceName, String pathPrefix, String namespacePrefix,
                                      String componentId, List<ApiEndpoint> apiEndpoints, boolean isPlural) {
        String basePath = combinePaths(pathPrefix, "/" + resourceName);
        String controllerName = namespacePrefix.isEmpty() ? resourceName : namespacePrefix + "/" + resourceName;

        Map<String, RouteInfo> actions = isPlural ? RESOURCES_ACTIONS : RESOURCE_ACTIONS;

        for (Map.Entry<String, RouteInfo> entry : actions.entrySet()) {
            String actionName = entry.getKey();
            RouteInfo routeInfo = entry.getValue();
            String fullPath = basePath + routeInfo.pathSuffix;
            String controllerAction = controllerName + "#" + actionName;

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                routeInfo.method,
                controllerAction,
                null, // requestSchema
                null, // responseSchema
                null  // authentication
            );
            apiEndpoints.add(endpoint);
            log.debug("Found Rails route: {} {} -> {}", routeInfo.method, fullPath, controllerAction);
        }
    }

    /**
     * Adds a custom route endpoint.
     *
     * @param method HTTP method
     * @param path route path
     * @param controllerAction controller#action (may be null)
     * @param componentId component identifier
     * @param apiEndpoints list to add endpoints
     */
    private void addCustomRoute(String method, String path, String controllerAction,
                               String componentId, List<ApiEndpoint> apiEndpoints) {
        ApiEndpoint endpoint = new ApiEndpoint(
            componentId,
            ApiType.REST,
            path,
            method,
            controllerAction != null ? controllerAction : "unknown",
            null, // requestSchema
            null, // responseSchema
            null  // authentication
        );
        apiEndpoints.add(endpoint);
        log.debug("Found Rails route: {} {} -> {}", method, path, controllerAction);
    }

    /**
     * Extracts component ID from route file path.
     *
     * @param routeFile path to route file
     * @return component ID
     */
    private String extractComponentId(Path routeFile) {
        // Use the parent directory name or "routes" as fallback
        Path parent = routeFile.getParent();
        if (parent != null && parent.getFileName() != null) {
            return parent.getFileName().toString();
        }
        return "routes";
    }

    /**
     * Combines base path and additional path into full path.
     *
     * @param basePath base path (e.g., "/api")
     * @param additionalPath additional path (e.g., "/users")
     * @return combined path
     */
    private String combinePaths(String basePath, String additionalPath) {
        if (basePath == null || basePath.isEmpty()) {
            return additionalPath.startsWith("/") ? additionalPath : "/" + additionalPath;
        }
        if (additionalPath == null || additionalPath.isEmpty()) {
            return basePath;
        }

        String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanAdditional = additionalPath.startsWith("/") ? additionalPath : "/" + additionalPath;

        return cleanBase + cleanAdditional;
    }

    /**
     * Combines namespace prefixes.
     *
     * @param baseNamespace base namespace (e.g., "api")
     * @param additionalNamespace additional namespace (e.g., "v1")
     * @return combined namespace (e.g., "api/v1")
     */
    private String combineNamespaces(String baseNamespace, String additionalNamespace) {
        if (baseNamespace == null || baseNamespace.isEmpty()) {
            return additionalNamespace;
        }
        if (additionalNamespace == null || additionalNamespace.isEmpty()) {
            return baseNamespace;
        }
        return baseNamespace + "/" + additionalNamespace;
    }

    /**
     * Represents routing information for a RESTful action.
     */
    private static class RouteInfo {
        final String method;
        final String pathSuffix;
        final String action;

        RouteInfo(String method, String pathSuffix, String action) {
            this.method = method;
            this.pathSuffix = pathSuffix;
            this.action = action;
        }
    }

    /**
     * Represents the current scope context while parsing routes.
     */
    private static class ScopeContext {
        final String pathPrefix;
        final String namespacePrefix;

        ScopeContext(String pathPrefix, String namespacePrefix) {
            this.pathPrefix = pathPrefix;
            this.namespacePrefix = namespacePrefix;
        }
    }
}
