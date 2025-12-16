package com.docarchitect.core.scanner.impl.javascript;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJacksonScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

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
 * @see Dependency
 * @see Component
 * @since 1.0.0
 */
public class NpmDependencyScanner extends AbstractJacksonScanner {

    // Scanner identification
    private static final String SCANNER_ID = "npm-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "npm Dependency Scanner";

    // File discovery
    private static final String PACKAGE_JSON_GLOB = "**/package.json";
    private static final int PRIORITY = 10;

    // package.json fields
    private static final String FIELD_NAME = "name";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_DESCRIPTION = "description";

    // Dependency sections in package.json
    private static final String SECTION_DEPENDENCIES = "dependencies";
    private static final String SECTION_DEV_DEPENDENCIES = "devDependencies";
    private static final String SECTION_PEER_DEPENDENCIES = "peerDependencies";

    // Dependency scopes
    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_PROVIDED = "provided";

    // NPM specifics
    private static final String PACKAGE_MANAGER_NPM = "npm";
    private static final String METADATA_PACKAGE_MANAGER = "packageManager";
    private static final String DEFAULT_PACKAGE_NAME = "unknown";
    private static final String DESCRIPTION_PREFIX = "npm package: ";

    // Scoped package markers
    private static final String SCOPED_PACKAGE_PREFIX = "@";
    private static final String PACKAGE_SCOPE_SEPARATOR = "/";

    // Error messages
    private static final String ERROR_PARSE_PREFIX = "Failed to parse package.json file: ";

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
        return Set.of(Technologies.JAVASCRIPT, Technologies.TYPESCRIPT);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PACKAGE_JSON_GLOB);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PACKAGE_JSON_GLOB);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning npm dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all package.json files
        List<Path> packageJsonFiles = context.findFiles(PACKAGE_JSON_GLOB).toList();

        if (packageJsonFiles.isEmpty()) {
            log.warn("No package.json files found in project");
            return emptyResult();
        }

        for (Path packageJsonFile : packageJsonFiles) {
            try {
                parsePackageJson(packageJsonFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse package.json: {}", packageJsonFile, e);
                return ScanResult.failed(getId(), List.of(ERROR_PARSE_PREFIX + packageJsonFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} npm dependencies across {} package.json files", dependencies.size(), packageJsonFiles.size());

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
        // Parse package.json using Jackson
        String content = readFileContent(packageJsonFile);
        Map<String, Object> packageJson = objectMapper.readValue(content, Map.class);

        // Extract package metadata
        String packageName = extractText(packageJson, FIELD_NAME);
        String version = extractText(packageJson, FIELD_VERSION);
        String description = extractText(packageJson, FIELD_DESCRIPTION);

        if (packageName == null) {
            log.warn("package.json missing '{}' field: {}", FIELD_NAME, packageJsonFile);
            packageName = DEFAULT_PACKAGE_NAME;
        }

        // Create component for this npm package
        Component component = new Component(
            IdGenerator.generate(PACKAGE_MANAGER_NPM, packageName),
            packageName,
            ComponentType.LIBRARY,
            description != null ? description : DESCRIPTION_PREFIX + packageName,
            PACKAGE_MANAGER_NPM,
            packageJsonFile.getParent().toString(),
            Map.of(
                FIELD_VERSION, Objects.toString(version, ""),
                METADATA_PACKAGE_MANAGER, PACKAGE_MANAGER_NPM
            )
        );
        components.add(component);

        // Extract dependencies with different scopes
        extractDependencies(packageJson, SECTION_DEPENDENCIES, SCOPE_COMPILE, packageName, dependencies);
        extractDependencies(packageJson, SECTION_DEV_DEPENDENCIES, SCOPE_TEST, packageName, dependencies);
        extractDependencies(packageJson, SECTION_PEER_DEPENDENCIES, SCOPE_PROVIDED, packageName, dependencies);
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

            if (packageName.startsWith(SCOPED_PACKAGE_PREFIX) && packageName.contains(PACKAGE_SCOPE_SEPARATOR)) {
                // Scoped package: @angular/core -> groupId=@angular, artifactId=core
                int slashIndex = packageName.indexOf(PACKAGE_SCOPE_SEPARATOR);
                groupId = packageName.substring(0, slashIndex);
                artifactId = packageName.substring(slashIndex + 1);
            } else {
                // Regular package: express -> groupId=npm, artifactId=express
                groupId = PACKAGE_MANAGER_NPM;
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
