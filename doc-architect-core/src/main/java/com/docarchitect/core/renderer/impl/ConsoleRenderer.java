package com.docarchitect.core.renderer.impl;

import com.docarchitect.core.renderer.GeneratedFile;
import com.docarchitect.core.renderer.GeneratedOutput;
import com.docarchitect.core.renderer.OutputRenderer;
import com.docarchitect.core.renderer.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renderer that outputs generated files to console with optional ANSI color formatting.
 *
 * <p>Supports colored output for better readability in terminals. Color support can be
 * disabled via settings for compatibility with CI/CD environments or when redirecting output.
 *
 * <p><b>Configuration Settings:</b>
 * <ul>
 *   <li>{@code console.colors} - Enable/disable ANSI colors ("true"/"false", default: "true")</li>
 *   <li>{@code console.separator} - Custom separator between files (default: "---")</li>
 *   <li>{@code console.showHeaders} - Show file headers ("true"/"false", default: "true")</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * RenderContext context = new RenderContext(
 *     "./output",
 *     Map.of(
 *         "console.colors", "true",
 *         "console.separator", "==="
 *     )
 * );
 *
 * GeneratedOutput output = new GeneratedOutput(List.of(
 *     new GeneratedFile("diagram.md", "# Diagram...", "text/markdown")
 * ));
 *
 * ConsoleRenderer renderer = new ConsoleRenderer();
 * renderer.render(output, context);
 * }</pre>
 */
public class ConsoleRenderer implements OutputRenderer {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleRenderer.class);

    // ANSI color codes
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    private static final String DEFAULT_SEPARATOR = "---";

    @Override
    public String getId() {
        return "console";
    }

    @Override
    public void render(GeneratedOutput output, RenderContext context) {
        boolean useColors = Boolean.parseBoolean(context.getSettingOrDefault("console.colors", "true"));
        String separator = context.getSettingOrDefault("console.separator", DEFAULT_SEPARATOR);
        boolean showHeaders = Boolean.parseBoolean(context.getSettingOrDefault("console.showHeaders", "true"));

        logger.info("Rendering {} files to console (colors: {}, headers: {})",
                output.files().size(), useColors, showHeaders);

        // Print summary header
        printSummary(output, useColors);

        // Print separator
        System.out.println();
        printSeparator(separator, useColors);
        System.out.println();

        // Print each file
        for (int i = 0; i < output.files().size(); i++) {
            GeneratedFile file = output.files().get(i);

            if (showHeaders) {
                printFileHeader(file, i + 1, output.files().size(), useColors);
            }

            printFileContent(file, useColors);

            // Print separator between files (but not after the last one)
            if (i < output.files().size() - 1) {
                System.out.println();
                printSeparator(separator, useColors);
                System.out.println();
            }
        }

        // Print final separator
        System.out.println();
        printSeparator(separator, useColors);

        logger.info("Successfully rendered {} files to console", output.files().size());
    }

    /**
     * Prints a summary of the output.
     *
     * @param output generated output
     * @param useColors whether to use ANSI colors
     */
    private void printSummary(GeneratedOutput output, boolean useColors) {
        String prefix = useColors ? ANSI_BOLD + ANSI_GREEN : "";
        String suffix = useColors ? ANSI_RESET : "";

        System.out.println(prefix + "Generated " + output.files().size() + " file(s)" + suffix);
    }

    /**
     * Prints a file header with metadata.
     *
     * @param file file to print header for
     * @param index file index (1-based)
     * @param total total number of files
     * @param useColors whether to use ANSI colors
     */
    private void printFileHeader(GeneratedFile file, int index, int total, boolean useColors) {
        String pathColor = useColors ? ANSI_BOLD + ANSI_CYAN : "";
        String metaColor = useColors ? ANSI_YELLOW : "";
        String reset = useColors ? ANSI_RESET : "";

        System.out.println(pathColor + "File " + index + "/" + total + ": " + file.relativePath() + reset);

        if (file.contentType() != null && !file.contentType().isEmpty()) {
            System.out.println(metaColor + "Type: " + file.contentType() + reset);
        }

        System.out.println(metaColor + "Size: " + file.content().length() + " bytes" + reset);
        System.out.println();
    }

    /**
     * Prints file content.
     *
     * @param file file to print
     * @param useColors whether to use ANSI colors (currently unused for content)
     */
    private void printFileContent(GeneratedFile file, boolean useColors) {
        System.out.println(file.content());
    }

    /**
     * Prints a separator line.
     *
     * @param separator separator string to repeat
     * @param useColors whether to use ANSI colors
     */
    private void printSeparator(String separator, boolean useColors) {
        String color = useColors ? ANSI_YELLOW : "";
        String reset = useColors ? ANSI_RESET : "";

        // Repeat separator to fill ~80 characters
        int repeatCount = Math.max(1, 80 / separator.length());
        String line = separator.repeat(repeatCount);

        System.out.println(color + line + reset);
    }
}
