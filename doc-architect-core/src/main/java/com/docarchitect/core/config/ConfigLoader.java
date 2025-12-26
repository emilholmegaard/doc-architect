package com.docarchitect.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility for loading DocArchitect configuration from YAML files.
 *
 * <p>Uses Jackson to deserialize {@code docarchitect.yaml} into {@link ProjectConfig} records.
 * If the config file is missing or invalid, returns {@link ProjectConfig#defaults()}.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Path configPath = Paths.get("docarchitect.yaml");
 * ProjectConfig config = ConfigLoader.load(configPath);
 *
 * if (config.scanners().isEnabled("maven-dependencies")) {
 *     // Scanner is enabled
 * }
 * }</pre>
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    /**
     * Loads configuration from a YAML file.
     *
     * <p>If the file doesn't exist or can't be parsed, logs a warning and returns
     * {@link ProjectConfig#defaults()} with all scanners enabled.
     *
     * @param configPath path to {@code docarchitect.yaml}
     * @return loaded configuration or defaults if unavailable
     */
    public static ProjectConfig load(Path configPath) {
        if (!Files.exists(configPath)) {
            log.warn("Configuration file not found: {}. Using defaults (all scanners enabled).", configPath);
            return ProjectConfig.defaults();
        }

        if (!Files.isRegularFile(configPath) || !Files.isReadable(configPath)) {
            log.warn("Configuration file is not readable: {}. Using defaults.", configPath);
            return ProjectConfig.defaults();
        }

        try {
            log.debug("Loading configuration from: {}", configPath);
            ProjectConfig config = YAML_MAPPER.readValue(configPath.toFile(), ProjectConfig.class);
            log.info("Loaded configuration from: {}", configPath);
            return config;
        } catch (IOException e) {
            log.error("Failed to parse configuration file: {}. Using defaults. Error: {}",
                configPath, e.getMessage());
            return ProjectConfig.defaults();
        }
    }

    /**
     * Loads configuration from a YAML file, or returns defaults if not found.
     *
     * <p>Convenience method that never throws - always returns a valid config.
     *
     * @param configPath path to {@code docarchitect.yaml}
     * @return loaded configuration or defaults
     */
    public static ProjectConfig loadOrDefaults(Path configPath) {
        return load(configPath);
    }
}
