package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

import graphql.language.*;
import graphql.parser.Parser;
import graphql.parser.ParserEnvironment;
import graphql.parser.ParserOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for GraphQL schema definitions in .graphql and .gql files.
 *
 * <p>This scanner parses GraphQL schema files using the graphql-java library to extract type definitions,
 * queries, and mutations. It converts GraphQL types into {@link DataEntity} records and
 * GraphQL operations (queries/mutations) into {@link ApiEndpoint} records.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate .graphql and .gql files using pattern matching</li>
 *   <li>Pre-filter files based on configurable size/line limits</li>
 *   <li>Parse schema using graphql-java Parser (robust AST-based parsing)</li>
 *   <li>Extract type definitions → DataEntity records</li>
 *   <li>Extract Query fields → ApiEndpoint records (type=GRAPHQL)</li>
 *   <li>Extract Mutation fields → ApiEndpoint records (type=GRAPHQL)</li>
 *   <li>Log performance metrics for large schema files</li>
 * </ol>
 *
 * <p><b>Supported GraphQL Constructs:</b>
 * <ul>
 *   <li>Type definitions: {@code type User { ... }}</li>
 *   <li>Input types: {@code input CreateUserInput { ... }}</li>
 *   <li>Query operations: {@code type Query { getUser(id: ID!): User }}</li>
 *   <li>Mutation operations: {@code type Mutation { createUser(input: CreateUserInput!): User }}</li>
 *   <li>Field definitions with types and nullability</li>
 * </ul>
 *
 * <p><b>Configuration:</b>
 * <p>Schema size limits can be configured in {@code docarchitect.yaml}:
 * <pre>{@code
 * scanners:
 *   config:
 *     graphql-schema:
 *       maxFileSizeBytes: 10000000  # 10MB (default: 5MB)
 *       maxSchemaLines: 100000      # 100k lines (default: 50k)
 * }</pre>
 *
 * <p>These limits prevent DoS-style parsing errors with extremely large schemas (e.g., Saleor's 35k+ line schema).
 * Files exceeding the limits will be skipped with a warning.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GraphQLScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of("maxSchemaLines", 50000),  // Optional configuration
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<DataEntity> types = result.dataEntities();
 * List<ApiEndpoint> operations = result.apiEndpoints();
 * }</pre>
 *
 * <p><b>Example GraphQL Schema:</b>
 * <pre>{@code
 * type User {
 *   id: ID!
 *   name: String!
 *   email: String
 * }
 *
 * type Query {
 *   getUser(id: ID!): User
 *   listUsers: [User!]!
 * }
 *
 * type Mutation {
 *   createUser(input: CreateUserInput!): User
 * }
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see ApiEndpoint
 * @see DataEntity
 * @since 1.0.0
 */
public class GraphQLScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "graphql-schema";
    private static final String DISPLAY_NAME = "GraphQL Schema Scanner";
    private static final String GRAPHQL_FILE_PATTERN = "**/*.graphql";
    private static final String GQL_FILE_PATTERN = "**/*.gql";
    private static final String GRAPHQL_EXTENSION = ".graphql";
    private static final String GQL_EXTENSION = ".gql";
    private static final int PRIORITY = 60;
    private static final String QUERY_TYPE = "Query";
    private static final String MUTATION_TYPE = "Mutation";
    private static final String SUBSCRIPTION_TYPE = "Subscription";
    private static final String QUERY_OPERATION = "query";
    private static final String MUTATION_OPERATION = "mutation";
    private static final String SUBSCRIPTION_OPERATION = "subscription";
    private static final String GRAPHQL_INPUT_TYPE = "graphql-input";
    private static final String GRAPHQL_TYPE = "graphql-type";
    private static final String ID_FIELD_NAME = "id";
    private static final String ID_TYPE_NAME = "ID";
    private static final String UNKNOWN_TYPE = "Unknown";
    private static final String COMMA_SEPARATOR = ", ";
    private static final String COLON_SEPARATOR = ": ";

    // File size limits to prevent DoS-style parsing errors (e.g., Saleor's 35k+ line schemas)
    // Default limits - can be overridden via configuration
    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5_000_000; // 5MB max per schema file
    private static final int DEFAULT_MAX_SCHEMA_LINES = 50_000; // ~50k lines max (increased from 10k for Saleor)

    // Configuration keys for YAML customization
    private static final String CONFIG_MAX_FILE_SIZE_BYTES = "maxFileSizeBytes";
    private static final String CONFIG_MAX_SCHEMA_LINES = "maxSchemaLines";

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
        return Set.of(Technologies.GRAPHQL, Technologies.JAVASCRIPT,Technologies.TYPESCRIPT, Technologies.JAVA, Technologies.KOTLIN, Technologies.CSHARP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(GRAPHQL_FILE_PATTERN, GQL_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, GRAPHQL_FILE_PATTERN, GQL_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning GraphQL schemas in: {}", context.rootPath());

        // Read configurable limits from context
        long maxFileSizeBytes = getMaxFileSizeBytes(context);
        int maxSchemaLines = getMaxSchemaLines(context);

        log.debug("GraphQL scanner limits: maxFileSize={}MB, maxLines={}",
            maxFileSizeBytes / 1_048_576, maxSchemaLines);

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<DataEntity> dataEntities = new ArrayList<>();

        // Find all GraphQL schema files
        List<Path> schemaFiles = new ArrayList<>();
        context.findFiles(GRAPHQL_FILE_PATTERN).forEach(schemaFiles::add);
        context.findFiles(GQL_FILE_PATTERN).forEach(schemaFiles::add);

        if (schemaFiles.isEmpty()) {
            log.warn("No GraphQL schema files found in project");
            return emptyResult();
        }

        List<String> warnings = new ArrayList<>();
        for (Path schemaFile : schemaFiles) {
            try {
                // Pre-filter: Skip files that are too large to parse safely
                if (!shouldScanFile(schemaFile, maxFileSizeBytes, maxSchemaLines, warnings)) {
                    continue;
                }

                // Performance logging
                long startTime = System.currentTimeMillis();
                parseSchemaFile(schemaFile, apiEndpoints, dataEntities);
                long duration = System.currentTimeMillis() - startTime;

                if (duration > 1000) {
                    log.info("Parsed large GraphQL schema: {} ({} ms)", schemaFile.getFileName(), duration);
                } else {
                    log.debug("Parsed GraphQL schema: {} ({} ms)", schemaFile.getFileName(), duration);
                }
            } catch (Exception e) {
                log.error("Failed to parse GraphQL schema: {}", schemaFile, e);
                // Don't fail the entire scan - just skip this file and log a warning
                warnings.add("Failed to parse GraphQL schema: " + schemaFile.getFileName() + " - " + e.getMessage());
            }
        }

        log.info("Found {} GraphQL types and {} operations across {} schema files",
            dataEntities.size(), apiEndpoints.size(), schemaFiles.size());

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
     * Gets the maximum file size in bytes from configuration, or default if not configured.
     *
     * @param context scan context
     * @return maximum file size in bytes
     */
    private long getMaxFileSizeBytes(ScanContext context) {
        Object value = context.getConfig(CONFIG_MAX_FILE_SIZE_BYTES);
        if (value instanceof Number num) {
            return num.longValue();
        }
        return DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    /**
     * Gets the maximum schema line count from configuration, or default if not configured.
     *
     * @param context scan context
     * @return maximum schema line count
     */
    private int getMaxSchemaLines(ScanContext context) {
        Object value = context.getConfig(CONFIG_MAX_SCHEMA_LINES);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return DEFAULT_MAX_SCHEMA_LINES;
    }

    /**
     * Pre-filter files to skip those that are too large to parse safely.
     *
     * <p>The graphql-java parser has a token limit (15,000 tokens by default) to prevent
     * DoS attacks. Very large schema files (e.g., Saleor's monolithic schema.graphql with 35k+ lines)
     * may exceed this limit and fail to parse. This method uses configurable limits to
     * pre-filter files before attempting to parse them.
     *
     * <p><b>Detection Strategy:</b>
     * <ol>
     *   <li>Check file size - skip files larger than configured limit (default 5MB)</li>
     *   <li>Check line count - skip files with more than configured limit (default 50k lines)</li>
     * </ol>
     *
     * @param file path to GraphQL schema file
     * @param maxFileSizeBytes maximum file size in bytes (configurable)
     * @param maxSchemaLines maximum line count (configurable)
     * @param warnings list to add warning messages for skipped files
     * @return true if file should be scanned, false if it should be skipped
     */
    private boolean shouldScanFile(Path file, long maxFileSizeBytes, int maxSchemaLines, List<String> warnings) {
        try {
            // Check 1: File size
            long fileSize = Files.size(file);
            if (fileSize > maxFileSizeBytes) {
                String msg = String.format("Skipping large GraphQL schema file: %s (%d KB) - exceeds %d KB limit",
                    file.getFileName(), fileSize / 1024, maxFileSizeBytes / 1024);
                log.warn(msg);
                warnings.add(msg);
                return false;
            }

            // Check 2: Line count (simple heuristic - count newlines)
            String content = readFileContent(file);
            long lineCount = content.lines().count();
            if (lineCount > maxSchemaLines) {
                String msg = String.format("Skipping large GraphQL schema file: %s (%d lines) - exceeds %d line limit",
                    file.getFileName(), lineCount, maxSchemaLines);
                log.warn(msg);
                warnings.add(msg);
                return false;
            }

            log.debug("GraphQL schema file passed pre-filter checks: {} ({} KB, {} lines)",
                file.getFileName(), fileSize / 1024, lineCount);
            return true;
        } catch (IOException e) {
            log.debug("Failed to check file size for pre-filtering: {}", file, e);
            // If we can't check the file, try to scan it anyway
            return true;
        }
    }

    /**
     * Parses a single GraphQL schema file using graphql-java Parser and extracts types and operations.
     *
     * <p>Uses custom ParserOptions to increase the token limit beyond the default 15,000 tokens
     * to support large schema files like Saleor's 35k+ line schema.
     *
     * @param schemaFile path to GraphQL schema file
     * @param apiEndpoints list to add discovered API endpoints
     * @param dataEntities list to add discovered data entities
     * @throws IOException if file cannot be read
     */
    private void parseSchemaFile(Path schemaFile, List<ApiEndpoint> apiEndpoints,
                                 List<DataEntity> dataEntities) throws IOException {
        String content = readFileContent(schemaFile);
        String componentId = schemaFile.getFileName().toString()
            .replace(GRAPHQL_EXTENSION, "")
            .replace(GQL_EXTENSION, "");

        // Parse GraphQL schema using graphql-java library with increased token limit
        // Default is 15,000 tokens; we increase to 200,000 to handle large schemas like Saleor (35k+ lines)
        ParserOptions parserOptions = ParserOptions.newParserOptions()
            .maxTokens(200_000)
            .build();
        ParserEnvironment parserEnvironment = ParserEnvironment.newParserEnvironment()
            .document(content)
            .parserOptions(parserOptions)
            .build();
        Document document = Parser.parse(parserEnvironment);

        // Process all definitions in the document
        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof ObjectTypeDefinition objectType) {
                processObjectType(objectType, componentId, apiEndpoints, dataEntities);
            } else if (definition instanceof InputObjectTypeDefinition inputType) {
                processInputType(inputType, componentId, dataEntities);
            }
        }
    }

    /**
     * Processes a GraphQL object type definition.
     * Query and Mutation types are extracted as API endpoints, others as data entities.
     *
     * @param objectType object type definition from AST
     * @param componentId component ID
     * @param apiEndpoints list to add discovered endpoints
     * @param dataEntities list to add discovered entities
     */
    private void processObjectType(ObjectTypeDefinition objectType, String componentId,
                                   List<ApiEndpoint> apiEndpoints, List<DataEntity> dataEntities) {
        String typeName = objectType.getName();

        if (QUERY_TYPE.equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, QUERY_OPERATION, apiEndpoints);
        } else if (MUTATION_TYPE.equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, MUTATION_OPERATION, apiEndpoints);
        } else if (SUBSCRIPTION_TYPE.equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, SUBSCRIPTION_OPERATION, apiEndpoints);
        } else {
            extractTypeAsEntity(typeName, objectType.getFieldDefinitions(), componentId, dataEntities);
        }
    }

    /**
     * Processes a GraphQL input type definition as a data entity.
     *
     * @param inputType input type definition from AST
     * @param componentId component ID
     * @param dataEntities list to add discovered entities
     */
    private void processInputType(InputObjectTypeDefinition inputType, String componentId,
                                  List<DataEntity> dataEntities) {
        String typeName = inputType.getName();
        List<DataEntity.Field> fields = new ArrayList<>();

        for (InputValueDefinition inputValue : inputType.getInputValueDefinitions()) {
            String fieldName = inputValue.getName();
            Type<?> fieldType = inputValue.getType();

            boolean nullable = !(fieldType instanceof NonNullType);
            String cleanType = extractTypeName(fieldType);

            DataEntity.Field field = new DataEntity.Field(
                fieldName,
                cleanType,
                nullable,
                null
            );
            fields.add(field);
        }

        DataEntity entity = new DataEntity(
            componentId,
            typeName,
            GRAPHQL_INPUT_TYPE,
            fields,
            null,
            "GraphQL input type: " + typeName
        );

        dataEntities.add(entity);
        log.debug("Found GraphQL input type: {} with {} fields", typeName, fields.size());
    }

    /**
     * Extracts GraphQL operations (queries, mutations, subscriptions) as API endpoints.
     *
     * @param fieldDefinitions field definitions from Query/Mutation/Subscription type
     * @param componentId component ID
     * @param operationType "query", "mutation", or "subscription"
     * @param apiEndpoints list to add discovered endpoints
     */
    private void extractOperations(List<FieldDefinition> fieldDefinitions, String componentId, String operationType,
                                   List<ApiEndpoint> apiEndpoints) {
        for (FieldDefinition field : fieldDefinitions) {
            String fieldName = field.getName();
            String returnType = extractTypeName(field.getType());

            // Build request schema from arguments
            String requestSchema = field.getInputValueDefinitions().isEmpty() ? null :
                field.getInputValueDefinitions().stream()
                    .map(arg -> arg.getName() + COLON_SEPARATOR + extractTypeName(arg.getType()))
                    .reduce((a, b) -> a + COMMA_SEPARATOR + b)
                    .orElse(null);

            // Determine ApiType based on operation type
            ApiType apiType = switch (operationType.toLowerCase()) {
                case QUERY_OPERATION -> ApiType.GRAPHQL_QUERY;
                case MUTATION_OPERATION -> ApiType.GRAPHQL_MUTATION;
                case SUBSCRIPTION_OPERATION -> ApiType.GRAPHQL_SUBSCRIPTION;
                default -> ApiType.GRAPHQL_QUERY;
            };

            ApiEndpoint endpoint = new ApiEndpoint(
                componentId,
                apiType,
                fieldName,
                operationType.toUpperCase(),
                "GraphQL " + operationType + ": " + fieldName,
                requestSchema,
                returnType,
                null // authentication
            );

            apiEndpoints.add(endpoint);
            log.debug("Found GraphQL {}: {} -> {}", operationType, fieldName, returnType);
        }
    }

    /**
     * Extracts a GraphQL type definition as a data entity.
     *
     * @param typeName type name
     * @param fieldDefinitions field definitions from type
     * @param componentId component ID
     * @param dataEntities list to add discovered entities
     */
    private void extractTypeAsEntity(String typeName, List<FieldDefinition> fieldDefinitions, String componentId,
                                     List<DataEntity> dataEntities) {
        List<DataEntity.Field> fields = new ArrayList<>();

        for (FieldDefinition field : fieldDefinitions) {
            String fieldName = field.getName();
            Type<?> fieldType = field.getType();

            boolean nullable = !(fieldType instanceof NonNullType);
            String cleanType = extractTypeName(fieldType);

            DataEntity.Field entityField = new DataEntity.Field(
                fieldName,
                cleanType,
                nullable,
                null
            );
            fields.add(entityField);
        }

        // Try to find ID field for primary key
        String primaryKey = fields.stream()
            .filter(f -> ID_FIELD_NAME.equalsIgnoreCase(f.name()) || ID_TYPE_NAME.equals(f.dataType()))
            .findFirst()
            .map(DataEntity.Field::name)
            .orElse(null);

        DataEntity entity = new DataEntity(
            componentId,
            typeName,
            GRAPHQL_TYPE,
            fields,
            primaryKey,
            "GraphQL type: " + typeName
        );

        dataEntities.add(entity);
        log.debug("Found GraphQL type: {} with {} fields", typeName, fields.size());
    }

    /**
     * Recursively extracts the type name from a GraphQL Type object,
     * handling NonNullType and ListType wrappers.
     *
     * @param type GraphQL type from AST
     * @return clean type name (e.g., "String", "User", "[User]")
     */
    private String extractTypeName(Type<?> type) {
        if (type instanceof NonNullType nonNullType) {
            return extractTypeName(nonNullType.getType());
        } else if (type instanceof ListType listType) {
            return "[" + extractTypeName(listType.getType()) + "]";
        } else if (type instanceof TypeName typeName) {
            return typeName.getName();
        }
        return UNKNOWN_TYPE;
    }
}
