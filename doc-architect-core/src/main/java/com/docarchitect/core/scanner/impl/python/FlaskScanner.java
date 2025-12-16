package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * Scanner for Flask REST endpoints in Python source files.
 *
 * <p>Uses regex patterns to extract Flask route decorators and function definitions.
 * Flask routes are typically module-level functions, which are not captured by AST parsers
 * that focus on class definitions.
 *
 * <p><b>Supported Decorator Styles</b></p>
 *
 * <p><b>Legacy Style (Flask 1.x):</b></p>
 * <ul>
 *   <li>{@code @app.route("/path", methods=["GET"])}</li>
 *   <li>{@code @app.route("/path", methods=["POST", "PUT"])}</li>
 *   <li>{@code @blueprint.route("/path", methods=["DELETE"])}</li>
 * </ul>
 *
 * <p><b>Modern Style (Flask 2.0+):</b></p>
 * <ul>
 *   <li>{@code @app.get("/path")}</li>
 *   <li>{@code @app.post("/path")}</li>
 *   <li>{@code @app.put("/path")}</li>
 *   <li>{@code @app.delete("/path")}</li>
 *   <li>{@code @app.patch("/path")}</li>
 *   <li>{@code @blueprint.get("/path")}</li>
 * </ul>
 *
 * <p><b>Parameter Extraction</b></p>
 * <ul>
 *   <li>Path parameters: {@code <user_id>} or {@code <int:user_id>}</li>
 *   <li>Query parameters: {@code request.args.get('param')}</li>
 *   <li>Body parameters: {@code request.json} or {@code request.get_json()}</li>
 * </ul>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class FlaskScanner extends AbstractRegexScanner {

    // Scanner identification
    private static final String SCANNER_ID = "flask-rest";
    private static final String SCANNER_DISPLAY_NAME = "Flask REST Scanner";
    private static final int SCANNER_PRIORITY = 51;

    // File patterns
    private static final String PYTHON_FILE_PATTERN = "**/*.py";
    private static final String PYTHON_FILE_EXTENSION = ".py";

    // HTTP Methods
    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";
    private static final String HTTP_METHOD_PUT = "PUT";
    private static final String HTTP_METHOD_DELETE = "DELETE";
    private static final String HTTP_METHOD_PATCH = "PATCH";

    // Python syntax
    private static final String PYTHON_DEF_KEYWORD = "def ";

    // Parsing constants
    private static final int FUNCTION_SEARCH_WINDOW = 5;
    private static final String DEFAULT_RESPONSE_SCHEMA = "dict";

    // Regex group indices for decorators
    private static final int DECORATOR_APP_VAR_GROUP = 1;
    private static final int DECORATOR_METHOD_GROUP = 2;
    private static final int DECORATOR_PATH_GROUP = 3;
    private static final int LEGACY_DECORATOR_PATH_GROUP = 2;
    private static final int LEGACY_DECORATOR_METHODS_GROUP = 3;
    private static final int FUNCTION_NAME_GROUP = 1;
    private static final int FUNCTION_PARAMS_GROUP = 2;
    private static final int PATH_PARAM_TYPE_GROUP = 1;
    private static final int PATH_PARAM_NAME_GROUP = 2;

    /**
     * Regex for modern Flask 2.0+ decorators: @app.get("/users").
     * Captures: (1) app variable name, (2) HTTP method, (3) path.
     */
    private static final Pattern MODERN_DECORATOR = Pattern.compile(
        "@(\\w+)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex for legacy Flask decorators with methods: @app.route("/users", methods=["GET", "POST"]).
     * Captures: (1) app variable name, (2) path, (3) methods list.
     */
    private static final Pattern LEGACY_DECORATOR = Pattern.compile(
        "@(\\w+)\\.route\\s*\\(\\s*['\"](.+?)['\"].*?methods\\s*=\\s*\\[(.+?)\\]",
        Pattern.DOTALL
    );

    /**
     * Regex for simple route decorator without methods: @app.route("/users").
     * Captures: (1) app variable name, (2) path.
     * Defaults to GET method.
     */
    private static final Pattern SIMPLE_ROUTE = Pattern.compile(
        "@(\\w+)\\.route\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex for function definition: def get_user(user_id):.
     * Captures: (1) function name, (2) parameters.
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "def\\s+(\\w+)\\s*\\((.*)\\):"
    );

    /**
     * Regex to extract path parameters: <user_id> or <int:user_id>.
     * Captures: (1) type (optional), (2) parameter name.
     */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(
        "<(?:(\\w+):)?(\\w+)>"
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
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PYTHON_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PYTHON_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Flask endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles(PYTHON_FILE_PATTERN).toList();

        if (pythonFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path pythonFile : pythonFiles) {
            try {
                parsePythonFile(pythonFile, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse Python file: {} - {}", pythonFile, e.getMessage());
            }
        }

        log.info("Found {} Flask endpoints", apiEndpoints.size());

        return buildSuccessResult(
            List.of(),
            List.of(),
            apiEndpoints,
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void parsePythonFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        List<String> lines = readFileLines(file);
        String componentId = extractModuleName(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Try modern decorator style first
            Matcher modernMatcher = MODERN_DECORATOR.matcher(line);
            if (modernMatcher.find()) {
                String httpMethod = modernMatcher.group(DECORATOR_METHOD_GROUP).toUpperCase();
                String path = modernMatcher.group(DECORATOR_PATH_GROUP);
                extractEndpoint(lines, i, componentId, httpMethod, path, apiEndpoints);
                continue;
            }

            // Try legacy decorator with methods
            Matcher legacyMatcher = LEGACY_DECORATOR.matcher(line);
            if (legacyMatcher.find()) {
                String path = legacyMatcher.group(LEGACY_DECORATOR_PATH_GROUP);
                String methodsStr = legacyMatcher.group(LEGACY_DECORATOR_METHODS_GROUP);
                List<String> methods = extractMethods(methodsStr);

                for (String method : methods) {
                    extractEndpoint(lines, i, componentId, method, path, apiEndpoints);
                }
                continue;
            }

            // Try simple route decorator (defaults to GET)
            Matcher simpleMatcher = SIMPLE_ROUTE.matcher(line);
            if (simpleMatcher.find()) {
                String path = simpleMatcher.group(LEGACY_DECORATOR_PATH_GROUP);
                extractEndpoint(lines, i, componentId, HTTP_METHOD_GET, path, apiEndpoints);
            }
        }
    }

    /**
     * Extracts HTTP methods from methods list string.
     * Example: '"GET", "POST"' → [GET, POST]
     */
    private List<String> extractMethods(String methodsStr) {
        List<String> methods = new ArrayList<>();
        String[] parts = methodsStr.split(",");
        for (String part : parts) {
            String method = part.trim().replaceAll("['\"]", "");
            if (!method.isEmpty()) {
                methods.add(method.toUpperCase());
            }
        }
        return methods;
    }

    /**
     * Extracts endpoint information and creates ApiEndpoint record.
     */
    private void extractEndpoint(List<String> lines, int decoratorLineIndex, String componentId,
                                  String httpMethod, String path, List<ApiEndpoint> apiEndpoints) {
        // Find the function definition
        String functionLine = findNextFunctionDefinition(lines, decoratorLineIndex + 1);
        if (functionLine != null) {
            Matcher funcMatcher = FUNCTION_PATTERN.matcher(functionLine);
            if (funcMatcher.find()) {
                String functionName = funcMatcher.group(FUNCTION_NAME_GROUP);
                String parameters = funcMatcher.group(FUNCTION_PARAMS_GROUP);

                // Extract path parameters
                List<String> pathParams = extractPathParameters(path);

                String requestSchema = buildRequestSchema(pathParams, parameters);

                ApiEndpoint endpoint = new ApiEndpoint(
                    componentId,
                    ApiType.REST,
                    path,
                    httpMethod,
                    componentId + "." + functionName,
                    requestSchema,
                    DEFAULT_RESPONSE_SCHEMA,
                    null
                );

                apiEndpoints.add(endpoint);
                log.debug("Found Flask endpoint: {} {} -> {}", httpMethod, path, functionName);
            }
        }
    }

    /**
     * Finds the next function definition starting from the given line index.
     */
    private String findNextFunctionDefinition(List<String> lines, int startIndex) {
        for (int i = startIndex; i < Math.min(startIndex + FUNCTION_SEARCH_WINDOW, lines.size()); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(PYTHON_DEF_KEYWORD)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Extracts path parameters from the URL path.
     * Example: /users/<int:user_id>/items/<item_id> → [user_id: int, item_id]
     */
    private List<String> extractPathParameters(String path) {
        List<String> params = new ArrayList<>();
        Matcher matcher = PATH_PARAM_PATTERN.matcher(path);
        while (matcher.find()) {
            String type = matcher.group(PATH_PARAM_TYPE_GROUP);
            String name = matcher.group(PATH_PARAM_NAME_GROUP);
            if (type != null) {
                params.add(name + ": " + type);
            } else {
                params.add(name);
            }
        }
        return params;
    }

    /**
     * Builds request schema from extracted parameters.
     */
    private String buildRequestSchema(List<String> pathParams, String functionParams) {
        List<String> allParams = new ArrayList<>();

        if (!pathParams.isEmpty()) {
            allParams.add("Path: " + String.join(", ", pathParams));
        }

        // Check for request.args (query params) or request.json (body) in function params
        if (functionParams.contains("request")) {
            allParams.add("Body/Query: request object");
        }

        return allParams.isEmpty() ? null : String.join("; ", allParams);
    }

    /**
     * Extracts module name from file path.
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll("\\" + PYTHON_FILE_EXTENSION + "$", "");
    }
}
