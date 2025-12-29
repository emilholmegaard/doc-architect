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

    @Test
    void scan_aspNetCoreWithMultipleControllers_detectsAllEndpoints() throws IOException {
        // Given: Multiple controllers with multiple endpoints
        createFile("Controllers/OrderController.cs", """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/orders")]
public class OrderController : ControllerBase
{
    [HttpGet]
    public IActionResult GetAll()
    {
        return Ok();
    }

    [HttpPost]
    public IActionResult Create([FromBody] OrderDto order)
    {
        return Created("", order);
    }
}
""");

        createFile("Controllers/CatalogController.cs", """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/catalog")]
public class CatalogController : ControllerBase
{
    [HttpGet("{id}")]
    public IActionResult GetById(int id)
    {
        return Ok();
    }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all 3 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints())
            .hasSize(3)
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/api/orders", "/api/orders", "/api/catalog/{id}");

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "GET");
    }

    @Test
    void scan_withVariedFormattingAndMultipleVerbs_detectsAllEndpoints() throws IOException {
        // Given: Controllers with varied formatting (blank lines between attributes and methods)
        createFile("Controllers/ProductController.cs", """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/products")]
public class ProductController : ControllerBase
{
    [HttpGet]
    public IActionResult GetAll() => Ok();

    [HttpGet("{id}")]

    public IActionResult GetById(int id) => Ok();

    [HttpPost]
    public IActionResult Create([FromBody] ProductDto product) => Created("", product);

    [HttpPut("{id}")]
    public IActionResult Update(int id, [FromBody] ProductDto product) => Ok();

    [HttpDelete("{id}")]
    public IActionResult Delete(int id) => NoContent();

    [HttpPatch("{id}")]
    public IActionResult Patch(int id, [FromBody] JsonPatchDocument<ProductDto> patch) => Ok();
}
""");

        createFile("Controllers/CustomerController.cs", """
using Microsoft.AspNetCore.Mvc;

[Route("api/customers")]
public class CustomerController
{
    [HttpGet]
    public IActionResult List() => Ok();

    [HttpGet("{id}")]
    public IActionResult GetCustomer(int id) => Ok();

    [HttpPost]
    public IActionResult CreateCustomer([FromBody] CustomerDto customer) => Created("", customer);
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all 9 endpoints (6 from ProductController + 3 from CustomerController)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(9);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "GET", "POST", "PUT", "DELETE", "PATCH", "GET", "GET", "POST");
    }

    @Test
    void scan_withAccessModifiersAndAttributes_detectsAllEndpoints() throws IOException {
        // Given: Controllers with various access modifiers and multiple attribute formats
        createFile("Controllers/WeatherController.cs", """
using Microsoft.AspNetCore.Mvc;
using System;

namespace MyApp.Controllers
{
    [ApiController]
    [Route("[controller]")]
    public class WeatherController : ControllerBase
    {
        private readonly ILogger _logger;

        [HttpGet]
        [ProducesResponseType(200)]
        public IActionResult Get()
        {
            return Ok();
        }

        [HttpGet("{id:int}")]
        [ProducesResponseType(200)]
        [ProducesResponseType(404)]
        public IActionResult GetById(int id)
        {
            return Ok();
        }

        [HttpPost]
        [Authorize]
        public async Task<IActionResult> Post([FromBody] WeatherData data)
        {
            return Created("", data);
        }
    }
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all 3 endpoints
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "GET", "POST");

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/weather", "/weather/{id:int}", "/weather");
    }

    @Test
    void scan_withVirtualAndOverrideModifiers_detectsAllEndpoints() throws IOException {
        // Given: Controller with virtual, override, and static modifiers
        createFile("Controllers/BaseController.cs", """
using Microsoft.AspNetCore.Mvc;

[Route("api/base")]
public class BaseController : ControllerBase
{
    [HttpGet("virtual")]
    public virtual IActionResult GetVirtual() => Ok();

    [HttpGet("override")]
    public override string ToString() => "base";

    [HttpPost("static")]
    public static async Task<IActionResult> PostStatic([FromBody] object data) => Ok();
}
""");

        // When: Scanner is executed
        ScanResult result = scanner.scan(context);

        // Then: Should detect all 3 endpoints (virtual, override, static async)
        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "GET", "POST");
    }

    // Tests for shouldScanFile() pre-filtering logic

    @Test
    void scan_withFilenameConvention_scansControllerFiles() throws IOException {
        createFile("src/Controllers/UsersController.cs", """
            using Microsoft.AspNetCore.Mvc;

            [ApiController]
            [Route("api/[controller]")]
            public class UsersController : ControllerBase
            {
                [HttpGet]
                public IActionResult GetAll() => Ok();
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api/users");
    }

    @Test
    void scan_withNoAspNetPatterns_skipsFile() throws IOException {
        createFile("src/Models/User.cs", """
            namespace MyApp.Models
            {
                public class User
                {
                    public int Id { get; set; }
                    public string Name { get; set; }
                }
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.apiEndpoints()).isEmpty();
    }

    // ========== Minimal API Tests (ASP.NET Core 6+) ==========

    @Test
    void scan_withMinimalApiMapGet_detectsEndpoint() throws IOException {
        createFile("Program.cs", """
using Microsoft.AspNetCore.Builder;

var app = WebApplication.Create();

app.MapGet("/api/products", () => {
    return Results.Ok(new[] { "Product1", "Product2" });
});

app.Run();
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/api/products");
        assertThat(result.apiEndpoints().get(0).method()).isEqualTo("GET");
    }

    @Test
    void scan_withMinimalApiMultipleEndpoints_detectsAll() throws IOException {
        createFile("Endpoints/ProductEndpoints.cs", """
using Microsoft.AspNetCore.Builder;

public static class ProductEndpoints
{
    public static void MapProductEndpoints(this WebApplication app)
    {
        app.MapGet("/api/products", () => Results.Ok());
        app.MapPost("/api/products", (Product p) => Results.Created("", p));
        app.MapPut("/api/products/{id}", (int id, Product p) => Results.Ok());
        app.MapDelete("/api/products/{id}", (int id) => Results.NoContent());
        app.MapPatch("/api/products/{id}", (int id) => Results.Ok());
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH");
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "/api/products",
                "/api/products",
                "/api/products/{id}",
                "/api/products/{id}",
                "/api/products/{id}"
            );
    }

    @Test
    void scan_withMinimalApiWithParameters_extractsParameters() throws IOException {
        createFile("Program.cs", """
using Microsoft.AspNetCore.Builder;

var app = WebApplication.Create();

app.MapPost("/api/users", (User user) => Results.Created("", user));
app.MapGet("/api/users/{id}", (int id) => Results.Ok());

app.Run();
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        // POST endpoint should have Body parameter
        ApiEndpoint postEndpoint = result.apiEndpoints().stream()
            .filter(e -> e.method().equals("POST"))
            .findFirst()
            .orElseThrow();
        assertThat(postEndpoint.requestSchema()).contains("Body: user: User");

        // GET endpoint should have Route parameter
        ApiEndpoint getEndpoint = result.apiEndpoints().stream()
            .filter(e -> e.method().equals("GET"))
            .findFirst()
            .orElseThrow();
        assertThat(getEndpoint.requestSchema()).contains("Route: id: int");
    }

    @Test
    void scan_withMinimalApiRouteGroups_detectsEndpoints() throws IOException {
        createFile("Program.cs", """
using Microsoft.AspNetCore.Builder;

var app = WebApplication.Create();
var group = app.MapGroup("/api/v1");

group.MapGet("/products", () => Results.Ok());
group.MapPost("/products", (Product p) => Results.Created("", p));

app.Run();
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder("/products", "/products");
    }

    // ========== Razor Pages Tests ==========

    @Test
    void scan_withRazorPageOnGetOnPost_detectsHandlers() throws IOException {
        createFile("Pages/Products/Index.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;

namespace MyApp.Pages.Products
{
    public class IndexModel : PageModel
    {
        public void OnGet()
        {
        }

        public void OnPost()
        {
        }
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");

        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .allMatch(path -> path.equals("/Products"));
    }

    @Test
    void scan_withRazorPageAsyncHandlers_detectsEndpoints() throws IOException {
        createFile("Pages/Products/Details.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;
using System.Threading.Tasks;

namespace MyApp.Pages.Products
{
    public class DetailsModel : PageModel
    {
        public async Task OnGetAsync(int id)
        {
        }

        public async Task OnPostAsync(int id)
        {
        }
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST");
    }

    @Test
    void scan_withRazorPageIndexPage_derivesRootRoute() throws IOException {
        createFile("Pages/Index.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;

public class IndexModel : PageModel
{
    public void OnGet() { }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(1);
        assertThat(result.apiEndpoints().get(0).path()).isEqualTo("/");
    }

    @Test
    void scan_withRazorPageNestedDirectory_derivesCorrectRoute() throws IOException {
        createFile("Pages/Admin/Users/Edit.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;

namespace MyApp.Pages.Admin.Users
{
    public class EditModel : PageModel
    {
        public void OnGet(int id) { }
        public void OnPost(int id) { }
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(2);
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::path)
            .allMatch(path -> path.equals("/Admin/Users/Edit"));
    }

    @Test
    void scan_withRazorPageAllHttpMethods_detectsAll() throws IOException {
        createFile("Pages/Products/Manage.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;
using System.Threading.Tasks;

namespace MyApp.Pages.Products
{
    public class ManageModel : PageModel
    {
        public void OnGet() { }
        public void OnPost() { }
        public void OnPut() { }
        public void OnDelete() { }
        public void OnPatch() { }
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(5);
        assertThat(result.apiEndpoints())
            .extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "POST", "PUT", "DELETE", "PATCH");
    }

    // ========== Integration Tests: All Three Patterns Together ==========

    @Test
    void scan_withMixedPatterns_detectsAllEndpoints() throws IOException {
        // MVC Controller
        createFile("Controllers/ApiController.cs", """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/data")]
public class ApiController : ControllerBase
{
    [HttpGet]
    public IActionResult Get() => Ok();
}
""");

        // Minimal API
        createFile("Program.cs", """
var app = WebApplication.Create();
app.MapGet("/api/status", () => Results.Ok());
app.Run();
""");

        // Razor Page
        createFile("Pages/Index.cshtml.cs", """
using Microsoft.AspNetCore.Mvc.RazorPages;
public class IndexModel : PageModel
{
    public void OnGet() { }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).hasSize(3);

        // Verify MVC Controller endpoint
        assertThat(result.apiEndpoints())
            .filteredOn(e -> e.path().equals("/api/data"))
            .hasSize(1);

        // Verify Minimal API endpoint
        assertThat(result.apiEndpoints())
            .filteredOn(e -> e.path().equals("/api/status"))
            .hasSize(1);

        // Verify Razor Page endpoint
        assertThat(result.apiEndpoints())
            .filteredOn(e -> e.path().equals("/"))
            .hasSize(1);
    }
}
