package com.docarchitect.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command to generate specific diagram types from saved models.
 */
@Command(
    name = "generate",
    description = "Generate specific diagram types from saved architecture model",
    mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(GenerateCommand.class);

    @Option(names = {"-t", "--type"}, description = "Diagram type to generate", required = true)
    private String diagramType;

    @Option(names = {"-i", "--input"}, description = "Input model file")
    private Path inputModel = Paths.get(".docarchitect/model.json");

    @Option(names = {"-o", "--output"}, description = "Output directory")
    private Path outputDir = Paths.get("docs/architecture");

    @Override
    public Integer call() {
        log.info("Generate command - diagram type: {}", diagramType);
        System.out.println("Generate command will be fully implemented in Phase 4");
        return 0;
    }
}
