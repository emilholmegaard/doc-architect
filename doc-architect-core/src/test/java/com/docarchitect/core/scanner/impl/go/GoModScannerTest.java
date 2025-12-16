package com.docarchitect.core.scanner.impl.go;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link GoModScanner}.
 */
class GoModScannerTest extends ScannerTestBase {

    private GoModScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new GoModScanner();
    }

    @Test
    void scan_withSimpleGoMod_extractsDependencies() throws IOException {
        // Given: Simple go.mod with require block
        createFile("go.mod", """
module github.com/example/myapp

go 1.21

require (
    github.com/gin-gonic/gin v1.9.1
    github.com/stretchr/testify v1.8.4
    golang.org/x/crypto v0.14.0
)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract module and dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.dependencies()).hasSize(3);

        Component module = result.components().get(0);
        assertThat(module.name()).isEqualTo("myapp");
        assertThat(module.metadata()).containsEntry("modulePath", "github.com/example/myapp");

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("gin", "testify", "crypto");
    }

    @Test
    void scan_withSingleRequire_extractsDependency() throws IOException {
        // Given: go.mod with single require statement
        createFile("go.mod", """
module github.com/example/simple

go 1.21

require github.com/pkg/errors v0.9.1
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract single dependency
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);

        Dependency dep = result.dependencies().get(0);
        assertThat(dep.groupId()).isEqualTo("github.com/pkg");
        assertThat(dep.artifactId()).isEqualTo("errors");
        assertThat(dep.version()).isEqualTo("v0.9.1");
    }

    @Test
    void scan_withIndirectDependencies_extractsAll() throws IOException {
        // Given: go.mod with indirect dependencies
        createFile("go.mod", """
module github.com/example/app

require (
    github.com/gorilla/mux v1.8.0
    github.com/lib/pq v1.10.9 // indirect
)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all dependencies including indirect
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);
    }

    @Test
    void scan_withVersionedModule_handlesCorrectly() throws IOException {
        // Given: go.mod with major version in path
        createFile("go.mod", """
module github.com/example/mylib/v2

require (
    github.com/go-chi/chi/v5 v5.0.10
)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should handle versioned modules
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.dependencies()).hasSize(1);
    }

    @Test
    void scan_withPseudoVersion_parsesCorrectly() throws IOException {
        // Given: go.mod with pseudo-version
        createFile("go.mod", """
module github.com/example/test

require (
    example.com/package v0.0.0-20230101120000-abcdef123456
)
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should parse pseudo-version
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);

        Dependency dep = result.dependencies().get(0);
        assertThat(dep.version()).isEqualTo("v0.0.0-20230101120000-abcdef123456");
    }

    @Test
    void scan_withMultipleGoMod_extractsAll() throws IOException {
        // Given: Multiple go.mod files in workspace
        createFile("go.mod", """
module github.com/example/root
require github.com/pkg/errors v0.9.1
""");

        createFile("service1/go.mod", """
module github.com/example/service1
require github.com/gin-gonic/gin v1.9.1
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from all go.mod files
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);
        assertThat(result.dependencies()).hasSize(2);
    }

    @Test
    void scan_withNoGoMod_returnsEmpty() throws IOException {
        // Given: No go.mod files in project
        createDirectory("src");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void appliesTo_withGoMod_returnsTrue() throws IOException {
        // Given: Project with go.mod
        createFile("go.mod", "module test");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutGoMod_returnsFalse() throws IOException {
        // Given: Project without go.mod
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
