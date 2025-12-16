package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link AvroSchemaScanner}.
 */
class AvroSchemaScannerTest extends ScannerTestBase {

    private AvroSchemaScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new AvroSchemaScanner();
    }

    @Test
    void scan_withSimpleRecord_extractsEntity() throws IOException {
        // Given: Simple Avro schema with primitive fields
        createFile("schemas/user.avsc", """
{
  "type": "record",
  "name": "User",
  "namespace": "com.example",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "username", "type": "string"},
    {"name": "email", "type": ["null", "string"], "default": null}
  ]
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract User entity with 3 fields
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity user = result.dataEntities().get(0);
        assertThat(user.name()).isEqualTo("com.example.User");
        assertThat(user.type()).isEqualTo("avro-record");
        assertThat(user.fields()).hasSize(3);

        // Check nullable field
        DataEntity.Field emailField = user.fields().stream()
            .filter(f -> "email".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(emailField.nullable()).isTrue();
        assertThat(emailField.dataType()).isEqualTo("string");
    }

    @Test
    void scan_withEventSchema_createsMessageFlow() throws IOException {
        // Given: Avro schema with event naming
        createFile("schemas/UserCreatedEvent.avsc", """
{
  "type": "record",
  "name": "UserCreatedEvent",
  "namespace": "com.example.events",
  "fields": [
    {"name": "userId", "type": "string"},
    {"name": "timestamp", "type": "long"}
  ]
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create message flow for event
        assertThat(result.success()).isTrue();
        assertThat(result.messageFlows()).hasSize(1);

        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("UserCreatedEvent");
        assertThat(flow.messageType()).isEqualTo("com.example.events.UserCreatedEvent");
        assertThat(flow.broker()).isEqualTo("kafka");
    }

    @Test
    void scan_withComplexTypes_extractsCorrectly() throws IOException {
        // Given: Avro schema with array and map types
        createFile("schemas/product.avsc", """
{
  "type": "record",
  "name": "Product",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "tags", "type": {"type": "array", "items": "string"}},
    {"name": "attributes", "type": {"type": "map", "values": "string"}}
  ]
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle complex types
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity product = result.dataEntities().get(0);

        DataEntity.Field tagsField = product.fields().stream()
            .filter(f -> "tags".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(tagsField.dataType()).isEqualTo("array<string>");

        DataEntity.Field attrsField = product.fields().stream()
            .filter(f -> "attributes".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(attrsField.dataType()).isEqualTo("map<string>");
    }

    @Test
    void scan_withMultipleRecords_extractsAll() throws IOException {
        // Given: Multiple Avro schema files
        createFile("schemas/order.avsc", """
{
  "type": "record",
  "name": "Order",
  "fields": [
    {"name": "orderId", "type": "string"},
    {"name": "total", "type": "double"}
  ]
}
""");

        createFile("schemas/payment.avsc", """
{
  "type": "record",
  "name": "Payment",
  "fields": [
    {"name": "paymentId", "type": "string"},
    {"name": "amount", "type": "double"}
  ]
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both records
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);
        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder("Order", "Payment");
    }

    @Test
    void scan_withAvroExtension_parsesCorrectly() throws IOException {
        // Given: Schema file with .avro extension
        createFile("schemas/message.avro", """
{
  "type": "record",
  "name": "Message",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "content", "type": "string"}
  ]
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse .avro files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
    }

    @Test
    void scan_withNoAvroFiles_returnsEmpty() throws IOException {
        // Given: No Avro files in project
        createDirectory("schemas");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void appliesTo_withAvroFiles_returnsTrue() throws IOException {
        // Given: Project with Avro files
        createFile("schemas/test.avsc", "{}");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutAvroFiles_returnsFalse() throws IOException {
        // Given: Project without Avro files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
