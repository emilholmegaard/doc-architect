package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for Protocol Buffer (.proto) schema definitions.
 *
 * <p>This scanner parses Protocol Buffer schema files using regex patterns to extract service
 * definitions and message types. Protobuf is a language-neutral, platform-neutral extensible
 * mechanism for serializing structured data, commonly used with gRPC for API definitions.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate .proto files using pattern matching</li>
 *   <li>Parse syntax declarations (proto2/proto3)</li>
 *   <li>Extract package declarations for namespacing</li>
 *   <li>Extract gRPC service definitions → ApiEndpoint records</li>
 *   <li>Extract message types → DataEntity records with fields</li>
 *   <li>Handle nested message definitions and field types</li>
 * </ol>
 *
 * <p><b>Supported Protobuf Constructs:</b>
 * <ul>
 *   <li>Syntax declarations: {@code syntax = "proto3";}</li>
 *   <li>Package declarations: {@code package example.v1;}</li>
 *   <li>Service definitions: {@code service UserService { ... }}</li>
 *   <li>RPC methods: {@code rpc GetUser (GetUserRequest) returns (User);}</li>
 *   <li>Message types: {@code message User { ... }}</li>
 *   <li>Field definitions with types and field numbers</li>
 *   <li>Import statements (tracked but not deeply parsed)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new ProtobufSchemaScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<ApiEndpoint> services = result.apiEndpoints();
 * List<DataEntity> messages = result.dataEntities();
 * }</pre>
 *
 * <p><b>Example Protobuf Schema:</b>
 * <pre>{@code
 * syntax = "proto3";
 *
 * package example;
 *
 * service UserService {
 *   rpc GetUser (GetUserRequest) returns (User);
 *   rpc ListUsers (ListUsersRequest) returns (ListUsersResponse);
 * }
 *
 * message User {
 *   string id = 1;
 *   string name = 2;
 *   string email = 3;
 * }
 *
 * message GetUserRequest {
 *   string id = 1;
 * }
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see ApiEndpoint
 * @see DataEntity
 * @since 1.0.0
 */
public class ProtobufSchemaScanner extends AbstractRegexScanner {

    // Scanner Metadata
    private static final String SCANNER_ID = "protobuf-schema";
    private static final String SCANNER_DISPLAY_NAME = "Protobuf Schema Scanner";
    private static final int PRIORITY = 60;

    // File Patterns
    private static final String PROTO_FILE_PATTERN = "**/*.proto";

    // Regex Patterns
    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
        "^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;",
        Pattern.MULTILINE
    );

    private static final Pattern SERVICE_PATTERN = Pattern.compile(
        "service\\s+([A-Za-z0-9_]+)\\s*\\{([^}]+)\\}",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern RPC_PATTERN = Pattern.compile(
        "rpc\\s+([A-Za-z0-9_]+)\\s*\\(\\s*([A-Za-z0-9_.]+)\\s*\\)\\s*returns\\s*\\(\\s*([A-Za-z0-9_.]+)\\s*\\)",
        Pattern.MULTILINE
    );

    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
        "message\\s+([A-Za-z0-9_]+)\\s*\\{([^}]*(?:\\{[^}]*\\}[^}]*)*)\\}",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "^\\s*(?:repeated\\s+|optional\\s+|required\\s+)?([a-zA-Z0-9_.]+)\\s+([a-zA-Z0-9_]+)\\s*=\\s*([0-9]+)\\s*;",
        Pattern.MULTILINE
    );

    // Constants
    private static final String GRPC_METHOD = "GRPC";
    private static final String PROTOBUF_MESSAGE_TYPE = "protobuf-message";

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
        // Protobuf is language-agnostic - can be used with any language
        return Set.of(
            Technologies.JAVA,
            Technologies.PYTHON,
            Technologies.GO,
            Technologies.GOLANG,
            Technologies.JAVASCRIPT,
            Technologies.TYPESCRIPT,
            Technologies.CSHARP,
            Technologies.DOTNET,
            Technologies.KOTLIN,
            Technologies.RUBY,
            Technologies.SCALA
        );
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PROTO_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PROTO_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Protobuf schemas in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<DataEntity> dataEntities = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Find all .proto files
        List<Path> protoFiles = new ArrayList<>();
        context.findFiles(PROTO_FILE_PATTERN).forEach(protoFiles::add);

        if (protoFiles.isEmpty()) {
            log.warn("No Protobuf schema files found in project");
            return emptyResult();
        }

        for (Path protoFile : protoFiles) {
            try {
                parseProtoFile(protoFile, apiEndpoints, dataEntities);
            } catch (Exception e) {
                log.error("Failed to parse Protobuf schema: {}", protoFile, e);
                warnings.add("Failed to parse Protobuf schema: " + protoFile.getFileName() + " - " + e.getMessage());
            }
        }

        log.info("Found {} Protobuf messages and {} RPC methods across {} schema files",
            dataEntities.size(), apiEndpoints.size(), protoFiles.size());

        return buildSuccessResult(
            List.of(), // No components
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            dataEntities,
            List.of(), // No relationships
            warnings
        );
    }

    /**
     * Parses a single .proto file and extracts services and messages.
     *
     * @param protoFile path to .proto file
     * @param apiEndpoints list to add discovered API endpoints
     * @param dataEntities list to add discovered data entities
     * @throws IOException if file cannot be read
     */
    private void parseProtoFile(Path protoFile, List<ApiEndpoint> apiEndpoints,
                                List<DataEntity> dataEntities) throws IOException {
        String content = readFileContent(protoFile);
        String fileName = protoFile.getFileName().toString().replace(".proto", "");

        // Extract package name for namespacing
        String packageName = extractPackageName(content);
        String componentId = packageName != null ? packageName : fileName;

        // Extract and process all services
        extractServices(content, componentId, apiEndpoints);

        // Extract and process all messages
        extractMessages(content, componentId, packageName, dataEntities);
    }

    /**
     * Extracts the package name from a .proto file.
     *
     * @param content file content
     * @return package name or null if not found
     */
    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts gRPC service definitions and their RPC methods as API endpoints.
     *
     * @param content file content
     * @param componentId component ID
     * @param apiEndpoints list to add discovered endpoints
     */
    private void extractServices(String content, String componentId, List<ApiEndpoint> apiEndpoints) {
        Matcher serviceMatcher = SERVICE_PATTERN.matcher(content);

        while (serviceMatcher.find()) {
            String serviceName = serviceMatcher.group(1);
            String serviceBody = serviceMatcher.group(2);

            // Extract all RPC methods from this service
            Matcher rpcMatcher = RPC_PATTERN.matcher(serviceBody);
            while (rpcMatcher.find()) {
                String methodName = rpcMatcher.group(1);
                String requestType = rpcMatcher.group(2);
                String responseType = rpcMatcher.group(3);

                ApiEndpoint endpoint = new ApiEndpoint(
                    componentId,
                    ApiType.GRPC,
                    serviceName + "." + methodName,
                    GRPC_METHOD,
                    "gRPC service: " + serviceName + "." + methodName,
                    requestType,
                    responseType,
                    null // authentication not specified in proto files
                );

                apiEndpoints.add(endpoint);
                log.debug("Found gRPC method: {}.{} ({} -> {})",
                    serviceName, methodName, requestType, responseType);
            }
        }
    }

    /**
     * Extracts message type definitions as data entities.
     *
     * @param content file content
     * @param componentId component ID
     * @param packageName package name for namespacing
     * @param dataEntities list to add discovered entities
     */
    private void extractMessages(String content, String componentId, String packageName,
                                 List<DataEntity> dataEntities) {
        Matcher messageMatcher = MESSAGE_PATTERN.matcher(content);

        while (messageMatcher.find()) {
            String messageName = messageMatcher.group(1);
            String messageBody = messageMatcher.group(2);

            // Build full message name with package
            String fullMessageName = packageName != null ? packageName + "." + messageName : messageName;

            // Extract fields from message body
            List<DataEntity.Field> fields = extractFields(messageBody);

            DataEntity entity = new DataEntity(
                componentId,
                fullMessageName,
                PROTOBUF_MESSAGE_TYPE,
                fields,
                null, // Protobuf doesn't have explicit primary keys
                "Protobuf message: " + fullMessageName
            );

            dataEntities.add(entity);
            log.debug("Found Protobuf message: {} with {} fields", fullMessageName, fields.size());
        }
    }

    /**
     * Extracts field definitions from a message body.
     *
     * @param messageBody message body content
     * @return list of fields
     */
    private List<DataEntity.Field> extractFields(String messageBody) {
        List<DataEntity.Field> fields = new ArrayList<>();
        Matcher fieldMatcher = FIELD_PATTERN.matcher(messageBody);

        while (fieldMatcher.find()) {
            String fieldType = fieldMatcher.group(1);
            String fieldName = fieldMatcher.group(2);
            String fieldNumber = fieldMatcher.group(3);

            // Determine nullability (proto3 fields are implicitly optional)
            boolean nullable = !messageBody.contains("required " + fieldType + " " + fieldName);

            DataEntity.Field field = new DataEntity.Field(
                fieldName,
                fieldType,
                nullable,
                "Field number: " + fieldNumber
            );

            fields.add(field);
        }

        return fields;
    }
}
