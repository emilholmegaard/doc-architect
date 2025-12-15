package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.model.RelationshipType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link JpaEntityScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse JPA @Entity classes using JavaParser AST</li>
 *   <li>Extract entity fields with correct data types</li>
 *   <li>Detect @Id primary key annotations</li>
 *   <li>Extract table names from @Table annotation</li>
 *   <li>Detect JPA relationships (@OneToMany, @ManyToOne, etc.)</li>
 *   <li>Convert class names to snake_case table names</li>
 * </ul>
 *
 * @see JpaEntityScanner
 * @since 1.0.0
 */
class JpaEntityScannerTest extends ScannerTestBase {

    private JpaEntityScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new JpaEntityScanner();
    }

    @Test
    void scan_withSimpleEntity_extractsFieldsAndPrimaryKey() throws IOException {
        // Given: A simple JPA entity with @Id annotation
        createFile("src/main/java/com/example/User.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            public class User {
                @Id
                private Long id;

                private String username;

                private String email;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract entity with fields and primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("com.example.User");
        assertThat(entity.name()).isEqualTo("user"); // snake_case conversion
        assertThat(entity.type()).isEqualTo("table");
        assertThat(entity.primaryKey()).isEqualTo("id");
        assertThat(entity.fields()).hasSize(3);

        DataEntity.Field idField = entity.fields().stream()
            .filter(f -> "id".equals(f.name()))
            .findFirst()
            .orElseThrow();

        assertThat(idField.dataType()).isEqualTo("Long");
        assertThat(idField.nullable()).isTrue(); // No @Column(nullable=false)
    }

    @Test
    void scan_withTableAnnotation_usesSpecifiedTableName() throws IOException {
        // Given: Entity with @Table annotation
        createFile("src/main/java/com/example/Product.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            @Table(name = "products")
            public class Product {
                @Id
                private Long id;

                private String name;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use table name from @Table annotation
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("products");
    }

    @Test
    void scan_withOneToManyRelationship_extractsRelationship() throws IOException {
        // Given: Entity with @OneToMany relationship
        createFile("src/main/java/com/example/Order.java", """
            package com.example;

            import javax.persistence.*;
            import java.util.List;

            @Entity
            public class Order {
                @Id
                private Long id;

                @OneToMany(mappedBy = "order")
                private List<OrderItem> items;
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
        assertThat(relationship.targetId()).isEqualTo("OrderItem");
        assertThat(relationship.type()).isEqualTo(RelationshipType.DEPENDS_ON);
        assertThat(relationship.description()).isEqualTo("OneToMany relationship");
        assertThat(relationship.technology()).isEqualTo("JPA");
    }

    @Test
    void scan_withManyToOneRelationship_extractsRelationship() throws IOException {
        // Given: Entity with @ManyToOne relationship
        createFile("src/main/java/com/example/Comment.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            public class Comment {
                @Id
                private Long id;

                private String content;

                @ManyToOne
                private User author;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship relationship = result.relationships().get(0);
        assertThat(relationship.sourceId()).isEqualTo("com.example.Comment");
        assertThat(relationship.targetId()).isEqualTo("User");
        assertThat(relationship.description()).isEqualTo("ManyToOne relationship");
    }

    @Test
    void scan_withColumnNullableConstraint_setsNullableFalse() throws IOException {
        // Given: Entity with @Column(nullable = false)
        createFile("src/main/java/com/example/Account.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            public class Account {
                @Id
                private Long id;

                @Column(nullable = false)
                private String accountNumber;

                private String description;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should mark field as non-nullable
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        DataEntity.Field accountNumberField = entity.fields().stream()
            .filter(f -> "accountNumber".equals(f.name()))
            .findFirst()
            .orElseThrow();

        assertThat(accountNumberField.nullable()).isFalse();

        DataEntity.Field descriptionField = entity.fields().stream()
            .filter(f -> "description".equals(f.name()))
            .findFirst()
            .orElseThrow();

        assertThat(descriptionField.nullable()).isTrue(); // No constraint
    }

    @Test
    void scan_withMultipleEntities_extractsAll() throws IOException {
        // Given: Multiple entity files
        createFile("src/main/java/com/example/Customer.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            public class Customer {
                @Id
                private Long id;

                private String name;
            }
            """);

        createFile("src/main/java/com/example/Invoice.java", """
            package com.example;

            import javax.persistence.*;

            @Entity
            public class Invoice {
                @Id
                private Long id;

                private String number;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both entities
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);

        assertThat(result.dataEntities())
            .extracting(DataEntity::componentId)
            .containsExactlyInAnyOrder("com.example.Customer", "com.example.Invoice");
    }

    @Test
    void scan_withNonEntityClass_ignoresClass() throws IOException {
        // Given: A regular class without @Entity
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            public class UserService {
                private Long id;

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
        // Given: Project with Java files
        createFile("src/main/java/com/example/Test.java", "public class Test {}");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

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
}
