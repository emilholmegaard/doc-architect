package com.docarchitect.core.generator.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docarchitect.core.generator.DiagramGenerator;
import com.docarchitect.core.generator.DiagramType;
import com.docarchitect.core.generator.GeneratedDiagram;
import com.docarchitect.core.generator.GeneratorConfig;
import com.docarchitect.core.model.ArchitectureModel;
import com.docarchitect.core.model.Component;

/**
 * Generates Sokrates configuration files for code analysis integration.
 *
 * <p>Produces {@code sokrates.json} configuration files that enable integration
 * with the Sokrates code analysis platform. The configuration organizes components
 * with source file patterns for automated code quality analysis.
 *
 * <p>Sokrates configuration includes:
 * <ul>
 *   <li>Logical components with source file patterns</li>
 *   <li>Technology stack definitions</li>
 *   <li>File extensions and scoping rules</li>
 *   <li>Component organization and grouping</li>
 * </ul>
 *
 * @see <a href="https://d3axxy9bcycpv7.cloudfront.net/java/html/index.html">Sokrates Documentation</a>
 */
public class SokratesGenerator implements DiagramGenerator {

    private static final Logger log = LoggerFactory.getLogger(SokratesGenerator.class);

    // JSON Keys
    private static final String KEY_NAME = "name";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXTENSIONS = "extensions";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_SRC_ROOT = "srcRoot";
    private static final String KEY_IGNORE = "ignore";
    private static final String KEY_LOGICAL_COMPONENTS = "logicalComponents";
    private static final String KEY_PATH_PATTERNS = "pathPatterns";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_TECHNOLOGY = "technology";
    private static final String KEY_REPOSITORY = "repository";
    private static final String KEY_CROSS_CUTTING_CONCERNS = "crossCuttingConcerns";
    private static final String KEY_ANALYSIS = "analysis";
    private static final String KEY_SKIP_DUPLICATION = "skipDuplication";
    private static final String KEY_SKIP_DEPENDENCIES = "skipDependencies";
    private static final String KEY_CACHE_SOURCE_FILES = "cacheSourceFiles";
    private static final String KEY_GOALS = "goals";
    private static final String KEY_MAIN = "main";
    private static final String KEY_TYPE = "type";
    private static final String KEY_TARGET = "target";

    // Configuration Values
    private static final String VALUE_DEFAULT = "Default";
    private static final String VALUE_TEST_CODE = "Test Code";
    private static final String VALUE_GENERATED_CODE = "Generated Code";
    private static final String VALUE_MAINTAIN_COMPONENT = "Maintain component separation";
    private static final String VALUE_METRIC = "METRIC";
    private static final String VALUE_COMPONENT_DEPENDENCIES = "COMPONENT_DEPENDENCIES";

    // Path Patterns
    private static final String PATTERN_ALL = "**/*";
    private static final String PATTERN_TARGET = "target/**";
    private static final String PATTERN_BUILD = "build/**";
    private static final String PATTERN_NODE_MODULES = "node_modules/**";
    private static final String PATTERN_DIST = "dist/**";
    private static final String PATTERN_MIN_JS = "*.min.js";
    private static final String PATTERN_TEST = "*.test.*";
    private static final String PATTERN_SPEC = "*.spec.*";
    private static final String PATTERN_TEST_FILES = "**/*Test.*";
    private static final String PATTERN_SPEC_FILES = "**/*Spec.*";
    private static final String PATTERN_TEST_DIR = "**/test/**";
    private static final String PATTERN_GENERATED = "**/generated/**";

    // Component Type Patterns
    private static final String PATTERN_SRC_MAIN_JAVA = "**/src/main/java/**";
    private static final String PATTERN_SRC = "**/src/**";
    private static final String PATTERN_LIB = "**/lib";
    private static final String PATTERN_MIGRATIONS = "**/migrations/**";
    private static final String PATTERN_SCHEMA = "**/schema/**";
    private static final String PATTERN_DB = "**/db/**";
    private static final String PATTERN_GATEWAY = "**/gateway/**";
    private static final String PATTERN_API = "**/api/**";
    private static final String PATTERN_MESSAGING = "**/messaging/**";
    private static final String PATTERN_EVENTS = "**/events/**";

    // Suffixes to Remove
    private static final String SUFFIX_SERVICE = "service";
    private static final String SUFFIX_APPLICATION = "application";
    private static final String SUFFIX_API = "api";

