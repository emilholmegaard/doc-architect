package com.docarchitect.core.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test validating SPI registration for Scanner implementations.
 *
 * <p>This test ensures that:
 * <ul>
 *   <li>All scanners registered in META-INF/services can be discovered via ServiceLoader</li>
 *   <li>ServiceLoader can successfully instantiate all registered scanners</li>
 *   <li>All scanner IDs are unique (no duplicates)</li>
 *   <li>Expected scanner count matches registered implementations</li>
 * </ul>
 *
 * <p><b>Critical Quality Gate:</b> This test prevents runtime failures due to:
 * <ul>
 *   <li>Typos in SPI registration file</li>
 *   <li>Missing scanner classes</li>
 *   <li>Constructor failures during instantiation</li>
 *   <li>Duplicate scanner IDs</li>
 * </ul>
 *
 * <p>Expected scanner count: 24 scanners
 * <ul>
 *   <li>7 Java/JVM scanners (Maven, Gradle, Spring, JAX-RS, JPA, Kafka, RabbitMQ)</li>
 *   <li>5 Python scanners (Pip/Poetry, FastAPI, Flask, SQLAlchemy, Django)</li>
 *   <li>3 .NET scanners (NuGet, ASP.NET Core, Entity Framework)</li>
 *   <li>2 Ruby scanners (Bundler, Rails API)</li>
 *   <li>7 Additional scanners (GraphQL, Avro, Protobuf, SQL, npm, Go, Express)</li>
 * </ul>
 *
 * @see Scanner
 * @see ServiceLoader
 * @since 1.0.0
 */
class ScannerServiceLoaderTest {

    /**
     * Expected number of scanner implementations.
     * Update this constant when adding new scanners.
     */
    private static final int EXPECTED_SCANNER_COUNT = 24;

    @Test
    void serviceLoader_discoversAllRegisteredScanners() {
        ServiceLoader<Scanner> serviceLoader = ServiceLoader.load(Scanner.class);
        List<Scanner> scanners = serviceLoader.stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        assertThat(scanners)
            .as("ServiceLoader should discover all %d registered scanners", EXPECTED_SCANNER_COUNT)
            .hasSize(EXPECTED_SCANNER_COUNT)
            .allMatch(scanner -> scanner != null, "All scanners should be non-null")
            .allMatch(scanner -> scanner.getId() != null, "All scanners should have non-null ID");
    }

    @Test
    void serviceLoader_scannersHaveUniqueIds() {
        ServiceLoader<Scanner> serviceLoader = ServiceLoader.load(Scanner.class);
        List<Scanner> scanners = serviceLoader.stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        Set<String> scannerIds = scanners.stream()
            .map(Scanner::getId)
            .collect(Collectors.toSet());

        assertThat(scannerIds)
            .as("All scanner IDs should be unique (no duplicates)")
            .hasSize(scanners.size());
    }

    @Test
    void serviceLoader_canInstantiateAllScanners() {
        ServiceLoader<Scanner> serviceLoader = ServiceLoader.load(Scanner.class);

        // This test passes if no exceptions are thrown during instantiation
        List<Scanner> scanners = serviceLoader.stream()
            .map(provider -> {
                Scanner scanner = provider.get();
                // Verify basic metadata is accessible without errors
                scanner.getId();
                scanner.getDisplayName();
                scanner.getSupportedLanguages();
                scanner.getSupportedFilePatterns();
                scanner.getPriority();
                return scanner;
            })
            .toList();

        assertThat(scanners)
            .hasSize(EXPECTED_SCANNER_COUNT);
    }

    @Test
    void serviceLoader_discoversExpectedScannerTypes() {
        ServiceLoader<Scanner> serviceLoader = ServiceLoader.load(Scanner.class);
        List<Scanner> scanners = serviceLoader.stream()
            .map(ServiceLoader.Provider::get)
            .toList();

        List<String> scannerIds = scanners.stream()
            .map(Scanner::getId)
            .sorted()
            .toList();

        // Verify key scanners are present
        assertThat(scannerIds)
            .as("Expected scanner types should be discovered")
            .contains(
                "maven-dependencies",
                "gradle-dependencies",
                "spring-rest-api",
                "jaxrs-api",
                "jpa-entities",
                "kafka-messaging",
                "rabbitmq-messaging",
                "pip-poetry-dependencies",
                "fastapi-rest",
                "flask-rest",
                "sqlalchemy-entities",
                "django-orm",
                "nuget-dependencies",
                "aspnetcore-rest",
                "entity-framework",
                "bundler-dependencies",
                "graphql-schema",
                "avro-schema",
                "protobuf-schema",
                "sql-migration",
                "npm-dependencies",
                "go-modules",
                "express-api"
            );
    }
}
