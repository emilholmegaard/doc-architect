package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link EloquentModelScanner}.
 *
 * <p>Tests the scanner's ability to parse Laravel Eloquent model definitions
 * and extract database entities, fields, and relationships.
 */
class EloquentModelScannerTest extends ScannerTestBase {

    private final EloquentModelScanner scanner = new EloquentModelScanner();

    @Test
    void scan_withBasicModel_extractsEntity() throws IOException {
        // Given: A basic Eloquent model
        createFile("app/Models/User.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class User extends Model
            {
                protected $fillable = ['name', 'email', 'password'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("App\\Models\\User");
        assertThat(entity.name()).isEqualTo("users"); // snake_case plural
        assertThat(entity.type()).isEqualTo("table");
        assertThat(entity.description()).isEqualTo("Eloquent Model: User");
    }

    @Test
    void scan_withCustomTableName_usesProvidedName() throws IOException {
        // Given: A model with custom table name
        createFile("app/Models/UserAccount.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class UserAccount extends Model
            {
                protected $table = 'user_accounts_custom';
                protected $fillable = ['username'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use custom table name
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("user_accounts_custom");
    }

    @Test
    void scan_withCustomPrimaryKey_extractsPrimaryKey() throws IOException {
        // Given: A model with custom primary key
        createFile("app/Models/Product.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class Product extends Model
            {
                protected $primaryKey = 'product_id';
                protected $fillable = ['name', 'price'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract custom primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.primaryKey()).isEqualTo("product_id");
    }

    @Test
    void scan_withFillableAndCasts_extractsFields() throws IOException {
        // Given: A model with fillable fields and casts
        createFile("app/Models/Post.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class Post extends Model
            {
                protected $fillable = ['title', 'content', 'published_at'];

                protected $casts = [
                    'published_at' => 'datetime',
                    'is_featured' => 'boolean',
                ];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract fields with types from casts
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields()).hasSizeGreaterThanOrEqualTo(3);

        // Verify cast types are used
        assertThat(entity.fields())
            .anyMatch(f -> f.name().equals("published_at") && f.dataType().equals("datetime"));
        assertThat(entity.fields())
            .anyMatch(f -> f.name().equals("is_featured") && f.dataType().equals("boolean"));
    }

    @Test
    void scan_withHasManyRelationship_extractsRelationship() throws IOException {
        // Given: A model with HasMany relationship
        createFile("app/Models/Author.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;
            use Illuminate\\Database\\Eloquent\\Relations\\HasMany;

            class Author extends Model
            {
                protected $fillable = ['name'];

                public function posts(): HasMany
                {
                    return $this->hasMany(Post::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.sourceId()).isEqualTo("App\\Models\\Author");
        assertThat(rel.targetId()).isEqualTo("App\\Models\\Post");
        assertThat(rel.description()).contains("HasMany");
        assertThat(rel.technology()).isEqualTo("Eloquent");
    }

    @Test
    void scan_withBelongsToRelationship_extractsRelationship() throws IOException {
        // Given: A model with BelongsTo relationship
        createFile("app/Models/Comment.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;
            use Illuminate\\Database\\Eloquent\\Relations\\BelongsTo;

            class Comment extends Model
            {
                protected $fillable = ['body'];

                public function post(): BelongsTo
                {
                    return $this->belongsTo(Post::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.sourceId()).isEqualTo("App\\Models\\Comment");
        assertThat(rel.targetId()).isEqualTo("App\\Models\\Post");
        assertThat(rel.description()).contains("BelongsTo");
    }

    @Test
    void scan_withBelongsToManyRelationship_extractsRelationship() throws IOException {
        // Given: A model with BelongsToMany relationship
        createFile("app/Models/Tag.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;
            use Illuminate\\Database\\Eloquent\\Relations\\BelongsToMany;

            class Tag extends Model
            {
                protected $fillable = ['name'];

                public function posts(): BelongsToMany
                {
                    return $this->belongsToMany(Post::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.description()).contains("BelongsToMany");
    }

    @Test
    void scan_withHasOneRelationship_extractsRelationship() throws IOException {
        // Given: A model with HasOne relationship
        createFile("app/Models/Customer.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;
            use Illuminate\\Database\\Eloquent\\Relations\\HasOne;

            class Customer extends Model
            {
                protected $fillable = ['name'];

                public function profile(): HasOne
                {
                    return $this->hasOne(Profile::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationship
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(1);

        Relationship rel = result.relationships().get(0);
        assertThat(rel.description()).contains("HasOne");
    }

    @Test
    void scan_withMultipleRelationships_extractsAll() throws IOException {
        // Given: A model with multiple relationships
        createFile("app/Models/Order.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;
            use Illuminate\\Database\\Eloquent\\Relations\\BelongsTo;
            use Illuminate\\Database\\Eloquent\\Relations\\HasMany;

            class Order extends Model
            {
                protected $fillable = ['total'];

                public function customer(): BelongsTo
                {
                    return $this->belongsTo(Customer::class);
                }

                public function items(): HasMany
                {
                    return $this->hasMany(OrderItem::class);
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all relationships
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).hasSize(2);
    }

    @Test
    void scan_withFullyQualifiedModelExtends_extractsEntity() throws IOException {
        // Given: A model using fully qualified Model class
        createFile("app/Models/Category.php", """
            <?php

            namespace App\\Models;

            class Category extends Illuminate\\Database\\Eloquent\\Model
            {
                protected $fillable = ['name'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract the entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("App\\Models\\Category");
    }

    @Test
    void scan_withNoModels_returnsEmptyResult() {
        // Given: No PHP model files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void scan_withGuardedFields_extractsFields() throws IOException {
        // Given: A model with guarded fields
        createFile("app/Models/Admin.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class Admin extends Model
            {
                protected $guarded = ['id', 'created_at'];
                protected $fillable = ['name', 'email'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract fields from both fillable and guarded
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .contains("name", "email", "id", "created_at");
    }

    @Test
    void scan_withDefaultPrimaryKey_usesId() throws IOException {
        // Given: A model without custom primary key
        createFile("app/Models/Simple.php", """
            <?php

            namespace App\\Models;

            use Illuminate\\Database\\Eloquent\\Model;

            class Simple extends Model
            {
                protected $fillable = ['value'];
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should default to 'id' primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.primaryKey()).isEqualTo("id");
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("eloquent-models");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Eloquent Model Scanner");
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
    void appliesTo_withEloquentModel_returnsTrue() throws IOException {
        // Given: An Eloquent model file exists
        createFile("app/Models/Test.php", """
            <?php
            namespace App\\Models;
            use Illuminate\\Database\\Eloquent\\Model;
            class Test extends Model {}
            """);

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutEloquentModels_returnsFalse() {
        // Given: No Eloquent model files

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
