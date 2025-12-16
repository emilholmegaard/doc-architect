package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJacksonScanner;
import com.docarchitect.core.util.IdGenerator;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Maven dependency declarations in pom.xml files.
 *
 * <p>This scanner parses Maven POM files using Jackson XmlMapper to extract dependency information.
 * It handles property placeholders like {@code ${project.version}} and {@code ${spring.version}}
 * by resolving them from the POM's properties section.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate pom.xml files using pattern matching</li>
 *   <li>Parse XML using Jackson XmlMapper (not regex)</li>
 *   <li>Extract dependencies from dependencyManagement and dependencies sections</li>
 *   <li>Resolve property placeholders from properties section</li>
 *   <li>Create Dependency records for each Maven dependency</li>
 * </ol>
 *
 * <p><b>Property Resolution:</b>
 * Supports common Maven properties:
 * <ul>
 *   <li>{@code ${project.version}} - Resolves to project version</li>
 *   <li>{@code ${project.groupId}} - Resolves to project groupId</li>
 *   <li>{@code ${spring.version}} - Custom properties from properties section</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new MavenDependencyScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     "my-project",
 *     new HashSet<>(scanner.getSupportedFilePatterns())
 * );
 * ScanResult result = scanner.scan(context);
 * List<Dependency> dependencies = result.dependencies();
 * }</pre>
 *
 * @see Dependency
 * @since 1.0.0
 */
public class MavenDependencyScanner extends AbstractJacksonScanner {

    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final String SCANNER_ID = "maven-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "Maven Dependency Scanner";
    private static final String POM_FILE_PATTERN = "**/pom.xml";
    private static final int SCANNER_PRIORITY = 10;

    private static final String KEY_GROUP_ID = "groupId";
    private static final String KEY_ARTIFACT_ID = "artifactId";
    private static final String KEY_VERSION = "version";
    private static final String KEY_PACKAGING = "packaging";
    private static final String KEY_PARENT = "parent";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_DEPENDENCIES = "dependencies";
    private static final String KEY_DEPENDENCY_MANAGEMENT = "dependencyManagement";
    private static final String KEY_DEPENDENCY = "dependency";
    private static final String KEY_SCOPE = "scope";

    private static final String DEFAULT_PACKAGING = "jar";
    private static final String DEFAULT_SCOPE = "compile";
    private static final String DEFAULT_SOURCE_COMPONENT = "unknown";
    private static final String COMPONENT_DESCRIPTION_PREFIX = "Maven project: ";
    private static final String COMPONENT_TECH = "maven";

