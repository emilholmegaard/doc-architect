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
 * Functional tests for {@link JaxRsApiScanner}.
 *
 * <p>These tests validate the scanner's ability to:
 * <ul>
 *   <li>Parse JAX-RS resources using JavaParser AST</li>
 *   <li>Extract REST API endpoints from JAX-RS annotations (@Path, @GET, @POST, etc.)</li>
 *   <li>Handle different HTTP methods (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS)</li>
 *   <li>Combine class-level and method-level @Path annotations</li>
 *   <li>Extract content types from @Produces and @Consumes</li>
 *   <li>Extract request parameters (@PathParam, @QueryParam, @FormParam)</li>
 *   <li>Support both jakarta.ws.rs.* and javax.ws.rs.* packages</li>
 *   <li>Handle interface-based JAX-RS resources (common pattern)</li>
 *   <li>Pre-filter files to only scan those with JAX-RS patterns</li>
 * </ul>
 *
 * @see JaxRsApiScanner
 * @since 1.0.0
 */
class JaxRsApiScannerTest extends ScannerTestBase {

    private JaxRsApiScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new JaxRsApiScanner();
    }

    @Test
    void scan_withSimpleJakartaResource_extractsEndpoints() throws IOException {
        // Given: A simple JAX-RS resource with @Path and @GET/@POST
        createFile("src/main/java/com/example/UserResource.java", """
            package com.example;

            import jakarta.ws.rs.*;
            import jakarta.ws.rs.core.MediaType;
            import java.util.List;

            @Path("/api/users")
            @Produces(MediaType.APPLICATION_JSON)
            @Consumes(MediaType.APPLICATION_JSON)
            public class UserResource {

                @GET
                public List<User> getAllUsers() {
                    return List.of();
                }

                @POST
                public User createUser(User user) {
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
        assertThat(getEndpoint.componentId()).isEqualTo("com.example.UserResource");
        assertThat(getEndpoint.responseSchema()).contains("List<User>");
        assertThat(getEndpoint.responseSchema()).contains("application/json");

        ApiEndpoint postEndpoint = result.apiEndpoints().stream()
            .filter(e -> "POST".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(postEndpoint.type()).isEqualTo(ApiType.REST);
        assertThat(postEndpoint.path()).isEqualTo("/api/users");
        assertThat(postEndpoint.method()).isEqualTo("POST");
    }

    @Test
    void scan_withJavaxResource_extractsEndpoints() throws IOException {
        // Given: Legacy JAX-RS resource using javax.ws.rs package
        createFile("src/main/java/com/example/ProductResource.java", """
            package com.example;

            import javax.ws.rs.*;

            @Path("/api/products")
            public class ProductResource {

                @GET
                @Path("/{id}")
                public Product getProduct(@PathParam("id") Long id) {
                    return null;
                }

                @DELETE
                @Path("/{id}")
                public void deleteProduct(@PathParam("id") Long id) {
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

        assertThat(getEndpoint.path()).isEqualTo("/api/products/id");
        assertThat(getEndpoint.method()).isEqualTo("GET");
        assertThat(getEndpoint.requestSchema()).isEqualTo("PathParam:id:Long");

        ApiEndpoint deleteEndpoint = result.apiEndpoints().stream()
            .filter(e -> "DELETE".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(deleteEndpoint.path()).isEqualTo("/api/products/id");
        assertThat(deleteEndpoint.method()).isEqualTo("DELETE");
    }

    @Test
    void scan_withInterfaceResource_extractsEndpoints() throws IOException {
        // Given: Interface-based JAX-RS resource (common in Keycloak)
        createFile("src/main/java/org/keycloak/admin/client/resource/UsersResource.java", """
            package org.keycloak.admin.client.resource;

            import jakarta.ws.rs.*;
            import jakarta.ws.rs.core.MediaType;
            import jakarta.ws.rs.core.Response;
            import java.util.List;

            @Path("/admin/realms/{realm}/users")
            @Produces(MediaType.APPLICATION_JSON)
            @Consumes(MediaType.APPLICATION_JSON)
            public interface UsersResource {

                @GET
                List<UserRepresentation> list(@QueryParam("search") String search);

                @POST
                Response create(UserRepresentation user);

                @Path("{id}")
                @GET
                UserRepresentation get(@PathParam("id") String id);

                @Path("{id}")
                @PUT
                Response update(@PathParam("id") String id, UserRepresentation user);

                @Path("{id}")
                @DELETE
                Response delete(@PathParam("id") String id);
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract 5 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);

        // Verify path combinations
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "/admin/realms/realm/users",
                "/admin/realms/realm/users",
                "/admin/realms/realm/users/id",
                "/admin/realms/realm/users/id",
                "/admin/realms/realm/users/id"
            );

        // Verify HTTP methods
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "GET", "PUT", "DELETE");

        // Verify query parameter detection
        ApiEndpoint listEndpoint = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()) && "/admin/realms/realm/users".equals(e.path()))
            .findFirst()
            .orElseThrow();

        assertThat(listEndpoint.requestSchema()).contains("QueryParam:search:String");
    }

    @Test
    void scan_withAllHttpMethods_extractsEndpoints() throws IOException {
        // Given: Resource with all HTTP method annotations
        createFile("src/main/java/com/example/TestResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/api/test")
            public class TestResource {

                @GET
                public String doGet() { return "GET"; }

                @POST
                public String doPost() { return "POST"; }

                @PUT
                public String doPut() { return "PUT"; }

                @DELETE
                public String doDelete() { return "DELETE"; }

                @PATCH
                public String doPatch() { return "PATCH"; }

                @HEAD
                public void doHead() { }

                @OPTIONS
                public String doOptions() { return "OPTIONS"; }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract 7 endpoints (one for each HTTP method)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(7);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    }

    @Test
    void scan_withProducesAndConsumes_extractsContentTypes() throws IOException {
        // Given: Resource with method-level @Produces and @Consumes
        createFile("src/main/java/com/example/ContentTypeResource.java", """
            package com.example;

            import jakarta.ws.rs.*;
            import jakarta.ws.rs.core.MediaType;

            @Path("/api/content")
            public class ContentTypeResource {

                @GET
                @Produces(MediaType.APPLICATION_JSON)
                public String getJson() {
                    return "json";
                }

                @POST
                @Consumes(MediaType.APPLICATION_XML)
                @Produces(MediaType.TEXT_PLAIN)
                public String postXml(String data) {
                    return "ok";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract content types
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint getEndpoint = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(getEndpoint.responseSchema()).contains("application/json");

        ApiEndpoint postEndpoint = result.apiEndpoints().stream()
            .filter(e -> "POST".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(postEndpoint.requestSchema()).contains("application/xml");
        assertThat(postEndpoint.responseSchema()).contains("text/plain");
    }

    @Test
    void scan_withMultipleParameters_extractsAllParameters() throws IOException {
        // Given: Resource with multiple parameter types
        createFile("src/main/java/com/example/ParamResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/api/params")
            public class ParamResource {

                @GET
                @Path("/{id}")
                public String getWithParams(
                    @PathParam("id") Long id,
                    @QueryParam("filter") String filter,
                    @QueryParam("page") int page,
                    @HeaderParam("Authorization") String auth
                ) {
                    return "result";
                }

                @POST
                public String createWithForm(
                    @FormParam("email") String email,
                    @FormParam("password") String password
                ) {
                    return "created";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all parameters
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        ApiEndpoint getEndpoint = result.apiEndpoints().stream()
            .filter(e -> "GET".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(getEndpoint.requestSchema()).contains("PathParam:id:Long");
        assertThat(getEndpoint.requestSchema()).contains("QueryParam:filter:String");
        assertThat(getEndpoint.requestSchema()).contains("QueryParam:page:int");
        assertThat(getEndpoint.requestSchema()).contains("HeaderParam:auth:String");

        ApiEndpoint postEndpoint = result.apiEndpoints().stream()
            .filter(e -> "POST".equals(e.method()))
            .findFirst()
            .orElseThrow();

        assertThat(postEndpoint.requestSchema()).contains("FormParam:email:String");
        assertThat(postEndpoint.requestSchema()).contains("FormParam:password:String");
    }

    // Tests for shouldScanFile() pre-filtering logic

    @Test
    void scan_withFilenameConvention_scansResourceFiles() throws IOException {
        // Given: File named *Resource.java with JAX-RS annotations
        createFile("src/main/java/com/example/ApiResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/api")
            public class ApiResource {

                @GET
                public String test() {
                    return "test";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should scan file and detect endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api");
    }

    @Test
    void scan_withEndpointNamingConvention_scansEndpointFiles() throws IOException {
        // Given: File named *Endpoint.java with JAX-RS annotations
        createFile("src/main/java/com/example/UserEndpoint.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/users")
            public class UserEndpoint {

                @GET
                public String list() {
                    return "users";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should scan file and detect endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/users");
    }

    @Test
    void scan_withJakartaImport_detectsResource() throws IOException {
        // Given: Resource with jakarta.ws.rs import
        createFile("src/main/java/com/example/JakartaResource.java", """
            package com.example;

            import jakarta.ws.rs.GET;
            import jakarta.ws.rs.Path;

            @Path("/jakarta")
            public class JakartaResource {

                @GET
                public String test() {
                    return "jakarta";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect the endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/jakarta");
    }

    @Test
    void scan_withJavaxImport_detectsResource() throws IOException {
        // Given: Resource with javax.ws.rs import (legacy)
        createFile("src/main/java/com/example/JavaxResource.java", """
            package com.example;

            import javax.ws.rs.GET;
            import javax.ws.rs.Path;

            @Path("/javax")
            public class JavaxResource {

                @GET
                public String test() {
                    return "javax";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect the endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/javax");
    }

    @Test
    void scan_withWildcardImport_detectsResource() throws IOException {
        // Given: Resource with wildcard import (import jakarta.ws.rs.*)
        createFile("src/main/java/com/example/WildcardResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/wildcard")
            public class WildcardResource {

                @GET
                public String test() {
                    return "wildcard";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect the endpoint
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/wildcard");
    }

    @Test
    void scan_withNoJaxRsPatterns_skipsFile() throws IOException {
        // Given: Regular Java class with no JAX-RS patterns
        createFile("src/main/java/com/example/RegularService.java", """
            package com.example;

            public class RegularService {
                public String doSomething() {
                    return "nothing";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not detect any endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withTestFileContainingJaxRsPatterns_detectsEndpoints() throws IOException {
        // Given: Test file with JAX-RS patterns
        createFile("src/test/java/com/example/TestResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/test")
            public class TestResource {

                @GET
                public String test() {
                    return "test";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect the endpoint even in test directory
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/test");
    }

    @Test
    void scan_withTestFileWithoutJaxRsPatterns_skipsFile() throws IOException {
        // Given: Test file without JAX-RS patterns
        createFile("src/test/java/com/example/RegularTest.java", """
            package com.example;

            import org.junit.jupiter.api.Test;

            public class RegularTest {
                @Test
                public void testSomething() {
                    // test code
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should not detect any endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_withClassLevelPathOnly_detectsEndpoints() throws IOException {
        // Given: Resource with @Path only on class level
        createFile("src/main/java/com/example/SimpleResource.java", """
            package com.example;

            import jakarta.ws.rs.*;

            @Path("/simple")
            public class SimpleResource {

                @GET
                public String get() {
                    return "get";
                }

                @POST
                public String post() {
                    return "post";
                }
            }
            """);

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect endpoints with class-level path
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.apiEndpoints()).allMatch(e -> "/simple".equals(e.path()));
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
