package com.docarchitect.core.generator;

import com.docarchitect.core.model.ArchitectureModel;
import java.util.Set;

/**
 * Interface for diagram generators that transform architecture models into visual formats.
 *
 * <p>Generators convert the intermediate {@link ArchitectureModel} into specific diagram
 * syntaxes (Mermaid, PlantUML, D2, Structurizr DSL, etc.). Each generator supports one or
 * more {@link DiagramType}s.
 *
 * <p>Generators are discovered via Java Service Provider Interface (SPI) and can be
 * enabled/disabled via configuration.
 *
 * <p><b>Example Implementation:</b>
 * <pre>{@code
 * public class MermaidGenerator implements DiagramGenerator {
 *     @Override
 *     public String getId() {
 *         return "mermaid";
 *     }
 *
 *     @Override
 *     public String getDisplayName() {
 *         return "Mermaid Diagram Generator";
 *     }
 *
 *     @Override
 *     public String getFileExtension() {
 *         return "md";
 *     }
 *
 *     @Override
 *     public Set<DiagramType> getSupportedDiagramTypes() {
 *         return Set.of(
 *             DiagramType.DEPENDENCY_GRAPH,
 *             DiagramType.C4_CONTEXT,
 *             DiagramType.ER_DIAGRAM
 *         );
 *     }
 *
 *     @Override
 *     public GeneratedDiagram generate(ArchitectureModel model,
 *                                       DiagramType type,
 *                                       GeneratorConfig config) {
 *         String content = switch (type) {
 *             case DEPENDENCY_GRAPH -> generateDependencyGraph(model);
 *             case C4_CONTEXT -> generateC4Context(model);
 *             case ER_DIAGRAM -> generateErDiagram(model);
 *             default -> throw new IllegalArgumentException("Unsupported type: " + type);
 *         };
 *         return new GeneratedDiagram(type.name().toLowerCase(), content, "md");
 *     }
 * }
 * }</pre>
 *
 * <p><b>Registration:</b> Register implementations in
 * {@code META-INF/services/com.docarchitect.core.generator.DiagramGenerator}
 *
 * @see ArchitectureModel
 * @see DiagramType
 * @see GeneratorConfig
 * @see GeneratedDiagram
 */
public interface DiagramGenerator {

    /**
     * Returns unique identifier for this generator.
     *
     * <p>Used for referencing the generator in configuration. Should be lowercase
     * (e.g., "mermaid", "plantuml", "structurizr").
     *
     * @return unique generator identifier
     */
    String getId();

    /**
     * Returns human-readable display name for this generator.
     *
     * <p>Used in CLI output and logs (e.g., "Mermaid Diagram Generator").
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Returns file extension for generated diagrams.
     *
     * <p>Examples: "md" (Mermaid embedded in Markdown), "puml" (PlantUML),
     * "d2" (D2), "dsl" (Structurizr).
     *
     * @return file extension without leading dot
     */
    String getFileExtension();

    /**
     * Returns set of diagram types this generator can produce.
     *
     * <p>Generators may support multiple diagram types. The framework will call
     * {@link #generate(ArchitectureModel, DiagramType, GeneratorConfig)} for each
     * enabled type.
     *
     * @return supported diagram types
     */
    Set<DiagramType> getSupportedDiagramTypes();

    /**
     * Generates a diagram from the architecture model.
     *
     * <p>This method should:
     * <ol>
     *   <li>Extract relevant data from the model based on diagram type</li>
     *   <li>Transform data into the target syntax (Mermaid, PlantUML, etc.)</li>
     *   <li>Apply configuration settings (theme, layout, filters)</li>
     *   <li>Return a {@link GeneratedDiagram} with the content</li>
     * </ol>
     *
     * <p>If the model is empty or doesn't contain data for the requested type,
     * generate a meaningful placeholder (e.g., "No components found").
     *
     * @param model the architecture model to visualize
     * @param type the diagram type to generate
     * @param config configuration settings for generation
     * @return generated diagram content
     * @throws IllegalArgumentException if diagram type is not supported
     */
    GeneratedDiagram generate(ArchitectureModel model, DiagramType type, GeneratorConfig config);
}
