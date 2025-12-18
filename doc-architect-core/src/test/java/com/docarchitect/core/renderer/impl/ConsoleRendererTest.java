package com.docarchitect.core.renderer.impl;

import com.docarchitect.core.renderer.GeneratedFile;
import com.docarchitect.core.renderer.GeneratedOutput;
import com.docarchitect.core.renderer.RenderContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ConsoleRenderer}.
 */
class ConsoleRendererTest {

    private ConsoleRenderer renderer;
    private final PrintStream originalOut = System.out;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        renderer = new ConsoleRenderer();
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void getId_returnsConsole() {
        assertThat(renderer.getId()).isEqualTo("console");
    }

    @Test
    void render_withSingleFile_printsFileContent() {
        // Given
        String content = "# Test Document\n\nThis is a test.";
        GeneratedFile file = new GeneratedFile("test.md", content, "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("Generated 1 file(s)");
        assertThat(consoleOutput).contains("File 1/1: test.md");
        assertThat(consoleOutput).contains("Type: text/markdown");
        assertThat(consoleOutput).contains(content);
    }

    @Test
    void render_withMultipleFiles_printsAllFiles() {
        // Given
        GeneratedFile file1 = new GeneratedFile("doc1.md", "Content 1", "text/markdown");
        GeneratedFile file2 = new GeneratedFile("doc2.md", "Content 2", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file1, file2));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("Generated 2 file(s)");
        assertThat(consoleOutput).contains("File 1/2: doc1.md");
        assertThat(consoleOutput).contains("File 2/2: doc2.md");
        assertThat(consoleOutput).contains("Content 1");
        assertThat(consoleOutput).contains("Content 2");
    }

    @Test
    void render_withColorsEnabled_includesAnsiCodes() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of("console.colors", "true"));

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("\u001B["); // Contains ANSI escape codes
    }

    @Test
    void render_withColorsDisabled_excludesAnsiCodes() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of("console.colors", "false"));

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).doesNotContain("\u001B["); // No ANSI escape codes
        assertThat(consoleOutput).contains("Generated 1 file(s)");
        assertThat(consoleOutput).contains("Content");
    }

    @Test
    void render_withCustomSeparator_usesCustomSeparator() {
        // Given
        GeneratedFile file1 = new GeneratedFile("doc1.md", "Content 1", "text/markdown");
        GeneratedFile file2 = new GeneratedFile("doc2.md", "Content 2", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file1, file2));
        RenderContext context = new RenderContext("./output", Map.of("console.separator", "==="));

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("===");
        assertThat(consoleOutput).doesNotContain("---");
    }

    @Test
    void render_withHeadersDisabled_excludesHeaders() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of("console.showHeaders", "false"));

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("Generated 1 file(s)"); // Summary still shown
        assertThat(consoleOutput).doesNotContain("File 1/1:"); // Headers not shown
        assertThat(consoleOutput).doesNotContain("Type:");
        assertThat(consoleOutput).contains("Content"); // Content still shown
    }

    @Test
    void render_withEmptyFileList_printsSummaryOnly() {
        // Given
        GeneratedOutput output = new GeneratedOutput(List.of());
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("Generated 0 file(s)");
    }

    @Test
    void render_withNullContentType_excludesTypeInHeader() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", null);
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("File 1/1: test.md");
        assertThat(consoleOutput).doesNotContain("Type:");
        assertThat(consoleOutput).contains("Size:");
    }

    @Test
    void render_withEmptyContentType_excludesTypeInHeader() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("File 1/1: test.md");
        assertThat(consoleOutput).doesNotContain("Type:");
        assertThat(consoleOutput).contains("Size:");
    }

    @Test
    void render_withLargeContent_printsCompleteContent() {
        // Given
        String largeContent = "X".repeat(10_000);
        GeneratedFile file = new GeneratedFile("large.txt", largeContent, "text/plain");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains(largeContent);
        assertThat(consoleOutput).contains("Size: 10000 bytes");
    }

    @Test
    void render_withMultilineContent_preservesFormatting() {
        // Given
        String content = "Line 1\nLine 2\nLine 3";
        GeneratedFile file = new GeneratedFile("test.txt", content, "text/plain");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("Line 1\nLine 2\nLine 3");
    }

    @Test
    void render_withDefaultSettings_usesDefaults() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of());

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).contains("\u001B["); // Colors enabled by default
        assertThat(consoleOutput).contains("---"); // Default separator
        assertThat(consoleOutput).contains("File 1/1:"); // Headers enabled by default
    }

    @Test
    void render_withAllCustomSettings_appliesAllSettings() {
        // Given
        GeneratedFile file = new GeneratedFile("test.md", "Content", "text/markdown");
        GeneratedOutput output = new GeneratedOutput(List.of(file));
        RenderContext context = new RenderContext("./output", Map.of(
                "console.colors", "false",
                "console.separator", "***",
                "console.showHeaders", "false"
        ));

        // When
        renderer.render(output, context);

        // Then
        String consoleOutput = outputStream.toString();
        assertThat(consoleOutput).doesNotContain("\u001B["); // No colors
        assertThat(consoleOutput).contains("***"); // Custom separator
        assertThat(consoleOutput).doesNotContain("File 1/1:"); // No headers
        assertThat(consoleOutput).contains("Content");
    }
}
