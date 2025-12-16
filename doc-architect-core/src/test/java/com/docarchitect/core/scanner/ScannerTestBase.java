package com.docarchitect.core.scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base class for scanner functional tests.
 *
 * <p>Provides common test infrastructure including:
 * <ul>
 *   <li>Temporary directory creation for test projects</li>
 *   <li>Helper methods for creating test files</li>
 *   <li>ScanContext creation with sensible defaults</li>
 *   <li>Assertion helpers for validating scan results</li>
 * </ul>
 *
 * <p>Subclasses should focus on creating test fixtures and validating
 * scanner-specific behavior without boilerplate setup code.
 *
 * @since 1.0.0
 */
public abstract class ScannerTestBase {

    @TempDir
    protected Path tempDir;

    protected ScanContext context;

    @BeforeEach
    void setUp() {
        // Create default scan context for tests
        context = new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of(),
            Map.of(),
            Map.of()
        );
    }

    /**
     * Creates a file in the temp directory with the given content.
     *
     * @param relativePath path relative to tempDir (e.g., "pom.xml" or "src/main/User.java")
     * @param content file content
     * @return the created file path
     * @throws IOException if file cannot be created
     */
    protected Path createFile(String relativePath, String content) throws IOException {
        Path filePath = tempDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return filePath;
    }

    /**
     * Creates a directory in the temp directory.
     *
     * @param relativePath path relative to tempDir (e.g., "src/main/java")
     * @return the created directory path
     * @throws IOException if directory cannot be created
     */
    protected Path createDirectory(String relativePath) throws IOException {
        Path dirPath = tempDir.resolve(relativePath);
        Files.createDirectories(dirPath);
        return dirPath;
    }

    /**
     * Creates multiple files from a map of relative paths to content.
     *
     * @param files map of relative path → content
     * @throws IOException if any file cannot be created
     */
    protected void createFiles(Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            createFile(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates a new ScanContext with custom configuration.
     *
     * @param configuration custom configuration map
     * @return new ScanContext instance
     */
    protected ScanContext createContext(Map<String, Object> configuration) {
        return new ScanContext(
            tempDir,
            List.of(tempDir),
            configuration,
            Map.of(),
            Map.of()
        );
    }

    /**
     * Creates a new ScanContext with custom source paths.
     *
     * @param sourcePaths list of source paths to scan
     * @return new ScanContext instance
     */
    protected ScanContext createContext(List<Path> sourcePaths) {
        return new ScanContext(
            tempDir,
            sourcePaths,
            Map.of(),
            Map.of(),
            Map.of()
        );
    }
}
