package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link ComposerDependencyScanner}.
 *
 * <p>Tests the scanner's ability to parse real composer.json files and extract
 * dependency information including vendor/package parsing and scope handling.
 */
class ComposerDependencyScannerTest extends ScannerTestBase {

    private final ComposerDependencyScanner scanner = new ComposerDependencyScanner();

    @Test
    void scan_withSimpleComposerJson_extractsDependencies() throws IOException {
        // Given: A simple composer.json with dependencies
        createFile("project/composer.json", """
            {
                "name": "acme/my-app",
                "description": "My PHP Application",
                "type": "project",
                "require": {
                    "php": "^8.1",
                    "laravel/framework": "^10.0",
                    "guzzlehttp/guzzle": "^7.5"
                },
                "require-dev": {
                    "phpunit/phpunit": "^10.0"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should successfully extract dependencies (excluding php platform requirement)
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(3);

        // Verify Laravel dependency
        Dependency laravel = result.dependencies().stream()
            .filter(d -> d.artifactId().equals("framework"))
            .findFirst()
            .orElseThrow();
        assertThat(laravel.groupId()).isEqualTo("laravel");
        assertThat(laravel.version()).isEqualTo("^10.0");
        assertThat(laravel.scope()).isEqualTo("compile");

        // Verify Guzzle dependency
        Dependency guzzle = result.dependencies().stream()
            .filter(d -> d.artifactId().equals("guzzle"))
            .findFirst()
            .orElseThrow();
        assertThat(guzzle.groupId()).isEqualTo("guzzlehttp");
        assertThat(guzzle.version()).isEqualTo("^7.5");
        assertThat(guzzle.scope()).isEqualTo("compile");

        // Verify PHPUnit dev dependency
        Dependency phpunit = result.dependencies().stream()
            .filter(d -> d.artifactId().equals("phpunit"))
            .findFirst()
            .orElseThrow();
        assertThat(phpunit.groupId()).isEqualTo("phpunit");
        assertThat(phpunit.version()).isEqualTo("^10.0");
        assertThat(phpunit.scope()).isEqualTo("test");
    }

    @Test
    void scan_withLaravelProject_extractsCorrectComponent() throws IOException {
        // Given: A Laravel project composer.json
        createFile("laravel-app/composer.json", """
            {
                "name": "my-company/laravel-app",
                "description": "A Laravel application",
                "type": "project",
                "require": {
                    "laravel/framework": "^10.0"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create a SERVICE component (type=project)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("laravel-app");
        assertThat(component.type()).isEqualTo(ComponentType.SERVICE);
        assertThat(component.technology()).isEqualTo("composer");
        assertThat(component.description()).contains("my-company/laravel-app");
    }

    @Test
    void scan_withLibraryPackage_extractsCorrectComponent() throws IOException {
        // Given: A library package composer.json
        createFile("my-lib/composer.json", """
            {
                "name": "vendor/my-library",
                "description": "A reusable library",
                "type": "library",
                "require": {
                    "psr/log": "^3.0"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create a LIBRARY component (type=library)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("my-library");
        assertThat(component.type()).isEqualTo(ComponentType.LIBRARY);
    }

    @Test
    void scan_skipsPlatformRequirements() throws IOException {
        // Given: A composer.json with platform requirements
        createFile("project/composer.json", """
            {
                "name": "acme/platform-test",
                "require": {
                    "php": "^8.2",
                    "ext-json": "*",
                    "ext-mbstring": "*",
                    "lib-libxml": ">=2.7.0",
                    "symfony/console": "^6.0"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should only extract the actual package dependency
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);

        Dependency symfony = result.dependencies().get(0);
        assertThat(symfony.groupId()).isEqualTo("symfony");
        assertThat(symfony.artifactId()).isEqualTo("console");
    }

    @Test
    void scan_withNoComposerFile_returnsEmptyResult() {
        // Given: No composer.json file exists

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).isEmpty();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void scan_withSymfonyProject_extractsAllDependencies() throws IOException {
        // Given: A Symfony project composer.json
        createFile("symfony-app/composer.json", """
            {
                "name": "my-company/symfony-app",
                "type": "project",
                "require": {
                    "php": ">=8.1",
                    "symfony/framework-bundle": "^6.4",
                    "symfony/http-kernel": "^6.4",
                    "doctrine/orm": "^2.17",
                    "doctrine/doctrine-bundle": "^2.11"
                },
                "require-dev": {
                    "symfony/debug-bundle": "^6.4",
                    "phpstan/phpstan": "^1.10"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all dependencies with correct scopes
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(6); // Excludes php platform requirement

        // Verify production dependencies
        long compileCount = result.dependencies().stream()
            .filter(d -> d.scope().equals("compile"))
            .count();
        assertThat(compileCount).isEqualTo(4);

        // Verify dev dependencies
        long testCount = result.dependencies().stream()
            .filter(d -> d.scope().equals("test"))
            .count();
        assertThat(testCount).isEqualTo(2);
    }

    @Test
    void scan_withMultipleComposerFiles_extractsFromAll() throws IOException {
        // Given: Multiple composer.json files in different directories
        createFile("packages/auth/composer.json", """
            {
                "name": "myapp/auth-package",
                "type": "library",
                "require": {
                    "firebase/php-jwt": "^6.0"
                }
            }
            """);

        createFile("packages/api/composer.json", """
            {
                "name": "myapp/api-package",
                "type": "library",
                "require": {
                    "league/fractal": "^0.20"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from both files
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);
        assertThat(result.dependencies()).hasSize(2);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("auth-package", "api-package");
    }

    @Test
    void scan_withVersionConstraints_preservesConstraintFormats() throws IOException {
        // Given: A composer.json with various version constraint formats
        createFile("project/composer.json", """
            {
                "name": "acme/version-test",
                "require": {
                    "vendor/exact": "1.2.3",
                    "vendor/caret": "^2.0",
                    "vendor/tilde": "~3.1",
                    "vendor/range": ">=1.0 <2.0",
                    "vendor/wildcard": "1.0.*",
                    "vendor/dev": "dev-main"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should preserve all version constraint formats
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(6);

        assertThat(result.dependencies())
            .extracting(Dependency::version)
            .containsExactlyInAnyOrder(
                "1.2.3",
                "^2.0",
                "~3.1",
                ">=1.0 <2.0",
                "1.0.*",
                "dev-main"
            );
    }

    @Test
    void scan_withMinimalComposerJson_handlesGracefully() throws IOException {
        // Given: A minimal composer.json without name
        createFile("project/composer.json", """
            {
                "require": {
                    "monolog/monolog": "^3.0"
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should still extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);
        assertThat(result.components()).isEmpty(); // No component without name
    }

    @Test
    void scan_withEmptyRequire_returnsNoDependencies() throws IOException {
        // Given: A composer.json with empty require section
        createFile("project/composer.json", """
            {
                "name": "acme/empty-deps",
                "require": {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return component but no dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void appliesTo_withComposerFile_returnsTrue() throws IOException {
        // Given: A composer.json file exists
        createFile("project/composer.json", "{}");

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutComposerFile_returnsFalse() {
        // Given: No composer.json file

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("composer-dependencies");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Composer Dependency Scanner");
    }

    @Test
    void getSupportedLanguages_includesPhp() {
        assertThat(scanner.getSupportedLanguages()).containsExactly("php");
    }

    @Test
    void getSupportedFilePatterns_includesComposerJson() {
        assertThat(scanner.getSupportedFilePatterns()).containsExactly("**/composer.json");
    }

    @Test
    void getPriority_returnsEarlyPriority() {
        // Dependency scanners should run early (low number = high priority)
        assertThat(scanner.getPriority()).isEqualTo(10);
    }
}
