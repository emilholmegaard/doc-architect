package com.docarchitect.core.model;

/**
 * Types of API endpoints.
 */
public enum ApiType {
    /** REST/HTTP endpoint */
    REST,

    /** GraphQL query operation */
    GRAPHQL_QUERY,

    /** GraphQL mutation operation */
    GRAPHQL_MUTATION,

    /** GraphQL subscription operation */
    GRAPHQL_SUBSCRIPTION,

    /** gRPC service method */
    GRPC,

    /** WebSocket endpoint */
    WEBSOCKET,

    /** SOAP web service */
    SOAP
}
