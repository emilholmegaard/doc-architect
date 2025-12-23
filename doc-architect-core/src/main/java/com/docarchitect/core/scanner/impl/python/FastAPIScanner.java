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
 * Scanner for FastAPI REST endpoints in Python source files.
 *
 * <p>Since we're running in Java, we parse Python files as TEXT using regex patterns
 * to extract route decorators and function definitions.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code @app.get("/path")} - FastAPI instance endpoints</li>
 *   <li>{@code @router.get("/path")} - APIRouter endpoints</li>
 *   <li>{@code @api.get("/path")} - Custom variable names</li>
 *   <li>Supports any valid Python variable name: {@code @my_router.post("/items")}</li>
 *   <li>All HTTP methods: GET, POST, PUT, DELETE, PATCH</li>
 * </ul>
 *
 * <p><b>Parameter Extraction</b></p>
 * <ul>
 *   <li>Path parameters: {@code {user_id}} or {@code {item_id: int}}</li>
 *   <li>Query parameters: {@code param: Query(...)} or {@code param: Optional[str] = Query(None)}</li>
 *   <li>Body parameters: {@code body: Model} or {@code body: dict}</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
 * <ul>
 *   <li>{@code DECORATOR_PATTERN}: {@code @[a-zA-Z_][a-zA-Z0-9_]*\.(get|post|put|delete|patch)\s*\(\s*['"](.*?)['"]}</li>
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

    private static final String SCANNER_ID = "fastapi-rest";
    private static final String SCANNER_DISPLAY_NAME = "FastAPI REST Scanner";
    private static final String PATTERN_PYTHON_FILES = "**/*.py";
    
    private static final String DEFAULT_RESPONSE_SCHEMA = "dict";
    private static final String PYTHON_FILE_EXTENSION = "\\.py$";
    
    private static final String FUNCTION_PREFIX = "def ";

    private static final String PARAM_REQUEST = "request:";
    private static final String PARAM_RESPONSE = "response:";
    private static final String PARAM_QUERY = "Query(";
    private static final String PARAM_PATH = "Path(";
    private static final String PARAM_HEADER = "Header(";

    private static final String PRIMITIVE_TYPES_REGEX = "int|str|float|bool";

    private static final String SCHEMA_PATH_PREFIX = "Path: ";
    private static final String SCHEMA_QUERY_PREFIX = "Query: ";
    private static final String SCHEMA_BODY_PREFIX = "Body: ";
    private static final String SCHEMA_SEPARATOR = "; ";
    private static final String PARAM_SEPARATOR = ", ";
    private static final String TYPE_SEPARATOR = ": ";

    private static final int MAX_FUNCTION_SEARCH_LINES = 5;

    /**
     * Regex to match FastAPI decorator: @app.get("/users") or @router.post("/items") or @api.get("/v1").
     * Matches any valid Python identifier (variable name) before the HTTP method.
     * Captures: (1) variable name, (2) HTTP method, (3) path.
     */
    private static final Pattern DECORATOR_PATTERN = Pattern.compile(
        "@([a-zA-Z_][a-zA-Z0-9_]*)\\.(get|post|put|delete|patch)\\s*\\(\\s*['\"](.+?)['\"]"
    );

    /**
     * Regex to match function definition: def get_user(user_id: int): or def get_user() -> Any:.
     * Captures: (1) function name, (2) parameters.
     * Handles return type annotations like ) -> Any:
     */
    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
        "def\\s+(\\w+)\\s*\\((.*)\\)\\s*(?:->\\s*[^:]+)?:"
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
        return Set.of(PATTERN_PYTHON_FILES);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PATTERN_PYTHON_FILES);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning FastAPI endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles(PATTERN_PYTHON_FILES).toList();

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
            String decoratorText = extractDecoratorText(lines, i);
            if (decoratorText == null) {
                continue;
            }

            Matcher decoratorMatcher = DECORATOR_PATTERN.matcher(decoratorText);
            if (decoratorMatcher.find()) {
                int functionStartIndex = findFunctionStartIndex(lines, i);
                ApiEndpoint endpoint = createEndpointFromDecorator(
                    decoratorMatcher,
                    lines,
                    functionStartIndex,
                    componentId
                );

                if (endpoint != null) {
                    apiEndpoints.add(endpoint);
                    log.debug("Found FastAPI endpoint: {} {}", endpoint.method(), endpoint.path());
                }
            }
        }
    }

    /**
     * Extracts decorator text, handling both single-line and multi-line decorators.
     * Returns null if the line is not a FastAPI decorator.
     */
    private String extractDecoratorText(List<String> lines, int lineIndex) {
        String line = lines.get(lineIndex).trim();

        if (!line.startsWith("@")) {
            return null;
        }

        // Check if this looks like a FastAPI decorator
        if (!containsHttpMethod(line)) {
            return null;
        }

        // Single-line decorator
        if (line.contains("\"") && !line.endsWith("(")) {
            return line;
        }

        // Multi-line decorator - collect until closing parenthesis
        return collectMultiLineDecorator(lines, lineIndex);
    }

    /**
     * Checks if line contains an HTTP method decorator.
     */
    private boolean containsHttpMethod(String line) {
        return line.contains(".get(") || line.contains(".post(") ||
               line.contains(".put(") || line.contains(".delete(") ||
               line.contains(".patch(");
    }

    /**
     * Collects a multi-line decorator into a single string.
     */
    private String collectMultiLineDecorator(List<String> lines, int startIndex) {
        StringBuilder decorator = new StringBuilder();
        int openParens = 0;
        boolean started = false;

        for (int i = startIndex; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            decorator.append(line).append(" ");

            // Count parentheses to find the end
            for (char c : line.toCharArray()) {
                if (c == '(') {
                    openParens++;
                    started = true;
                } else if (c == ')') {
                    openParens--;
                }
            }

            if (started && openParens == 0) {
                break;
            }
        }

        return decorator.toString();
    }

    /**
     * Finds the index where the function definition starts after a decorator.
     */
    private int findFunctionStartIndex(List<String> lines, int decoratorIndex) {
        // Skip past any additional decorator lines
        int index = decoratorIndex;
        while (index < lines.size()) {
            String line = lines.get(index).trim();
            if (line.startsWith("def ")) {
                return index;
            }
            index++;
            if (index - decoratorIndex > MAX_FUNCTION_SEARCH_LINES) {
                break;
            }
        }
        return decoratorIndex + 1;
    }

    /**
     * Creates an ApiEndpoint from a matched decorator and function definition.
     */
    private ApiEndpoint createEndpointFromDecorator(
            Matcher decoratorMatcher,
            List<String> lines,
            int functionStartIndex,
            String componentId) {

        String httpMethod = decoratorMatcher.group(2).toUpperCase();
        String path = decoratorMatcher.group(3);

        String functionLine = findNextFunctionDefinition(lines, functionStartIndex);
        if (functionLine == null) {
            return null;
        }

        Matcher funcMatcher = FUNCTION_PATTERN.matcher(functionLine);
        if (!funcMatcher.find()) {
            return null;
        }

        String functionName = funcMatcher.group(1);
        String parameters = funcMatcher.group(2);

        List<String> pathParams = extractPathParameters(path);
        List<String> queryParams = extractQueryParameters(parameters);
        List<String> bodyParams = extractBodyParameters(parameters);

        String requestSchema = buildRequestSchema(pathParams, queryParams, bodyParams);

        return new ApiEndpoint(
            componentId,
            ApiType.REST,
            path,
            httpMethod,
            componentId + "." + functionName,
            requestSchema,
            DEFAULT_RESPONSE_SCHEMA,
            null
        );
    }

    /**
     * Finds the next function definition starting from the given line index.
     * Handles multi-line function definitions by collecting lines until the closing : after ).
     */
    private String findNextFunctionDefinition(List<String> lines, int startIndex) {
        for (int i = startIndex; i < Math.min(startIndex + MAX_FUNCTION_SEARCH_LINES, lines.size()); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith(FUNCTION_PREFIX)) {
                // Check if this is a complete single-line function definition
                // Handle both ): and ) -> Type: patterns
                if (line.contains(")") && line.contains(":")) {
                    // Find the colon that ends the function signature
                    int closingParenIndex = line.indexOf(")");
                    int colonIndex = line.indexOf(":", closingParenIndex);
                    if (colonIndex > 0) {
                        return line.substring(0, colonIndex + 1);
                    }
                }

                // Multi-line function definition - collect until we find : after )
                StringBuilder functionDef = new StringBuilder(line);
                boolean foundClosingParen = line.contains(")");

                for (int j = i + 1; j < Math.min(i + MAX_FUNCTION_SEARCH_LINES, lines.size()); j++) {
                    String nextLine = lines.get(j).trim();
                    functionDef.append(" ").append(nextLine);

                    if (!foundClosingParen && nextLine.contains(")")) {
                        foundClosingParen = true;
                    }

                    if (foundClosingParen && nextLine.contains(":")) {
                        // Found the end of the function signature
                        String fullDef = functionDef.toString();
                        int closingParenIndex = fullDef.indexOf(")");
                        int colonIndex = fullDef.indexOf(":", closingParenIndex);
                        if (colonIndex > 0) {
                            return fullDef.substring(0, colonIndex + 1);
                        }
                    }
                }
                // If we didn't find closing, return null
                return null;
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
            if (param.isEmpty() || param.startsWith(PARAM_REQUEST) || param.startsWith(PARAM_RESPONSE)) {
                continue;
            }

            // Skip if it's a Query, Path, or Header parameter
            if (param.contains(PARAM_QUERY) || param.contains(PARAM_PATH) || param.contains(PARAM_HEADER)) {
                continue;
            }

            // Extract parameter name and type
            Matcher matcher = BODY_PARAM_PATTERN.matcher(param);
            if (matcher.find()) {
                String paramName = matcher.group(1);
                String paramType = matcher.group(2);

                // Skip primitive types that are likely path/query params
                if (!paramType.matches(PRIMITIVE_TYPES_REGEX)) {
                    params.add(paramName + TYPE_SEPARATOR + paramType);
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
            allParams.add(SCHEMA_PATH_PREFIX + String.join(PARAM_SEPARATOR, pathParams));
        }
        if (!queryParams.isEmpty()) {
            allParams.add(SCHEMA_QUERY_PREFIX + String.join(PARAM_SEPARATOR, queryParams));
        }
        if (!bodyParams.isEmpty()) {
            allParams.add(SCHEMA_BODY_PREFIX + String.join(PARAM_SEPARATOR, bodyParams));
        }

        return allParams.isEmpty() ? null : String.join(SCHEMA_SEPARATOR, allParams);
    }

    /**
     * Extracts module name from file path.
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll(PYTHON_FILE_EXTENSION, "");
    }
}
