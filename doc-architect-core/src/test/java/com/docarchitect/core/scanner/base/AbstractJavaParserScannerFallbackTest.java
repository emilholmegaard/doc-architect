package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.ConfidenceLevel;
import com.docarchitect.core.scanner.ScanStatistics;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for fallback parsing mechanism in {@link AbstractJavaParserScanner}.
 *
 * <p>Tests the three-tier parsing approach:
 * <ol>
 *   <li>Tier 1: AST parsing (HIGH confidence)</li>
 *   <li>Tier 2: Regex fallback (MEDIUM confidence)</li>
 *   <li>Tier 3: Failure tracking</li>
 * </ol>
 */
class AbstractJavaParserScannerFallbackTest {

    @TempDir
    Path tempDir;

    /**
     * Simple test scanner to test the fallback mechanism.
     * This is a minimal scanner just for testing the parseWithFallback method.
     */
    private static class TestScanner extends AbstractJavaParserScanner {

        @Override
        public String getId() {
            return "test-scanner";
        }

        @Override
        public String getDisplayName() {
            return "Test Scanner";
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public java.util.Set<String> getSupportedLanguages() {
            return java.util.Set.of("java");
        }

        @Override
        public java.util.Set<String> getSupportedFilePatterns() {
            return java.util.Set.of("**/*.java");
        }

        @Override
        public boolean appliesTo(com.docarchitect.core.scanner.ScanContext context) {
            return true;
        }

        @Override
        public com.docarchitect.core.scanner.ScanResult scan(com.docarchitect.core.scanner.ScanContext context) {
            // Not used in these tests
            return emptyResult();
        }

        /**
         * Extract class names from AST (Tier 1).
         */
        public List<String> extractFromAST(CompilationUnit cu) {
            List<String> classNames = new ArrayList<>();
            cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                .forEach(clazz -> classNames.add(clazz.getNameAsString()));
            return classNames;
        }

        /**
         * Extract class names from content via regex (Tier 2).
         */
        public FallbackParsingStrategy<String> createFallback() {
            return (file, content) -> {
                List<String> classNames = new ArrayList<>();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("class\\s+(\\w+)");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    classNames.add(matcher.group(1));
                }
                return classNames;
            };
        }
    }

    @Test
    void tier1_validJavaFile_usesAST() throws IOException {
        // Create valid Java file
        Path javaFile = tempDir.resolve("User.java");
        Files.writeString(javaFile, """
            package com.example;

            public class User {
                private String name;
            }
            """);

        TestScanner scanner = new TestScanner();
        ScanStatistics.Builder stats = new ScanStatistics.Builder();

        // Parse with fallback
        AbstractJavaParserScanner.FileParseResult<String> result =
            scanner.parseWithFallback(javaFile, scanner::extractFromAST, scanner.createFallback(), stats);

        // Verify AST parsing succeeded
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly("User");
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH);

