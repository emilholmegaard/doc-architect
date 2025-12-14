package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for Apache Avro schema definitions in .avsc and .avro files.
 *
 * <p>This scanner parses Avro schema JSON files using Jackson to extract record types
 * and their field definitions. Avro schemas are commonly used in event-driven architectures
 * with messaging systems like Apache Kafka.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate .avsc and .avro files using pattern matching</li>
 *   <li>Parse JSON schema files using Jackson ObjectMapper</li>
 *   <li>Extract record types → DataEntity records with fields</li>
 *   <li>Create MessageFlow records for schemas used in messaging</li>
 *   <li>Handle nested field definitions and complex types</li>
 * </ol>
 *
 * <p><b>Supported Avro Constructs:</b>
 * <ul>
 *   <li>Record types: {@code {"type": "record", "name": "User", "fields": [...]}}</li>
 *   <li>Primitive types: string, int, long, float, double, boolean, null, bytes</li>
 *   <li>Complex types: array, map, union, enum, fixed</li>
 *   <li>Nested records and references</li>
 *   <li>Namespace and documentation fields</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new AvroSchemaScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<DataEntity> records = result.dataEntities();
 * List<MessageFlow> messages = result.messageFlows();
 * }</pre>
 *
 * <p><b>Example Avro Schema:</b>
 * <pre>{@code
 * {
 *   "type": "record",
 *   "name": "User",
 *   "namespace": "com.example.events",
 *   "fields": [
 *     {"name": "id", "type": "string"},
 *     {"name": "name", "type": "string"},
 *     {"name": "email", "type": ["null", "string"], "default": null}
 *   ]
 * }
 * }</pre>
 *
 * @see Scanner
 * @see DataEntity
 * @see MessageFlow
 * @since 1.0.0
 */
