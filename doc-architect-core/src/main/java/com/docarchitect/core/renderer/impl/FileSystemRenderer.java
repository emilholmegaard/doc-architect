package com.docarchitect.core.renderer.impl;

import com.docarchitect.core.renderer.GeneratedFile;
import com.docarchitect.core.renderer.GeneratedOutput;
import com.docarchitect.core.renderer.OutputRenderer;
import com.docarchitect.core.renderer.RenderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Renderer that writes generated files to the filesystem.
 *
 * <p>Creates directory structure automatically and preserves relative paths.
 * Handles existing files by overwriting them.
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>{@code outputDirectory} - Target directory (from RenderContext)</li>
 * </ul>
 *
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * RenderContext context = new RenderContext(
 *     "./docs/architecture",
 *     Map.of()
 * );
 *
 * GeneratedOutput output = new GeneratedOutput(List.of(
 *     new GeneratedFile("diagrams/dependencies.md", "# Dependencies...", "text/markdown")
 * ));
 *
 * FileSystemRenderer renderer = new FileSystemRenderer();
 * renderer.render(output, context);
 * // Creates: ./docs/architecture/diagrams/dependencies.md
 * }</pre>
 */
public class FileSystemRenderer implements OutputRenderer {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemRenderer.class);

    @Override
    public String getId() {
        return "filesystem";
    }

    @Override
    public void render(GeneratedOutput output, RenderContext context) {
        Path outputDir = Paths.get(context.outputDirectory());
        logger.info("Rendering {} files to filesystem at: {}", output.files().size(), outputDir);

        try {
            // Create output directory if it doesn't exist
            Files.createDirectories(outputDir);
            logger.debug("Output directory created/verified: {}", outputDir);

            // Verify directory is writable
            if (!Files.isWritable(outputDir)) {
                throw new IOException("Output directory is not writable: " + outputDir +
                    ". Check permissions or run with appropriate user/group.");
            }

            // Write each file
            for (GeneratedFile file : output.files()) {
                writeFile(outputDir, file);
            }

            logger.info("Successfully rendered {} files to filesystem", output.files().size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create output directory: " + outputDir +
                ". Ensure the directory exists and has write permissions.", e);
        }
    }

    /**
     * Writes a single file to the filesystem.
     *
     * @param outputDir base output directory
     * @param file file to write
     */
    private void writeFile(Path outputDir, GeneratedFile file) {
        Path targetPath = outputDir.resolve(file.relativePath());
        logger.debug("Writing file: {}", targetPath);

        try {
            // Create parent directories if needed
            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);

                // Verify parent directory is writable
                if (!Files.isWritable(parentDir)) {
                    throw new IOException("Parent directory is not writable: " + parentDir +
                        ". Check directory permissions.");
                }
            }

            // Write file content
            Files.writeString(targetPath, file.content());
            logger.info("Wrote file: {} ({} bytes)", file.relativePath(), file.content().length());
        } catch (IOException e) {
            String errorMsg = String.format(
                "Failed to write file: %s to %s. Error: %s. " +
                "Ensure the output directory has write permissions (e.g., chmod 777 for Docker mounts).",
                file.relativePath(), targetPath, e.getMessage()
            );
            throw new IllegalStateException(errorMsg, e);
        }
    }
}
