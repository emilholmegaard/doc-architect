package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJacksonScanner;
import com.docarchitect.core.util.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Regex pattern for requirements.txt lines: package==1.0.0, package>=2.0.0, etc.
     * Captures: (1) package name, (2) version operator, (3) version spec.
     */
    private static final Pattern REQUIREMENTS_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9_-]+)\\s*([=<>~!]+)\\s*(.+)$"
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

    private final TomlMapper tomlMapper = new TomlMapper();

    @Override
    public String getId() {
        return "pip-poetry-dependencies";
    }

    @Override
    public String getDisplayName() {
        return "Pip/Poetry Dependency Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("python");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/requirements*.txt", "**/pyproject.toml", "**/setup.py", "**/Pipfile");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context,
            "**/requirements*.txt",
            "**/pyproject.toml",
            "**/setup.py",
            "**/Pipfile"
        );
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
            "Python Project",
            "Python",
            null,
            Map.of()
        );
        components.add(pythonProject);

        // Parse requirements.txt files
        context.findFiles("**/requirements*.txt").forEach(file -> {
            try {
                parseRequirementsTxt(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse requirements file: {} - {}", file, e.getMessage());
            }
        });

        // Parse pyproject.toml files
        context.findFiles("**/pyproject.toml").forEach(file -> {
            try {
                parsePyprojectToml(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse pyproject.toml: {} - {}", file, e.getMessage());
            }
        });

        // Parse setup.py files
        context.findFiles("**/setup.py").forEach(file -> {
            try {
                parseSetupPy(file, sourceComponentId, dependencies);
            } catch (IOException e) {
                log.warn("Failed to parse setup.py: {} - {}", file, e.getMessage());
            }
        });

        // Parse Pipfile
        context.findFiles("**/Pipfile").forEach(file -> {
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
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Handle -e git+https://... editable installs
            if (line.startsWith("-e ") || line.startsWith("--editable")) {
                continue;
            }

            // Handle -r requirements-dev.txt includes
            if (line.startsWith("-r ") || line.startsWith("--requirement")) {
                continue;
            }

            Matcher matcher = REQUIREMENTS_PATTERN.matcher(line);
            if (matcher.matches()) {
                String packageName = matcher.group(1);
                String versionSpec = matcher.group(3);

                Dependency dep = new Dependency(
                    sourceComponentId,
                    packageName,
                    null, // Python packages don't have groupId
                    versionSpec,
                    "compile",
                    true
                );

                dependencies.add(dep);
                log.debug("Found dependency from {}: {} {}", file.getFileName(), packageName, versionSpec);
            }
        }
    }

    /**
     * Parses pyproject.toml (PEP 621 format).
     * Looks for [project.dependencies] and [project.optional-dependencies].
     */
    private void parsePyprojectToml(Path file, String sourceComponentId, List<Dependency> dependencies) throws IOException {
        String content = readFileContent(file);
        JsonNode root = tomlMapper.readTree(content);

        // Parse [project.dependencies]
        JsonNode project = root.get("project");
        if (project != null) {
            JsonNode deps = project.get("dependencies");
            if (deps != null && deps.isArray()) {
                for (JsonNode dep : deps) {
                    parseDependencySpec(dep.asText(), sourceComponentId, "compile", dependencies);
                }
            }

            // Parse [project.optional-dependencies]
            JsonNode optionalDeps = project.get("optional-dependencies");
            if (optionalDeps != null && optionalDeps.isObject()) {
                optionalDeps.fields().forEachRemaining(entry -> {
                    if (entry.getValue().isArray()) {
                        for (JsonNode dep : entry.getValue()) {
                            parseDependencySpec(dep.asText(), sourceComponentId, "optional", dependencies);
                        }
                    }
                });
            }
        }

        // Parse [tool.poetry.dependencies] (Poetry format)
        JsonNode tool = root.get("tool");
        if (tool != null) {
            JsonNode poetry = tool.get("poetry");
            if (poetry != null) {
                JsonNode poetryDeps = poetry.get("dependencies");
                if (poetryDeps != null && poetryDeps.isObject()) {
                    poetryDeps.fields().forEachRemaining(entry -> {
                        String packageName = entry.getKey();
                        if (!"python".equals(packageName)) { // Skip python version requirement
                            String version = extractVersionFromPoetryDep(entry.getValue());
                            Dependency dep = new Dependency(
                                sourceComponentId,
                                packageName,
                                null,
                                version,
                                "compile",
                                true
                            );
                            dependencies.add(dep);
                        }
                    });
                }

                // Parse [tool.poetry.dev-dependencies]
                JsonNode devDeps = poetry.get("dev-dependencies");
                if (devDeps != null && devDeps.isObject()) {
                    devDeps.fields().forEachRemaining(entry -> {
                        String packageName = entry.getKey();
                        String version = extractVersionFromPoetryDep(entry.getValue());
                        Dependency dep = new Dependency(
                            sourceComponentId,
                            packageName,
                            null,
                            version,
                            "test",
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
            JsonNode version = depNode.get("version");
            return version != null ? version.asText() : "*";
        }
        return "*";
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
                    packageName,
                    null,
                    versionSpec,
                    "compile",
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
        JsonNode packages = root.get("packages");
        if (packages != null && packages.isObject()) {
            packages.fields().forEachRemaining(entry -> {
                String packageName = entry.getKey();
                String version = extractVersionFromPipfileDep(entry.getValue());

                Dependency dep = new Dependency(
                    sourceComponentId,
                    packageName,
                    null,
                    version,
                    "compile",
                    true
                );

                dependencies.add(dep);
                log.debug("Found Pipfile dependency: {} {}", packageName, version);
            });
        }

        // Parse [dev-packages]
        JsonNode devPackages = root.get("dev-packages");
        if (devPackages != null && devPackages.isObject()) {
            devPackages.fields().forEachRemaining(entry -> {
                String packageName = entry.getKey();
                String version = extractVersionFromPipfileDep(entry.getValue());

                Dependency dep = new Dependency(
                    sourceComponentId,
                    packageName,
                    null,
                    version,
                    "test",
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
            JsonNode version = depNode.get("version");
            return version != null ? version.asText() : "*";
        }
        return "*";
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
                packageName,
                null,
                versionSpec,
                scope,
                true
            );

            dependencies.add(dep);
        }
    }
}
