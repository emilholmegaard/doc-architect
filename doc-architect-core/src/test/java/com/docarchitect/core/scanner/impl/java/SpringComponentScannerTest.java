package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link SpringComponentScanner}.
 *
 * <p>Tests the scanner's ability to extract Spring Framework components
 * from Java source files based on stereotype annotations.
 */
class SpringComponentScannerTest extends ScannerTestBase {

    private final SpringComponentScanner scanner = new SpringComponentScanner();

    @Test
    void scan_withServiceAnnotation_extractsServiceComponent() throws IOException {
        // Given: A Java class with @Service annotation
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            import org.springframework.stereotype.Service;

            @Service
            public class UserService {
                public void createUser() {
                    // Business logic
                }
            }
            """);

        // Create context with previous Spring dependency scan result
        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should extract service component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("UserService");
        assertThat(component.type()).isEqualTo(ComponentType.SERVICE);
        assertThat(component.technology()).isEqualTo("Spring Framework");
        assertThat(component.metadata().get("fullyQualifiedName")).isEqualTo("com.example.UserService");
        assertThat(component.metadata().get("package")).isEqualTo("com.example");
    }

    @Test
    void scan_withRestControllerAnnotation_extractsServiceComponent() throws IOException {
        // Given: A Java class with @RestController annotation
        createFile("src/main/java/com/example/UserController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.RestController;

            @RestController
            public class UserController {
                // REST endpoints
            }
            """);

        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should extract controller as service component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("UserController");
        assertThat(component.type()).isEqualTo(ComponentType.SERVICE);
    }

    @Test
    void scan_withRepositoryAnnotation_extractsModuleComponent() throws IOException {
        // Given: A Java class with @Repository annotation
        createFile("src/main/java/com/example/UserRepository.java", """
            package com.example;

            import org.springframework.stereotype.Repository;

            @Repository
            public class UserRepository {
                // Data access logic
            }
            """);

        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should extract repository as module component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("UserRepository");
        assertThat(component.type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withMultipleComponents_extractsAll() throws IOException {
        // Given: Multiple Spring component classes
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;
            import org.springframework.stereotype.Service;

            @Service
            public class UserService {}
            """);

        createFile("src/main/java/com/example/OrderService.java", """
            package com.example;
            import org.springframework.stereotype.Service;

            @Service
            public class OrderService {}
            """);

        createFile("src/main/java/com/example/UserRepository.java", """
            package com.example;
            import org.springframework.stereotype.Repository;

            @Repository
            public class UserRepository {}
            """);

        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should extract all components
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(3);

        assertThat(result.components())
            .extracting(Component::name)
            .containsExactlyInAnyOrder("UserService", "OrderService", "UserRepository");
    }

    @Test
    void scan_withConfigurationAnnotation_extractsModuleComponent() throws IOException {
        // Given: A Java class with @Configuration annotation
        createFile("src/main/java/com/example/AppConfig.java", """
            package com.example;

            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class AppConfig {
                // Configuration beans
            }
            """);

        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should extract configuration as module component
        assertThat(result.success()).isTrue();
        assertThat(result.components()).hasSize(1);

        Component component = result.components().get(0);
        assertThat(component.name()).isEqualTo("AppConfig");
        assertThat(component.type()).isEqualTo(ComponentType.MODULE);
    }

    @Test
    void scan_withInterface_skipsInterface() throws IOException {
        // Given: An interface with @Service annotation
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            import org.springframework.stereotype.Service;

            @Service
            public interface UserService {
                void createUser();
            }
            """);

        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should not extract interface
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void scan_withoutSpringDependency_doesNotApply() throws IOException {
        // Given: Java class with Spring annotation but no Spring dependency
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            import org.springframework.stereotype.Service;

            @Service
            public class UserService {}
            """);

        // When: Check if scanner applies (no Spring in dependencies)
        boolean applies = scanner.appliesTo(context);

        // Then: Should not apply
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withNoJavaFiles_returnsEmptyResult() {
        // Given: No Java files
        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Scanner is executed
        ScanResult result = scanner.scan(contextWithSpring);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.components()).isEmpty();
    }

    @Test
    void appliesTo_withSpringDependency_returnsTrue() {
        // Given: Context with Spring dependency in previous results
        ScanContext contextWithSpring = createContextWithSpringDependency();

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(contextWithSpring);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutSpringDependency_returnsFalse() {
        // Given: Context without Spring dependency

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    /**
     * Creates a ScanContext with a mocked Spring dependency in previous results.
     */
    private ScanContext createContextWithSpringDependency() {
        Dependency springDep = new Dependency(
            "test-project",
            "org.springframework.boot",
            "spring-boot-starter-web",
            "3.2.0",
            "compile",
            true
        );

        ScanResult previousResult = new ScanResult(
            "maven-dependencies",
            true,
            List.of(),
            List.of(springDep),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        return new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of(),
            Map.of(),
            Map.of("maven-dependencies", previousResult)
        );
    }
}