    private static final String PROPERTY_PROJECT_GROUP_ID = "project.groupId";
    private static final String PROPERTY_PROJECT_ARTIFACT_ID = "project.artifactId";
    private static final String PROPERTY_PROJECT_VERSION = "project.version";

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
        return Set.of(Technologies.JAVA, Technologies.KOTLIN, Technologies.GROOVY, Technologies.SCALA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(POM_FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, POM_FILE_PATTERN);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Maven dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        // Find all pom.xml files
        List<Path> pomFiles = context.findFiles(POM_FILE_PATTERN).toList();

        if (pomFiles.isEmpty()) {
            log.warn("No pom.xml files found in project");
            return emptyResult();
        }

        for (Path pomFile : pomFiles) {
            try {
                parsePomFile(pomFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse pom.xml: {}", pomFile, e);
                return failedResult(List.of("Failed to parse Maven POM file: " + pomFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Maven dependencies across {} POM files", dependencies.size(), pomFiles.size());

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
     * Parses a single pom.xml file and extracts dependencies.
     *
     * @param pomFile path to pom.xml
     * @param dependencies list to add discovered dependencies
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    private void parsePomFile(Path pomFile, List<Dependency> dependencies, List<Component> components) throws IOException {
        String content = readFileContent(pomFile);

        // Parse POM using Jackson
        @SuppressWarnings("unchecked")
        Map<String, Object> pom = xmlMapper.readValue(content, Map.class);

        // Extract project coordinates
        String groupId = extractText(pom, KEY_GROUP_ID);
        String artifactId = extractText(pom, KEY_ARTIFACT_ID);
        String version = extractText(pom, KEY_VERSION);
        String packaging = extractText(pom, KEY_PACKAGING, DEFAULT_PACKAGING);

        // Handle parent POM inheritance
        if (groupId == null || version == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parent = (Map<String, Object>) pom.get(KEY_PARENT);
            if (parent != null) {
                if (groupId == null) {
                    groupId = extractText(parent, "groupId");
                }
                if (version == null) {
                    version = extractText(parent, "version");
                }
            }
        }

        // Build properties map for placeholder resolution
        Map<String, String> properties = new HashMap<>();
        if (groupId != null) properties.put(PROPERTY_PROJECT_GROUP_ID, groupId);
        if (artifactId != null) properties.put(PROPERTY_PROJECT_ARTIFACT_ID, artifactId);
        if (version != null) properties.put(PROPERTY_PROJECT_VERSION, version);

        // Extract custom properties
        @SuppressWarnings("unchecked")
        Map<String, Object> propertiesSection = (Map<String, Object>) pom.get(KEY_PROPERTIES);
        if (propertiesSection != null) {
            propertiesSection.forEach((key, value) ->
                properties.put(key, String.valueOf(value))
            );
        }

        // Create component for this Maven project
        if (artifactId != null) {
            Component component = new Component(
                IdGenerator.generate("maven", groupId, artifactId),
                artifactId,
                "jar".equals(packaging) ? ComponentType.LIBRARY : ComponentType.SERVICE,
                COMPONENT_DESCRIPTION_PREFIX + (groupId != null ? groupId + ":" : "") + artifactId,
                COMPONENT_TECH,
                pomFile.getParent().toString(),
                Map.of(
                    "groupId", Objects.toString(groupId, ""),
                    "version", Objects.toString(version, ""),
                    "packaging", packaging
                )
            );
            components.add(component);
        }

        // Extract dependencies
        extractDependencies(pom, KEY_DEPENDENCIES, properties, dependencies);
        extractDependencies(pom, KEY_DEPENDENCY_MANAGEMENT, properties, dependencies);
    }

    /**
     * Extracts dependencies from a POM section.
     *
     * @param pom parsed POM map
     * @param section section name ("dependencies" or "dependencyManagement")
     * @param properties property map for placeholder resolution
     * @param dependencies list to add dependencies
     */
    @SuppressWarnings("unchecked")
    private void extractDependencies(Map<String, Object> pom, String section,
                                     Map<String, String> properties, List<Dependency> dependencies) {
        Object sectionObj = pom.get(section);
        if (sectionObj == null) {
            return;
        }

        // Handle dependencyManagement wrapper
        if (KEY_DEPENDENCY_MANAGEMENT.equals(section) && sectionObj instanceof Map) {
            Map<String, Object> depMgmt = (Map<String, Object>) sectionObj;
            sectionObj = depMgmt.get(KEY_DEPENDENCIES);
            if (sectionObj == null) {
                return;
            }
        }

        List<Map<String, Object>> depsList;
        if (sectionObj instanceof Map) {
            Map<String, Object> depsMap = (Map<String, Object>) sectionObj;
            Object dependency = depsMap.get(KEY_DEPENDENCY);
            if (dependency instanceof List) {
                depsList = (List<Map<String, Object>>) dependency;
            } else if (dependency instanceof Map) {
                depsList = List.of((Map<String, Object>) dependency);
            } else {
                return;
            }
        } else if (sectionObj instanceof List) {
            depsList = (List<Map<String, Object>>) sectionObj;
        } else {
            return;
        }

        for (Map<String, Object> dep : depsList) {
            String groupId = resolveProperties(extractText(dep, KEY_GROUP_ID), properties);
            String artifactId = extractText(dep, KEY_ARTIFACT_ID);
            String versionRaw = extractText(dep, KEY_VERSION);
            String version = versionRaw != null ? resolveProperties(versionRaw, properties) : null;
            String scope = extractText(dep, KEY_SCOPE, DEFAULT_SCOPE);

            if (groupId != null && artifactId != null) {
                // Use current component as source (extracted from properties map)
                String sourceComponentId = properties.getOrDefault(PROPERTY_PROJECT_ARTIFACT_ID, DEFAULT_SOURCE_COMPONENT);

                Dependency dependency = new Dependency(
                    sourceComponentId,
                    groupId,
                    artifactId,
                    version,
                    scope,
                    true // All POM dependencies are direct dependencies
                );
                dependencies.add(dependency);
            }
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
        return extractText(map, key, null);
    }

    /**
     * Extracts text value from a map with default fallback.
     *
     * @param map source map
     * @param key key to extract
     * @param defaultValue default if not found
     * @return text value or default
     */
    private String extractText(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    /**
     * Resolves Maven property placeholders in a string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ${project.version}} → "1.0.0-SNAPSHOT"</li>
     *   <li>{@code ${spring.version}} → "3.2.1"</li>
     * </ul>
     *
     * @param value string possibly containing placeholders
     * @param properties property map
     * @return resolved string
     */
    private String resolveProperties(String value, Map<String, String> properties) {
        if (value == null) {
            return null;
        }

        Matcher matcher = PROPERTY_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String propertyName = matcher.group(1);
            String propertyValue = properties.getOrDefault(propertyName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(propertyValue));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
