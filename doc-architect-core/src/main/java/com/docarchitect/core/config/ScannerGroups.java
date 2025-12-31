package com.docarchitect.core.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines technology groups for scanner auto-configuration.
 *
 * <p>Groups allow users to enable multiple related scanners with a single configuration:</p>
 * <pre>{@code
 * scanners:
 *   groups:
 *     - java
 *     - messaging
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ScannerGroups {

    private ScannerGroups() {
        // Utility class
    }

    /**
     * Map of group names to scanner IDs.
     */
    public static final Map<String, List<String>> GROUPS = Map.ofEntries(
        // Java/JVM ecosystem
        Map.entry("java", List.of(
            "maven-dependencies",
            "gradle-dependencies",
            "spring-component",
            "spring-rest-api",
            "jaxrs-api",
            "jpa-entities",
            "mongodb-repository",
            "java-http-client",
            "java-grpc-service"
        )),

        // Python ecosystem
        Map.entry("python", List.of(
            "pip-poetry-dependencies",
            "django-app",
            "django-orm",
            "fastapi-endpoints",
            "flask-routes",
            "sqlalchemy-models"
        )),

        // .NET ecosystem
        Map.entry("dotnet", List.of(
            "nuget-dependencies",
            "dotnet-solution",
            "aspnet-core-api",
            "entity-framework",
            "dotnet-grpc-service"
        )),

        // Go ecosystem
        Map.entry("go", List.of(
            "go-modules",
            "go-http-router",
            "go-struct-orm",
            "go-grpc-service"
        )),

        // Ruby ecosystem
        Map.entry("ruby", List.of(
            "bundler-dependencies",
            "rails-api",
            "rails-routes"
        )),

        // JavaScript/TypeScript ecosystem
        Map.entry("javascript", List.of(
            "npm-dependencies",
            "express-routes"
        )),

        // Messaging (cross-language)
        Map.entry("messaging", List.of(
            "kafka-consumer",
            "kafka-streams",
            "rabbitmq-listener",
            "dotnet-kafka",
            "dotnet-kafka-streams",
            "celery-tasks",
            "faust-streams",
            "sidekiq-workers"
        )),

        // Schema definitions (cross-language)
        Map.entry("schema", List.of(
            "graphql-schema",
            "avro-schema",
            "protobuf-schema",
            "sql-migrations",
            "rest-event-flow"
        ))
    );

    /**
     * Get all scanner IDs for the specified groups.
     *
     * @param groupNames names of groups to expand
     * @return set of scanner IDs from all specified groups
     */
    public static Set<String> getScannersForGroups(List<String> groupNames) {
        return groupNames.stream()
            .filter(GROUPS::containsKey)
            .flatMap(group -> GROUPS.get(group).stream())
            .collect(Collectors.toSet());
    }

    /**
     * Check if a scanner belongs to any of the specified groups.
     *
     * @param scannerId scanner ID to check
     * @param groupNames group names to check
     * @return true if scanner is in any of the groups
     */
    public static boolean isInGroups(String scannerId, List<String> groupNames) {
        return groupNames.stream()
            .filter(GROUPS::containsKey)
            .flatMap(group -> GROUPS.get(group).stream())
            .anyMatch(id -> id.equals(scannerId));
    }

    /**
     * Get all available group names.
     *
     * @return set of group names
     */
    public static Set<String> getAvailableGroups() {
        return GROUPS.keySet();
    }
}
