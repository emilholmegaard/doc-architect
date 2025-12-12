package com.docarchitect.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link IdGenerator}.
 */
class IdGeneratorTest {

    @Test
    void generate_withSingleComponent_returnsDeterministicId() {
        String id1 = IdGenerator.generate("user-service");
        String id2 = IdGenerator.generate("user-service");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).hasSize(16);
    }

    @Test
    void generate_withMultipleComponents_returnsDeterministicId() {
        String id1 = IdGenerator.generate("user-service", "v1.0.0", "api");
        String id2 = IdGenerator.generate("user-service", "v1.0.0", "api");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).hasSize(16);
    }

    @Test
    void generate_withDifferentInputs_returnsDifferentIds() {
        String id1 = IdGenerator.generate("user-service");
        String id2 = IdGenerator.generate("order-service");

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void generate_withNullComponents_throwsException() {
        assertThatThrownBy(() -> IdGenerator.generate((String[]) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one component required");
    }

    @Test
    void generate_withEmptyArray_throwsException() {
        assertThatThrownBy(() -> IdGenerator.generate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one component required");
    }

    @Test
    void generateFromString_withValidInput_returnsDeterministicId() {
        String id1 = IdGenerator.generateFromString("test-input");
        String id2 = IdGenerator.generateFromString("test-input");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1).hasSize(16);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    void generateFromString_withInvalidInput_throwsException(String input) {
        assertThatThrownBy(() -> IdGenerator.generateFromString(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Input must not be null or blank");
    }

    @Test
    void generateFullHash_withValidInput_returns64CharacterHash() {
        String hash = IdGenerator.generateFullHash("test-input");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void generateFullHash_isDeterministic() {
        String hash1 = IdGenerator.generateFullHash("test-input");
        String hash2 = IdGenerator.generateFullHash("test-input");

        assertThat(hash1).isEqualTo(hash2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    void generateFullHash_withInvalidInput_throwsException(String input) {
        assertThatThrownBy(() -> IdGenerator.generateFullHash(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Input must not be null or blank");
    }
}
