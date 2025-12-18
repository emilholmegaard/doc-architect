package com.docarchitect.core.renderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base class for renderer functional tests.
 *
 * <p>Provides common test infrastructure including:
 * <ul>
 *   <li>Temporary directory creation for test outputs</li>
 *   <li>Helper methods for creating test files and contexts</li>
 *   <li>RenderContext creation with sensible defaults</li>
 *   <li>Assertion helpers for validating rendered outputs</li>
 * </ul>
 *
 * <p>Subclasses should focus on creating test fixtures and validating
 * renderer-specific behavior without boilerplate setup code.
 *
 * @since 1.0.0
 */
public abstract class RendererTestBase {

    @TempDir
    protected Path tempDir;

    protected RenderContext context;

    @BeforeEach
    void setUp() {
        // Create default render context for tests
        context = new RenderContext(
            tempDir.toString(),
            Map.of()
        );
    }

    /**
     * Creates a GeneratedFile for testing.
     *
     * @param relativePath relative path for the file
     * @param content file content
     * @param contentType content type
     * @return GeneratedFile instance
     */
    protected GeneratedFile createGeneratedFile(String relativePath, String content, String contentType) {
        return new GeneratedFile(relativePath, content, contentType);
    }

    /**
     * Creates a GeneratedOutput with multiple files.
     *
     * @param files list of generated files
     * @return GeneratedOutput instance
     */
    protected GeneratedOutput createGeneratedOutput(List<GeneratedFile> files) {
        return new GeneratedOutput(files);
    }

    /**
     * Creates a GeneratedOutput with a single file.
     *
     * @param relativePath relative path for the file
     * @param content file content
     * @param contentType content type
     * @return GeneratedOutput instance
     */
    protected GeneratedOutput createGeneratedOutput(String relativePath, String content, String contentType) {
        return new GeneratedOutput(List.of(
            new GeneratedFile(relativePath, content, contentType)
        ));
    }

    /**
     * Creates a RenderContext with custom settings.
     *
     * @param outputDirectory output directory path
     * @param settings custom settings map
     * @return RenderContext instance
     */
    protected RenderContext createContext(String outputDirectory, Map<String, String> settings) {
        return new RenderContext(outputDirectory, settings);
    }

    /**
     * Reads a file from the temp directory.
     *
     * @param relativePath path relative to tempDir
     * @return file content
     * @throws IOException if file cannot be read
     */
    protected String readFile(String relativePath) throws IOException {
        Path filePath = tempDir.resolve(relativePath);
        return Files.readString(filePath);
    }

    /**
     * Checks if a file exists in the temp directory.
     *
     * @param relativePath path relative to tempDir
     * @return true if file exists
     */
    protected boolean fileExists(String relativePath) {
        return Files.exists(tempDir.resolve(relativePath));
    }

    /**
     * Creates a directory in the temp directory.
     *
     * @param relativePath path relative to tempDir
     * @return the created directory path
     * @throws IOException if directory cannot be created
     */
    protected Path createDirectory(String relativePath) throws IOException {
        Path dirPath = tempDir.resolve(relativePath);
        Files.createDirectories(dirPath);
        return dirPath;
    }
}
