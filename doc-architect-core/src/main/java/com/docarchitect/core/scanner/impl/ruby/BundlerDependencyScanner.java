package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
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

/**
 * Scanner for Ruby Bundler dependency files (Gemfile and Gemfile.lock).
 *
 * <p>This scanner parses Ruby dependency files to extract gem dependencies with their
 * version constraints. Bundler is the standard dependency management tool for Ruby and
 * Ruby on Rails applications.
 *
 * <p><b>Parsing Strategy:</b>
 * <ol>
 *   <li>Locate Gemfile and Gemfile.lock files</li>
 *   <li>Parse Gemfile using regex to extract gem declarations with version constraints</li>
 *   <li>Parse Gemfile.lock to extract exact locked versions</li>
 *   <li>Identify development vs production dependencies based on groups</li>
 *   <li>Create Component record for the Ruby project</li>
 *   <li>Create Dependency records for each gem with version information</li>
 * </ol>
 *
 * <p><b>Supported Gemfile Constructs:</b>
 * <ul>
 *   <li>Simple gem declarations: {@code gem 'rails', '~> 7.0'}</li>
 *   <li>Multiple version constraints: {@code gem 'pg', '~> 1.4', '>= 1.4.5'}</li>
 *   <li>Group declarations: {@code group :development, :test do ... end}</li>
 *   <li>Path-based gems: {@code gem 'my_gem', path: 'vendor/gems/my_gem'}</li>
 *   <li>Git-based gems: {@code gem 'my_gem', git: 'https://...'}</li>
 *   <li>Version operators: {@code ~>}, {@code >=}, {@code <=}, {@code =}, {@code !=}</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * Scanner scanner = new BundlerDependencyScanner();
 * ScanContext context = new ScanContext(
 *     projectRoot,
 *     List.of(projectRoot),
 *     Map.of(),
 *     Map.of(),
 *     Map.of()
 * );
 * ScanResult result = scanner.scan(context);
 * List<Dependency> gems = result.dependencies();
 * }</pre>
 *
 * <p><b>Example Gemfile:</b>
 * <pre>{@code
 * source 'https://rubygems.org'
 *
 * gem 'rails', '~> 7.0.0'
 * gem 'pg', '~> 1.4'
 * gem 'redis', '~> 4.0'
 *
 * group :development, :test do
 *   gem 'rspec-rails', '~> 6.0'
 *   gem 'factory_bot_rails', '~> 6.2'
 * end
 * }</pre>
 *
 * @see AbstractRegexScanner
 * @see Dependency
 * @since 1.0.0
 */
public class BundlerDependencyScanner extends AbstractRegexScanner {

    // Scanner Metadata
    private static final String SCANNER_ID = "bundler-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "Bundler Dependency Scanner";
    private static final int PRIORITY = 80;

    // File Patterns
    private static final String GEMFILE_NAME = "Gemfile";
    private static final String GEMFILE_PATTERN = "**/Gemfile";
    private static final String GEMFILE_LOCK_NAME = "Gemfile.lock";
    private static final String GEMFILE_LOCK_PATTERN = "**/Gemfile.lock";
    private static final Set<String> GEMFILE_PATTERNS = Set.of(GEMFILE_NAME, GEMFILE_PATTERN);

    // Regex Patterns for Gemfile parsing
    private static final Pattern GEM_PATTERN = Pattern.compile(
        "gem\\s+['\"]([a-zA-Z0-9_-]+)['\"](?:,\\s*['\"]([^'\"]+)['\"])?",
        Pattern.MULTILINE
    );

    private static final Pattern GROUP_START_PATTERN = Pattern.compile(
        "group\\s+([:\\w,\\s]+)\\s+do",
        Pattern.MULTILINE
    );

    private static final Pattern GROUP_END_PATTERN = Pattern.compile(
        "^\\s*end\\s*$",
        Pattern.MULTILINE
    );

    // Regex Patterns for Gemfile.lock parsing
    private static final Pattern LOCK_SPECS_SECTION = Pattern.compile(
        "^\\s*specs:$",
        Pattern.MULTILINE
    );

