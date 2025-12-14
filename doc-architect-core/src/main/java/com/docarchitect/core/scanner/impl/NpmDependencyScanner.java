package com.docarchitect.core.scanner.impl;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Scanner for npm dependency declarations in package.json files.
 *
 * <p>This scanner parses npm package.json files using Jackson ObjectMapper to extract dependency information.
 * It handles all npm dependency types: dependencies, devDependencies, and peerDependencies.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate package.json files using pattern matching</li>
 *   <li>Parse JSON using Jackson ObjectMapper</li>
 *   <li>Extract dependencies from dependencies, devDependencies, and peerDependencies sections</li>
 *   <li>Map to appropriate dependency scopes (compile, test, provided)</li>
 *   <li>Create Component for the npm package</li>
 *   <li>Create Dependency records for each npm dependency</li>
 * </ol>
 *
 * <p><b>Dependency Scope Mapping:</b>
 * <ul>
 *   <li>{@code dependencies} → scope "compile" (runtime dependencies)</li>
 *   <li>{@code devDependencies} → scope "test" (development-only dependencies)</li>
 *   <li>{@code peerDependencies} → scope "provided" (peer dependencies)</li>
 * </ul>
 *
 * <p><b>Version Formats:</b>
 * Supports standard npm version formats:
 * <ul>
 *   <li>Exact: "1.2.3"</li>
 *   <li>Range: "^1.2.3", "~1.2.3", ">=1.2.3"</li>
 *   <li>Git URLs: "git+https://github.com/user/repo.git"</li>
 *   <li>File paths: "file:../local-package"</li>
 *   <li>Latest: "*" or "latest"</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new NpmDependencyScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<Dependency> dependencies = result.dependencies();
 * List<Component> components = result.components();
 * }</pre>
 *
 * @see Scanner
 * @see Dependency
 * @see Component
 * @since 1.0.0
 */
public class NpmDependencyScanner implements Scanner {

    private static final Logger log = LoggerFactory.getLogger(NpmDependencyScanner.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return "npm-dependencies";
    }

    @Override
    public String getDisplayName() {
        return "npm Dependency Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("javascript", "typescript");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/package.json");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Check if any package.json files exist
        return context.findFiles("**/package.json").findAny().isPresent();
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning npm dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all package.json files
        List<Path> packageJsonFiles = context.findFiles("**/package.json").toList();

        if (packageJsonFiles.isEmpty()) {
            log.warn("No package.json files found in project");
            return ScanResult.empty(getId());
        }

        for (Path packageJsonFile : packageJsonFiles) {
            try {
                parsePackageJson(packageJsonFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse package.json: {}", packageJsonFile, e);
                return ScanResult.failed(getId(), List.of("Failed to parse package.json file: " + packageJsonFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} npm dependencies across {} package.json files", dependencies.size(), packageJsonFiles.size());

        return new ScanResult(
            getId(),
            true, // success
            components,
            dependencies,
            List.of(), // No API endpoints
            List.of(), // No message flows
            List.of(), // No data entities
            List.of(), // No relationships
            List.of(), // No warnings
            List.of()  // No errors
        );
    }

    /**
     * Parses a single package.json file and extracts dependencies.
     *
     * @param packageJsonFile path to package.json
     * @param dependencies list to add discovered dependencies
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    @SuppressWarnings("unchecked")
    private void parsePackageJson(Path packageJsonFile, List<Dependency> dependencies, List<Component> components) throws IOException {
        String content = Files.readString(packageJsonFile);

        // Parse package.json using Jackson ObjectMapper
        Map<String, Object> packageJson = objectMapper.readValue(content, Map.class);

        // Extract package metadata
        String packageName = extractText(packageJson, "name");
        String version = extractText(packageJson, "version");
        String description = extractText(packageJson, "description");

        if (packageName == null) {
            log.warn("package.json missing 'name' field: {}", packageJsonFile);
            packageName = "unknown";
        }

        // Create component for this npm package
        Component component = new Component(
            IdGenerator.generate("npm", packageName),
            packageName,
            ComponentType.LIBRARY,
            description != null ? description : "npm package: " + packageName,
            "npm",
            packageJsonFile.getParent().toString(),
            Map.of(
                "version", Objects.toString(version, ""),
                "packageManager", "npm"
            )
        );
        components.add(component);

        // Extract dependencies with different scopes
        extractDependencies(packageJson, "dependencies", "compile", packageName, dependencies);
        extractDependencies(packageJson, "devDependencies", "test", packageName, dependencies);
        extractDependencies(packageJson, "peerDependencies", "provided", packageName, dependencies);
    }

    /**
     * Extracts dependencies from a package.json section.
     *
     * @param packageJson parsed package.json map
     * @param section section name ("dependencies", "devDependencies", "peerDependencies")
     * @param scope dependency scope (compile, test, provided)
     * @param sourceComponentId source component identifier
     * @param dependencies list to add dependencies
     */
    @SuppressWarnings("unchecked")
    private void extractDependencies(Map<String, Object> packageJson, String section,
                                     String scope, String sourceComponentId, List<Dependency> dependencies) {
        Object sectionObj = packageJson.get(section);
        if (sectionObj == null || !(sectionObj instanceof Map)) {
            return;
        }

        Map<String, Object> depsMap = (Map<String, Object>) sectionObj;

        for (Map.Entry<String, Object> entry : depsMap.entrySet()) {
            String packageName = entry.getKey();
            String versionSpec = String.valueOf(entry.getValue());

            // For npm, we use the package name as both groupId and artifactId
            // Some packages have scope like @angular/core, we split on /
            String groupId;
            String artifactId;

            if (packageName.startsWith("@") && packageName.contains("/")) {
                // Scoped package: @angular/core -> groupId=@angular, artifactId=core
                int slashIndex = packageName.indexOf('/');
                groupId = packageName.substring(0, slashIndex);
                artifactId = packageName.substring(slashIndex + 1);
            } else {
                // Regular package: express -> groupId=npm, artifactId=express
                groupId = "npm";
                artifactId = packageName;
            }

            Dependency dependency = new Dependency(
                sourceComponentId,
                groupId,
                artifactId,
                versionSpec,
                scope,
                true // All package.json dependencies are direct dependencies
            );
            dependencies.add(dependency);
        }
    }

    /**
     * Extracts text value from a map, handling null cases.
     *
     * @param map source map
     * @param key key to extract
     * @return text value or null
     */
    private String extractText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }
}
