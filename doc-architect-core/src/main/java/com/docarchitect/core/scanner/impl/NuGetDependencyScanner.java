package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.util.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for NuGet package dependencies in .NET projects.
 *
 * <p>Uses Jackson XML to parse .csproj and packages.config files.
 * Supports both SDK-style and legacy .NET project formats.
 *
 * <h3>Supported Formats</h3>
 *
 * <h4>SDK-Style .csproj (Modern .NET Core/.NET 5+)</h4>
 * <pre>{@code
 * <Project Sdk="Microsoft.NET.Sdk">
 *   <ItemGroup>
 *     <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
 *     <PackageReference Include="Microsoft.EntityFrameworkCore" Version="7.0.0" />
 *   </ItemGroup>
 * </Project>
 * }</pre>
 *
 * <h4>Legacy .csproj (.NET Framework)</h4>
 * <pre>{@code
 * <Project ToolsVersion="15.0">
 *   <ItemGroup>
 *     <Reference Include="Newtonsoft.Json, Version=13.0.0.0, Culture=neutral">
 *       <HintPath>..\packages\Newtonsoft.Json.13.0.3\lib\net45\Newtonsoft.Json.dll</HintPath>
 *     </Reference>
 *   </ItemGroup>
 * </Project>
 * }</pre>
 *
 * <h4>packages.config (Legacy NuGet)</h4>
 * <pre>{@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <packages>
 *   <package id="Newtonsoft.Json" version="13.0.3" targetFramework="net472" />
 *   <package id="EntityFramework" version="6.4.4" targetFramework="net472" />
 * </packages>
 * }</pre>
 *
 * <h4>Directory.Build.props (Centralized Package Management)</h4>
 * <pre>{@code
 * <Project>
 *   <ItemGroup>
 *     <PackageReference Update="Newtonsoft.Json" Version="13.0.3" />
 *   </ItemGroup>
 * </Project>
 * }</pre>
 *
 * @see Scanner
 * @see Dependency
 * @since 1.0.0
 */
