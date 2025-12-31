package com.docarchitect.core.scanner;

import java.util.Set;

/**
 * Interface for code scanners that extract architecture information from source code.
 *
 * <p>Scanners are discovered via Java Service Provider Interface (SPI). Each scanner
 * analyzes specific aspects of a codebase (dependencies, APIs, database entities, etc.)
 * and produces a {@link ScanResult} that feeds into the intermediate {@link com.docarchitect.core.model.ArchitectureModel}.
 *
 * <p>Scanners are executed in priority order (lower numbers first) and can access
 * results from previously executed scanners via {@link ScanContext#previousResults()}.
 *
 * <p><b>Registration:</b> Register implementations in
 * {@code META-INF/services/com.docarchitect.core.scanner.Scanner}
 *
 * @see ScanContext
 * @see ScanResult
 */
public interface Scanner {

    /**
     * Returns unique identifier for this scanner.
     *
     * <p>Used for referencing scanner results and configuration. Should be kebab-case
     * (e.g., "maven-dependencies", "spring-rest-api").
     *
     * @return unique scanner identifier
     */
    String getId();

    /**
     * Returns human-readable display name for this scanner.
     *
     * <p>Used in CLI output and logs (e.g., "Maven Dependency Scanner").
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Returns set of programming languages this scanner supports.
     *
     * <p>Used for filtering scanners based on detected project languages.
     * Common values: "java", "kotlin", "python", "csharp", "go", "javascript", "typescript".
     *
     * @return supported language identifiers
     */
    Set<String> getSupportedLanguages();

    /**
     * Returns glob patterns for files this scanner analyzes.
     *
     * <p>Examples: pom.xml files, Java files, Python files.
     *
     * @return glob patterns
     */
    Set<String> getSupportedFilePatterns();

    /**
     * Returns execution priority for this scanner.
     *
     * <p>Lower values execute first. Recommended ranges:
     * <ul>
     *   <li>1-50: Dependency scanners (need to run before other scanners)</li>
     *   <li>50-100: API and messaging scanners</li>
     *   <li>100+: Integration and documentation scanners</li>
     * </ul>
     *
     * @return priority value (lower = earlier execution)
     */
    int getPriority();

    /**
     * Get the applicability strategy for this scanner.
     *
     * <p>The strategy determines whether this scanner should run on a given project.
     * Scanners should override this method to provide a declarative strategy
     * using {@link ApplicabilityStrategies}.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * @Override
     * public ScannerApplicabilityStrategy getApplicabilityStrategy() {
     *     return ApplicabilityStrategies.hasJavaFiles()
     *         .and(ApplicabilityStrategies.hasSpringFramework());
     * }
     * }</pre>
     *
     * <p>The default implementation returns {@code null}, which means the scanner
     * relies on its own {@link #appliesTo(ScanContext)} implementation.</p>
     *
     * @return the applicability strategy, or {@code null} to use appliesTo() instead
     * @see ApplicabilityStrategies
     * @since 1.0.0
     */
    default ScannerApplicabilityStrategy getApplicabilityStrategy() {
        // Default: no strategy, use appliesTo() implementation
        return null;
    }

    /**
     * Checks if this scanner should run for the given context.
     *
     * <p>This method is called before {@link #scan(ScanContext)} to determine if the
     * scanner is applicable. Use this to check for required files or previous scan results.
     *
     * <p><b>Note:</b> New scanners should override {@link #getApplicabilityStrategy()}
     * instead of this method for better composability and reusability.</p>
     *
     * @param context scan context containing project information
     * @return true if scanner should execute, false otherwise
     */
    boolean appliesTo(ScanContext context);

    /**
     * Executes the scan and returns results.
     *
     * <p>This method should:
     * <ol>
     *   <li>Find relevant files using {@link ScanContext#findFiles(String)}</li>
     *   <li>Parse files and extract architecture information</li>
     *   <li>Create domain objects ({@link com.docarchitect.core.model.Component},
     *       {@link com.docarchitect.core.model.Dependency}, etc.)</li>
     *   <li>Return a {@link ScanResult} with findings</li>
     * </ol>
     *
     * <p>If scanning fails, return a {@link ScanResult} with {@code success=false}
     * and populate the {@code errors} list. Do not throw exceptions unless the
     * error is unrecoverable.
     *
     * @param context scan context with access to files and previous results
     * @return scan result containing extracted architecture information
     */
    ScanResult scan(ScanContext context);
}
