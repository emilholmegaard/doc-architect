package com.docarchitect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example test class to demonstrate test infrastructure.
 * Replace with actual tests when implementing features.
 */
@DisplayName("Example Test Suite")
class ExampleTest {

    @Test
    @DisplayName("Should pass basic assertion")
    void shouldPassBasicAssertion() {
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("Should demonstrate string assertion")
    void shouldDemonstrateStringAssertion() {
        String projectName = "DocArchitect";
        assertThat(projectName)
                .isNotEmpty()
                .hasSize(12)
                .startsWith("Doc")
                .endsWith("Architect");
    }
}
