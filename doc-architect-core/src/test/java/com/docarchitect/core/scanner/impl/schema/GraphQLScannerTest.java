package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link GraphQLScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse GraphQL schema files using graphql-java library</li>
 *   <li>Extract type definitions as data entities</li>
 *   <li>Extract Query operations as API endpoints</li>
 *   <li>Extract Mutation operations as API endpoints</li>
 *   <li>Extract Subscription operations as API endpoints</li>
 *   <li>Handle both .graphql and .gql file extensions</li>
 *   <li>Parse input types</li>
 * </ul>
 *
 * @see GraphQLScanner
 * @since 1.0.0
 */
class GraphQLScannerTest extends ScannerTestBase {

    private GraphQLScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new GraphQLScanner();
    }

    @Test
    void scan_withSimpleSchema_extractsTypesAndQueries() throws IOException {
        // Given: A simple GraphQL schema with type and query
        createFile("schema/schema.graphql", """
            type User {
              id: ID!
              name: String!
              email: String
            }

            type Query {
              getUser(id: ID!): User
              listUsers: [User!]!
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract type and queries
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.apiEndpoints()).hasSize(2);

        DataEntity userType = result.dataEntities().get(0);
        assertThat(userType.name()).isEqualTo("User");
        assertThat(userType.type()).isEqualTo("graphql-type");
        assertThat(userType.fields()).hasSize(3);

        DataEntity.Field idField = userType.fields().stream()
            .filter(f -> "id".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(idField.dataType()).isEqualTo("ID");
        assertThat(idField.nullable()).isFalse(); // ID! is non-null

        ApiEndpoint getUserQuery = result.apiEndpoints().stream()
            .filter(e -> "getUser".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(getUserQuery.type()).isEqualTo(ApiType.GRAPHQL_QUERY);
        assertThat(getUserQuery.method()).isEqualTo("QUERY");
        assertThat(getUserQuery.requestSchema()).isEqualTo("id: ID");
        assertThat(getUserQuery.responseSchema()).isEqualTo("User");
    }

    @Test
    void scan_withMutations_extractsEndpoints() throws IOException {
        // Given: GraphQL schema with mutations
        createFile("schema/mutations.graphql", """
            type User {
              id: ID!
              name: String!
            }

            input CreateUserInput {
              name: String!
              email: String!
            }

            type Mutation {
              createUser(input: CreateUserInput!): User
              deleteUser(id: ID!): Boolean
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract mutations
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.dataEntities()).hasSize(2); // User type + CreateUserInput

        ApiEndpoint createUserMutation = result.apiEndpoints().stream()
            .filter(e -> "createUser".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(createUserMutation.type()).isEqualTo(ApiType.GRAPHQL_MUTATION);
        assertThat(createUserMutation.method()).isEqualTo("MUTATION");
        assertThat(createUserMutation.requestSchema()).isEqualTo("input: CreateUserInput");
        assertThat(createUserMutation.responseSchema()).isEqualTo("User");

        DataEntity inputType = result.dataEntities().stream()
            .filter(e -> "CreateUserInput".equals(e.name()))
            .findFirst()
            .orElseThrow();
        assertThat(inputType.type()).isEqualTo("graphql-input");
        assertThat(inputType.fields()).hasSize(2);
    }

    @Test
    void scan_withSubscriptions_extractsEndpoints() throws IOException {
        // Given: GraphQL schema with subscriptions
        createFile("schema/subscriptions.graphql", """
            type Message {
              id: ID!
              content: String!
            }

            type Subscription {
              messageAdded: Message
              messageUpdated(id: ID!): Message
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract subscriptions
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint messageAddedSub = result.apiEndpoints().stream()
            .filter(e -> "messageAdded".equals(e.path()))
            .findFirst()
            .orElseThrow();
        assertThat(messageAddedSub.type()).isEqualTo(ApiType.GRAPHQL_SUBSCRIPTION);
        assertThat(messageAddedSub.method()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    void scan_withListTypes_extractsCorrectly() throws IOException {
        // Given: GraphQL schema with list types
        createFile("schema/lists.gql", """
            type Post {
              id: ID!
              tags: [String!]!
              comments: [Comment]
            }

            type Comment {
              id: ID!
              text: String
            }

            type Query {
              getPosts: [Post!]!
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle list types
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);

        DataEntity postType = result.dataEntities().stream()
            .filter(e -> "Post".equals(e.name()))
            .findFirst()
            .orElseThrow();

        DataEntity.Field tagsField = postType.fields().stream()
            .filter(f -> "tags".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(tagsField.dataType()).isEqualTo("[String]");
        assertThat(tagsField.nullable()).isFalse(); // [String!]! is non-null

        ApiEndpoint getPostsQuery = result.apiEndpoints().get(0);
        assertThat(getPostsQuery.responseSchema()).isEqualTo("[Post]");
    }

    @Test
    void scan_withGqlExtension_parsesCorrectly() throws IOException {
        // Given: Schema file with .gql extension
        createFile("schema/api.gql", """
            type Product {
              id: ID!
              name: String!
            }

            type Query {
              getProduct(id: ID!): Product
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse .gql files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.apiEndpoints()).hasSize(1);
    }

    @Test
    void scan_withNoGraphQLFiles_returnsEmptyResult() throws IOException {
        // Given: No GraphQL files in project
        createDirectory("schema");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withMultipleSchemaFiles_extractsAll() throws IOException {
        // Given: Multiple schema files
        createFile("schema/types.graphql", """
            type User {
              id: ID!
              name: String!
            }
            """);

        createFile("schema/queries.graphql", """
            type Query {
              getUser(id: ID!): User
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from all files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.apiEndpoints()).hasSize(1);
    }

    @Test
    void appliesTo_withGraphQLFiles_returnsTrue() throws IOException {
        // Given: Project with GraphQL files
        createFile("schema/schema.graphql", "type Query { hello: String }");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutGraphQLFiles_returnsFalse() throws IOException {
        // Given: Project without GraphQL files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withLargeSchema_usesDefaultLimits() throws IOException {
        // Given: A schema file with many lines (but within default 50k limit)
        StringBuilder largeSchema = new StringBuilder();
        largeSchema.append("type Query {\n");
        for (int i = 0; i < 1000; i++) {
            largeSchema.append("  field").append(i).append(": String\n");
        }
        largeSchema.append("}\n");

        createFile("schema/large.graphql", largeSchema.toString());

        // When: Scanner is executed with default limits
        ScanResult result = scanner.scan(context);

        // Then: Should parse successfully
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1000);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void scan_withConfiguredLimits_respectsConfiguration() throws IOException {
        // Given: A schema file with 100 lines
        StringBuilder schema = new StringBuilder();
        schema.append("type Query {\n");
        for (int i = 0; i < 98; i++) {
            schema.append("  field").append(i).append(": String\n");
        }
        schema.append("}\n");

        createFile("schema/medium.graphql", schema.toString());

        // When: Scanner is executed with low limit (50 lines)
        context = createContext(Map.of("maxSchemaLines", 50));
        ScanResult result = scanner.scan(context);

        // Then: Should skip the file with warning
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("exceeds 50 line limit");
    }

    @Test
    void scan_withIncreasedLimit_parsesSaleorSizedSchema() throws IOException {
        // Given: A very large schema file (simulating Saleor's 35k lines)
        // Create a realistic large schema with multiple types (not just one huge Query type)
        StringBuilder largeSchema = new StringBuilder();

        // Create many entity types (more realistic than one giant Query type)
        for (int i = 0; i < 3000; i++) {
            largeSchema.append("type Entity").append(i).append(" {\n");
            largeSchema.append("  id: ID!\n");
            largeSchema.append("  name: String!\n");
            largeSchema.append("  description: String\n");
            largeSchema.append("  createdAt: String\n");
            largeSchema.append("  updatedAt: String\n");
            largeSchema.append("}\n\n");
        }

        // Add a Query type with fields referencing these entities
        largeSchema.append("type Query {\n");
        for (int i = 0; i < 100; i++) {
            largeSchema.append("  getEntity").append(i).append("(id: ID!): Entity").append(i).append("\n");
        }
        largeSchema.append("}\n");

        createFile("schema/saleor.graphql", largeSchema.toString());

        // When: Scanner is executed with increased limit (50k lines)
        context = createContext(Map.of("maxSchemaLines", 50000));
        ScanResult result = scanner.scan(context);

        // Then: Should parse successfully
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(3000); // 3000 entity types
        assertThat(result.apiEndpoints()).hasSize(100); // 100 query operations
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void scan_withFileSizeLimit_skipsTooLargeFiles() throws IOException {
        // Given: A large schema file (in bytes)
        StringBuilder largeSchema = new StringBuilder();
        // Create a schema that's > 1MB in size
        for (int i = 0; i < 100000; i++) {
            largeSchema.append("type Type").append(i).append(" { id: ID! }\n");
        }

        createFile("schema/huge.graphql", largeSchema.toString());

        // When: Scanner is executed with 500KB file size limit
        context = createContext(Map.of("maxFileSizeBytes", 500_000));
        ScanResult result = scanner.scan(context);

        // Then: Should skip the file with warning
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("exceeds").contains("KB limit");
    }

    @Test
    void scan_withPerformanceLogging_logsLargeFileParsing() throws IOException {
        // Given: A moderately large schema file
        StringBuilder schema = new StringBuilder();
        schema.append("type Query {\n");
        for (int i = 0; i < 500; i++) {
            schema.append("  field").append(i).append(": String\n");
        }
        schema.append("}\n");

        createFile("schema/perf.graphql", schema.toString());

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse successfully (performance logging is tested via logs, not assertions)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(500);
    }
}
