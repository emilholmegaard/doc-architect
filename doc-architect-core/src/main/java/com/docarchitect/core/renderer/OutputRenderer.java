package com.docarchitect.core.renderer;

/**
 * Interface for output renderers that handle various output destinations.
 *
 * <p>Renderers write generated documentation to different targets: filesystem,
 * console, external services (Confluence, Notion, etc.), or artifact repositories.
 *
 * <p>Renderers are discovered via Java Service Provider Interface (SPI) and can be
 * configured to run in sequence (e.g., write to filesystem AND publish to Confluence).
 *
 * <p><b>Example Implementation:</b>
 * <pre>{@code
 * public class FileSystemRenderer implements OutputRenderer {
 *     @Override
 *     public String getId() {
 *         return "filesystem";
 *     }
 *
 *     @Override
 *     public void render(GeneratedOutput output, RenderContext context) {
 *         Path outputDir = Paths.get(context.getSetting("output.directory"));
 *         Files.createDirectories(outputDir);
 *
 *         for (GeneratedFile file : output.files()) {
 *             Path targetPath = outputDir.resolve(file.relativePath());
 *             Files.createDirectories(targetPath.getParent());
 *             Files.writeString(targetPath, file.content());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Registration:</b> Register implementations in
 * {@code META-INF/services/com.docarchitect.core.renderer.OutputRenderer}
 *
 * @see GeneratedOutput
 * @see RenderContext
 */
public interface OutputRenderer {

    /**
     * Returns unique identifier for this renderer.
     *
     * <p>Used for referencing the renderer in configuration. Should be lowercase
     * (e.g., "filesystem", "console", "confluence").
     *
     * @return unique renderer identifier
     */
    String getId();

    /**
     * Renders the generated output to the target destination.
     *
     * <p>This method should:
     * <ol>
     *   <li>Read configuration from {@link RenderContext} (output path, credentials, etc.)</li>
     *   <li>Process each {@link GeneratedFile} in the output</li>
     *   <li>Write/publish content to the target destination</li>
     *   <li>Handle errors gracefully and log appropriately</li>
     * </ol>
     *
     * <p>Implementations should validate required settings and throw
     * {@link IllegalStateException} if configuration is invalid.
     *
     * @param output the generated documentation files to render
     * @param context rendering context with configuration and settings
     * @throws IllegalStateException if required configuration is missing
     */
    void render(GeneratedOutput output, RenderContext context);
}
