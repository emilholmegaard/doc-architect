package com.docarchitect.core.model;

import java.util.Objects;

/**
 * Metrics for a specific architecture component type during scanning.
 *
 * <p>Tracks expected vs actual files scanned for a particular component category
 * (e.g., REST APIs, Database Entities, Message Flows).</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ArchitectureComponentMetrics metrics = new ArchitectureComponentMetrics(
 *     "REST APIs",
 *     47,  // expected files
 *     45,  // scanned files
 *     96.0 // coverage percentage
 * );
 * }</pre>
 *
 * @param componentType the type of architecture component (e.g., "REST APIs", "JPA Entities")
 * @param expectedFiles estimated number of files for this component type
 * @param scannedFiles actual number of files scanned
 * @param coveragePercentage coverage as a percentage (0-100)
 */
public record ArchitectureComponentMetrics(
    String componentType,
    int expectedFiles,
    int scannedFiles,
    double coveragePercentage
) {
    /**
     * Compact constructor with validation.
     */
    public ArchitectureComponentMetrics {
        Objects.requireNonNull(componentType, "componentType must not be null");
        if (expectedFiles < 0) {
            throw new IllegalArgumentException("expectedFiles must be >= 0");
        }
        if (scannedFiles < 0) {
            throw new IllegalArgumentException("scannedFiles must be >= 0");
        }
        if (coveragePercentage < 0.0 || coveragePercentage > 100.0) {
            throw new IllegalArgumentException("coveragePercentage must be between 0 and 100");
        }
    }

    /**
     * Check if coverage is high (>= 90%).
     *
     * @return true if coverage is >= 90%
     */
    public boolean isHighCoverage() {
        return coveragePercentage >= 90.0;
    }

    /**
     * Check if coverage is low (&lt; 70%).
     *
     * @return true if coverage is &lt; 70%
     */
    public boolean isLowCoverage() {
        return coveragePercentage < 70.0;
    }

    /**
     * Get a formatted coverage string (e.g., "45/47 (96%)").
     *
     * @return formatted coverage string
     */
    public String getFormattedCoverage() {
        return String.format("%d/%d (%.0f%%)", scannedFiles, expectedFiles, coveragePercentage);
    }
}
