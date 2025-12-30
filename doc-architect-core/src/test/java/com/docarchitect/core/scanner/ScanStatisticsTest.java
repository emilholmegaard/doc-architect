package com.docarchitect.core.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScanStatistics}.
 */
class ScanStatisticsTest {

    @Test
    void empty_createsZeroStatistics() {
        ScanStatistics stats = ScanStatistics.empty();

        assertThat(stats.filesDiscovered()).isZero();
        assertThat(stats.filesScanned()).isZero();
        assertThat(stats.filesParsedSuccessfully()).isZero();
        assertThat(stats.filesParsedWithFallback()).isZero();
        assertThat(stats.filesFailed()).isZero();
        assertThat(stats.errorCounts()).isEmpty();
        assertThat(stats.topErrors()).isEmpty();
    }

    @Test
    void getSuccessRate_withNoFilesScanned_returnsZero() {
        ScanStatistics stats = new ScanStatistics(100, 0, 0, 0, 0, Map.of(), List.of());

        assertThat(stats.getSuccessRate()).isZero();
    }

    @Test
    void getSuccessRate_withAllFilesSuccessful_returns100() {
        ScanStatistics stats = new ScanStatistics(100, 100, 100, 0, 0, Map.of(), List.of());

        assertThat(stats.getSuccessRate()).isEqualTo(100.0);
    }

    @Test
    void getSuccessRate_withPartialSuccess_returnsCorrectPercentage() {
        ScanStatistics stats = new ScanStatistics(100, 100, 70, 0, 30, Map.of(), List.of());

        assertThat(stats.getSuccessRate()).isEqualTo(70.0);
    }

    @Test
    void getOverallParseRate_combinesSuccessAndFallback() {
        ScanStatistics stats = new ScanStatistics(100, 100, 70, 20, 10, Map.of(), List.of());

        assertThat(stats.getOverallParseRate()).isEqualTo(90.0);
    }

    @Test
    void getFailureRate_withNoFailures_returnsZero() {
        ScanStatistics stats = new ScanStatistics(100, 100, 90, 10, 0, Map.of(), List.of());

        assertThat(stats.getFailureRate()).isZero();
    }

    @Test
    void getFailureRate_withAllFailures_returns100() {
        ScanStatistics stats = new ScanStatistics(100, 100, 0, 0, 100, Map.of(), List.of());

        assertThat(stats.getFailureRate()).isEqualTo(100.0);
    }

    @Test
    void hasFailures_withFailures_returnsTrue() {
        ScanStatistics stats = new ScanStatistics(100, 100, 90, 0, 10, Map.of(), List.of());

        assertThat(stats.hasFailures()).isTrue();
    }

    @Test
    void hasFailures_withoutFailures_returnsFalse() {
        ScanStatistics stats = new ScanStatistics(100, 100, 100, 0, 0, Map.of(), List.of());

        assertThat(stats.hasFailures()).isFalse();
    }

    @Test
    void usedFallback_withFallback_returnsTrue() {
        ScanStatistics stats = new ScanStatistics(100, 100, 80, 20, 0, Map.of(), List.of());

        assertThat(stats.usedFallback()).isTrue();
    }

    @Test
    void usedFallback_withoutFallback_returnsFalse() {
        ScanStatistics stats = new ScanStatistics(100, 100, 100, 0, 0, Map.of(), List.of());

        assertThat(stats.usedFallback()).isFalse();
    }

    @Test
    void getSummary_formatsCorrectly() {
        ScanStatistics stats = new ScanStatistics(
            150,
            100,
            70,
            20,
            10,
            Map.of("AST parsing failure", 8, "File read error", 2),
            List.of("File1.java: Unsupported syntax", "File2.java: IOException")
        );

        String summary = stats.getSummary();

        assertThat(summary).contains("150"); // discovered
        assertThat(summary).contains("100"); // scanned
        assertThat(summary).contains("70");  // success
        assertThat(summary).containsPattern("70[.,]0%"); // success rate (locale-independent)
        assertThat(summary).contains("20");  // fallback
        assertThat(summary).contains("10");  // failed
        assertThat(summary).containsPattern("10[.,]0%"); // failure rate (locale-independent)
    }

    @Test
    void builder_incrementsCountsCorrectly() {
        ScanStatistics.Builder builder = new ScanStatistics.Builder();

        builder.filesDiscovered(150)
            .incrementFilesScanned()
            .incrementFilesScanned()
            .incrementFilesScanned()
            .incrementFilesParsedSuccessfully()
            .incrementFilesParsedSuccessfully()
            .incrementFilesParsedWithFallback()
            .incrementFilesFailed();

        ScanStatistics stats = builder.build();

        assertThat(stats.filesDiscovered()).isEqualTo(150);
        assertThat(stats.filesScanned()).isEqualTo(3);
        assertThat(stats.filesParsedSuccessfully()).isEqualTo(2);
        assertThat(stats.filesParsedWithFallback()).isEqualTo(1);
        assertThat(stats.filesFailed()).isEqualTo(1);
    }

