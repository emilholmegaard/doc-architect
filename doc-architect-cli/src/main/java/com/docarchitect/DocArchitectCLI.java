package com.docarchitect;

import com.docarchitect.cli.InitCommand;
import com.docarchitect.cli.ListCommand;
import com.docarchitect.cli.ScanCommand;
import com.docarchitect.cli.GenerateCommand;
import com.docarchitect.cli.ValidateCommand;
import com.docarchitect.cli.DiffCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

/**
 * Main CLI entry point for DocArchitect.
 *
 * <p>DocArchitect scans source code and generates architecture documentation including
 * dependency graphs, API documentation, ER diagrams, and C4 models.
 *
 * <p><b>Commands:</b>
 * <ul>
 *   <li>{@code init} - Initialize configuration file</li>
 *   <li>{@code scan} - Scan codebase and generate documentation</li>
 *   <li>{@code generate} - Generate specific diagram types</li>
 *   <li>{@code list} - List available scanners, generators, or renderers</li>
 *   <li>{@code validate} - Validate configuration and architecture model</li>
 *   <li>{@code diff} - Compare architecture against baseline</li>
 * </ul>
 *
 * <p><b>Global Options:</b>
 * <ul>
 *   <li>{@code -v, --verbose} - Enable verbose output</li>
 *   <li>{@code -q, --quiet} - Suppress all output except errors</li>
 *   <li>{@code --help} - Show help information</li>
 *   <li>{@code --version} - Show version information</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * # Initialize configuration
 * doc-architect init
 *
 * # Scan current directory
 * doc-architect scan
 *
 * # Scan with verbose output
 * doc-architect scan -v
 *
 * # List available scanners
 * doc-architect list scanners
 * }</pre>
 */
@Command(
    name = "docarchitect",
    mixinStandardHelpOptions = true,
    version = "DocArchitect 1.0.0-SNAPSHOT",
    description = "Automated Architecture Documentation Generator from Source Code",
    subcommands = {
        InitCommand.class,
        ScanCommand.class,
        GenerateCommand.class,
        ListCommand.class,
        ValidateCommand.class,
        DiffCommand.class
    }
)
public class DocArchitectCLI implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DocArchitectCLI.class);

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output (DEBUG level)")
    private boolean verbose;

    @Option(names = {"-q", "--quiet"}, description = "Suppress all output except errors")
    private boolean quiet;

    @Override
    public void run() {
        configureLogging();

        if (quiet) {
            return; // Suppress banner in quiet mode
        }

        System.out.println("DocArchitect - Automated Architecture Documentation Generator");
        System.out.println("Version: 1.0.0-SNAPSHOT");
        System.out.println();
        System.out.println("Use 'docarchitect --help' to see available commands");
        System.out.println("Use 'docarchitect <command> --help' for command-specific help");
    }

    /**
     * Configures logging level based on global options.
     */
    private void configureLogging() {
        ch.qos.logback.classic.Logger root =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        if (quiet) {
            root.setLevel(Level.ERROR);
        } else if (verbose) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }
    }

    /**
     * Returns whether verbose mode is enabled.
     *
     * @return true if verbose mode is enabled
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Returns whether quiet mode is enabled.
     *
     * @return true if quiet mode is enabled
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Main entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new DocArchitectCLI()).execute(args);
        System.exit(exitCode);
    }
}
