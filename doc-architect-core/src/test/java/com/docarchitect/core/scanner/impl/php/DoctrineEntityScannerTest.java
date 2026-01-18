package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link DoctrineEntityScanner}.
 *
 * <p>Tests the scanner's ability to parse Doctrine ORM entity definitions
 * and extract database entities, fields, and relationships.
 */
class DoctrineEntityScannerTest extends ScannerTestBase {

    private final DoctrineEntityScanner scanner = new DoctrineEntityScanner();

    @Test
    void scan_withPhp8Attributes_extractsEntity() throws IOException {
        // Given: A Doctrine entity with PHP 8 attributes
        createFile("src/Entity/User.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            #[ORM\\Table(name: 'users')]
            class User
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                #[ORM\\GeneratedValue]
                private int $id;

                #[ORM\\Column(type: 'string', length: 255)]
                private string $email;

                #[ORM\\Column(type: 'string', length: 255)]
                private string $name;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("App\\Entity\\User");
        assertThat(entity.name()).isEqualTo("users");
        assertThat(entity.type()).isEqualTo("table");
        assertThat(entity.primaryKey()).isEqualTo("id");
        assertThat(entity.description()).isEqualTo("Doctrine Entity: User");
    }

    @Test
    void scan_withDoctrineAnnotations_extractsEntity() throws IOException {
        // Given: A Doctrine entity with legacy annotations
        createFile("src/Entity/Post.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            /**
             * @ORM\\Entity
             * @ORM\\Table(name="posts")
             */
            class Post
            {
                /**
                 * @ORM\\Id
                 * @ORM\\Column(type="integer")
                 * @ORM\\GeneratedValue
                 */
                private $id;

                /**
                 * @ORM\\Column(type="string", length=255)
                 */
                private $title;

                /**
                 * @ORM\\Column(type="text")
                 */
                private $content;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("App\\Entity\\Post");
        assertThat(entity.name()).isEqualTo("posts");
        assertThat(entity.primaryKey()).isEqualTo("id");
    }

    @Test
    void scan_withDefaultTableName_derivesFromClassName() throws IOException {
        // Given: A Doctrine entity without explicit table name
        createFile("src/Entity/ProductCategory.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            class ProductCategory
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should derive table name from class name
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("product_category"); // snake_case
    }

    @Test
    void scan_withColumnAttributes_extractsFields() throws IOException {
        // Given: A Doctrine entity with column definitions
        createFile("src/Entity/Customer.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            class Customer
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\Column(type: 'string', length: 255)]
                private string $email;

                #[ORM\\Column(type: 'datetime')]
                private \\DateTimeInterface $createdAt;

                #[ORM\\Column(type: 'boolean', nullable: true)]
                private ?bool $isActive;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract fields with types
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields()).hasSizeGreaterThanOrEqualTo(3);

        // Verify field types
        assertThat(entity.fields())
            .anyMatch(f -> f.name().equals("email") && f.dataType().equals("string"));
        assertThat(entity.fields())
            .anyMatch(f -> f.name().equals("createdAt") && f.dataType().equals("datetime"));
    }

    @Test
    void scan_withOneToManyRelationship_extractsRelationship() throws IOException {
        // Given: A Doctrine entity with OneToMany relationship
        createFile("src/Entity/Author.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;
            use Doctrine\\Common\\Collections\\Collection;

            #[ORM\\Entity]
            class Author
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\OneToMany(targetEntity: Article::class, mappedBy: 'author')]
                private Collection $articles;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.sourceId()).isEqualTo("App\\Entity\\Author");
        assertThat(rel.targetId()).isEqualTo("App\\Entity\\Article");
        assertThat(rel.description()).contains("OneToMany");
        assertThat(rel.technology()).isEqualTo("Doctrine");
    }

    @Test
    void scan_withManyToOneRelationship_extractsRelationship() throws IOException {
        // Given: A Doctrine entity with ManyToOne relationship
        createFile("src/Entity/Article.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            class Article
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\ManyToOne(targetEntity: Author::class, inversedBy: 'articles')]
                private Author $author;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.description()).contains("ManyToOne");
    }

    @Test
    void scan_withManyToManyRelationship_extractsRelationship() throws IOException {
        // Given: A Doctrine entity with ManyToMany relationship
        createFile("src/Entity/Tag.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;
            use Doctrine\\Common\\Collections\\Collection;

            #[ORM\\Entity]
            class Tag
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\ManyToMany(targetEntity: Post::class, mappedBy: 'tags')]
                private Collection $posts;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.description()).contains("ManyToMany");
    }

