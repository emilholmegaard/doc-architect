package com.docarchitect.core.scanner.impl.dotnet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJacksonScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Scanner for NuGet package dependencies in .NET projects.
 *
 * <p>Uses Jackson XML to parse .csproj and packages.config files.
 * Supports both SDK-style and legacy .NET project formats.
 *
 * <p><b>Supported Formats</b></p>
 *
 * <p><b>SDK-Style .csproj (Modern .NET Core/.NET 5+):</b></p>
 * <pre>{@code
 * <Project Sdk="Microsoft.NET.Sdk">
 *   <ItemGroup>
 *     <PackageReference Include="Newtonsoft.Json" Version="13.0.3" />
 *     <PackageReference Include="Microsoft.EntityFrameworkCore" Version="7.0.0" />
 *   </ItemGroup>
 * </Project>
 * }</pre>
 *
 * <p><b>Legacy .csproj (.NET Framework):</b></p>
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
 * <p><b>packages.config (Legacy NuGet):</b></p>
 * <pre>{@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <packages>
 *   <package id="Newtonsoft.Json" version="13.0.3" targetFramework="net472" />
 *   <package id="EntityFramework" version="6.4.4" targetFramework="net472" />
 * </packages>
 * }</pre>
 *
 * <p><b>Directory.Build.props (Centralized Package Management):</b></p>
 * <pre>{@code
 * <Project>
 *   <ItemGroup>
 *     <PackageReference Update="Newtonsoft.Json" Version="13.0.3" />
 *   </ItemGroup>
 * </Project>
 * }</pre>
 *
 * @see Dependency
 * @since 1.0.0
 */
public class NuGetDependencyScanner extends AbstractJacksonScanner {

    // XML Element Names
    private static final String ITEM_GROUP = "ItemGroup";
    private static final String PACKAGE_REFERENCE = "PackageReference";
    private static final String REFERENCE = "Reference";
    private static final String PACKAGE = "package";
    private static final String HINT_PATH = "HintPath";

    // XML Attribute Names
    private static final String INCLUDE = "Include";
    private static final String UPDATE = "Update";
    private static final String VERSION = "Version";
    private static final String ID = "id";
    private static final String VERSION_ATTRIBUTE = "version";

    // Dependency Properties
    private static final String GROUP_ID_NUGET = "nuget";
    private static final String SCOPE_COMPILE = "compile";
    private static final String UNKNOWN_VERSION = "*";

    // File Patterns
    private static final String CSPROJ_PATTERN = "**/*.csproj";
    private static final String PACKAGES_CONFIG_PATTERN = "**/packages.config";
    private static final String BUILD_PROPS_PATTERN = "**/Directory.Build.props";

    // Prefixes and Delimiters
    private static final String VERSION_PREFIX = "Version=";
    private static final String SYSTEM_ASSEMBLY_PREFIX = "System.";
    private static final String MSCORLIB = "mscorlib";
    private static final String COMMA_DELIMITER = ",";
    private static final String VERSION_SEPARATOR = "\\.";
    private static final String PATH_SEPARATOR_REGEX = "[\\\\\\\\|\\\\/]";
    private static final String DOT = ".";

    // Component Properties
    private static final String DOTNET_PROJECT_TYPE = ".NET Project";
    private static final String DOTNET_TECHNOLOGY = ".NET";
    private static final String SCANNER_ID = "nuget-dependencies";
    private static final String SCANNER_NAME = "NuGet Dependency Scanner";

