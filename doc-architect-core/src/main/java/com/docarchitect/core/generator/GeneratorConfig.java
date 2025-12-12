package com.docarchitect.core.generator;

import java.util.Map;
import java.util.Objects;

/**
 * Configuration for diagram generation.
 *
 * @param theme theme or style to apply
 * @param includeExternal whether to include external components
 * @param maxDepth maximum relationship depth to display
 * @param customSettings generator-specific custom settings
 */
public record GeneratorConfig(
    String theme,
    boolean includeExternal,
    int maxDepth,
    Map<String, Object> customSettings
) {
    /**
     * Compact constructor with validation.
     */
    public GeneratorConfig {
        if (maxDepth < 0) {
            maxDepth = Integer.MAX_VALUE;
        }
        if (customSettings == null) {
            customSettings = Map.of();
        }
    }

    /**
     * Creates a default configuration.
     *
     * @return default generator config
     */
    public static GeneratorConfig defaults() {
        return new GeneratorConfig(null, true, Integer.MAX_VALUE, Map.of());
    }

    /**
     * Gets a custom setting value.
     *
     * @param key setting key
     * @param <T> expected type
     * @return setting value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getSetting(String key) {
        return (T) customSettings.get(key);
    }

    /**
     * Gets a custom setting with a default.
     *
     * @param key setting key
     * @param defaultValue default value
     * @param <T> expected type
     * @return setting value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getSettingOrDefault(String key, T defaultValue) {
        T value = (T) customSettings.get(key);
        return value != null ? value : defaultValue;
    }
}
