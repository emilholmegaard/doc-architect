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
 * <p>Detects three types of ASP.NET Core API patterns using AST parsing and regex:
 * <ol>
 *   <li><b>MVC Controllers</b> - Traditional attribute-based routing</li>
 *   <li><b>Minimal APIs</b> - Fluent routing (ASP.NET Core 6+)</li>
 *   <li><b>Razor Pages</b> - Convention-based page handlers</li>
 * </ol>
 *
 * <p><b>Pattern 1: MVC Controllers</b></p>
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
 * <p><b>Pattern 2: Minimal APIs (ASP.NET Core 6+)</b></p>
 * <pre>{@code
 * app.MapGet("/api/products", () => {
 *     return Results.Ok(products);
 * });
 *
 * app.MapPost("/api/products", (Product p) => {
 *     return Results.Created($"/api/products/{p.Id}", p);
 * });
 *
 * app.MapGet("/api/products/{id}", (int id) => {
 *     return Results.Ok(GetProduct(id));
 * });
 * }</pre>
 *
 * <p><b>Pattern 3: Razor Pages</b></p>
 * <pre>{@code
 * // File: Pages/Products/Index.cshtml.cs
 * public class IndexModel : PageModel
 * {
 *     public void OnGet() { }  // → GET /Products/Index
 *     public void OnPost() { } // → POST /Products/Index
 * }
 *
 * // File: Pages/Products/Details.cshtml.cs
 * public class DetailsModel : PageModel
 * {
 *     public void OnGet(int id) { }    // → GET /Products/Details
 *     public void OnPost(int id) { }   // → POST /Products/Details
 * }
 * }</pre>
 *
 * <p><b>HTTP Method Attributes (MVC)</b></p>
 * <ul>
 *   <li>{@code [HttpGet]} - GET requests</li>
 *   <li>{@code [HttpPost]} - POST requests</li>
 *   <li>{@code [HttpPut]} - PUT requests</li>
 *   <li>{@code [HttpDelete]} - DELETE requests</li>
 *   <li>{@code [HttpPatch]} - PATCH requests</li>
 * </ul>
 *
 * <p><b>Parameter Attributes (MVC)</b></p>
 * <ul>
 *   <li>{@code [FromRoute]} - Path parameter</li>
 *   <li>{@code [FromQuery]} - Query parameter</li>
 *   <li>{@code [FromBody]} - Request body</li>
 *   <li>{@code [FromHeader]} - Header parameter</li>
 * </ul>
 *
 * <p><b>Parsing Strategy</b></p>
 * <ul>
 *   <li>MVC Controllers: AST-based parsing via {@link com.docarchitect.core.scanner.impl.dotnet.util.CSharpAstParser}</li>
 *   <li>Minimal APIs: Regex pattern matching (cannot be reliably parsed via AST)</li>
 *   <li>Razor Pages: AST-based parsing with convention-based route derivation</li>
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
    private static final String CS_FILE_PATTERN_ROOT = "*.cs";
    
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

    /**
     * Regex to match Minimal API patterns: app.MapGet("/api/products", ...).
     * Captures: (1) HTTP method (Get, Post, etc.), (2) route template.
     */
    private static final Pattern MINIMAL_API_PATTERN = Pattern.compile(
        "(?:app|group|routes|builder)\\.Map(Get|Post|Put|Delete|Patch)\\s*\\(\\s*\"([^\"]+)\""
    );

    /**
     * Regex to match Razor Pages OnXxx handler methods: OnGet(), OnPostAsync().
     * Captures: (1) HTTP method (Get, Post, etc.).
     */
    private static final Pattern RAZOR_PAGE_HANDLER_PATTERN = Pattern.compile(
        "public\\s+(?:async\\s+)?(?:Task<?[^>]*>?\\s+)?On(Get|Post|Put|Delete|Patch)(?:Async)?\\s*\\("
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
        return Set.of(Technologies.CSHARP, Technologies.DOTNET);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(CONTROLLER_FILE_PATTERN, CS_FILE_PATTERN, CS_FILE_PATTERN_ROOT);
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, CONTROLLER_FILE_PATTERN, CS_FILE_PATTERN, CS_FILE_PATTERN_ROOT);
    }

    /**
     * Pre-filter files to only scan those containing ASP.NET Core API patterns.
     *
     * <p>This avoids attempting to parse files that don't contain API code,
     * reducing unnecessary processing and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Controller.cs, *Resource.cs, *Api.cs, *Model.cs (Razor Pages)</li>
     *   <li>ASP.NET Core imports: Microsoft.AspNetCore.Mvc, Microsoft.AspNetCore.Builder</li>
     *   <li>HTTP method attributes: [HttpGet], [HttpPost], etc.</li>
     *   <li>Controller attributes: [ApiController], [Route]</li>
     *   <li>Controller base classes: ControllerBase, Controller</li>
     *   <li>Minimal API patterns: app.MapGet, app.MapPost, etc.</li>
     *   <li>Razor Pages patterns: PageModel, OnGet, OnPost</li>
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
            fileName.contains("Api") ||
            // Razor Pages typically use *Model.cs or *.cshtml.cs
            (fileName.endsWith("Model.cs") && !fileName.contains("ViewModel")) ||
            fileName.endsWith(".cshtml.cs") ||
            // Minimal APIs often in Program.cs or *Endpoints.cs
            fileName.equals("Program.cs") ||
            fileName.endsWith("Endpoints.cs")) {
            log.trace("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain ASP.NET patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\") ||
                            filePath.contains(".test.") || filePath.contains(".Test.");

        try {
            String content = readFileContent(file);

            // Priority 2: Check for ASP.NET Core imports (expanded)
            boolean hasAspNetMvcImport =
                (content.contains("Microsoft.AspNetCore") && content.contains("Mvc")) ||
                (content.contains("using") && content.contains("AspNetCore") && content.contains("Mvc"));

            boolean hasAspNetBuilderImport =
                content.contains("Microsoft.AspNetCore.Builder") ||
                (content.contains("using") && content.contains("AspNetCore") && content.contains("Builder"));

            boolean hasAspNetRazorImport =
                content.contains("Microsoft.AspNetCore.Mvc.RazorPages") ||
                (content.contains("using") && content.contains("RazorPages"));

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

            // Priority 6: Check for Minimal API patterns (ASP.NET Core 6+)
            boolean hasMinimalApiPatterns =
                content.contains("app.Map") ||
                content.contains(".MapGet(") ||
                content.contains(".MapPost(") ||
                content.contains(".MapPut(") ||
                content.contains(".MapDelete(") ||
                content.contains(".MapPatch(");

            // Priority 7: Check for Razor Pages patterns
            boolean hasRazorPagePatterns =
                content.contains(": PageModel") ||
                content.contains("OnGet(") ||
                content.contains("OnPost(") ||
                content.contains("OnPut(") ||
                content.contains("OnDelete(");

            boolean hasAspNetPatterns = hasAspNetMvcImport || hasAspNetBuilderImport || hasAspNetRazorImport ||
                                       hasHttpMethodAttributes || hasControllerAttributes || hasControllerBase ||
                                       hasMinimalApiPatterns || hasRazorPagePatterns;

            if (hasAspNetPatterns) {
                log.debug("Including file with ASP.NET Core patterns: {} (mvcImport={}, builderImport={}, razorImport={}, httpAttrs={}, ctrlAttrs={}, ctrlBase={}, minimalApi={}, razorPages={})",
                    fileName, hasAspNetMvcImport, hasAspNetBuilderImport, hasAspNetRazorImport, hasHttpMethodAttributes,
                    hasControllerAttributes, hasControllerBase, hasMinimalApiPatterns, hasRazorPagePatterns);
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
        // Need to search both subdirectories and root level
        List<Path> csFiles = new ArrayList<>();
        csFiles.addAll(context.findFiles(CS_FILE_PATTERN).toList());
        csFiles.addAll(context.findFiles(CS_FILE_PATTERN_ROOT).toList());
        // Remove duplicates (in case a file matches both patterns)
        csFiles = csFiles.stream().distinct().toList();

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
        // Read file content for pattern matching
        String content = readFileContent(file);

        // Pattern 3: Minimal APIs (check first, doesn't require AST parsing)
        // Must be done before AST parsing because Minimal API files may not have proper class structures
        extractMinimalApiEndpoints(content, file, apiEndpoints);

        // Try to parse with AST for MVC Controllers and Razor Pages
        try {
            List<DotNetAst.CSharpClass> classes = parseAstFile(file);

            for (DotNetAst.CSharpClass csharpClass : classes) {
                String className = csharpClass.name();

                // Pattern 1: MVC Controllers
                if (className.endsWith("Controller")) {
                    String baseRoute = extractBaseRouteFromAst(csharpClass);
                    extractActionMethodsFromAst(csharpClass, baseRoute, apiEndpoints);
                }
                // Pattern 2: Razor Pages
                else if (className.endsWith("Model") || csharpClass.inheritsFrom("PageModel")) {
                    extractRazorPageHandlers(csharpClass, file, apiEndpoints);
                }
            }
        } catch (Exception e) {
            // AST parsing failed - this is ok for files like Program.cs that don't have proper class structures
            // Minimal API endpoints were already extracted above
            log.trace("AST parsing failed for {}, skipping class-based endpoint extraction: {}", file.getFileName(), e.getMessage());
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

    /**
     * Extracts Minimal API endpoints from file content.
     *
     * <p>Minimal APIs (introduced in ASP.NET Core 6) use a fluent syntax like:
     * <pre>{@code
     * app.MapGet("/api/products", () => { });
     * app.MapPost("/api/products", (Product p) => { });
     * }</pre>
     *
     * @param content file content
     * @param file source file path
     * @param apiEndpoints list to add discovered endpoints to
     */
    private void extractMinimalApiEndpoints(String content, Path file, List<ApiEndpoint> apiEndpoints) {
        Matcher matcher = MINIMAL_API_PATTERN.matcher(content);

        while (matcher.find()) {
            String httpMethod = matcher.group(1).toUpperCase(); // Get, Post, etc.
            String routeTemplate = matcher.group(2); // "/api/products"

            // Normalize path
            String fullPath = routeTemplate.startsWith(PATH_SEPARATOR) ? routeTemplate : PATH_SEPARATOR + routeTemplate;

            // Extract parameters from lambda (simplified - just detect presence)
            String requestSchema = extractMinimalApiParameters(content, matcher.end());

            ApiEndpoint endpoint = new ApiEndpoint(
                file.getFileName().toString(),
                ApiType.REST,
                fullPath,
                httpMethod,
                "MinimalAPI",
                requestSchema,
                DEFAULT_RETURN_TYPE,
                null
            );

            apiEndpoints.add(endpoint);
            log.debug("Found Minimal API endpoint: {} {} in {}", httpMethod, fullPath, file.getFileName());
        }
    }

    /**
     * Extracts parameters from Minimal API lambda expression (simplified).
     *
     * <p>This is a basic implementation that detects common parameter patterns.
     * For complex lambdas, consider enhancing with more sophisticated parsing.
     */
    private String extractMinimalApiParameters(String content, int startPos) {
        // Look ahead for lambda parameters like (Product p) or (int id, Product p)
        int endPos = Math.min(startPos + 200, content.length());
        String snippet = content.substring(startPos, endPos);

        // Simple pattern: (Type name, Type2 name2)
        Pattern paramPattern = Pattern.compile("\\(([^)]+)\\)\\s*=>");
        Matcher matcher = paramPattern.matcher(snippet);

        if (matcher.find()) {
            String params = matcher.group(1).trim();
            if (!params.isEmpty() && !params.equals("")) {
                // Parse parameters: "Product p" or "int id, Product p"
                List<String> bodyParams = new ArrayList<>();
                List<String> routeParams = new ArrayList<>();

                for (String param : params.split(",")) {
                    String trimmed = param.trim();
                    Matcher simpleParam = SIMPLE_PARAM_PATTERN.matcher(trimmed);
                    if (simpleParam.find()) {
                        String type = simpleParam.group(1);
                        String name = simpleParam.group(2);

                        if (isPrimitiveType(type)) {
                            routeParams.add(name + TYPE_SEPARATOR + type);
                        } else {
                            bodyParams.add(name + TYPE_SEPARATOR + type);
                        }
                    }
                }

                List<String> allParams = new ArrayList<>();
                if (!routeParams.isEmpty()) {
                    allParams.add(SCHEMA_LABEL_ROUTE + String.join(PARAM_SEPARATOR, routeParams));
                }
                if (!bodyParams.isEmpty()) {
                    allParams.add(SCHEMA_LABEL_BODY + String.join(PARAM_SEPARATOR, bodyParams));
                }

                return allParams.isEmpty() ? null : String.join(SCHEMA_SEPARATOR, allParams);
            }
        }

        return null;
    }

    /**
     * Extracts Razor Pages handler methods from a PageModel class.
     *
     * <p>Razor Pages use convention-based routing where:
     * <ul>
     *   <li>OnGet() handles GET requests</li>
     *   <li>OnPost() handles POST requests</li>
     *   <li>OnGetAsync() handles async GET requests</li>
     * </ul>
     *
     * <p>The route is derived from the file path (e.g., Pages/Products/Index.cshtml.cs -> /Products/Index)
     *
     * @param csharpClass parsed class AST
     * @param file source file path
     * @param apiEndpoints list to add discovered endpoints to
     */
    private void extractRazorPageHandlers(DotNetAst.CSharpClass csharpClass, Path file, List<ApiEndpoint> apiEndpoints) {
        // Derive route from file path
        String filePath = file.toString();
        String route = deriveRazorPageRoute(filePath);

        for (DotNetAst.Method method : csharpClass.methods()) {
            String methodName = method.name();

            // Match OnGet, OnPost, OnGetAsync, OnPostAsync, etc.
            if (methodName.startsWith("On") && (methodName.contains("Get") || methodName.contains("Post") ||
                methodName.contains("Put") || methodName.contains("Delete") || methodName.contains("Patch"))) {

                // Extract HTTP method from handler name
                String httpMethod = extractHttpMethodFromHandler(methodName);
                if (httpMethod == null) {
                    continue;
                }

                // Extract parameters
                String requestSchema = buildRequestSchemaFromAst(method.parameters(), route);

                // Check for handler-specific route (e.g., OnGetDetails with [RouteAttribute])
                String handlerRoute = route;
                for (DotNetAst.Attribute attribute : method.attributes()) {
                    if (attribute.name().equals("Route") && !attribute.arguments().isEmpty()) {
                        String customRoute = attribute.arguments().get(0).replace("\"", "");
                        handlerRoute = customRoute.startsWith(PATH_SEPARATOR) ? customRoute : PATH_SEPARATOR + customRoute;
                    }
                }

                ApiEndpoint endpoint = new ApiEndpoint(
                    csharpClass.name(),
                    ApiType.REST,
                    handlerRoute,
                    httpMethod,
                    csharpClass.name() + "." + methodName,
                    requestSchema,
                    method.returnType(),
                    null
                );

                apiEndpoints.add(endpoint);
                log.debug("Found Razor Page handler: {} {} -> {}", httpMethod, handlerRoute, methodName);
            }
        }
    }

    /**
     * Derives the route for a Razor Page from its file path.
     *
     * <p>Convention: Pages/Products/Index.cshtml.cs -> /Products/Index
     * <p>Special case: Pages/Index.cshtml.cs -> /
     */
    private String deriveRazorPageRoute(String filePath) {
        // Normalize path separators
        String normalized = filePath.replace('\\', '/');

        // Find "Pages/" directory
        int pagesIndex = normalized.indexOf("/Pages/");
        if (pagesIndex == -1) {
            pagesIndex = normalized.indexOf("Pages/");
        }

        if (pagesIndex != -1) {
            // Extract path after Pages/
            String afterPages = normalized.substring(pagesIndex + "Pages/".length());

            // Remove .cshtml.cs or .cs extension
            afterPages = afterPages.replace(".cshtml.cs", "").replace(".cs", "");

            // Remove "Model" suffix from class name if present
            if (afterPages.endsWith("Model")) {
                afterPages = afterPages.substring(0, afterPages.length() - "Model".length());
            }

            // Handle Index pages -> directory route
            if (afterPages.endsWith("/Index")) {
                afterPages = afterPages.substring(0, afterPages.length() - "/Index".length());
            } else if (afterPages.equals("Index")) {
                return PATH_SEPARATOR;
            }

            return afterPages.startsWith(PATH_SEPARATOR) ? afterPages : PATH_SEPARATOR + afterPages;
        }

        // Fallback: use filename
        int lastSlash = normalized.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        fileName = fileName.replace(".cshtml.cs", "").replace(".cs", "");
        if (fileName.endsWith("Model")) {
            fileName = fileName.substring(0, fileName.length() - "Model".length());
        }

        return PATH_SEPARATOR + fileName;
    }

    /**
     * Extracts HTTP method from Razor Page handler name.
     *
     * <p>Examples: OnGet -> GET, OnPostAsync -> POST, OnGetDetails -> GET
     */
    private String extractHttpMethodFromHandler(String handlerName) {
        if (handlerName.contains("Get")) {
            return "GET";
        } else if (handlerName.contains("Post")) {
            return "POST";
        } else if (handlerName.contains("Put")) {
            return "PUT";
        } else if (handlerName.contains("Delete")) {
            return "DELETE";
        } else if (handlerName.contains("Patch")) {
            return "PATCH";
        }
        return null;
    }
}
