package com.docarchitect.core.model;

import java.util.Objects;

/**
 * Represents a relationship between two components.
 *
 * @param sourceId source component ID
 * @param targetId target component ID
 * @param type relationship type
 * @param description optional description of the relationship
 * @param technology technology used for the relationship (HTTP, Kafka, etc.)
 */
public record Relationship(
    String sourceId,
    String targetId,
    RelationshipType type,
    String description,
    String technology
) {
    /**
     * Compact constructor with validation.
     */
    public Relationship {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(type, "type must not be null");
    }
}