    private static final Pattern LOCK_GEM_PATTERN = Pattern.compile(
        "^\\s{4}([a-zA-Z0-9_-]+)\\s+\\(([^)]+)\\)$",
        Pattern.MULTILINE
    );

    // Constants
    private static final String RUBYGEMS_GROUP_ID = "rubygems";
    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_DEVELOPMENT = "development";
    private static final String GROUP_TEST = "test";
    private static final String GROUP_DEVELOPMENT = "development";

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
        return Set.of(Technologies.RUBY);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(GEMFILE_NAME, GEMFILE_PATTERN, GEMFILE_LOCK_NAME, GEMFILE_LOCK_PATTERN);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, GEMFILE_PATTERNS.toArray(new String[0]));
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Ruby Bundler dependencies in: {}", context.rootPath());

        List<Component> components = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();

        // Find Gemfile
        List<Path> gemfiles = new ArrayList<>();
        context.findFiles(GEMFILE_NAME).forEach(gemfiles::add);
        context.findFiles(GEMFILE_PATTERN).forEach(gemfiles::add);

        if (gemfiles.isEmpty()) {
            log.warn("No Gemfile found in project");
            return emptyResult();
        }

        // Process each Gemfile (supports monorepos with multiple Ruby projects)
        for (Path gemfile : gemfiles) {
            try {
                processGemfile(gemfile, context, components, dependencies);
            } catch (Exception e) {
                log.error("Failed to parse Gemfile: {}", gemfile, e);
                return failedResult(List.of("Failed to parse Gemfile: " + gemfile + " - " + e.getMessage()));
            }
        }

        log.info("Found {} Ruby gem dependencies across {} Gemfile(s)",
            dependencies.size(), gemfiles.size());

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
     * Processes a single Gemfile and its associated Gemfile.lock.
     *
     * @param gemfile path to Gemfile
     * @param context scan context
     * @param components list to add discovered components
     * @param dependencies list to add discovered dependencies
     * @throws IOException if file cannot be read
     */
    private void processGemfile(Path gemfile, ScanContext context, List<Component> components,
                                List<Dependency> dependencies) throws IOException {
        String gemfileContent = readFileContent(gemfile);

        // Create component for this Ruby project
        String projectName = deriveProjectName(gemfile);
        String componentId = IdGenerator.generate(projectName);

        Component component = new Component(
            componentId,
            projectName,
            ComponentType.SERVICE,
            "Ruby project using Bundler for dependency management",
            Technologies.RUBY,
            null, // repository
            Map.of() // metadata
        );
        components.add(component);

        // Parse Gemfile for dependencies with version constraints
        Map<String, GemInfo> gemfileGems = parseGemfile(gemfileContent);

        // Try to find and parse Gemfile.lock for exact versions
        Path gemfileLock = gemfile.getParent().resolve("Gemfile.lock");
        Map<String, String> lockedVersions = new HashMap<>();

        try {
            boolean lockExists = context.findFiles(GEMFILE_LOCK_NAME).anyMatch(p -> p.equals(gemfileLock)) ||
                               context.findFiles(GEMFILE_LOCK_PATTERN).anyMatch(p -> p.equals(gemfileLock));
            if (lockExists) {
                try {
                    String lockContent = readFileContent(gemfileLock);
                    lockedVersions = parseGemfileLock(lockContent);
                    log.debug("Found Gemfile.lock with {} locked gem versions", lockedVersions.size());
                } catch (IOException e) {
                    log.warn("Failed to parse Gemfile.lock for {}: {}", gemfile, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("Gemfile.lock check failed for {}: {}", gemfile, e.getMessage());
        }

        // Create dependency records
        for (Map.Entry<String, GemInfo> entry : gemfileGems.entrySet()) {
            String gemName = entry.getKey();
            GemInfo gemInfo = entry.getValue();

            // Use locked version if available, otherwise use constraint from Gemfile
            String version = lockedVersions.getOrDefault(gemName, gemInfo.version);

            Dependency dependency = new Dependency(
                componentId,
                RUBYGEMS_GROUP_ID,
                gemName,
                version,
                gemInfo.scope,
                true // direct dependency
            );

            dependencies.add(dependency);
            log.debug("Found gem dependency: {} {} (scope: {})", gemName, version, gemInfo.scope);
        }
    }

    /**
     * Parses a Gemfile to extract gem declarations with version constraints and groups.
     *
     * @param content Gemfile content
     * @return map of gem name to gem info (version constraint and scope)
     */
    private Map<String, GemInfo> parseGemfile(String content) {
        Map<String, GemInfo> gems = new HashMap<>();
        String[] lines = content.split("\n");

        String currentScope = SCOPE_COMPILE;
        int groupDepth = 0;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // Skip comments and empty lines
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            // Check for group start
            Matcher groupStartMatcher = GROUP_START_PATTERN.matcher(line);
            if (groupStartMatcher.find()) {
                String groupDef = groupStartMatcher.group(1);
                currentScope = determineScope(groupDef);
                groupDepth++;
                continue;
            }

            // Check for group end
            if (GROUP_END_PATTERN.matcher(trimmedLine).matches() && groupDepth > 0) {
                groupDepth--;
                if (groupDepth == 0) {
                    currentScope = SCOPE_COMPILE;
                }
                continue;
            }

            // Parse gem declaration
            Matcher gemMatcher = GEM_PATTERN.matcher(line);
            if (gemMatcher.find()) {
                String gemName = gemMatcher.group(1);
                String versionConstraint = gemMatcher.group(2);

                // Default to any version if not specified
                if (versionConstraint == null || versionConstraint.trim().isEmpty()) {
                    versionConstraint = "*";
                }

                gems.put(gemName, new GemInfo(versionConstraint, currentScope));
            }
        }

        return gems;
    }

    /**
     * Parses a Gemfile.lock to extract exact locked versions.
     *
     * @param content Gemfile.lock content
     * @return map of gem name to locked version
     */
    private Map<String, String> parseGemfileLock(String content) {
        Map<String, String> lockedVersions = new HashMap<>();
        String[] lines = content.split("\n");

        boolean inSpecsSection = false;

        for (String line : lines) {
            // Check if we're entering the specs section
            if (LOCK_SPECS_SECTION.matcher(line).matches()) {
                inSpecsSection = true;
                continue;
            }

            // Exit specs section when we hit a new top-level section
            if (inSpecsSection && !line.isEmpty() && !line.startsWith(" ")) {
                inSpecsSection = false;
            }

            // Parse gem version in specs section
            if (inSpecsSection) {
                Matcher gemMatcher = LOCK_GEM_PATTERN.matcher(line);
                if (gemMatcher.find()) {
                    String gemName = gemMatcher.group(1);
                    String version = gemMatcher.group(2);
                    lockedVersions.put(gemName, version);
                }
            }
        }

        return lockedVersions;
    }

    /**
     * Determines the dependency scope based on Bundler group definition.
     *
     * @param groupDef group definition (e.g., ":development, :test")
     * @return dependency scope
     */
    private String determineScope(String groupDef) {
        String lower = groupDef.toLowerCase();

        if (lower.contains(GROUP_TEST)) {
            return SCOPE_TEST;
        } else if (lower.contains(GROUP_DEVELOPMENT)) {
            return SCOPE_DEVELOPMENT;
        }

        return SCOPE_COMPILE;
    }

    /**
     * Derives a project name from the Gemfile path.
     *
     * @param gemfile path to Gemfile
     * @return project name
     */
    private String deriveProjectName(Path gemfile) {
        Path parent = gemfile.getParent();
        if (parent != null && parent.getFileName() != null) {
            return parent.getFileName().toString();
        }
        return "ruby-project";
    }

    /**
     * Internal class to hold gem information (version constraint and scope).
     */
    private static class GemInfo {
        final String version;
        final String scope;

        GemInfo(String version, String scope) {
            this.version = version;
            this.scope = scope;
        }
    }
}
