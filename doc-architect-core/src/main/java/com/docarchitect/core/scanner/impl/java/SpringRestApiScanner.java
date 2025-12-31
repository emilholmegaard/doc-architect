package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.scanner.base.RegexPatterns;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

/**
 * Scanner for Spring MVC REST API endpoints in Java source files.
 *
 * <p>This scanner uses JavaParser to analyze Java source code and extract REST API endpoint
 * information from Spring MVC annotations ({@code @RestController}, {@code @Controller},
 * {@code @RequestMapping}, {@code @GetMapping}, etc.).
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Java files using pattern matching</li>
 *   <li>Parse Java source using JavaParser AST</li>
 *   <li>Find classes annotated with @RestController or @Controller</li>
 *   <li>Extract request mapping methods (@GetMapping, @PostMapping, etc.)</li>
 *   <li>Capture method parameters (@PathVariable, @RequestParam, @RequestBody)</li>
 *   <li>Create ApiEndpoint records for each discovered endpoint</li>
 * </ol>
 *
 * <p><b>Supported Annotations:</b>
 * <ul>
 *   <li>Class-level: @RestController, @Controller, @RequestMapping</li>
 *   <li>Method-level: @GetMapping, @PostMapping, @PutMapping, @PatchMapping, @DeleteMapping, @RequestMapping</li>
 *   <li>Parameter: @PathVariable, @RequestParam, @RequestBody, @RequestHeader</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new SpringRestApiScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<ApiEndpoint> endpoints = result.apiEndpoints();
 * }</pre>
 *
 * @see AbstractJavaParserScanner
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class SpringRestApiScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "spring-rest-api";
    private static final String DISPLAY_NAME = "Spring REST API Scanner";
    private static final String JAVA_FILE_PATTERN = "**/*.java";
    private static final int DEFAULT_PRIORITY = 50;

    private static final String REST_CONTROLLER_ANNOTATION = "RestController";
    private static final String CONTROLLER_ANNOTATION = "Controller";
    private static final String REQUEST_MAPPING = "RequestMapping";
    private static final String GET_MAPPING = "GetMapping";
    private static final String POST_MAPPING = "PostMapping";
    private static final String PUT_MAPPING = "PutMapping";
    private static final String PATCH_MAPPING = "PatchMapping";
    private static final String DELETE_MAPPING = "DeleteMapping";

    private static final String VALUE_ATTRIBUTE = "value";
    private static final String PATH_ATTRIBUTE = "path";
    private static final String METHOD_ATTRIBUTE = "method";
    private static final String REQUEST_METHOD_PREFIX = "RequestMethod\\.";
    private static final String DEFAULT_HTTP_METHOD = "GET";

    private static final String PATH_REGEX = "[\"'{}\\[\\]]";
    private static final String PATH_SEPARATOR = "/";

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(REST_CONTROLLER_ANNOTATION, CONTROLLER_ANNOTATION);
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
        REQUEST_MAPPING, GET_MAPPING, POST_MAPPING, PUT_MAPPING, PATCH_MAPPING, DELETE_MAPPING);
    private static final Set<String> PARAMETER_ANNOTATIONS = Set.of("PathVariable", "RequestParam", "RequestBody", "RequestHeader");

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final Pattern BASE_PATH_PATTERN = Pattern.compile("@RequestMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
    private static final Pattern GET_MAPPING_PATTERN = Pattern.compile("@GetMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
    private static final Pattern POST_MAPPING_PATTERN = Pattern.compile("@PostMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
    private static final Pattern PUT_MAPPING_PATTERN = Pattern.compile("@PutMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
    private static final Pattern DELETE_MAPPING_PATTERN = Pattern.compile("@DeleteMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");
    private static final Pattern PATCH_MAPPING_PATTERN = Pattern.compile("@PatchMapping\\s*\\(\\s*[\"']([^\"']+)[\"']");

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(JAVA_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasJavaFiles()
            .and(ApplicabilityStrategies.hasSpringFramework());
    }

    /**
     * Pre-filter files to only scan those containing Spring MVC imports or annotations.
     *
     * <p>This avoids attempting to parse files that don't contain Spring REST code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Controller.java, *RestController.java, *Resource.java</li>
     *   <li>Spring Web package imports (including wildcards): springframework.web + annotation</li>
     *   <li>Direct Spring MVC annotations: @RestController, @GetMapping, etc.</li>
     *   <li>Base class patterns: extends *Controller with Spring imports</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains Spring MVC patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Controller.java") ||
            fileName.endsWith("RestController.java") ||
            fileName.endsWith("Resource.java")) {
            log.trace("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain Spring MVC patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Priority 2: Check for Spring Web package imports (loose pattern for wildcards)
            // Matches: org.springframework.web.bind.annotation.* OR individual imports
            boolean hasSpringWebImport =
                (content.contains("springframework") && content.contains("web") && content.contains("annotation")) ||
                (content.contains("springframework") && content.contains("stereotype") && content.contains("Controller"));

            // Priority 3: Check for direct Spring MVC annotations (catches any usage)
            boolean hasSpringAnnotations =
                content.contains("@RestController") ||
                content.contains("@Controller") ||
                content.contains("@RequestMapping") ||
                content.contains("@GetMapping") ||
                content.contains("@PostMapping") ||
                content.contains("@PutMapping") ||
                content.contains("@DeleteMapping") ||
                content.contains("@PatchMapping");

            // Priority 4: Check for base class patterns (conservative)
            // Matches: extends BaseController/BaseRestController/etc. with Spring imports
            boolean extendsController =
                content.contains("extends") &&
                (content.contains("Controller") || content.contains("Resource")) &&
                hasSpringWebImport;

            boolean hasSpringMvcPatterns = hasSpringWebImport || hasSpringAnnotations || extendsController;

            if (hasSpringMvcPatterns) {
                log.debug("Including file with Spring MVC patterns: {} (webImport={}, annotations={}, extends={})",
                    fileName, hasSpringWebImport, hasSpringAnnotations, extendsController);
            } else {
                log.trace("Skipping file without Spring MVC patterns: {}", fileName);
            }

            // For test files, require Spring MVC patterns
            // For non-test files, allow if they have Spring MVC patterns
            if (isTestFile) {
                return hasSpringMvcPatterns;
            }

            return hasSpringMvcPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Spring REST APIs in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        // Find all Java files
        List<Path> javaFiles = context.findFiles(JAVA_FILE_PATTERN).toList();
        statsBuilder.filesDiscovered(javaFiles.size());

        if (javaFiles.isEmpty()) {
            log.warn("No Java files found in project");
            return emptyResult();
        }

        int skippedFiles = 0;
        for (Path javaFile : javaFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(javaFile)) {
                skippedFiles++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<ApiEndpoint> result = parseWithFallback(
                javaFile,
                cu -> extractEndpointsFromAST(cu),
                createFallbackStrategy(),
                statsBuilder
            );

            if (result.isSuccess()) {
                apiEndpoints.addAll(result.getData());
            }
        }

        log.debug("Pre-filtered {} files (not Spring MVC controllers)", skippedFiles);

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} REST API endpoints (success rate: {:.1f}%, overall parse rate: {:.1f}%)",
            apiEndpoints.size(), statistics.getSuccessRate(), statistics.getOverallParseRate());

        return buildSuccessResult(
            components,
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of(), // No warnings
            statistics
        );
    }

    /**
     * Extracts API endpoints from a parsed CompilationUnit using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param cu the parsed CompilationUnit
     * @return list of discovered API endpoints
     */
    private List<ApiEndpoint> extractEndpointsFromAST(CompilationUnit cu) {
        List<ApiEndpoint> endpoints = new ArrayList<>();

        // Find all classes
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Check if class is a controller
            boolean isController = classDecl.getAnnotations().stream()
                .anyMatch(ann -> CONTROLLER_ANNOTATIONS.contains(ann.getNameAsString()));

            if (!isController) {
                return; // Skip non-controller classes
            }

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Extract base path from class-level @RequestMapping
            String basePath = extractPathFromAnnotation(
                classDecl.getAnnotations().stream()
                    .filter(ann -> REQUEST_MAPPING.equals(ann.getNameAsString()))
                    .findFirst()
                    .orElse(null)
            );

            log.debug("Found Spring controller: {} with base path: {}", fullyQualifiedName, basePath);

            // Find all request mapping methods
            classDecl.getMethods().forEach(method -> {
                extractEndpointFromMethod(method, fullyQualifiedName, basePath, endpoints);
            });
        });

        return endpoints;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * <p>The fallback strategy uses regex patterns to extract:
     * <ul>
     *   <li>@RestController or @Controller annotations</li>
     *   <li>@GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping annotations</li>
     *   <li>Path values from annotation parameters</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<ApiEndpoint> createFallbackStrategy() {
        return (file, content) -> {
            List<ApiEndpoint> endpoints = new ArrayList<>();

            // Check if file contains controller annotations
            if (!content.contains("@RestController") && !content.contains("@Controller")) {
                return endpoints; // Not a controller file
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract base path from class-level @RequestMapping
            String basePath = extractBasePathFromContent(content);

            // Extract endpoints for each HTTP method using pre-compiled patterns
            endpoints.addAll(extractEndpointsWithPattern(GET_MAPPING_PATTERN, "GET", basePath, fullyQualifiedName, content));
            endpoints.addAll(extractEndpointsWithPattern(POST_MAPPING_PATTERN, "POST", basePath, fullyQualifiedName, content));
            endpoints.addAll(extractEndpointsWithPattern(PUT_MAPPING_PATTERN, "PUT", basePath, fullyQualifiedName, content));
            endpoints.addAll(extractEndpointsWithPattern(DELETE_MAPPING_PATTERN, "DELETE", basePath, fullyQualifiedName, content));
            endpoints.addAll(extractEndpointsWithPattern(PATCH_MAPPING_PATTERN, "PATCH", basePath, fullyQualifiedName, content));

            log.debug("Fallback parsing found {} endpoints in {}", endpoints.size(), file.getFileName());
            return endpoints;
        };
    }

    /**
     * Extracts endpoints using a regex pattern for a specific HTTP method.
     */
    private List<ApiEndpoint> extractEndpointsWithPattern(Pattern pattern, String httpMethod,
                                                          String basePath, String componentId, String content) {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String path = matcher.group(1);
            String fullPath = combinePaths(basePath, path);

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                ApiType.REST,
                fullPath,
                httpMethod,
                componentId + ".<unknown>", // method name unknown in fallback
                null, // request schema unknown
                null, // response schema unknown
                null  // authentication unknown
            );

            endpoints.add(endpoint);
        }

        return endpoints;
    }

    /**
     * Extracts base path from class-level @RequestMapping annotation.
     */
    private String extractBasePathFromContent(String content) {
        Matcher matcher = BASE_PATH_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Parses a single Java file and extracts REST API endpoints.
     * @deprecated Use {@link #extractEndpointsFromAST(CompilationUnit)} instead
     *
     * @param javaFile path to Java file
     * @param apiEndpoints list to add discovered API endpoints
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    @Deprecated
    private void parseSpringController(Path javaFile, List<ApiEndpoint> apiEndpoints, List<Component> components) throws IOException {
        // Parse Java source using JavaParser
        var compilationUnit = parseJavaFile(javaFile);

        if (compilationUnit.isEmpty()) {
            return; // Parsing failed, skip this file
        }

        CompilationUnit cu = compilationUnit.get();

        // Find all classes
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Check if class is a controller
            boolean isController = classDecl.getAnnotations().stream()
                .anyMatch(ann -> CONTROLLER_ANNOTATIONS.contains(ann.getNameAsString()));

            if (!isController) {
                return; // Skip non-controller classes
            }

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Extract base path from class-level @RequestMapping
            String basePath = extractPathFromAnnotation(
                classDecl.getAnnotations().stream()
                    .filter(ann -> REQUEST_MAPPING.equals(ann.getNameAsString()))
                    .findFirst()
                    .orElse(null)
            );

            log.debug("Found Spring controller: {} with base path: {}", fullyQualifiedName, basePath);

            // Find all request mapping methods
            classDecl.getMethods().forEach(method -> {
                extractEndpointFromMethod(method, fullyQualifiedName, basePath, apiEndpoints);
            });
        });
    }

    /**
     * Extracts API endpoint from a method declaration.
     *
     * @param method method declaration
     * @param controllerName controller class name
     * @param basePath base path from controller
     * @param apiEndpoints list to add discovered endpoints
     */
    private void extractEndpointFromMethod(MethodDeclaration method, String controllerName,
                                           String basePath, List<ApiEndpoint> apiEndpoints) {
        // Find mapping annotation
        Optional<AnnotationExpr> mappingAnnotation = method.getAnnotations().stream()
            .filter(ann -> MAPPING_ANNOTATIONS.contains(ann.getNameAsString()))
            .findFirst();

        if (mappingAnnotation.isEmpty()) {
            return; // Not a request mapping method
        }

        AnnotationExpr annotation = mappingAnnotation.get();
        String annotationName = annotation.getNameAsString();

        // Extract path from annotation
        String methodPath = extractPathFromAnnotation(annotation);

        // Combine base path and method path
        String fullPath = combinePaths(basePath, methodPath);

        // Determine HTTP method
        String httpMethod = determineHttpMethod(annotationName, annotation);

        // Extract parameters
        List<String> parameters = new ArrayList<>();
        method.getParameters().forEach(param -> {
            String paramInfo = extractParameterInfo(param);
            if (paramInfo != null) {
                parameters.add(paramInfo);
            }
        });

        // Extract return type
        String returnType = method.getType().asString();

        // Build request schema from parameters
        String requestSchema = parameters.isEmpty() ? null : String.join(", ", parameters);

        // Create API endpoint
        ApiEndpoint endpoint = new ApiEndpoint(
            controllerName, // componentId
            ApiType.REST,
            fullPath,
            httpMethod,
            controllerName + "." + method.getNameAsString(),
            requestSchema,
            returnType,
            null // authentication
        );

        apiEndpoints.add(endpoint);
        log.debug("Found API endpoint: {} {} -> {}", httpMethod, fullPath, controllerName);
    }

    /**
     * Extracts path value from a mapping annotation.
     *
     * @param annotation mapping annotation
     * @return path value or empty string
     */
    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        if (annotation == null) {
            return "";
        }

        // Handle @GetMapping("/path") - single member annotation
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            return cleanPath(singleMember.getMemberValue().toString());
        }

        // Handle @RequestMapping(value = "/path", method = RequestMethod.GET)
        if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            return normalAnnotation.getPairs().stream()
                .filter(pair -> VALUE_ATTRIBUTE.equals(pair.getNameAsString()) || PATH_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(MemberValuePair::getValue)
                .map(expr -> cleanPath(expr.toString()))
                .orElse("");
        }

        return "";
    }

    /**
     * Determines HTTP method from annotation.
     *
     * @param annotationName annotation name (GetMapping, PostMapping, etc.)
     * @param annotation annotation expression
     * @return HTTP method (GET, POST, PUT, etc.)
     */
    private String determineHttpMethod(String annotationName, AnnotationExpr annotation) {
        return switch (annotationName) {
            case GET_MAPPING -> "GET";
            case POST_MAPPING -> "POST";
            case PUT_MAPPING -> "PUT";
            case PATCH_MAPPING -> "PATCH";
            case DELETE_MAPPING -> "DELETE";
            case REQUEST_MAPPING -> {
                // Extract method from annotation if present
                if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
                    String method = normalAnnotation.getPairs().stream()
                        .filter(pair -> METHOD_ATTRIBUTE.equals(pair.getNameAsString()))
                        .findFirst()
                        .map(pair -> pair.getValue().toString())
                        .map(value -> value.replaceAll(REQUEST_METHOD_PREFIX, ""))
                        .orElse(DEFAULT_HTTP_METHOD);
                    yield method;
                }
                yield DEFAULT_HTTP_METHOD;
            }
            default -> DEFAULT_HTTP_METHOD;
        };
    }

    /**
     * Extracts parameter information from method parameter.
     *
     * @param param method parameter
     * @return parameter description or null
     */
    private String extractParameterInfo(Parameter param) {
        String paramType = param.getType().asString();
        String paramName = param.getNameAsString();

        // Check for parameter annotations
        Optional<String> annotationType = param.getAnnotations().stream()
            .filter(ann -> PARAMETER_ANNOTATIONS.contains(ann.getNameAsString()))
            .map(AnnotationExpr::getNameAsString)
            .findFirst();

        if (annotationType.isPresent()) {
            return annotationType.get() + ":" + paramName + ":" + paramType;
        }

        return null; // Not a REST parameter
    }

    /**
     * Cleans path string by removing quotes and array brackets.
     *
     * @param path raw path string
     * @return cleaned path
     */
    private String cleanPath(String path) {
        return path.replaceAll(PATH_REGEX, "").trim();
    }

    /**
     * Combines base path and method path into full path.
     *
     * @param basePath base path from controller
     * @param methodPath method path
     * @return combined path
     */
    private String combinePaths(String basePath, String methodPath) {
        if (basePath == null || basePath.isEmpty()) {
            return methodPath.startsWith(PATH_SEPARATOR) ? methodPath : PATH_SEPARATOR + methodPath;
        }
        if (methodPath == null || methodPath.isEmpty()) {
            return basePath;
        }

        String cleanBase = basePath.endsWith(PATH_SEPARATOR) ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanMethod = methodPath.startsWith(PATH_SEPARATOR) ? methodPath : PATH_SEPARATOR + methodPath;

        return cleanBase + cleanMethod;
    }
}
