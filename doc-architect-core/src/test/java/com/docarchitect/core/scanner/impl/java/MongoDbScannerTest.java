package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link MongoDbScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse Spring Data MongoDB @Document classes using JavaParser AST</li>
 *   <li>Extract document fields with @Field annotation mapping</li>
 *   <li>Detect @Id primary key annotations</li>
 *   <li>Extract collection names from @Document annotation</li>
 *   <li>Detect @DBRef relationships and create Relationship records</li>
 *   <li>Handle embedded documents (fields without @DBRef)</li>
 *   <li>Convert class names to snake_case collection names</li>
 *   <li>Pre-filter files without MongoDB imports for performance</li>
 * </ul>
 *
 * @see MongoDbScanner
 * @since 1.0.0
 */
class MongoDbScannerTest extends ScannerTestBase {

    private MongoDbScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new MongoDbScanner();
    }

    @Test
    void scan_withSimpleDocument_extractsFieldsAndPrimaryKey() throws IOException {
        // Given: A simple MongoDB @Document entity
        createFile("src/main/java/com/example/User.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document(collection = "users")
            public class User {
                @Id
                private String id;

                private String username;

                private String email;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract document with fields and primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("com.example.User");
        assertThat(entity.name()).isEqualTo("users");
        assertThat(entity.type()).isEqualTo("collection");
        assertThat(entity.primaryKey()).isEqualTo("id");
        assertThat(entity.description()).isEqualTo("MongoDB Document: User");
        assertThat(entity.fields()).hasSize(3);

        DataEntity.Field idField = entity.fields().stream()
            .filter(f -> "id".equals(f.name()))
            .findFirst()
            .orElseThrow();

        assertThat(idField.dataType()).isEqualTo("String");
        assertThat(idField.nullable()).isTrue();
    }

    @Test
    void scan_withDocumentWithoutCollection_usesDefaultName() throws IOException {
        // Given: MongoDB @Document without collection attribute
        createFile("src/main/java/com/example/DataPoint.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document
            public class DataPoint {
                @Id
                private String id;

                private Double value;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use snake_case conversion of class name
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("data_point");
        assertThat(entity.type()).isEqualTo("collection");
    }

    @Test
    void scan_withFieldAnnotation_usesMappedFieldName() throws IOException {
        // Given: Document with @Field annotation
        createFile("src/main/java/com/example/Account.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.Field;
            import org.springframework.data.annotation.Id;

            @Document(collection = "accounts")
            public class Account {
                @Id
                private String id;

                @Field("username")
                private String userName;

                @Field("email_address")
                private String emailAddress;

                private String status;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use mapped field names from @Field annotation
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields()).hasSize(4);

        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("id", "username", "email_address", "status");
    }

    @Test
    void scan_withDBRefSingleReference_createsRelationship() throws IOException {
        // Given: Document with @DBRef to single entity
        createFile("src/main/java/com/example/Order.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.DBRef;
            import org.springframework.data.annotation.Id;

            @Document(collection = "orders")
            public class Order {
                @Id
                private String id;

                @DBRef
                private User owner;

                private String status;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.sourceId()).isEqualTo("com.example.Order");
        assertThat(relationship.targetId()).isEqualTo("User");
        assertThat(relationship.type()).isEqualTo(RelationshipType.DEPENDS_ON);
        assertThat(relationship.description()).isEqualTo("DBRef relationship");
        assertThat(relationship.technology()).isEqualTo("MongoDB");

        // Owner field should not be included in regular fields
        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("id", "status");
    }

    @Test
    void scan_withDBRefCollectionReference_createsRelationship() throws IOException {
        // Given: Document with @DBRef to collection
        createFile("src/main/java/com/example/Account.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.DBRef;
            import org.springframework.data.annotation.Id;
            import java.util.List;

            @Document(collection = "accounts")
            public class Account {
                @Id
                private String id;

                @DBRef
                private List<Transaction> transactions;

                private String accountNumber;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship with target from List<T>
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.sourceId()).isEqualTo("com.example.Account");
        assertThat(relationship.targetId()).isEqualTo("Transaction");
        assertThat(relationship.type()).isEqualTo(RelationshipType.DEPENDS_ON);
    }

    @Test
    void scan_withDBRefSetReference_createsRelationship() throws IOException {
        // Given: Document with @DBRef to Set
        createFile("src/main/java/com/example/Project.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.DBRef;
            import org.springframework.data.annotation.Id;
            import java.util.Set;

            @Document(collection = "projects")
            public class Project {
                @Id
                private String id;

                @DBRef
                private Set<User> members;

                private String name;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship with target from Set<T>
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.sourceId()).isEqualTo("com.example.Project");
        assertThat(relationship.targetId()).isEqualTo("User");
    }

    @Test
    void scan_withEmbeddedDocument_extractsAsField() throws IOException {
        // Given: Document with embedded object (no @DBRef)
        createFile("src/main/java/com/example/Order.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document(collection = "orders")
            public class Order {
                @Id
                private String id;

                private Address shippingAddress;

                private String status;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract embedded object as regular field
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.relationships()).isEmpty();

        DataEntity entity = result.dataEntities().get(0);
        DataEntity.Field addressField = entity.fields().stream()
            .filter(f -> "shippingAddress".equals(f.name()))
            .findFirst()
            .orElseThrow();

        assertThat(addressField.dataType()).isEqualTo("Address");
    }

    @Test
    void scan_withComplexDocument_extractsAllFeatures() throws IOException {
        // Given: Complex document with @Field, @DBRef, and embedded objects
        createFile("src/main/java/com/example/Account.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.Field;
            import org.springframework.data.mongodb.core.mapping.DBRef;
            import org.springframework.data.annotation.Id;
            import java.time.Instant;
            import java.util.List;

            @Document(collection = "accounts")
            public class Account {
                @Id
                private String id;

                @Field("username")
                private String userName;

                @DBRef
                private User owner;

                @DBRef
                private List<Item> items;

                private Address billingAddress;

                @Field("created_at")
                private Instant createdAt;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all features correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.relationships()).hasSize(2);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("com.example.Account");
        assertThat(entity.name()).isEqualTo("accounts");
        assertThat(entity.primaryKey()).isEqualTo("id");

        // Check fields (excluding @DBRef fields)
        assertThat(entity.fields()).hasSize(4);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("id", "username", "billingAddress", "created_at");

        // Check relationships
        assertThat(result.relationships())
            .extracting(Relationship::sourceId, Relationship::targetId)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("com.example.Account", "User"),
                org.assertj.core.groups.Tuple.tuple("com.example.Account", "Item")
            );
    }

    @Test
    void scan_withMultipleDocuments_extractsAll() throws IOException {
        // Given: Multiple document files
        createFile("src/main/java/com/example/Customer.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document(collection = "customers")
            public class Customer {
                @Id
                private String id;

                private String name;
            }
            """);

        createFile("src/main/java/com/example/Invoice.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document(collection = "invoices")
            public class Invoice {
                @Id
                private String id;

                private String number;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both documents
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);

        assertThat(result.dataEntities())
            .extracting(DataEntity::componentId)
            .containsExactlyInAnyOrder("com.example.Customer", "com.example.Invoice");
    }

    @Test
    void scan_withNonDocumentClass_ignoresClass() throws IOException {
        // Given: A regular class without @Document
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            public class UserService {
                private String id;

                public void doSomething() {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withNoMongoImports_skipsFile() throws IOException {
        // Given: Java file without MongoDB imports (pre-filter test)
        createFile("src/main/java/com/example/PlainClass.java", """
            package com.example;

            public class PlainClass {
                private String name;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should skip file and return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withNoJavaFiles_returnsEmptyResult() throws IOException {
        // Given: No Java files in project
        createDirectory("src/main/resources");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void appliesTo_withJavaFiles_returnsTrue() throws IOException {
        // Given: Project with Java files and MongoDB dependency
        createFile("src/main/java/com/example/Test.java", "public class Test {}");

        // Create mock dependency scan result with MongoDB
        ScanResult depResult = new ScanResult(
            "maven-dependencies",
            true,
            List.of(),  // components
            List.of(new com.docarchitect.core.model.Dependency("test-component", "org.springframework.data", "spring-data-mongodb", "4.0.0", "compile", true)),  // dependencies
            List.of(),  // apiEndpoints
            List.of(),  // messageFlows
            List.of(),  // dataEntities
            List.of(),  // relationships
            List.of(),  // warnings
            List.of(),  // errors
            com.docarchitect.core.scanner.ScanStatistics.empty()  // statistics
        );

        Map<String, ScanResult> previousResults = Map.of("maven-dependencies", depResult);
        ScanContext contextWithDeps = new ScanContext(tempDir, List.of(tempDir), Map.of(), Map.of(), previousResults);

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(contextWithDeps);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutJavaFiles_returnsFalse() throws IOException {
        // Given: Project without Java files
        createDirectory("src/main/resources");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withMultipleDBRefs_createsMultipleRelationships() throws IOException {
        // Given: Document with multiple @DBRef fields
        createFile("src/main/java/com/example/Order.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.mongodb.core.mapping.DBRef;
            import org.springframework.data.annotation.Id;
            import java.util.List;

            @Document(collection = "orders")
            public class Order {
                @Id
                private String id;

                @DBRef
                private User customer;

                @DBRef
                private User assignedAgent;

                @DBRef
                private List<Product> products;

                private String status;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create relationship for each @DBRef
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(3);

        assertThat(result.relationships())
            .extracting(Relationship::targetId)
            .containsExactlyInAnyOrder("User", "User", "Product");
    }

    @Test
    void scan_withCamelCaseClassName_convertsToSnakeCase() throws IOException {
        // Given: Document with CamelCase name and no collection attribute
        createFile("src/main/java/com/example/UserPreference.java", """
            package com.example;

            import org.springframework.data.mongodb.core.mapping.Document;
            import org.springframework.data.annotation.Id;

            @Document
            public class UserPreference {
                @Id
                private String id;

                private String value;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should convert to snake_case
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("user_preference");
    }
}
