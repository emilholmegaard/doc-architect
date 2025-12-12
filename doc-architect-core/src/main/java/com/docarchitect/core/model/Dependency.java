package com.docarchitect.core.model;

import java.util.Objects;

/**
 * Represents a dependency between components or on external libraries.
 *
 * @param sourceComponentId component that has the dependency
 * @param groupId dependency group/organization ID
 * @param artifactId dependency artifact ID
 * @param version dependency version
 * @param scope dependency scope (compile, runtime, test, etc.)
 * @param direct true if direct dependency, false if transitive
 */
public record Dependency(
    String sourceComponentId,
    String groupId,
    String artifactId,
    String version,
    String scope,
    boolean direct
) {
    /**
     * Compact constructor with validation.
     */
    public Dependency {
        Objects.requireNonNull(sourceComponentId, "sourceComponentId must not be null");
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        if (scope == null) {
            scope = "compile";
        }
    }
}
