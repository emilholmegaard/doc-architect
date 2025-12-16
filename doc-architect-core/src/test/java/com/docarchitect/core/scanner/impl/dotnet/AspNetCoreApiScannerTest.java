package com.docarchitect.core.scanner.impl.dotnet;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional tests for {@link AspNetCoreApiScanner}.
 */
class AspNetCoreApiScannerTest extends ScannerTestBase {

    private AspNetCoreApiScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new AspNetCoreApiScanner();
    }

    @Test
    void scan_withHttpMethodAttributes_extractsEndpoints() throws IOException {
        // Given: ASP.NET Core controller with HTTP method attributes
        createFile("Controllers/UserController.cs", """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/[controller]")]
public class UserController : ControllerBase
{
    [HttpGet]
    public IActionResult GetAll()
    {
        return Ok();
    }

    [HttpGet("{id}")]
    public IActionResult GetById(int id)
    {
        return Ok();
    }

    [HttpPost]
    public IActionResult Create([FromBody] User user)
    {
        return Created("", user);
    }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract all 3 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "GET", "POST");
    }

    @Test
    void scan_withRouteTemplates_extractsPaths() throws IOException {
        // Given: Controller with route templates
        createFile("Controllers/ProductController.cs", """
using Microsoft.AspNetCore.Mvc;

[Route("api/products")]
public class ProductController
{
    [HttpGet]
    public IActionResult List() => Ok();

    [HttpGet("{id:int}")]
    public IActionResult GetById(int id) => Ok();
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should extract endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
    }

    @Test
    void scan_withNoCSharpFiles_returnsEmpty() throws IOException {
        // Given: No C# files in project
        createDirectory("src/main/java");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should return empty result
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void appliesTo_withCSharpFiles_returnsTrue() throws IOException {
        // Given: Project with C# files
        createFile("Controllers/Test.cs", "public class Test { }");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return true
        assertThat(applies).isTrue();
    }

    @Test
    void appliesTo_withoutCSharpFiles_returnsFalse() throws IOException {
        // Given: Project without C# files
        createDirectory("src/main/java");

        // When: appliesTo is checked
        boolean applies = scanner.appliesTo(context);

        // Then: Should return false
        assertThat(applies).isFalse();
    }
}
