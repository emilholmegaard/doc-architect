package com.docarchitect.core.util;

import com.docarchitect.core.model.ArchitectureComponentMetrics;
import com.docarchitect.core.model.QualityGap;
import com.docarchitect.core.model.ScanQualityReport;
import com.docarchitect.core.scanner.ConfidenceLevel;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;

import java.util.*;

/**
 * Utility for calculating quality metrics from scan results.
 *
 * <p>Provides methods to compute coverage statistics, confidence distributions,
 * and quality gaps for architecture scanning.</p>
 *
 * @since 1.0.0
 */
public final class QualityMetricsCalculator {

    private QualityMetricsCalculator() {
        // Utility class
    }

    /**
     * Calculate overall scan quality report from scan results.
     *
     * @param scanResults map of scanner ID to scan result
     * @param context scan context
     * @return quality report
     */
    public static ScanQualityReport calculateQualityReport(
        Map<String, ScanResult> scanResults,
        ScanContext context
    ) {
        // Calculate file statistics
        int totalFilesInProject = estimateTotalFiles(context);
        int filesAnalyzed = calculateFilesAnalyzed(scanResults);
        // Ensure filesSkipped is non-negative (analyzedfiles can exceed total due to scanner overlap)
        int filesSkipped = Math.max(0, totalFilesInProject - filesAnalyzed);

        // Calculate coverage by component
        Map<String, ArchitectureComponentMetrics> coverageByComponent =
            calculateComponentCoverage(scanResults, context);

        // Calculate findings by confidence level
        Map<ConfidenceLevel, Integer> findingsByConfidence =
            calculateFindingsByConfidence(scanResults);

        // Detect quality gaps
        List<QualityGap> gaps = detectQualityGaps(scanResults);

        return new ScanQualityReport(
            totalFilesInProject,
            filesAnalyzed,
            filesSkipped,
            coverageByComponent,
            findingsByConfidence,
            gaps
        );
    }

    /**
     * Estimate total number of files in project based on context.
     *
     * <p>This is a heuristic estimate using glob patterns for common file types.</p>
     *
     * @param context scan context
     * @return estimated total file count
     */
    private static int estimateTotalFiles(ScanContext context) {
        Set<String> uniqueFiles = new HashSet<>();

        // Common file patterns
        String[] patterns = {
            "**/*.java", "**/*.py", "**/*.cs", "**/*.go", "**/*.rb",
            "**/*.js", "**/*.ts", "**/*.xml", "**/*.json", "**/*.yaml",
            "**/*.sql", "**/*.graphql", "**/*.proto"
        };

        for (String pattern : patterns) {
            context.findFiles(pattern).forEach(path -> uniqueFiles.add(path.toString()));
        }

        return uniqueFiles.size();
    }

    /**
     * Calculate total number of files analyzed across all scanners.
     *
     * @param scanResults scan results
     * @return total files analyzed
     */
    private static int calculateFilesAnalyzed(Map<String, ScanResult> scanResults) {
        return scanResults.values().stream()
            .filter(ScanResult::success)
            .map(ScanResult::statistics)
            .filter(Objects::nonNull)
            .mapToInt(stats -> stats.filesScanned())
            .sum();
    }

    /**
     * Calculate coverage metrics for each architecture component type.
     *
     * @param scanResults scan results
     * @param context scan context
     * @return coverage by component type
     */
    private static Map<String, ArchitectureComponentMetrics> calculateComponentCoverage(
        Map<String, ScanResult> scanResults,
        ScanContext context
    ) {
        Map<String, ArchitectureComponentMetrics> coverage = new LinkedHashMap<>();

        // REST APIs coverage
        calculateComponentMetrics(coverage, "REST APIs",
            estimateRestApiFiles(context),
            countRestApiFindings(scanResults));

        // Database Entities coverage
        calculateComponentMetrics(coverage, "Database Entities",
            estimateDatabaseEntityFiles(context),
            countDatabaseEntityFindings(scanResults));

        // Message Flows coverage
        calculateComponentMetrics(coverage, "Message Flows",
            estimateMessageFlowFiles(context),
            countMessageFlowFindings(scanResults));

        // Dependencies coverage
        calculateComponentMetrics(coverage, "Dependencies",
            estimateDependencyFiles(context),
            countDependencyFindings(scanResults));

        return coverage;
    }

    /**
     * Add component metrics if there are expected or scanned files.
     */
    private static void calculateComponentMetrics(
        Map<String, ArchitectureComponentMetrics> coverage,
        String componentType,
        int expectedFiles,
        int scannedFiles
    ) {
        if (expectedFiles > 0 || scannedFiles > 0) {
            double percentage = expectedFiles > 0
                ? Math.min((double) scannedFiles / expectedFiles * 100.0, 100.0)
                : (scannedFiles > 0 ? 100.0 : 0.0);
            coverage.put(componentType, new ArchitectureComponentMetrics(
                componentType, expectedFiles, scannedFiles, percentage
            ));
        }
    }

    /**
     * Estimate number of REST API files based on patterns.
     */
    private static int estimateRestApiFiles(ScanContext context) {
        long javaControllers = context.findFiles("**/*Controller.java").count();
        long pythonRoutes = context.findFiles("**/routes.py").count();
        long pythonViews = context.findFiles("**/views.py").count();
        long pythonApi = context.findFiles("**/api.py").count();
        long dotnetControllers = context.findFiles("**/*Controller.cs").count();
        long goHandlers = context.findFiles("**/handler*.go").count();
        long goRouters = context.findFiles("**/router*.go").count();
        return (int) (javaControllers + pythonRoutes + pythonViews + pythonApi + dotnetControllers + goHandlers + goRouters);
    }

