package com.docarchitect.core.scanner.impl.dotnet;

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
 * Scanner for ASP.NET Core REST API endpoints in C# source files.
 *
 * <p>Uses regex patterns to extract controller attributes and action methods.
 * Similar to Spring's annotation-based approach, but for C# attributes.
 *
 * <h3>Supported Patterns</h3>
 *
 * <h4>Controller Attributes</h4>
 * <pre>{@code
 * [ApiController]
 * [Route("api/[controller]")]
 * public class UsersController : ControllerBase
 * {
 *     [HttpGet]
 *     public IActionResult GetAll() { }
 *
 *     [HttpGet("{id}")]
 *     public IActionResult GetById(int id) { }
 *
 *     [HttpPost]
 *     public IActionResult Create([FromBody] UserDto user) { }
 * }
 * }</pre>
 *
 * <h3>HTTP Method Attributes</h3>
 * <ul>
 *   <li>{@code [HttpGet]} - GET requests</li>
 *   <li>{@code [HttpPost]} - POST requests</li>
 *   <li>{@code [HttpPut]} - PUT requests</li>
 *   <li>{@code [HttpDelete]} - DELETE requests</li>
 *   <li>{@code [HttpPatch]} - PATCH requests</li>
 * </ul>
 *
 * <h3>Parameter Attributes</h3>
 * <ul>
 *   <li>{@code [FromRoute]} - Path parameter</li>
 *   <li>{@code [FromQuery]} - Query parameter</li>
 *   <li>{@code [FromBody]} - Request body</li>
 *   <li>{@code [FromHeader]} - Header parameter</li>
 * </ul>
 *
 * <h3>Regex Patterns</h3>
 * <ul>
 *   <li>{@code CLASS_PATTERN}: {@code public\s+class\s+(\w+)\s*:\s*ControllerBase}</li>
 *   <li>{@code ROUTE_PATTERN}: {@code \[Route\("(.+?)"\)\]}</li>
 *   <li>{@code HTTP_METHOD_PATTERN}: {@code \[Http(Get|Post|Put|Delete|Patch)(?:\("(.+?)")?\]}</li>
 *   <li>{@code METHOD_PATTERN}: {@code public\s+\w+\s+(\w+)\s*\((.+?)\)}</li>
 *   <li>{@code PARAM_PATTERN}: {@code \[From(Route|Query|Body|Header)\]\s*(\w+)\s+(\w+)}</li>
 * </ul>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class AspNetCoreApiScanner extends AbstractRegexScanner {

    /**
     * Regex to match controller class: public class UsersController : ControllerBase.
     * Captures: (1) class name.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "public\\s+class\\s+(\\w+)\\s*:\\s*ControllerBase"
    );

    /**
     * Regex to match [Route("...")] attribute.
     * Captures: (1) route template.
     */
    private static final Pattern ROUTE_PATTERN = Pattern.compile(
        "\\[Route\\(\"(.+?)\"\\)\\]"
    );

    /**
     * Regex to match HTTP method attributes: [HttpGet], [HttpPost("{id}")], etc.
     * Captures: (1) HTTP method (Get, Post, etc.), (2) optional route template.
     */
    private static final Pattern HTTP_METHOD_PATTERN = Pattern.compile(
        "\\[Http(Get|Post|Put|Delete|Patch)(?:\\(\"(.+?)\"\\))?\\]"
    );

    /**
     * Regex to match method declaration: public IActionResult GetById(int id).
     * Captures: (1) method name, (2) parameters.
     */
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "public\\s+\\w+(?:<[^>]+>)?\\s+(\\w+)\\s*\\(([^)]*)\\)"
    );

    /**
     * Regex to match parameter with From* attribute: [FromBody] UserDto user.
     * Captures: (1) source (Route, Query, Body, Header), (2) type, (3) name.
     */
    private static final Pattern PARAM_PATTERN = Pattern.compile(
        "\\[From(Route|Query|Body|Header)\\]\\s*(\\w+)\\s+(\\w+)"
    );

    /**
     * Regex to match simple parameter: int id, string name.
     * Captures: (1) type, (2) name.
     */
    private static final Pattern SIMPLE_PARAM_PATTERN = Pattern.compile(
        "(\\w+(?:<[^>]+>)?)\\s+(\\w+)"
    );

    @Override
    public String getId() {
        return "aspnetcore-rest";
    }

    @Override
    public String getDisplayName() {
        return "ASP.NET Core API Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("csharp", "dotnet");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*Controller.cs", "**/*.cs");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*Controller.cs", "**/*.cs");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning ASP.NET Core API endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> csFiles = context.findFiles("**/*.cs").toList();

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path csFile : csFiles) {
            try {
                parseCSharpFile(csFile, apiEndpoints);
            } catch (Exception e) {
                log.warn("Failed to parse C# file: {} - {}", csFile, e.getMessage());
            }
        }

        log.info("Found {} ASP.NET Core API endpoints", apiEndpoints.size());

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

    private void parseCSharpFile(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        List<String> lines = readFileLines(file);
        String content = String.join("\n", lines);

        // Find controller classes
        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            int classStartPos = classMatcher.start();

            // Extract class body
            String classBody = extractClassBody(lines, classStartPos, content);

            // Extract base route from [Route] attribute
            String baseRoute = extractBaseRoute(classBody, className);

            // Extract action methods
            extractActionMethods(classBody, className, baseRoute, apiEndpoints);
        }
    }

    /**
     * Extracts the class body from the source code.
     */
    private String extractClassBody(List<String> lines, int classStartPos, String content) {
        int lineNumber = content.substring(0, classStartPos).split("\n").length - 1;

        StringBuilder classBody = new StringBuilder();
        int braceCount = 0;
        boolean inClass = false;

        for (int i = lineNumber; i < lines.size(); i++) {
            String line = lines.get(i);

            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceCount++;
                    inClass = true;
                } else if (c == '}') {
                    braceCount--;
                }
            }

            classBody.append(line).append("\n");

            if (inClass && braceCount == 0) {
                break;
            }
        }

        return classBody.toString();
    }

    /**
     * Extracts base route from [Route] attribute or generates from controller name.
     */
    private String extractBaseRoute(String classBody, String className) {
        Matcher routeMatcher = ROUTE_PATTERN.matcher(classBody);
        if (routeMatcher.find()) {
            String route = routeMatcher.group(1);
            // Replace [controller] placeholder with actual controller name
            if (route.contains("[controller]")) {
                String controllerName = className.replace("Controller", "").toLowerCase();
                route = route.replace("[controller]", controllerName);
            }
            return route;
        }

        // Default route from controller name
        return "api/" + className.replace("Controller", "").toLowerCase();
    }

    /**
     * Extracts action methods from class body.
     */
    private void extractActionMethods(String classBody, String className, String baseRoute,
                                      List<ApiEndpoint> apiEndpoints) {
        String[] lines = classBody.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Look for HTTP method attributes
            Matcher httpMatcher = HTTP_METHOD_PATTERN.matcher(line);
            if (httpMatcher.find()) {
                String httpMethod = httpMatcher.group(1).toUpperCase();
                String methodRoute = httpMatcher.group(2);

                // Find the method declaration (within next 10 lines)
                String methodDeclaration = findNextMethodDeclaration(lines, i + 1, 10);
                if (methodDeclaration != null) {
                    Matcher methodMatcher = METHOD_PATTERN.matcher(methodDeclaration);
                    if (methodMatcher.find()) {
                        String methodName = methodMatcher.group(1);
                        String parameters = methodMatcher.group(2);

                        // Build full path
                        String fullPath = combinePaths(baseRoute, methodRoute);

                        // Extract parameters
                        String requestSchema = buildRequestSchema(parameters, fullPath);

                        ApiEndpoint endpoint = new ApiEndpoint(
                            className,
                            ApiType.REST,
                            fullPath,
                            httpMethod,
                            className + "." + methodName,
                            requestSchema,
                            "object", // Default return type
                            null
                        );

                        apiEndpoints.add(endpoint);
                        log.debug("Found ASP.NET Core endpoint: {} {} -> {}", httpMethod, fullPath, methodName);
                    }
                }
            }
        }
    }

    /**
     * Finds the next method declaration starting from the given line index.
     */
    private String findNextMethodDeclaration(String[] lines, int startIndex, int maxLines) {
        for (int i = startIndex; i < Math.min(startIndex + maxLines, lines.length); i++) {
            String line = lines[i].trim();
            if (line.startsWith("public ")) {
                // Collect multi-line method declarations
                StringBuilder method = new StringBuilder(line);
                int j = i + 1;
                while (j < lines.length && !lines[j].trim().contains("{")) {
                    method.append(" ").append(lines[j].trim());
                    j++;
                    if (j - i > 5) break; // Limit multi-line search
                }
                return method.toString();
            }
        }
        return null;
    }

    /**
     * Combines base route and method route.
     */
    private String combinePaths(String baseRoute, String methodRoute) {
        if (baseRoute == null) {
            baseRoute = "";
        }
        if (methodRoute == null) {
            methodRoute = "";
        }

        baseRoute = baseRoute.trim();
        methodRoute = methodRoute.trim();

        if (!baseRoute.startsWith("/")) {
            baseRoute = "/" + baseRoute;
        }

        if (methodRoute.isEmpty()) {
            return baseRoute;
        }

        if (methodRoute.startsWith("/")) {
            return methodRoute; // Absolute path
        }

        if (baseRoute.endsWith("/")) {
            return baseRoute + methodRoute;
        }

        return baseRoute + "/" + methodRoute;
    }

    /**
     * Builds request schema from method parameters.
     */
    private String buildRequestSchema(String parameters, String fullPath) {
        if (parameters == null || parameters.trim().isEmpty()) {
            return null;
        }

        List<String> routeParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        List<String> bodyParams = new ArrayList<>();

        // Extract path parameters from route
        Pattern routeParamPattern = Pattern.compile("\\{(\\w+)(?::\\w+)?\\}");
        Matcher routeMatcher = routeParamPattern.matcher(fullPath);
        while (routeMatcher.find()) {
            routeParams.add(routeMatcher.group(1));
        }

        // Parse method parameters
        String[] paramArray = parameters.split(",");
        for (String param : paramArray) {
            param = param.trim();
            if (param.isEmpty()) {
                continue;
            }

            // Check for From* attributes
            Matcher fromMatcher = PARAM_PATTERN.matcher(param);
            if (fromMatcher.find()) {
                String source = fromMatcher.group(1);
                String type = fromMatcher.group(2);
                String name = fromMatcher.group(3);

                switch (source) {
                    case "Route" -> routeParams.add(name + ": " + type);
                    case "Query" -> queryParams.add(name + ": " + type);
                    case "Body" -> bodyParams.add(name + ": " + type);
                }
            } else {
                // Simple parameter without From* attribute
                Matcher simpleMatcher = SIMPLE_PARAM_PATTERN.matcher(param);
                if (simpleMatcher.find()) {
                    String type = simpleMatcher.group(1);
                    String name = simpleMatcher.group(2);

                    // If parameter is in route, it's a route param
                    if (routeParams.contains(name)) {
                        continue; // Already added
                    }

                    // Complex types are body parameters
                    if (!isPrimitiveType(type)) {
                        bodyParams.add(name + ": " + type);
                    } else {
                        // Simple types default to query parameters (unless in route)
                        queryParams.add(name + ": " + type);
                    }
                }
            }
        }

        List<String> allParams = new ArrayList<>();
        if (!routeParams.isEmpty()) {
            allParams.add("Route: " + String.join(", ", routeParams));
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
     * Checks if a type is a primitive C# type.
     */
    private boolean isPrimitiveType(String type) {
        return switch (type.toLowerCase()) {
            case "int", "long", "short", "byte", "sbyte",
                 "uint", "ulong", "ushort",
                 "float", "double", "decimal",
                 "bool", "char", "string",
                 "datetime", "guid", "timespan" -> true;
            default -> false;
        };
    }
}
