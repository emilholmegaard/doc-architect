package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

/**
 * Scanner for Python dependency files (requirements.txt, pyproject.toml, setup.py, Pipfile).
 *
 * <p>Since we're running in Java, we parse Python files as TEXT, not with a Python AST parser.
 * This means we use regex patterns for imports and decorators, line-by-line parsing for structured
 * content, and TOML parsers for config files.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>requirements.txt - Line-based with {@code ==}, {@code >=}, {@code ~=} version specs</li>
 *   <li>pyproject.toml - PEP 621 format using TOML parser</li>
 *   <li>setup.py - Regex extraction from install_requires and extras_require</li>
 *   <li>Pipfile - TOML format with [packages] and [dev-packages] sections</li>
 * </ul>
 *
 * <p><b>Regex Patterns</b></p>
 * <ul>
 *   <li>{@code REQUIREMENTS_PATTERN}: {@code ^([a-zA-Z0-9_-]+)\s*([=<>~!]+)\s*(.+)$} - Parses {@code package==1.0.0}</li>
 *   <li>{@code SETUP_INSTALL_REQUIRES}: {@code install_requires\s*=\s*\[(.*?)\]} - Extracts install_requires list</li>
 *   <li>{@code SETUP_DEP_PATTERN}: {@code ['"]([a-zA-Z0-9_-]+)\s*([=<>~!]+)\s*(.+?)['"]} - Parses setup.py dependencies</li>
 * </ul>
 *
 * @see com.docarchitect.core.scanner.Scanner
 * @see Dependency
 * @since 1.0.0
 */
public class PipPoetryDependencyScanner extends AbstractJacksonScanner {

