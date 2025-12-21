package com.docarchitect.core.scanner.impl.dotnet;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;

/**
 * Functional tests for {@link EntityFrameworkScanner}.
 */
class EntityFrameworkScannerTest extends ScannerTestBase {

    private EntityFrameworkScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new EntityFrameworkScanner();
    }

    @Disabled
    void scan_withDbContext_extractsEntities() throws IOException {
        // Given: DbContext with DbSet properties
        createFile("Data/AppDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class AppDbContext : DbContext
{
    public DbSet<User> Users { get; set; }
    public DbSet<Post> Posts { get; set; }
}

public class User
{
    public int Id { get; set; }
    public string Username { get; set; }
}

public class Post
{
    public int Id { get; set; }
    public string Title { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract entities
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSizeGreaterThanOrEqualTo(2);

        assertThat(result.dataEntities())
            .extracting(DataEntity::name)
            .contains("User", "Post");
    }

    @Disabled
    void scan_withNavigationProperties_detectsRelationships() throws IOException {
        // Given: Entities with navigation properties
        createFile("Models/Order.cs", """
using System.Collections.Generic;

public class Order
{
    public int Id { get; set; }
    public int CustomerId { get; set; }
    public Customer Customer { get; set; }
}

public class Customer
{
    public int Id { get; set; }
    public List<Order> Orders { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect relationships
        assertThat(result.success()).isTrue();
        assertThat(result.relationships()).isNotEmpty();
    }

    @Test
    void scan_withNoCSharpFiles_returnsEmpty() throws IOException {
        // Given: No C# files in project
        createDirectory("src/main/java");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void appliesTo_withCSharpFiles_returnsTrue() throws IOException {
        // Given: Project with C# files
        createFile("Data/Test.cs", "public class Test { }");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutCSharpFiles_returnsFalse() throws IOException {
        // Given: Project without C# files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_efCoreEntity_identifiesPrimaryKeyByIdConvention() throws IOException {
        // Given: Entity with Id field (EF convention)
        createFile("Data/AppDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class AppDbContext : DbContext
{
    public DbSet<Product> Products { get; set; }
}
""");

        createFile("Models/Product.cs", """
public class Product
{
    public int Id { get; set; }
    public string Name { get; set; }
    public decimal Price { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should identify Id as primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("Products");
        assertThat(entity.primaryKey()).isEqualTo("Id");
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("Id", "Name", "Price");
    }

    @Test
    void scan_efCoreEntity_identifiesPrimaryKeyByClassNameIdConvention() throws IOException {
        // Given: Entity with ProductId field (EF convention)
        createFile("Data/AppDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class AppDbContext : DbContext
{
    public DbSet<Product> Products { get; set; }
}
""");

        createFile("Models/Product.cs", """
public class Product
{
    public int ProductId { get; set; }
    public string Name { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should identify ProductId as primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("Products");
        assertThat(entity.primaryKey()).isEqualTo("ProductId");
    }

    @Test
    void scan_efCoreEntity_identifiesPrimaryKeyByKeyAttribute() throws IOException {
        // Given: Entity with [Key] attribute
        createFile("Data/AppDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class AppDbContext : DbContext
{
    public DbSet<User> Users { get; set; }
}
""");

        createFile("Models/User.cs", """
using System.ComponentModel.DataAnnotations;

public class User
{
    [Key]
    public string Username { get; set; }
    public string Email { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should identify Username as primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("Users");
        assertThat(entity.primaryKey()).isEqualTo("Username");
    }

    @Test
    void scan_efCoreEntity_onlyPrimaryKeyFieldIdentified() throws IOException {
        // Given: Entity with multiple non-nullable fields but only one PK
        createFile("Data/CatalogDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class CatalogDbContext : DbContext
{
    public DbSet<CatalogItem> CatalogItems { get; set; }
}
""");

        createFile("Models/CatalogItem.cs", """
public class CatalogItem
{
    public int Id { get; set; }
    public string Name { get; set; }
    public string Description { get; set; }
    public decimal Price { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Only Id should be identified as primary key
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.name()).isEqualTo("CatalogItems");
        assertThat(entity.primaryKey()).isEqualTo("Id");

        // Verify all fields are present
        assertThat(entity.fields())
            .extracting(DataEntity.Field::name)
            .containsExactlyInAnyOrder("Id", "Name", "Description", "Price");
    }

    @Test
    void scan_efCoreEntity_noPrimaryKey_returnsNull() throws IOException {
        // Given: Entity without conventional primary key
        createFile("Data/AppDbContext.cs", """
using Microsoft.EntityFrameworkCore;

public class AppDbContext : DbContext
{
    public DbSet<Settings> Settings { get; set; }
}
""");

        createFile("Models/Settings.cs", """
public class Settings
{
    public string ConfigKey { get; set; }
    public string ConfigValue { get; set; }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Primary key should be null
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity entity = result.dataEntities().get(0);
        assertThat(entity.primaryKey()).isNull();
    }
}
