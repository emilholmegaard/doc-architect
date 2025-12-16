package com.docarchitect.core.scanner.impl.schema;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJacksonScanner;
import com.docarchitect.core.util.Technologies;
import com.fasterxml.jackson.databind.JsonNode;

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
 * @see com.docarchitect.core.scanner.Scanner
 * @see DataEntity
 * @see MessageFlow
 * @since 1.0.0
 */
public class AvroSchemaScanner extends AbstractJacksonScanner {

    // Scanner Metadata
    private static final String SCANNER_ID = "avro-schema";
    private static final String SCANNER_DISPLAY_NAME = "Avro Schema Scanner";

    // File Extensions
    private static final String AVSC_EXTENSION = ".avsc";
    private static final String AVRO_EXTENSION = ".avro";
    private static final String PATTERN_AVSC_FILES = "**/*.avsc";
    private static final String PATTERN_AVRO_FILES = "**/*.avro";

    // Avro Schema Field Names
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_NAMESPACE = "namespace";
    private static final String FIELD_DOC = "doc";
    private static final String FIELD_FIELDS = "fields";
    private static final String FIELD_ITEMS = "items";
    private static final String FIELD_VALUES = "values";

    // Avro Type Names
    private static final String TYPE_RECORD = "record";
    private static final String TYPE_ARRAY = "array";
    private static final String TYPE_MAP = "map";
    private static final String TYPE_ENUM = "enum";
    private static final String TYPE_FIXED = "fixed";
    private static final String TYPE_NULL = "null";
    private static final String TYPE_UNKNOWN = "unknown";
    private static final String TYPE_COMPLEX = "complex";

    // Default Values
    private static final String DEFAULT_RECORD_NAME = "UnknownRecord";
    private static final String DEFAULT_DESCRIPTION_PREFIX = "Avro record: ";
    private static final String COMPONENT_TYPE_AVRO_RECORD = "avro-record";
    private static final String MESSAGE_PROTOCOL_KAFKA = "kafka";

    // Event/Message Detection Keywords
    private static final String KEYWORD_EVENT = "event";
    private static final String KEYWORD_MESSAGE = "message";
    private static final String KEYWORD_COMMAND = "command";
    private static final String KEYWORD_KAFKA = "kafka";

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
        return Set.of(Technologies.JAVA, Technologies.PYTHON, Technologies.GO, Technologies.JAVASCRIPT, Technologies.CSHARP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_AVSC_FILES, PATTERN_AVRO_FILES);
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PATTERN_AVSC_FILES, PATTERN_AVRO_FILES);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Avro schemas in: {}", context.rootPath());

        List<DataEntity> dataEntities = new ArrayList<>();
        List<MessageFlow> messageFlows = new ArrayList<>();

        // Find all Avro schema files
        List<Path> schemaFiles = new ArrayList<>();
        context.findFiles(PATTERN_AVSC_FILES).forEach(schemaFiles::add);
        context.findFiles(PATTERN_AVRO_FILES).forEach(schemaFiles::add);

        if (schemaFiles.isEmpty()) {
            log.warn("No Avro schema files found in project");
            return emptyResult();
        }

        for (Path schemaFile : schemaFiles) {
            try {
                parseSchemaFile(schemaFile, dataEntities, messageFlows);
            } catch (Exception e) {
                log.error("Failed to parse Avro schema: {}", schemaFile, e);
                return failedResult(List.of("Failed to parse Avro schema: " + schemaFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Avro records and {} message flows across {} schema files",
            dataEntities.size(), messageFlows.size(), schemaFiles.size());

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            messageFlows,
            dataEntities,
            List.of(),
            List.of()
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
        String content = readFileContent(schemaFile);
        JsonNode schema = objectMapper.readTree(content);

        String componentId = schemaFile.getFileName().toString()
            .replace(AVSC_EXTENSION, "")
            .replace(AVRO_EXTENSION, "");

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
        if (!recordNode.has(FIELD_TYPE)) {
            return;
        }

        String type = recordNode.get(FIELD_TYPE).asText();
        if (!TYPE_RECORD.equals(type)) {
            return; // Only process record types
        }

        String name = recordNode.has(FIELD_NAME) ? recordNode.get(FIELD_NAME).asText() : DEFAULT_RECORD_NAME;
        String namespace = recordNode.has(FIELD_NAMESPACE) ? recordNode.get(FIELD_NAMESPACE).asText() : null;
        String fullName = namespace != null ? namespace + "." + name : name;
        String description = recordNode.has(FIELD_DOC) ? recordNode.get(FIELD_DOC).asText() : DEFAULT_DESCRIPTION_PREFIX + name;

        List<DataEntity.Field> fields = new ArrayList<>();

        // Extract fields
        if (recordNode.has(FIELD_FIELDS)) {
            JsonNode fieldsNode = recordNode.get(FIELD_FIELDS);
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
            COMPONENT_TYPE_AVRO_RECORD,
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
                MESSAGE_PROTOCOL_KAFKA // Avro commonly used with Kafka
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
        if (!fieldNode.has(FIELD_NAME) || !fieldNode.has(FIELD_TYPE)) {
            return null;
        }

        String fieldName = fieldNode.get(FIELD_NAME).asText();
        JsonNode typeNode = fieldNode.get(FIELD_TYPE);
        String fieldType = parseFieldType(typeNode);
        boolean nullable = isNullableType(typeNode);
        String description = fieldNode.has(FIELD_DOC) ? fieldNode.get(FIELD_DOC).asText() : null;

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
                String typeName = unionType.isTextual() ? unionType.asText() : TYPE_COMPLEX;
                if (!TYPE_NULL.equals(typeName)) {
                    return typeName;
                }
            }
            return TYPE_NULL;
        }

        if (typeNode.isObject()) {
            if (typeNode.has(FIELD_TYPE)) {
                String type = typeNode.get(FIELD_TYPE).asText();

                // Handle complex types
                return switch (type) {
                    case TYPE_ARRAY -> typeNode.has(FIELD_ITEMS) ?
                        TYPE_ARRAY + "<" + parseFieldType(typeNode.get(FIELD_ITEMS)) + ">" : TYPE_ARRAY;
                    case TYPE_MAP -> typeNode.has(FIELD_VALUES) ?
                        TYPE_MAP + "<" + parseFieldType(typeNode.get(FIELD_VALUES)) + ">" : TYPE_MAP;
                    case TYPE_RECORD -> typeNode.has(FIELD_NAME) ?
                        typeNode.get(FIELD_NAME).asText() : TYPE_RECORD;
                    case TYPE_ENUM -> typeNode.has(FIELD_NAME) ?
                        typeNode.get(FIELD_NAME).asText() : TYPE_ENUM;
                    case TYPE_FIXED -> typeNode.has(FIELD_NAME) ?
                        typeNode.get(FIELD_NAME).asText() : TYPE_FIXED;
                    default -> type;
                };
            }
        }

        return TYPE_UNKNOWN;
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
                if (unionType.isTextual() && TYPE_NULL.equals(unionType.asText())) {
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

        return lowerName.contains(KEYWORD_EVENT) ||
               lowerName.contains(KEYWORD_MESSAGE) ||
               lowerName.contains(KEYWORD_COMMAND) ||
               lowerNamespace.contains(KEYWORD_EVENT) ||
               lowerNamespace.contains(KEYWORD_MESSAGE) ||
               lowerNamespace.contains(KEYWORD_KAFKA);
    }
}
