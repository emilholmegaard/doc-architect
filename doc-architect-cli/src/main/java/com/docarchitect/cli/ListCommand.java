package com.docarchitect.cli;

import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.generator.DiagramGenerator;
import com.docarchitect.core.renderer.OutputRenderer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;
import java.util.concurrent.Callable;

/**
 * Command to list available scanners, generators, or renderers.
 *
 * <p>Discovers plugins via Java Service Provider Interface (SPI) and displays
 * their capabilities.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * # List all scanners
 * docarchitect list scanners
 *
 * # List all generators
 * docarchitect list generators
 *
 * # List all renderers
 * docarchitect list renderers
 * }</pre>
 */
@Command(
    name = "list",
    description = "List available scanners, generators, or renderers",
    mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ListCommand.class);

    @Parameters(
        index = "0",
        description = "Type to list: scanners, generators, or renderers"
    )
    private String type;

    @Override
    public Integer call() {
        return switch (type.toLowerCase()) {
            case "scanners", "scanner" -> listScanners();
            case "generators", "generator" -> listGenerators();
            case "renderers", "renderer" -> listRenderers();
            default -> {
                log.error("Unknown type: {}. Use: scanners, generators, or renderers", type);
                yield 1;
            }
        };
    }

    private int listScanners() {
        System.out.println("Available Scanners:");
        System.out.println();

        ServiceLoader<Scanner> scanners = ServiceLoader.load(Scanner.class);
        boolean found = false;

        for (Scanner scanner : scanners) {
            found = true;
            System.out.printf("  • %s (ID: %s)%n", scanner.getDisplayName(), scanner.getId());
            System.out.printf("    Languages: %s%n", scanner.getSupportedLanguages());
            System.out.printf("    Priority: %d%n", scanner.getPriority());
            System.out.println();
        }

        if (!found) {
            System.out.println("  No scanners found.");
            System.out.println("  Scanners will be implemented in Phase 3.");
        }

        return 0;
    }

    private int listGenerators() {
        System.out.println("Available Generators:");
        System.out.println();

        ServiceLoader<DiagramGenerator> generators = ServiceLoader.load(DiagramGenerator.class);
        boolean found = false;

        for (DiagramGenerator generator : generators) {
            found = true;
            System.out.printf("  • %s (ID: %s)%n", generator.getDisplayName(), generator.getId());
            System.out.printf("    File Extension: .%s%n", generator.getFileExtension());
            System.out.printf("    Diagram Types: %s%n", generator.getSupportedDiagramTypes());
            System.out.println();
        }

        if (!found) {
            System.out.println("  No generators found.");
            System.out.println("  Generators will be implemented in Phase 4.");
        }

        return 0;
    }

    private int listRenderers() {
        System.out.println("Available Renderers:");
        System.out.println();

        ServiceLoader<OutputRenderer> renderers = ServiceLoader.load(OutputRenderer.class);
        boolean found = false;

        for (OutputRenderer renderer : renderers) {
            found = true;
            System.out.printf("  • %s (ID: %s)%n", renderer.getId(), renderer.getId());
            System.out.println();
        }

        if (!found) {
            System.out.println("  No renderers found.");
            System.out.println("  Renderers will be implemented in Phase 5.");
        }

        return 0;
    }
}
