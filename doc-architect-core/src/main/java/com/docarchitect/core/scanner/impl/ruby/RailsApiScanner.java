package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.RubyAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Rails API endpoints in Ruby controller files.
 *
 * <p>This scanner extracts REST API endpoints from Rails controller classes
 * by parsing Ruby files using ANTLR-based AST parsing with regex fallback.
 *
 * <p><b>Three-Tier Parsing Strategy:</b></p>
 * <ol>
 *   <li><b>Tier 1 (HIGH confidence):</b> ANTLR-based AST parsing</li>
 *   <li><b>Tier 2 (MEDIUM confidence):</b> Regex-based fallback parsing</li>
 *   <li><b>Tier 3 (LOW confidence):</b> Graceful degradation with statistics</li>
 * </ol>
 *
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 *   <li>Controller classes inheriting from {@code ApplicationController}</li>
 *   <li>RESTful action methods: {@code index, show, create, update, destroy, new, edit}</li>
 *   <li>Custom action methods defined in controllers</li>
 *   <li>{@code before_action} callbacks for authentication/authorization</li>
 * </ul>
 *
 * <p><b>HTTP Method Mapping:</b></p>
 * <ul>
 *   <li>{@code index} → GET /resource</li>
 *   <li>{@code show} → GET /resource/:id</li>
 *   <li>{@code new} → GET /resource/new</li>
 *   <li>{@code create} → POST /resource</li>
 *   <li>{@code edit} → GET /resource/:id/edit</li>
 *   <li>{@code update} → PUT/PATCH /resource/:id</li>
 *   <li>{@code destroy} → DELETE /resource/:id</li>
 * </ul>
 *
 * <p><b>Example Controller:</b></p>
 * <pre>{@code
 * class UsersController < ApplicationController
 *   before_action :authenticate_user
 *
 *   def index
 *     @users = User.all
 *   end
 *
 *   def show
 *     @user = User.find(params[:id])
 *   end
 * end
 * }</pre>
 *
 * @see AbstractAstScanner
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class RailsApiScanner extends AbstractAstScanner<RubyAst.RubyClass> {

    private static final String SCANNER_ID = "rails-api";
    private static final String SCANNER_DISPLAY_NAME = "Rails API Scanner";
    private static final String PATTERN_RUBY_CONTROLLERS = "**/*_controller.rb";
    private static final String PATTERN_ALL_RUBY = "**/*.rb";

    private static final String CONTROLLER_SUFFIX = "Controller";

    // RESTful action to HTTP method mapping
    private static final Map<String, String> ACTION_TO_HTTP_METHOD = Map.of(
        "index", "GET",
        "show", "GET",
        "new", "GET",
        "create", "POST",
        "edit", "GET",
        "update", "PUT",
        "destroy", "DELETE"
    );

    // RESTful action to path suffix mapping
    private static final Map<String, String> ACTION_TO_PATH_SUFFIX = Map.of(
        "index", "",
        "show", "/:id",
        "new", "/new",
        "create", "",
        "edit", "/:id/edit",
        "update", "/:id",
        "destroy", "/:id"
    );

    // Regex patterns for fallback parsing
    private static final Pattern CLASS_PATTERN =
        Pattern.compile("class\\s+(\\w+(?:::\\w+)*)\\s*<\\s*(\\w+(?:::\\w+)*)");

    private static final Pattern METHOD_PATTERN =
        Pattern.compile("def\\s+(\\w+)(?:\\s*\\(([^)]*)\\))?");

    public RailsApiScanner() {
        super(AstParserFactory.getRubyParser());
    }

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
        return Set.of(PATTERN_RUBY_CONTROLLERS, PATTERN_ALL_RUBY);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Check if this is a Rails project (has app/controllers directory)
        return hasAnyFiles(context, PATTERN_RUBY_CONTROLLERS) ||
               context.rootPath().resolve("app/controllers").toFile().exists();
    }

    @Override
    protected boolean shouldScanFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith("_controller.rb");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Rails API endpoints in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();
        Path rootPath = context.rootPath();

        // Find controller files
        List<Path> controllerFiles = context.findFiles(PATTERN_RUBY_CONTROLLERS).toList();
        statsBuilder.filesDiscovered(controllerFiles.size());

        if (controllerFiles.isEmpty()) {
            log.debug("No Rails controller files found");
            return emptyResult();
        }

        int skippedFiles = 0;

        // Parse each controller with three-tier fallback
        for (Path controllerFile : controllerFiles) {
            if (!shouldScanFile(controllerFile)) {
                skippedFiles++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<ControllerData> result = parseWithFallback(
                controllerFile,
                classes -> extractControllerDataFromAST(classes, controllerFile, rootPath),
                createFallbackStrategy(rootPath),
                statsBuilder
            );

            if (result.isSuccess()) {
                for (ControllerData data : result.getData()) {
                    components.add(data.component);
                    apiEndpoints.addAll(data.endpoints);
                }
            }
        }

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} Rails components and {} API endpoints (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
                 components.size(), apiEndpoints.size(), statistics.getSuccessRate(),
                 statistics.getOverallParseRate(), skippedFiles);

        return buildSuccessResult(
            components,
            List.of(),           // No dependencies
            apiEndpoints,
            List.of(),           // No message flows
            List.of(),           // No data entities
            List.of(),           // No relationships
            List.of(),           // No warnings
            statistics
        );
    }

    /**
     * Extracts controller data from parsed AST classes.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param classes parsed Ruby classes
     * @param file source file
     * @param rootPath project root
     * @return list of controller data
     */
    private List<ControllerData> extractControllerDataFromAST(
        List<RubyAst.RubyClass> classes,
        Path file,
        Path rootPath
    ) {
        List<ControllerData> results = new ArrayList<>();

        for (RubyAst.RubyClass rubyClass : classes) {
            // Only process controller classes
            if (!rubyClass.name().endsWith(CONTROLLER_SUFFIX)) {
                continue;
            }

            if (!isRailsController(rubyClass)) {
                continue;
            }

            // Create component for this controller
            Component component = createControllerComponent(rubyClass, file, rootPath);

            // Extract API endpoints from controller methods
            String resourcePath = extractResourcePath(rubyClass.name());
            List<ApiEndpoint> endpoints = new ArrayList<>();

            for (RubyAst.Method method : rubyClass.methods()) {
                ApiEndpoint endpoint = createApiEndpoint(
                    method.name(),
                    resourcePath,
                    component.id()
                );

                if (endpoint != null) {
                    endpoints.add(endpoint);
                    log.debug("Found Rails endpoint: {} {}", endpoint.method(), endpoint.path());
                }
            }

            results.add(new ControllerData(component, endpoints));
        }

        return results;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * @param rootPath project root path
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<ControllerData> createFallbackStrategy(Path rootPath) {
        return (file, content) -> {
            List<ControllerData> results = new ArrayList<>();

            if (!content.contains("Controller")) {
                return results;
            }

            // Extract class definition
            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            if (!classMatcher.find()) {
                return results;
            }

            String className = classMatcher.group(1);
            String superclass = classMatcher.group(2);

            // Only process controller classes
            if (!className.endsWith(CONTROLLER_SUFFIX)) {
                return results;
            }

            if (!superclass.contains("ApplicationController") &&
                !superclass.contains("ActionController")) {
                return results;
            }

            // Create component
            String componentId = IdGenerator.generate(className);
            String relativePath = rootPath.relativize(file).toString();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("file", relativePath);
            metadata.put("language", Technologies.RUBY);
            metadata.put("framework", "Rails");
            metadata.put("superclass", superclass);
            metadata.put("parsing", "regex-fallback");

            Component component = new Component(
                componentId,
                className,
                ComponentType.SERVICE,
                null,
                Technologies.RUBY,
                relativePath,
                metadata
            );

            // Extract methods
            String resourcePath = extractResourcePath(className);
            List<ApiEndpoint> endpoints = new ArrayList<>();

            Matcher methodMatcher = METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);

                // Skip private/protected markers and non-action methods
                if (methodName.equals("private") || methodName.equals("protected") ||
                    methodName.equals("public") || methodName.startsWith("_")) {
                    continue;
                }

                ApiEndpoint endpoint = createApiEndpoint(
                    methodName,
                    resourcePath,
                    componentId
                );

                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }

            if (!endpoints.isEmpty()) {
                results.add(new ControllerData(component, endpoints));
                log.debug("Fallback parsing found {} endpoints in {}", endpoints.size(), file.getFileName());
            }

            return results;
        };
    }

    /**
     * Check if a Ruby class is a Rails controller.
     *
     * @param rubyClass parsed Ruby class
     * @return true if class inherits from ApplicationController or ActionController
     */
    private boolean isRailsController(RubyAst.RubyClass rubyClass) {
        String superclass = rubyClass.superclass();
        if (superclass == null || superclass.isEmpty()) {
            return false;
        }

        return superclass.contains("ApplicationController") ||
               superclass.contains("ActionController");
    }

    /**
     * Create a Component from a Rails controller class.
     *
     * @param rubyClass parsed Ruby class
     * @param file source file
     * @param rootPath project root
     * @return Component instance
     */
    private Component createControllerComponent(
        RubyAst.RubyClass rubyClass,
        Path file,
        Path rootPath
    ) {
        String componentId = IdGenerator.generate(rubyClass.name());
        String relativePath = rootPath.relativize(file).toString();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("file", relativePath);
        metadata.put("language", Technologies.RUBY);
        metadata.put("framework", "Rails");
        metadata.put("superclass", rubyClass.superclass());
        metadata.put("line", String.valueOf(rubyClass.lineNumber()));

        if (!rubyClass.beforeActions().isEmpty()) {
            metadata.put("before_actions", String.join(", ", rubyClass.beforeActions()));
        }

        return new Component(
            componentId,
            rubyClass.name(),
            ComponentType.SERVICE,
            null,  // description
            Technologies.RUBY,
            relativePath,
            metadata
        );
    }

    /**
     * Extract resource path from controller name.
     *
     * <p>Examples:
     * <ul>
     *   <li>UsersController → /users</li>
     *   <li>Api::V1::ProductsController → /api/v1/products</li>
     *   <li>Admin::PostsController → /admin/posts</li>
     * </ul>
     *
     * @param className controller class name
     * @return resource path
     */
    private String extractResourcePath(String className) {
        // Remove "Controller" suffix
        String name = className.replace(CONTROLLER_SUFFIX, "");

        // Convert from PascalCase to snake_case and pluralize
        // Handle namespace separators (::)
        name = name.replace("::", "/");

        // Convert to lowercase and add leading slash
        StringBuilder path = new StringBuilder("/");

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && name.charAt(i - 1) != '/') {
                    path.append('_');
                }
                path.append(Character.toLowerCase(c));
            } else {
                path.append(c);
            }
        }

        return path.toString();
    }

    /**
     * Create an ApiEndpoint from a controller method.
     *
     * @param methodName Ruby method name
     * @param resourcePath base resource path (e.g., "/users")
     * @param componentId owning component ID
     * @return ApiEndpoint or null if not a valid endpoint
     */
    private ApiEndpoint createApiEndpoint(
        String methodName,
        String resourcePath,
        String componentId
    ) {
        String httpMethod = determineHttpMethod(methodName);
        String pathSuffix = determinePathSuffix(methodName);

        if (httpMethod == null) {
            // Not a recognized action - treat as custom endpoint with GET
            httpMethod = "GET";
            pathSuffix = "/" + methodName;
        }

        String fullPath = resourcePath + pathSuffix;

        return new ApiEndpoint(
            componentId,
            ApiType.REST,
            fullPath,
            httpMethod,
            null,  // description
            null,  // request schema
            null,  // response schema
            null   // authentication
        );
    }

    /**
     * Determine HTTP method for a Rails action.
     *
     * @param actionName Rails action name
     * @return HTTP method or null if not a standard RESTful action
     */
    private String determineHttpMethod(String actionName) {
        return ACTION_TO_HTTP_METHOD.get(actionName);
    }

    /**
     * Determine path suffix for a Rails action.
     *
     * @param actionName Rails action name
     * @return path suffix
     */
    private String determinePathSuffix(String actionName) {
        return ACTION_TO_PATH_SUFFIX.getOrDefault(actionName, "/" + actionName);
    }

    /**
     * Container for controller parsing results.
     */
    private static class ControllerData {
        final Component component;
        final List<ApiEndpoint> endpoints;

        ControllerData(Component component, List<ApiEndpoint> endpoints) {
            this.component = component;
            this.endpoints = endpoints;
        }
    }
}