public class AvroSchemaScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(AvroSchemaScanner.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return "avro-schema";
    }

    @Override
    public String getDisplayName() {
        return "Avro Schema Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java", "python", "go", "javascript", "csharp");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.avsc", "**/*.avro");
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return context.findFiles("**/*.avsc").findAny().isPresent() ||
               context.findFiles("**/*.avro").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Avro schemas in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<MessageFlow> messageFlows = new ArrayList<>();

        // Find all Avro schema files
        List<Path> schemaFiles = new ArrayList<>();
        context.findFiles("**/*.avsc").forEach(schemaFiles::add);
        context.findFiles("**/*.avro").forEach(schemaFiles::add);

        if (schemaFiles.isEmpty()) {
            log.warn("No Avro schema files found in project");
            return ScanResult.empty(getId());
        }

        for (Path schemaFile : schemaFiles) {
            try {
                parseSchemaFile(schemaFile, dataEntities, messageFlows);
            } catch (Exception e) {
                log.error("Failed to parse Avro schema: {}", schemaFile, e);
                return ScanResult.failed(getId(), List.of("Failed to parse Avro schema: " + schemaFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Avro records and {} message flows across {} schema files",
            dataEntities.size(), messageFlows.size(), schemaFiles.size());

        return new ScanResult(
            getId(),
            true, // success
            List.of(), // No components
            List.of(), // No dependencies
            List.of(), // No API endpoints
            messageFlows,
            dataEntities,
            List.of(), // No relationships
            List.of(), // No warnings
            List.of()  // No errors
        );
    }

    /**
     * Parses a single Avro schema file and extracts record types and message flows.
     *
     * @param schemaFile path to Avro schema file
     * @param dataEntities list to add discovered data entities
     * @param messageFlows list to add discovered message flows
     * @throws IOException if file cannot be read or parsed
     */
    private void parseSchemaFile(Path schemaFile, List<DataEntity> dataEntities,
                                 List<MessageFlow> messageFlows) throws IOException {
        String content = Files.readString(schemaFile);
        JsonNode schema = objectMapper.readTree(content);

        String componentId = schemaFile.getFileName().toString()
            .replace(".avsc", "")
            .replace(".avro", "");

        // Parse the schema (can be a single record or array of records)
        if (schema.isArray()) {
            for (JsonNode recordNode : schema) {
                parseAvroRecord(recordNode, componentId, dataEntities, messageFlows);
            }
        } else {
            parseAvroRecord(schema, componentId, dataEntities, messageFlows);
        }
    }

    /**
     * Parses a single Avro record definition.
     *
     * @param recordNode JSON node containing the record definition
     * @param componentId component ID
     * @param dataEntities list to add discovered entities
     * @param messageFlows list to add discovered message flows
     */
    private void parseAvroRecord(JsonNode recordNode, String componentId,
                                 List<DataEntity> dataEntities, List<MessageFlow> messageFlows) {
        if (!recordNode.has("type")) {
            return;
        }

        String type = recordNode.get("type").asText();
        if (!"record".equals(type)) {
            return; // Only process record types
        }

        String name = recordNode.has("name") ? recordNode.get("name").asText() : "UnknownRecord";
        String namespace = recordNode.has("namespace") ? recordNode.get("namespace").asText() : null;
        String fullName = namespace != null ? namespace + "." + name : name;
        String description = recordNode.has("doc") ? recordNode.get("doc").asText() : "Avro record: " + name;

        List<DataEntity.Field> fields = new ArrayList<>();

        // Extract fields
        if (recordNode.has("fields")) {
            JsonNode fieldsNode = recordNode.get("fields");
            for (JsonNode fieldNode : fieldsNode) {
                DataEntity.Field field = parseField(fieldNode);
                if (field != null) {
                    fields.add(field);
                }
            }
        }

        // Create data entity for this record
        DataEntity entity = new DataEntity(
            componentId,
            fullName,
            "avro-record",
            fields,
            null, // Avro doesn't have explicit primary keys
            description
        );

        dataEntities.add(entity);
        log.debug("Found Avro record: {} with {} fields", fullName, fields.size());

        // Create message flow if this appears to be an event/message schema
        if (isEventSchema(name, namespace)) {
            MessageFlow messageFlow = new MessageFlow(
                componentId, // publisher
                null, // subscriber unknown
                name, // topic derived from schema name
                fullName, // message type
                fullName, // schema
                "kafka" // Avro commonly used with Kafka
            );
            messageFlows.add(messageFlow);
            log.debug("Created message flow for Avro schema: {}", fullName);
        }
    }

    /**
     * Parses a field definition from an Avro schema.
     *
     * @param fieldNode JSON node containing the field definition
     * @return parsed field or null if invalid
     */
    private DataEntity.Field parseField(JsonNode fieldNode) {
        if (!fieldNode.has("name") || !fieldNode.has("type")) {
            return null;
        }

        String fieldName = fieldNode.get("name").asText();
        JsonNode typeNode = fieldNode.get("type");
        String fieldType = parseFieldType(typeNode);
        boolean nullable = isNullableType(typeNode);
        String description = fieldNode.has("doc") ? fieldNode.get("doc").asText() : null;

        return new DataEntity.Field(
            fieldName,
            fieldType,
            nullable,
            description
        );
    }

    /**
     * Parses an Avro type definition into a string representation.
     *
     * @param typeNode JSON node containing the type definition
     * @return string representation of the type
     */
    private String parseFieldType(JsonNode typeNode) {
        if (typeNode.isTextual()) {
            return typeNode.asText();
        }

        if (typeNode.isArray()) {
            // Union type - extract non-null type
            for (JsonNode unionType : typeNode) {
                String typeName = unionType.isTextual() ? unionType.asText() : "complex";
                if (!"null".equals(typeName)) {
                    return typeName;
                }
            }
            return "null";
        }

        if (typeNode.isObject()) {
            if (typeNode.has("type")) {
                String type = typeNode.get("type").asText();

                // Handle complex types
                return switch (type) {
                    case "array" -> typeNode.has("items") ?
                        "array<" + parseFieldType(typeNode.get("items")) + ">" : "array";
                    case "map" -> typeNode.has("values") ?
                        "map<" + parseFieldType(typeNode.get("values")) + ">" : "map";
                    case "record" -> typeNode.has("name") ?
                        typeNode.get("name").asText() : "record";
                    case "enum" -> typeNode.has("name") ?
                        typeNode.get("name").asText() : "enum";
                    case "fixed" -> typeNode.has("name") ?
                        typeNode.get("name").asText() : "fixed";
                    default -> type;
                };
            }
        }

        return "unknown";
    }

    /**
     * Determines if an Avro type is nullable.
     *
     * @param typeNode JSON node containing the type definition
     * @return true if the type allows null values
     */
    private boolean isNullableType(JsonNode typeNode) {
        if (typeNode.isArray()) {
            // Union type - check if it contains "null"
            for (JsonNode unionType : typeNode) {
                if (unionType.isTextual() && "null".equals(unionType.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Determines if an Avro schema represents an event/message type based on naming conventions.
     *
     * @param name schema name
     * @param namespace schema namespace
     * @return true if this appears to be an event schema
     */
    private boolean isEventSchema(String name, String namespace) {
        String lowerName = name.toLowerCase();
        String lowerNamespace = namespace != null ? namespace.toLowerCase() : "";

        return lowerName.contains("event") ||
               lowerName.contains("message") ||
               lowerName.contains("command") ||
               lowerNamespace.contains("event") ||
               lowerNamespace.contains("message") ||
               lowerNamespace.contains("kafka");
    }
}
