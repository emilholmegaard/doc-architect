package com.docarchitect.core.scanner;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Context provided to scanners during execution.
 *
 * <p>Contains project information, access to files, and results from previous scanners.
 *
 * @param rootPath project root directory
 * @param sourcePaths directories containing source code
 * @param configuration scanner-specific configuration
 * @param settings global settings from docarchitect.yaml
 * @param previousResults results from scanners that ran before this one (keyed by scanner ID)
 */
public record ScanContext(
    Path rootPath,
    List<Path> sourcePaths,
    Map<String, Object> configuration,
    Map<String, String> settings,
    Map<String, ScanResult> previousResults
) {
    /**
     * Compact constructor with validation.
     */
    public ScanContext {
        Objects.requireNonNull(rootPath, "rootPath must not be null");
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            sourcePaths = List.of(rootPath);
        }
        if (configuration == null) {
            configuration = Map.of();
        }
        if (settings == null) {
            settings = Map.of();
        }
        if (previousResults == null) {
            previousResults = Map.of();
        }
    }

    /**
     * Finds files matching the given glob pattern.
     *
     * <p>Example patterns: pom.xml files, Java files, Kotlin files in src/main.
     *
     * @param pattern glob pattern
     * @return stream of matching file paths
     */
    public Stream<Path> findFiles(String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        return sourcePaths.stream()
            .flatMap(sourcePath -> {
                try {
                    return Files.walk(sourcePath)
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            Path relativePath = rootPath.relativize(path);
                            return matcher.matches(relativePath);
                        });
                } catch (IOException e) {
                    return Stream.empty();
                }
            });
    }

    /**
     * Gets a configuration value for the current scanner.
     *
     * @param key configuration key
     * @param <T> expected type
     * @return configuration value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key) {
        return (T) configuration.get(key);
    }

    /**
     * Gets a configuration value with a default.
     *
     * @param key configuration key
     * @param defaultValue default value if key not found
     * @param <T> expected type
     * @return configuration value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigOrDefault(String key, T defaultValue) {
        T value = (T) configuration.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a global setting value.
     *
     * @param key setting key
     * @return setting value or null if not found
     */
    public String getSetting(String key) {
        return settings.get(key);
    }

    /**
     * Gets a global setting with a default.
     *
     * @param key setting key
     * @param defaultValue default value if key not found
     * @return setting value or default
     */
    public String getSettingOrDefault(String key, String defaultValue) {
        return settings.getOrDefault(key, defaultValue);
    }
}
