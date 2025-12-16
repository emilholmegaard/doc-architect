package com.docarchitect.core.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GeneratedDiagram}.
 */
class GeneratedDiagramTest {

    @Test
    void constructor_withValidInputs_createsDiagram() {
        GeneratedDiagram diagram = new GeneratedDiagram(
            "dependency-graph",
            "graph TD\n  A --> B",
            "md"
        );

        assertThat(diagram.name()).isEqualTo("dependency-graph");
        assertThat(diagram.content()).isEqualTo("graph TD\n  A --> B");
        assertThat(diagram.fileExtension()).isEqualTo("md");
    }

    @Test
    void constructor_withNullName_throwsException() {
        assertThatThrownBy(() -> new GeneratedDiagram(
            null,
            "content",
            "md"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name must not be null");
    }

    @Test
    void constructor_withNullContent_throwsException() {
        assertThatThrownBy(() -> new GeneratedDiagram(
            "diagram",
            null,
            "md"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("content must not be null");
    }

    @Test
    void constructor_withNullFileExtension_throwsException() {
        assertThatThrownBy(() -> new GeneratedDiagram(
            "diagram",
            "content",
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fileExtension must not be null");
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        GeneratedDiagram diagram1 = new GeneratedDiagram("name", "content", "md");
        GeneratedDiagram diagram2 = new GeneratedDiagram("name", "content", "md");

        assertThat(diagram1).isEqualTo(diagram2);
    }

    @Test
    void hashCode_withSameValues_returnsSameHash() {
        GeneratedDiagram diagram1 = new GeneratedDiagram("name", "content", "md");
        GeneratedDiagram diagram2 = new GeneratedDiagram("name", "content", "md");

        assertThat(diagram1.hashCode()).isEqualTo(diagram2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        GeneratedDiagram diagram = new GeneratedDiagram("test-diagram", "test content", "puml");

        String result = diagram.toString();

        assertThat(result).contains("test-diagram");
        assertThat(result).contains("test content");
        assertThat(result).contains("puml");
    }
}
