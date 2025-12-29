package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link SolutionFileScanner}.
 *
 * <p>Tests the scanner's ability to parse Visual Studio solution files
 * and extract project components.
 */
class SolutionFileScannerTest extends ScannerTestBase {

    private final SolutionFileScanner scanner = new SolutionFileScanner();

    @Test
    void scan_withSimpleSolution_extractsProjects() throws IOException {
        // Given: A .sln file with two projects
        createFile("MyApp.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            # Visual Studio Version 17
            VisualStudioVersion = 17.0.31903.59
            MinimumVisualStudioVersion = 10.0.40219.1
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Web", "src\\Web\\Web.csproj", "{12345678-1234-1234-1234-123456789012}"
            EndProject
            Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Core", "src\\Core\\Core.csproj", "{87654321-4321-4321-4321-210987654321}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both projects
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        Component web = result.components().get(0);
        assertThat(web.name()).isEqualTo("Web");
        assertThat(web.type()).isEqualTo(ComponentType.SERVICE);
        assertThat(web.technology()).isEqualTo(".NET");
        assertThat(web.metadata().get("projectGuid")).isEqualTo("{12345678-1234-1234-1234-123456789012}");
        assertThat(web.metadata().get("projectPath")).isEqualTo("src\\Web\\Web.csproj");
        assertThat(web.metadata().get("solution")).isEqualTo("MyApp.sln");

        Component core = result.components().get(1);
        assertThat(core.name()).isEqualTo("Core");
        assertThat(core.type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withWebProject_classifiesAsService() throws IOException {
        // Given: A solution with web API project
        createFile("eShop.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "PublicApi", "src\\PublicApi\\PublicApi.csproj", "{GUID1}"
            EndProject
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "WebApi", "src\\WebApi\\WebApi.csproj", "{GUID2}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify web projects as SERVICE
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.SERVICE);
        assertThat(result.components().get(1).type()).isEqualTo(ComponentType.SERVICE);
    }

    @Test
    void scan_withInfrastructureProject_classifiesAsModule() throws IOException {
        // Given: A solution with infrastructure project
        createFile("MyApp.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Infrastructure", "src\\Infrastructure\\Infrastructure.csproj", "{GUID1}"
            EndProject
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Data", "src\\Data\\Data.csproj", "{GUID2}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify infrastructure projects as MODULE
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.MODULE);
        assertThat(result.components().get(1).type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withTestProject_classifiesAsModule() throws IOException {
        // Given: A solution with test project
        createFile("MyApp.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "UnitTests", "tests\\UnitTests\\UnitTests.csproj", "{GUID1}"
            EndProject
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "IntegrationTests", "tests\\IntegrationTests\\IntegrationTests.csproj", "{GUID2}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify test projects as MODULE
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.MODULE);
        assertThat(result.components().get(1).type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withDuplicateProjectNames_deduplicates() throws IOException {
        // Given: Two solution files with overlapping projects
        createFile("App1.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Core", "src\\Core\\Core.csproj", "{GUID1}"
            EndProject
            """);

        createFile("App2.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Core", "src\\Core\\Core.csproj", "{GUID1}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should only include Core once
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).name()).isEqualTo("Core");
    }

    @Test
    void scan_withNoSlnFile_returnsEmptyResult() {
        // Given: No .sln files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void scan_withMultipleSolutions_extractsAllProjects() throws IOException {
        // Given: Multiple solution files
        createFile("Frontend.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Web", "src\\Web\\Web.csproj", "{GUID1}"
            EndProject
            """);

        createFile("Backend.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Api", "src\\Api\\Api.csproj", "{GUID2}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract projects from both solutions
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(2);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("Web", "Api");
    }

    @Test
    void scan_withLibraryProject_classifiesAsLibrary() throws IOException {
        // Given: A solution with library project
        createFile("MyApp.sln", """
            Microsoft Visual Studio Solution File, Format Version 12.00
            Project("{9A19103F-16F7-4668-BE54-9A1E7A4F7556}") = "Utilities", "src\\Utilities\\Utilities.csproj", "{GUID1}"
            EndProject
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should classify as LIBRARY (default for unrecognized patterns)
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).type()).isEqualTo(ComponentType.LIBRARY);
    }

    @Test
    void appliesTo_withSlnFile_returnsTrue() throws IOException {
        // Given: A .sln file exists
        createFile("MyApp.sln", "Microsoft Visual Studio Solution File");

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutSlnFile_returnsFalse() {
        // Given: No .sln file

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
