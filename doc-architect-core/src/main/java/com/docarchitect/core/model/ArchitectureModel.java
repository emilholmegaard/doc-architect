package com.docarchitect.core.model;

import java.util.List;
import java.util.Objects;

/**
 * Central aggregation of all scan results into a unified architecture model.
 *
 * <p>This is the intermediate representation that bridges scanners and generators.
 * Scanners populate this model via {@link com.docarchitect.core.scanner.ScanResult}s,
 * and generators consume it to produce diagrams.
 *
 * @param projectName project or system name
 * @param projectVersion project version
 * @param repositories list of scanned repositories
 * @param components discovered components
 * @param dependencies library/framework dependencies
 * @param relationships relationships between components
 * @param apiEndpoints API endpoints exposed by components
 * @param messageFlows message flows between components
 * @param dataEntities data entities (tables, collections, etc.)
 */
public record ArchitectureModel(
    String projectName,
    String projectVersion,
    List<String> repositories,
    List<Component> components,
    List<Dependency> dependencies,
    List<Relationship> relationships,
    List<ApiEndpoint> apiEndpoints,
    List<MessageFlow> messageFlows,
    List<DataEntity> dataEntities
) {
    /**
     * Compact constructor with validation.
     */
    public ArchitectureModel {
        Objects.requireNonNull(projectName, "projectName must not be null");
        if (projectVersion == null) {
            projectVersion = "unknown";
        }
        if (repositories == null) {
            repositories = List.of();
        }
        if (components == null) {
            components = List.of();
        }
        if (dependencies == null) {
            dependencies = List.of();
        }
        if (relationships == null) {
            relationships = List.of();
        }
        if (apiEndpoints == null) {
            apiEndpoints = List.of();
        }
        if (messageFlows == null) {
            messageFlows = List.of();
        }
        if (dataEntities == null) {
            dataEntities = List.of();
        }
    }
}
