package com.docarchitect.core.scanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Statistics collected during a scan operation.
 *
 * <p>Provides transparency into scanner performance and parsing success rates.
 * Used for diagnostics, quality metrics, and troubleshooting failed scans.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ScanStatistics stats = new ScanStatistics(
 *     100,  // files discovered
 *     85,   // files scanned
 *     70,   // successfully parsed with AST
 *     15,   // parsed with fallback regex
 *     15,   // failed to parse
 *     Map.of(
 *         "Unsupported Java 21 syntax", 8,
 *         "Lombok annotation issues", 5,
 *         "Complex generics", 2
 *     ),
 *     List.of(
 *         "UserController.java: Unsupported Java 21 record patterns",
 *         "OrderService.java: Lombok @Builder not resolved"
 *     )
 * );
 * }</pre>
 *
 * @param filesDiscovered total files matching scanner's glob patterns
 * @param filesScanned files actually examined (after pre-filtering)
 * @param filesParsedSuccessfully files parsed via primary method (e.g., AST)
 * @param filesParsedWithFallback files parsed via fallback method (e.g., regex)
 * @param filesFailed files that could not be parsed at all
 * @param errorCounts map of error types to their occurrence counts
 * @param topErrors list of most significant error messages (max 10)
 *
 * @since 1.0.0
 */
public record ScanStatistics(
    int filesDiscovered,
    int filesScanned,
    int filesParsedSuccessfully,
    int filesParsedWithFallback,
    int filesFailed,
    Map<String, Integer> errorCounts,
    List<String> topErrors
) {
    /**
     * Compact constructor with validation and defaults.
     */
    public ScanStatistics {
        if (filesDiscovered < 0) {
            filesDiscovered = 0;
        }
        if (filesScanned < 0) {
            filesScanned = 0;
        }
        if (filesParsedSuccessfully < 0) {
            filesParsedSuccessfully = 0;
        }
        if (filesParsedWithFallback < 0) {
            filesParsedWithFallback = 0;
        }
        if (filesFailed < 0) {
            filesFailed = 0;
        }
        if (errorCounts == null) {
            errorCounts = Map.of();
        }
        if (topErrors == null) {
            topErrors = List.of();
        }
    }

    /**
     * Creates an empty statistics instance (no files processed).
     *
     * @return empty statistics
     */
    public static ScanStatistics empty() {
        return new ScanStatistics(0, 0, 0, 0, 0, Map.of(), List.of());
    }

    /**
     * Calculates the success rate of primary parsing (AST).
     *
     * @return success rate as percentage (0.0 to 100.0), or 0 if no files scanned
     */
    public double getSuccessRate() {
        if (filesScanned == 0) {
            return 0.0;
        }
        return (filesParsedSuccessfully * 100.0) / filesScanned;
    }

    /**
     * Calculates the overall parse rate (primary + fallback).
     *
     * @return overall parse rate as percentage (0.0 to 100.0), or 0 if no files scanned
     */
    public double getOverallParseRate() {
        if (filesScanned == 0) {
            return 0.0;
        }
        return ((filesParsedSuccessfully + filesParsedWithFallback) * 100.0) / filesScanned;
    }

    /**
     * Calculates the failure rate.
     *
     * @return failure rate as percentage (0.0 to 100.0), or 0 if no files scanned
     */
    public double getFailureRate() {
        if (filesScanned == 0) {
            return 0.0;
        }
        return (filesFailed * 100.0) / filesScanned;
    }

    /**
     * Returns true if this scan had any failures.
     *
     * @return true if at least one file failed to parse
     */
    public boolean hasFailures() {
        return filesFailed > 0;
    }

    /**
     * Returns true if fallback parsing was used.
     *
     * @return true if at least one file was parsed via fallback
     */
    public boolean usedFallback() {
        return filesParsedWithFallback > 0;
    }

    /**
     * Returns a human-readable summary of the statistics.
     *
     * @return summary string
     */
    public String getSummary() {
        return String.format(
            "Discovered: %d, Scanned: %d, Success: %d (%.1f%%), Fallback: %d, Failed: %d (%.1f%%)",
            filesDiscovered,
            filesScanned,
            filesParsedSuccessfully,
            getSuccessRate(),
            filesParsedWithFallback,
            filesFailed,
            getFailureRate()
        );
    }

    /**
     * Builder for constructing ScanStatistics incrementally.
     */
    public static class Builder {
        private int filesDiscovered = 0;
        private int filesScanned = 0;
        private int filesParsedSuccessfully = 0;
        private int filesParsedWithFallback = 0;
        private int filesFailed = 0;
        private final Map<String, Integer> errorCounts = new java.util.HashMap<>();
        private final List<String> topErrors = new java.util.ArrayList<>();

        public Builder filesDiscovered(int count) {
            this.filesDiscovered = count;
            return this;
        }

        public Builder incrementFilesScanned() {
            this.filesScanned++;
            return this;
        }

        public Builder incrementFilesParsedSuccessfully() {
            this.filesParsedSuccessfully++;
            return this;
        }

        public Builder incrementFilesParsedWithFallback() {
            this.filesParsedWithFallback++;
            return this;
        }

        public Builder incrementFilesFailed() {
            this.filesFailed++;
            return this;
        }

        public Builder addError(String errorType, String errorDetail) {
            errorCounts.merge(errorType, 1, Integer::sum);
            if (topErrors.size() < 10) {
                topErrors.add(errorDetail);
            }
            return this;
        }

        public ScanStatistics build() {
            return new ScanStatistics(
                filesDiscovered,
                filesScanned,
                filesParsedSuccessfully,
                filesParsedWithFallback,
                filesFailed,
                Map.copyOf(errorCounts),
                List.copyOf(topErrors)
            );
        }
    }
}
