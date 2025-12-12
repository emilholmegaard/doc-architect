package com.docarchitect;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main CLI entry point for DocArchitect.
 */
@Command(name = "doc-architect", mixinStandardHelpOptions = true, version = "DocArchitect 1.0.0", description = "Automated Architecture Documentation Generator from Source Code")
public class DocArchitectCLI implements Runnable {

    @Option(names = { "-v", "--verbose" }, description = "Verbose output")
    private boolean verbose;

    @Override
    public void run() {
        System.out.println("DocArchitect - Automated Architecture Documentation Generator");
        System.out.println("Version: 1.0.0");
        System.out.println();
        System.out.println("Use --help for available commands");
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DocArchitectCLI()).execute(args);
        System.exit(exitCode);
    }
}
