package com.docarchitect.core.scanner.impl.php;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link LaravelApiScanner}.
 *
 * <p>Tests the scanner's ability to parse Laravel route definitions and extract
 * API endpoint information from both route files and controller-based routing.
 */
class LaravelApiScannerTest extends ScannerTestBase {

    private final LaravelApiScanner scanner = new LaravelApiScanner();

    @Test
    void scan_withSimpleRoutes_extractsEndpoints() throws IOException {
        // Given: A Laravel routes file with simple route definitions
        createFile("routes/api.php", """
            <?php

            use Illuminate\\Support\\Facades\\Route;
            use App\\Http\\Controllers\\UserController;

            Route::get('/users', [UserController::class, 'index']);
            Route::post('/users', [UserController::class, 'store']);
            Route::get('/users/{id}', [UserController::class, 'show']);
            Route::put('/users/{id}', [UserController::class, 'update']);
            Route::delete('/users/{id}', [UserController::class, 'destroy']);
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        // Verify GET /users
        ApiEndpoint getUsers = result.apiEndpoints().stream()
            .filter(e -> e.path().equals("/users") && e.method().equals("GET"))
            .findFirst()
            .orElseThrow();
        assertThat(getUsers.type()).isEqualTo(ApiType.REST);
        assertThat(getUsers.description()).isEqualTo("UserController@index");

        // Verify GET /users/{id} has path parameter
        ApiEndpoint showUser = result.apiEndpoints().stream()
            .filter(e -> e.path().equals("/users/{id}") && e.method().equals("GET"))
            .findFirst()
            .orElseThrow();
        assertThat(showUser.requestSchema()).contains("id");
    }

    @Test
    void scan_withStringBasedRouting_extractsEndpoints() throws IOException {
        // Given: A Laravel routes file with string-based controller references
        createFile("routes/web.php", """
            <?php

            Route::get('/posts', 'PostController@index');
            Route::post('/posts', 'PostController@store');
            Route::get('/posts/{post}', 'PostController@show');
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        ApiEndpoint getPost = result.apiEndpoints().stream()
            .filter(e -> e.path().equals("/posts/{post}"))
            .findFirst()
            .orElseThrow();
        assertThat(getPost.description()).isEqualTo("PostController@show");
    }

    @Test
    void scan_withResourceRoute_generatesStandardEndpoints() throws IOException {
        // Given: A Laravel routes file with resource route
        createFile("routes/api.php", """
            <?php

            use Illuminate\\Support\\Facades\\Route;
            use App\\Http\\Controllers\\ArticleController;

            Route::apiResource('articles', ArticleController::class);
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should generate standard REST endpoints (5 for apiResource)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        // Verify all standard API resource routes are created
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "GET", "PUT", "DELETE");

        // Verify paths include the singular parameter
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .contains("/articles", "/articles/{article}");
    }

    @Test
    void scan_withFullResourceRoute_includesCreateAndEdit() throws IOException {
        // Given: A Laravel routes file with full resource route
        createFile("routes/web.php", """
            <?php

            Route::resource('products', ProductController::class);
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should generate all 7 resource routes (including create and edit)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(7);

        // Verify create and edit routes are included
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .contains("/products/create", "/products/{product}/edit");
    }

    @Test
    void scan_withRoutePrefix_appliesPrefix() throws IOException {
        // Given: A Laravel routes file with route prefix
        createFile("routes/api.php", """
            <?php

            Route::prefix('api/v1')->group(function () {
                Route::get('/users', [UserController::class, 'index']);
                Route::get('/users/{id}', [UserController::class, 'show']);
            });
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should apply prefix to routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/api/v1/users", "/api/v1/users/{id}");
    }

    @Test
    void scan_withOptionalParameter_extractsParameter() throws IOException {
        // Given: A Laravel routes file with optional parameter
        createFile("routes/api.php", """
            <?php

            Route::get('/search/{query?}', [SearchController::class, 'search']);
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract optional parameter
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/search/{query?}");
        assertThat(endpoint.requestSchema()).contains("query");
    }

    @Test
    void scan_withAllHttpMethods_extractsCorrectMethods() throws IOException {
        // Given: A Laravel routes file with all HTTP methods
        createFile("routes/api.php", """
            <?php

            Route::get('/test', 'TestController@get');
            Route::post('/test', 'TestController@post');
            Route::put('/test', 'TestController@put');
            Route::patch('/test', 'TestController@patch');
            Route::delete('/test', 'TestController@delete');
            Route::options('/test', 'TestController@options');
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all HTTP methods
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(6);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    void scan_withNoRouteFiles_returnsEmptyResult() {
        // Given: No PHP files

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withMultiplePathParameters_extractsAll() throws IOException {
        // Given: A Laravel routes file with multiple path parameters
        createFile("routes/api.php", """
            <?php

            Route::get('/users/{user}/posts/{post}/comments/{comment}', 'CommentController@show');
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all path parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.requestSchema()).contains("user", "post", "comment");
    }

    @Test
    void scan_withNamespacedController_extractsClassName() throws IOException {
        // Given: A Laravel routes file with namespaced controller
        createFile("routes/api.php", """
            <?php

            Route::get('/admin/users', [App\\Http\\Controllers\\Admin\\UserController::class, 'index']);
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract just the class name without namespace
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.description()).isEqualTo("UserController@index");
    }

    @Test
    void appliesTo_withLaravelRouteFile_returnsTrue() throws IOException {
        // Given: A Laravel route file exists
        createFile("routes/api.php", """
            <?php
            use Illuminate\\Support\\Facades\\Route;
            Route::get('/test', 'TestController@test');
            """);

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutRouteFiles_returnsFalse() {
        // Given: No PHP route files

        // When: Check if scanner applies
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("laravel-api");
    }

    @Test
    void getDisplayName_returnsHumanReadableName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Laravel API Scanner");
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
