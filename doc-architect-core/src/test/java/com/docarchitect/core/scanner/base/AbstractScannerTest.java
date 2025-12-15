package com.docarchitect.core.scanner.base;

import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractScanner} base class functionality.
 *
 * <p>This test validates the common functionality provided to all scanner implementations:
 * <ul>
 *   <li>Logger initialization</li>
 *   <li>File reading utilities (readFileContent, readFileLines)</li>
 *   <li>appliesTo() helper (hasAnyFiles)</li>
 *   <li>ScanResult creation helpers (emptyResult, failedResult, buildSuccessResult)</li>
 * </ul>
 *
 * @since 1.0.0
 */
class AbstractScannerTest extends ScannerTestBase {

    private TestScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new TestScanner();
    }

    // ==================== File Reading Utilities ====================

    @Test
    void readFileContent_withValidFile_returnsContent() throws IOException {
        // Given: A file with text content
        Path file = createFile("test.txt", "Hello World\nLine 2\nLine 3");

        // When: Content is read
        String content = scanner.readFileContent(file);

        // Then: Should return full content
        assertThat(content).isEqualTo("Hello World\nLine 2\nLine 3");
    }

    @Test
    void readFileLines_withValidFile_returnsLines() throws IOException {
        // Given: A file with multiple lines
        Path file = createFile("test.txt", "Line 1\nLine 2\nLine 3");

        // When: Lines are read
        List<String> lines = scanner.readFileLines(file);

        // Then: Should return all lines
        assertThat(lines).containsExactly("Line 1", "Line 2", "Line 3");
    }

    @Test
    void readFileLines_withEmptyFile_returnsEmptyList() throws IOException {
        // Given: An empty file
        Path file = createFile("empty.txt", "");

        // When: Lines are read
        List<String> lines = scanner.readFileLines(file);

        // Then: Should return empty list
        assertThat(lines).isEmpty();
    }

    // ==================== appliesTo() Helper ====================

    @Test
    void hasAnyFiles_withMatchingPattern_returnsTrue() throws IOException {
        // Given: Files matching the pattern
        createFile("src/main/java/Test.java", "public class Test {}");

        // When: hasAnyFiles is called
        boolean result = scanner.hasAnyFiles(context, "**/*.java");

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    void hasAnyFiles_withMultiplePatterns_returnsTrueIfAnyMatches() throws IOException {
        // Given: Only one pattern matches
        createFile("pom.xml", "<project />");

        // When: hasAnyFiles is called with multiple patterns
        boolean result = scanner.hasAnyFiles(context, "**/*.java", "pom.xml", "**/*.kt");

        // Then: Should return true (pom.xml matches)
        assertThat(result).isTrue();
    }

    @Test
    void hasAnyFiles_withNoMatchingFiles_returnsFalse() throws IOException {
        // Given: No matching files
        createDirectory("src/main/resources");

        // When: hasAnyFiles is called
        boolean result = scanner.hasAnyFiles(context, "**/*.java");

        // Then: Should return false
        assertThat(result).isFalse();
    }

    // ==================== ScanResult Creation Helpers ====================

    @Test
    void emptyResult_returnsEmptySuccessfulResult() {
        // When: emptyResult is called
        ScanResult result = scanner.emptyResult();

        // Then: Should return empty successful result
        assertThat(result.success()).isTrue();
        assertThat(result.scannerId()).isEqualTo("test-scanner");
        assertThat(result.components()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
        assertThat(result.apiEndpoints()).isEmpty();
        assertThat(result.messageFlows()).isEmpty();
        assertThat(result.dataEntities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void failedResult_returnsFailedResultWithErrors() {
        // Given: Error messages
        List<String> errors = List.of("Error 1", "Error 2");

        // When: failedResult is called
        ScanResult result = scanner.failedResult(errors);

        // Then: Should return failed result with errors
        assertThat(result.success()).isFalse();
        assertThat(result.scannerId()).isEqualTo("test-scanner");
        assertThat(result.errors()).containsExactly("Error 1", "Error 2");
        assertThat(result.components()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void buildSuccessResult_returnsSuccessfulResultWithData() {
        // Given: Scanner data
        List<Dependency> dependencies = List.of(
            new Dependency("app", "com.example", "library", "1.0", "compile", true)
        );

        // When: buildSuccessResult is called
        ScanResult result = scanner.buildSuccessResult(
            List.of(),      // components
            dependencies,
            List.of(),      // apiEndpoints
            List.of(),      // messageFlows
            List.of(),      // dataEntities
            List.of(),      // relationships
            List.of("Warning 1") // warnings
        );

        // Then: Should return successful result with data
        assertThat(result.success()).isTrue();
        assertThat(result.scannerId()).isEqualTo("test-scanner");
        assertThat(result.dependencies()).hasSize(1);
        assertThat(result.warnings()).containsExactly("Warning 1");
        assertThat(result.errors()).isEmpty();
    }

    // ==================== Logger Initialization ====================

    @Test
    void constructor_initializesLogger() {
        // Then: Logger should be initialized (not null)
        assertThat(scanner.getLog()).isNotNull();
    }

    // ==================== Test Scanner Implementation ====================

    /**
     * Concrete test implementation of AbstractScanner for testing base functionality.
     */
    private static class TestScanner extends AbstractScanner {

        @Override
        public String getId() {
            return "test-scanner";
        }

        @Override
        public String getDisplayName() {
            return "Test Scanner";
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("test");
        }

        @Override
        public Set<String> getSupportedFilePatterns() {
            return Set.of("**/*.test");
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public boolean appliesTo(ScanContext context) {
            return hasAnyFiles(context, "**/*.test");
        }

        @Override
        public ScanResult scan(ScanContext context) {
            return emptyResult();
        }

        // Expose protected methods for testing
        public org.slf4j.Logger getLog() {
            return log;
        }
    }
}
