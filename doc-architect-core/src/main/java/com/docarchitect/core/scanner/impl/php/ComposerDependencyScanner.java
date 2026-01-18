package com.docarchitect.core.scanner.impl.php;

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
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Scanner for PHP Composer dependency declarations in composer.json files.
 *
 * <p>This scanner parses Composer package files using Jackson ObjectMapper to extract
 * dependency information. It handles both production dependencies ({@code require}) and
 * development dependencies ({@code require-dev}).
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate composer.json files using pattern matching</li>
 *   <li>Parse JSON using Jackson ObjectMapper</li>
 *   <li>Extract dependencies from require and require-dev sections</li>
 *   <li>Create Component for the PHP project</li>
 *   <li>Create Dependency records for each Composer package</li>
 * </ol>
 *
 * <p><b>Version Constraints:</b>
 * Composer uses semantic versioning with various constraint formats:
 * <ul>
 *   <li>{@code ^8.0} - Compatible with 8.x</li>
 *   <li>{@code ~5.4} - Approximately 5.4.x</li>
 *   <li>{@code >=1.0 <2.0} - Range constraints</li>
 *   <li>{@code dev-main} - Development branches</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new ComposerDependencyScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-php-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<Dependency> dependencies = result.dependencies();
 * }</pre>
 *
 * @see Dependency
 * @since 1.0.0
 */
public class ComposerDependencyScanner extends AbstractJacksonScanner {

    private static final String SCANNER_ID = "composer-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "Composer Dependency Scanner";
    private static final String COMPOSER_FILE_PATTERN = "**/composer.json";
    private static final int SCANNER_PRIORITY = 10;

    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TYPE = "type";
    private static final String KEY_REQUIRE = "require";
    private static final String KEY_REQUIRE_DEV = "require-dev";

