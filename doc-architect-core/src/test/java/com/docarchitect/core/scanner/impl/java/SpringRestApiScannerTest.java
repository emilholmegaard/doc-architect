package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link SpringRestApiScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse Spring REST controllers using JavaParser AST</li>
 *   <li>Extract REST API endpoints from various Spring annotations</li>
 *   <li>Handle different HTTP methods (GET, POST, PUT, DELETE, PATCH)</li>
 *   <li>Combine class-level and method-level request mappings</li>
 *   <li>Extract request parameters (@PathVariable, @RequestParam, @RequestBody)</li>
 *   <li>Capture return types from method signatures</li>
 * </ul>
 *
 * @see SpringRestApiScanner
 * @since 1.0.0
 */
class SpringRestApiScannerTest extends ScannerTestBase {

    private SpringRestApiScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new SpringRestApiScanner();
    }

    @Test
    void scan_withSimpleRestController_extractsEndpoints() throws IOException {
        // Given: A simple REST controller with @GetMapping and @PostMapping
        createFile("src/main/java/com/example/UserController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/users")
            public class UserController {

                @GetMapping
                public List<User> getAllUsers() {
                    return List.of();
                }

                @PostMapping
                public User createUser(@RequestBody User user) {
                    return user;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract 2 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint getEndpoint = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(getEndpoint.type()).isEqualTo(ApiType.REST);
        assertThat(getEndpoint.path()).isEqualTo("/api/users");
        assertThat(getEndpoint.method()).isEqualTo("GET");
        assertThat(getEndpoint.componentId()).isEqualTo("com.example.UserController");
        assertThat(getEndpoint.responseSchema()).isEqualTo("List<User>");

        ApiEndpoint postEndpoint = result.apiEndpoints().stream()
            .filter(e -> "POST".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(postEndpoint.type()).isEqualTo(ApiType.REST);
        assertThat(postEndpoint.path()).isEqualTo("/api/users");
        assertThat(postEndpoint.method()).isEqualTo("POST");
        assertThat(postEndpoint.requestSchema()).isEqualTo("RequestBody:user:User");
    }

    @Test
    void scan_withPathVariable_extractsParameter() throws IOException {
        // Given: REST controller with @PathVariable
        createFile("src/main/java/com/example/ProductController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/products")
            public class ProductController {

                @GetMapping("/{id}")
                public Product getProduct(@PathVariable Long id) {
                    return null;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract endpoint with path variable
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/api/products/id");
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.requestSchema()).isEqualTo("PathVariable:id:Long");
    }

    @Test
    void scan_withRequestParam_extractsParameter() throws IOException {
        // Given: REST controller with @RequestParam
        createFile("src/main/java/com/example/SearchController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            public class SearchController {

                @GetMapping("/search")
                public List<Result> search(@RequestParam String query, @RequestParam int page) {
                    return List.of();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract endpoint with request params
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/search");
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.requestSchema()).isEqualTo("RequestParam:query:String, RequestParam:page:int");
    }

    @Test
    void scan_withMultipleHttpMethods_extractsAll() throws IOException {
        // Given: REST controller with GET, POST, PUT, DELETE
        createFile("src/main/java/com/example/OrderController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/orders")
            public class OrderController {

                @GetMapping("/{id}")
                public Order getOrder(@PathVariable String id) {
                    return null;
                }

                @PostMapping
                public Order createOrder(@RequestBody Order order) {
                    return order;
                }

                @PutMapping("/{id}")
                public Order updateOrder(@PathVariable String id, @RequestBody Order order) {
                    return order;
                }

                @DeleteMapping("/{id}")
                public void deleteOrder(@PathVariable String id) {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 4 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(4);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE");
    }

    @Test
    void scan_withNoControllerAnnotation_returnsEmpty() throws IOException {
        // Given: A regular class without @RestController or @Controller
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            public class UserService {
                public User getUser(Long id) {
                    return null;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result (no endpoints)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withNoJavaFiles_returnsEmptyResult() throws IOException {
        // Given: No Java files in project
        createDirectory("src/main/resources");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withPatchMapping_extractsEndpoint() throws IOException {
        // Given: REST controller with @PatchMapping
        createFile("src/main/java/com/example/ProfileController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/profile")
            public class ProfileController {

                @PatchMapping("/{id}")
                public Profile updateProfile(@PathVariable Long id, @RequestBody ProfilePatch patch) {
                    return null;
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract PATCH endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.method()).isEqualTo("PATCH");
        assertThat(endpoint.path()).isEqualTo("/profile/id");
        assertThat(endpoint.requestSchema()).isEqualTo("PathVariable:id:Long, RequestBody:patch:ProfilePatch");
    }

    @Test
    void scan_withControllerWithoutRequestMapping_extractsEndpoints() throws IOException {
        // Given: @RestController without class-level @RequestMapping
        createFile("src/main/java/com/example/HealthController.java", """
            package com.example;

            import org.springframework.web.bind.annotation.*;

            @RestController
            public class HealthController {

                @GetMapping("/health")
                public String health() {
                    return "OK";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract endpoint with correct path
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/health");
        assertThat(endpoint.method()).isEqualTo("GET");
    }

    @Test
    void appliesTo_withJavaFiles_returnsTrue() throws IOException {
        // Given: Project with Java files
        createFile("src/main/java/com/example/Test.java", "public class Test {}");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutJavaFiles_returnsFalse() throws IOException {
        // Given: Project without Java files
        createDirectory("src/main/resources");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
