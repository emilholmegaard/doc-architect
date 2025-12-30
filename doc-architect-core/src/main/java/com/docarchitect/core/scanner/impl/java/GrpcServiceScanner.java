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
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.scanner.base.RegexPatterns;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

/**
 * Scanner for gRPC service implementations in Java source files.
 *
 * <p>Detects gRPC service implementations that extend generated base classes
 * from protobuf definitions. Links protobuf service definitions to their
 * Java implementations.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code extends UserServiceGrpc.UserServiceImplBase} - Standard gRPC service base</li>
 *   <li>{@code @GrpcService} - Spring Boot gRPC annotation</li>
 *   <li>{@code implements BindableService} - Generic bindable service</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * @GrpcService
 * public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {
 *     @Override
 *     public void getUser(GetUserRequest req, StreamObserver<GetUserResponse> resp) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 *
 * <p>Generates:
 * <ul>
 *   <li>ApiEndpoint: /UserService/GetUser (type: GRPC, handler: UserServiceImpl.getUser)</li>
 * </ul>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class GrpcServiceScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "grpc-service";
    private static final String SCANNER_DISPLAY_NAME = "gRPC Service Implementation Scanner";
    private static final String FILE_PATTERN = "**/*.java";
    private static final int SCANNER_PRIORITY = 55;

    private static final String GRPC_SERVICE_ANNOTATION = "GrpcService";
    private static final String GRPC_BASE_SUFFIX = "ImplBase";
    private static final String OVERRIDE_ANNOTATION = "Override";

    // Regex patterns for fallback parsing
    private static final Pattern GRPC_SERVICE_PATTERN =
        Pattern.compile("class\\s+(\\w+)\\s+extends\\s+(\\w+)Grpc\\.(\\w+)ImplBase");

    private static final Pattern GRPC_METHOD_PATTERN =
        Pattern.compile("public\\s+void\\s+(\\w+)\\s*\\([^)]*StreamObserver");

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
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, FILE_PATTERN);
    }

    @Override
    protected boolean shouldScanFile(Path file) {
        String fileName = file.getFileName().toString();

        // Quick filename check
        if (fileName.contains("Grpc") || fileName.contains("Service") || fileName.endsWith("Impl.java")) {
            return true;
        }

        try {
            String content = readFileContent(file);
            return content.contains("@GrpcService") ||
                   content.contains("ImplBase") ||
                   content.contains("io.grpc.") ||
                   content.contains("StreamObserver") ||
                   content.contains("BindableService");
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning gRPC service implementations in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> javaFiles = context.findFiles(FILE_PATTERN).toList();
        statsBuilder.filesDiscovered(javaFiles.size());

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        int skippedCount = 0;

        for (Path javaFile : javaFiles) {
            if (!shouldScanFile(javaFile)) {
                skippedCount++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            FileParseResult<ApiEndpoint> result = parseWithFallback(
                javaFile,
                cu -> extractApiEndpointsFromAST(cu),
                createFallbackStrategy(),
                statsBuilder
            );

            if (result.isSuccess()) {
                apiEndpoints.addAll(result.getData());
            }
        }

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} gRPC service endpoints (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
                 apiEndpoints.size(), statistics.getSuccessRate(), statistics.getOverallParseRate(), skippedCount);

        return buildSuccessResult(
            List.of(),
            List.of(),
            apiEndpoints,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            statistics
        );
    }

    private List<ApiEndpoint> extractApiEndpointsFromAST(CompilationUnit cu) {
        List<ApiEndpoint> endpoints = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            // Check if this is a gRPC service implementation
            Optional<String> serviceName = extractServiceName(classDecl);

            if (serviceName.isPresent()) {
                String className = classDecl.getNameAsString();
                String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
                String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

                // Extract all @Override methods (these are the gRPC methods)
                classDecl.getMethods().forEach(method -> {
                    if (isGrpcMethod(method)) {
                        String methodName = method.getNameAsString();
                        String path = "/" + serviceName.get() + "/" + capitalize(methodName);

                        ApiEndpoint endpoint = new ApiEndpoint(
                            fullyQualifiedName,
                            ApiType.GRPC,
                            path,
                            "POST", // gRPC always uses POST
                            fullyQualifiedName + "." + methodName,
                            extractRequestType(method),
                            extractResponseType(method),
                            null
                        );

                        endpoints.add(endpoint);
                        log.debug("Found gRPC endpoint: {} in service: {}", path, fullyQualifiedName);
                    }
                });
            }
        });

        return endpoints;
    }

    private Optional<String> extractServiceName(ClassOrInterfaceDeclaration classDecl) {
        // Check for @GrpcService annotation
        if (classDecl.getAnnotationByName(GRPC_SERVICE_ANNOTATION).isPresent()) {
            // Try to extract service name from extended class
            return classDecl.getExtendedTypes().stream()
                .filter(type -> type.getNameAsString().contains("Grpc"))
                .map(type -> {
                    String typeName = type.getNameAsString();
                    // Extract service name from pattern: UserServiceGrpc.UserServiceImplBase
                    if (typeName.contains(".")) {
                        String[] parts = typeName.split("\\.");
                        if (parts.length > 0 && parts[0].endsWith("Grpc")) {
                            return parts[0].substring(0, parts[0].length() - 4); // Remove "Grpc" suffix
                        }
                    }
                    return null;
                })
                .filter(name -> name != null)
                .findFirst();
        }

        // Check if extends *Grpc.*ImplBase
        return classDecl.getExtendedTypes().stream()
            .filter(type -> type.getNameAsString().contains("ImplBase"))
            .map(type -> {
                String typeName = type.getNameAsString();
                if (typeName.contains(".")) {
                    String[] parts = typeName.split("\\.");
                    if (parts.length > 0 && parts[0].endsWith("Grpc")) {
                        return parts[0].substring(0, parts[0].length() - 4);
                    }
                }
                return null;
            })
            .filter(name -> name != null)
            .findFirst();
    }

    private boolean isGrpcMethod(MethodDeclaration method) {
        // gRPC methods are @Override and have StreamObserver parameter
        boolean hasOverride = method.getAnnotationByName(OVERRIDE_ANNOTATION).isPresent();
        boolean hasStreamObserver = method.getParameters().stream()
            .anyMatch(param -> param.getType().asString().contains("StreamObserver"));

        return hasOverride && hasStreamObserver;
    }

    private String extractRequestType(MethodDeclaration method) {
        return method.getParameters().stream()
            .filter(param -> !param.getType().asString().contains("StreamObserver"))
            .map(param -> param.getType().asString())
            .findFirst()
            .orElse("Request");
    }

    private String extractResponseType(MethodDeclaration method) {
        return method.getParameters().stream()
            .filter(param -> param.getType().asString().contains("StreamObserver"))
            .map(param -> {
                String type = param.getType().asString();
                // Extract type from StreamObserver<Type>
                int start = type.indexOf('<');
                int end = type.lastIndexOf('>');
                if (start > 0 && end > start) {
                    return type.substring(start + 1, end);
                }
                return "Response";
            })
            .findFirst()
            .orElse("Response");
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private FallbackParsingStrategy<ApiEndpoint> createFallbackStrategy() {
        return (file, content) -> {
            List<ApiEndpoint> endpoints = new ArrayList<>();

            if (!content.contains("Grpc") && !content.contains("StreamObserver")) {
                return endpoints;
            }

            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract service name from class declaration
            Matcher serviceMatcher = GRPC_SERVICE_PATTERN.matcher(content);
            String serviceName = null;

            if (serviceMatcher.find()) {
                serviceName = serviceMatcher.group(2); // Service name before "Grpc"
            }

            if (serviceName == null) {
                return endpoints;
            }

            // Extract gRPC methods
            Matcher methodMatcher = GRPC_METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                String path = "/" + serviceName + "/" + capitalize(methodName);

                endpoints.add(new ApiEndpoint(
                    fullyQualifiedName,
                    ApiType.GRPC,
                    path,
                    "POST",
                    fullyQualifiedName + "." + methodName,
                    "Request",
                    "Response",
                    null
                ));
            }

            log.debug("Fallback parsing found {} gRPC endpoints in {}", endpoints.size(), file.getFileName());
            return endpoints;
        };
    }
}
