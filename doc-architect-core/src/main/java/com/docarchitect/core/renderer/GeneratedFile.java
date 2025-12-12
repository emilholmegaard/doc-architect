package com.docarchitect.core.renderer;

import java.util.Objects;

/**
 * Represents a generated file to be rendered.
 *
 * @param relativePath relative path for the file (e.g., "architecture/dependency-graph.md")
 * @param content file content
 * @param contentType content type or format
 */
public record GeneratedFile(
    String relativePath,
    String content,
    String contentType
) {
    /**
     * Compact constructor with validation.
     */
    public GeneratedFile {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