public class NuGetDependencyScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(NuGetDependencyScanner.class);

    private final XmlMapper xmlMapper = new XmlMapper();

    @Override
    public String getId() {
        return "nuget-dependencies";
    }

    @Override
    public String getDisplayName() {
        return "NuGet Dependency Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("csharp", "dotnet");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.csproj", "**/packages.config", "**/Directory.Build.props");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return context.findFiles("**/*.csproj").findAny().isPresent() ||
               context.findFiles("**/packages.config").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning NuGet dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        String sourceComponentId = IdGenerator.generate(context.rootPath().toString());

        // Create component for the .NET project
        Component dotnetProject = new Component(
            sourceComponentId,
            context.rootPath().getFileName().toString(),
            ComponentType.LIBRARY,
            ".NET Project",
            ".NET",
            null,
            Map.of()
        );
        components.add(dotnetProject);

        // Parse .csproj files
        context.findFiles("**/*.csproj").forEach(file -> {
            try {
                parseCsprojFile(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse .csproj file: {} - {}", file, e.getMessage());
            }
        });

        // Parse packages.config files
        context.findFiles("**/packages.config").forEach(file -> {
            try {
                parsePackagesConfig(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse packages.config: {} - {}", file, e.getMessage());
            }
        });

        // Parse Directory.Build.props files
        context.findFiles("**/Directory.Build.props").forEach(file -> {
            try {
                parseDirectoryBuildProps(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse Directory.Build.props: {} - {}", file, e.getMessage());
            }
        });

        log.info("Found {} NuGet dependencies", dependencies.size());

        return new ScanResult(
            getId(),
            true,
            components,
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Parses .csproj file for PackageReference elements (SDK-style)
     * and Reference elements (legacy .NET Framework).
     */
    private void parseCsprojFile(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = Files.readString(file);
        JsonNode root = xmlMapper.readTree(content);

        // SDK-style: <PackageReference Include="..." Version="..." />
        extractPackageReferences(root, sourceComponentId, dependencies, file);

        // Legacy: <Reference Include="PackageName, Version=...">
        extractLegacyReferences(root, sourceComponentId, dependencies, file);
    }

    /**
     * Extracts PackageReference elements from SDK-style projects.
     */
    private void extractPackageReferences(JsonNode root, String sourceComponentId,
                                         List<Dependency> dependencies, Path file) {
        JsonNode itemGroups = root.get("ItemGroup");
        if (itemGroups == null) {
            return;
        }

        // ItemGroup can be single object or array
        if (!itemGroups.isArray()) {
            itemGroups = xmlMapper.createArrayNode().add(itemGroups);
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode packageRefs = itemGroup.get("PackageReference");
            if (packageRefs == null) {
                continue;
            }

            // PackageReference can be single or array
            if (!packageRefs.isArray()) {
                packageRefs = xmlMapper.createArrayNode().add(packageRefs);
            }

            for (JsonNode pkgRef : packageRefs) {
                String packageId = extractAttribute(pkgRef, "Include");
                String version = extractAttribute(pkgRef, "Version");

                if (packageId != null && version != null) {
                    Dependency dep = new Dependency(
                        sourceComponentId,
                        packageId,
                        null, // NuGet doesn't use groupId
                        version,
                        "compile",
                        true
                    );

                    dependencies.add(dep);
                    log.debug("Found NuGet package from {}: {} {}", file.getFileName(), packageId, version);
                }
            }
        }
    }

    /**
     * Extracts Reference elements from legacy .NET Framework projects.
     */
    private void extractLegacyReferences(JsonNode root, String sourceComponentId,
                                        List<Dependency> dependencies, Path file) {
        JsonNode itemGroups = root.get("ItemGroup");
        if (itemGroups == null) {
            return;
        }

        if (!itemGroups.isArray()) {
            itemGroups = xmlMapper.createArrayNode().add(itemGroups);
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode references = itemGroup.get("Reference");
            if (references == null) {
                continue;
            }

            if (!references.isArray()) {
                references = xmlMapper.createArrayNode().add(references);
            }

            for (JsonNode reference : references) {
                String include = extractAttribute(reference, "Include");
                if (include == null) {
                    continue;
                }

                // Extract package name from: "Newtonsoft.Json, Version=13.0.0.0, ..."
                String[] parts = include.split(",");
                if (parts.length == 0) {
                    continue;
                }

                String packageId = parts[0].trim();

                // Extract version from Include attribute or HintPath
                String version = extractVersionFromInclude(include);
                if (version == null) {
                    JsonNode hintPath = reference.get("HintPath");
                    if (hintPath != null) {
                        version = extractVersionFromHintPath(hintPath.asText());
                    }
                }

                if (version == null) {
                    version = "*"; // Unknown version
                }

                // Skip system assemblies
                if (packageId.startsWith("System.") || packageId.equals("mscorlib")) {
                    continue;
                }

                Dependency dep = new Dependency(
                    sourceComponentId,
                    packageId,
                    null,
                    version,
                    "compile",
                    true
                );

                dependencies.add(dep);
                log.debug("Found legacy reference from {}: {} {}", file.getFileName(), packageId, version);
            }
        }
    }

    /**
     * Parses packages.config file.
     */
    private void parsePackagesConfig(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = Files.readString(file);
        JsonNode root = xmlMapper.readTree(content);

        JsonNode packages = root.get("package");
        if (packages == null) {
            return;
        }

        if (!packages.isArray()) {
            packages = xmlMapper.createArrayNode().add(packages);
        }

        for (JsonNode pkg : packages) {
            String packageId = extractAttribute(pkg, "id");
            String version = extractAttribute(pkg, "version");

            if (packageId != null && version != null) {
                Dependency dep = new Dependency(
                    sourceComponentId,
                    packageId,
                    null,
                    version,
                    "compile",
                    true
                );

                dependencies.add(dep);
                log.debug("Found package from packages.config: {} {}", packageId, version);
            }
        }
    }

    /**
     * Parses Directory.Build.props for centralized package management.
     */
    private void parseDirectoryBuildProps(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = Files.readString(file);
        JsonNode root = xmlMapper.readTree(content);

        JsonNode itemGroups = root.get("ItemGroup");
        if (itemGroups == null) {
            return;
        }

        if (!itemGroups.isArray()) {
            itemGroups = xmlMapper.createArrayNode().add(itemGroups);
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode packageRefs = itemGroup.get("PackageReference");
            if (packageRefs == null) {
                continue;
            }

            if (!packageRefs.isArray()) {
                packageRefs = xmlMapper.createArrayNode().add(packageRefs);
            }

            for (JsonNode pkgRef : packageRefs) {
                // Directory.Build.props uses Update instead of Include
                String packageId = extractAttribute(pkgRef, "Update");
                if (packageId == null) {
                    packageId = extractAttribute(pkgRef, "Include");
                }

                String version = extractAttribute(pkgRef, "Version");

                if (packageId != null && version != null) {
                    Dependency dep = new Dependency(
                        sourceComponentId,
                        packageId,
                        null,
                        version,
                        "compile",
                        true
                    );

                    dependencies.add(dep);
                    log.debug("Found centralized package: {} {}", packageId, version);
                }
            }
        }
    }

    /**
     * Extracts attribute value from Jackson JsonNode.
     * Jackson XML represents attributes as empty strings with attribute name.
     */
    private String extractAttribute(JsonNode node, String attributeName) {
        JsonNode attr = node.get(attributeName);
        if (attr != null && attr.isTextual()) {
            return attr.asText();
        }
        return null;
    }

    /**
     * Extracts version from Include attribute: "PackageName, Version=13.0.0.0".
     */
    private String extractVersionFromInclude(String include) {
        String[] parts = include.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("Version=")) {
                String version = part.substring("Version=".length());
                // Simplify assembly version to package version (13.0.0.0 -> 13.0.0)
                String[] versionParts = version.split("\\.");
                if (versionParts.length >= 3) {
                    return versionParts[0] + "." + versionParts[1] + "." + versionParts[2];
                }
                return version;
            }
        }
        return null;
    }

    /**
     * Extracts version from HintPath: "..\packages\PackageName.13.0.3\lib\...".
     */
    private String extractVersionFromHintPath(String hintPath) {
        // Pattern: packages\PackageName.Version\lib\...
        String[] parts = hintPath.split("[\\\\/]");
        for (String part : parts) {
            if (part.contains(".")) {
                // Try to find package folder: PackageName.13.0.3
                int lastDot = part.lastIndexOf('.');
                if (lastDot > 0) {
                    String possibleVersion = part.substring(lastDot + 1);
                    // Check if it looks like a version (starts with digit)
                    if (!possibleVersion.isEmpty() && Character.isDigit(possibleVersion.charAt(0))) {
                        // Extract full version by going backwards
                        int versionStart = part.lastIndexOf('.', lastDot - 1);
                        while (versionStart > 0) {
                            char c = part.charAt(versionStart - 1);
                            if (!Character.isDigit(c) && c != '.') {
                                break;
                            }
                            versionStart = part.lastIndexOf('.', versionStart - 1);
                        }
                        if (versionStart > 0) {
                            return part.substring(versionStart + 1);
                        }
                    }
                }
            }
        }
        return null;
    }
}
