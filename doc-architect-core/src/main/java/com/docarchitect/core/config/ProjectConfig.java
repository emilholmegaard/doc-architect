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
     * Creates a default configuration with all scanners enabled in AUTO mode.
     *
     * @return default configuration
     */
    public static ProjectConfig defaults() {
        return new ProjectConfig(
            new ProjectInfo("project", "1.0.0", null),
            List.of(new RepositoryConfig("main", ".")),
            new ScannerConfig(null, List.of(), List.of(), Map.of()),  // AUTO mode (mode=null, no enabled/groups)
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
     * Scanner configuration mode.
     */
    public enum ScannerMode {
        /** Auto-enable all scanners, let them self-filter via applicability strategies */
        AUTO,
        /** Enable scanners by technology groups (java, python, etc.) */
        GROUPS,
        /** Explicitly list enabled scanner IDs (legacy mode) */
        EXPLICIT
    }

    /**
     * Scanner configuration.
     *
     * <p>Supports three configuration modes:</p>
     * <ul>
     *   <li><b>AUTO</b> - All scanners run and self-filter (zero config)</li>
     *   <li><b>GROUPS</b> - Enable by technology groups (e.g., "java", "python")</li>
     *   <li><b>EXPLICIT</b> - List specific scanner IDs (legacy)</li>
     * </ul>
     *
     * @param mode scanner selection mode (null = AUTO if no enabled list, EXPLICIT if enabled list present)
     * @param enabled list of enabled scanner IDs (EXPLICIT mode only)
     * @param groups list of scanner groups (GROUPS mode only)
     * @param config scanner-specific configuration
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScannerConfig(
        @JsonProperty("mode") ScannerMode mode,
        @JsonProperty("enabled") List<String> enabled,
        @JsonProperty("groups") List<String> groups,
        @JsonProperty("config") Map<String, Object> config
    ) {
        /**
         * Get the effective scanner mode.
         *
         * <p>If mode is explicitly set, use it. Otherwise infer from configuration:</p>
         * <ul>
         *   <li>If groups is non-empty → GROUPS</li>
         *   <li>If enabled is non-empty → EXPLICIT</li>
         *   <li>Otherwise → AUTO</li>
         * </ul>
         *
         * @return the effective scanner mode
         */
        public ScannerMode getEffectiveMode() {
            if (mode != null) {
                return mode;
            }
            // Infer mode from configuration
            if (groups != null && !groups.isEmpty()) {
                return ScannerMode.GROUPS;
            }
            if (enabled != null && !enabled.isEmpty()) {
                return ScannerMode.EXPLICIT;
            }
            return ScannerMode.AUTO;
        }

        /**
         * Checks if a scanner is enabled.
         *
         * <p>Behavior depends on mode:</p>
         * <ul>
         *   <li>AUTO: Always returns true (scanners self-filter)</li>
         *   <li>GROUPS: Returns true if scanner is in any enabled group</li>
         *   <li>EXPLICIT: Returns true if scanner ID is in enabled list</li>
         * </ul>
         *
         * @param scannerId scanner ID to check
         * @return true if scanner is enabled
         */
        public boolean isEnabled(String scannerId) {
            ScannerMode effectiveMode = getEffectiveMode();
            return switch (effectiveMode) {
                case AUTO -> true;  // All scanners enabled, self-filter via applicability
                case GROUPS -> true;  // Group filtering done elsewhere
                case EXPLICIT -> enabled == null || enabled.isEmpty() || enabled.contains(scannerId);
            };
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
