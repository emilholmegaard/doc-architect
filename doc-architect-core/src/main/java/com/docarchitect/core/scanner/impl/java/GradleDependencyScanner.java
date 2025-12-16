package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Gradle dependency declarations in build.gradle and build.gradle.kts files.
 *
 * <p>This scanner uses regex patterns to extract dependency information from both Groovy DSL
 * (build.gradle) and Kotlin DSL (build.gradle.kts) build files.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate build.gradle and build.gradle.kts files using pattern matching</li>
 *   <li>Extract dependencies using regex patterns for three notation styles</li>
 *   <li>Create Dependency records for each Gradle dependency</li>
 * </ol>
 *
 * <p><b>Supported Dependency Notations:</b>
 * <ul>
 *   <li><b>String notation:</b> {@code implementation 'org.springframework:spring-core:5.3.0'}</li>
 *   <li><b>Kotlin function:</b> {@code implementation("org.springframework:spring-core:5.3.0")}</li>
 *   <li><b>Map notation:</b> {@code implementation group: 'org.springframework', name: 'spring-core', version: '5.3.0'}</li>
 * </ul>
 *
 * <p><b>Supported Configurations:</b>
 * implementation, api, compileOnly, runtimeOnly, testImplementation, testRuntimeOnly, etc.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GradleDependencyScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<Dependency> dependencies = result.dependencies();
 * }</pre>
 *
 * @see Scanner
 * @see Dependency
 * @since 1.0.0
 */
public class GradleDependencyScanner extends AbstractRegexScanner {

    // Regex for string notation: implementation 'group:artifact:version'
    private static final Pattern STRING_NOTATION = Pattern.compile(
        "(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s*[\"']([^:]+):([^:]+):([^\"']+)[\"']"
    );

    // Regex for Kotlin function notation: implementation("group:artifact:version")
    private static final Pattern KOTLIN_NOTATION = Pattern.compile(
        "(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s*\\(\\s*[\"']([^:]+):([^:]+):([^\"']+)[\"']\\s*\\)"
    );

    // Regex for map notation: implementation group: 'group', name: 'artifact', version: 'version'
    private static final Pattern MAP_NOTATION = Pattern.compile(
        "(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|annotationProcessor)\\s+group:\\s*[\"']([^\"']+)[\"']\\s*,\\s*name:\\s*[\"']([^\"']+)[\"']\\s*,\\s*version:\\s*[\"']([^\"']+)[\"']"
    );

    @Override
    public String getId() {
        return "gradle-dependencies";
    }

    @Override
    public String getDisplayName() {
        return "Gradle Dependency Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.JAVA, Technologies.KOTLIN, Technologies.GROOVY, Technologies.SCALA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/build.gradle", "**/build.gradle.kts");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/build.gradle", "**/build.gradle.kts");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Gradle dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all build.gradle and build.gradle.kts files
        List<Path> gradleFiles = new ArrayList<>();
        gradleFiles.addAll(context.findFiles("**/build.gradle").toList());
        gradleFiles.addAll(context.findFiles("**/build.gradle.kts").toList());

        if (gradleFiles.isEmpty()) {
            log.warn("No build.gradle or build.gradle.kts files found in project");
            return emptyResult();
        }

        for (Path gradleFile : gradleFiles) {
            try {
                parseBuildFile(gradleFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse Gradle build file: {}", gradleFile, e);
                return failedResult(List.of("Failed to parse Gradle build file: " + gradleFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Gradle dependencies across {} build files", dependencies.size(), gradleFiles.size());

        return buildSuccessResult(
            components,
            dependencies,
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of()  // No warnings
        );
    }

    /**
     * Parses a single build.gradle or build.gradle.kts file and extracts dependencies.
     *
     * @param buildFile path to build.gradle or build.gradle.kts
     * @param dependencies list to add discovered dependencies
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    private void parseBuildFile(Path buildFile, List<Dependency> dependencies, List<Component> components) throws IOException {
        String content = readFileContent(buildFile);
        String fileName = buildFile.getFileName().toString();
        boolean isKotlinDsl = fileName.endsWith(".kts");

        // Try to extract project name from settings or directory
        String projectName = buildFile.getParent().getFileName().toString();

        // Create component for this Gradle project
        Component component = new Component(
            IdGenerator.generate("gradle", projectName),
            projectName,
            ComponentType.SERVICE,
            "Gradle project: " + projectName,
            "gradle",
            buildFile.getParent().toString(),
            Map.of(
                "buildFile", fileName,
                "dsl", isKotlinDsl ? "kotlin" : "groovy"
            )
        );
        components.add(component);

        // Extract dependencies using all three patterns
        extractDependencies(content, STRING_NOTATION, dependencies, projectName);
        extractDependencies(content, KOTLIN_NOTATION, dependencies, projectName);
        extractDependencies(content, MAP_NOTATION, dependencies, projectName);
    }

    /**
     * Extracts dependencies from build file content using a regex pattern.
     *
     * @param content build file content
     * @param pattern regex pattern to match dependencies
     * @param dependencies list to add discovered dependencies
     * @param sourceComponentId source component identifier
     */
    private void extractDependencies(String content, Pattern pattern, List<Dependency> dependencies, String sourceComponentId) {
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String configuration = matcher.group(1); // implementation, api, etc.
            String groupId = matcher.group(2);
            String artifactId = matcher.group(3);
            String version = matcher.group(4);

            // Map Gradle configuration to Maven scope equivalent
            String scope = mapConfigurationToScope(configuration);

            Dependency dependency = new Dependency(
                sourceComponentId,
                groupId,
                artifactId,
                version,
                scope,
                true // All build.gradle dependencies are direct dependencies
            );
            dependencies.add(dependency);

            log.debug("Found Gradle dependency: {}:{}:{} ({})", groupId, artifactId, version, configuration);
        }
    }

    /**
     * Maps Gradle configuration to Maven scope equivalent.
     *
     * @param configuration Gradle configuration (implementation, api, etc.)
     * @return Maven scope (compile, runtime, test, etc.)
     */
    private String mapConfigurationToScope(String configuration) {
        return switch (configuration) {
            case "implementation", "api" -> "compile";
            case "compileOnly" -> "provided";
            case "runtimeOnly" -> "runtime";
            case "testImplementation", "testRuntimeOnly" -> "test";
            case "annotationProcessor" -> "provided";
            default -> "compile";
        };
    }
}
