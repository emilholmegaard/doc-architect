package com.docarchitect.core.scanner.impl.go;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * Scanner for Go module dependencies in go.mod files.
 *
 * <p>This scanner parses Go module files using regex patterns to extract dependency information.
 * Go modules use a simple text format (not JSON/XML), so regex-based parsing is appropriate.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate go.mod files using pattern matching</li>
 *   <li>Extract module name from first line: {@code module github.com/example/myapp}</li>
 *   <li>Parse require blocks: {@code require ( ... )}</li>
 *   <li>Parse single require statements: {@code require github.com/pkg/errors v0.9.1}</li>
 *   <li>Create Component for the Go module</li>
 *   <li>Create Dependency records for each required module</li>
 * </ol>
 *
 * <p><b>Supported Formats:</b>
 * <ul>
 *   <li>Module declaration: {@code module github.com/user/repo}</li>
 *   <li>Single require: {@code require github.com/pkg/errors v0.9.1}</li>
 *   <li>Require block:
 *     <pre>{@code
 *     require (
 *         github.com/gin-gonic/gin v1.9.1
 *         github.com/stretchr/testify v1.8.4
 *     )
 *     }</pre>
 *   </li>
 *   <li>Indirect dependencies: {@code require github.com/foo/bar v1.0.0 // indirect}</li>
 * </ul>
 *
 * <p><b>Version Formats:</b>
 * Go modules use semantic versioning with 'v' prefix:
 * <ul>
 *   <li>Release: {@code v1.2.3}</li>
 *   <li>Pre-release: {@code v1.2.3-beta.1}</li>
 *   <li>Pseudo-version: {@code v0.0.0-20230101120000-abcdef123456}</li>
 *   <li>Major version suffix: {@code github.com/foo/bar/v2 v2.0.0}</li>
 * </ul>
 *
 * <p><b>Regex Patterns:</b>
 * <ul>
 *   <li>{@code MODULE_PATTERN}: {@code module\s+([\w./-]+)}</li>
 *   <li>{@code REQUIRE_BLOCK_PATTERN}: {@code require\s*\(([^)]+)\)}</li>
 *   <li>{@code REQUIRE_LINE_PATTERN}: {@code ([\w./-]+)\s+(v[\d.]+(?:-[\w.]+)?(?:\+[\w.]+)?)} with optional {@code // indirect}</li>
 *   <li>{@code SINGLE_REQUIRE_PATTERN}: {@code require\s+([\w./-]+)\s+(v[\d.]+(?:-[\w.]+)?(?:\+[\w.]+)?)}</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new GoModScanner();
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
public class GoModScanner extends AbstractRegexScanner {
    private static final String SCANNER_ID = "go-modules";
    private static final String SCANNER_DISPLAY_NAME = "Go Module Scanner";
    private static final String GO_LANGUAGE = "go";
    private static final String GO_MOD_FILE = "go.mod";
    private static final String GO_MOD_GLOB = "**/go.mod";
    private static final Set<String> GO_MOD_PATTERNS = Set.of(GO_MOD_FILE, GO_MOD_GLOB);
    private static final String UNKNOWN_MODULE = "unknown";
    private static final String MODULE_DESCRIPTION_PREFIX = "Go module: ";
    private static final String META_MODULE_PATH = "modulePath";
    private static final String META_PACKAGE_MANAGER = "packageManager";
    private static final String SCOPE_COMPILE = "compile";
    private static final int PRIORITY = 10;

    /**
     * Regex to extract module name: module github.com/user/repo.
     * Captures: (1) module path.
     */
    private static final Pattern MODULE_PATTERN = Pattern.compile(
        "^module\\s+([\\w./-]+)",
        Pattern.MULTILINE
    );

    /**
     * Regex to extract require block: require ( ... ).
     * Captures: (1) content inside parentheses.
     */
    private static final Pattern REQUIRE_BLOCK_PATTERN = Pattern.compile(
        "require\\s*\\(([^)]+)\\)",
        Pattern.DOTALL
    );

    /**
     * Regex to extract single require statement: require github.com/pkg/errors v0.9.1.
     * Captures: (1) module path, (2) version.
     * Supports pseudo-versions: v0.0.0-20230101120000-abcdef123456
     */
    private static final Pattern SINGLE_REQUIRE_PATTERN = Pattern.compile(
        "^require\\s+([\\w./-]+)\\s+(v[\\w.-]+)",
        Pattern.MULTILINE
    );

    /**
     * Regex to parse individual require lines within a block.
     * Captures: (1) module path, (2) version.
     * Handles optional // indirect comment and pseudo-versions.
     */
    private static final Pattern REQUIRE_LINE_PATTERN = Pattern.compile(
        "([\\w./-]+)\\s+(v[\\w.-]+)"
    );

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
        return Set.of(Technologies.GO, Technologies.GOLANG);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return GO_MOD_PATTERNS;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, GO_MOD_PATTERNS.toArray(new String[0]));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Go module dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();

        Set<Path> goModFiles = new LinkedHashSet<>();
        GO_MOD_PATTERNS.forEach(pattern -> context.findFiles(pattern).forEach(goModFiles::add));

        if (goModFiles.isEmpty()) {
            log.warn("No go.mod files found in project");
            return emptyResult();
        }

        for (Path goModFile : goModFiles) {
            try {
                parseGoMod(goModFile, dependencies, components);
            } catch (Exception e) {
                log.error("Failed to parse go.mod: {}", goModFile, e);
                return failedResult(List.of("Failed to parse go.mod file: " + goModFile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Go module dependencies across {} go.mod files", dependencies.size(), goModFiles.size());

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
     * Parses a single go.mod file and extracts dependencies.
     *
     * @param goModFile path to go.mod
     * @param dependencies list to add discovered dependencies
     * @param components list to add discovered components
     * @throws IOException if file cannot be read
     */
    private void parseGoMod(Path goModFile, List<Dependency> dependencies, List<Component> components) throws IOException {
        String content = readFileContent(goModFile);

        String moduleName = extractModuleName(content);
        if (moduleName == null) {
            log.warn("go.mod missing module declaration: {}", goModFile);
            moduleName = UNKNOWN_MODULE;
        }

        String moduleShortName = extractModuleShortName(moduleName);
        Component component = new Component(
            IdGenerator.generate(GO_LANGUAGE, moduleName),
            moduleShortName,
            ComponentType.SERVICE,
            MODULE_DESCRIPTION_PREFIX + moduleName,
            GO_LANGUAGE,
            goModFile.getParent().toString(),
            Map.of(
                META_MODULE_PATH, moduleName,
                META_PACKAGE_MANAGER, GO_LANGUAGE
            )
        );
        components.add(component);

        // Extract dependencies from require blocks
        extractRequireBlocks(content, moduleName, dependencies);

        // Extract single require statements
        extractSingleRequires(content, moduleName, dependencies);
    }

    /**
     * Extracts the module name from go.mod content.
     *
     * @param content go.mod file content
     * @return module name or null if not found
     */
    private String extractModuleName(String content) {
        Matcher matcher = MODULE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts short name from full module path.
     * Example: github.com/user/my-app -> my-app
     *
     * @param modulePath full module path
     * @return short name
     */
    private String extractModuleShortName(String modulePath) {
        if (modulePath == null) {
            return UNKNOWN_MODULE;
        }
        String[] parts = modulePath.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Extracts dependencies from require blocks.
     *
     * @param content go.mod file content
     * @param sourceModule source module name
     * @param dependencies list to add dependencies
     */
    private void extractRequireBlocks(String content, String sourceModule, List<Dependency> dependencies) {
        Matcher blockMatcher = REQUIRE_BLOCK_PATTERN.matcher(content);

        while (blockMatcher.find()) {
            String blockContent = blockMatcher.group(1);

            // Parse each line in the block
            Matcher lineMatcher = REQUIRE_LINE_PATTERN.matcher(blockContent);
            while (lineMatcher.find()) {
                String modulePath = lineMatcher.group(1);
                String version = lineMatcher.group(2);

                addDependency(sourceModule, modulePath, version, dependencies);
            }
        }
    }

    /**
     * Extracts single require statements (not in blocks).
     *
     * @param content go.mod file content
     * @param sourceModule source module name
     * @param dependencies list to add dependencies
     */
    private void extractSingleRequires(String content, String sourceModule, List<Dependency> dependencies) {
        Matcher matcher = SINGLE_REQUIRE_PATTERN.matcher(content);

        while (matcher.find()) {
            String modulePath = matcher.group(1);
            String version = matcher.group(2);

            addDependency(sourceModule, modulePath, version, dependencies);
        }
    }

    /**
     * Creates and adds a dependency to the list.
     *
     * <p>For Go modules, we extract the domain and path as groupId, and the last segment as artifactId.
     * Example: github.com/gin-gonic/gin -> groupId=github.com/gin-gonic, artifactId=gin
     *
     * @param sourceModule source module name
     * @param modulePath dependency module path
     * @param version dependency version
     * @param dependencies list to add dependency
     */
    private void addDependency(String sourceModule, String modulePath, String version, List<Dependency> dependencies) {
        // Split module path into groupId and artifactId
        String groupId;
        String artifactId;

        int lastSlash = modulePath.lastIndexOf('/');
        if (lastSlash > 0) {
            groupId = modulePath.substring(0, lastSlash);
            artifactId = modulePath.substring(lastSlash + 1);
        } else {
            // If no slash, treat the whole path as artifactId with "go" as groupId
            groupId = GO_LANGUAGE;
            artifactId = modulePath;
        }

        // Remove version suffix from artifactId if present (e.g., /v2, /v3)
        if (artifactId.matches("v\\d+")) {
            // This is a major version path segment, not the actual module name
            // We should keep the full path as groupId in this case
            groupId = modulePath;
            artifactId = extractModuleShortName(modulePath);
        }

        Dependency dependency = new Dependency(
            sourceModule,
            groupId,
            artifactId,
            version,
            SCOPE_COMPILE,
            true
        );
        dependencies.add(dependency);
    }
}
