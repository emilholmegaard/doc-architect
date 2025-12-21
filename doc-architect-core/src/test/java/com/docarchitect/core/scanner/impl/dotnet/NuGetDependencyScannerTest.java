package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link NuGetDependencyScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse SDK-style .csproj files (modern .NET Core/.NET 5+)</li>
 *   <li>Parse legacy .csproj files (.NET Framework)</li>
 *   <li>Parse packages.config files</li>
 *   <li>Parse Directory.Build.props files</li>
 *   <li>Extract package references with correct versions</li>
 *   <li>Handle multiple project files</li>
 * </ul>
 *
 * @see NuGetDependencyScanner
 * @since 1.0.0
 */
class NuGetDependencyScannerTest extends ScannerTestBase {

    private NuGetDependencyScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new NuGetDependencyScanner();
    }

    @Test
    void scan_withSdkStyleCsproj_extractsDependencies() throws IOException {
        // Given: SDK-style .csproj (modern .NET)
        createFile("project/MyApp.csproj", """
            <Project Sdk="Microsoft.NET.Sdk">
              <PropertyGroup>
                <TargetFramework>net8.0</TargetFramework>
              </PropertyGroup>

              <ItemGroup>
                <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
                <PackageReference Include="Microsoft.EntityFrameworkCore" Version="8.0.0" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency newtonsoftJson = result.dependencies().stream()
            .filter(d -> "Newtonsoft.Json".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(newtonsoftJson.groupId()).isEqualTo("nuget");
        assertThat(newtonsoftJson.version()).isEqualTo("13.0.3");
        assertThat(newtonsoftJson.direct()).isTrue();
    }

    @Test
    void scan_withPackagesConfig_extractsDependencies() throws IOException {
        // Given: packages.config file (legacy NuGet)
        createFile("project/packages.config", """
            <?xml version="1.0" encoding="utf-8"?>
            <packages>
              <package id="Newtonsoft.Json" version="13.0.3" targetFramework="net472" />
              <package id="EntityFramework" version="6.4.4" targetFramework="net472" />
            </packages>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency entityFramework = result.dependencies().stream()
            .filter(d -> "EntityFramework".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(entityFramework.groupId()).isEqualTo("nuget");
        assertThat(entityFramework.version()).isEqualTo("6.4.4");
    }

    @Test
    void scan_withDirectoryBuildProps_extractsDependencies() throws IOException {
        // Given: Directory.Build.props file (centralized package management)
        createFile("project/Directory.Build.props", """
            <Project>
              <ItemGroup>
                <PackageReference Update="Newtonsoft.Json" Version="13.0.3" />
                <PackageReference Update="Serilog" Version="3.1.0" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency serilog = result.dependencies().stream()
            .filter(d -> "Serilog".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(serilog.version()).isEqualTo("3.1.0");
    }

    @Test
    void scan_withMultipleCsprojFiles_extractsAll() throws IOException {
        // Given: Multiple .csproj files
        createFile("project1/App.csproj", """
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
              </ItemGroup>
            </Project>
            """);

        createFile("project2/Api.csproj", """
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="Microsoft.AspNetCore" Version="2.2.0" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from all files
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("Newtonsoft.Json", "Microsoft.AspNetCore");
    }

    @Test
    void scan_withLegacyCsproj_extractsDependencies() throws IOException {
        // Given: Legacy .csproj with Reference elements
        createFile("project/LegacyApp.csproj", """
            <?xml version="1.0" encoding="utf-8"?>
            <Project ToolsVersion="15.0" xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <ItemGroup>
                <Reference Include="Newtonsoft.Json, Version=13.0.0.0, Culture=neutral, PublicKeyToken=30ad4fe6b2a6aeed">
                  <HintPath>..\\packages\\Newtonsoft.Json.13.0.3\\lib\\net45\\Newtonsoft.Json.dll</HintPath>
                </Reference>
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies from HintPath
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(1);

        Dependency newtonsoftJson = result.dependencies().get(0);
        assertThat(newtonsoftJson.artifactId()).isEqualTo("Newtonsoft.Json");
        assertThat(newtonsoftJson.version()).isEqualTo("13.0.0"); // Extracted from Version attribute in Include
    }

    @Test
    void scan_withNoCsprojFiles_returnsEmpty() throws IOException {
        // Given: No .csproj or packages.config files
        createDirectory("src/main/java");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).isEmpty();
    }

    @Test
    void appliesTo_withCsprojFiles_returnsTrue() throws IOException {
        // Given: Project with .csproj file
        createFile("project/App.csproj", "<Project />");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withPackagesConfig_returnsTrue() throws IOException {
        // Given: Project with packages.config
        createFile("project/packages.config", "<packages />");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withDirectoryPackagesProps_returnsTrue() throws IOException {
        // Given: Project with Directory.Packages.props
        createFile("Directory.Packages.props", "<Project />");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutDotNetFiles_returnsFalse() throws IOException {
        // Given: Project without .NET files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withPackagesWithoutVersion_usesFallbackVersion() throws IOException {
        // Given: SDK-style .csproj with packages without explicit versions (Central Package Management)
        createFile("project/MyApp.csproj", """
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="MediatR" />
                <PackageReference Include="FluentValidation" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies with fallback version
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(2);

        Dependency mediatr = result.dependencies().stream()
            .filter(d -> "MediatR".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(mediatr.groupId()).isEqualTo("nuget");
        assertThat(mediatr.version()).isEqualTo("*");
        assertThat(mediatr.direct()).isTrue();
    }

    @Test
    void scan_withDirectoryPackagesProps_extractsDependencies() throws IOException {
        // Given: Directory.Packages.props file (Central Package Management)
        createFile("project/Directory.Packages.props", """
            <Project>
              <PropertyGroup>
                <ManagePackageVersionsCentrally>true</ManagePackageVersionsCentrally>
              </PropertyGroup>
              <ItemGroup>
                <PackageVersion Include="MediatR" Version="12.0.1" />
                <PackageVersion Include="FluentValidation" Version="11.9.0" />
                <PackageVersion Include="Serilog" Version="3.1.0" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract dependencies
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(3);

        Dependency mediatr = result.dependencies().stream()
            .filter(d -> "MediatR".equals(d.artifactId()))
            .findFirst()
            .orElseThrow();

        assertThat(mediatr.groupId()).isEqualTo("nuget");
        assertThat(mediatr.version()).isEqualTo("12.0.1");
        assertThat(mediatr.direct()).isTrue();
    }

    @Test
    void scan_withCentralPackageManagement_combinesBothSources() throws IOException {
        // Given: Both Directory.Packages.props and .csproj files
        createFile("project/Directory.Packages.props", """
            <Project>
              <PropertyGroup>
                <ManagePackageVersionsCentrally>true</ManagePackageVersionsCentrally>
              </PropertyGroup>
              <ItemGroup>
                <PackageVersion Include="MediatR" Version="12.0.1" />
                <PackageVersion Include="FluentValidation" Version="11.9.0" />
              </ItemGroup>
            </Project>
            """);

        createFile("project/MyApp.csproj", """
            <Project Sdk="Microsoft.NET.Sdk">
              <ItemGroup>
                <PackageReference Include="Serilog" />
              </ItemGroup>
            </Project>
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract from both sources
        assertThat(result.success()).isTrue();
        assertThat(result.dependencies()).hasSize(3);

        assertThat(result.dependencies())
            .extracting(Dependency::artifactId)
            .containsExactlyInAnyOrder("MediatR", "FluentValidation", "Serilog");
    }
}