    /**
     * Count REST API findings from scan results.
     */
    private static int countRestApiFindings(Map<String, ScanResult> scanResults) {
        return scanResults.values().stream()
            .mapToInt(result -> result.apiEndpoints().size())
            .sum();
    }

    /**
     * Estimate number of database entity files.
     */
    private static int estimateDatabaseEntityFiles(ScanContext context) {
        long javaEntityDir = context.findFiles("**/entity/**/*.java").count();
        long javaModelDir = context.findFiles("**/model/**/*.java").count();
        long pythonModels = context.findFiles("**/models.py").count();
        long dotnetEntityDir = context.findFiles("**/Entities/**/*.cs").count();
        long dotnetModelDir = context.findFiles("**/Models/**/*.cs").count();
        return (int) (javaEntityDir + javaModelDir + pythonModels + dotnetEntityDir + dotnetModelDir);
    }

    /**
     * Count database entity findings from scan results.
     */
    private static int countDatabaseEntityFindings(Map<String, ScanResult> scanResults) {
        return scanResults.values().stream()
            .mapToInt(result -> result.dataEntities().size())
            .sum();
    }

    /**
     * Estimate number of message flow files.
     */
    private static int estimateMessageFlowFiles(ScanContext context) {
        long javaConsumers = context.findFiles("**/*Consumer*.java").count();
        long javaProducers = context.findFiles("**/*Producer*.java").count();
        long pythonConsumers = context.findFiles("**/*consumer*.py").count();
        long pythonProducers = context.findFiles("**/*producer*.py").count();
        return (int) (javaConsumers + javaProducers + pythonConsumers + pythonProducers);
    }

    /**
     * Count message flow findings from scan results.
     */
    private static int countMessageFlowFindings(Map<String, ScanResult> scanResults) {
        return scanResults.values().stream()
            .mapToInt(result -> result.messageFlows().size())
            .sum();
    }

    /**
     * Estimate number of dependency files.
     */
    private static int estimateDependencyFiles(ScanContext context) {
        long pomFiles = context.findFiles("**/pom.xml").count();
        long gradleFiles = context.findFiles("**/build.gradle*").count();
        long npmFiles = context.findFiles("**/package.json").count();
        long requirementsFiles = context.findFiles("**/requirements.txt").count();
        long pyprojectFiles = context.findFiles("**/pyproject.toml").count();
        long csprojFiles = context.findFiles("**/*.csproj").count();
        long goModFiles = context.findFiles("**/go.mod").count();
        long gemFiles = context.findFiles("**/Gemfile").count();
        return (int) (pomFiles + gradleFiles + npmFiles + requirementsFiles + pyprojectFiles + csprojFiles + goModFiles + gemFiles);
    }

    /**
     * Count dependency findings from scan results.
     */
    private static int countDependencyFindings(Map<String, ScanResult> scanResults) {
        return scanResults.values().stream()
            .mapToInt(result -> result.dependencies().size())
            .sum();
    }

    /**
     * Calculate distribution of findings by confidence level.
     *
     * @param scanResults scan results
     * @return findings count by confidence level
     */
    private static Map<ConfidenceLevel, Integer> calculateFindingsByConfidence(
        Map<String, ScanResult> scanResults
    ) {
        Map<ConfidenceLevel, Integer> distribution = new EnumMap<>(ConfidenceLevel.class);

        // Initialize all levels to 0
        for (ConfidenceLevel level : ConfidenceLevel.values()) {
            distribution.put(level, 0);
        }

        // For now, assume all findings are HIGH confidence (AST-based)
        // In future, scanners should report confidence per finding
        int totalFindings = scanResults.values().stream()
            .mapToInt(result ->
                result.components().size() +
                result.apiEndpoints().size() +
                result.dataEntities().size() +
                result.messageFlows().size()
            )
            .sum();

        distribution.put(ConfidenceLevel.HIGH, totalFindings);

        return distribution;
    }

    /**
     * Detect quality gaps in scan results.
     *
     * @param scanResults scan results
     * @return list of detected gaps
     */
    private static List<QualityGap> detectQualityGaps(Map<String, ScanResult> scanResults) {
        List<QualityGap> gaps = new ArrayList<>();

        for (Map.Entry<String, ScanResult> entry : scanResults.entrySet()) {
            String scannerId = entry.getKey();
            ScanResult result = entry.getValue();

            // Check for failures
            if (!result.success() && !result.errors().isEmpty()) {
                gaps.add(QualityGap.error(scannerId,
                    "Scanner failed: " + result.errors().get(0)));
            }

            // Check for warnings
            if (!result.warnings().isEmpty()) {
                gaps.add(QualityGap.warning(scannerId,
                    result.warnings().get(0)));
            }

            // Check for low parse rates
            if (result.statistics() != null && result.statistics().hasFailures()) {
                double failureRate = result.statistics().getFailureRate();
                if (failureRate > 20.0) {
                    gaps.add(QualityGap.warning(scannerId,
                        String.format("High failure rate: %.1f%% of files failed to parse", failureRate)));
                }
            }
        }

        return gaps;
    }
}
