package com.docarchitect.core.scanner.impl.go;

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
 * Scanner for gRPC service implementations in Go source files.
 *
 * <p>Detects gRPC service implementations from generated protobuf code
 * and custom service implementations.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code type server struct} - gRPC server struct</li>
 *   <li>{@code func (s *server) MethodName(...) (..., error)} - gRPC method</li>
 *   <li>{@code pb.RegisterXxxServer()} - Service registration</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * type server struct {
 *     pb.UnimplementedUserServiceServer
 * }
 *
 * func (s *server) GetUser(ctx context.Context, req *pb.GetUserRequest) (*pb.GetUserResponse, error) {
 *     // Implementation
 * }
 * }</pre>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class GrpcServiceScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "grpc-service-go";
    private static final String SCANNER_DISPLAY_NAME = "gRPC Service Implementation Scanner (Go)";
    private static final String FILE_PATTERN = "**/*.go";
    private static final int SCANNER_PRIORITY = 55;

    // Regex patterns
    private static final Pattern REGISTER_PATTERN =
        Pattern.compile("pb\\.Register(\\w+)Server\\(");

    private static final Pattern METHOD_PATTERN =
        Pattern.compile("func\\s+\\(\\w+\\s+\\*\\w+\\)\\s+(\\w+)\\s*\\(.*?context\\.Context.*?\\*pb\\.(\\w+)\\)\\s*\\(\\*pb\\.(\\w+)");

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
        return Set.of(Technologies.GO, Technologies.GOLANG);
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
    public ScanResult scan(ScanContext context) {
        log.info("Scanning gRPC service implementations in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<Path> goFiles = context.findFiles(FILE_PATTERN).toList();

        if (goFiles.isEmpty()) {
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path goFile : goFiles) {
            try {
                if (!shouldScanFile(goFile)) {
                    skippedFiles++;
                    continue;
                }
                parseGrpcService(goFile, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse Go file: {} - {}", goFile, e.getMessage());
            }
        }

        log.info("Found {} gRPC service endpoints (parsed {}/{} files, skipped {} files)",
                 apiEndpoints.size(), parsedFiles, goFiles.size(), skippedFiles);

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

    private boolean shouldScanFile(Path file) {
        String fileName = file.getFileName().toString();

        // Skip generated pb.go files - we want the implementations, not the generated code
        if (fileName.endsWith(".pb.go") || fileName.endsWith("_grpc.pb.go")) {
            return false;
        }

        if (fileName.contains("server") || fileName.contains("service") || fileName.contains("grpc")) {
            return true;
        }

        try {
            String content = readFileContent(file);
            return (content.contains("google.golang.org/grpc") ||
                   content.contains("pb.Register") ||
                   content.contains("context.Context")) &&
                   content.contains("*pb.");
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    private void parseGrpcService(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(file);
        String packageName = extractPackageName(content);

        // Extract service name from RegisterXxxServer call
        Matcher registerMatcher = REGISTER_PATTERN.matcher(content);
        String serviceName = null;

        if (registerMatcher.find()) {
            serviceName = registerMatcher.group(1); // Service name
        }

        if (serviceName == null) {
            return;
        }

        // Extract gRPC methods
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            String requestType = methodMatcher.group(2);
            String responseType = methodMatcher.group(3);

            String path = "/" + serviceName + "/" + methodName;
            String handler = packageName + "." + methodName;

            apiEndpoints.add(new ApiEndpoint(
                packageName,
                ApiType.GRPC,
                path,
                "POST",
                handler,
                requestType,
                responseType,
                null
            ));

            log.debug("Found gRPC endpoint: {} in package: {}", path, packageName);
        }
    }

    private String extractPackageName(String content) {
        Pattern pattern = Pattern.compile("package\\s+(\\w+)");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "main";
    }
}