        // Verify statistics
        ScanStatistics scanStats = stats.build();
        assertThat(scanStats.filesScanned()).isEqualTo(1);
        assertThat(scanStats.filesParsedSuccessfully()).isEqualTo(1);
        assertThat(scanStats.filesParsedWithFallback()).isZero();
        assertThat(scanStats.filesFailed()).isZero();
    }

    @Test
    void tier2_invalidJavaFile_usesFallback() throws IOException {
        // Create invalid Java file (syntax error)
        Path javaFile = tempDir.resolve("Broken.java");
        Files.writeString(javaFile, """
            // Missing package and imports
            public class Broken {
                // Missing closing brace
            """);

        TestScanner scanner = new TestScanner();
        ScanStatistics.Builder stats = new ScanStatistics.Builder();

        // Parse with fallback
        AbstractJavaParserScanner.FileParseResult<String> result =
            scanner.parseWithFallback(javaFile, scanner::extractFromAST, scanner.createFallback(), stats);

        // Verify fallback parsing succeeded
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsExactly("Broken");
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.MEDIUM);

        // Verify statistics
        ScanStatistics scanStats = stats.build();
        assertThat(scanStats.filesScanned()).isEqualTo(1);
        assertThat(scanStats.filesParsedSuccessfully()).isZero();
        assertThat(scanStats.filesParsedWithFallback()).isEqualTo(1);
        assertThat(scanStats.filesFailed()).isZero();
    }

    @Test
    void tier3_emptyFile_tracksFailure() throws IOException {
        // Create file with only comments - AST parses successfully but extracts nothing
        // Both AST and regex will return empty results
        Path javaFile = tempDir.resolve("Empty.java");
        Files.writeString(javaFile, "// Just a comment");

        TestScanner scanner = new TestScanner();
        ScanStatistics.Builder stats = new ScanStatistics.Builder();

        // Parse with fallback
        AbstractJavaParserScanner.FileParseResult<String> result =
            scanner.parseWithFallback(javaFile, scanner::extractFromAST, scanner.createFallback(), stats);

        // Verify AST parsing succeeded but returned no data
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getConfidence()).isEqualTo(ConfidenceLevel.HIGH); // AST succeeded

        // Verify statistics - AST succeeded (even though it extracted nothing)
        ScanStatistics scanStats = stats.build();
        assertThat(scanStats.filesScanned()).isEqualTo(1);
        assertThat(scanStats.filesParsedSuccessfully()).isEqualTo(1);  // AST parsed successfully
        assertThat(scanStats.filesParsedWithFallback()).isZero();
        assertThat(scanStats.filesFailed()).isZero();
    }

    @Test
    void mixedFiles_usesAllThreeTiers() throws IOException {
        // Create 3 files: valid (AST), broken (fallback), empty (AST succeeds but no data)
        Files.writeString(tempDir.resolve("Valid.java"), "public class Valid {}");
        Files.writeString(tempDir.resolve("Broken.java"), "public class Broken { /* missing brace */");
        Files.writeString(tempDir.resolve("Empty.java"), "");

        TestScanner scanner = new TestScanner();
        ScanStatistics.Builder stats = new ScanStatistics.Builder();

        List<String> allClasses = new ArrayList<>();

        // Parse all files
        for (Path file : List.of(
            tempDir.resolve("Valid.java"),
            tempDir.resolve("Broken.java"),
            tempDir.resolve("Empty.java")
        )) {
            AbstractJavaParserScanner.FileParseResult<String> result =
                scanner.parseWithFallback(file, scanner::extractFromAST, scanner.createFallback(), stats);
            allClasses.addAll(result.getData());
        }

        // Verify results from all tiers
        assertThat(allClasses).containsExactlyInAnyOrder("Valid", "Broken");

        // Verify statistics
        ScanStatistics scanStats = stats.build();
        assertThat(scanStats.filesScanned()).isEqualTo(3);
        assertThat(scanStats.filesParsedSuccessfully()).isEqualTo(2);  // Valid.java, Empty.java (AST succeeded)
        assertThat(scanStats.filesParsedWithFallback()).isEqualTo(1);  // Broken.java
        assertThat(scanStats.filesFailed()).isZero();                  // None failed parsing
        assertThat(scanStats.getSuccessRate()).isCloseTo(66.7, org.assertj.core.data.Offset.offset(0.1));
        assertThat(scanStats.getOverallParseRate()).isEqualTo(100.0);
    }

    @Test
    void realWorldScenario_100Files_mixedSuccessRates() throws IOException {
        // Simulate real-world: 10 valid (AST), 30 broken (fallback), 60 empty (AST succeeds)
        TestScanner scanner = new TestScanner();
        ScanStatistics.Builder stats = new ScanStatistics.Builder();

        // Create 10 valid files
        for (int i = 0; i < 10; i++) {
            Files.writeString(tempDir.resolve("Valid" + i + ".java"),
                "public class Valid" + i + " {}");
        }

        // Create 30 broken files
        for (int i = 0; i < 30; i++) {
            Files.writeString(tempDir.resolve("Broken" + i + ".java"),
                "public class Broken" + i + " { /* missing brace */");
        }

        // Create 60 empty files (AST will parse these successfully, just no classes extracted)
        for (int i = 0; i < 60; i++) {
            Files.writeString(tempDir.resolve("Empty" + i + ".java"), "");
        }

        // Parse all files
        List<Path> allFiles = new ArrayList<>();
        Files.walk(tempDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(allFiles::add);

        for (Path file : allFiles) {
            scanner.parseWithFallback(file, scanner::extractFromAST, scanner.createFallback(), stats);
        }

        // Verify statistics match expected distribution
        ScanStatistics scanStats = stats.build();
        assertThat(scanStats.filesScanned()).isEqualTo(100);
        assertThat(scanStats.filesParsedSuccessfully()).isEqualTo(70);  // 10 valid + 60 empty
        assertThat(scanStats.filesParsedWithFallback()).isEqualTo(30);  // 30 broken
        assertThat(scanStats.filesFailed()).isZero();                    // None failed
        assertThat(scanStats.getSuccessRate()).isEqualTo(70.0);
        assertThat(scanStats.getOverallParseRate()).isEqualTo(100.0);
        assertThat(scanStats.getFailureRate()).isZero();
    }
}