    /**
     * Regex pattern for requirements.txt lines: package==1.0.0, package[extra]>=2.0.0, etc.
     * Captures: (1) package name (with optional extras in brackets), (2) version operator, (3) version spec.
     * Supports:
     * - Simple packages: django==4.2.0
     * - Packages with extras: django[bcrypt]~=5.2.8, psycopg[binary]>=3.2.9
     * - Packages with multiple extras: celery[redis, sqs]>=4.4.5 (allows spaces in extras)
     * - Complex version specs: Adyen>=4.0.0,<5 (captures full version part)
     */
    private static final Pattern REQUIREMENTS_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9_-]+(?:\\[[a-zA-Z0-9_,\\s-]+\\])?)\\s*([=<>~!]+)\\s*(.+)$"
    );

    /**
     * Regex pattern to find install_requires in setup.py.
     * Captures the full list content between square brackets.
     */
    private static final Pattern SETUP_INSTALL_REQUIRES = Pattern.compile(
        "install_requires\\s*=\\s*\\[(.*?)\\]",
        Pattern.DOTALL
    );

    /**
     * Regex pattern to extract individual dependencies from setup.py strings.
     * Captures: (1) package name, (2) version operator, (3) version spec.
     */
    private static final Pattern SETUP_DEP_PATTERN = Pattern.compile(
        "['\"]([a-zA-Z0-9_-]+)\\s*([=<>~!]+)\\s*(.+?)['\"]"
    );

    // GroupId for Python packages
    private static final String PYPI_GROUP_ID = "pypi";
    
    // Dependency scopes
    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_TEST = "test";
    private static final String SCOPE_OPTIONAL = "optional";
    
    // Component and project identifiers
    private static final String PYTHON_PROJECT_DESCRIPTION = "Python Project";
    private static final String PYTHON_LANGUAGE = "Python";
    
    // Poetry and Pipfile configuration keys
    private static final String POETRY_PYTHON_KEY = "python";
    private static final String POETRY_VERSION_WILDCARD = "*";
    
    // TOML/JSON configuration keys for pyproject.toml
    private static final String CONFIG_KEY_PROJECT = "project";
    private static final String CONFIG_KEY_DEPENDENCIES = "dependencies";
    private static final String CONFIG_KEY_OPTIONAL_DEPENDENCIES = "optional-dependencies";
    private static final String CONFIG_KEY_DEPENDENCY_GROUPS = "dependency-groups";
    private static final String CONFIG_KEY_TOOL = "tool";
    private static final String CONFIG_KEY_POETRY = "poetry";
    private static final String CONFIG_KEY_DEV_DEPENDENCIES = "dev-dependencies";
    private static final String CONFIG_KEY_VERSION = "version";
    
    // TOML/JSON configuration keys for Pipfile
    private static final String CONFIG_KEY_PACKAGES = "packages";
    private static final String CONFIG_KEY_DEV_PACKAGES = "dev-packages";

    // File pattern prefixes
    private static final String PREFIX_EDITABLE = "-e ";
    private static final String PREFIX_EDITABLE_LONG = "--editable";
    private static final String PREFIX_REQUIREMENT = "-r ";
    private static final String PREFIX_REQUIREMENT_LONG = "--requirement";
    private static final String COMMENT_PREFIX = "#";

    // File patterns
    // Note: Using {**/,} prefix to match both root-level and nested files
    // because Java's PathMatcher ** doesn't match zero directories
    private static final String PATTERN_REQUIREMENTS = "{**/,}requirements*.txt";
    private static final String PATTERN_PYPROJECT = "{**/,}pyproject.toml";
    private static final String PATTERN_SETUP_PY = "{**/,}setup.py";
    private static final String PATTERN_PIPFILE = "{**/,}Pipfile";
    
    // Scanner priority
    private static final int SCANNER_PRIORITY = 10;
    
    // Scanner ID and display name
    private static final String SCANNER_ID = "pip-poetry-dependencies";
    private static final String SCANNER_DISPLAY_NAME = "Pip/Poetry Dependency Scanner";

    private final TomlMapper tomlMapper = new TomlMapper();

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
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_REQUIREMENTS, PATTERN_PYPROJECT, PATTERN_SETUP_PY, PATTERN_PIPFILE);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        boolean applies = hasAnyFiles(context,
            PATTERN_REQUIREMENTS,
            PATTERN_PYPROJECT,
            PATTERN_SETUP_PY,
            PATTERN_PIPFILE
        );
        log.debug("PipPoetryDependencyScanner appliesTo: {} (found files: requirements={}, pyproject={}, setup={}, pipfile={})",
            applies,
            context.findFiles(PATTERN_REQUIREMENTS).findAny().isPresent(),
            context.findFiles(PATTERN_PYPROJECT).findAny().isPresent(),
            context.findFiles(PATTERN_SETUP_PY).findAny().isPresent(),
            context.findFiles(PATTERN_PIPFILE).findAny().isPresent()
        );
        return applies;
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Python dependencies in: {}", context.rootPath());

        List<Dependency> dependencies = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        String sourceComponentId = IdGenerator.generate(context.rootPath().toString());

        // Create component for the Python project
        Component pythonProject = new Component(
            sourceComponentId,
            context.rootPath().getFileName().toString(),
            ComponentType.LIBRARY,
            PYTHON_PROJECT_DESCRIPTION,
            PYTHON_LANGUAGE,
            null,
            Map.of()
        );
        components.add(pythonProject);

        // Parse requirements.txt files
        context.findFiles(PATTERN_REQUIREMENTS).forEach(file -> {
            try {
                parseRequirementsTxt(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse requirements file: {} - {}", file, e.getMessage());
            }
        });

        // Parse pyproject.toml files
        context.findFiles(PATTERN_PYPROJECT).forEach(file -> {
            try {
                parsePyprojectToml(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse pyproject.toml: {} - {}", file, e.getMessage());
            }
        });

        // Parse setup.py files
        context.findFiles(PATTERN_SETUP_PY).forEach(file -> {
            try {
                parseSetupPy(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse setup.py: {} - {}", file, e.getMessage());
            }
        });

        // Parse Pipfile
        context.findFiles(PATTERN_PIPFILE).forEach(file -> {
            try {
                parsePipfile(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse Pipfile: {} - {}", file, e.getMessage());
            }
        });

        log.info("Found {} Python dependencies", dependencies.size());

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
     * Parses requirements.txt format.
     * Format: package==1.0.0, package>=2.0.0, etc.
     */
    private void parseRequirementsTxt(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        List<String> lines = readFileLines(file);

        for (String line : lines) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith(COMMENT_PREFIX)) {
                continue;
            }

            // Handle -e git+https://... editable installs
            if (line.startsWith(PREFIX_EDITABLE) || line.startsWith(PREFIX_EDITABLE_LONG)) {
                continue;
            }

            // Handle -r requirements-dev.txt includes
            if (line.startsWith(PREFIX_REQUIREMENT) || line.startsWith(PREFIX_REQUIREMENT_LONG)) {
                continue;
            }

            Matcher matcher = REQUIREMENTS_PATTERN.matcher(line);
            if (matcher.matches()) {
                String packageName = matcher.group(1);
                String versionSpec = matcher.group(3);

                if (packageName != null && !packageName.isEmpty()) {
                    Dependency dep = new Dependency(
                        sourceComponentId,
                        PYPI_GROUP_ID,
                        packageName,
                        versionSpec,
                        SCOPE_COMPILE,
                        true
                    );

                    dependencies.add(dep);
                    log.debug("Found dependency from {}: {} {}", file.getFileName(), packageName, versionSpec);
                } else {
                    log.warn("Matched requirements pattern but packageName is null/empty for line: {}", line);
                }
            }
        }
    }

    /**
     * Parses pyproject.toml (PEP 621 and PEP 735 formats).
     * Looks for:
     * - [project.dependencies] (PEP 621)
     * - [project.optional-dependencies] (PEP 621)
     * - [dependency-groups] (PEP 735)
     * - [tool.poetry.dependencies] (Poetry)
     * - [tool.poetry.dev-dependencies] (Poetry)
     */
    private void parsePyprojectToml(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = readFileContent(file);
        JsonNode root = tomlMapper.readTree(content);

        // Parse [project.dependencies] (PEP 621)
        JsonNode project = root.get(CONFIG_KEY_PROJECT);
        if (project != null) {
            JsonNode deps = project.get(CONFIG_KEY_DEPENDENCIES);
            if (deps != null && deps.isArray()) {
                for (JsonNode dep : deps) {
                    parseDependencySpec(dep.asText(), sourceComponentId, SCOPE_COMPILE, dependencies);
                }
            }

            // Parse [project.optional-dependencies] (PEP 621)
            JsonNode optionalDeps = project.get(CONFIG_KEY_OPTIONAL_DEPENDENCIES);
            if (optionalDeps != null && optionalDeps.isObject()) {
                optionalDeps.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isArray()) {
                        for (JsonNode dep : entry.getValue()) {
                            parseDependencySpec(dep.asText(), sourceComponentId, SCOPE_OPTIONAL, dependencies);
                        }
                    }
                });
            }
        }

        // Parse [dependency-groups] (PEP 735) - used by uv, Hatch, and modern Python tools
        JsonNode dependencyGroups = root.get(CONFIG_KEY_DEPENDENCY_GROUPS);
        if (dependencyGroups != null && dependencyGroups.isObject()) {
            dependencyGroups.fields().forEachRemaining(entry -> {
                String groupName = entry.getKey();
                String scope = groupName.equals("dev") || groupName.equals("test") ? SCOPE_TEST : SCOPE_OPTIONAL;

                if (entry.getValue().isArray()) {
                    for (JsonNode dep : entry.getValue()) {
                        parseDependencySpec(dep.asText(), sourceComponentId, scope, dependencies);
                    }
                }
            });
        }

        // Parse [tool.poetry.dependencies] (Poetry format)
        JsonNode tool = root.get(CONFIG_KEY_TOOL);
        if (tool != null) {
            JsonNode poetry = tool.get(CONFIG_KEY_POETRY);
            if (poetry != null) {
                JsonNode poetryDeps = poetry.get(CONFIG_KEY_DEPENDENCIES);
                if (poetryDeps != null && poetryDeps.isObject()) {
                    poetryDeps.fields().forEachRemaining(entry -> {
                        String packageName = entry.getKey();
                        if (!POETRY_PYTHON_KEY.equals(packageName)) {
                            String version = extractVersionFromPoetryDep(entry.getValue());
                            Dependency dep = new Dependency(
                                sourceComponentId,
                                PYPI_GROUP_ID,
                                packageName,
                                version,
                                SCOPE_COMPILE,
                                true
                            );
                            dependencies.add(dep);
                        }
                    });
                }

                // Parse [tool.poetry.dev-dependencies]
                JsonNode devDeps = poetry.get(CONFIG_KEY_DEV_DEPENDENCIES);
                if (devDeps != null && devDeps.isObject()) {
                    devDeps.fields().forEachRemaining(entry -> {
                        String packageName = entry.getKey();
                        String version = extractVersionFromPoetryDep(entry.getValue());
                        Dependency dep = new Dependency(
                            sourceComponentId,
                            PYPI_GROUP_ID,
                            packageName,
                            version,
                            SCOPE_TEST,
                            true
                        );
                        dependencies.add(dep);
                    });
                }
            }
        }

        log.debug("Parsed pyproject.toml: {}", file);
    }

    /**
     * Extracts version from Poetry dependency which can be a string or object.
     * Examples: "^1.0.0", {"version": "^1.0.0", "optional": true}
     */
    private String extractVersionFromPoetryDep(JsonNode depNode) {
        if (depNode.isTextual()) {
            return depNode.asText();
        } else if (depNode.isObject()) {
            JsonNode version = depNode.get(CONFIG_KEY_VERSION);
            return version != null ? version.asText() : POETRY_VERSION_WILDCARD;
        }
        return POETRY_VERSION_WILDCARD;
    }

    /**
     * Parses setup.py by extracting install_requires with regex.
     * Format: install_requires=['package==1.0.0', 'other>=2.0.0']
     */
    private void parseSetupPy(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = readFileContent(file);

        Matcher installRequiresMatcher = SETUP_INSTALL_REQUIRES.matcher(content);
        if (installRequiresMatcher.find()) {
            String installRequiresContent = installRequiresMatcher.group(1);

            Matcher depMatcher = SETUP_DEP_PATTERN.matcher(installRequiresContent);
            while (depMatcher.find()) {
                String packageName = depMatcher.group(1);
                String versionSpec = depMatcher.group(3);

                Dependency dep = new Dependency(
                    sourceComponentId,
                    PYPI_GROUP_ID,
                    packageName,
                    versionSpec,
                    SCOPE_COMPILE,
                    true
                );

                dependencies.add(dep);
                log.debug("Found dependency from setup.py: {} {}", packageName, versionSpec);
            }
        }
    }

    /**
     * Parses Pipfile (TOML format).
     * Looks for [packages] and [dev-packages] sections.
     */
    private void parsePipfile(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = readFileContent(file);
        JsonNode root = tomlMapper.readTree(content);

        // Parse [packages]
        JsonNode packages = root.get(CONFIG_KEY_PACKAGES);
        if (packages != null && packages.isObject()) {
            packages.fields().forEachRemaining(entry -> {
                String packageName = entry.getKey();
                String version = extractVersionFromPipfileDep(entry.getValue());

                Dependency dep = new Dependency(
                    sourceComponentId,
                    PYPI_GROUP_ID,
                    packageName,
                    version,
                    SCOPE_COMPILE,
                    true
                );

                dependencies.add(dep);
                log.debug("Found Pipfile dependency: {} {}", packageName, version);
            });
        }

        // Parse [dev-packages]
        JsonNode devPackages = root.get(CONFIG_KEY_DEV_PACKAGES);
        if (devPackages != null && devPackages.isObject()) {
            devPackages.fields().forEachRemaining(entry -> {
                String packageName = entry.getKey();
                String version = extractVersionFromPipfileDep(entry.getValue());

                Dependency dep = new Dependency(
                    sourceComponentId,
                    PYPI_GROUP_ID,
                    packageName,
                    version,
                    SCOPE_TEST,
                    true
                );

                dependencies.add(dep);
            });
        }

        log.debug("Parsed Pipfile: {}", file);
    }

    /**
     * Extracts version from Pipfile dependency which can be a string or object.
     * Examples: "*", "==1.0.0", {"version": "==1.0.0", "markers": "..."}
     */
    private String extractVersionFromPipfileDep(JsonNode depNode) {
        if (depNode.isTextual()) {
            return depNode.asText();
        } else if (depNode.isObject()) {
            JsonNode version = depNode.get(CONFIG_KEY_VERSION);
            return version != null ? version.asText() : POETRY_VERSION_WILDCARD;
        }
        return POETRY_VERSION_WILDCARD;
    }

    /**
     * Parses a dependency specification string like "package==1.0.0" or "package>=2.0.0".
     */
    private void parseDependencySpec(String spec, String sourceComponentId, String scope, List<Dependency> dependencies) {
        Matcher matcher = REQUIREMENTS_PATTERN.matcher(spec);
        if (matcher.matches()) {
            String packageName = matcher.group(1);
            String versionSpec = matcher.group(3);

            Dependency dep = new Dependency(
                sourceComponentId,
                PYPI_GROUP_ID,
                packageName,
                versionSpec,
                scope,
                true
            );

            dependencies.add(dep);
        }
    }
}
