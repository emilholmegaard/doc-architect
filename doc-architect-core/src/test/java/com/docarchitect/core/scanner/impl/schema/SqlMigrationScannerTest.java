package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link SqlMigrationScanner}.
 */
class SqlMigrationScannerTest extends ScannerTestBase {

    private SqlMigrationScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new SqlMigrationScanner();
    }

    @Test
    void scan_withSimpleCreateTable_extractsEntity() throws IOException {
        // Given: SQL migration with simple CREATE TABLE
        createFile("migrations/V1__create_users.sql", """
CREATE TABLE users (
    id BIGINT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100)
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract users table with 3 columns
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity users = result.dataEntities().get(0);
        assertThat(users.name()).isEqualTo("users");
        assertThat(users.type()).isEqualTo("table");
        assertThat(users.fields()).hasSize(3);
        assertThat(users.primaryKey()).isEqualTo("id");

        // Check nullable field
        DataEntity.Field usernameField = users.fields().stream()
            .filter(f -> "username".equals(f.name()))
            .findFirst()
            .orElseThrow();
        assertThat(usernameField.nullable()).isFalse();
    }

    @Test
    void scan_withForeignKeys_createsRelationships() throws IOException {
        // Given: SQL migration with foreign key
        createFile("migrations/V2__create_orders.sql", """
CREATE TABLE customers (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    total DECIMAL(10,2),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract foreign key relationship
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);
        assertThat(result.relationships()).hasSize(1);

        Relationship fk = result.relationships().get(0);
        assertThat(fk.sourceId()).isEqualTo("orders");
        assertThat(fk.targetId()).isEqualTo("customers");
    }

    @Test
    void scan_withFlywayNaming_parsesCorrectly() throws IOException {
        // Given: Flyway versioned migration
        createFile("db/migration/V1__initial_schema.sql", """
CREATE TABLE products (
    id INT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(10,2)
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse Flyway migrations
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.dataEntities().get(0).name()).isEqualTo("products");
    }

    @Test
    void scan_withGolangMigrate_parsesCorrectly() throws IOException {
        // Given: golang-migrate up migration
        createFile("migrations/001_create_posts.up.sql", """
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    content TEXT,
    created_at TIMESTAMP
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse golang-migrate files
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
    }

    @Test
    void scan_withMultipleTables_extractsAll() throws IOException {
        // Given: Migration with multiple CREATE TABLE statements
        createFile("migrations/schema.sql", """
CREATE TABLE authors (
    id INT PRIMARY KEY,
    name VARCHAR(100)
);

CREATE TABLE books (
    id INT PRIMARY KEY,
    title VARCHAR(255),
    author_id INT,
    FOREIGN KEY (author_id) REFERENCES authors(id)
);

CREATE TABLE reviews (
    id INT PRIMARY KEY,
    book_id INT,
    rating INT,
    FOREIGN KEY (book_id) REFERENCES books(id)
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all tables and relationships
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(3);
        assertThat(result.relationships()).hasSize(2);
    }

    @Test
    void scan_withIfNotExists_parsesCorrectly() throws IOException {
        // Given: SQL with IF NOT EXISTS clause
        createFile("migrations/init.sql", """
CREATE TABLE IF NOT EXISTS settings (
    key VARCHAR(50) PRIMARY KEY,
    value TEXT
);
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle IF NOT EXISTS
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);
        assertThat(result.dataEntities().get(0).name()).isEqualTo("settings");
    }

    @Test
    void scan_withNoSqlFiles_returnsEmpty() throws IOException {
        // Given: No SQL files in project
        createDirectory("migrations");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void appliesTo_withSqlFiles_returnsTrue() throws IOException {
        // Given: Project with SQL files
        createFile("migrations/V1__test.sql", "SELECT 1;");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutSqlFiles_returnsFalse() throws IOException {
        // Given: Project without SQL files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
