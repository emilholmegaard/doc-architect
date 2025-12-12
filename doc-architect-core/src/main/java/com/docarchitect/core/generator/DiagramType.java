package com.docarchitect.core.generator;

/**
 * Types of diagrams that can be generated.
 */
public enum DiagramType {
    /** System context diagram (C4 Level 1) */
    C4_CONTEXT,

    /** Container diagram (C4 Level 2) */
    C4_CONTAINER,

    /** Component diagram (C4 Level 3) */
    C4_COMPONENT,

    /** Dependency graph showing component dependencies */
    DEPENDENCY_GRAPH,

    /** Entity-relationship diagram */
    ER_DIAGRAM,

    /** Message flow diagram (event-driven architecture) */
    MESSAGE_FLOW,

    /** API catalog/documentation */
    API_CATALOG,

    /** Deployment diagram */
    DEPLOYMENT,

    /** Sequence diagram for specific flows */
    SEQUENCE
}
