package com.docarchitect.core.scanner;

import java.util.Arrays;

/**
 * Factory for common scanner applicability strategies.
 *
 * <p>Provides pre-built, reusable strategies for determining if a scanner
 * should run on a given project. Promotes consistency and reduces code
 * duplication across scanner implementations.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * // Spring REST API scanner
 * ScannerApplicabilityStrategy strategy =
 *     ApplicabilityStrategies.hasJavaFiles()
 *         .and(ApplicabilityStrategies.hasSpringFramework());
 *
 * // FastAPI scanner
 * ScannerApplicabilityStrategy strategy =
 *     ApplicabilityStrategies.hasPythonFiles()
 *         .and(ApplicabilityStrategies.hasFastAPI());
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ApplicabilityStrategies {

    private ApplicabilityStrategies() {
        // Utility class - prevent instantiation
    }

    // ===== Language Detection =====

    /**
     * Check if project contains Java files ({@code **\/*.java}).
     *
     * @return strategy that tests for Java files
     */
    public static ScannerApplicabilityStrategy hasJavaFiles() {
        return context -> hasAnyFiles(context, "**/*.java");
    }

    /**
     * Check if project contains Python files ({@code **\/*.py}).
     *
     * @return strategy that tests for Python files
     */
    public static ScannerApplicabilityStrategy hasPythonFiles() {
        return context -> hasAnyFiles(context, "**/*.py");
    }

    /**
     * Check if project contains C# files ({@code **\/*.cs}).
     *
     * @return strategy that tests for C# files
     */
    public static ScannerApplicabilityStrategy hasCSharpFiles() {
        return context -> hasAnyFiles(context, "**/*.cs");
    }

    /**
     * Check if project contains Go files ({@code **\/*.go}).
     *
     * @return strategy that tests for Go files
     */
    public static ScannerApplicabilityStrategy hasGoFiles() {
        return context -> hasAnyFiles(context, "**/*.go");
    }

    /**
     * Check if project contains Ruby files ({@code **\/*.rb}).
     *
     * @return strategy that tests for Ruby files
     */
    public static ScannerApplicabilityStrategy hasRubyFiles() {
        return context -> hasAnyFiles(context, "**/*.rb");
    }

    /**
     * Check if project contains JavaScript or TypeScript files.
     *
     * @return strategy that tests for {@code **\/*.js} or {@code **\/*.ts} files
     */
    public static ScannerApplicabilityStrategy hasJavaScriptFiles() {
        return context -> hasAnyFiles(context, "**/*.js", "**/*.ts");
    }

    // ===== Framework Detection (via dependencies) =====

    /**
     * Check if project has a dependency containing the specified name.
     *
     * <p>The check is case-insensitive and uses substring matching on
     * both groupId and artifactId.</p>
     *
     * @param dependencyName the dependency name to search for (e.g., "springframework", "fastapi")
     * @return strategy that tests for the dependency
     */
    public static ScannerApplicabilityStrategy hasDependency(String dependencyName) {
        return context -> context.previousResults().values().stream()
            .flatMap(result -> result.dependencies().stream())
            .anyMatch(dep -> {
                String lowerName = dependencyName.toLowerCase();
                String lowerGroup = dep.groupId().toLowerCase();
                String lowerArtifact = dep.artifactId().toLowerCase();
                return lowerGroup.contains(lowerName) || lowerArtifact.contains(lowerName);
            });
    }

    /**
     * Check if project has any of the specified dependencies.
     *
     * <p>The check is case-insensitive and uses substring matching on
     * both groupId and artifactId.</p>
     *
     * @param dependencyNames the dependency names to search for
     * @return strategy that tests if any dependency matches
     */
    public static ScannerApplicabilityStrategy hasAnyDependency(String... dependencyNames) {
        return context -> context.previousResults().values().stream()
            .flatMap(result -> result.dependencies().stream())
            .anyMatch(dep -> {
                String lowerGroup = dep.groupId().toLowerCase();
                String lowerArtifact = dep.artifactId().toLowerCase();
                return Arrays.stream(dependencyNames)
                    .anyMatch(depName -> {
                        String lowerName = depName.toLowerCase();
                        return lowerGroup.contains(lowerName) || lowerArtifact.contains(lowerName);
                    });
            });
    }

    // ===== Java Framework Checks =====

    /**
     * Check if project uses Spring Framework.
     *
     * @return strategy that tests for {@code org.springframework} dependency
     */
    public static ScannerApplicabilityStrategy hasSpringFramework() {
        return hasDependency("org.springframework");
    }

    /**
     * Check if project uses JAX-RS.
     *
     * @return strategy that tests for JAX-RS related dependencies
     */
    public static ScannerApplicabilityStrategy hasJaxRs() {
        return hasAnyDependency("javax.ws.rs", "jakarta.ws.rs", "jersey", "resteasy");
    }

    /**
     * Check if project uses JPA.
     *
     * @return strategy that tests for JPA/Hibernate related dependencies
     */
    public static ScannerApplicabilityStrategy hasJpa() {
        return hasAnyDependency("javax.persistence", "jakarta.persistence", "hibernate", "eclipselink");
    }

    /**
     * Check if project uses Apache Kafka.
     *
     * @return strategy that tests for {@code kafka} dependency
     */
    public static ScannerApplicabilityStrategy hasKafka() {
        return hasDependency("kafka");
    }

    /**
     * Check if project uses Kafka Streams.
     *
     * @return strategy that tests for {@code kafka-streams} dependency
     */
    public static ScannerApplicabilityStrategy hasKafkaStreams() {
        return hasDependency("kafka-streams");
    }

    /**
     * Check if project uses RabbitMQ.
     *
     * @return strategy that tests for RabbitMQ related dependencies
     */
    public static ScannerApplicabilityStrategy hasRabbitMQ() {
        return hasAnyDependency("rabbitmq", "spring-rabbit");
    }

    /**
     * Check if project uses MongoDB.
     *
     * @return strategy that tests for MongoDB related dependencies
     */
    public static ScannerApplicabilityStrategy hasMongoDB() {
        return hasAnyDependency("mongodb", "spring-data-mongodb");
    }

    // ===== Python Framework Checks =====

    /**
     * Check if project uses FastAPI.
     *
     * @return strategy that tests for {@code fastapi} dependency
     */
    public static ScannerApplicabilityStrategy hasFastAPI() {
        return hasDependency("fastapi");
    }

    /**
     * Check if project uses Flask.
     *
     * @return strategy that tests for {@code flask} dependency
     */
    public static ScannerApplicabilityStrategy hasFlask() {
        return hasDependency("flask");
    }

    /**
     * Check if project uses Celery.
     *
     * @return strategy that tests for {@code celery} dependency
     */
    public static ScannerApplicabilityStrategy hasCelery() {
        return hasDependency("celery");
    }

    /**
     * Check if project uses Faust (streaming).
     *
     * @return strategy that tests for {@code faust} dependency
     */
    public static ScannerApplicabilityStrategy hasFaust() {
        return hasDependency("faust");
    }

    /**
     * Check if project uses SQLAlchemy.
     *
     * @return strategy that tests for {@code sqlalchemy} dependency
     */
    public static ScannerApplicabilityStrategy hasSqlAlchemy() {
        return hasDependency("sqlalchemy");
    }

    // ===== .NET Framework Checks =====

    /**
     * Check if project uses ASP.NET Core.
     *
     * @return strategy that tests for {@code Microsoft.AspNetCore} dependency
     */
    public static ScannerApplicabilityStrategy hasAspNetCore() {
        return hasDependency("Microsoft.AspNetCore");
    }

    /**
     * Check if project uses Entity Framework.
     *
     * @return strategy that tests for Entity Framework related dependencies
     */
    public static ScannerApplicabilityStrategy hasEntityFramework() {
        return hasAnyDependency("Microsoft.EntityFrameworkCore", "EntityFramework");
    }

    // ===== File Pattern Checks =====

    /**
     * Check if project contains files matching any of the specified glob patterns.
     *
     * @param patterns glob patterns to match (e.g., pom.xml files, SQL files)
     * @return strategy that tests for matching files
     */
    public static ScannerApplicabilityStrategy hasFiles(String... patterns) {
        return context -> hasAnyFiles(context, patterns);
    }

    // ===== Composite Strategies =====

    /**
     * Strategy that always returns {@code true}.
     *
     * <p>Useful for scanners that should always run regardless of context.</p>
     *
     * @return strategy that always applies
     */
    public static ScannerApplicabilityStrategy alwaysApply() {
        return context -> true;
    }

    /**
     * Strategy that always returns {@code false}.
     *
     * <p>Useful for temporarily disabling scanners or testing.</p>
     *
     * @return strategy that never applies
     */
    public static ScannerApplicabilityStrategy neverApply() {
        return context -> false;
    }

    // ===== Helper Methods =====

    /**
     * Check if context contains files matching any of the specified patterns.
     *
     * @param context the scan context
     * @param patterns glob patterns to match
     * @return {@code true} if any files match, {@code false} otherwise
     */
    private static boolean hasAnyFiles(ScanContext context, String... patterns) {
        return Arrays.stream(patterns)
            .anyMatch(pattern -> context.findFiles(pattern).findAny().isPresent());
    }
}
