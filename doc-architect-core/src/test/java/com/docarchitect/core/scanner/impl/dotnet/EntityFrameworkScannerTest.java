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
}
