package com.docarchitect.core.model;

import java.util.Objects;

/**
 * Represents a gap or issue detected during scanning.
 *
 * <p>Quality gaps indicate areas where the scan may be incomplete or where
 * known limitations exist.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * QualityGap gap = new QualityGap(
 *     "kafka-consumer",
 *     "4 Kafka consumer files found but not parsed (Java 21 syntax)",
 *     GapSeverity.WARNING
 * );
 * }</pre>
 *
 * @param scannerId the ID of the scanner that detected the gap
 * @param message human-readable description of the gap
 * @param severity severity level of the gap
 */
public record QualityGap(
    String scannerId,
    String message,
    GapSeverity severity
) {
    /**
     * Compact constructor with validation.
     */
    public QualityGap {
        Objects.requireNonNull(scannerId, "scannerId must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
    }

    /**
     * Create an informational gap.
     *
     * @param scannerId the scanner ID
     * @param message the message
     * @return a new QualityGap with INFO severity
     */
    public static QualityGap info(String scannerId, String message) {
        return new QualityGap(scannerId, message, GapSeverity.INFO);
    }

    /**
     * Create a warning gap.
     *
     * @param scannerId the scanner ID
     * @param message the message
     * @return a new QualityGap with WARNING severity
     */
    public static QualityGap warning(String scannerId, String message) {
        return new QualityGap(scannerId, message, GapSeverity.WARNING);
    }

    /**
     * Create an error gap.
     *
     * @param scannerId the scanner ID
     * @param message the message
     * @return a new QualityGap with ERROR severity
     */
    public static QualityGap error(String scannerId, String message) {
        return new QualityGap(scannerId, message, GapSeverity.ERROR);
    }
}
