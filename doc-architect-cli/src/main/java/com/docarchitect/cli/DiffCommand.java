package com.docarchitect.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command to compare architecture against baseline for CI/CD integration.
 */
@Command(
    name = "diff",
    description = "Compare current architecture against baseline",
    mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DiffCommand.class);

    @Option(names = {"-b", "--baseline"}, description = "Baseline model file", required = true)
    private Path baselineFile;

    @Option(names = {"--fail-on-breaking"}, description = "Fail if breaking changes detected")
    private boolean failOnBreaking;

    @Override
    public Integer call() {
        log.info("Diff command - baseline: {}", baselineFile);
        System.out.println("Diff command will be fully implemented in Phase 7");
        return 0;
    }
}
