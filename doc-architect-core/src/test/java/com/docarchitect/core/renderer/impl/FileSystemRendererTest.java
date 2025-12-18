package com.docarchitect.core.renderer.impl;

import com.docarchitect.core.renderer.GeneratedFile;
import com.docarchitect.core.renderer.GeneratedOutput;
import com.docarchitect.core.renderer.RenderContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FileSystemRenderer}.
 */
class FileSystemRendererTest {

    private FileSystemRenderer renderer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        renderer = new FileSystemRenderer();
    }

    @Test
    void getId_returnsFilesystem() {
        assertThat(renderer.getId()).isEqualTo("filesystem");
    }

    @Test
    void render_withSingleFile_writesFileToOutputDirectory() throws IOException {
        // Given
        String content = "# Test Document\n\nThis is a test.";
        GeneratedFile file = new GeneratedFile("test.md", content, "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        Path expectedFile = tempDir.resolve("test.md");
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).isEqualTo(content);
    }

    @Test
    void render_withMultipleFiles_writesAllFilesToOutputDirectory() throws IOException {
        // Given
        GeneratedFile file1 = new GeneratedFile("doc1.md", "Content 1", "text/markdown");
        GeneratedFile file2 = new GeneratedFile("doc2.md", "Content 2", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file1, file2));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        assertThat(tempDir.resolve("doc1.md")).exists();
        assertThat(tempDir.resolve("doc2.md")).exists();
        assertThat(Files.readString(tempDir.resolve("doc1.md"))).isEqualTo("Content 1");
        assertThat(Files.readString(tempDir.resolve("doc2.md"))).isEqualTo("Content 2");
    }

    @Test
    void render_withNestedPath_createsDirectoryStructure() throws IOException {
        // Given
        String relativePath = "diagrams/architecture/dependencies.md";
        GeneratedFile file = new GeneratedFile(relativePath, "# Dependencies", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        Path expectedFile = tempDir.resolve(relativePath);
        assertThat(expectedFile).exists();
        assertThat(expectedFile.getParent()).exists();
        assertThat(Files.readString(expectedFile)).isEqualTo("# Dependencies");
    }

    @Test
    void render_withExistingFile_overwritesFile() throws IOException {
        // Given
        Path existingFile = tempDir.resolve("test.md");
        Files.writeString(existingFile, "Old content");

        String newContent = "New content";
        GeneratedFile file = new GeneratedFile("test.md", newContent, "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        assertThat(Files.readString(existingFile)).isEqualTo(newContent);
    }

    @Test
    void render_withEmptyFileList_createsOutputDirectoryOnly() {
        // Given
        GeneratedOutput output = new GeneratedOutput(List.of());
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        assertThat(tempDir).exists();
        assertThat(tempDir).isDirectory();
    }

    @Test
    void render_withNonExistentOutputDirectory_createsDirectory() {
        // Given
        Path newDir = tempDir.resolve("new/nested/directory");
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(newDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        assertThat(newDir).exists();
        assertThat(newDir.resolve("test.md")).exists();
    }

    @Test
    void render_withMultipleNestedFiles_createsAllDirectories() throws IOException {
        // Given
        GeneratedFile file1 = new GeneratedFile("a/b/c/file1.md", "Content 1", "text/markdown");
        GeneratedFile file2 = new GeneratedFile("x/y/file2.md", "Content 2", "text/markdown");
        GeneratedFile file3 = new GeneratedFile("root.md", "Content 3", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file1, file2, file3));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        assertThat(tempDir.resolve("a/b/c/file1.md")).exists();
        assertThat(tempDir.resolve("x/y/file2.md")).exists();
        assertThat(tempDir.resolve("root.md")).exists();
        assertThat(Files.readString(tempDir.resolve("a/b/c/file1.md"))).isEqualTo("Content 1");
    }

    @Test
    void render_withLargeContent_writesCompleteContent() throws IOException {
        // Given
        String largeContent = "X".repeat(100_000);
        GeneratedFile file = new GeneratedFile("large.txt", largeContent, "text/plain");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        Path expectedFile = tempDir.resolve("large.txt");
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).hasSize(100_000);
    }

    @Test
    void render_withSpecialCharactersInPath_handlesCorrectly() throws IOException {
        // Given
        String relativePath = "docs/file with spaces.md";
        GeneratedFile file = new GeneratedFile(relativePath, "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        Path expectedFile = tempDir.resolve(relativePath);
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).isEqualTo("Content");
    }

    @Test
    void render_withEmptyContent_writesEmptyFile() throws IOException {
        // Given
        GeneratedFile file = new GeneratedFile("empty.md", "", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext(tempDir.toString(), Map.of());

        // When
        renderer.render(output, context);

        // Then
        Path expectedFile = tempDir.resolve("empty.md");
        assertThat(expectedFile).exists();
        assertThat(Files.readString(expectedFile)).isEmpty();
    }
}
