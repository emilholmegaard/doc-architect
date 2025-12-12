package com.docarchitect.core.generator;

import java.util.Objects;

/**
 * Represents a generated diagram.
 *
 * @param name diagram name/title
 * @param content diagram content (Mermaid, PlantUML, etc.)
 * @param fileExtension file extension for this content
 */
public record GeneratedDiagram(
    String name,
    String content,
    String fileExtension
) {
    /**
     * Compact constructor with validation.
     */
    public GeneratedDiagram {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(fileExtension, "fileExtension must not be null");
    }
}
