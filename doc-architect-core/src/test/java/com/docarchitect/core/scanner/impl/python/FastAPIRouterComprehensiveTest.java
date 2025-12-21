package com.docarchitect.core.scanner.impl.python;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test for FastAPI router pattern detection.
 *
 * <p>Validates issue #61: FastAPIScanner fails to detect REST endpoints using APIRouter pattern.
 *
 * <p><b>Acceptance Criteria:</b>
 * <ul>
 *   <li>Scanner detects {@code @router.get/post/put/delete/patch()}</li>
 *   <li>Scanner handles path parameters</li>
 *   <li>Scanner finds 15+ endpoints in FastAPI test project</li>
 * </ul>
 *
 * @see <a href="https://github.com/emilholmegaard/doc-architect/issues/61">Issue #61</a>
 * @since 1.0.0
 */
class FastAPIRouterComprehensiveTest extends ScannerTestBase {

    private FastAPIScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new FastAPIScanner();
    }

    @Test
    void scan_withComprehensiveRouterProject_finds15PlusEndpoints() throws IOException {
        // Given: A realistic FastAPI project structure using APIRouter pattern

        // Main application file
        createFile("app/main.py", """
            from fastapi import FastAPI
            from app.routers import items, users, orders

            app = FastAPI()

            app.include_router(items.router, prefix="/api/v1")
            app.include_router(users.router, prefix="/api/v1")
            app.include_router(orders.router, prefix="/api/v1")

            @app.get("/")
            def root():
                return {"status": "ok"}

            @app.get("/health")
            def health():
                return {"status": "healthy"}
            """);

        // Items router - the exact pattern from issue #61
        createFile("app/routers/items.py", """
            from fastapi import APIRouter, Query
            from typing import Optional

            router = APIRouter(prefix="/items")

            @router.get("/")
            def read_items(skip: int = Query(0), limit: int = Query(10)):
                return []

            @router.get("/{item_id}")
            def read_item(item_id: int):
                return {"item_id": item_id}

            @router.post("/")
            def create_item(name: str, price: float):
                return {"name": name, "price": price}

            @router.put("/{item_id}")
            def update_item(item_id: int, name: str, price: float):
                return {"item_id": item_id, "name": name, "price": price}

            @router.delete("/{item_id}")
            def delete_item(item_id: int):
                return {"deleted": item_id}

            @router.patch("/{item_id}")
            def patch_item(item_id: int):
                return {"patched": item_id}
            """);

        // Users router
        createFile("app/routers/users.py", """
            from fastapi import APIRouter

            router = APIRouter(prefix="/users")

            @router.get("/")
            def list_users():
                return []

            @router.get("/{user_id}")
            def get_user(user_id: int):
                return {"user_id": user_id}

            @router.post("/")
            def create_user(username: str, email: str):
                return {"username": username, "email": email}

            @router.put("/{user_id}")
            def update_user(user_id: int, username: str):
                return {"user_id": user_id, "username": username}

            @router.delete("/{user_id}")
            def delete_user(user_id: int):
                return {"deleted": user_id}
            """);

        // Orders router
        createFile("app/routers/orders.py", """
            from fastapi import APIRouter

            router = APIRouter(prefix="/orders")

            @router.get("/")
            def list_orders():
                return []

            @router.get("/{order_id}")
            def get_order(order_id: str):
                return {"order_id": order_id}

            @router.post("/")
            def create_order(user_id: int, items: list):
                return {"user_id": user_id, "items": items}

            @router.put("/{order_id}/status")
            def update_order_status(order_id: str, status: str):
                return {"order_id": order_id, "status": status}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should find 15+ endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .as("Should find at least 15 endpoints across all router files")
            .hasSizeGreaterThanOrEqualTo(15);

        // Count by HTTP method
        long getCount = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()))
            .count();
        long postCount = result.apiEndpoints().stream()
            .filter(e -> "POST".equals(e.method()))
            .count();
        long putCount = result.apiEndpoints().stream()
            .filter(e -> "PUT".equals(e.method()))
            .count();
        long deleteCount = result.apiEndpoints().stream()
            .filter(e -> "DELETE".equals(e.method()))
            .count();
        long patchCount = result.apiEndpoints().stream()
            .filter(e -> "PATCH".equals(e.method()))
            .count();

        assertThat(getCount).as("Should detect GET endpoints").isGreaterThanOrEqualTo(6);
        assertThat(postCount).as("Should detect POST endpoints").isGreaterThanOrEqualTo(3);
        assertThat(putCount).as("Should detect PUT endpoints").isGreaterThanOrEqualTo(3);
        assertThat(deleteCount).as("Should detect DELETE endpoints").isGreaterThanOrEqualTo(2);
        assertThat(patchCount).as("Should detect PATCH endpoints").isGreaterThanOrEqualTo(1);
    }

    @Test
    void scan_withRouterGetMethod_detectsEndpoint() throws IOException {
        // Given: The exact pattern from issue #61
        createFile("app/items.py", """
            from fastapi import APIRouter

            router = APIRouter(prefix="/items")

            @router.get("/")
            def read_items():
                return items
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect the endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .as("Should detect @router.get() endpoint")
            .hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.type()).isEqualTo(ApiType.REST);
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.path()).isEqualTo("/");
    }

    @Test
    void scan_withRouterAndPathParameters_extractsParameters() throws IOException {
        // Given: Router endpoints with path parameters
        createFile("app/api.py", """
            from fastapi import APIRouter

            router = APIRouter()

            @router.get("/users/{user_id}/posts/{post_id}")
            def get_user_post(user_id: int, post_id: str):
                return {"user_id": user_id, "post_id": post_id}

            @router.put("/items/{item_id}")
            def update_item(item_id: int, name: str):
                return {"item_id": item_id, "name": name}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract path parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint getUserPost = result.apiEndpoints().stream()
            .filter(e -> e.path().contains("posts"))
            .findFirst()
            .orElseThrow();

        assertThat(getUserPost.requestSchema())
            .as("Should extract multiple path parameters")
            .contains("user_id", "post_id");

        ApiEndpoint updateItem = result.apiEndpoints().stream()
            .filter(e -> e.path().contains("items"))
            .findFirst()
            .orElseThrow();

        assertThat(updateItem.requestSchema())
            .as("Should extract path parameter")
            .contains("item_id");
    }

    @Test
    void scan_withAllRouterHttpMethods_detectsAll() throws IOException {
        // Given: Router with all HTTP methods
        createFile("app/resources.py", """
            from fastapi import APIRouter

            router = APIRouter()

            @router.get("/resource")
            def get_resource():
                return {}

            @router.post("/resource")
            def create_resource():
                return {}

            @router.put("/resource")
            def update_resource():
                return {}

            @router.delete("/resource")
            def delete_resource():
                return {}

            @router.patch("/resource")
            def patch_resource():
                return {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all HTTP methods
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .as("Should detect all @router.METHOD() decorators")
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH");
    }

    @Test
    void scan_withMixedAppAndRouterDecorators_detectsBoth() throws IOException {
        // Given: File with both @app and @router decorators
        createFile("app/mixed.py", """
            from fastapi import FastAPI, APIRouter

            app = FastAPI()
            router = APIRouter()

            @app.get("/app-endpoint")
            def app_endpoint():
                return {"source": "app"}

            @router.get("/router-endpoint")
            def router_endpoint():
                return {"source": "router"}

            @app.post("/app-create")
            def app_create():
                return {}

            @router.post("/router-create")
            def router_create():
                return {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect both app and router endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(4);

        long appEndpoints = result.apiEndpoints().stream()
            .filter(e -> e.path().contains("app"))
            .count();
        long routerEndpoints = result.apiEndpoints().stream()
            .filter(e -> e.path().contains("router"))
            .count();

        assertThat(appEndpoints).isEqualTo(2);
        assertThat(routerEndpoints).isEqualTo(2);
    }

    @Test
    void scan_withApiVariableName_detectsEndpoints() throws IOException {
        // Given: APIRouter assigned to variable named 'api' (issue #61)
        createFile("app/api_routes.py", """
            from fastapi import APIRouter

            api = APIRouter(prefix="/items")

            @api.get("/")
            def read_items():
                return items

            @api.post("/")
            def create_item(name: str):
                return {"name": name}

            @api.delete("/{item_id}")
            def delete_item(item_id: int):
                return {"deleted": item_id}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all @api.METHOD() endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .as("Should detect @api.get/post/delete() endpoints")
            .hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "DELETE");
    }

    @Test
    void scan_withCustomVariableNames_detectsAll() throws IOException {
        // Given: Multiple files with different variable names
        createFile("app/routes1.py", """
            from fastapi import APIRouter

            route = APIRouter()

            @route.get("/route1")
            def get_route1():
                return {}
            """);

        createFile("app/routes2.py", """
            from fastapi import APIRouter

            my_router = APIRouter()

            @my_router.post("/route2")
            def post_route2():
                return {}
            """);

        createFile("app/routes3.py", """
            from fastapi import APIRouter

            v1_api = APIRouter()

            @v1_api.put("/route3")
            def put_route3():
                return {}
            """);

        createFile("app/routes4.py", """
            from fastapi import APIRouter

            _private_router = APIRouter()

            @_private_router.delete("/route4")
            def delete_route4():
                return {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all endpoints regardless of variable name
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .as("Should detect endpoints with custom variable names: route, my_router, v1_api, _private_router")
            .hasSize(4);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE");
    }

    @Test
    void scan_withNumericVariableNames_detectsEndpoints() throws IOException {
        // Given: Variable names with numbers
        createFile("app/versioned.py", """
            from fastapi import APIRouter

            router_v1 = APIRouter()
            router_v2 = APIRouter()

            @router_v1.get("/v1/items")
            def get_items_v1():
                return []

            @router_v2.get("/v2/items")
            def get_items_v2():
                return []
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect endpoints with numeric suffixes
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .as("Should detect @router_v1 and @router_v2 endpoints")
            .hasSize(2);
    }
}
