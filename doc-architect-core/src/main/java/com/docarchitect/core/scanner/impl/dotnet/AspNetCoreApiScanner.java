package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.DotNetAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.util.Technologies;

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
 * <p><b>Supported Patterns</b></p>
 *
 * <p><b>Controller Attributes:</b></p>
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
 * <p><b>HTTP Method Attributes</b></p>
 * <ul>
 *   <li>{@code [HttpGet]} - GET requests</li>
 *   <li>{@code [HttpPost]} - POST requests</li>
 *   <li>{@code [HttpPut]} - PUT requests</li>
 *   <li>{@code [HttpDelete]} - DELETE requests</li>
 *   <li>{@code [HttpPatch]} - PATCH requests</li>
 * </ul>
 *
 * <p><b>Parameter Attributes</b></p>
 * <ul>
 *   <li>{@code [FromRoute]} - Path parameter</li>
 *   <li>{@code [FromQuery]} - Query parameter</li>
 *   <li>{@code [FromBody]} - Request body</li>
 *   <li>{@code [FromHeader]} - Header parameter</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
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
public class AspNetCoreApiScanner extends AbstractAstScanner<DotNetAst.CSharpClass> {

    // Scanner identification constants
    private static final String SCANNER_ID = "aspnetcore-rest";
    private static final String SCANNER_DISPLAY_NAME = "ASP.NET Core API Scanner";

    /**
     * Constructs a new AspNetCoreApiScanner with the DotNet AST parser.
     *
     * <p>The scanner uses ANTLR-based C# parsing to accurately extract ASP.NET Core
     * REST API endpoint information from controller attributes and action methods.
     */
    public AspNetCoreApiScanner() {
        super(AstParserFactory.getDotNetParser());
    }
    
    // File patterns
    private static final String CONTROLLER_FILE_PATTERN = "**/*Controller.cs";
    private static final String CS_FILE_PATTERN = "**/*.cs";
    
    // C# keywords and identifiers
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String CONTROLLER_PLACEHOLDER = "[controller]";
    private static final String DEFAULT_API_PREFIX = "api/";
    private static final String PUBLIC_KEYWORD = "public ";
    
    // Parameter source types
    private static final String PARAM_SOURCE_ROUTE = "Route";
    private static final String PARAM_SOURCE_QUERY = "Query";
    private static final String PARAM_SOURCE_BODY = "Body";
    private static final String PARAM_SOURCE_HEADER = "Header";
    
    // Schema labels
    private static final String SCHEMA_LABEL_ROUTE = "Route: ";
    private static final String SCHEMA_LABEL_QUERY = "Query: ";
    private static final String SCHEMA_LABEL_BODY = "Body: ";
    private static final String SCHEMA_SEPARATOR = "; ";
    private static final String PARAM_SEPARATOR = ", ";
    private static final String TYPE_SEPARATOR = ": ";
    
    // Default values
    private static final String DEFAULT_RETURN_TYPE = "object";
    private static final String LINE_SEPARATOR = "\n";
    private static final String PATH_SEPARATOR = "/";
    
    // Search limits
    private static final int MAX_METHOD_DECLARATION_LINES = 10;
    private static final int MAX_MULTILINE_METHOD_LINES = 5;
    
    // Primitive C# types
    private static final Set<String> PRIMITIVE_TYPES = Set.of(
        "int", "long", "short", "byte", "sbyte",
        "uint", "ulong", "ushort",
        "float", "double", "decimal",
        "bool", "char", "string",
        "datetime", "guid", "timespan"
    );

    /**
     * Regex to match controller class: public class UsersController : ControllerBase or public class ProductController.
     * Captures: (1) class name.
     * Made more flexible to match controllers that may or may not explicitly extend ControllerBase.
     */
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "public\\s+class\\s+(\\w+Controller)(?:\\s*:\\s*\\w+)?"
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

