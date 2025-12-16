package com.docarchitect.core.renderer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GeneratedFile}.
 */
class GeneratedFileTest {

    @Test
    void constructor_withValidInputs_createsFile() {
        GeneratedFile file = new GeneratedFile(
            "architecture/diagram.md",
            "# Architecture Diagram\n\nContent here",
            "markdown"
        );

        assertThat(file.relativePath()).isEqualTo("architecture/diagram.md");
        assertThat(file.content()).isEqualTo("# Architecture Diagram\n\nContent here");
        assertThat(file.contentType()).isEqualTo("markdown");
    }

    @Test
    void constructor_withNullRelativePath_throwsException() {
        assertThatThrownBy(() -> new GeneratedFile(
            null,
            "content",
            "markdown"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("relativePath must not be null");
    }

    @Test
    void constructor_withNullContent_throwsException() {
        assertThatThrownBy(() -> new GeneratedFile(
            "path/to/file.md",
            null,
            "markdown"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("content must not be null");
    }

    @Test
    void constructor_withNullContentType_acceptsNull() {
        GeneratedFile file = new GeneratedFile(
            "diagram.puml",
            "@startuml\nA --> B\n@enduml",
            null
        );

        assertThat(file.contentType()).isNull();
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        GeneratedFile file1 = new GeneratedFile("path", "content", "type");
        GeneratedFile file2 = new GeneratedFile("path", "content", "type");

        assertThat(file1).isEqualTo(file2);
    }

    @Test
    void hashCode_withSameValues_returnsSameHash() {
        GeneratedFile file1 = new GeneratedFile("path", "content", "type");
        GeneratedFile file2 = new GeneratedFile("path", "content", "type");

        assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        GeneratedFile file = new GeneratedFile("docs/api.md", "API content", "markdown");

        String result = file.toString();

        assertThat(result).contains("docs/api.md");
        assertThat(result).contains("API content");
        assertThat(result).contains("markdown");
    }
}
