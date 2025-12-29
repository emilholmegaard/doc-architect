package com.docarchitect.core.scanner.impl.ruby;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RailsRouteScanner}.
 */
class RailsRouteScannerTest extends ScannerTestBase {

    private RailsRouteScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new RailsRouteScanner();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(scanner.getId()).isEqualTo("rails-route");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(scanner.getDisplayName()).isEqualTo("Rails Route Scanner");
    }

    @Test
    void getSupportedLanguages_includesRuby() {
        assertThat(scanner.getSupportedLanguages()).contains("ruby");
    }

    @Test
    void getSupportedFilePatterns_includesRoutesFiles() {
        Set<String> patterns = scanner.getSupportedFilePatterns();
        assertThat(patterns).contains("**/routes.rb", "**/routes/*.rb");
    }

    @Test
    void getPriority_returns50() {
        assertThat(scanner.getPriority()).isEqualTo(50);
    }

    @Test
    void appliesTo_returnsTrueWhenRoutesFileExists() throws IOException {
        createFile("config/routes.rb", "Rails.application.routes.draw do\nend\n");

        assertThat(scanner.appliesTo(context)).isTrue();
    }

    @Test
    void appliesTo_returnsFalseWhenNoRoutesFile() {
        assertThat(scanner.appliesTo(context)).isFalse();
    }

    @Test
    void scan_extractsResourcefulRoutes() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              resources :users
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // Should generate 7 RESTful routes
        assertThat(endpoints).hasSize(7);

        // Verify all standard RESTful routes
        assertThat(endpoints).extracting(ApiEndpoint::method)
            .containsExactlyInAnyOrder("GET", "GET", "POST", "GET", "GET", "PATCH", "DELETE");

        assertThat(endpoints).extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "/users",           // index
                "/users/new",       // new
                "/users",           // create
                "/users/:id",       // show
                "/users/:id/edit",  // edit
                "/users/:id",       // update
                "/users/:id"        // destroy
            );

        assertThat(endpoints).allMatch(e -> e.type() == ApiType.REST);
        assertThat(endpoints).allMatch(e -> e.description() != null && e.description().contains("users#"));
    }

    @Test
    void scan_extractsSingularResourceRoutes() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              resource :profile
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // Should generate 6 routes (no index for singular resource)
        assertThat(endpoints).hasSize(6);

        assertThat(endpoints).extracting(ApiEndpoint::path)
            .containsExactlyInAnyOrder(
                "/profile/new",   // new
                "/profile",       // create
                "/profile",       // show
                "/profile/edit",  // edit
                "/profile",       // update
                "/profile"        // destroy
            );
    }

    @Test
    void scan_extractsCustomGetRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              get '/custom', to: 'custom#action'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        ApiEndpoint endpoint = endpoints.get(0);

        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.path()).isEqualTo("/custom");
        assertThat(endpoint.description()).isEqualTo("custom#action");
        assertThat(endpoint.type()).isEqualTo(ApiType.REST);
    }

    @Test
    void scan_extractsCustomPostRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              post '/submit', to: 'forms#submit'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        ApiEndpoint endpoint = endpoints.get(0);

        assertThat(endpoint.method()).isEqualTo("POST");
        assertThat(endpoint.path()).isEqualTo("/submit");
        assertThat(endpoint.description()).isEqualTo("forms#submit");
    }

    @Test
    void scan_extractsCustomPutRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              put '/update', to: 'items#update'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).method()).isEqualTo("PUT");
    }

    @Test
    void scan_extractsCustomPatchRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              patch '/modify', to: 'items#modify'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).method()).isEqualTo("PATCH");
    }

    @Test
    void scan_extractsCustomDeleteRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              delete '/remove', to: 'items#destroy'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).method()).isEqualTo("DELETE");
    }

    @Test
    void scan_extractsMatchRoute() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              match '/search', to: 'search#query', via: :post
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        ApiEndpoint endpoint = endpoints.get(0);

        assertThat(endpoint.method()).isEqualTo("POST");
        assertThat(endpoint.path()).isEqualTo("/search");
        assertThat(endpoint.description()).isEqualTo("search#query");
    }

    @Test
    void scan_handlesNamespacedRoutes() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              namespace :api do
                resources :posts
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(7);

        // All paths should be prefixed with /api
        assertThat(endpoints).allMatch(e -> e.path().startsWith("/api/posts"));

        // All controller actions should be prefixed with api/
        assertThat(endpoints).allMatch(e -> e.description().startsWith("api/posts#"));
    }

    @Test
    void scan_handlesNestedNamespaces() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              namespace :api do
                namespace :v1 do
                  resources :users
                end
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(7);

        // All paths should be prefixed with /api/v1
        assertThat(endpoints).allMatch(e -> e.path().startsWith("/api/v1/users"));

        // All controller actions should be prefixed with api/v1/
        assertThat(endpoints).allMatch(e -> e.description().startsWith("api/v1/users#"));
    }

    @Test
    void scan_handlesScopeWithPath() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              scope '/admin' do
                resources :settings
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(7);

        // All paths should be prefixed with /admin
        assertThat(endpoints).allMatch(e -> e.path().startsWith("/admin/settings"));
    }

    @Test
    void scan_handlesScopeWithModule() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              scope '/admin', module: 'admin' do
                resources :users
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(7);

        // All paths should be prefixed with /admin
        assertThat(endpoints).allMatch(e -> e.path().startsWith("/admin/users"));

        // All controller actions should be prefixed with admin/
        assertThat(endpoints).allMatch(e -> e.description().startsWith("admin/users#"));
    }

    @Test
    void scan_handlesMultipleResources() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              resources :users
              resources :posts
              resources :comments
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // 3 resources × 7 routes each = 21 total
        assertThat(endpoints).hasSize(21);

        // Verify each resource has routes
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/users"));
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/posts"));
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/comments"));
    }

    @Test
    void scan_handlesMixedRoutingPatterns() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              resources :users
              get '/about', to: 'pages#about'
              post '/contact', to: 'pages#contact'

              namespace :api do
                resources :posts
                get '/status', to: 'health#status'
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // 7 (users) + 2 (custom) + 7 (api/posts) + 1 (api/status) = 17
        assertThat(endpoints).hasSize(17);

        // Verify custom routes
        assertThat(endpoints).anyMatch(e ->
            e.method().equals("GET") && e.path().equals("/about"));
        assertThat(endpoints).anyMatch(e ->
            e.method().equals("POST") && e.path().equals("/contact"));
        assertThat(endpoints).anyMatch(e ->
            e.method().equals("GET") && e.path().equals("/api/status"));
    }

    @Test
    void scan_ignoresComments() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              # This is a comment about users
              resources :users

              # get '/disabled', to: 'disabled#action'

              get '/active', to: 'active#action'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // 7 (users) + 1 (active) = 8, commented route should not be included
        assertThat(endpoints).hasSize(8);
        assertThat(endpoints).noneMatch(e -> e.path().equals("/disabled"));
    }

    @Test
    void scan_handlesEmptyRoutesFile() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }

    @Test
    void scan_handlesCustomRouteWithoutController() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              get '/simple'
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        assertThat(endpoints).hasSize(1);
        ApiEndpoint endpoint = endpoints.get(0);

        assertThat(endpoint.method()).isEqualTo("GET");
        assertThat(endpoint.path()).isEqualTo("/simple");
        assertThat(endpoint.description()).isEqualTo("unknown");
    }

    @Test
    void scan_handlesComplexGitLabStyleRoutes() throws IOException {
        String routesContent = """
            Rails.application.routes.draw do
              namespace :api do
                namespace :v4 do
                  resources :projects
                  resources :issues
                  resources :merge_requests
                  get '/version', to: 'version#show'
                end
              end

              resources :users
              resource :profile

              scope '/admin' do
                resources :settings
              end
            end
            """;

        createFile("config/routes.rb", routesContent);

        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        List<ApiEndpoint> endpoints = result.apiEndpoints();

        // 3 resources in api/v4 (7 each) + 1 custom + 7 users + 6 profile + 7 admin/settings
        // = 21 + 1 + 7 + 6 + 7 = 42
        assertThat(endpoints).hasSize(42);

        // Verify API v4 routes
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/api/v4/projects"));
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/api/v4/issues"));
        assertThat(endpoints).anyMatch(e -> e.path().startsWith("/api/v4/merge_requests"));
        assertThat(endpoints).anyMatch(e -> e.path().equals("/api/v4/version"));
    }

    @Test
    void scan_returnsEmptyResultWhenNoRouteFiles() {
        ScanResult result = scanner.scan(context);

        assertThat(result.success()).isTrue();
        assertThat(result.apiEndpoints()).isEmpty();
    }
}