    // Version formatting constants
    private static final int MIN_VERSION_PARTS = 3;
    private static final int VERSION_MAJOR_INDEX = 0;
    private static final int VERSION_MINOR_INDEX = 1;
    private static final int VERSION_PATCH_INDEX = 2;

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.CSHARP, Technologies.DOTNET);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(CSPROJ_PATTERN, PACKAGES_CONFIG_PATTERN, BUILD_PROPS_PATTERN);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, CSPROJ_PATTERN, PACKAGES_CONFIG_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning NuGet dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        String sourceComponentId = IdGenerator.generate(context.rootPath().toString());

        Component dotnetProject = new Component(
            sourceComponentId,
            context.rootPath().getFileName().toString(),
            ComponentType.LIBRARY,
            DOTNET_PROJECT_TYPE,
            DOTNET_TECHNOLOGY,
            null,
            Map.of()
        );
        components.add(dotnetProject);

        context.findFiles(CSPROJ_PATTERN).forEach(file -> {
            try {
                parseCsprojFile(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse .csproj file: {} - {}", file, e.getMessage());
            }
        });

        context.findFiles(PACKAGES_CONFIG_PATTERN).forEach(file -> {
            try {
                parsePackagesConfig(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse packages.config: {} - {}", file, e.getMessage());
            }
        });

        context.findFiles(BUILD_PROPS_PATTERN).forEach(file -> {
            try {
                parseDirectoryBuildProps(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse Directory.Build.props: {} - {}", file, e.getMessage());
            }
        });

        log.info("Found {} NuGet dependencies", dependencies.size());

        return buildSuccessResult(
            components,
            dependencies,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void parseCsprojFile(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        JsonNode root = parseXml(file);
        extractPackageReferences(root, sourceComponentId, dependencies, file);
        extractLegacyReferences(root, sourceComponentId, dependencies, file);
    }

    private void extractPackageReferences(JsonNode root, String sourceComponentId,
                                         List<Dependency> dependencies, Path file) {
        JsonNode itemGroups = ensureArray(root.get(ITEM_GROUP));
        if (itemGroups == null) {
            return;
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode packageRefs = ensureArray(itemGroup.get(PACKAGE_REFERENCE));
            if (packageRefs == null) {
                continue;
            }

            for (JsonNode pkgRef : packageRefs) {
                String packageId = extractAttribute(pkgRef, INCLUDE);
                String version = extractAttribute(pkgRef, VERSION);

                if (packageId != null && version != null) {
                    addDependency(sourceComponentId, packageId, version, dependencies);
                    log.debug("Found NuGet package from {}: {} {}", file.getFileName(), packageId, version);
                }
            }
        }
    }

    private void extractLegacyReferences(JsonNode root, String sourceComponentId,
                                        List<Dependency> dependencies, Path file) {
        JsonNode itemGroups = ensureArray(root.get(ITEM_GROUP));
        if (itemGroups == null) {
            return;
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode references = ensureArray(itemGroup.get(REFERENCE));
            if (references == null) {
                continue;
            }

            for (JsonNode reference : references) {
                String include = extractAttribute(reference, INCLUDE);
                if (include == null) {
                    continue;
                }

                String packageId = extractPackageNameFromInclude(include);
                if (isSystemAssembly(packageId)) {
                    continue;
                }

                String version = extractVersionFromInclude(include);
                if (version == null) {
                    JsonNode hintPath = reference.get(HINT_PATH);
                    if (hintPath != null) {
                        version = extractVersionFromHintPath(hintPath.asText());
                    }
                }

                version = version != null ? version : UNKNOWN_VERSION;

                addDependency(sourceComponentId, packageId, version, dependencies);
                log.debug("Found legacy reference from {}: {} {}", file.getFileName(), packageId, version);
            }
        }
    }

    private void parsePackagesConfig(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        JsonNode root = parseXml(file);

        JsonNode packages = ensureArray(root.get(PACKAGE));
        if (packages == null) {
            return;
        }

        for (JsonNode pkg : packages) {
            String packageId = extractAttribute(pkg, ID);
            String version = extractAttribute(pkg, VERSION_ATTRIBUTE);

            if (packageId != null && version != null) {
                addDependency(sourceComponentId, packageId, version, dependencies);
                log.debug("Found package from packages.config: {} {}", packageId, version);
            }
        }
    }

    private void parseDirectoryBuildProps(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        JsonNode root = parseXml(file);

        JsonNode itemGroups = ensureArray(root.get(ITEM_GROUP));
        if (itemGroups == null) {
            return;
        }

        for (JsonNode itemGroup : itemGroups) {
            JsonNode packageRefs = ensureArray(itemGroup.get(PACKAGE_REFERENCE));
            if (packageRefs == null) {
                continue;
            }

            for (JsonNode pkgRef : packageRefs) {
                String packageId = extractAttribute(pkgRef, UPDATE);
                if (packageId == null) {
                    packageId = extractAttribute(pkgRef, INCLUDE);
                }

                String version = extractAttribute(pkgRef, VERSION);

                if (packageId != null && version != null) {
                    addDependency(sourceComponentId, packageId, version, dependencies);
                    log.debug("Found centralized package: {} {}", packageId, version);
                }
            }
        }
    }

    private JsonNode ensureArray(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        return xmlMapper.createArrayNode().add(node);
    }

    private void addDependency(String sourceComponentId, String packageId, String version, List<Dependency> dependencies) {
        Dependency dep = new Dependency(
            sourceComponentId,
            GROUP_ID_NUGET,
            packageId,
            version,
            SCOPE_COMPILE,
            true
        );
        dependencies.add(dep);
    }

    private boolean isSystemAssembly(String packageId) {
        return packageId.startsWith(SYSTEM_ASSEMBLY_PREFIX) || packageId.equals(MSCORLIB);
    }

    private String extractPackageNameFromInclude(String include) {
        String[] parts = include.split(COMMA_DELIMITER);
        return parts.length > 0 ? parts[0].trim() : include;
    }

    @Override
    protected String extractAttribute(JsonNode node, String attributeName) {
        JsonNode attr = node.get(attributeName);
        if (attr != null && attr.isTextual()) {
            return attr.asText();
        }
        return null;
    }

    private String extractVersionFromInclude(String include) {
        String[] parts = include.split(COMMA_DELIMITER);
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith(VERSION_PREFIX)) {
                String version = part.substring(VERSION_PREFIX.length());
                String[] versionParts = version.split(VERSION_SEPARATOR);
                if (versionParts.length >= MIN_VERSION_PARTS) {
                    return versionParts[VERSION_MAJOR_INDEX] + DOT + 
                           versionParts[VERSION_MINOR_INDEX] + DOT + 
                           versionParts[VERSION_PATCH_INDEX];
                }
                return version;
            }
        }
        return null;
    }

    private String extractVersionFromHintPath(String hintPath) {
        String[] parts = hintPath.split(PATH_SEPARATOR_REGEX);
        for (String part : parts) {
            if (part.contains(DOT)) {
                int lastDot = part.lastIndexOf('.');
                if (lastDot > 0) {
                    String possibleVersion = part.substring(lastDot + 1);
                    if (!possibleVersion.isEmpty() && Character.isDigit(possibleVersion.charAt(0))) {
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
