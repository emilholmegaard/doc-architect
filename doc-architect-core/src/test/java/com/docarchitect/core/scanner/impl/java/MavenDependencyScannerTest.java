package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link MavenDependencyScanner}.
 *
 * <p>Tests the scanner's ability to parse real pom.xml files and extract
 * dependency information including version resolution and property substitution.
 */
class MavenDependencyScannerTest extends ScannerTestBase {

    private final MavenDependencyScanner scanner = new MavenDependencyScanner();

    @Test
    void scan_withSimplePom_extractsDependencies() throws IOException {
        // Given: A simple pom.xml with 2 dependencies
        createFile("project/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>3.2.0</version>
                    </dependency>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.15.0</version>
                        <scope>runtime</scope>
                    </dependency>
                </dependencies>
            </project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should successfully extract both dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency springBoot = result.dependencies().get(0);
        assertThat(springBoot.groupId()).isEqualTo("org.springframework.boot");
        assertThat(springBoot.artifactId()).isEqualTo("spring-boot-starter-web");
        assertThat(springBoot.version()).isEqualTo("3.2.0");
        assertThat(springBoot.scope()).isEqualTo("compile"); // Default scope

        Dependency jackson = result.dependencies().get(1);
        assertThat(jackson.groupId()).isEqualTo("com.fasterxml.jackson.core");
        assertThat(jackson.artifactId()).isEqualTo("jackson-databind");
        assertThat(jackson.version()).isEqualTo("2.15.0");
        assertThat(jackson.scope()).isEqualTo("runtime");
    }

    @Test
    void scan_withPropertySubstitution_resolvesVersions() throws IOException {
        // Given: A pom.xml using property for version
        createFile("project/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>

                <properties>
                    <spring.version>3.2.0</spring.version>
                    <jackson.version>2.15.0</jackson.version>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                        <version>${spring.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>${jackson.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should resolve property placeholders
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        assertThat(result.dependencies().get(0).version()).isEqualTo("3.2.0");
        assertThat(result.dependencies().get(1).version()).isEqualTo("2.15.0");
    }

    @Test
    void scan_withProjectVersion_resolvesVersionPlaceholder() throws IOException {
        // Given: A pom.xml using ${project.version}
        createFile("project/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>2.5.0</version>

                <dependencies>
                    <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>common-lib</artifactId>
                        <version>${project.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should resolve ${project.version} to 2.5.0
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);
        assertThat(result.dependencies().get(0).version()).isEqualTo("2.5.0");
    }

    @Test
    void scan_withNoPomFile_returnsEmptyResult() {
        // Given: No pom.xml file exists

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void scan_withTestScope_includesTestDependencies() throws IOException {
        // Given: A pom.xml with test-scoped dependency
        createFile("project/pom.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>test-project</artifactId>
                <version>1.0.0</version>

                <dependencies>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.0</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should include test dependency
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);
        assertThat(result.dependencies().get(0).scope()).isEqualTo("test");
    }

    @Test
    void appliesTo_withPomFile_returnsTrue() throws IOException {
        // Given: A pom.xml file exists
        createFile("project/pom.xml", "<project></project>");

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutPomFile_returnsFalse() {
        // Given: No pom.xml file

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
