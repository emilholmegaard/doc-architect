package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Relationship;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scanner for .NET solution (.sln) files to extract project components.
 *
 * <p>This scanner parses Visual Studio solution files to discover all projects
 * and their relationships within a .NET solution. It extracts:
 * <ul>
 *   <li>Individual .csproj, .vbproj, .fsproj projects as components</li>
 *   <li>Solution folders as logical groupings</li>
 *   <li>Project-to-project dependencies from .csproj references</li>
 * </ul>
 *
 * <p><b>Solution File Format:</b></p>
 * <pre>{@code
 * Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Web", "src\Web\Web.csproj", "{GUID}"
 * EndProject
 * Project("{FAE04EC0-301F-11D3-BF4B-00C04F79EFBC}") = "Core", "src\Core\Core.csproj", "{GUID}"
 * EndProject
 * }</pre>
 *
 * <p><b>Component Extraction:</b>
 * Each project in the solution becomes a Component with:
 * <ul>
 *   <li>Name from solution file</li>
 *   <li>Type based on project type (library, web app, etc.)</li>
 *   <li>Metadata including project GUID and path</li>
 * </ul>
 *
 * <p><b>Priority:</b> 12 (after dependency scanners, with other component scanners)</p>
 *
 * @see Component
 * @since 1.0.0
 */
public class SolutionFileScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "dotnet-solution";
    private static final String SCANNER_DISPLAY_NAME = ".NET Solution Scanner";
    private static final String SLN_FILE_PATTERN_NESTED = "**/*.sln";
    private static final String SLN_FILE_PATTERN_ROOT = "*.sln";
    private static final int SCANNER_PRIORITY = 12;

    // Regex pattern for solution file project entries
    // Format: Project("{TYPE-GUID}") = "ProjectName", "RelativePath", "{PROJECT-GUID}"
    private static final Pattern PROJECT_PATTERN = Pattern.compile(
        "Project\\(\"\\{[^}]+\\}\"\\)\\s*=\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*,\\s*\"(\\{[^}]+\\})\"",
        Pattern.MULTILINE
    );

    private static final String TECHNOLOGY = ".NET";

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.CSHARP, Technologies.DOTNET);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(SLN_FILE_PATTERN_NESTED, SLN_FILE_PATTERN_ROOT);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, SLN_FILE_PATTERN_NESTED, SLN_FILE_PATTERN_ROOT);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning .NET solution files in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Set<String> processedProjects = new HashSet<>();

        List<Path> slnFiles = Stream.concat(
            context.findFiles(SLN_FILE_PATTERN_NESTED),
            context.findFiles(SLN_FILE_PATTERN_ROOT)
        ).toList();

        if (slnFiles.isEmpty()) {
            log.debug("No .sln files found");
            return emptyResult();
        }

        for (Path slnFile : slnFiles) {
            try {
                parseSolutionFile(slnFile, context, components, processedProjects);
            } catch (IOException e) {
                log.error("Failed to parse solution file: {}", slnFile, e);
                return failedResult(List.of("Failed to parse .sln file: " + slnFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} projects in {} solution file(s)", components.size(), slnFiles.size());

        return buildSuccessResult(
            components,
            List.of(), // No external dependencies (handled by NuGetDependencyScanner)
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            relationships,
            List.of()  // No warnings
        );
    }

    /**
     * Parses a .sln file and extracts project components.
     *
     * @param slnFile path to solution file
     * @param context scan context
     * @param components list to add discovered components
     * @param processedProjects set of processed project names (for deduplication)
     * @throws IOException if file cannot be read
     */
    private void parseSolutionFile(Path slnFile, ScanContext context,
                                   List<Component> components, Set<String> processedProjects) throws IOException {
        String content = readFileContent(slnFile);

        Matcher matcher = PROJECT_PATTERN.matcher(content);

        while (matcher.find()) {
            String projectName = matcher.group(1);
            String projectPath = matcher.group(2);
            String projectGuid = matcher.group(3);

            // Skip solution folders (they're not real projects)
            if (projectPath.endsWith(".sln")) {
                continue;
            }

            // Skip if already processed
            if (processedProjects.contains(projectName)) {
                continue;
            }

            ComponentType componentType = determineComponentType(projectPath);
            String componentId = IdGenerator.generate("dotnet-project", projectName);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("projectGuid", projectGuid);
            metadata.put("projectPath", projectPath);
            metadata.put("solution", slnFile.getFileName().toString());

            Component component = new Component(
                componentId,
                projectName,
                componentType,
                ".NET project: " + projectName,
                TECHNOLOGY,
                slnFile.getParent().resolve(projectPath).getParent().toString(),
                metadata
            );

            components.add(component);
            processedProjects.add(projectName);

            log.debug("Found .NET project: {} (type={}, path={})", projectName, componentType, projectPath);
        }
    }

    /**
     * Determines component type from project file extension and path.
     *
     * @param projectPath relative path to project file
     * @return component type
     */
    private ComponentType determineComponentType(String projectPath) {
        String lowerPath = projectPath.toLowerCase();

        // Web projects
        if (lowerPath.contains("web") || lowerPath.contains("api") || lowerPath.contains("mvc")) {
            return ComponentType.SERVICE;
        }

        // Test projects
        if (lowerPath.contains("test") || lowerPath.contains("tests")) {
            return ComponentType.MODULE;
        }

        // Infrastructure/data access
        if (lowerPath.contains("infrastructure") || lowerPath.contains("data") || lowerPath.contains("persistence")) {
            return ComponentType.MODULE;
        }

        // Core/domain
        if (lowerPath.contains("core") || lowerPath.contains("domain")) {
            return ComponentType.MODULE;
        }

        // Default to library
        return ComponentType.LIBRARY;
    }
}
