package com.docarchitect.core.scanner.impl.go;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link GoStructScanner}.
 */
class GoStructScannerTest extends ScannerTestBase {

    private GoStructScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new GoStructScanner();
    }

    // ==================== XORM Framework Tests ====================

    @Test
    void scan_withXormStruct_extractsEntity() throws IOException {
        // Given: Go file with XORM struct
        createFile("models/user.go", """
package models

type User struct {
    Id       int64  `xorm:"pk autoincr"`
    Username string `xorm:"unique not null"`
    Email    string `xorm:"varchar(255)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract XORM entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("models");
        assertThat(entity.name()).isEqualTo("user");
        assertThat(entity.type()).isEqualTo("table");
        assertThat(entity.fields()).hasSize(3);
        assertThat(entity.primaryKey()).isEqualTo("id");
    }

    @Test
    void scan_withXormFields_extractsFieldMetadata() throws IOException {
        // Given: Go file with XORM fields
        createFile("models/post.go", """
package models

type Post struct {
    Id      int64  `xorm:"pk autoincr"`
    Title   string `xorm:"varchar(200) not null"`
    Content string `xorm:"text"`
    UserId  int64  `xorm:"index"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract field metadata correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields()).hasSize(4);

        DataEntity.Field titleField = entity.fields().stream()
            .filter(f -> f.name().equals("title"))
            .findFirst()
            .orElseThrow();

        assertThat(titleField.dataType()).isEqualTo("VARCHAR");
        assertThat(titleField.nullable()).isFalse();
    }

    @Test
    void scan_withXormPrimaryKey_identifiesPk() throws IOException {
        // Given: Go file with XORM primary key
        createFile("models/item.go", """
package models

type Item struct {
    ItemId int64  `xorm:"'item_id' pk autoincr"`
    Name   string `xorm:"varchar(100)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should identify primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.primaryKey()).isEqualTo("item_id");
    }

    // ==================== GORM Framework Tests ====================

    @Test
    void scan_withGormStruct_extractsEntity() throws IOException {
        // Given: Go file with GORM struct
        createFile("models/product.go", """
package models

import "gorm.io/gorm"

type Product struct {
    gorm.Model
    Code  string `gorm:"uniqueIndex"`
    Price uint   `gorm:"not null"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract GORM entity with embedded Model fields
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("models");
        assertThat(entity.name()).isEqualTo("product");
        assertThat(entity.type()).isEqualTo("table");

        // Should have Code + Price + 4 gorm.Model fields (ID, CreatedAt, UpdatedAt, DeletedAt)
        assertThat(entity.fields()).hasSize(6);
        assertThat(entity.primaryKey()).isEqualTo("id");
    }

    @Test
    void scan_withGormModel_includesStandardFields() throws IOException {
        // Given: Go file with gorm.Model
        createFile("models/order.go", """
package models

import "gorm.io/gorm"

type Order struct {
    gorm.Model
    Total  float64 `gorm:"not null"`
    Status string  `gorm:"varchar(50)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should include standard GORM fields
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);

        // Check for standard GORM Model fields
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .contains("id", "created_at", "updated_at", "deleted_at");
    }

    @Test
    void scan_withGormColumnTag_usesColumnName() throws IOException {
        // Given: Go file with GORM column tag
        createFile("models/account.go", """
package models

type Account struct {
    AccountNumber string `gorm:"column:account_num;not null"`
    Balance       int64  `gorm:"column:balance"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should use column name from tag
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .contains("account_num", "balance");
    }

    // ==================== sqlx/db Tag Tests ====================

    @Test
    void scan_withDbTag_extractsEntity() throws IOException {
        // Given: Go file with db tag
        createFile("models/person.go", """
package models

type Person struct {
    FirstName string `db:"first_name"`
    LastName  string `db:"last_name"`
    Age       int    `db:"age"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract entity with db tags
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("person");
        assertThat(entity.fields()).hasSize(3);

        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("first_name", "last_name", "age");
    }

    @Test
    void scan_withSqlTag_extractsEntity() throws IOException {
        // Given: Go file with sql tag
        createFile("models/employee.go", """
package models

type Employee struct {
    EmployeeId int    `sql:"employee_id"`
    Department string `sql:"dept"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract entity with sql tags
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("employee_id", "dept");
    }

    // ==================== Type Mapping Tests ====================

    @Test
    void scan_withGoTypes_mapsToSqlTypes() throws IOException {
        // Given: Go file with various Go types
        createFile("models/types.go", """
package models

type TypeTest struct {
    IntField    int       `xorm:"int_field"`
    Int64Field  int64     `xorm:"int64_field"`
    StringField string    `xorm:"string_field"`
    BoolField   bool      `xorm:"bool_field"`
    FloatField  float64   `xorm:"float_field"`
    TimeField   time.Time `xorm:"time_field"`
    ByteField   []byte    `xorm:"byte_field"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should map Go types to SQL types
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("int_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("INTEGER");

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("string_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("VARCHAR");

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("bool_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("BOOLEAN");

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("float_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("FLOAT");

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("time_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("TIMESTAMP");

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("byte_field"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("BLOB");
    }

    @Test
    void scan_withPointerTypes_mapsCorrectly() throws IOException {
        // Given: Go file with pointer types
        createFile("models/nullable.go", """
package models

type NullableTest struct {
    Id     int64   `xorm:"pk"`
    Name   *string `xorm:"name"`
    Active *bool   `xorm:"active"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should map pointer types correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);

        // Pointer types should still map to correct SQL types
        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("name"))
            .extracting(DataEntity.Field::dataType)
            .containsExactly("VARCHAR");
    }

    // ==================== Multiple Structs Tests ====================

    @Test
    void scan_withMultipleStructs_extractsAll() throws IOException {
        // Given: Go file with multiple XORM structs
        createFile("models/entities.go", """
package models

type User struct {
    Id   int64  `xorm:"pk autoincr"`
    Name string `xorm:"varchar(100)"`
}

type Post struct {
    Id     int64  `xorm:"pk autoincr"`
    Title  string `xorm:"varchar(200)"`
    UserId int64  `xorm:"index"`
}

type Comment struct {
    Id     int64  `xorm:"pk autoincr"`
    Text   string `xorm:"text"`
    PostId int64  `xorm:"index"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all structs
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(3);

        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder("user", "post", "comment");
    }

    @Test
    void scan_withMultipleFiles_extractsAll() throws IOException {
        // Given: Multiple Go files with ORM structs
        createFile("models/user.go", """
package models

type User struct {
    Id   int64  `xorm:"pk autoincr"`
    Name string `xorm:"varchar(100)"`
}
""");

        createFile("models/post.go", """
package models

type Post struct {
    Id    int64  `xorm:"pk autoincr"`
    Title string `xorm:"varchar(200)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract entities from all files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);
    }

    // ==================== Pre-filtering Tests ====================

    @Test
    void scan_withoutOrmTags_skipsFile() throws IOException {
        // Given: Go file without ORM tags
        createFile("pkg/util.go", """
package util

type Config struct {
    Host string
    Port int
}

func LoadConfig() *Config {
    return &Config{}
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract any entities
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withMixedStructs_extractsOnlyOrmStructs() throws IOException {
        // Given: Go file with both ORM and non-ORM structs
        createFile("models/mixed.go", """
package models

type User struct {
    Id   int64  `xorm:"pk autoincr"`
    Name string `xorm:"varchar(100)"`
}

type UserDTO struct {
    Name  string
    Email string
}

type Post struct {
    Id    int64  `xorm:"pk autoincr"`
    Title string `xorm:"varchar(200)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract only ORM structs
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);

        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder("user", "post")
            .doesNotContain("user_dto");
    }

    // ==================== CamelCase to snake_case Tests ====================

    @Test
    void scan_withCamelCaseStructName_convertsToSnakeCase() throws IOException {
        // Given: Go file with CamelCase struct names
        createFile("models/entities.go", """
package models

type UserAccount struct {
    Id int64 `xorm:"pk"`
}

type BlogPost struct {
    Id int64 `xorm:"pk"`
}

type APIKey struct {
    Id int64 `xorm:"pk"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should convert names to snake_case
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(3);

        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .containsExactlyInAnyOrder("user_account", "blog_post", "api_key");
    }

    @Test
    void scan_withCamelCaseFieldName_convertsToSnakeCase() throws IOException {
        // Given: Go file with CamelCase field names (no explicit column tag)
        createFile("models/user.go", """
package models

type User struct {
    UserId    int64  `xorm:"pk"`
    FirstName string `xorm:"not null"`
    LastName  string `xorm:"not null"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should convert field names to snake_case
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .contains("user_id", "first_name", "last_name");
    }

    // ==================== Nullable Detection Tests ====================

    @Test
    void scan_withNotNullTag_setsFieldNotNullable() throws IOException {
        // Given: Go file with NOT NULL tags
        createFile("models/user.go", """
package models

type User struct {
    Id       int64  `xorm:"pk autoincr"`
    Username string `xorm:"not null"`
    Email    string `xorm:"notnull"`
    Bio      string `xorm:"text"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should set nullable correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);

        // Fields with NOT NULL should not be nullable
        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("username"))
            .extracting(DataEntity.Field::nullable)
            .containsExactly(false);

        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("email"))
            .extracting(DataEntity.Field::nullable)
            .containsExactly(false);

        // Fields without NOT NULL should be nullable
        assertThat(entity.fields())
            .filteredOn(f -> f.name().equals("bio"))
            .extracting(DataEntity.Field::nullable)
            .containsExactly(true);
    }

    // ==================== Edge Cases ====================

    @Test
    void scan_withNoGoFiles_returnsEmpty() throws IOException {
        // Given: No Go files in project
        createDirectory("src");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withEmptyStruct_skipsEntity() throws IOException {
        // Given: Go file with empty struct (no ORM fields)
        createFile("models/empty.go", """
package models

type Empty struct {
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract empty entity
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withComplexTags_parsesCorrectly() throws IOException {
        // Given: Go file with complex XORM tags
        createFile("models/user.go", """
package models

type User struct {
    Id       int64  `xorm:"'id' pk autoincr not null"`
    Username string `xorm:"'username' varchar(100) unique not null index"`
    Email    string `xorm:"varchar(255) unique"`
    Created  int64  `xorm:"created"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse complex tags correctly
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.fields()).hasSize(4);
        assertThat(entity.primaryKey()).isEqualTo("id");
    }

    // ==================== Metadata Tests ====================

    @Test
    void scan_withXormStruct_createsCorrectMetadata() throws IOException {
        // Given: Go file with XORM struct
        createFile("models/user.go", """
package models

type User struct {
    Id   int64  `xorm:"pk autoincr"`
    Name string `xorm:"varchar(100)"`
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create DataEntity with correct metadata
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.componentId()).isEqualTo("models");
        assertThat(entity.name()).isEqualTo("user");
        assertThat(entity.type()).isEqualTo("table");
        assertThat(entity.description()).isEqualTo("Go ORM Model: User");
    }

    // ==================== appliesTo Tests ====================

    @Test
    void appliesTo_withGoFiles_returnsTrue() throws IOException {
        // Given: Project with Go files
        createFile("models/user.go", "package models");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutGoFiles_returnsFalse() throws IOException {
        // Given: Project without Go files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
