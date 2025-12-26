package com.docarchitect.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Root configuration for DocArchitect projects.
 *
 * <p>Loaded from {@code docarchitect.yaml} in the project root. Defines project metadata,
 * enabled scanners/generators/renderers, and output settings.
 *
 * <p><b>Example YAML:</b>
 * <pre>{@code
 * project:
 *   name: "My Project"
 *   version: "1.0.0"
 *
 * scanners:
 *   enabled:
 *     - maven-dependencies
 *     - spring-rest-api
 *     - jpa-entities
 *
 * generators:
 *   default: mermaid
 *   enabled:
 *     - mermaid
 *     - markdown
 *
 * output:
 *   directory: "./docs/architecture"
 *   generateIndex: true
 * }</pre>
 *
 * @param project project metadata
 * @param repositories repository configurations
 * @param scanners scanner configuration
 * @param generators generator configuration
 * @param output output configuration
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectConfig(
    @JsonProperty("project") ProjectInfo project,
    @JsonProperty("repositories") List<RepositoryConfig> repositories,
    @JsonProperty("scanners") ScannerConfig scanners,
    @JsonProperty("generators") GeneratorConfigSettings generators,
    @JsonProperty("output") OutputConfig output
) {
    /**
     * Creates a default configuration with all scanners enabled.
     *
     * @return default configuration
     */
    public static ProjectConfig defaults() {
        return new ProjectConfig(
            new ProjectInfo("project", "1.0.0", null),
            List.of(new RepositoryConfig("main", ".")),
            new ScannerConfig(List.of(), Map.of()),
            new GeneratorConfigSettings("mermaid", List.of("mermaid", "markdown")),
            new OutputConfig("./docs/architecture", true)
        );
    }

    /**
     * Project metadata.
     *
     * @param name project name
     * @param version project version
     * @param description optional project description
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectInfo(
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("description") String description
    ) {}

    /**
     * Repository configuration.
     *
     * @param name repository name
     * @param path repository path
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryConfig(
        @JsonProperty("name") String name,
        @JsonProperty("path") String path
    ) {}

    /**
     * Scanner configuration.
     *
     * @param enabled list of enabled scanner IDs (empty = all enabled)
     * @param config scanner-specific configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScannerConfig(
        @JsonProperty("enabled") List<String> enabled,
        @JsonProperty("config") Map<String, Object> config
    ) {
        /**
         * Checks if a scanner is enabled.
         *
         * @param scannerId scanner ID to check
         * @return true if scanner is enabled (or if enabled list is empty)
         */
        public boolean isEnabled(String scannerId) {
            // If enabled list is empty, all scanners are enabled
            return enabled == null || enabled.isEmpty() || enabled.contains(scannerId);
        }
    }

    /**
     * Generator configuration.
     *
     * @param defaultGenerator default generator ID
     * @param enabled list of enabled generator IDs
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratorConfigSettings(
        @JsonProperty("default") String defaultGenerator,
        @JsonProperty("enabled") List<String> enabled
    ) {}

    /**
     * Output configuration.
     *
     * @param directory output directory path
     * @param generateIndex whether to generate index.md
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputConfig(
        @JsonProperty("directory") String directory,
        @JsonProperty("generateIndex") Boolean generateIndex
    ) {}
}
