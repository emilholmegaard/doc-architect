package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

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

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of("RestController", "Controller");
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
        "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping"
    );

    @Override
    public String getId() {
        return "spring-rest-api";
    }

    @Override
    public String getDisplayName() {
        return "Spring REST API Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.java");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*.java");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Spring REST APIs in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all Java files
        List<Path> javaFiles = context.findFiles("**/*.java").toList();

        if (javaFiles.isEmpty()) {
            log.warn("No Java files found in project");
            return emptyResult();
        }

        int parsedFiles = 0;
        for (Path javaFile : javaFiles) {
            try {
                parseSpringController(javaFile, apiEndpoints, components);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
                // Continue processing other files instead of failing completely
            }
        }

        log.info("Found {} REST API endpoints across {} Java files (parsed {}/{})",
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
     * Parses a single Java file and extracts REST API endpoints.
     *
     * @param javaFile path to Java file
     * @param apiEndpoints list to add discovered API endpoints
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
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
                    .filter(ann -> "RequestMapping".equals(ann.getNameAsString()))
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
                .filter(pair -> "value".equals(pair.getNameAsString()) || "path".equals(pair.getNameAsString()))
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
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "PatchMapping" -> "PATCH";
            case "DeleteMapping" -> "DELETE";
            case "RequestMapping" -> {
                // Extract method from annotation if present
                if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
                    String method = normalAnnotation.getPairs().stream()
                        .filter(pair -> "method".equals(pair.getNameAsString()))
                        .findFirst()
                        .map(pair -> pair.getValue().toString())
                        .map(value -> value.replaceAll("RequestMethod\\.", ""))
                        .orElse("GET");
                    yield method;
                }
                yield "GET"; // Default to GET
            }
            default -> "GET";
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
            .filter(ann -> Set.of("PathVariable", "RequestParam", "RequestBody", "RequestHeader").contains(ann.getNameAsString()))
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
        return path.replaceAll("[\"'{}\\[\\]]", "").trim();
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
            return methodPath.startsWith("/") ? methodPath : "/" + methodPath;
        }
        if (methodPath == null || methodPath.isEmpty()) {
            return basePath;
        }

        String cleanBase = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
        String cleanMethod = methodPath.startsWith("/") ? methodPath : "/" + methodPath;

        return cleanBase + cleanMethod;
    }
}
