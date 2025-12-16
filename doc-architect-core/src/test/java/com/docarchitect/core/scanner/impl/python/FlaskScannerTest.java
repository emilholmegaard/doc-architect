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
 * Functional tests for {@link FlaskScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse Flask route decorators using regex patterns</li>
 *   <li>Extract HTTP methods (GET, POST, PUT, DELETE, PATCH)</li>
 *   <li>Support both modern (@app.get) and legacy (@app.route) decorators</li>
 *   <li>Extract path parameters from routes</li>
 *   <li>Handle blueprint-based routes</li>
 * </ul>
 *
 * @see FlaskScanner
 * @since 1.0.0
 */
class FlaskScannerTest extends ScannerTestBase {

    private FlaskScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new FlaskScanner();
    }

    @Test
    void scan_withModernDecorators_extractsEndpoints() throws IOException {
        // Given: Flask app using modern decorators (Flask 2.0+)
        createFile("app/main.py", """
            from flask import Flask

            app = Flask(__name__)

            @app.get('/users')
            def get_users():
                return {'users': []}

            @app.post('/users')
            def create_user():
                return {}, 201
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract both endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");

        assertThat(result.apiEndpoints())
            .allMatch(e -> e.path().equals("/users"))
            .allMatch(e -> e.type() == ApiType.REST);
    }

    @Test
    void scan_withLegacyRouteDecorator_extractsEndpoints() throws IOException {
        // Given: Flask app using legacy route decorator with methods
        createFile("app/routes.py", """
            from flask import Blueprint

            bp = Blueprint('api', __name__)

            @bp.route('/products', methods=['GET'])
            def get_products():
                return []

            @bp.route('/products', methods=['POST', 'PUT'])
            def modify_product():
                return {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 3 endpoints (GET, POST, PUT)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT");
    }

    @Test
    void scan_withPathParameters_extractsParameters() throws IOException {
        // Given: Flask route with path parameters
        createFile("app/users.py", """
            from flask import Flask

            app = Flask(__name__)

            @app.get('/users/<int:user_id>')
            def get_user(user_id):
                return {'id': user_id}

            @app.put('/users/<user_id>/items/<item_id>')
            def update_item(user_id, item_id):
                return {}
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract path parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint first = result.apiEndpoints().get(0);
        assertThat(first.path()).isEqualTo("/users/<int:user_id>");
        assertThat(first.requestSchema()).contains("user_id");

        ApiEndpoint second = result.apiEndpoints().get(1);
        assertThat(second.path()).contains("<user_id>");
        assertThat(second.path()).contains("<item_id>");
    }

    @Test
    void scan_withSimpleRoute_defaultsToGet() throws IOException {
        // Given: Simple route without methods specified
        createFile("app/simple.py", """
            from flask import Flask

            app = Flask(__name__)

            @app.route('/about')
            def about():
                return 'About page'
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should default to GET method
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);

        ApiEndpoint endpoint = result.apiEndpoints().get(0);
        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.path()).isEqualTo("/about");
    }

    @Test
    void scan_withNoFlaskCode_returnsEmpty() throws IOException {
        // Given: Python file without Flask routes
        createFile("app/utils.py", """
            def helper_function():
                return "not a Flask route"
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
}
