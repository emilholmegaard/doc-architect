package com.docarchitect.core.scanner;

/**
 * Confidence level for scan results, indicating parsing quality.
 *
 * <p>Used to distinguish between high-quality AST-based findings and
 * lower-confidence regex/heuristic-based findings.
 *
 * <p><b>Levels:</b></p>
 * <ul>
 *   <li><b>HIGH:</b> Extracted via full AST parsing with type resolution</li>
 *   <li><b>MEDIUM:</b> Extracted via regex patterns or partial AST</li>
 *   <li><b>LOW:</b> Extracted via heuristics or file metadata</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // AST-based extraction
 * ApiEndpoint endpoint = new ApiEndpoint(
 *     id, componentId, "/api/users", "GET", ...
 * );
 * // Mark as HIGH confidence
 * endpointsWithConfidence.put(endpoint, ConfidenceLevel.HIGH);
 *
 * // Regex fallback extraction
 * ApiEndpoint fallbackEndpoint = extractViaRegex(file);
 * // Mark as MEDIUM confidence
 * endpointsWithConfidence.put(fallbackEndpoint, ConfidenceLevel.MEDIUM);
 * }</pre>
 *
 * @since 1.0.0
 */
public enum ConfidenceLevel {
    /**
     * High confidence - extracted via full AST parsing.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>JavaParser AST with full compilation unit</li>
     *   <li>Roslyn (C#) semantic model analysis</li>
     *   <li>Python AST with type hints resolved</li>
     * </ul>
     */
    HIGH("AST-based", 1.0),

    /**
     * Medium confidence - extracted via regex or partial AST.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Regex patterns for annotations/decorators</li>
     *   <li>AST parsing without type resolution</li>
     *   <li>Partial file analysis (comments, imports)</li>
     * </ul>
     */
    MEDIUM("Regex-based", 0.7),

    /**
     * Low confidence - extracted via heuristics or file metadata.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Filename-based detection</li>
     *   <li>Directory structure conventions</li>
     *   <li>Comment-based tagging</li>
     * </ul>
     */
    LOW("Heuristic-based", 0.4);

    private final String description;
    private final double weight;

    ConfidenceLevel(String description, double weight) {
        this.description = description;
        this.weight = weight;
    }

    /**
     * Returns a human-readable description of this confidence level.
     *
     * @return description string
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the numeric weight of this confidence level (0.0 to 1.0).
     *
     * <p>Used for scoring and filtering results by minimum confidence.
     *
     * @return weight (1.0 = HIGH, 0.7 = MEDIUM, 0.4 = LOW)
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Returns true if this confidence level is at least the specified level.
     *
     * @param minimum minimum acceptable confidence level
     * @return true if this level >= minimum level
     */
    public boolean isAtLeast(ConfidenceLevel minimum) {
        return this.weight >= minimum.weight;
    }
}