    @Test
    void builder_tracksErrorsCorrectly() {
        ScanStatistics.Builder builder = new ScanStatistics.Builder();

        builder.addError("AST parsing failure", "File1.java: Unsupported Java 21 syntax")
            .addError("AST parsing failure", "File2.java: Lombok annotation issues")
            .addError("File read error", "File3.java: IOException")
            .addError("AST parsing failure", "File4.java: Complex generics");

        ScanStatistics stats = builder.build();

        assertThat(stats.errorCounts())
            .containsEntry("AST parsing failure", 3)
            .containsEntry("File read error", 1);

        assertThat(stats.topErrors())
            .hasSize(4)
            .contains("File1.java: Unsupported Java 21 syntax")
            .contains("File3.java: IOException");
    }

    @Test
    void builder_limitsTopErrorsTo10() {
        ScanStatistics.Builder builder = new ScanStatistics.Builder();

        // Add 15 errors
        for (int i = 1; i <= 15; i++) {
            builder.addError("Error type " + i, "Error detail " + i);
        }

        ScanStatistics stats = builder.build();

        // Only first 10 should be in topErrors
        assertThat(stats.topErrors()).hasSize(10);
        assertThat(stats.errorCounts()).hasSize(15); // All error types counted
    }

    @Test
    void compactConstructor_handlesNegativeValues() {
        ScanStatistics stats = new ScanStatistics(-10, -5, -3, -2, -1, null, null);

        assertThat(stats.filesDiscovered()).isZero();
        assertThat(stats.filesScanned()).isZero();
        assertThat(stats.filesParsedSuccessfully()).isZero();
        assertThat(stats.filesParsedWithFallback()).isZero();
        assertThat(stats.filesFailed()).isZero();
        assertThat(stats.errorCounts()).isEmpty();
        assertThat(stats.topErrors()).isEmpty();
    }

    @Test
    void compactConstructor_handlesNullCollections() {
        ScanStatistics stats = new ScanStatistics(100, 50, 40, 5, 5, null, null);

        assertThat(stats.errorCounts()).isNotNull().isEmpty();
        assertThat(stats.topErrors()).isNotNull().isEmpty();
    }

    @Test
    void realWorldScenario_apacheDruid() {
        // Simulating Apache Druid: 8,989 files discovered, only 71 parsed successfully
        ScanStatistics.Builder builder = new ScanStatistics.Builder();
        builder.filesDiscovered(8989);

        for (int i = 0; i < 71; i++) {
            builder.incrementFilesScanned().incrementFilesParsedSuccessfully();
        }
        for (int i = 0; i < 8918; i++) {
            builder.incrementFilesScanned().incrementFilesFailed();
            if (i < 10) {
                builder.addError("Pre-filter skip", "File" + i + ": Not matching naming convention");
            }
        }

        ScanStatistics stats = builder.build();

        assertThat(stats.filesDiscovered()).isEqualTo(8989);
        assertThat(stats.filesScanned()).isEqualTo(8989);
        assertThat(stats.filesParsedSuccessfully()).isEqualTo(71);
        assertThat(stats.getSuccessRate()).isLessThan(1.0); // Less than 1%
        assertThat(stats.hasFailures()).isTrue();
    }

    @Test
    void realWorldScenario_keycloakWithFallback() {
        // Simulating Keycloak: 7,279 files, 1,876 AST success, rest use fallback
        ScanStatistics.Builder builder = new ScanStatistics.Builder();
        builder.filesDiscovered(7279);

        for (int i = 0; i < 1876; i++) {
            builder.incrementFilesScanned().incrementFilesParsedSuccessfully();
        }
        for (int i = 0; i < 5000; i++) {
            builder.incrementFilesScanned().incrementFilesParsedWithFallback();
        }
        for (int i = 0; i < 403; i++) {
            builder.incrementFilesScanned().incrementFilesFailed();
            if (i < 5) {
                builder.addError("AST parsing failure", "Complex Lombok usage");
            }
        }

        ScanStatistics stats = builder.build();

        assertThat(stats.filesDiscovered()).isEqualTo(7279);
        assertThat(stats.getSuccessRate()).isCloseTo(25.8, org.assertj.core.data.Offset.offset(1.0));
        assertThat(stats.getOverallParseRate()).isCloseTo(94.5, org.assertj.core.data.Offset.offset(1.0));
        assertThat(stats.usedFallback()).isTrue();
    }
}
