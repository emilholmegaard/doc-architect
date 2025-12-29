package com.docarchitect.core.scanner.impl.go;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link GoHttpRouterScanner}.
 */
class GoHttpRouterScannerTest extends ScannerTestBase {

    private GoHttpRouterScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new GoHttpRouterScanner();
    }

    // ==================== Gin Framework Tests ====================

    @Test
    void scan_withGinRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with Gin routes
        createFile("cmd/main.go", """
package main

import "github.com/gin-gonic/gin"

func main() {
    r := gin.Default()
    r.GET("/users/:id", getUser)
    r.POST("/users", createUser)
    r.PUT("/users/:id", updateUser)
    r.DELETE("/users/:id", deleteUser)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all Gin routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(4);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE");

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .contains("/users/:id", "/users");
    }

    @Test
    void scan_withGinRouteGroups_concatenatesPrefixes() throws IOException {
        // Given: Go file with Gin route groups
        createFile("internal/routes.go", """
package api

import "github.com/gin-gonic/gin"

func setupRoutes(r *gin.Engine) {
    v1 := r.Group("/api/v1")
    v1.GET("/users", listUsers)
    v1.POST("/users", createUser)

    v2 := r.Group("/api/v2")
    v2.GET("/users", listUsersV2)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should concatenate group prefixes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/api/v1/users", "/api/v1/users", "/api/v2/users");
    }

    @Test
    void scan_withNestedGinGroups_concatenatesMultiplePrefixes() throws IOException {
        // Given: Go file with nested Gin route groups
        createFile("internal/routes.go", """
package api

import "github.com/gin-gonic/gin"

func setupRoutes(r *gin.Engine) {
    api := r.Group("/api")
    v1 := api.Group("/v1")
    v1.GET("/users", listUsers)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should concatenate nested group prefixes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/api/v1/users");
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.description()).isEqualTo("listUsers");
    }

    // ==================== Echo Framework Tests ====================

    @Test
    void scan_withEchoRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with Echo routes
        createFile("cmd/server.go", """
package main

import "github.com/labstack/echo/v4"

func main() {
    e := echo.New()
    e.GET("/users/:id", getUser)
    e.POST("/users", createUser)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Echo routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");
    }

    @Test
    void scan_withEchoGroups_concatenatesPrefixes() throws IOException {
        // Given: Go file with Echo groups
        createFile("internal/routes.go", """
package api

import "github.com/labstack/echo/v4"

func setupRoutes(e *echo.Echo) {
    api := e.Group("/api")
    api.GET("/health", healthCheck)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should concatenate Echo group prefixes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api/health");
    }

    // ==================== Chi Framework Tests ====================

    @Test
    void scan_withChiRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with Chi routes
        createFile("cmd/main.go", """
package main

import "github.com/go-chi/chi/v5"

func main() {
    r := chi.NewRouter()
    r.Get("/users/{id}", getUser)
    r.Post("/users", createUser)
    r.Put("/users/{id}", updateUser)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Chi routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT");

        // Chi uses {param} syntax instead of :param
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .contains("/users/{id}", "/users");
    }

    @Test
    void scan_withChiRouteMethod_extractsGroupedRoutes() throws IOException {
        // Given: Go file with Chi Route() method
        createFile("internal/routes.go", """
package api

import "github.com/go-chi/chi/v5"

func setupRoutes(r chi.Router) {
    r.Route("/api", func(r chi.Router) {
        r.Get("/users", listUsers)
        r.Post("/users", createUser)
    })
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Chi routes with group prefix
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/api/users", "/api/users");
    }

    // ==================== Gorilla Mux Framework Tests ====================

    @Test
    void scan_withMuxRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with Gorilla Mux routes
        createFile("cmd/main.go", """
package main

import "github.com/gorilla/mux"

func main() {
    r := mux.NewRouter()
    r.HandleFunc("/users/{id}", getUser).Methods("GET")
    r.HandleFunc("/users", createUser).Methods("POST")
    r.HandleFunc("/users/{id}", deleteUser).Methods("DELETE")
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Mux routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "DELETE");
    }

    // ==================== Fiber Framework Tests ====================

    @Test
    void scan_withFiberRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with Fiber routes
        createFile("cmd/main.go", """
package main

import "github.com/gofiber/fiber/v2"

func main() {
    app := fiber.New()
    app.Get("/users/:id", getUser)
    app.Post("/users", createUser)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Fiber routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");
    }

    @Test
    void scan_withFiberGroups_concatenatesPrefixes() throws IOException {
        // Given: Go file with Fiber groups
        createFile("internal/routes.go", """
package api

import "github.com/gofiber/fiber/v2"

func setupRoutes(app *fiber.App) {
    api := app.Group("/api")
    api.Get("/status", getStatus)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should concatenate Fiber group prefixes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api/status");
    }

    // ==================== net/http Standard Library Tests ====================

    @Test
    void scan_withNetHttpRoutes_extractsEndpoints() throws IOException {
        // Given: Go file with net/http routes
        createFile("cmd/main.go", """
package main

import "net/http"

func main() {
    http.HandleFunc("/users", usersHandler)
    http.HandleFunc("/health", healthHandler)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract net/http routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/users", "/health");

        // net/http doesn't specify method in route registration
        assertThat(result.apiEndpoints())
            .allMatch(endpoint -> "GET".equals(endpoint.method()));
    }

    // ==================== Multi-Framework Tests ====================

    @Test
    void scan_withMultipleFrameworks_extractsAllRoutes() throws IOException {
        // Given: Multiple Go files with different frameworks
        createFile("cmd/gin.go", """
package main

import "github.com/gin-gonic/gin"

func setupGin() {
    r := gin.Default()
    r.GET("/gin/users", getUsers)
}
""");

        createFile("cmd/echo.go", """
package main

import "github.com/labstack/echo/v4"

func setupEcho() {
    e := echo.New()
    e.GET("/echo/users", getUsers)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract routes from all frameworks
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/gin/users", "/echo/users");
    }

    // ==================== Pre-filtering Tests ====================

    @Test
    void scan_withoutFrameworkImports_skipsFile() throws IOException {
        // Given: Go file without HTTP framework imports
        createFile("pkg/util.go", """
package util

import "fmt"

func PrintMessage(msg string) {
    fmt.Println(msg)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not extract any routes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withTestFiles_includesIfHasFramework() throws IOException {
        // Given: Test file with framework imports
        createFile("internal/routes_test.go", """
package api_test

import (
    "testing"
    "github.com/gin-gonic/gin"
)

func TestRoutes(t *testing.T) {
    r := gin.Default()
    r.GET("/test", testHandler)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract routes from test files
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
    }

    // ==================== Edge Cases ====================

    @Test
    void scan_withAllHttpMethods_extractsAll() throws IOException {
        // Given: Go file with all HTTP methods
        createFile("internal/routes.go", """
package api

import "github.com/gin-gonic/gin"

func setupRoutes(r *gin.Engine) {
    r.GET("/resource", handleGet)
    r.POST("/resource", handlePost)
    r.PUT("/resource", handlePut)
    r.DELETE("/resource", handleDelete)
    r.PATCH("/resource", handlePatch)
    r.HEAD("/resource", handleHead)
    r.OPTIONS("/resource", handleOptions)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all HTTP methods
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(7);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    }

    @Test
    void scan_withPathsWithoutLeadingSlash_normalizesPath() throws IOException {
        // Given: Go file with routes missing leading slash
        createFile("internal/routes.go", """
package api

import "github.com/gin-gonic/gin"

func setupRoutes(r *gin.Engine) {
    api := r.Group("api")
    api.GET("users", listUsers)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should normalize paths with leading slash
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api/users");
    }

    @Test
    void scan_withComplexRoutePatterns_extractsCorrectly() throws IOException {
        // Given: Go file with complex route patterns
        createFile("internal/routes.go", """
package api

import "github.com/gin-gonic/gin"

func setupRoutes(r *gin.Engine) {
    r.GET("/users/:id/posts/:postId/comments", getComments)
    r.POST("/api/v1/auth/login", login)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract complex routes correctly
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "/users/:id/posts/:postId/comments",
                "/api/v1/auth/login"
            );
    }

    @Test
    void scan_withNoGoFiles_returnsEmpty() throws IOException {
        // Given: No Go files in project
        createDirectory("src");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    // ==================== Metadata Tests ====================

    @Test
    void scan_withGinRoutes_createsCorrectMetadata() throws IOException {
        // Given: Go file with Gin route
        createFile("cmd/main.go", """
package main

import "github.com/gin-gonic/gin"

func main() {
    r := gin.Default()
    r.GET("/users/:id", getUserHandler)
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should create ApiEndpoint with correct metadata
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.componentId()).isEqualTo("main");
        assertThat(endpoint.type()).isEqualTo(ApiType.REST);
        assertThat(endpoint.path()).isEqualTo("/users/:id");
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.description()).isEqualTo("getUserHandler");
    }

    // ==================== appliesTo Tests ====================

    @Test
    void appliesTo_withGoFiles_returnsTrue() throws IOException {
        // Given: Project with Go files
        createFile("pkg/app.go", "package app");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutGoFiles_returnsFalse() throws IOException {
        // Given: Project without Go files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
