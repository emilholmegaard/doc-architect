package com.docarchitect.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a data entity (database table, document collection, etc.).
 *
 * @param componentId component that owns or uses this entity
 * @param name entity name (table name, collection name)
 * @param type entity type (table, collection, view, etc.)
 * @param fields list of field/column definitions
 * @param primaryKey primary key field(s)
 * @param description optional description
 */
public record DataEntity(
    String componentId,
    String name,
    String type,
    List<Field> fields,
    String primaryKey,
    String description
) {
    /**
     * Compact constructor with validation.
     */
    public DataEntity {
        Objects.requireNonNull(componentId, "componentId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (fields == null) {
            fields = List.of();
        }
    }

    /**
     * Represents a field/column in a data entity.
     *
     * @param name field name
     * @param dataType field data type
     * @param nullable whether field can be null
     * @param description optional description
     */
    public record Field(
        String name,
        String dataType,
        boolean nullable,
        String description
    ) {
        public Field {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(dataType, "dataType must not be null");
        }
    }
}
