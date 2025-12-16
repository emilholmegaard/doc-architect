package com.docarchitect.core.scanner.impl;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.scanner.impl.dotnet.AspNetCoreApiScanner;
import com.docarchitect.core.scanner.impl.dotnet.EntityFrameworkScanner;
import com.docarchitect.core.scanner.impl.dotnet.NuGetDependencyScanner;
import com.docarchitect.core.scanner.impl.go.GoModScanner;
import com.docarchitect.core.scanner.impl.java.GradleDependencyScanner;
import com.docarchitect.core.scanner.impl.java.JpaEntityScanner;
import com.docarchitect.core.scanner.impl.java.KafkaScanner;
import com.docarchitect.core.scanner.impl.java.MavenDependencyScanner;
import com.docarchitect.core.scanner.impl.java.SpringRestApiScanner;
import com.docarchitect.core.scanner.impl.javascript.ExpressScanner;
import com.docarchitect.core.scanner.impl.javascript.NpmDependencyScanner;
import com.docarchitect.core.scanner.impl.python.DjangoOrmScanner;
import com.docarchitect.core.scanner.impl.python.FastAPIScanner;
import com.docarchitect.core.scanner.impl.python.FlaskScanner;
import com.docarchitect.core.scanner.impl.python.PipPoetryDependencyScanner;
import com.docarchitect.core.scanner.impl.python.SqlAlchemyScanner;
import com.docarchitect.core.scanner.impl.schema.AvroSchemaScanner;
import com.docarchitect.core.scanner.impl.schema.GraphQLScanner;
import com.docarchitect.core.scanner.impl.schema.SqlMigrationScanner;

/**
 * Metadata tests for all scanner implementations.
 * Verifies scanner configuration and SPI compliance.
 */
class ScannersMetadataTest {

    static Stream<Scanner> allScanners() {
        return Stream.of(
            // JVM scanners
            new MavenDependencyScanner(),
            new GradleDependencyScanner(),
            new SpringRestApiScanner(),
            new JpaEntityScanner(),
            new KafkaScanner(),
            // Python scanners
            new PipPoetryDependencyScanner(),
            new FastAPIScanner(),
            new FlaskScanner(),
            new SqlAlchemyScanner(),
            new DjangoOrmScanner(),
            // .NET scanners
            new NuGetDependencyScanner(),
            new AspNetCoreApiScanner(),
            new EntityFrameworkScanner(),
            // Phase 6 scanners
            new GraphQLScanner(),
            new AvroSchemaScanner(),
            new SqlMigrationScanner(),
            new NpmDependencyScanner(),
            new GoModScanner(),
            new ExpressScanner()
        );
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasValidId(Scanner scanner) {
        assertThat(scanner.getId())
            .isNotNull()
            .isNotBlank()
            .matches("[a-z0-9-]+");
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasValidDisplayName(Scanner scanner) {
        assertThat(scanner.getDisplayName())
            .isNotNull()
            .isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasValidLanguages(Scanner scanner) {
        assertThat(scanner.getSupportedLanguages())
            .isNotNull()
            .isNotEmpty()
            .allMatch(lang -> lang.matches("[a-z]+"));
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasValidFilePatterns(Scanner scanner) {
        assertThat(scanner.getSupportedFilePatterns())
            .isNotNull()
            .isNotEmpty()
            .allMatch(pattern ->
                pattern.contains("*") ||
                pattern.contains("/") ||
                pattern.matches("[a-z0-9._-]+\\.(mod|js|ts|json|xml|yml|yaml|toml|sql|graphql|avsc|avro|cs|py|go)")
            );
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasValidPriority(Scanner scanner) {
        assertThat(scanner.getPriority())
            .isGreaterThanOrEqualTo(0)
            .isLessThanOrEqualTo(100);
    }

    @ParameterizedTest
    @MethodSource("allScanners")
    void scanner_hasUniqueId(Scanner scanner) {
        long count = allScanners()
            .filter(s -> s.getId().equals(scanner.getId()))
            .count();

        assertThat(count)
            .as("Scanner ID '%s' should be unique", scanner.getId())
            .isEqualTo(1);
    }
}
