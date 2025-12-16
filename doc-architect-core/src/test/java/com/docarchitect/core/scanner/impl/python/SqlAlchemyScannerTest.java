package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;

/**
 * Functional tests for {@link SqlAlchemyScanner}.
 */
class SqlAlchemyScannerTest extends ScannerTestBase {

    private SqlAlchemyScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new SqlAlchemyScanner();
    }

    @Test
    void scan_withLegacyColumnSyntax_extractsEntities() throws IOException {
        // Given: SQLAlchemy models using legacy Column syntax
        createFile("app/models.py", """
from sqlalchemy import Column, Integer, String
from sqlalchemy.ext.declarative import declarative_base

Base = declarative_base()

class User(Base):
    __tablename__ = 'users'

    id = Column(Integer, primary_key=True)
    username = Column(String(50), nullable=False)
    email = Column(String(100), unique=True)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract User entity with 3 fields
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity user = result.dataEntities().get(0);
        assertThat(user.name()).isEqualTo("users");
        assertThat(user.fields()).hasSize(3);
        assertThat(user.fields())
            .extracting(f -> f.name())
            .containsExactlyInAnyOrder("id", "username", "email");
    }

    @Test
    void scan_withMappedColumnSyntax_extractsEntities() throws IOException {
        // Given: SQLAlchemy 2.0+ models using mapped_column
        createFile("app/models.py", """
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

class Base(DeclarativeBase):
    pass

class Product(Base):
    __tablename__ = 'products'

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column()
    price: Mapped[float] = mapped_column()
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Product entity with 3 fields
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(1);

        DataEntity product = result.dataEntities().get(0);
        assertThat(product.name()).isEqualTo("products");
        assertThat(product.fields()).hasSize(3);
    }

    @Test
    void scan_withRelationships_createsRelationshipObjects() throws IOException {
        // Given: SQLAlchemy models with relationships
        createFile("app/models.py", """
from sqlalchemy import Column, Integer, String, ForeignKey
from sqlalchemy.orm import relationship, declarative_base

Base = declarative_base()

class Author(Base):
    __tablename__ = 'authors'
    id = Column(Integer, primary_key=True)
    books = relationship('Book', back_populates='author')

class Book(Base):
    __tablename__ = 'books'
    id = Column(Integer, primary_key=True)
    author_id = Column(Integer, ForeignKey('authors.id'))
    author = relationship('Author', back_populates='books')
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract relationships
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).hasSize(2);
        assertThat(result.relationships()).isNotEmpty();
    }

    @Test
    void scan_withNoModels_returnsEmpty() throws IOException {
        // Given: Python file without SQLAlchemy models
        createFile("app/utils.py", """
def format_price(price):
    return f"${price:.2f}"
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void scan_withNoPythonFiles_returnsEmptyResult() throws IOException {
        // Given: No Python files in project
        createDirectory("src/main/java");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dataEntities()).isEmpty();
    }

    @Test
    void appliesTo_withPythonFiles_returnsTrue() throws IOException {
        // Given: Project with Python files
        createFile("app/test.py", "print('hello')");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutPythonFiles_returnsFalse() throws IOException {
        // Given: Project without Python files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