    // File Extensions
    private static final String[] DEFAULT_EXTENSIONS = {"java", "kt", "py", "js", "ts", "cs", "go"};
    private static final String EXT_JAVA = "java";
    private static final String EXT_KOTLIN = "kt";
    private static final String EXT_KT_SCRIPT = "kts";
    private static final String EXT_PYTHON = "py";
    private static final String EXT_JAVASCRIPT = "js";
    private static final String EXT_JSX = "jsx";
    private static final String EXT_TYPESCRIPT = "ts";
    private static final String EXT_TSX = "tsx";
    private static final String EXT_CSHARP = "cs";
    private static final String EXT_GO = "go";
    private static final String EXT_RUBY = "rb";
    private static final String EXT_PHP = "php";
    private static final String EXT_RUST = "rs";
    private static final String EXT_SCALA = "scala";

    // Technology Keywords
    private static final String TECH_SPRING = "spring";
    private static final String TECH_SPRING_BOOT = "spring boot";
    private static final String TECH_KOTLIN = "kotlin";
    private static final String TECH_PYTHON = "python";
    private static final String TECH_DJANGO = "django";
    private static final String TECH_FLASK = "flask";
    private static final String TECH_FASTAPI = "fastapi";
    private static final String TECH_JAVASCRIPT = "javascript";
    private static final String TECH_NODE = "node.js";
    private static final String TECH_EXPRESS = "express";
    private static final String TECH_REACT = "react";
    private static final String TECH_VUE = "vue";
    private static final String TECH_TYPESCRIPT = "typescript";
    private static final String TECH_ANGULAR = "angular";
    private static final String TECH_CSHARP = "c#";
    private static final String TECH_DOTNET = ".net";
    private static final String TECH_ASPNET = "asp.net";
    private static final String TECH_GOLANG = "golang";
    private static final String TECH_RUBY = "ruby";
    private static final String TECH_RAILS = "rails";
    private static final String TECH_PHP = "php";
    private static final String TECH_LARAVEL = "laravel";
    private static final String TECH_RUST = "rust";
    private static final String TECH_SCALA = "scala";

    // Special Characters
    private static final String CHAR_SLASH = "/";
    private static final String CHAR_GIT_SUFFIX = ".git";
    private static final String CHAR_DOT = ".";

    @Override
    public String getId() {
        return "sokrates";
    }

    @Override
    public String getDisplayName() {
        return "Sokrates Configuration Generator";
    }

    @Override
    public String getFileExtension() {
        return "json";
    }

    @Override
    public Set<DiagramType> getSupportedDiagramTypes() {
        // Sokrates generates component-based analysis configuration
        return Set.of(DiagramType.C4_COMPONENT);
    }

    @Override
    public GeneratedDiagram generate(ArchitectureModel model, DiagramType type, GeneratorConfig config) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(config, "config must not be null");

        if (!getSupportedDiagramTypes().contains(type)) {
            throw new IllegalArgumentException("Unsupported diagram type: " + type);
        }

        log.debug("Generating Sokrates configuration");

        String content = generateSokratesConfig(model, config);

        log.info("Generated Sokrates configuration");

