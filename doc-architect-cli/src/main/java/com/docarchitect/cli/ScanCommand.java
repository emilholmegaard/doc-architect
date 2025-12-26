package com.docarchitect.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.model.*;
import com.docarchitect.core.generator.DiagramGenerator;
import com.docarchitect.core.generator.DiagramType;
import com.docarchitect.core.generator.GeneratedDiagram;
import com.docarchitect.core.generator.GeneratorConfig;
import com.docarchitect.core.renderer.OutputRenderer;
import com.docarchitect.core.renderer.GeneratedOutput;
import com.docarchitect.core.renderer.GeneratedFile;
import com.docarchitect.core.renderer.RenderContext;
import com.docarchitect.core.config.ProjectConfig;
import com.docarchitect.core.config.ConfigLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Command to scan codebase and generate documentation.
 *
 * <p>Orchestrates the full documentation pipeline:
 * <ol>
 *   <li>Discover and load scanners via SPI</li>
 *   <li>Execute scanners in priority order</li>
 *   <li>Aggregate results into ArchitectureModel</li>
 *   <li>Generate diagrams using configured generators</li>
 *   <li>Render output to configured destinations</li>
 * </ol>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * # Scan current directory
 * docarchitect scan
 *
 * # Scan specific directory
 * docarchitect scan /path/to/project
 *
 * # Dry run (no output generated)
 * docarchitect scan --dry-run
 * }</pre>
 */
