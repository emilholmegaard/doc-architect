package com.docarchitect.core.renderer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RenderContext}.
 */
class RenderContextTest {

    @Test
    void constructor_withValidInputs_createsContext() {
        RenderContext context = new RenderContext(
            "/path/to/output",
            Map.of("format", "markdown", "includeIndex", "true")
        );

        assertThat(context.outputDirectory()).isEqualTo("/path/to/output");
        assertThat(context.settings()).containsEntry("format", "markdown");
        assertThat(context.settings()).containsEntry("includeIndex", "true");
    }

    @Test
    void constructor_withNullOutputDirectory_throwsException() {
        assertThatThrownBy(() -> new RenderContext(
            null,
            Map.of()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("outputDirectory must not be null");
    }

    @Test
    void constructor_withNullSettings_setsEmptyMap() {
        RenderContext context = new RenderContext("/output", null);

        assertThat(context.settings()).isEmpty();
    }

    @Test
    void getSetting_withExistingKey_returnsValue() {
        RenderContext context = new RenderContext(
            "/output",
            Map.of("format", "html", "theme", "dark")
        );

        String format = context.getSetting("format");
        String theme = context.getSetting("theme");

        assertThat(format).isEqualTo("html");
        assertThat(theme).isEqualTo("dark");
    }

    @Test
    void getSetting_withNonExistentKey_returnsNull() {
        RenderContext context = new RenderContext("/output", Map.of());

        String value = context.getSetting("nonExistent");

        assertThat(value).isNull();
    }

    @Test
    void getSettingOrDefault_withExistingKey_returnsValue() {
        RenderContext context = new RenderContext(
            "/output",
            Map.of("format", "markdown")
        );

        String format = context.getSettingOrDefault("format", "html");

        assertThat(format).isEqualTo("markdown");
    }

    @Test
    void getSettingOrDefault_withNonExistentKey_returnsDefault() {
        RenderContext context = new RenderContext("/output", Map.of());

        String format = context.getSettingOrDefault("format", "html");

        assertThat(format).isEqualTo("html");
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        RenderContext context1 = new RenderContext("/output", Map.of("key", "value"));
        RenderContext context2 = new RenderContext("/output", Map.of("key", "value"));

        assertThat(context1).isEqualTo(context2);
    }

    @Test
    void hashCode_withSameValues_returnsSameHash() {
        RenderContext context1 = new RenderContext("/output", Map.of("key", "value"));
        RenderContext context2 = new RenderContext("/output", Map.of("key", "value"));

        assertThat(context1.hashCode()).isEqualTo(context2.hashCode());
    }
}
