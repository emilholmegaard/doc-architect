package com.docarchitect.core.model;

/**
 * Types of architectural components.
 */
public enum ComponentType {
    /** Microservice or standalone application */
    SERVICE,

    /** Module within a larger application */
    MODULE,

    /** Shared library or utility package */
    LIBRARY,

    /** External system or third-party service */
    EXTERNAL,

    /** Database instance */
    DATABASE,

    /** Message broker or queue system */
    MESSAGE_BROKER,

    /** API Gateway */
    API_GATEWAY,

    /** Load balancer */
    LOAD_BALANCER,

    /** Cache system */
    CACHE,

    /** Unknown or unclassified component */
    UNKNOWN
}
