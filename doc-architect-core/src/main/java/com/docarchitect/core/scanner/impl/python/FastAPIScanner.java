package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for FastAPI REST endpoints in Python source files.
 *
 * <p>Since we're running in Java, we parse Python files as TEXT using regex patterns
 * to extract route decorators and function definitions.
 *
 * <h3>Supported Patterns</h3>
 * <ul>
 *   <li>{@code @app.get("/path")} - GET endpoint</li>
 *   <li>{@code @app.post("/path")} - POST endpoint</li>
 *   <li>{@code @app.put("/path")} - PUT endpoint</li>
 *   <li>{@code @app.delete("/path")} - DELETE endpoint</li>
 *   <li>{@code @app.patch("/path")} - PATCH endpoint</li>
 *   <li>{@code @router.get("/path")} - APIRouter endpoints</li>
 * </ul>
 *
 * <h3>Parameter Extraction</h3>
 * <ul>
 *   <li>Path parameters: {@code {user_id}} or {@code {item_id: int}}</li>
 *   <li>Query parameters: {@code param: Query(...)} or {@code param: Optional[str] = Query(None)}</li>
 *   <li>Body parameters: {@code body: Model} or {@code body: dict}</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code DECORATOR_PATTERN}: {@code @(app|router)\.(get|post|put|delete|patch)\s*\(\s*['"](.*?)['"]}</li>
 *   <li>{@code FUNCTION_PATTERN}: {@code def\s+(\w+)\s*\((.*?)\):}</li>
 *   <li>{@code PATH_PARAM_PATTERN}: {@code \{(\w+)(?::\s*\w+)?\}}</li>
 *   <li>{@code QUERY_PARAM_PATTERN}: {@code (\w+):\s*.*?Query\(}</li>
 *   <li>{@code BODY_PARAM_PATTERN}: {@code (\w+):\s*(\w+)(?!\s*=\s*Query)}</li>
 * </ul>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class FastAPIScanner extends AbstractRegexScanner {

    /**
     * Regex to match FastAPI decorator: @app.get("/users") or @router.post("/items").
     * Captures: (1) app|router, (2) HTTP method, (3) path.
     */
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "@(app|router)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex to match function definition: def get_user(user_id: int):.
     * Captures: (1) function name, (2) parameters.
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "def\\s+(\\w+)\\s*\\((.*)\\):"
    );

    /**
     * Regex to extract path parameters: {user_id} or {item_id: int}.
     * Captures: (1) parameter name.
     */
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile(
        "\\{(\\w+)(?::\\s*\\w+)?\\}"
    );

    /**
     * Regex to match query parameters: param: Query(...) or param: Optional[str] = Query(None).
     * Captures: (1) parameter name.
     */
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile(
        "(\\w+):\\s*.*?Query\\("
    );

    /**
     * Regex to match body parameters: body: UserModel or item: dict.
     * Captures: (1) parameter name, (2) type.
     */
    private static final Pattern BODY_PARAM_PATTERN = Pattern.compile(
        "(\\w+):\\s*(\\w+)(?!\\s*=\\s*Query)(?!\\s*=\\s*Path)(?!\\s*=\\s*Header)"
    );

    @Override
    public String getId() {
        return "fastapi-rest";
    }

    @Override
    public String getDisplayName() {
        return "FastAPI REST Scanner";
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
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*.py");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning FastAPI endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles("**/*.py").toList();

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

        log.info("Found {} FastAPI endpoints", apiEndpoints.size());

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

    private void parsePythonFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        List<String> lines = readFileLines(file);
        String componentId = extractModuleName(file);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Look for FastAPI decorators
            Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(line);
            if (decoratorMatcher.find()) {
                String httpMethod = decoratorMatcher.group(2).toUpperCase();
                String path = decoratorMatcher.group(3);

                // Find the function definition on the next non-empty line
                String functionLine = findNextFunctionDefinition(lines, i + 1);
                if (functionLine != null) {
                    Matcher funcMatcher = FUNCTION_PATTERN.matcher(functionLine);
                    if (funcMatcher.find()) {
                        String functionName = funcMatcher.group(1);
                        String parameters = funcMatcher.group(2);

                        // Extract parameters
                        List<String> pathParams = extractPathParameters(path);
                        List<String> queryParams = extractQueryParameters(parameters);
                        List<String> bodyParams = extractBodyParameters(parameters);

                        String requestSchema = buildRequestSchema(pathParams, queryParams, bodyParams);
                        String responseSchema = "dict"; // Default, could be enhanced

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
                        log.debug("Found FastAPI endpoint: {} {} -> {}", httpMethod, path, functionName);
                    }
                }
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
     * Example: /users/{user_id}/items/{item_id} → [user_id, item_id]
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
     * Extracts query parameters from function parameters.
     * Example: user_id: int = Query(...) → [user_id]
     */
    private List<String> extractQueryParameters(String parameters) {
        List<String> params = new ArrayList<>();
        Matcher matcher = QUERY_PARAM_PATTERN.matcher(parameters);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }

    /**
     * Extracts body parameters from function parameters.
     * Example: body: UserCreate, item: dict → [body: UserCreate, item: dict]
     */
    private List<String> extractBodyParameters(String parameters) {
        List<String> params = new ArrayList<>();
        String[] paramList = parameters.split(",");

        for (String param : paramList) {
            param = param.trim();
            if (param.isEmpty() || param.startsWith("request:") || param.startsWith("response:")) {
                continue;
            }

            // Skip if it's a Query, Path, or Header parameter
            if (param.contains("Query(") || param.contains("Path(") || param.contains("Header(")) {
                continue;
            }

            // Extract parameter name and type
            Matcher matcher = BODY_PARAM_PATTERN.matcher(param);
            if (matcher.find()) {
                String paramName = matcher.group(1);
                String paramType = matcher.group(2);

                // Skip primitive types that are likely path/query params
                if (!paramType.matches("int|str|float|bool")) {
                    params.add(paramName + ": " + paramType);
                }
            }
        }
        return params;
    }

    /**
     * Builds request schema from extracted parameters.
     */
    private String buildRequestSchema(List<String> pathParams, List<String> queryParams, List<String> bodyParams) {
        List<String> allParams = new ArrayList<>();

        if (!pathParams.isEmpty()) {
            allParams.add("Path: " + String.join(", ", pathParams));
        }
        if (!queryParams.isEmpty()) {
            allParams.add("Query: " + String.join(", ", queryParams));
        }
        if (!bodyParams.isEmpty()) {
            allParams.add("Body: " + String.join(", ", bodyParams));
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
