package com.docarchitect.core.model;

import com.docarchitect.core.scanner.ConfidenceLevel;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Overall quality report for a scanning session.
 *
 * <p>Provides metrics about scan completeness, coverage, confidence levels,
 * and identified gaps.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ScanQualityReport report = new ScanQualityReport(
 *     1500,  // total files in project
 *     1234,  // files analyzed
 *     266,   // files skipped
 *     Map.of("REST APIs", componentMetrics, ...),
 *     Map.of(ConfidenceLevel.HIGH, 850, ...),
 *     List.of(gap1, gap2)
 * );
 * }</pre>
 *
 * @param totalFilesInProject total files discovered in the project
 * @param filesAnalyzed total files that were analyzed by scanners
 * @param filesSkipped files that were skipped or failed
 * @param coverageByComponent coverage metrics per component type
 * @param findingsByConfidence number of findings by confidence level
 * @param gaps list of quality gaps detected
 */
public record ScanQualityReport(
    int totalFilesInProject,
    int filesAnalyzed,
    int filesSkipped,
    Map<String, ArchitectureComponentMetrics> coverageByComponent,
    Map<ConfidenceLevel, Integer> findingsByConfidence,
    List<QualityGap> gaps
) {
    /**
     * Compact constructor with validation.
     */
    public ScanQualityReport {
        if (totalFilesInProject < 0) {
            throw new IllegalArgumentException("totalFilesInProject must be >= 0");
        }
        if (filesAnalyzed < 0) {
            throw new IllegalArgumentException("filesAnalyzed must be >= 0");
        }
        if (filesSkipped < 0) {
            throw new IllegalArgumentException("filesSkipped must be >= 0");
        }
        Objects.requireNonNull(coverageByComponent, "coverageByComponent must not be null");
        Objects.requireNonNull(findingsByConfidence, "findingsByConfidence must not be null");
        Objects.requireNonNull(gaps, "gaps must not be null");

        // Make collections immutable
        coverageByComponent = Map.copyOf(coverageByComponent);
        findingsByConfidence = Map.copyOf(findingsByConfidence);
        gaps = List.copyOf(gaps);
    }

    /**
     * Calculate overall coverage percentage.
     *
     * @return coverage percentage (0-100)
     */
    public double getCoveragePercentage() {
        if (totalFilesInProject == 0) {
            return 0.0;
        }
        return (double) filesAnalyzed / totalFilesInProject * 100.0;
    }

    /**
     * Get total number of findings across all confidence levels.
     *
     * @return total findings
     */
    public int getTotalFindings() {
        return findingsByConfidence.values().stream()
            .mapToInt(Integer::intValue)
            .sum();
    }

    /**
     * Get number of high-confidence findings.
     *
     * @return high confidence findings count
     */
    public int getHighConfidenceFindings() {
        return findingsByConfidence.getOrDefault(ConfidenceLevel.HIGH, 0);
    }

    /**
     * Get number of medium-confidence findings.
     *
     * @return medium confidence findings count
     */
    public int getMediumConfidenceFindings() {
        return findingsByConfidence.getOrDefault(ConfidenceLevel.MEDIUM, 0);
    }

    /**
     * Get number of low-confidence findings.
     *
     * @return low confidence findings count
     */
    public int getLowConfidenceFindings() {
        return findingsByConfidence.getOrDefault(ConfidenceLevel.LOW, 0);
    }

    /**
     * Check if there are any quality gaps.
     *
     * @return true if gaps exist
     */
    public boolean hasGaps() {
        return !gaps.isEmpty();
    }

    /**
     * Get gaps by severity level.
     *
     * @param severity the severity level
     * @return list of gaps with the specified severity
     */
    public List<QualityGap> getGapsBySeverity(GapSeverity severity) {
        return gaps.stream()
            .filter(gap -> gap.severity() == severity)
            .toList();
    }
}
