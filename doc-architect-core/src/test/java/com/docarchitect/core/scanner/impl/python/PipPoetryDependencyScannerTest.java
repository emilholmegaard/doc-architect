package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link PipPoetryDependencyScanner}.
 */
class PipPoetryDependencyScannerTest extends ScannerTestBase {

    private PipPoetryDependencyScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new PipPoetryDependencyScanner();
    }

    @Test
    void scan_withRequirementsTxt_extractsDependencies() throws IOException {
        // Given: requirements.txt with dependencies
        createFile("app/requirements.txt", String.join(System.lineSeparator(),
            "Django==4.2.0",
            "requests>=2.28.0",
            "pytest~=7.3.0",
            "black==23.0.0"
        ));

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSizeGreaterThanOrEqualTo(4);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("Django", "requests", "pytest", "black");
    }

    @Test
    void scan_withPyprojectToml_extractsDependencies() throws IOException {
        // Given: pyproject.toml with Poetry dependencies
        createFile("app/pyproject.toml", """
[tool.poetry]
name = "my-app"
version = "1.0.0"

[tool.poetry.dependencies]
python = "^3.11"
fastapi = "^0.100.0"
sqlalchemy = "^2.0.0"

[tool.poetry.dev-dependencies]
pytest = "^7.3.0"
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSizeGreaterThanOrEqualTo(3);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("fastapi", "sqlalchemy", "pytest");
    }

    @Test
    void scan_withSetupPy_extractsDependencies() throws IOException {
        // Given: setup.py with install_requires
        createFile("app/setup.py", """
from setuptools import setup

setup(
    name='my-package',
    version='1.0.0',
    install_requires=[
        'numpy>=1.24.0',
        'pandas==2.0.0',
        'matplotlib>=3.7.0',
    ],
)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSizeGreaterThanOrEqualTo(3);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("numpy", "pandas", "matplotlib");
    }

    @Test
    void scan_withNoDependencyFiles_returnsEmpty() throws IOException {
        // Given: Python file without dependency specifications
        createFile("app/main.py", """
            def hello():
                print("Hello, World!")
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void appliesTo_withRequirementsTxt_returnsTrue() throws IOException {
        // Given: Project with requirements.txt
        createFile("app/requirements.txt", "Django==4.2.0");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutDependencyFiles_returnsFalse() throws IOException {
        // Given: Project without dependency files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withPyprojectTomlDependencyGroups_extractsDependencies() throws IOException {
        // Given: pyproject.toml with PEP 735 dependency-groups (used by Saleor, uv, Hatch)
        createFile("app/pyproject.toml", """
[project]
name = "saleor"
version = "3.23.0"
dependencies = [
    "django[bcrypt]~=5.2.8",
    "Adyen>=4.0.0,<5",
    "psycopg[binary]>=3.2.9,<4",
    "celery[redis, sqs]>=4.4.5,<6.0.0",
]

[dependency-groups]
dev = [
    "pytest>=8.3.2,<9",
    "coverage~=7.6",
    "ruff>=0.12.2,<0.13",
]
test = [
    "pytest-django==4.11.1",
    "pytest-cov>=6.0.0,<7",
]
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all dependencies including dev groups
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSizeGreaterThanOrEqualTo(9);

        // Verify compile-scope dependencies (from project.dependencies)
        assertThat(result.dependencies())
            .filteredOn(d -> d.scope().equals("compile"))
            .extracting(Dependency::artifactId)
            .contains("django[bcrypt]", "Adyen", "psycopg[binary]", "celery[redis, sqs]");

        // Verify test-scope dependencies (from dependency-groups.dev and .test)
        assertThat(result.dependencies())
            .filteredOn(d -> d.scope().equals("test"))
            .extracting(Dependency::artifactId)
            .containsAnyOf("pytest", "coverage", "ruff", "pytest-django", "pytest-cov");
    }

    @Test
    void scan_withPackageExtras_preservesExtrasInArtifactId() throws IOException {
        // Given: requirements.txt with package extras
        createFile("app/requirements.txt", String.join(System.lineSeparator(),
            "django[bcrypt]~=5.2.8",
            "psycopg[binary]>=3.2.9",
            "celery[redis,sqs]>=4.4.5"
        ));

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should preserve extras in artifact ID
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("django[bcrypt]", "psycopg[binary]", "celery[redis,sqs]");
    }

    @Test
    void scan_withComplexVersionSpecs_extractsFullVersionString() throws IOException {
        // Given: requirements.txt with complex version specifiers
        createFile("app/requirements.txt", String.join(System.lineSeparator(),
            "Adyen>=4.0.0,<5",
            "babel>=2.8,<2.18",
            "boto3~=1.28",
            "cryptography>=44.0.2,<45"
        ));

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract full version specifications
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(4);

        assertThat(result.dependencies())
            .filteredOn(d -> d.artifactId().equals("Adyen"))
            .extracting(Dependency::version)
            .containsExactly("4.0.0,<5");

        assertThat(result.dependencies())
            .filteredOn(d -> d.artifactId().equals("babel"))
            .extracting(Dependency::version)
            .containsExactly("2.8,<2.18");
    }

    @Test
    void scan_withPipfile_extractsDependencies() throws IOException {
        // Given: Pipfile with dependencies
        createFile("app/Pipfile", """
[packages]
django = "~=4.2"
requests = "*"
celery = {version = ">=5.0", extras = ["redis"]}

[dev-packages]
pytest = "*"
black = "==23.0.0"
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSizeGreaterThanOrEqualTo(5);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .contains("django", "requests", "celery", "pytest", "black");
    }
}
