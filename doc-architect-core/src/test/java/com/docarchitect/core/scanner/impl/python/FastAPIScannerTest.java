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
 * Functional tests for {@link FastAPIScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse FastAPI route decorators using regex patterns</li>
 *   <li>Extract HTTP methods (GET, POST, PUT, DELETE, PATCH)</li>
 *   <li>Extract path parameters from routes</li>
 *   <li>Detect Query parameters</li>
 *   <li>Detect request body parameters</li>
 *   <li>Handle both @app and @router decorators</li>
 * </ul>
 *
 * @see FastAPIScanner
 * @since 1.0.0
 */
class FastAPIScannerTest extends ScannerTestBase {

    private FastAPIScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new FastAPIScanner();
    }

    @Test
    void scan_withSimpleGetEndpoint_extractsEndpoint() throws IOException {
        // Given: A simple FastAPI GET endpoint
        createFile("app/main.py", """
            from fastapi import FastAPI

            app = FastAPI()

            @app.get("/users")
            def get_users():
                return []
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract GET endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.type()).isEqualTo(ApiType.REST);
        assertThat(endpoint.path()).isEqualTo("/users");
        assertThat(endpoint.method()).isEqualTo("GET");
    }

    @Test
    void scan_withPathParameter_extractsParameter() throws IOException {
        // Given: FastAPI endpoint with path parameter
        createFile("app/users.py", """
            from fastapi import FastAPI

            app = FastAPI()

            @app.get("/users/{user_id}")
            def get_user(user_id: int):
                return {"id": user_id}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract path parameter
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.path()).isEqualTo("/users/{user_id}");
        assertThat(endpoint.requestSchema()).contains("user_id");
    }

    @Test
    void scan_withMultipleHttpMethods_extractsAll() throws IOException {
        // Given: FastAPI file with multiple HTTP methods
        createFile("app/items.py", """
            from fastapi import FastAPI

            app = FastAPI()

            @app.get("/items")
            def get_items():
                return []

            @app.post("/items")
            def create_item(item: dict):
                return item

            @app.put("/items/{item_id}")
            def update_item(item_id: int, item: dict):
                return item

            @app.delete("/items/{item_id}")
            def delete_item(item_id: int):
                return {"deleted": item_id}

            @app.patch("/items/{item_id}")
            def patch_item(item_id: int):
                return {"patched": item_id}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 5 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH");
    }

    @Test
    void scan_withRouter_extractsEndpoints() throws IOException {
        // Given: FastAPI using APIRouter
        createFile("app/routers/products.py", """
            from fastapi import APIRouter

            router = APIRouter()

            @router.get("/products")
            def get_products():
                return []

            @router.post("/products")
            def create_product(name: str, price: float):
                return {"name": name, "price": price}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract router endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");
    }

    @Test
    void scan_withQueryParameter_extractsParameter() throws IOException {
        // Given: FastAPI endpoint with Query parameter
        createFile("app/search.py", """
            from fastapi import FastAPI, Query

            app = FastAPI()

            @app.get("/search")
            def search(query: str = Query(...), page: int = Query(1)):
                return {"query": query, "page": page}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract Query parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.requestSchema()).contains("query", "page");
    }

    @Test
    void scan_withBodyParameter_extractsParameter() throws IOException {
        // Given: FastAPI endpoint with request body
        createFile("app/create.py", """
            from fastapi import FastAPI
            from pydantic import BaseModel

            app = FastAPI()

            class UserCreate(BaseModel):
                name: str
                email: str

            @app.post("/users")
            def create_user(user: UserCreate):
                return user
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract body parameter
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.requestSchema()).contains("user");
        assertThat(endpoint.requestSchema()).contains("UserCreate");
    }

    @Test
    void scan_withNoFastAPICode_returnsEmpty() throws IOException {
        // Given: Python file without FastAPI code
        createFile("app/utils.py", """
            def helper_function():
                return "not a FastAPI endpoint"
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withNoPythonFiles_returnsEmptyResult() throws IOException {
        // Given: No Python files in project
        createDirectory("src/main/java");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void appliesTo_withPythonFiles_returnsTrue() throws IOException {
        // Given: Project with Python files
        createFile("app/test.py", "print('hello')");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutPythonFiles_returnsFalse() throws IOException {
        // Given: Project without Python files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }

    @Test
    void scan_withMultiLineDecorator_extractsEndpoint() throws IOException {
        // Given: FastAPI endpoint with multi-line decorator
        createFile("app/routes/login.py", """
            from fastapi import APIRouter, Depends
            from fastapi.responses import HTMLResponse

            router = APIRouter(tags=["login"])

            @router.post(
                "/password-recovery-html-content/{email}",
                dependencies=[Depends(get_current_active_superuser)],
                response_class=HTMLResponse,
            )
            def recover_password_html_content(email: str, session: SessionDep):
                return HTMLResponse(content="test")
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract endpoint from multi-line decorator
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.type()).isEqualTo(ApiType.REST);
        assertThat(endpoint.path()).isEqualTo("/password-recovery-html-content/{email}");
        assertThat(endpoint.method()).isEqualTo("POST");
        assertThat(endpoint.requestSchema()).contains("email");
    }

    @Test
    void scan_withMultipleMultiLineDecorators_extractsAll() throws IOException {
        // Given: Multiple endpoints with multi-line decorators
        createFile("app/api/routes.py", """
            from fastapi import APIRouter

            router = APIRouter()

            @router.get(
                "/items",
                response_model=list,
            )
            def get_items():
                return []

            @router.post(
                "/items",
                status_code=201,
            )
            def create_item(item: dict):
                return item

            @router.put(
                "/items/{item_id}",
                response_model=dict,
            )
            def update_item(item_id: int, item: dict):
                return item
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 3 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT");
    }
}
