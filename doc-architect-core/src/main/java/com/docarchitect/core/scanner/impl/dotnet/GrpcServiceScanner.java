package com.docarchitect.core.scanner.impl.dotnet;

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
 * Scanner for gRPC service implementations in C# source files.
 *
 * <p>Detects gRPC service implementations that inherit from generated base classes
 * from protobuf definitions.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code : UserService.UserServiceBase} - Standard gRPC service base</li>
 *   <li>{@code override Task<Response> Method(...)} - gRPC method implementations</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * public class UserServiceImpl : UserService.UserServiceBase
 * {
 *     public override Task<GetUserResponse> GetUser(GetUserRequest request, ServerCallContext context)
 *     {
 *         // Implementation
 *     }
 * }
 * }</pre>
 *
 * @see ApiEndpoint
 * @since 1.0.0
 */
public class GrpcServiceScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "grpc-service-dotnet";
    private static final String SCANNER_DISPLAY_NAME = "gRPC Service Implementation Scanner (.NET)";
    private static final String FILE_PATTERN_NESTED = "**/*.cs";
    private static final String FILE_PATTERN_ROOT = "*.cs";
    private static final int SCANNER_PRIORITY = 55;

    // Regex patterns
    private static final Pattern SERVICE_PATTERN =
        Pattern.compile("class\\s+(\\w+)\\s*:\\s*(\\w+)\\.(\\w+)Base");

    private static final Pattern METHOD_PATTERN =
        Pattern.compile("override\\s+Task<(\\w+)>\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s+");

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
        return Set.of(Technologies.CSHARP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning gRPC service implementations in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();

        List<Path> csFiles = new ArrayList<>();
        context.findFiles(FILE_PATTERN_ROOT).forEach(csFiles::add);
        context.findFiles(FILE_PATTERN_NESTED).forEach(csFiles::add);

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        int parsedFiles = 0;
        int skippedFiles = 0;

        for (Path csFile : csFiles) {
            try {
                if (!shouldScanFile(csFile)) {
                    skippedFiles++;
                    continue;
                }
                parseGrpcService(csFile, apiEndpoints);
                parsedFiles++;
            } catch (Exception e) {
                log.warn("Failed to parse C# file: {} - {}", csFile, e.getMessage());
            }
        }

        log.info("Found {} gRPC service endpoints (parsed {}/{} files, skipped {} files)",
                 apiEndpoints.size(), parsedFiles, csFiles.size(), skippedFiles);

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

        if (fileName.contains("Service") || fileName.contains("Grpc")) {
            return true;
        }

        try {
            String content = readFileContent(file);
            return content.contains("using Grpc.") ||
                   content.contains("ServerCallContext") ||
                   content.contains("ServiceBase") ||
                   content.contains(": ") && content.contains("Base");
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    private void parseGrpcService(Path file, List<ApiEndpoint> apiEndpoints) throws IOException {
        String content = readFileContent(file);
        String className = extractClassName(file, content);

        // Extract service name from class declaration
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(content);
        String serviceName = null;

        if (serviceMatcher.find()) {
            serviceName = serviceMatcher.group(2); // Service name before Base
        }

        if (serviceName == null) {
            return;
        }

        // Extract gRPC methods
        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            String responseType = methodMatcher.group(1);
            String methodName = methodMatcher.group(2);
            String requestType = methodMatcher.group(3);

            String path = "/" + serviceName + "/" + methodName;

            apiEndpoints.add(new ApiEndpoint(
                className,
                ApiType.GRPC,
                path,
                "POST",
                className + "." + methodName,
                requestType,
                responseType,
                null
            ));

            log.debug("Found gRPC endpoint: {} in service: {}", path, className);
        }
    }

    private String extractClassName(Path file, String content) {
        Pattern namespacePattern = Pattern.compile("namespace\\s+([\\w.]+)");
        Pattern classPattern = Pattern.compile("(?:public|internal)?\\s*class\\s+(\\w+)");

        Matcher namespaceMatcher = namespacePattern.matcher(content);
        Matcher classMatcher = classPattern.matcher(content);

        String namespace = namespaceMatcher.find() ? namespaceMatcher.group(1) : "";
        String className = classMatcher.find() ? classMatcher.group(1) : file.getFileName().toString().replace(".cs", "");

        return namespace.isEmpty() ? className : namespace + "." + className;
    }
}
