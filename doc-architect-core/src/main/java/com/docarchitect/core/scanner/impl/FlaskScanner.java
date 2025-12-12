package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Flask REST endpoints in Python source files.
 *
 * <p>Since we're running in Java, we parse Python files as TEXT using regex patterns
 * to extract route decorators and function definitions.
 *
 * <h3>Supported Decorator Styles</h3>
 *
 * <h4>Legacy Style (Flask 1.x)</h4>
 * <ul>
 *   <li>{@code @app.route("/path", methods=["GET"])}</li>
 *   <li>{@code @app.route("/path", methods=["POST", "PUT"])}</li>
 *   <li>{@code @blueprint.route("/path", methods=["DELETE"])}</li>
 * </ul>
 *
 * <h4>Modern Style (Flask 2.0+)</h4>
 * <ul>
 *   <li>{@code @app.get("/path")}</li>
 *   <li>{@code @app.post("/path")}</li>
 *   <li>{@code @app.put("/path")}</li>
 *   <li>{@code @app.delete("/path")}</li>
 *   <li>{@code @app.patch("/path")}</li>
 *   <li>{@code @blueprint.get("/path")}</li>
 * </ul>
 *
 * <h3>Parameter Extraction</h3>
 * <ul>
 *   <li>Path parameters: {@code <user_id>} or {@code <int:user_id>}</li>
 *   <li>Query parameters: {@code request.args.get('param')}</li>
 *   <li>Body parameters: {@code request.json} or {@code request.get_json()}</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code MODERN_DECORATOR}: {@code @(app|blueprint)\.(get|post|put|delete|patch)\s*\(\s*['"](.*?)['"]}</li>
 *   <li>{@code LEGACY_DECORATOR}: {@code @(app|blueprint)\.route\s*\(\s*['"](.*?)['"].*?methods\s*=\s*\[(.*?)\]}</li>
 *   <li>{@code SIMPLE_ROUTE}: {@code @(app|blueprint)\.route\s*\(\s*['"](.*?)['"]}</li>
 *   <li>{@code PATH_PARAM_PATTERN}: {@code <(?:(\w+):)?(\w+)>}</li>
 * </ul>
 *
 * @see Scanner
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class FlaskScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(FlaskScanner.class);

    /**
     * Regex for modern Flask 2.0+ decorators: @app.get("/users").
     * Captures: (1) app|blueprint, (2) HTTP method, (3) path.
     */
    private static final Pattern MODERN_DECORATOR = Pattern.compile(
        "@(app|blueprint|\\w+_bp)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex for legacy Flask decorators with methods: @app.route("/users", methods=["GET", "POST"]).
     * Captures: (1) app|blueprint, (2) path, (3) methods list.
     */
    private static final Pattern LEGACY_DECORATOR = Pattern.compile(
        "@(app|blueprint|\\w+_bp)\\.route\\s*\\(\\s*['\"](.+?)['\"].*?methods\\s*=\\s*\\[(.+?)\\]",
        Pattern.DOTALL
    );

    /**
     * Regex for simple route decorator without methods: @app.route("/users").
     * Captures: (1) app|blueprint, (2) path.
     * Defaults to GET method.
     */
    private static final Pattern SIMPLE_ROUTE = Pattern.compile(
        "@(app|blueprint|\\w+_bp)\\.route\\s*\\(\\s*['\"](.+?)['\"]"
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
        return "flask-rest";
    }

    @Override
    public String getDisplayName() {
        return "Flask REST Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.py");
    }

    @Override
    public int getPriority() {
        return 51;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return context.findFiles("**/*.py").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Flask endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles("**/*.py").toList();

        if (pythonFiles.isEmpty()) {
            return ScanResult.empty(getId());
        }

        for (Path pythonFile : pythonFiles) {
            try {
                parsePythonFile(pythonFile, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse Python file: {} - {}", pythonFile, e.getMessage());
            }
        }

        log.info("Found {} Flask endpoints", apiEndpoints.size());

        return new ScanResult(
            getId(),
            true,
            List.of(),
            List.of(),
            apiEndpoints,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void parsePythonFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        List<String> lines = Files.readAllLines(file);
        String componentId = extractModuleName(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Try modern decorator style first
            Matcher modernMatcher = MODERN_DECORATOR.matcher(line);
            if (modernMatcher.find()) {
                String httpMethod = modernMatcher.group(2).toUpperCase();
                String path = modernMatcher.group(3);
                extractEndpoint(lines, i, componentId, httpMethod, path, apiEndpoints);
                continue;
            }

            // Try legacy decorator with methods
            Matcher legacyMatcher = LEGACY_DECORATOR.matcher(line);
            if (legacyMatcher.find()) {
                String path = legacyMatcher.group(2);
                String methodsStr = legacyMatcher.group(3);
                List<String> methods = extractMethods(methodsStr);

                for (String method : methods) {
                    extractEndpoint(lines, i, componentId, method, path, apiEndpoints);
                }
                continue;
            }

            // Try simple route decorator (defaults to GET)
            Matcher simpleMatcher = SIMPLE_ROUTE.matcher(line);
            if (simpleMatcher.find()) {
                String path = simpleMatcher.group(2);
                extractEndpoint(lines, i, componentId, "GET", path, apiEndpoints);
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
                String functionName = funcMatcher.group(1);
                String parameters = funcMatcher.group(2);

                // Extract path parameters
                List<String> pathParams = extractPathParameters(path);

                String requestSchema = buildRequestSchema(pathParams, parameters);
                String responseSchema = "dict"; // Default

                ApiEndpoint endpoint = new ApiEndpoint(
                    componentId,
                    ApiType.REST,
                    path,
                    httpMethod,
                    componentId + "." + functionName,
                    requestSchema,
                    responseSchema,
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
        for (int i = startIndex; i < Math.min(startIndex + 5, lines.size()); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("def ")) {
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
            String type = matcher.group(1);
            String name = matcher.group(2);
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
        return fileName.replaceAll("\\.py$", "");
    }
}
