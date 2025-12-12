package com.docarchitect.core.renderer;

import java.util.Map;
import java.util.Objects;

/**
 * Context provided to renderers during execution.
 *
 * @param outputDirectory target output directory path
 * @param settings renderer-specific settings
 */
public record RenderContext(
    String outputDirectory,
    Map<String, String> settings
) {
    /**
     * Compact constructor with validation.
     */
    public RenderContext {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        if (settings == null) {
            settings = Map.of();
        }
    }

    /**
     * Gets a setting value.
     *
     * @param key setting key
     * @return setting value or null
     */
    public String getSetting(String key) {
        return settings.get(key);
    }

    /**
     * Gets a setting with a default.
     *
     * @param key setting key
     * @param defaultValue default value
     * @return setting value or default
     */
    public String getSettingOrDefault(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
}
