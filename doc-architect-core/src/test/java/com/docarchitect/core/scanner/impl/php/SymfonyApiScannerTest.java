package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link SymfonyApiScanner}.
 *
 * <p>Tests the scanner's ability to parse Symfony route annotations and attributes
 * to extract API endpoint information.
 */
class SymfonyApiScannerTest extends ScannerTestBase {

    private final SymfonyApiScanner scanner = new SymfonyApiScanner();

    @Test
    void scan_withPhp8Attributes_extractsEndpoints() throws IOException {
        // Given: A Symfony controller with PHP 8 attributes
        createFile("src/Controller/UserController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;
            use Symfony\\Component\\HttpFoundation\\Response;

            class UserController
            {
                #[Route('/api/users', methods: ['GET'])]
                public function index(): Response
                {
                    return new Response();
                }

                #[Route('/api/users', methods: ['POST'])]
                public function create(): Response
                {
                    return new Response();
                }

                #[Route('/api/users/{id}', methods: ['GET'])]
                public function show(int $id): Response
                {
                    return new Response();
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        // Verify GET /api/users
        ApiEndpoint getUsers = result.apiEndpoints().stream()
            .filter(e -> e.path().equals("/api/users") && e.method().equals("GET"))
            .findFirst()
            .orElseThrow();
        assertThat(getUsers.type()).isEqualTo(ApiType.REST);
        assertThat(getUsers.description()).contains("index");

        // Verify GET /api/users/{id} has path parameter
        ApiEndpoint showUser = result.apiEndpoints().stream()
            .filter(e -> e.path().equals("/api/users/{id}"))
            .findFirst()
            .orElseThrow();
        assertThat(showUser.requestSchema()).contains("id");
    }

    @Test
    void scan_withDoctrineAnnotations_extractsEndpoints() throws IOException {
        // Given: A Symfony controller with Doctrine annotations
        createFile("src/Controller/PostController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Annotation\\Route;

            class PostController
            {
                /**
                 * @Route("/posts", methods={"GET"})
                 */
                public function list()
                {
                }

                /**
                 * @Route("/posts/{id}", methods={"GET"})
                 */
                public function show($id)
                {
                }

                /**
                 * @Route("/posts", methods={"POST"})
                 */
                public function create()
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/posts", "/posts/{id}", "/posts");
    }

    @Test
    void scan_withMultipleMethods_createsMultipleEndpoints() throws IOException {
        // Given: A Symfony controller with route supporting multiple methods
        createFile("src/Controller/ApiController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class ApiController
            {
                #[Route('/resource', methods: ['GET', 'POST'])]
                public function handleResource()
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create separate endpoints for each method
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");

        assertThat(result.apiEndpoints())
            .allMatch(e -> e.path().equals("/resource"));
    }

    @Test
    void scan_withRouteWithName_includesNameInDescription() throws IOException {
        // Given: A Symfony controller with named route
        createFile("src/Controller/ProductController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class ProductController
            {
                #[Route('/products/{id}', name: 'product_show', methods: ['GET'])]
                public function show(int $id)
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should include route name in description
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.description()).isEqualTo("product_show");
    }

    @Test
    void scan_withClassLevelPrefix_appliesPrefix() throws IOException {
        // Given: A Symfony controller with class-level route prefix
        createFile("src/Controller/AdminController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            #[Route('/admin')]
            class AdminController
            {
                #[Route('/users', methods: ['GET'])]
                public function users()
                {
                }

                #[Route('/settings', methods: ['GET'])]
                public function settings()
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should apply class-level prefix
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/admin/users", "/admin/settings");
    }

    @Test
    void scan_withNoMethodsSpecified_defaultsToGet() throws IOException {
        // Given: A Symfony controller with route without methods specified
        createFile("src/Controller/DefaultController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class DefaultController
            {
                #[Route('/home')]
                public function home()
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should default to GET method
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.method()).isEqualTo("GET");
    }

    @Test
    void scan_withPathParameterRequirements_extractsParameter() throws IOException {
        // Given: A Symfony controller with parameter requirements
        createFile("src/Controller/ItemController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class ItemController
            {
                #[Route('/items/{id<\\d+>}', methods: ['GET'])]
                public function show(int $id)
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract parameter without requirements syntax
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.requestSchema()).contains("id");
    }

    @Test
    void scan_withNoControllerFiles_returnsEmptyResult() {
        // Given: No PHP controller files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withMixedAnnotationsAndAttributes_extractsBoth() throws IOException {
        // Given: A Symfony controller with both annotations and attributes
        createFile("src/Controller/MixedController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class MixedController
            {
                #[Route('/new-style', methods: ['GET'])]
                public function newStyle()
                {
                }

                /**
                 * @Route("/old-style", methods={"POST"})
                 */
                public function oldStyle()
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both annotation styles
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/new-style", "/old-style");
    }

    @Test
    void scan_withAllHttpMethods_extractsCorrectMethods() throws IOException {
        // Given: A Symfony controller with various HTTP methods
        createFile("src/Controller/CrudController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class CrudController
            {
                #[Route('/resource', methods: ['GET'])]
                public function list() {}

                #[Route('/resource', methods: ['POST'])]
                public function create() {}

                #[Route('/resource/{id}', methods: ['PUT'])]
                public function update() {}

                #[Route('/resource/{id}', methods: ['PATCH'])]
                public function patch() {}

                #[Route('/resource/{id}', methods: ['DELETE'])]
                public function delete() {}
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all HTTP methods
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE");
    }

    @Test
    void scan_withMultiplePathParameters_extractsAll() throws IOException {
        // Given: A Symfony controller with multiple path parameters
        createFile("src/Controller/NestedController.php", """
            <?php

            namespace App\\Controller;

            use Symfony\\Component\\Routing\\Attribute\\Route;

            class NestedController
            {
                #[Route('/users/{userId}/posts/{postId}/comments/{commentId}', methods: ['GET'])]
                public function show(int $userId, int $postId, int $commentId)
                {
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all path parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.requestSchema()).contains("userId", "postId", "commentId");
    }

    @Test
    void appliesTo_withSymfonyController_returnsTrue() throws IOException {
        // Given: A Symfony controller file exists
        createFile("src/Controller/TestController.php", """
            <?php
            use Symfony\\Component\\Routing\\Attribute\\Route;

            class TestController
            {
                #[Route('/test', methods: ['GET'])]
                public function test() {}
            }
            """);

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutSymfonyRoutes_returnsFalse() {
        // Given: No Symfony route files

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("symfony-api");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Symfony API Scanner");
    }

    @Test
    void getSupportedLanguages_includesPhp() {
        assertThat(scanner.getSupportedLanguages()).containsExactly("php");
    }

    @Test
    void getPriority_returnsMidPriority() {
        // API scanners should run after dependency scanners
        assertThat(scanner.getPriority()).isEqualTo(50);
    }
}
