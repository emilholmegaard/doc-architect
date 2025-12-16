package com.docarchitect.core.renderer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GeneratedOutput}.
 */
class GeneratedOutputTest {

    @Test
    void constructor_withValidInputs_createsOutput() {
        List<GeneratedFile> files = List.of(
            new GeneratedFile("file1.md", "content1", "markdown"),
            new GeneratedFile("file2.md", "content2", "markdown")
        );

        GeneratedOutput output = new GeneratedOutput(files);

        assertThat(output.files()).hasSize(2);
        assertThat(output.files()).containsExactlyElementsOf(files);
    }

    @Test
    void constructor_withEmptyList_createsEmptyOutput() {
        GeneratedOutput output = new GeneratedOutput(List.of());

        assertThat(output.files()).isEmpty();
    }

    @Test
    void constructor_withNullFiles_throwsException() {
        assertThatThrownBy(() -> new GeneratedOutput(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("files must not be null");
    }

    @Test
    void constructor_createsImmutableCopy() {
        List<GeneratedFile> mutableList = new ArrayList<>();
        mutableList.add(new GeneratedFile("file1.md", "content", "markdown"));

        GeneratedOutput output = new GeneratedOutput(mutableList);

        // Modify original list
        mutableList.add(new GeneratedFile("file2.md", "content2", "markdown"));

        // Output should not be affected
        assertThat(output.files()).hasSize(1);
    }

    @Test
    void files_returnsImmutableList() {
        GeneratedOutput output = new GeneratedOutput(
            List.of(new GeneratedFile("file.md", "content", "markdown"))
        );

        assertThatThrownBy(() -> output.files().add(
            new GeneratedFile("new.md", "new content", "markdown")
        ))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        List<GeneratedFile> files = List.of(
            new GeneratedFile("file.md", "content", "markdown")
        );

        GeneratedOutput output1 = new GeneratedOutput(files);
        GeneratedOutput output2 = new GeneratedOutput(files);

        assertThat(output1).isEqualTo(output2);
    }

    @Test
    void hashCode_withSameValues_returnsSameHash() {
        List<GeneratedFile> files = List.of(
            new GeneratedFile("file.md", "content", "markdown")
        );

        GeneratedOutput output1 = new GeneratedOutput(files);
        GeneratedOutput output2 = new GeneratedOutput(files);

        assertThat(output1.hashCode()).isEqualTo(output2.hashCode());
    }
}
