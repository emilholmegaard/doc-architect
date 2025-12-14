package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scanner for GraphQL schema definitions in .graphql and .gql files.
 *
 * <p>This scanner parses GraphQL schema files using regex patterns to extract type definitions,
 * queries, and mutations. It converts GraphQL types into {@link DataEntity} records and
 * GraphQL operations (queries/mutations) into {@link ApiEndpoint} records.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate .graphql and .gql files using pattern matching</li>
 *   <li>Parse schema using regex patterns for types, queries, and mutations</li>
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

    // Regex patterns for GraphQL parsing
    private static final Pattern TYPE_PATTERN = Pattern.compile(
        "type\\s+(\\w+)\\s*\\{([^}]+)\\}",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern INPUT_TYPE_PATTERN = Pattern.compile(
        "input\\s+(\\w+)\\s*\\{([^}]+)\\}",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "^\\s*(\\w+)(?:\\(([^)]*)\\))?\\s*:\\s*([^\\s]+)",
        Pattern.MULTILINE
    );

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
     * Parses a single GraphQL schema file and extracts types and operations.
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

        // Remove comments
        content = content.replaceAll("#[^\n]*", "");

        // Extract type definitions (including Query and Mutation types)
        Matcher typeMatcher = TYPE_PATTERN.matcher(content);
        while (typeMatcher.find()) {
            String typeName = typeMatcher.group(1);
            String typeBody = typeMatcher.group(2);

            if ("Query".equals(typeName)) {
                // Extract query operations as API endpoints
                extractOperations(typeBody, componentId, "query", apiEndpoints);
            } else if ("Mutation".equals(typeName)) {
                // Extract mutation operations as API endpoints
                extractOperations(typeBody, componentId, "mutation", apiEndpoints);
            } else {
                // Extract as data entity
                extractTypeAsEntity(typeName, typeBody, componentId, dataEntities);
            }
        }

        // Extract input types as data entities
        Matcher inputMatcher = INPUT_TYPE_PATTERN.matcher(content);
        while (inputMatcher.find()) {
            String typeName = inputMatcher.group(1);
            String typeBody = inputMatcher.group(2);
            extractTypeAsEntity(typeName, typeBody, componentId, dataEntities);
        }
    }

    /**
     * Extracts GraphQL operations (queries or mutations) as API endpoints.
     *
     * @param operationBody body of the Query or Mutation type
     * @param componentId component ID
     * @param operationType "query" or "mutation"
     * @param apiEndpoints list to add discovered endpoints
     */
    private void extractOperations(String operationBody, String componentId, String operationType,
                                   List<ApiEndpoint> apiEndpoints) {
        Matcher fieldMatcher = FIELD_PATTERN.matcher(operationBody);

        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String arguments = fieldMatcher.group(2); // May be null if no arguments
            String returnType = fieldMatcher.group(3);

            // Build request schema from arguments
            String requestSchema = arguments != null ? arguments.trim() : null;

            // Determine ApiType based on operation type
            ApiType apiType = switch (operationType.toLowerCase()) {
                case "query" -> ApiType.GRAPHQL_QUERY;
                case "mutation" -> ApiType.GRAPHQL_MUTATION;
                case "subscription" -> ApiType.GRAPHQL_SUBSCRIPTION;
                default -> ApiType.GRAPHQL_QUERY; // Default to query
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
     * @param typeBody type body with field definitions
     * @param componentId component ID
     * @param dataEntities list to add discovered entities
     */
    private void extractTypeAsEntity(String typeName, String typeBody, String componentId,
                                     List<DataEntity> dataEntities) {
        List<DataEntity.Field> fields = new ArrayList<>();
        Matcher fieldMatcher = FIELD_PATTERN.matcher(typeBody);

        while (fieldMatcher.find()) {
            String fieldName = fieldMatcher.group(1);
            String fieldType = fieldMatcher.group(3);

            // Determine if field is nullable (! means non-null in GraphQL)
            boolean nullable = !fieldType.endsWith("!");
            String cleanType = fieldType.replaceAll("!", "").replaceAll("[\\[\\]]", "");

            DataEntity.Field field = new DataEntity.Field(
                fieldName,
                cleanType,
                nullable,
                null
            );
            fields.add(field);
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
}
