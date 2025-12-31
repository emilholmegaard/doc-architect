package com.docarchitect.core.scanner;

/**
 * Strategy for determining if a scanner should run on a given project.
 *
 * <p>Implementations define the criteria for scanner applicability, such as
 * presence of specific files, languages, or framework dependencies.</p>
 *
 * <p>This interface supports composition via {@link #and(ScannerApplicabilityStrategy)}
 * and {@link #or(ScannerApplicabilityStrategy)} for building complex applicability rules.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * ScannerApplicabilityStrategy strategy =
 *     ApplicabilityStrategies.hasJavaFiles()
 *         .and(ApplicabilityStrategies.hasSpringFramework());
 *
 * if (strategy.test(context)) {
 *     // Scanner applies to this project
 * }
 * }</pre>
 *
 * @see ApplicabilityStrategies
 * @since 1.0.0
 */
@FunctionalInterface
public interface ScannerApplicabilityStrategy {

    /**
     * Check if the scanner should apply to the given context.
     *
     * @param context the scan context containing project information
     * @return {@code true} if scanner should run, {@code false} otherwise
     */
    boolean test(ScanContext context);

    /**
     * Combine this strategy with another using AND logic.
     *
     * <p>The resulting strategy returns {@code true} only if both strategies
     * return {@code true}.</p>
     *
     * @param other the other strategy to combine with
     * @return a new strategy that is the logical AND of this and the other strategy
     */
    default ScannerApplicabilityStrategy and(ScannerApplicabilityStrategy other) {
        return context -> this.test(context) && other.test(context);
    }

    /**
     * Combine this strategy with another using OR logic.
     *
     * <p>The resulting strategy returns {@code true} if either strategy
     * returns {@code true}.</p>
     *
     * @param other the other strategy to combine with
     * @return a new strategy that is the logical OR of this and the other strategy
     */
    default ScannerApplicabilityStrategy or(ScannerApplicabilityStrategy other) {
        return context -> this.test(context) || other.test(context);
    }

    /**
     * Negate this strategy.
     *
     * <p>The resulting strategy returns {@code true} if this strategy returns
     * {@code false}, and vice versa.</p>
     *
     * @return a new strategy that is the logical negation of this strategy
     */
    default ScannerApplicabilityStrategy negate() {
        return context -> !this.test(context);
    }
}
