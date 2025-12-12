package com.docarchitect.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FileUtils}.
 */
class FileUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void findFiles_withMatchingPattern_returnsFiles() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("pom.xml");
        Path file2 = tempDir.resolve("subdir/pom.xml");
        Files.createDirectories(file2.getParent());
        Files.writeString(file1, "test");
        Files.writeString(file2, "test");

        List<Path> files = FileUtils.findFiles(tempDir, "**/pom.xml");

        assertThat(files)
            .hasSizeGreaterThanOrEqualTo(1)
            .anySatisfy(p -> assertThat(p.getFileName().toString()).isEqualTo("pom.xml"));
    }

    @Test
    void findFiles_withNoMatches_returnsEmptyList() throws IOException {
        List<Path> files = FileUtils.findFiles(tempDir, "**/*.java");

        assertThat(files).isEmpty();
    }

    @Test
    void findFiles_withWildcardPattern_returnsMatchingFiles() throws IOException {
        Path javaFile = tempDir.resolve("Test.java");
        Path kotlinFile = tempDir.resolve("Test.kt");
        Path txtFile = tempDir.resolve("readme.txt");
        Files.writeString(javaFile, "test");
        Files.writeString(kotlinFile, "test");
        Files.writeString(txtFile, "test");

        List<Path> files = FileUtils.findFiles(tempDir, "*.java");

        assertThat(files)
            .hasSize(1)
            .contains(javaFile);
    }

    @Test
    void readString_withExistingFile_returnsContent() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String content = "Hello, World!";
        Files.writeString(file, content);

        String result = FileUtils.readString(file);

        assertThat(result).isEqualTo(content);
    }

    @Test
    void readLines_withExistingFile_returnsLines() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3");

        List<String> lines = FileUtils.readLines(file);

        assertThat(lines).containsExactly("line1", "line2", "line3");
    }

    @Test
    void exists_withExistingFile_returnsTrue() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test");

        assertThat(FileUtils.exists(file)).isTrue();
    }

    @Test
    void exists_withNonExistingFile_returnsFalse() {
        Path file = tempDir.resolve("nonexistent.txt");

        assertThat(FileUtils.exists(file)).isFalse();
    }

    @Test
    void isDirectory_withDirectory_returnsTrue() {
        assertThat(FileUtils.isDirectory(tempDir)).isTrue();
    }

    @Test
    void isDirectory_withFile_returnsFalse() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test");

        assertThat(FileUtils.isDirectory(file)).isFalse();
    }

    @Test
    void getExtension_withExtension_returnsExtension() {
        Path file = Path.of("test.java");

        assertThat(FileUtils.getExtension(file)).isEqualTo("java");
    }

    @Test
    void getExtension_withMultipleDots_returnsLastExtension() {
        Path file = Path.of("test.tar.gz");

        assertThat(FileUtils.getExtension(file)).isEqualTo("gz");
    }

    @Test
    void getExtension_withNoExtension_returnsEmptyString() {
        Path file = Path.of("test");

        assertThat(FileUtils.getExtension(file)).isEmpty();
    }

    @Test
    void getExtension_withDotFile_returnsExtension() {
        Path file = Path.of(".gitignore");

        assertThat(FileUtils.getExtension(file)).isEmpty();
    }
}
