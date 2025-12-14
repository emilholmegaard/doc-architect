package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import graphql.language.*;
import graphql.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>Parse schema using graphql-java Parser (robust AST-based parsing)</li>
 *   <li>Extract type definitions → DataEntity records</li>
 *   <li>Extract Query fields → ApiEndpoint records (type=GRAPHQL)</li>
 *   <li>Extract Mutation fields → ApiEndpoint records (type=GRAPHQL)</li>
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
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GraphQLScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
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
 * @see Scanner
 * @see ApiEndpoint
 * @see DataEntity
 * @since 1.0.0
 */
public class GraphQLScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(GraphQLScanner.class);
    private final Parser parser = new Parser();

    @Override
    public String getId() {
        return "graphql-schema";
    }

    @Override
    public String getDisplayName() {
        return "GraphQL Schema Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("graphql", "javascript", "typescript", "java", "kotlin");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.graphql", "**/*.gql");
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return context.findFiles("**/*.graphql").findAny().isPresent() ||
               context.findFiles("**/*.gql").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning GraphQL schemas in: {}", context.rootPath());

        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<DataEntity> dataEntities = new ArrayList<>();

        // Find all GraphQL schema files
        List<Path> schemaFiles = new ArrayList<>();
        context.findFiles("**/*.graphql").forEach(schemaFiles::add);
        context.findFiles("**/*.gql").forEach(schemaFiles::add);

        if (schemaFiles.isEmpty()) {
            log.warn("No GraphQL schema files found in project");
            return ScanResult.empty(getId());
        }

        for (Path schemaFile : schemaFiles) {
            try {
                parseSchemaFile(schemaFile, apiEndpoints, dataEntities);
            } catch (Exception e) {
                log.error("Failed to parse GraphQL schema: {}", schemaFile, e);
                return ScanResult.failed(getId(), List.of("Failed to parse GraphQL schema: " + schemaFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} GraphQL types and {} operations across {} schema files",
            dataEntities.size(), apiEndpoints.size(), schemaFiles.size());

        return new ScanResult(
            getId(),
            true, // success
            List.of(), // No components
            List.of(), // No dependencies
            apiEndpoints,
            List.of(), // No message flows
            dataEntities,
            List.of(), // No relationships
            List.of(), // No warnings
            List.of()  // No errors
        );
    }

    /**
     * Parses a single GraphQL schema file using graphql-java Parser and extracts types and operations.
     *
     * @param schemaFile path to GraphQL schema file
     * @param apiEndpoints list to add discovered API endpoints
     * @param dataEntities list to add discovered data entities
     * @throws IOException if file cannot be read
     */
    private void parseSchemaFile(Path schemaFile, List<ApiEndpoint> apiEndpoints,
                                 List<DataEntity> dataEntities) throws IOException {
        String content = Files.readString(schemaFile);
        String componentId = schemaFile.getFileName().toString().replace(".graphql", "").replace(".gql", "");

        // Parse GraphQL schema using graphql-java library
        Document document = parser.parseDocument(content);

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

        if ("Query".equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, "query", apiEndpoints);
        } else if ("Mutation".equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, "mutation", apiEndpoints);
        } else if ("Subscription".equals(typeName)) {
            extractOperations(objectType.getFieldDefinitions(), componentId, "subscription", apiEndpoints);
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
            "graphql-input",
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
                    .map(arg -> arg.getName() + ": " + extractTypeName(arg.getType()))
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);

            // Determine ApiType based on operation type
            ApiType apiType = switch (operationType.toLowerCase()) {
                case "query" -> ApiType.GRAPHQL_QUERY;
                case "mutation" -> ApiType.GRAPHQL_MUTATION;
                case "subscription" -> ApiType.GRAPHQL_SUBSCRIPTION;
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
            .filter(f -> "id".equalsIgnoreCase(f.name()) || "ID".equals(f.dataType()))
            .findFirst()
            .map(DataEntity.Field::name)
            .orElse(null);

        DataEntity entity = new DataEntity(
            componentId,
            typeName,
            "graphql-type",
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
        return "Unknown";
    }
}