        return new GeneratedDiagram("sokrates", content, getFileExtension());
    }

    /**
     * Generates the complete Sokrates JSON configuration.
     */
    private String generateSokratesConfig(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        sb.append("  \"").append(KEY_NAME).append("\": ").append(jsonString(model.projectName())).append(",\n");
        sb.append("  \"").append(KEY_DESCRIPTION).append("\": \"Architecture analysis for ").append(escape(model.projectName())).append("\",\n");

        if (!model.projectVersion().equals("unknown")) {
            sb.append("  \"").append(KEY_VERSION).append("\": ").append(jsonString(model.projectVersion())).append(",\n");
        }

        // Extensions configuration
        sb.append("  \"").append(KEY_EXTENSIONS).append("\": [\n");
        Set<String> extensions = inferExtensions(model);
        List<String> extList = new ArrayList<>(extensions);
        for (int i = 0; i < extList.size(); i++) {
            sb.append("    ").append(jsonString(extList.get(i)));
            if (i < extList.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Scope configuration
        sb.append("  \"").append(KEY_SCOPE).append("\": {\n");
        sb.append("    \"").append(KEY_SRC_ROOT).append("\": \".\",\n");
        sb.append("    \"").append(KEY_IGNORE).append("\": [\n");
        sb.append("      \"").append(PATTERN_TARGET).append("\",\n");
        sb.append("      \"").append(PATTERN_BUILD).append("\",\n");
        sb.append("      \"").append(PATTERN_NODE_MODULES).append("\",\n");
        sb.append("      \"").append(PATTERN_DIST).append("\",\n");
        sb.append("      \"").append(PATTERN_MIN_JS).append("\",\n");
        sb.append("      \"").append(PATTERN_TEST).append("\",\n");
        sb.append("      \"").append(PATTERN_SPEC).append("\"\n");
        sb.append("    ]\n");
        sb.append("  },\n");

        // Logical components
        sb.append("  \"").append(KEY_LOGICAL_COMPONENTS).append("\": [\n");

        if (model.components().isEmpty()) {
            sb.append("    {\n");
            sb.append("      \"").append(KEY_NAME).append("\": ").append(jsonString(VALUE_DEFAULT)).append(",\n");
            sb.append("      \"").append(KEY_PATH_PATTERNS).append("\": [\"").append(PATTERN_ALL).append("\"]\n");
            sb.append("    }\n");
        } else {
            List<Component> components = model.components();
            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                sb.append("    {\n");
                sb.append("      \"").append(KEY_NAME).append("\": ").append(jsonString(comp.name())).append(",\n");

                if (comp.description() != null) {
                    sb.append("      \"").append(KEY_DESCRIPTION).append("\": ").append(jsonString(comp.description())).append(",\n");
                }

                // Generate path patterns based on component name and type
                List<String> patterns = generatePathPatterns(comp, model);
                sb.append("      \"").append(KEY_PATH_PATTERNS).append("\": [\n");
                for (int j = 0; j < patterns.size(); j++) {
                    sb.append("        ").append(jsonString(patterns.get(j)));
                    if (j < patterns.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append("      ]");

                // Add metadata
                if (comp.technology() != null) {
                    sb.append(",\n");
                    sb.append("      \"").append(KEY_METADATA).append("\": {\n");
                    sb.append("        \"").append(KEY_TECHNOLOGY).append("\": ").append(jsonString(comp.technology()));
                    if (comp.repository() != null) {
                        sb.append(",\n");
                        sb.append("        \"").append(KEY_REPOSITORY).append("\": ").append(jsonString(comp.repository()));
                    }
                    sb.append("\n      }\n");
                } else {
                    sb.append("\n");
                }

                sb.append("    }");
                if (i < components.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        }

        sb.append("  ],\n");

        // Cross-cutting concerns (if any)
        sb.append("  \"").append(KEY_CROSS_CUTTING_CONCERNS).append("\": [\n");
        sb.append("    {\n");
        sb.append("      \"").append(KEY_NAME).append("\": ").append(jsonString(VALUE_TEST_CODE)).append(",\n");
        sb.append("      \"").append(KEY_PATH_PATTERNS).append("\": [\"").append(PATTERN_TEST_FILES).append("\", \"").append(PATTERN_SPEC_FILES).append("\", \"").append(PATTERN_TEST_DIR).append("\"]\n");
        sb.append("    },\n");
        sb.append("    {\n");
        sb.append("      \"").append(KEY_NAME).append("\": ").append(jsonString(VALUE_GENERATED_CODE)).append(",\n");
        sb.append("      \"").append(KEY_PATH_PATTERNS).append("\": [\"").append(PATTERN_GENERATED).append("\", \"").append(PATTERN_TARGET).append("\", \"").append(PATTERN_BUILD).append("\"]\n");
        sb.append("    }\n");
        sb.append("  ],\n");

        // Analysis configuration
        sb.append("  \"").append(KEY_ANALYSIS).append("\": {\n");
        sb.append("    \"").append(KEY_SKIP_DUPLICATION).append("\": false,\n");
        sb.append("    \"").append(KEY_SKIP_DEPENDENCIES).append("\": false,\n");
        sb.append("    \"").append(KEY_CACHE_SOURCE_FILES).append("\": true\n");
        sb.append("  },\n");

        // Goals (optional)
        sb.append("  \"").append(KEY_GOALS).append("\": {\n");
        sb.append("    \"").append(KEY_MAIN).append("\": [\n");
        sb.append("      {\n");
        sb.append("        \"").append(KEY_DESCRIPTION).append("\": ").append(jsonString(VALUE_MAINTAIN_COMPONENT)).append(",\n");
        sb.append("        \"").append(KEY_TYPE).append("\": ").append(jsonString(VALUE_METRIC)).append(",\n");
        sb.append("        \"").append(KEY_TARGET).append("\": ").append(jsonString(VALUE_COMPONENT_DEPENDENCIES)).append("\n");
        sb.append("      }\n");
        sb.append("    ]\n");
        sb.append("  }\n");

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Infers file extensions from the architecture model.
     */
    private Set<String> inferExtensions(ArchitectureModel model) {
        Set<String> extensions = new HashSet<>();

        for (Component comp : model.components()) {
            String tech = comp.technology();
            if (tech != null) {
                extensions.addAll(getExtensionsForTechnology(tech.toLowerCase()));
            }
        }

        // Add common extensions if none found
        if (extensions.isEmpty()) {
            extensions.addAll(List.of(DEFAULT_EXTENSIONS));
        }

        return extensions;
    }

    /**
     * Maps technology to file extensions.
     */
    private List<String> getExtensionsForTechnology(String technology) {
        return switch (technology) {
            case EXT_JAVA, TECH_SPRING, TECH_SPRING_BOOT -> List.of(EXT_JAVA);
            case TECH_KOTLIN -> List.of(EXT_KOTLIN, EXT_KT_SCRIPT);
            case TECH_PYTHON, TECH_DJANGO, TECH_FLASK, TECH_FASTAPI -> List.of(EXT_PYTHON);
            case TECH_JAVASCRIPT, TECH_NODE, TECH_EXPRESS, TECH_REACT, TECH_VUE -> List.of(EXT_JAVASCRIPT, EXT_JSX);
            case TECH_TYPESCRIPT, TECH_ANGULAR -> List.of(EXT_TYPESCRIPT, EXT_TSX);
            case TECH_CSHARP, TECH_DOTNET, TECH_ASPNET -> List.of(EXT_CSHARP);
            case TECH_GOLANG -> List.of(EXT_GO);
            case TECH_RUBY, TECH_RAILS -> List.of(EXT_RUBY);
            case TECH_PHP, TECH_LARAVEL -> List.of(EXT_PHP);
            case TECH_RUST -> List.of(EXT_RUST);
            case TECH_SCALA -> List.of(EXT_SCALA);
            default -> List.of();
        };
    }

    /**
     * Generates path patterns for a component based on its properties.
     */
    private List<String> generatePathPatterns(Component comp, ArchitectureModel model) {
        List<String> patterns = new ArrayList<>();

        String baseName = comp.name()
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "")
            .replaceAll(SUFFIX_SERVICE + "$", "")
            .replaceAll(SUFFIX_APPLICATION + "$", "")
            .replaceAll(SUFFIX_API + "$", "");

        // Add patterns based on component type
        switch (comp.type()) {
            case SERVICE, MODULE -> {
                patterns.add("**/" + baseName + "/**");
                patterns.add(PATTERN_SRC_MAIN_JAVA + "/" + baseName + "/**");
                patterns.add(PATTERN_SRC + "/" + baseName + "/**");
            }
            case LIBRARY -> {
                patterns.add("**/" + baseName + "/**");
                patterns.add(PATTERN_LIB + "/" + baseName + "/**");
            }
            case DATABASE -> {
                patterns.add(PATTERN_MIGRATIONS);
                patterns.add(PATTERN_SCHEMA);
                patterns.add(PATTERN_DB);
            }
            case API_GATEWAY -> {
                patterns.add(PATTERN_GATEWAY);
                patterns.add(PATTERN_API);
            }
            case MESSAGE_BROKER -> {
                patterns.add(PATTERN_MESSAGING);
                patterns.add(PATTERN_EVENTS);
            }
            default -> patterns.add("**/" + baseName + "/**");
        }

        // If repository is specified, add repository-based patterns
        if (comp.repository() != null && !comp.repository().isEmpty()) {
            String repoName = comp.repository().replaceAll(CHAR_SLASH, "").replaceAll("\\" + CHAR_GIT_SUFFIX + "$", "");
            patterns.add(repoName + "/**");
        }

        // Fallback pattern
        if (patterns.isEmpty()) {
            patterns.add("**/*" + capitalize(baseName) + "*");
        }

        return patterns;
    }

    /**
     * Capitalizes first letter of a string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Escapes special characters for JSON strings.
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Wraps a string in JSON quotes with proper escaping.
     */
    private String jsonString(String text) {
        return "\"" + escape(text) + "\"";
    }
}