    private static final String DEFAULT_TYPE = "library";
    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String DEFAULT_SOURCE_COMPONENT = "unknown";
    private static final String COMPONENT_DESCRIPTION_PREFIX = "Composer project: ";
    private static final String COMPONENT_TECH = "composer";

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
        return Set.of(Technologies.PHP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(COMPOSER_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, COMPOSER_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Composer dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        List<Path> composerFiles = context.findFiles(COMPOSER_FILE_PATTERN).toList();

        if (composerFiles.isEmpty()) {
            log.warn("No composer.json files found in project");
            return emptyResult();
        }

        for (Path composerFile : composerFiles) {
            try {
                parseComposerFile(composerFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse composer.json: {}", composerFile, e);
                return failedResult(List.of("Failed to parse Composer file: " + composerFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Composer dependencies across {} composer.json files", dependencies.size(), composerFiles.size());

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
     * Parses a single composer.json file and extracts dependencies.
     *
     * @param composerFile path to composer.json
     * @param dependencies list to add discovered dependencies
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    private void parseComposerFile(Path composerFile, List<Dependency> dependencies, List<Component> components)
            throws IOException {
        JsonNode root = parseJson(composerFile);

        // Extract project metadata
        String name = extractText(root, KEY_NAME);
        String description = extractText(root, KEY_DESCRIPTION);
        String type = extractTextOrDefault(root, KEY_TYPE, DEFAULT_TYPE);

        // Determine component type based on Composer package type
        ComponentType componentType = determineComponentType(type);

        // Create component for this Composer project
        if (name != null) {
            String[] nameParts = splitPackageName(name);
            String vendor = nameParts[0];
            String packageName = nameParts[1];

            Component component = new Component(
                IdGenerator.generate("composer", vendor, packageName),
                packageName,
                componentType,
                COMPONENT_DESCRIPTION_PREFIX + name,
                COMPONENT_TECH,
                composerFile.getParent().toString(),
                Map.of(
                    "vendor", Objects.toString(vendor, ""),
                    "type", type,
                    "description", Objects.toString(description, "")
                )
            );
            components.add(component);
        }

        // Use package name as source component ID, or default if not available
        String sourceComponentId = name != null ? splitPackageName(name)[1] : DEFAULT_SOURCE_COMPONENT;

        // Extract production dependencies
        extractDependencies(root, KEY_REQUIRE, sourceComponentId, SCOPE_COMPILE, dependencies);

        // Extract development dependencies
        extractDependencies(root, KEY_REQUIRE_DEV, sourceComponentId, SCOPE_TEST, dependencies);
    }

    /**
     * Extracts dependencies from a composer.json section.
     *
     * @param root root JsonNode of composer.json
     * @param section section name ("require" or "require-dev")
     * @param sourceComponentId source component for the dependencies
     * @param scope dependency scope ("compile" or "test")
     * @param dependencies list to add dependencies
     */
    private void extractDependencies(JsonNode root, String section, String sourceComponentId,
                                     String scope, List<Dependency> dependencies) {
        JsonNode sectionNode = root.get(section);
        if (sectionNode == null || !sectionNode.isObject()) {
            return;
        }

        sectionNode.fields().forEachRemaining(entry -> {
            String packageName = entry.getKey();
            String version = entry.getValue().asText();

            // Skip PHP platform requirements (php, ext-*, lib-*)
            if (isPlatformRequirement(packageName)) {
                log.debug("Skipping platform requirement: {}", packageName);
                return;
            }

            String[] nameParts = splitPackageName(packageName);
            String vendor = nameParts[0];
            String artifact = nameParts[1];

            Dependency dependency = new Dependency(
                sourceComponentId,
                vendor,  // groupId = vendor
                artifact, // artifactId = package name
                version,
                scope,
                true // All composer.json dependencies are direct dependencies
            );
            dependencies.add(dependency);
        });
    }

    /**
     * Splits a Composer package name into vendor and package parts.
     *
     * <p>Composer packages follow the format "vendor/package". If no vendor is
     * specified (e.g., "php"), the vendor defaults to "_platform".
     *
     * @param packageName full package name (e.g., "laravel/framework")
     * @return array with [vendor, package]
     */
    private String[] splitPackageName(String packageName) {
        if (packageName == null) {
            return new String[]{"unknown", "unknown"};
        }

        int slashIndex = packageName.indexOf('/');
        if (slashIndex > 0) {
            return new String[]{
                packageName.substring(0, slashIndex),
                packageName.substring(slashIndex + 1)
            };
        }

        // No vendor specified (e.g., platform packages)
        return new String[]{"_platform", packageName};
    }

    /**
     * Checks if a package name is a PHP platform requirement.
     *
     * <p>Platform requirements include:
     * <ul>
     *   <li>{@code php} - PHP version requirement</li>
     *   <li>{@code ext-*} - PHP extension requirements</li>
     *   <li>{@code lib-*} - System library requirements</li>
     *   <li>{@code composer-*} - Composer plugin API requirements</li>
     * </ul>
     *
     * @param packageName package name to check
     * @return true if this is a platform requirement
     */
    private boolean isPlatformRequirement(String packageName) {
        if (packageName == null) {
            return false;
        }
        return packageName.equals("php")
            || packageName.startsWith("ext-")
            || packageName.startsWith("lib-")
            || packageName.startsWith("composer-");
    }

    /**
     * Determines the component type based on Composer package type.
     *
     * <p>Common Composer package types:
     * <ul>
     *   <li>{@code project} - Application project (SERVICE)</li>
     *   <li>{@code library} - Reusable library (LIBRARY)</li>
     *   <li>{@code metapackage} - Virtual package (LIBRARY)</li>
     *   <li>{@code composer-plugin} - Composer plugin (LIBRARY)</li>
     * </ul>
     *
     * @param composerType Composer package type
     * @return appropriate ComponentType
     */
    private ComponentType determineComponentType(String composerType) {
        if ("project".equals(composerType)) {
            return ComponentType.SERVICE;
        }
        return ComponentType.LIBRARY;
    }

    /**
     * Extracts text value from a JsonNode with a default fallback.
     *
     * @param node parent JsonNode
     * @param fieldName field to extract
     * @param defaultValue default if not found
     * @return text value or default
     */
    private String extractTextOrDefault(JsonNode node, String fieldName, String defaultValue) {
        String value = extractText(node, fieldName);
        return value != null ? value : defaultValue;
    }
}