@Command(
    name = "scan",
    description = "Scan codebase and generate architecture documentation",
    mixinStandardHelpOptions = true
)
public class ScanCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ScanCommand.class);

    @Parameters(
        index = "0",
        description = "Project directory (default: current directory)",
        defaultValue = "."
    )
    private Path projectPath;

    @Option(
        names = {"-c", "--config"},
        description = "Configuration file (default: docarchitect.yaml)"
    )
    private Path configPath = Paths.get("docarchitect.yaml");

    @Option(
        names = {"--dry-run"},
        description = "Run scanners but don't generate output"
    )
    private boolean dryRun;

    @Option(
        names = {"-o", "--output"},
        description = "Output directory (overrides config)"
    )
    private Path outputDir;

    @Override
    public Integer call() {
        try {
            log.info("Starting scan of: {}", projectPath.toAbsolutePath());
            System.out.println("Scanning project: " + projectPath.toAbsolutePath());
            System.out.println();

            if (dryRun) {
                System.out.println("Running in dry-run mode (no output will be generated)");
                System.out.println();
            }

            // Step 0: Load configuration
            ProjectConfig config = loadConfiguration();

            // Step 1: Discover and load scanners
            List<Scanner> scanners = discoverScanners();
            System.out.println("✓ Discovered " + scanners.size() + " scanners");

            // Step 2: Filter and execute scanners based on config
            Map<String, ScanResult> scanResults = executeScanners(scanners, config);
            System.out.println("✓ Executed " + scanResults.size() + " scanners");

            // Step 3: Aggregate results into ArchitectureModel
            ArchitectureModel model = aggregateResults(scanResults);
            printModelSummary(model);

            if (dryRun) {
                System.out.println();
                System.out.println("Dry-run mode: Skipping diagram generation and output rendering");
                return 0;
            }

            // Step 4: Generate diagrams
            List<GeneratedDiagram> diagrams = generateDiagrams(model);
            System.out.println("✓ Generated " + diagrams.size() + " diagrams");

            // Step 5: Convert diagrams to GeneratedOutput
            GeneratedOutput output = convertToOutput(diagrams, model);
            System.out.println("✓ Created " + output.files().size() + " output files");

            // Step 6: Render output
            renderOutput(output);
            System.out.println("✓ Rendered output to: " + getOutputDirectory());

            System.out.println();
            System.out.println("✓ Scan complete");

            return 0;

        } catch (Exception e) {
            log.error("Scan failed", e);
            System.err.println("✗ Scan failed: " + e.getMessage());
            if (log.isDebugEnabled()) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Loads project configuration from YAML file.
     */
    private ProjectConfig loadConfiguration() {
        Path absoluteConfigPath = configPath.isAbsolute()
            ? configPath
            : projectPath.resolve(configPath);

        log.debug("Loading configuration from: {}", absoluteConfigPath);
        ProjectConfig config = ConfigLoader.load(absoluteConfigPath);

        if (config.scanners() != null && config.scanners().enabled() != null) {
            log.debug("Configured enabled scanners: {}", config.scanners().enabled());
        } else {
            log.debug("No scanner filtering configured - all scanners enabled");
        }

        return config;
    }

    /**
     * Discovers all available scanners via SPI.
     */
    private List<Scanner> discoverScanners() {
        log.debug("Discovering scanners via ServiceLoader");
        ServiceLoader<Scanner> loader = ServiceLoader.load(Scanner.class);
        List<Scanner> scanners = new ArrayList<>();
        loader.forEach(scanners::add);

        // Sort by priority (higher priority first)
        scanners.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        log.info("Discovered {} scanners", scanners.size());
        if (log.isDebugEnabled()) {
            scanners.forEach(s -> log.debug("  - {} ({})", s.getId(), s.getDisplayName()));
        }
        return scanners;
    }

    /**
     * Executes scanners that are enabled and apply to the project.
     *
     * @param scanners discovered scanners
     * @param config project configuration
     * @return scan results by scanner ID
     */
    private Map<String, ScanResult> executeScanners(List<Scanner> scanners, ProjectConfig config) {
        log.debug("Executing scanners on project: {}", projectPath);

        // Validate config and warn about unknown scanner IDs
        validateScannerConfig(scanners, config);

        Map<String, ScanResult> results = new LinkedHashMap<>();
        ScanContext context = createScanContext(results);

        int skippedCount = 0;
        int notApplicableCount = 0;

        for (Scanner scanner : scanners) {
            try {
                // Check if scanner is enabled in config
                if (config.scanners() != null && !config.scanners().isEnabled(scanner.getId())) {
                    log.debug("Scanner {} is disabled in configuration", scanner.getId());
                    skippedCount++;
                    continue;
                }

                if (scanner.appliesTo(context)) {
                    log.info("Running scanner: {} ({})", scanner.getDisplayName(), scanner.getId());
                    System.out.println("  → " + scanner.getDisplayName());

                    ScanResult result = scanner.scan(context);
                    results.put(scanner.getId(), result);

                    if (result.hasFindings()) {
                        log.debug("Scanner {} found: {} components, {} dependencies, {} endpoints, {} entities",
                            scanner.getId(),
                            result.components().size(),
                            result.dependencies().size(),
                            result.apiEndpoints().size(),
                            result.dataEntities().size());
                    }
                } else {
                    log.debug("Scanner {} does not apply to this project", scanner.getId());
                    notApplicableCount++;
                }
            } catch (Exception e) {
                log.error("Scanner {} failed: {}", scanner.getId(), e.getMessage(), e);
                results.put(scanner.getId(), ScanResult.failed(scanner.getId(), List.of(e.getMessage())));
            }
        }

        log.debug("Scanner execution summary: {} executed, {} disabled, {} not applicable",
            results.size(), skippedCount, notApplicableCount);

        // Warn if no scanners executed
        if (results.isEmpty()) {
            System.err.println();
            System.err.println("⚠ WARNING: 0 scanners executed!");
            System.err.println("  Possible causes:");
            System.err.println("  - Scanner IDs in config don't match available scanners");
            System.err.println("  - No files match scanner patterns");
            System.err.println("  - All scanners failed");
            System.err.println();
            System.err.println("  Available scanner IDs:");
            scanners.forEach(s -> System.err.println("    - " + s.getId()));
            System.err.println();
        }

        return results;
    }

    /**
     * Validates scanner configuration and warns about unknown IDs.
     *
     * @param scanners discovered scanners
     * @param config project configuration
     */
    private void validateScannerConfig(List<Scanner> scanners, ProjectConfig config) {
        if (config.scanners() == null || config.scanners().enabled() == null || config.scanners().enabled().isEmpty()) {
            return; // No filtering configured
        }

        Set<String> availableIds = scanners.stream()
            .map(Scanner::getId)
            .collect(java.util.stream.Collectors.toSet());

        List<String> unknownIds = config.scanners().enabled().stream()
            .filter(id -> !availableIds.contains(id))
            .toList();

        if (!unknownIds.isEmpty()) {
            log.warn("Unknown scanner IDs in configuration: {}", unknownIds);
            System.err.println("⚠ WARNING: Unknown scanner IDs in configuration:");
            unknownIds.forEach(id -> System.err.println("    - " + id));
            System.err.println();
            System.err.println("  Available scanner IDs:");
            availableIds.forEach(id -> System.err.println("    - " + id));
            System.err.println();
        }
    }

    /**
     * Creates a ScanContext for scanner execution.
     */
    private ScanContext createScanContext(Map<String, ScanResult> previousResults) {
        Path absolutePath = projectPath.toAbsolutePath().normalize();
        return new ScanContext(
            absolutePath,
            List.of(absolutePath),
            Map.of(),
            Map.of(),
            previousResults
        );
    }

    /**
     * Aggregates all scan results into a unified ArchitectureModel.
     */
    private ArchitectureModel aggregateResults(Map<String, ScanResult> scanResults) {
        log.debug("Aggregating scan results into ArchitectureModel");

        List<Component> allComponents = new ArrayList<>();
        List<Dependency> allDependencies = new ArrayList<>();
        List<ApiEndpoint> allApiEndpoints = new ArrayList<>();
        List<MessageFlow> allMessageFlows = new ArrayList<>();
        List<DataEntity> allDataEntities = new ArrayList<>();
        List<Relationship> allRelationships = new ArrayList<>();

        for (ScanResult result : scanResults.values()) {
            if (result.success()) {
                allComponents.addAll(result.components());
                allDependencies.addAll(result.dependencies());
                allApiEndpoints.addAll(result.apiEndpoints());
                allMessageFlows.addAll(result.messageFlows());
                allDataEntities.addAll(result.dataEntities());
                allRelationships.addAll(result.relationships());
            }
        }

        // Deduplicate components, dependencies, etc. by unique keys
        allComponents = deduplicateByKey(allComponents, Component::id);
        allDependencies = deduplicateByKey(allDependencies, d -> d.groupId() + ":" + d.artifactId() + ":" + d.version());
        allApiEndpoints = deduplicateByKey(allApiEndpoints, e -> e.componentId() + ":" + e.method() + ":" + e.path());
        allMessageFlows = deduplicateByKey(allMessageFlows, m -> m.topic() + ":" + m.publisherComponentId() + ":" + m.subscriberComponentId());
        allDataEntities = deduplicateByKey(allDataEntities, e -> e.componentId() + ":" + e.name());
        allRelationships = deduplicateByKey(allRelationships, r -> r.sourceId() + ":" + r.targetId() + ":" + r.type());

        return new ArchitectureModel(
            projectPath.getFileName() != null ? projectPath.getFileName().toString() : "project",
            "1.0.0",
            List.of(projectPath.toAbsolutePath().toString()),
            allComponents,
            allDependencies,
            allRelationships,
            allApiEndpoints,
            allMessageFlows,
            allDataEntities
        );
    }

    /**
     * Deduplicates a list by a key function, keeping the first occurrence.
     */
    private <T> List<T> deduplicateByKey(List<T> items, java.util.function.Function<T, String> keyExtractor) {
        Map<String, T> seen = new LinkedHashMap<>();
        for (T item : items) {
            String key = keyExtractor.apply(item);
            if (key != null) {
                seen.putIfAbsent(key, item);
            }
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Prints a summary of the architecture model.
     */
    private void printModelSummary(ArchitectureModel model) {
        System.out.println();
        System.out.println("Architecture Model Summary:");
        System.out.println("  Components:     " + model.components().size());
        System.out.println("  Dependencies:   " + model.dependencies().size());
        System.out.println("  API Endpoints:  " + model.apiEndpoints().size());
        System.out.println("  Data Entities:  " + model.dataEntities().size());
        System.out.println("  Message Flows:  " + model.messageFlows().size());
        System.out.println("  Relationships:  " + model.relationships().size());
        System.out.println();
    }

    /**
     * Generates diagrams from the architecture model.
     */
    private List<GeneratedDiagram> generateDiagrams(ArchitectureModel model) {
        log.debug("Discovering diagram generators via ServiceLoader");

        ServiceLoader<DiagramGenerator> loader = ServiceLoader.load(DiagramGenerator.class);
        List<DiagramGenerator> generators = new ArrayList<>();
        loader.forEach(generators::add);

        log.info("Discovered {} diagram generators", generators.size());

        List<GeneratedDiagram> allDiagrams = new ArrayList<>();
        GeneratorConfig config = GeneratorConfig.defaults();

        for (DiagramGenerator generator : generators) {
            try {
                log.info("Running generator: {} ({})", generator.getDisplayName(), generator.getId());
                System.out.println("  → " + generator.getDisplayName());

                for (DiagramType type : generator.getSupportedDiagramTypes()) {
                    try {
                        GeneratedDiagram diagram = generator.generate(model, type, config);
                        allDiagrams.add(diagram);
                        log.debug("Generated diagram: {} (type: {})", diagram.name(), type);
                    } catch (Exception e) {
                        log.warn("Failed to generate diagram type {} with generator {}: {}",
                            type, generator.getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Generator {} failed: {}", generator.getId(), e.getMessage(), e);
            }
        }

        return allDiagrams;
    }

    /**
     * Converts generated diagrams to GeneratedOutput.
     */
    private GeneratedOutput convertToOutput(List<GeneratedDiagram> diagrams, ArchitectureModel model) {
        log.debug("Converting {} diagrams to output files", diagrams.size());

        List<GeneratedFile> files = new ArrayList<>();

        // Add generated diagrams
        for (GeneratedDiagram diagram : diagrams) {
            String relativePath = diagram.name() + "." + diagram.fileExtension();
            files.add(new GeneratedFile(
                relativePath,
                diagram.content(),
                "text/markdown"
            ));
        }

        // Add index file
        String indexContent = generateIndexContent(model);
        files.add(new GeneratedFile("index.md", indexContent, "text/markdown"));

        return new GeneratedOutput(files);
    }

    /**
     * Generates index.md content.
     */
    private String generateIndexContent(ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(model.projectName()).append(" - Architecture Documentation\n\n");
        sb.append("**Version:** ").append(model.projectVersion()).append("\n\n");

        sb.append("## Overview\n\n");
        sb.append("This documentation was automatically generated by DocArchitect.\n\n");

        sb.append("### Architecture Statistics\n\n");
        sb.append("| Metric | Count |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Components | ").append(model.components().size()).append(" |\n");
        sb.append("| Dependencies | ").append(model.dependencies().size()).append(" |\n");
        sb.append("| API Endpoints | ").append(model.apiEndpoints().size()).append(" |\n");
        sb.append("| Data Entities | ").append(model.dataEntities().size()).append(" |\n");
        sb.append("| Message Flows | ").append(model.messageFlows().size()).append(" |\n");
        sb.append("| Relationships | ").append(model.relationships().size()).append(" |\n\n");

        sb.append("## Documentation Sections\n\n");
        sb.append("- [Dependency Graph](dependency-graph.md)\n");
        sb.append("- [Component Catalog](component-catalog.md)\n");
        sb.append("- [API Catalog](api-catalog.md)\n");
        sb.append("- [Data Entity Catalog](data-catalog.md)\n");
        sb.append("- [Message Flow Catalog](message-flow-catalog.md)\n");

        return sb.toString();
    }

    /**
     * Renders output using configured renderers.
     */
    private void renderOutput(GeneratedOutput output) {
        log.debug("Discovering output renderers via ServiceLoader");

        ServiceLoader<OutputRenderer> loader = ServiceLoader.load(OutputRenderer.class);
        List<OutputRenderer> renderers = new ArrayList<>();
        loader.forEach(renderers::add);

        log.info("Discovered {} output renderers", renderers.size());

        // Use filesystem renderer by default
        OutputRenderer fileSystemRenderer = renderers.stream()
            .filter(r -> "filesystem".equals(r.getId()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("FileSystemRenderer not found"));

        RenderContext context = new RenderContext(
            getOutputDirectory(),
            Map.of()
        );

        log.info("Rendering output with: {}", fileSystemRenderer.getId());
        fileSystemRenderer.render(output, context);
    }

    /**
     * Gets the output directory path.
     */
    private String getOutputDirectory() {
        if (outputDir != null) {
            return outputDir.toAbsolutePath().toString();
        }
        // Default to ./docs/architecture
        return projectPath.resolve("docs/architecture").toAbsolutePath().toString();
    }
}
