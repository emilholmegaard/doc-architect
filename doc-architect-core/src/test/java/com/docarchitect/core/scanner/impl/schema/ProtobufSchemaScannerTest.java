package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link ProtobufSchemaScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse Protocol Buffer schema files (.proto)</li>
 *   <li>Extract package declarations</li>
 *   <li>Extract gRPC service definitions as API endpoints</li>
 *   <li>Extract message types as data entities</li>
 *   <li>Handle both proto2 and proto3 syntax</li>
 *   <li>Parse nested messages and field types</li>
 * </ul>
 *
 * @see ProtobufSchemaScanner
 * @since 1.0.0
 */
class ProtobufSchemaScannerTest extends ScannerTestBase {

    private ProtobufSchemaScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new ProtobufSchemaScanner();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("protobuf-schema");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Protobuf Schema Scanner");
    }

    @Test
    void getPriority_returnsCorrectPriority() {
        assertThat(scanner.getPriority()).isEqualTo(60);
    }

    @Test
    void getSupportedLanguages_includesMultipleLanguages() {
        assertThat(scanner.getSupportedLanguages())
            .contains("java", "python", "go", "golang", "javascript", "typescript", "csharp");
    }

    @Test
    void getSupportedFilePatterns_includesProtoPattern() {
        assertThat(scanner.getSupportedFilePatterns()).contains("**/*.proto");
    }

    @Test
    void appliesTo_withProtoFiles_returnsTrue() throws IOException {
        // Given: Project with .proto files
        createFile("schema/user.proto", "syntax = \"proto3\";");

        // When/Then
        assertThat(scanner.appliesTo(context)).isTrue();
    }

    @Test
    void appliesTo_withoutProtoFiles_returnsFalse() {
        // Given: Project without .proto files (empty temp directory)
        // When/Then
        assertThat(scanner.appliesTo(context)).isFalse();
    }

    @Test
    void scan_withSimpleServiceAndMessage_extractsServicesAndMessages() throws IOException {
        // Given: A simple protobuf schema with service and message
        createFile("proto/user.proto", """
            syntax = "proto3";

            package example;

            service UserService {
              rpc GetUser (GetUserRequest) returns (User);
              rpc ListUsers (ListUsersRequest) returns (ListUsersResponse);
            }

            message User {
              string id = 1;
              string name = 2;
              string email = 3;
            }

            message GetUserRequest {
              string id = 1;
            }

            message ListUsersRequest {
            }

            message ListUsersResponse {
              repeated User users = 1;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract service and messages
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.dataEntities()).hasSize(4);

        // Verify gRPC service methods
        ApiEndpoint getUserRpc = result.apiEndpoints().stream()
            .filter(e -> "UserService.GetUser".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(getUserRpc.type()).isEqualTo(ApiType.GRPC);
        assertThat(getUserRpc.method()).isEqualTo("GRPC");
        assertThat(getUserRpc.requestSchema()).isEqualTo("GetUserRequest");
        assertThat(getUserRpc.responseSchema()).isEqualTo("User");

        ApiEndpoint listUsersRpc = result.apiEndpoints().stream()
            .filter(e -> "UserService.ListUsers".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(listUsersRpc.type()).isEqualTo(ApiType.GRPC);
        assertThat(listUsersRpc.requestSchema()).isEqualTo("ListUsersRequest");
        assertThat(listUsersRpc.responseSchema()).isEqualTo("ListUsersResponse");

        // Verify message types
        DataEntity userMessage = result.dataEntities().stream()
            .filter(e -> "example.User".equals(e.name()))
            .findFirst()
            .orElseThrow();
        assertThat(userMessage.type()).isEqualTo("protobuf-message");
        assertThat(userMessage.fields()).hasSize(3);

        DataEntity.Field idField = userMessage.fields().stream()
            .filter(f -> "id".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(idField.dataType()).isEqualTo("string");
        assertThat(idField.description()).isEqualTo("Field number: 1");
    }

    @Test
    void scan_withoutPackage_usesFileNameAsComponentId() throws IOException {
        // Given: Protobuf schema without package declaration
        createFile("proto/simple.proto", """
            syntax = "proto3";

            message SimpleMessage {
              string value = 1;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use filename as component and message name without package
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity message = result.dataEntities().get(0);
        assertThat(message.componentId()).isEqualTo("simple");
        assertThat(message.name()).isEqualTo("SimpleMessage");
    }

    @Test
    void scan_withProto2Syntax_parsesCorrectly() throws IOException {
        // Given: Protobuf schema with proto2 syntax
        createFile("proto/legacy.proto", """
            syntax = "proto2";

            package legacy;

            message LegacyMessage {
              required string id = 1;
              optional string name = 2;
              repeated string tags = 3;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse proto2 syntax correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity message = result.dataEntities().get(0);
        assertThat(message.name()).isEqualTo("legacy.LegacyMessage");
        assertThat(message.fields()).hasSize(3);

        // Verify field types
        assertThat(message.fields()).extracting(DataEntity.Field::name)
            .containsExactly("id", "name", "tags");
    }

    @Test
    void scan_withMultipleServices_extractsAllServices() throws IOException {
        // Given: Protobuf schema with multiple services
        createFile("proto/api.proto", """
            syntax = "proto3";

            package api.v1;

            service UserService {
              rpc GetUser (GetUserRequest) returns (UserResponse);
            }

            service ProductService {
              rpc GetProduct (GetProductRequest) returns (ProductResponse);
              rpc ListProducts (ListProductsRequest) returns (ListProductsResponse);
            }

            message GetUserRequest { string id = 1; }
            message UserResponse { string name = 1; }
            message GetProductRequest { string id = 1; }
            message ProductResponse { string name = 1; }
            message ListProductsRequest { }
            message ListProductsResponse { repeated ProductResponse products = 1; }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all services and methods
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "UserService.GetUser",
                "ProductService.GetProduct",
                "ProductService.ListProducts"
            );
    }

    @Test
    void scan_withComplexFieldTypes_parsesCorrectly() throws IOException {
        // Given: Protobuf schema with various field types
        createFile("proto/types.proto", """
            syntax = "proto3";

            package types;

            message ComplexMessage {
              string text_field = 1;
              int32 int_field = 2;
              int64 long_field = 3;
              bool bool_field = 4;
              double double_field = 5;
              repeated string tags = 6;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse all field types correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity message = result.dataEntities().get(0);
        assertThat(message.fields()).hasSize(6);
        assertThat(message.fields()).extracting(DataEntity.Field::dataType)
            .containsExactly("string", "int32", "int64", "bool", "double", "string");
    }

    @Test
    void scan_withMultipleFiles_scansAll() throws IOException {
        // Given: Multiple protobuf schema files
        createFile("proto/user.proto", """
            syntax = "proto3";
            package user;
            message User { string id = 1; }
            """);

        createFile("proto/product.proto", """
            syntax = "proto3";
            package product;
            message Product { string id = 1; }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should scan all files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);
        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder("user.User", "product.Product");
    }

    @Test
    void scan_withNoProtoFiles_returnsEmptyResult() {
        // Given: No protobuf files in project
        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withInvalidProtoFile_handlesGracefully() throws IOException {
        // Given: Malformed protobuf file
        createFile("proto/broken.proto", "this is not valid protobuf syntax {{{");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle error gracefully (returns empty result or warning)
        assertThat(result.success()).isTrue();
        // Scanner may return empty results or warnings depending on error
        assertThat(result.apiEndpoints()).isEmpty();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withNestedPackage_preservesFullPackageName() throws IOException {
        // Given: Protobuf with nested package name
        createFile("proto/api.proto", """
            syntax = "proto3";

            package com.example.api.v1;

            message Request {
              string id = 1;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should preserve full package name
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.dataEntities().get(0).name()).isEqualTo("com.example.api.v1.Request");
        assertThat(result.dataEntities().get(0).componentId()).isEqualTo("com.example.api.v1");
    }

    @Test
    void scan_withRealWorldExample_parsesLinkerdProto() throws IOException {
        // Given: Real-world protobuf from Linkerd2 project
        createFile("proto/common/net.proto", """
            syntax = "proto3";

            package linkerd2.common.net;

            option go_package = "github.com/linkerd/linkerd2/controller/gen/common/net";

            message IPAddress {
              oneof ip {
                fixed32 ipv4 = 1;
                IPv6 ipv6 = 2;
              }
            }

            message IPv6 {
              fixed64 first = 1;
              fixed64 last = 2;
            }

            message TcpAddress {
              IPAddress ip = 1;
              uint32 port = 2;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse Linkerd protobuf correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(3);

        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder(
                "linkerd2.common.net.IPAddress",
                "linkerd2.common.net.IPv6",
                "linkerd2.common.net.TcpAddress"
            );

        // Verify TcpAddress has correct fields
        DataEntity tcpAddress = result.dataEntities().stream()
            .filter(e -> "linkerd2.common.net.TcpAddress".equals(e.name()))
            .findFirst()
            .orElseThrow();
        assertThat(tcpAddress.fields()).hasSize(2);
        assertThat(tcpAddress.fields()).extracting(DataEntity.Field::name)
            .containsExactly("ip", "port");
    }
}
