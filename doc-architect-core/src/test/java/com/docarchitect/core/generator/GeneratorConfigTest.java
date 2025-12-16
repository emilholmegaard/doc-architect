package com.docarchitect.core.generator;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GeneratorConfig}.
 */
class GeneratorConfigTest {

    @Test
    void constructor_withValidInputs_createsConfig() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            Map.of("key", "value")
        );

        assertThat(config.theme()).isEqualTo("dark");
        assertThat(config.includeExternal()).isTrue();
        assertThat(config.maxDepth()).isEqualTo(5);
        assertThat(config.customSettings()).containsEntry("key", "value");
    }

    @Test
    void constructor_withNullTheme_acceptsNull() {
        GeneratorConfig config = new GeneratorConfig(
            null,
            false,
            3,
            Map.of()
        );

        assertThat(config.theme()).isNull();
    }

    @Test
    void constructor_withNegativeMaxDepth_setsMaxValue() {
        GeneratorConfig config = new GeneratorConfig(
            "light",
            true,
            -1,
            Map.of()
        );

        assertThat(config.maxDepth()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void constructor_withNullCustomSettings_setsEmptyMap() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            false,
            10,
            null
        );

        assertThat(config.customSettings()).isEmpty();
    }

    @Test
    void defaults_createsDefaultConfiguration() {
        GeneratorConfig config = GeneratorConfig.defaults();

        assertThat(config.theme()).isNull();
        assertThat(config.includeExternal()).isTrue();
        assertThat(config.maxDepth()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.customSettings()).isEmpty();
    }

    @Test
    void getSetting_withExistingKey_returnsValue() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            Map.of("fontSize", 14, "layout", "vertical")
        );

        Integer fontSize = config.getSetting("fontSize");
        String layout = config.getSetting("layout");

        assertThat(fontSize).isEqualTo(14);
        assertThat(layout).isEqualTo("vertical");
    }

    @Test
    void getSetting_withNonExistentKey_returnsNull() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            Map.of()
        );

        String value = config.getSetting("nonExistent");

        assertThat(value).isNull();
    }

    @Test
    void getSettingOrDefault_withExistingKey_returnsValue() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            Map.of("fontSize", 14)
        );

        Integer fontSize = config.getSettingOrDefault("fontSize", 12);

        assertThat(fontSize).isEqualTo(14);
    }

    @Test
    void getSettingOrDefault_withNonExistentKey_returnsDefault() {
        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            Map.of()
        );

        Integer fontSize = config.getSettingOrDefault("fontSize", 12);

        assertThat(fontSize).isEqualTo(12);
    }

    @Test
    void getSettingOrDefault_withNullValue_returnsDefault() {
        Map<String, Object> settings = new java.util.HashMap<>();
        settings.put("fontSize", null);

        GeneratorConfig config = new GeneratorConfig(
            "dark",
            true,
            5,
            settings
        );

        Integer fontSize = config.getSettingOrDefault("fontSize", 12);

        assertThat(fontSize).isEqualTo(12);
    }

    @Test
    void equals_withSameValues_returnsTrue() {
        GeneratorConfig config1 = new GeneratorConfig("dark", true, 5, Map.of("key", "value"));
        GeneratorConfig config2 = new GeneratorConfig("dark", true, 5, Map.of("key", "value"));

        assertThat(config1).isEqualTo(config2);
    }

    @Test
    void hashCode_withSameValues_returnsSameHash() {
        GeneratorConfig config1 = new GeneratorConfig("dark", true, 5, Map.of("key", "value"));
        GeneratorConfig config2 = new GeneratorConfig("dark", true, 5, Map.of("key", "value"));

        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }
}
