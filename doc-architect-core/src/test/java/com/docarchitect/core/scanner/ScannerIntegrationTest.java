package com.docarchitect.core.scanner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Scanner SPI (Service Provider Interface).
 *
 * <p>These tests verify:
 * <ul>
 *   <li>All 19 scanners are discoverable via ServiceLoader</li>
 *   <li>Each scanner has valid metadata (ID, display name, languages, patterns, priority)</li>
 *   <li>Scanner IDs are unique across all implementations</li>
 *   <li>SPI registration files are correctly configured</li>
 * </ul>
 *
 * <p><b>Critical for runtime behavior:</b> If these tests fail, scanners won't be
 * discovered by the CLI application, resulting in silent failures during execution.
 */
class ScannerIntegrationTest {

    /**
     * Expected scanner count based on implemented phases:
     * - Phase 3: 5 Java/JVM scanners
     * - Phase 4: 5 Python scanners
     * - Phase 5: 3 .NET scanners
     * - Phase 6: 6 Additional scanners (GraphQL, Avro, SQL, npm, Go, Express)
     * Total: 19 scanners
     */
    private static final int EXPECTED_SCANNER_COUNT = 19;

    /**
     * Verifies all scanners are discoverable via ServiceLoader.
     * This is the core SPI mechanism used by the CLI to find available scanners.
     */
    @Test
    void serviceLoader_shouldDiscoverAllScanners() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);
        List<Scanner> scanners = new ArrayList<>();
        loader.forEach(scanners::add);

        assertThat(scanners)
            .as("ServiceLoader should discover exactly %d scanners", EXPECTED_SCANNER_COUNT)
            .hasSize(EXPECTED_SCANNER_COUNT);
    }

    /**
     * Verifies each scanner has a valid, non-empty ID.
     * IDs are used for configuration, logging, and referencing scan results.
     */
    @Test
    void allScanners_shouldHaveValidId() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner.getId())
                .as("Scanner %s should have non-null ID", scanner.getClass().getSimpleName())
                .isNotNull()
                .isNotBlank();
        });
    }

    /**
     * Verifies all scanner IDs are unique.
     * Duplicate IDs would cause configuration conflicts and ambiguous logging.
     */
    @Test
    void allScanners_shouldHaveUniqueIds() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);
        Set<String> ids = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        loader.forEach(scanner -> {
            String id = scanner.getId();
            if (!ids.add(id)) {
                duplicates.add(id);
            }
        });

        assertThat(duplicates)
            .as("Scanner IDs should be unique, but found duplicates: %s", duplicates)
            .isEmpty();
    }

    /**
     * Verifies each scanner has a valid display name.
     * Display names are shown in CLI output and must be user-friendly.
     */
    @Test
    void allScanners_shouldHaveValidDisplayName() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner.getDisplayName())
                .as("Scanner %s should have non-null display name", scanner.getId())
                .isNotNull()
                .isNotBlank();
        });
    }

    /**
     * Verifies each scanner declares supported languages.
     * Language detection is used to filter applicable scanners for a project.
     */
    @Test
    void allScanners_shouldDeclareLanguages() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner.getSupportedLanguages())
                .as("Scanner %s should declare supported languages", scanner.getId())
                .isNotNull()
                .isNotEmpty();
        });
    }

    /**
     * Verifies each scanner declares file patterns.
     * File patterns are used to locate relevant files for scanning.
     */
    @Test
    void allScanners_shouldDeclareFilePatterns() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner.getSupportedFilePatterns())
                .as("Scanner %s should declare file patterns", scanner.getId())
                .isNotNull()
                .isNotEmpty();
        });
    }

    /**
     * Verifies each scanner has a valid priority.
     * Priorities determine execution order (lower values execute first).
     */
    @Test
    void allScanners_shouldHaveValidPriority() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner.getPriority())
                .as("Scanner %s should have positive priority", scanner.getId())
                .isPositive();
        });
    }

    /**
     * Verifies scanners can be instantiated and basic methods called.
     * This ensures no runtime initialization errors occur.
     */
    @Test
    void allScanners_shouldBeInstantiable() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            assertThat(scanner)
                .as("Scanner should be instantiable")
                .isNotNull();

            // Verify metadata methods don't throw exceptions
            assertThat(scanner.getId()).isNotNull();
            assertThat(scanner.getDisplayName()).isNotNull();
            assertThat(scanner.getSupportedLanguages()).isNotNull();
            assertThat(scanner.getSupportedFilePatterns()).isNotNull();
            assertThat(scanner.getPriority()).isGreaterThan(0);
        });
    }

    /**
     * Verifies expected scanner categories are present.
     * This ensures all technology stacks are represented.
     */
    @Test
    void allTechnologyStacks_shouldHaveScanners() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);
        Set<String> allLanguages = new HashSet<>();

        loader.forEach(scanner -> allLanguages.addAll(scanner.getSupportedLanguages()));

        assertThat(allLanguages)
            .as("Should have scanners for all major technology stacks")
            .contains("java", "python", "csharp", "javascript", "go");
    }

    /**
     * Verifies dependency scanners have appropriate priority (1-50).
     * Dependency scanners should run before other scanners.
     */
    @Test
    void dependencyScanners_shouldHaveHighPriority() {
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);

        loader.forEach(scanner -> {
            if (scanner.getId().contains("dependency") || scanner.getId().contains("maven") ||
                scanner.getId().contains("gradle") || scanner.getId().contains("nuget") ||
                scanner.getId().contains("pip") || scanner.getId().contains("poetry") ||
                scanner.getId().contains("npm") || scanner.getId().contains("go-mod")) {
                assertThat(scanner.getPriority())
                    .as("Dependency scanner %s should have priority 1-50", scanner.getId())
                    .isBetween(1, 50);
            }
        });
    }
}
