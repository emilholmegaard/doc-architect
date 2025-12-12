package com.docarchitect.core.renderer;

import java.util.List;
import java.util.Objects;

/**
 * Collection of generated files to be rendered.
 *
 * @param files list of generated files
 */
public record GeneratedOutput(
    List<GeneratedFile> files
) {
    /**
     * Compact constructor with validation.
     */
    public GeneratedOutput {
        Objects.requireNonNull(files, "files must not be null");
        files = List.copyOf(files);
    }
}
