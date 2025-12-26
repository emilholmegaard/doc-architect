package com.docarchitect.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfigLoader}.
 */
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void load_validYaml_returnsConfig() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, """
            project:
              name: "TestProject"
              version: "1.0.0"
              description: "Test description"

            repositories:
              - name: "main"
                path: "."

            scanners:
              enabled:
                - maven-dependencies
                - spring-rest-api
                - jpa-entities

            generators:
              default: mermaid
              enabled:
                - mermaid
                - markdown

            output:
              directory: "./docs/architecture"
              generateIndex: true
            """);

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("TestProject");
        assertThat(config.project().version()).isEqualTo("1.0.0");
        assertThat(config.project().description()).isEqualTo("Test description");
        assertThat(config.repositories()).hasSize(1);
        assertThat(config.repositories().get(0).name()).isEqualTo("main");
        assertThat(config.scanners().enabled()).containsExactly(
            "maven-dependencies", "spring-rest-api", "jpa-entities"
        );
        assertThat(config.generators().defaultGenerator()).isEqualTo("mermaid");
        assertThat(config.output().directory()).isEqualTo("./docs/architecture");
        assertThat(config.output().generateIndex()).isTrue();
    }

    @Test
    void load_minimalYaml_returnsConfigWithNulls() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, """
            project:
              name: "MinimalProject"
              version: "1.0.0"
            """);

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("MinimalProject");
        assertThat(config.project().version()).isEqualTo("1.0.0");
        assertThat(config.project().description()).isNull();
        assertThat(config.repositories()).isNull();
        assertThat(config.scanners()).isNull();
    }

    @Test
    void load_fileDoesNotExist_returnsDefaults() {
        Path nonExistentFile = tempDir.resolve("nonexistent.yaml");

        ProjectConfig config = ConfigLoader.load(nonExistentFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("project");
        assertThat(config.project().version()).isEqualTo("1.0.0");
    }

    @Test
    void load_invalidYaml_returnsDefaults() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, "invalid: yaml: syntax: [[[");

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("project");
        assertThat(config.project().version()).isEqualTo("1.0.0");
    }

    @Test
    void load_emptyFile_returnsDefaults() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, "");

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        // Empty YAML should still parse but with all nulls
        // ConfigLoader should handle this gracefully
    }

    @Test
    void load_directoryInsteadOfFile_returnsDefaults() throws IOException {
        Path directory = tempDir.resolve("directory");
        Files.createDirectory(directory);

        ProjectConfig config = ConfigLoader.load(directory);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("project");
    }

    @Test
    void loadOrDefaults_validYaml_returnsConfig() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, """
            project:
              name: "TestProject"
              version: "2.0.0"
            """);

        ProjectConfig config = ConfigLoader.loadOrDefaults(configFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("TestProject");
        assertThat(config.project().version()).isEqualTo("2.0.0");
    }

    @Test
    void loadOrDefaults_fileDoesNotExist_returnsDefaults() {
        Path nonExistentFile = tempDir.resolve("nonexistent.yaml");

        ProjectConfig config = ConfigLoader.loadOrDefaults(nonExistentFile);

        assertThat(config).isNotNull();
        assertThat(config.project().name()).isEqualTo("project");
    }

    @Test
    void load_withEmptyEnabledList_parsesCorrectly() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, """
            project:
              name: "AllScannersProject"
              version: "1.0.0"

            scanners:
              enabled: []
            """);

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        assertThat(config.scanners()).isNotNull();
        assertThat(config.scanners().enabled()).isEmpty();
        // Empty list means all scanners enabled
        assertThat(config.scanners().isEnabled("any-scanner")).isTrue();
    }

    @Test
    void load_withScannerConfig_parsesCorrectly() throws IOException {
        Path configFile = tempDir.resolve("docarchitect.yaml");
        Files.writeString(configFile, """
            project:
              name: "ConfiguredProject"
              version: "1.0.0"

            scanners:
              enabled:
                - maven-dependencies
              config:
                maven:
                  includedScopes:
                    - compile
                    - runtime
            """);

        ProjectConfig config = ConfigLoader.load(configFile);

        assertThat(config).isNotNull();
        assertThat(config.scanners().enabled()).containsExactly("maven-dependencies");
        assertThat(config.scanners().config()).isNotEmpty();
        assertThat(config.scanners().config()).containsKey("maven");
    }
}
