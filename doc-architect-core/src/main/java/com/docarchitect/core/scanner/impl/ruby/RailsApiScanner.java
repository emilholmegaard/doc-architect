package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.scanner.impl.ruby.util.RubyAstParser;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scanner for Rails API endpoints in Ruby controller files.
 *
 * <p>This scanner extracts REST API endpoints from Rails controller classes
 * by parsing Ruby files using ANTLR-based AST parsing with regex fallback.
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
 * @see RubyAstParser
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class RailsApiScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "rails-api";
    private static final String SCANNER_DISPLAY_NAME = "Rails API Scanner";
    private static final String PATTERN_RUBY_CONTROLLERS = "**/*_controller.rb";
    private static final String PATTERN_ALL_RUBY = "**/*.rb";

    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String CONTROLLER_FILE_SUFFIX = "_controller.rb";

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
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Rails API endpoints in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        List<ApiEndpoint> apiEndpoints = new ArrayList<>();

        // Find controller files
        List<Path> controllerFiles = context.findFiles(PATTERN_RUBY_CONTROLLERS).toList();

        if (controllerFiles.isEmpty()) {
            log.debug("No Rails controller files found");
            return emptyResult();
        }

        // Parse each controller
        for (Path controllerFile : controllerFiles) {
            try {
                parseController(controllerFile, context.rootPath(), components, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse controller file: {} - {}", controllerFile, e.getMessage());
            }
        }

        log.info("Found {} Rails components and {} API endpoints", components.size(), apiEndpoints.size());

        return buildSuccessResult(
            components,
            List.of(),           // No dependencies
            apiEndpoints,
            List.of(),           // No message flows
            List.of(),           // No data entities
            List.of(),           // No relationships
            List.of()            // No warnings
        );
    }

    /**
     * Parse a Rails controller file and extract components and API endpoints.
     *
     * @param file controller file path
     * @param rootPath project root path
     * @param components output list for components
     * @param apiEndpoints output list for API endpoints
     */
    private void parseController(
        Path file,
        Path rootPath,
        List<Component> components,
        List<ApiEndpoint> apiEndpoints
    ) throws IOException {
        List<RubyAstParser.RubyClass> classes = RubyAstParser.parseFile(file);

        for (RubyAstParser.RubyClass rubyClass : classes) {
            // Only process controller classes
            if (!rubyClass.name.endsWith(CONTROLLER_SUFFIX)) {
                continue;
            }

            if (!isRailsController(rubyClass)) {
                continue;
            }

            // Create component for this controller
            Component component = createControllerComponent(rubyClass, file, rootPath);
            components.add(component);

            // Extract API endpoints from controller methods
            String resourcePath = extractResourcePath(rubyClass.name);

            for (RubyAstParser.RubyMethod method : rubyClass.methods) {
                ApiEndpoint endpoint = createApiEndpoint(
                    method,
                    resourcePath,
                    component.id(),
                    file,
                    rootPath
                );

                if (endpoint != null) {
                    apiEndpoints.add(endpoint);
                    log.debug("Found Rails endpoint: {} {}", endpoint.method(), endpoint.path());
                }
            }
        }
    }

    /**
     * Check if a Ruby class is a Rails controller.
     *
     * @param rubyClass parsed Ruby class
     * @return true if class inherits from ApplicationController or ActionController
     */
    private boolean isRailsController(RubyAstParser.RubyClass rubyClass) {
        if (rubyClass.superclass == null || rubyClass.superclass.isEmpty()) {
            return false;
        }

        return rubyClass.superclass.contains("ApplicationController") ||
               rubyClass.superclass.contains("ActionController");
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
        RubyAstParser.RubyClass rubyClass,
        Path file,
        Path rootPath
    ) {
        String componentId = IdGenerator.generate(rubyClass.name);
        String relativePath = rootPath.relativize(file).toString();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("file", relativePath);
        metadata.put("language", Technologies.RUBY);
        metadata.put("framework", "Rails");
        metadata.put("superclass", rubyClass.superclass);
        metadata.put("line", String.valueOf(rubyClass.lineNumber));

        if (!rubyClass.beforeActions.isEmpty()) {
            metadata.put("before_actions", String.join(", ", rubyClass.beforeActions));
        }

        return new Component(
            componentId,
            rubyClass.name,
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
     * @param method Ruby method definition
     * @param resourcePath base resource path (e.g., "/users")
     * @param componentId owning component ID
     * @param file source file
     * @param rootPath project root
     * @return ApiEndpoint or null if not a valid endpoint
     */
    private ApiEndpoint createApiEndpoint(
        RubyAstParser.RubyMethod method,
        String resourcePath,
        String componentId,
        Path file,
        Path rootPath
    ) {
        String httpMethod = determineHttpMethod(method.name);
        String pathSuffix = determinePathSuffix(method.name);

        if (httpMethod == null) {
            // Not a recognized action - treat as custom endpoint with GET
            httpMethod = "GET";
            pathSuffix = "/" + method.name;
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
}
