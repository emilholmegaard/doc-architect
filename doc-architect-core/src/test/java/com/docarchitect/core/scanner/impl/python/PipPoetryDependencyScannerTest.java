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
}