    /**
     * Regex to match route parameters: {id}, {id:int}.
     * Captures: (1) parameter name.
     */
    private static final Pattern ROUTE_PARAM_PATTERN = Pattern.compile("\\{(\\w+)(?::\\w+)?\\}");

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
        return Set.of(Technologies.CSHARP, Technologies.DOTNET);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(CONTROLLER_FILE_PATTERN, CS_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, CONTROLLER_FILE_PATTERN, CS_FILE_PATTERN);
    }

    /**
     * Pre-filter files to only scan those containing ASP.NET Core API patterns.
     *
     * <p>This avoids attempting to parse files that don't contain API code,
     * reducing unnecessary processing and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Controller.cs, *Resource.cs, *Api.cs</li>
     *   <li>ASP.NET Core MVC imports: Microsoft.AspNetCore.Mvc</li>
     *   <li>HTTP method attributes: [HttpGet], [HttpPost], etc.</li>
     *   <li>Controller attributes: [ApiController], [Route]</li>
     *   <li>Controller base classes: ControllerBase, Controller</li>
     * </ol>
     *
     * @param file path to C# source file
     * @return true if file contains ASP.NET Core API patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Controller.cs") ||
            fileName.endsWith("Resource.cs") ||
            fileName.endsWith("Api.cs") ||
            fileName.contains("Controller") ||
            fileName.contains("Api")) {
            log.trace("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain ASP.NET patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\") ||
                            filePath.contains(".test.") || filePath.contains(".Test.");

        try {
            String content = readFileContent(file);

            // Priority 2: Check for ASP.NET Core MVC imports (loose pattern)
            boolean hasAspNetMvcImport =
                (content.contains("Microsoft.AspNetCore") && content.contains("Mvc")) ||
                (content.contains("using") && content.contains("AspNetCore") && content.contains("Mvc"));

            // Priority 3: Check for HTTP method attributes
            boolean hasHttpMethodAttributes =
                content.contains("[HttpGet") ||
                content.contains("[HttpPost") ||
                content.contains("[HttpPut") ||
                content.contains("[HttpDelete") ||
                content.contains("[HttpPatch");

            // Priority 4: Check for controller attributes
            boolean hasControllerAttributes =
                content.contains("[ApiController]") ||
                content.contains("[Route(") ||
                content.contains("[Route \"");

            // Priority 5: Check for controller base classes
            boolean hasControllerBase =
                (content.contains("ControllerBase") || content.contains(": Controller")) &&
                (hasAspNetMvcImport || hasHttpMethodAttributes);

            boolean hasAspNetPatterns = hasAspNetMvcImport || hasHttpMethodAttributes ||
                                       hasControllerAttributes || hasControllerBase;

            if (hasAspNetPatterns) {
                log.debug("Including file with ASP.NET Core patterns: {} (mvcImport={}, httpAttrs={}, ctrlAttrs={}, ctrlBase={})",
                    fileName, hasAspNetMvcImport, hasHttpMethodAttributes, hasControllerAttributes, hasControllerBase);
            } else {
                log.trace("Skipping file without ASP.NET Core patterns: {}", fileName);
            }

            // For test files, require ASP.NET patterns
            if (isTestFile) {
                return hasAspNetPatterns;
            }

            return hasAspNetPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning ASP.NET Core API endpoints in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> csFiles = context.findFiles(CS_FILE_PATTERN).toList();

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;
        for (Path csFile : csFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(csFile)) {
                skippedFiles++;
                continue;
            }

            try {
                parseCSharpFile(csFile, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                // Files without ASP.NET patterns are already filtered by shouldScanFile()
                // Any remaining parse failures are logged at DEBUG level
                log.debug("Failed to parse C# file: {} - {}", csFile, e.getMessage());
            }
        }

        log.debug("Pre-filtered {} files (not ASP.NET Core controllers)", skippedFiles);
        log.info("Found {} ASP.NET Core API endpoints across {} C# files (parsed {}/{})",
            apiEndpoints.size(), csFiles.size(), parsedFiles, csFiles.size());

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
        // Use AST parser to parse C# file
        List<DotNetAst.CSharpClass> classes = parseAstFile(file);

        for (DotNetAst.CSharpClass csharpClass : classes) {
            String className = csharpClass.name();

            // Only process controller classes
            if (!className.endsWith("Controller")) {
                continue;
            }

            // Extract base route from [Route] attribute
            String baseRoute = extractBaseRouteFromAst(csharpClass);

            // Extract action methods
            extractActionMethodsFromAst(csharpClass, baseRoute, apiEndpoints);
        }
    }

    /**
     * Extracts the class body from the source code.
     */
    private String extractClassBody(List<String> lines, int classStartPos, String content) {
        int lineNumber = content.substring(0, classStartPos).split(LINE_SEPARATOR).length - 1;

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

            classBody.append(line).append(LINE_SEPARATOR);

            if (inClass && braceCount == 0) {
                break;
            }
        }

        return classBody.toString();
    }

    /**
     * Extracts base route from [Route] attribute using AST.
     */
    private String extractBaseRouteFromAst(DotNetAst.CSharpClass csharpClass) {
        // Look for Route attribute
        for (DotNetAst.Attribute attribute : csharpClass.attributes()) {
            if (attribute.name().equals("Route")) {
                if (!attribute.arguments().isEmpty()) {
                    String route = attribute.arguments().get(0).replace("\"", "");
                    // Replace [controller] placeholder with actual controller name
                    if (route.contains(CONTROLLER_PLACEHOLDER)) {
                        String controllerName = csharpClass.name().replace(CONTROLLER_SUFFIX, "").toLowerCase();
                        route = route.replace(CONTROLLER_PLACEHOLDER, controllerName);
                    }
                    return route;
                }
            }
        }

        // Default route from controller name
        return DEFAULT_API_PREFIX + csharpClass.name().replace(CONTROLLER_SUFFIX, "").toLowerCase();
    }

    /**
     * Extracts action methods from AST.
     */
    private void extractActionMethodsFromAst(DotNetAst.CSharpClass csharpClass, String baseRoute,
                                             List<ApiEndpoint> apiEndpoints) {
        String className = csharpClass.name();

        for (DotNetAst.Method method : csharpClass.methods()) {
            // Look for HTTP method attributes (HttpGet, HttpPost, etc.)
            String httpMethod = null;
            String methodRoute = null;

            for (DotNetAst.Attribute attribute : method.attributes()) {
                String attrName = attribute.name();
                if (attrName.startsWith("Http")) {
                    httpMethod = attrName.substring(4).toUpperCase(); // HttpGet -> GET
                    if (!attribute.arguments().isEmpty()) {
                        methodRoute = attribute.arguments().get(0).replace("\"", "");
                    }
                    break;
                }
            }

            if (httpMethod != null) {
                // Build full path
                String fullPath = combinePaths(baseRoute, methodRoute);

                // Extract parameters
                String requestSchema = buildRequestSchemaFromAst(method.parameters(), fullPath);

                ApiEndpoint endpoint = new ApiEndpoint(
                    className,
                    ApiType.REST,
                    fullPath,
                    httpMethod,
                    className + "." + method.name(),
                    requestSchema,
                    method.returnType(),
                    null
                );

                apiEndpoints.add(endpoint);
                log.debug("Found ASP.NET Core endpoint: {} {} -> {}", httpMethod, fullPath, method.name());
            }
        }
    }

    /**
     * Finds the next method declaration starting from the given line index.
     */
    private String findNextMethodDeclaration(String[] lines, int startIndex, int maxLines) {
        for (int i = startIndex; i < Math.min(startIndex + maxLines, lines.length); i++) {
            String line = lines[i].trim();
            if (line.startsWith(PUBLIC_KEYWORD)) {
                // Collect multi-line method declarations
                StringBuilder method = new StringBuilder(line);
                int j = i + 1;
                while (j < lines.length && !lines[j].trim().contains("{")) {
                    method.append(" ").append(lines[j].trim());
                    j++;
                    if (j - i > MAX_MULTILINE_METHOD_LINES) break;
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

        if (!baseRoute.startsWith(PATH_SEPARATOR)) {
            baseRoute = PATH_SEPARATOR + baseRoute;
        }

        if (methodRoute.isEmpty()) {
            return baseRoute;
        }

        if (methodRoute.startsWith(PATH_SEPARATOR)) {
            return methodRoute; // Absolute path
        }

        if (baseRoute.endsWith(PATH_SEPARATOR)) {
            return baseRoute + methodRoute;
        }

        return baseRoute + PATH_SEPARATOR + methodRoute;
    }

    /**
     * Builds request schema from AST parameters.
     */
    private String buildRequestSchemaFromAst(List<DotNetAst.Parameter> parameters, String fullPath) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }

        List<String> routeParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        List<String> bodyParams = new ArrayList<>();

        // Extract path parameters from route
        Matcher routeMatcher = ROUTE_PARAM_PATTERN.matcher(fullPath);
        while (routeMatcher.find()) {
            routeParams.add(routeMatcher.group(1));
        }

        // Process method parameters from AST
        for (DotNetAst.Parameter parameter : parameters) {
            String type = parameter.type();
            String name = parameter.name();

            // Check for From* attributes
            String source = null;
            for (DotNetAst.Attribute attribute : parameter.attributes()) {
                if (attribute.name().startsWith("From")) {
                    source = attribute.name().substring(4); // FromBody -> Body
                    break;
                }
            }

            if (source != null) {
                switch (source) {
                    case PARAM_SOURCE_ROUTE -> routeParams.add(name + TYPE_SEPARATOR + type);
                    case PARAM_SOURCE_QUERY -> queryParams.add(name + TYPE_SEPARATOR + type);
                    case PARAM_SOURCE_BODY -> bodyParams.add(name + TYPE_SEPARATOR + type);
                }
            } else {
                // No From* attribute - infer from type and route
                if (routeParams.contains(name)) {
                    continue; // Already in route
                }

                // Complex types are body parameters
                if (!isPrimitiveType(type)) {
                    bodyParams.add(name + TYPE_SEPARATOR + type);
                } else {
                    // Simple types default to query parameters
                    queryParams.add(name + TYPE_SEPARATOR + type);
                }
            }
        }

        List<String> allParams = new ArrayList<>();
        if (!routeParams.isEmpty()) {
            allParams.add(SCHEMA_LABEL_ROUTE + String.join(PARAM_SEPARATOR, routeParams));
        }
        if (!queryParams.isEmpty()) {
            allParams.add(SCHEMA_LABEL_QUERY + String.join(PARAM_SEPARATOR, queryParams));
        }
        if (!bodyParams.isEmpty()) {
            allParams.add(SCHEMA_LABEL_BODY + String.join(PARAM_SEPARATOR, bodyParams));
        }

        return allParams.isEmpty() ? null : String.join(SCHEMA_SEPARATOR, allParams);
    }

    /**
     * Checks if a type is a primitive C# type.
     */
    private boolean isPrimitiveType(String type) {
        return PRIMITIVE_TYPES.contains(type.toLowerCase());
    }
}
