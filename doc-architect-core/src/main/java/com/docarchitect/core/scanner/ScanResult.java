package com.docarchitect.core.scanner;

import com.docarchitect.core.model.*;
import java.util.List;
import java.util.Objects;

/**
 * Result returned by a scanner after execution.
 *
 * @param scannerId ID of the scanner that produced this result
 * @param success whether the scan completed successfully
 * @param components discovered components
 * @param dependencies discovered dependencies
 * @param apiEndpoints discovered API endpoints
 * @param messageFlows discovered message flows
 * @param dataEntities discovered data entities
 * @param relationships discovered relationships
 * @param warnings non-fatal issues encountered during scanning
 * @param errors fatal errors that prevented complete scanning
 */
public record ScanResult(
    String scannerId,
    boolean success,
    List<Component> components,
    List<Dependency> dependencies,
    List<ApiEndpoint> apiEndpoints,
    List<MessageFlow> messageFlows,
    List<DataEntity> dataEntities,
    List<Relationship> relationships,
    List<String> warnings,
    List<String> errors
) {
    /**
     * Compact constructor with validation.
     */
    public ScanResult {
        Objects.requireNonNull(scannerId, "scannerId must not be null");
        if (components == null) {
            components = List.of();
        }
        if (dependencies == null) {
            dependencies = List.of();
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
        if (relationships == null) {
            relationships = List.of();
        }
        if (warnings == null) {
            warnings = List.of();
        }
        if (errors == null) {
            errors = List.of();
        }
    }

    /**
     * Creates a successful scan result with no findings.
     *
     * @param scannerId scanner ID
     * @return empty successful result
     */
    public static ScanResult empty(String scannerId) {
        return new ScanResult(
            scannerId,
            true,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    /**
     * Creates a failed scan result with errors.
     *
     * @param scannerId scanner ID
     * @param errors error messages
     * @return failed result
     */
    public static ScanResult failed(String scannerId, List<String> errors) {
        return new ScanResult(
            scannerId,
            false,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            errors
        );
    }

    /**
     * Returns true if this result has any findings.
     *
     * @return true if components, dependencies, APIs, etc. were found
     */
    public boolean hasFindings() {
        return !components.isEmpty()
            || !dependencies.isEmpty()
            || !apiEndpoints.isEmpty()
            || !messageFlows.isEmpty()
            || !dataEntities.isEmpty()
            || !relationships.isEmpty();
    }
}
