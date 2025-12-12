package com.docarchitect.core.model;

/**
 * Types of relationships between architectural components.
 */
public enum RelationshipType {
    /** Synchronous API call (HTTP, gRPC, etc.) */
    CALLS,

    /** Uses as a dependency (library, framework) */
    USES,

    /** Publishes messages or events to */
    PUBLISHES,

    /** Subscribes to messages or events from */
    SUBSCRIBES,

    /** Generic dependency relationship */
    DEPENDS_ON,

    /** Reads data from (database, cache) */
    READS_FROM,

    /** Writes data to (database, cache) */
    WRITES_TO,

    /** Contains as a sub-component */
    CONTAINS,

    /** Deployed on (infrastructure) */
    DEPLOYED_ON
}
