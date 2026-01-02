package com.docarchitect.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ProjectConfig} and nested records.
 */
class ProjectConfigTest {

    @Test
    void defaults_createsValidConfiguration() {
        ProjectConfig config = ProjectConfig.defaults();

        assertThat(config).isNotNull();
        assertThat(config.project()).isNotNull();
        assertThat(config.project().name()).isEqualTo("project");
        assertThat(config.project().version()).isEqualTo("1.0.0");
        assertThat(config.repositories()).hasSize(1);
        assertThat(config.scanners()).isNotNull();
        assertThat(config.generators()).isNotNull();
        assertThat(config.output()).isNotNull();
    }

    @Test
    void projectInfo_storesMetadata() {
        var projectInfo = new ProjectConfig.ProjectInfo("MyProject", "2.0.0", "Test description");

        assertThat(projectInfo.name()).isEqualTo("MyProject");
        assertThat(projectInfo.version()).isEqualTo("2.0.0");
        assertThat(projectInfo.description()).isEqualTo("Test description");
    }

    @Test
    void repositoryConfig_storesPathAndName() {
        var repoConfig = new ProjectConfig.RepositoryConfig("main", "/path/to/repo");

        assertThat(repoConfig.name()).isEqualTo("main");
        assertThat(repoConfig.path()).isEqualTo("/path/to/repo");
    }

    @Test
    void scannerConfig_isEnabled_returnsTrueWhenListIsEmpty() {
        var scannerConfig = new ProjectConfig.ScannerConfig(null, List.of(), List.of(), Map.of());

        assertThat(scannerConfig.isEnabled("maven-dependencies")).isTrue();
        assertThat(scannerConfig.isEnabled("any-scanner")).isTrue();
    }

    @Test
    void scannerConfig_isEnabled_returnsTrueWhenListIsNull() {
        var scannerConfig = new ProjectConfig.ScannerConfig(null, null, List.of(), Map.of());

        assertThat(scannerConfig.isEnabled("maven-dependencies")).isTrue();
        assertThat(scannerConfig.isEnabled("any-scanner")).isTrue();
    }

    @Test
    void scannerConfig_isEnabled_returnsTrueForEnabledScanner() {
        var scannerConfig = new ProjectConfig.ScannerConfig(
            null,
            List.of("maven-dependencies", "spring-rest-api", "jpa-entities"),
            List.of(),
            Map.of()
        );

        assertThat(scannerConfig.isEnabled("maven-dependencies")).isTrue();
        assertThat(scannerConfig.isEnabled("spring-rest-api")).isTrue();
        assertThat(scannerConfig.isEnabled("jpa-entities")).isTrue();
    }

    @Test
    void scannerConfig_isEnabled_returnsFalseForDisabledScanner() {
        var scannerConfig = new ProjectConfig.ScannerConfig(
            null,
            List.of("maven-dependencies", "spring-rest-api"),
            List.of(),
            Map.of()
        );

        assertThat(scannerConfig.isEnabled("maven-dependencies")).isTrue();
        assertThat(scannerConfig.isEnabled("spring-rest-api")).isTrue();
        assertThat(scannerConfig.isEnabled("jpa-entities")).isFalse();
        assertThat(scannerConfig.isEnabled("gradle-dependencies")).isFalse();
    }

    @Test
    void generatorConfigSettings_storesDefaultAndEnabled() {
        var generatorConfig = new ProjectConfig.GeneratorConfigSettings(
            "mermaid",
            List.of("mermaid", "markdown")
        );

        assertThat(generatorConfig.defaultGenerator()).isEqualTo("mermaid");
        assertThat(generatorConfig.enabled()).containsExactly("mermaid", "markdown");
    }

    @Test
    void outputConfig_storesDirectoryAndGenerateIndex() {
        var outputConfig = new ProjectConfig.OutputConfig("./docs/architecture", true);

        assertThat(outputConfig.directory()).isEqualTo("./docs/architecture");
        assertThat(outputConfig.generateIndex()).isTrue();
    }

    @Test
    void outputConfig_supportsNullGenerateIndex() {
        var outputConfig = new ProjectConfig.OutputConfig("./docs", null);

        assertThat(outputConfig.directory()).isEqualTo("./docs");
        assertThat(outputConfig.generateIndex()).isNull();
    }

    @Test
    void fullConfig_canBeConstructed() {
        var config = new ProjectConfig(
            new ProjectConfig.ProjectInfo("TestProject", "1.0.0", "Test"),
            List.of(new ProjectConfig.RepositoryConfig("main", ".")),
            new ProjectConfig.ScannerConfig(null, List.of("maven-dependencies"), List.of(), Map.of()),
            new ProjectConfig.GeneratorConfigSettings("mermaid", List.of("mermaid")),
            new ProjectConfig.OutputConfig("./docs", true)
        );

        assertThat(config.project().name()).isEqualTo("TestProject");
        assertThat(config.repositories()).hasSize(1);
        assertThat(config.scanners().isEnabled("maven-dependencies")).isTrue();
        assertThat(config.scanners().isEnabled("gradle-dependencies")).isFalse();
        assertThat(config.generators().defaultGenerator()).isEqualTo("mermaid");
        assertThat(config.output().directory()).isEqualTo("./docs");
    }

    @Test
    void scannerMode_fromString_handlesUpperCase() {
        assertThat(ProjectConfig.ScannerMode.fromString("AUTO")).isEqualTo(ProjectConfig.ScannerMode.AUTO);
        assertThat(ProjectConfig.ScannerMode.fromString("GROUPS")).isEqualTo(ProjectConfig.ScannerMode.GROUPS);
        assertThat(ProjectConfig.ScannerMode.fromString("EXPLICIT")).isEqualTo(ProjectConfig.ScannerMode.EXPLICIT);
    }

    @Test
    void scannerMode_fromString_handlesLowerCase() {
        assertThat(ProjectConfig.ScannerMode.fromString("auto")).isEqualTo(ProjectConfig.ScannerMode.AUTO);
        assertThat(ProjectConfig.ScannerMode.fromString("groups")).isEqualTo(ProjectConfig.ScannerMode.GROUPS);
        assertThat(ProjectConfig.ScannerMode.fromString("explicit")).isEqualTo(ProjectConfig.ScannerMode.EXPLICIT);
    }

    @Test
    void scannerMode_fromString_handlesMixedCase() {
        assertThat(ProjectConfig.ScannerMode.fromString("Auto")).isEqualTo(ProjectConfig.ScannerMode.AUTO);
        assertThat(ProjectConfig.ScannerMode.fromString("Groups")).isEqualTo(ProjectConfig.ScannerMode.GROUPS);
        assertThat(ProjectConfig.ScannerMode.fromString("Explicit")).isEqualTo(ProjectConfig.ScannerMode.EXPLICIT);
    }

    @Test
    void scannerMode_fromString_handlesNull() {
        assertThat(ProjectConfig.ScannerMode.fromString(null)).isEqualTo(ProjectConfig.ScannerMode.AUTO);
    }
}
