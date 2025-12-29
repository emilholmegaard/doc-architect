package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
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
 * Scanner for JAX-RS (Jakarta/Javax) REST API endpoints in Java source files.
 *
 * <p>This scanner uses JavaParser to analyze Java source code and extract REST API endpoint
 * information from JAX-RS annotations ({@code @Path}, {@code @GET}, {@code @POST}, etc.).
 *
 * <p><b>JAX-RS Support:</b>
 * <ul>
 *   <li>Jakarta EE (modern): {@code jakarta.ws.rs.*}</li>
 *   <li>Java EE (legacy): {@code javax.ws.rs.*}</li>
 * </ul>
 *
 * <p><b>Supported Frameworks:</b>
 * <ul>
 *   <li>RESTEasy (JBoss/Red Hat - used by Keycloak)</li>
 *   <li>Jersey (Reference implementation)</li>
 *   <li>Apache CXF</li>
 *   <li>Quarkus REST (formerly RESTEasy Reactive)</li>
 * </ul>
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Java files using pattern matching</li>
 *   <li>Pre-filter files containing JAX-RS imports</li>
 *   <li>Parse Java source using JavaParser AST</li>
 *   <li>Find classes/interfaces annotated with @Path</li>
 *   <li>Extract HTTP method annotations (@GET, @POST, etc.)</li>
 *   <li>Combine class-level and method-level @Path values</li>
 *   <li>Extract @Produces and @Consumes for content types</li>
 *   <li>Capture method parameters (@PathParam, @QueryParam, etc.)</li>
 *   <li>Create ApiEndpoint records for each discovered endpoint</li>
 * </ol>
 *
 * <p><b>Supported Annotations:</b>
 * <ul>
 *   <li>Resource: @Path (class or method level)</li>
 *   <li>HTTP Methods: @GET, @POST, @PUT, @DELETE, @PATCH, @HEAD, @OPTIONS</li>
 *   <li>Content: @Produces, @Consumes</li>
 *   <li>Parameters: @PathParam, @QueryParam, @FormParam, @HeaderParam, @MatrixParam</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new JaxRsApiScanner();
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
public class JaxRsApiScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "jaxrs-api";
    private static final String DISPLAY_NAME = "JAX-RS API Scanner";
    private static final String JAVA_FILE_PATTERN = "**/*.java";
    private static final int DEFAULT_PRIORITY = 50;

    private static final String PATH_ANNOTATION = "Path";
    private static final String GET_ANNOTATION = "GET";
    private static final String POST_ANNOTATION = "POST";
    private static final String PUT_ANNOTATION = "PUT";
    private static final String DELETE_ANNOTATION = "DELETE";
    private static final String PATCH_ANNOTATION = "PATCH";
    private static final String HEAD_ANNOTATION = "HEAD";
    private static final String OPTIONS_ANNOTATION = "OPTIONS";
    private static final String PRODUCES_ANNOTATION = "Produces";
    private static final String CONSUMES_ANNOTATION = "Consumes";

    private static final String VALUE_ATTRIBUTE = "value";
    private static final String PATH_REGEX = "[\"'{}\\[\\]]";
    private static final String PATH_SEPARATOR = "/";

    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
        GET_ANNOTATION, POST_ANNOTATION, PUT_ANNOTATION, DELETE_ANNOTATION,
        PATCH_ANNOTATION, HEAD_ANNOTATION, OPTIONS_ANNOTATION);

    private static final Set<String> PARAMETER_ANNOTATIONS = Set.of(
        "PathParam", "QueryParam", "FormParam", "HeaderParam", "MatrixParam", "CookieParam");

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
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, JAVA_FILE_PATTERN);
    }

    /**
     * Pre-filter files to only scan those containing JAX-RS imports or annotations.
     *
     * <p>This avoids attempting to parse files that don't contain JAX-RS REST code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Resource.java, *Endpoint.java, *Api.java</li>
     *   <li>JAX-RS package imports: jakarta.ws.rs or javax.ws.rs</li>
     *   <li>Direct JAX-RS annotations: @Path, @GET, @POST, etc.</li>
     *   <li>JAX-RS classes: Response, MediaType, UriInfo, etc.</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains JAX-RS patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Resource.java") ||
            fileName.endsWith("Endpoint.java") ||
            fileName.endsWith("Api.java") ||
            fileName.contains("Resource") ||
            fileName.contains("Endpoint")) {
            log.trace("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain JAX-RS patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Priority 2: Check for JAX-RS package imports (loose pattern for wildcards)
            // Matches: jakarta.ws.rs.* OR javax.ws.rs.* OR individual imports
            boolean hasJaxRsImport =
                (content.contains("jakarta") && content.contains("ws") && content.contains("rs")) ||
                (content.contains("javax") && content.contains("ws") && content.contains("rs"));

            // Priority 3: Check for direct JAX-RS annotations (catches any usage)
            boolean hasJaxRsAnnotations =
                content.contains("@Path") ||
                content.contains("@GET") ||
                content.contains("@POST") ||
                content.contains("@PUT") ||
                content.contains("@DELETE") ||
                content.contains("@PATCH") ||
                content.contains("@Produces") ||
                content.contains("@Consumes");

            // Priority 4: Check for common JAX-RS classes (conservative)
            boolean hasJaxRsClasses =
                content.contains("Response") ||
                content.contains("MediaType") ||
                content.contains("UriInfo");

            boolean hasJaxRsPatterns = hasJaxRsImport || hasJaxRsAnnotations || hasJaxRsClasses;

            if (hasJaxRsPatterns) {
                log.debug("Including file with JAX-RS patterns: {} (import={}, annotations={}, classes={})",
                    fileName, hasJaxRsImport, hasJaxRsAnnotations, hasJaxRsClasses);
            } else {
                log.trace("Skipping file without JAX-RS patterns: {}", fileName);
            }

            // For test files, require JAX-RS patterns
            // For non-test files, allow if they have JAX-RS patterns
            if (isTestFile) {
                return hasJaxRsPatterns;
            }

            return hasJaxRsPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning JAX-RS APIs in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all Java files
        List<Path> javaFiles = context.findFiles(JAVA_FILE_PATTERN).toList();

        if (javaFiles.isEmpty()) {
            log.warn("No Java files found in project");
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;
        for (Path javaFile : javaFiles) {
            // Pre-filter files before attempting to parse
            if (!shouldScanFile(javaFile)) {
                skippedFiles++;
                continue;
            }

            try {
                parseJaxRsResource(javaFile, apiEndpoints, components);
                parsedFiles++;
            } catch (Exception e) {
                // Files without JAX-RS patterns are already filtered by shouldScanFile()
                // Any remaining parse failures are logged at DEBUG level
                log.debug("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
                // Continue processing other files instead of failing completely
            }
        }

        log.debug("Pre-filtered {} files (not JAX-RS resources)", skippedFiles);

        log.info("Found {} JAX-RS API endpoints across {} Java files (parsed {}/{})",
            apiEndpoints.size(), javaFiles.size(), parsedFiles, javaFiles.size());

        return buildSuccessResult(
            components,
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single Java file and extracts JAX-RS API endpoints.
     *
     * @param javaFile path to Java file
     * @param apiEndpoints list to add discovered API endpoints
     * @param components list to add discovered components (reserved for future use)
     * @throws IOException if file cannot be read
     */
    @SuppressWarnings("unused")
    private void parseJaxRsResource(Path javaFile, List<ApiEndpoint> apiEndpoints, List<Component> components) throws IOException {
        // Parse Java source using JavaParser
        var compilationUnit = parseJavaFile(javaFile);

        if (compilationUnit.isEmpty()) {
            return; // Parsing failed, skip this file
        }

        CompilationUnit cu = compilationUnit.get();

        // Find all classes and interfaces
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Check if class/interface has @Path annotation
            Optional<AnnotationExpr> pathAnnotation = classDecl.getAnnotations().stream()
                .filter(ann -> PATH_ANNOTATION.equals(ann.getNameAsString()))
                .findFirst();

            if (pathAnnotation.isEmpty()) {
                return; // Skip classes without @Path
            }

            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Extract base path from class-level @Path
            String basePath = extractPathFromAnnotation(pathAnnotation.get());

            // Extract class-level @Produces and @Consumes
            String classProduces = extractContentType(classDecl.getAnnotations(), PRODUCES_ANNOTATION);
            String classConsumes = extractContentType(classDecl.getAnnotations(), CONSUMES_ANNOTATION);

            log.debug("Found JAX-RS resource: {} with base path: {}", fullyQualifiedName, basePath);

            // Find all HTTP method annotated methods
            classDecl.getMethods().forEach(method -> {
                extractEndpointFromMethod(method, fullyQualifiedName, basePath, classProduces, classConsumes, apiEndpoints);
            });
        });
    }

    /**
     * Extracts API endpoint from a method declaration.
     *
     * @param method method declaration
     * @param resourceName resource class/interface name
     * @param basePath base path from class-level @Path
     * @param classProduces class-level @Produces value
     * @param classConsumes class-level @Consumes value
     * @param apiEndpoints list to add discovered endpoints
     */
    private void extractEndpointFromMethod(MethodDeclaration method, String resourceName,
                                           String basePath, String classProduces, String classConsumes,
                                           List<ApiEndpoint> apiEndpoints) {
        // Find HTTP method annotation
        Optional<String> httpMethod = method.getAnnotations().stream()
            .map(AnnotationExpr::getNameAsString)
            .filter(HTTP_METHOD_ANNOTATIONS::contains)
            .findFirst();

        if (httpMethod.isEmpty()) {
            return; // Not an HTTP method
        }

        // Extract method-level @Path if present
        String methodPath = method.getAnnotations().stream()
            .filter(ann -> PATH_ANNOTATION.equals(ann.getNameAsString()))
            .findFirst()
            .map(this::extractPathFromAnnotation)
            .orElse("");

        // Combine base path and method path
        String fullPath = combinePaths(basePath, methodPath);

        // Extract method-level @Produces and @Consumes (overrides class-level)
        String methodProduces = extractContentType(method.getAnnotations(), PRODUCES_ANNOTATION);
        String methodConsumes = extractContentType(method.getAnnotations(), CONSUMES_ANNOTATION);

        String produces = methodProduces != null ? methodProduces : classProduces;
        String consumes = methodConsumes != null ? methodConsumes : classConsumes;

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
            resourceName, // componentId
            ApiType.REST,
            fullPath,
            httpMethod.get(),
            resourceName + "." + method.getNameAsString(),
            requestSchema != null ? requestSchema : consumes,
            returnType + (produces != null ? " (" + produces + ")" : ""),
            null // authentication
        );

        apiEndpoints.add(endpoint);
        log.debug("Found JAX-RS endpoint: {} {} -> {}", httpMethod.get(), fullPath, resourceName);
    }

    /**
     * Extracts path value from a @Path annotation.
     *
     * @param annotation @Path annotation
     * @return path value or empty string
     */
    private String extractPathFromAnnotation(AnnotationExpr annotation) {
        if (annotation == null) {
            return "";
        }

        // Handle @Path("/path") - single member annotation
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            return cleanPath(singleMember.getMemberValue().toString());
        }

        // Handle @Path(value = "/path")
        if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            return normalAnnotation.getPairs().stream()
                .filter(pair -> VALUE_ATTRIBUTE.equals(pair.getNameAsString()))
                .findFirst()
                .map(MemberValuePair::getValue)
                .map(expr -> cleanPath(expr.toString()))
                .orElse("");
        }

        return "";
    }

    /**
     * Extracts content type from @Produces or @Consumes annotation.
     *
     * @param annotations list of annotations
     * @param annotationName annotation name (Produces or Consumes)
     * @return content type or null
     */
    private String extractContentType(List<AnnotationExpr> annotations, String annotationName) {
        return annotations.stream()
            .filter(ann -> annotationName.equals(ann.getNameAsString()))
            .findFirst()
            .map(ann -> {
                if (ann instanceof SingleMemberAnnotationExpr singleMember) {
                    return cleanContentType(singleMember.getMemberValue().toString());
                }
                if (ann instanceof NormalAnnotationExpr normalAnnotation) {
                    return normalAnnotation.getPairs().stream()
                        .filter(pair -> VALUE_ATTRIBUTE.equals(pair.getNameAsString()))
                        .findFirst()
                        .map(pair -> cleanContentType(pair.getValue().toString()))
                        .orElse(null);
                }
                return null;
            })
            .orElse(null);
    }

    /**
     * Cleans content type string by removing quotes and converting MediaType constants to actual values.
     *
     * @param contentType raw content type string
     * @return cleaned content type
     */
    private String cleanContentType(String contentType) {
        String cleaned = cleanPath(contentType);

        // Convert MediaType constants to actual MIME types
        return cleaned
            .replace("MediaType.APPLICATION_JSON", "application/json")
            .replace("MediaType.APPLICATION_XML", "application/xml")
            .replace("MediaType.TEXT_PLAIN", "text/plain")
            .replace("MediaType.TEXT_HTML", "text/html")
            .replace("MediaType.MULTIPART_FORM_DATA", "multipart/form-data")
            .replace("MediaType.APPLICATION_FORM_URLENCODED", "application/x-www-form-urlencoded")
            .replace("MediaType.APPLICATION_OCTET_STREAM", "application/octet-stream");
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

        // Check for JAX-RS parameter annotations
        Optional<String> annotationType = param.getAnnotations().stream()
            .filter(ann -> PARAMETER_ANNOTATIONS.contains(ann.getNameAsString()))
            .map(AnnotationExpr::getNameAsString)
            .findFirst();

        if (annotationType.isPresent()) {
            return annotationType.get() + ":" + paramName + ":" + paramType;
        }

        return null; // Not a JAX-RS parameter
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
     * @param basePath base path from class-level @Path
     * @param methodPath method-level @Path
     * @return combined path
     */
    private String combinePaths(String basePath, String methodPath) {
        if (methodPath == null || methodPath.isEmpty()) {
            return basePath != null && !basePath.isEmpty() ? basePath : PATH_SEPARATOR;
        }
        if (basePath == null || basePath.isEmpty()) {
            return methodPath.startsWith(PATH_SEPARATOR) ? methodPath : PATH_SEPARATOR + methodPath;
        }

        String cleanBase = basePath.endsWith(PATH_SEPARATOR) ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanMethod = methodPath.startsWith(PATH_SEPARATOR) ? methodPath : PATH_SEPARATOR + methodPath;

        return cleanBase + cleanMethod;
    }
}