    @Test
    void scan_withOneToOneRelationship_extractsRelationship() throws IOException {
        // Given: A Doctrine entity with OneToOne relationship
        createFile("src/Entity/UserProfile.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            class UserProfile
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\OneToOne(targetEntity: User::class, inversedBy: 'profile')]
                private User $user;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.description()).contains("OneToOne");
    }

    @Test
    void scan_withAnnotationRelationships_extractsRelationship() throws IOException {
        // Given: A Doctrine entity with annotation-style relationships
        createFile("src/Entity/Comment.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            /**
             * @ORM\\Entity
             */
            class Comment
            {
                /**
                 * @ORM\\Id
                 * @ORM\\Column(type="integer")
                 */
                private $id;

                /**
                 * @ORM\\ManyToOne(targetEntity="Post", inversedBy="comments")
                 */
                private $post;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.targetId()).isEqualTo("App\\Entity\\Post");
    }

    @Test
    void scan_withMultipleRelationships_extractsAll() throws IOException {
        // Given: A Doctrine entity with multiple relationships
        createFile("src/Entity/Order.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;
            use Doctrine\\Common\\Collections\\Collection;

            #[ORM\\Entity]
            class Order
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\ManyToOne(targetEntity: Customer::class)]
                private Customer $customer;

                #[ORM\\OneToMany(targetEntity: OrderItem::class, mappedBy: 'order')]
                private Collection $items;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all relationships
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(2);
    }

    @Test
    void scan_withSimpleEntityAttribute_extractsEntity() throws IOException {
        // Given: A Doctrine entity with simple #[Entity] attribute
        createFile("src/Entity/Simple.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping\\Entity;
            use Doctrine\\ORM\\Mapping\\Column;
            use Doctrine\\ORM\\Mapping\\Id;

            #[Entity]
            class Simple
            {
                #[Id]
                #[Column(type: 'integer')]
                private int $id;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("App\\Entity\\Simple");
    }

    @Test
    void scan_withNoEntities_returnsEmptyResult() {
        // Given: No Doctrine entity files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withNullableField_setsNullableTrue() throws IOException {
        // Given: A Doctrine entity with nullable field
        createFile("src/Entity/Profile.php", """
            <?php

            namespace App\\Entity;

            use Doctrine\\ORM\\Mapping as ORM;

            #[ORM\\Entity]
            class Profile
            {
                #[ORM\\Id]
                #[ORM\\Column(type: 'integer')]
                private int $id;

                #[ORM\\Column(type: 'string', nullable: true)]
                private ?string $bio;
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should mark field as nullable
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .anyMatch(f -> f.name().equals("bio") && f.nullable());
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("doctrine-entities");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Doctrine Entity Scanner");
    }

    @Test
    void getSupportedLanguages_includesPhp() {
        assertThat(scanner.getSupportedLanguages()).containsExactly("php");
    }

    @Test
    void getPriority_returnsOrmPriority() {
        // ORM scanners should run after API scanners
        assertThat(scanner.getPriority()).isEqualTo(60);
    }

    @Test
    void appliesTo_withDoctrineEntity_returnsTrue() throws IOException {
        // Given: A Doctrine entity file exists
        createFile("src/Entity/Test.php", """
            <?php
            namespace App\\Entity;
            use Doctrine\\ORM\\Mapping as ORM;
            #[ORM\\Entity]
            class Test {}
            """);

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutDoctrineEntities_returnsFalse() {
        // Given: No Doctrine entity files

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
