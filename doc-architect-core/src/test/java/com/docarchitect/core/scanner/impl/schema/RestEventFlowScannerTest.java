package com.docarchitect.core.scanner.impl.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ApiType;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ScannerTestBase;

/**
 * Comprehensive tests for {@link RestEventFlowScanner}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Event endpoint detection via URL patterns</li>
 *   <li>Event endpoint detection via past-tense verbs</li>
 *   <li>Event endpoint detection via schema types</li>
 *   <li>RESTful CRUD pattern detection</li>
 *   <li>False positive avoidance</li>
 *   <li>Configuration options</li>
 * </ul>
 */
class RestEventFlowScannerTest extends ScannerTestBase {

    private RestEventFlowScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new RestEventFlowScanner();
    }

    @Test
    void shouldHaveCorrectMetadata() {
        assertThat(scanner.getId()).isEqualTo("rest-event-flow");
        assertThat(scanner.getDisplayName()).isEqualTo("REST Event Flow Scanner");
        assertThat(scanner.getPriority()).isEqualTo(150); // Runs after API scanners
        assertThat(scanner.getSupportedFilePatterns()).isEmpty(); // Post-processor
    }

    @Test
    void shouldNotApplyWhenNoApiEndpointsExist() {
        // Empty previous results
        ScanContext emptyContext = new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of(),
            Map.of(),
            Map.of()
        );

        assertThat(scanner.appliesTo(emptyContext)).isFalse();
    }

    @Test
    void shouldApplyWhenApiEndpointsExist() {
        // Create mock previous results with API endpoints
        ApiEndpoint mockEndpoint = new ApiEndpoint(
            "test-component",
            ApiType.REST,
            "/api/test",
            "GET",
            null,
            null,
            null,
            null
        );

        ScanResult mockApiScanResult = new ScanResult(
            "mock-api-scanner",
            true,
            List.of(),
            List.of(),
            List.of(mockEndpoint),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScanStatistics.empty()
        );

        ScanContext contextWithApis = new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of(),
            Map.of(),
            Map.of("mock-api-scanner", mockApiScanResult)
        );

        assertThat(scanner.appliesTo(contextWithApis)).isTrue();
    }

    // ========== Event URL Pattern Detection Tests ==========

    @Test
    void shouldDetectEventEndpoint_whenUrlContainsEvents() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "order-service",
            ApiType.REST,
            "/api/events/order-created",
            "POST",
            "Order creation event",
            "OrderCreatedEvent",
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.success()).isTrue();
        assertThat(result.messageFlows()).hasSize(1);

        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isNull(); // Unknown publisher
        assertThat(flow.subscriberComponentId()).isEqualTo("order-service");
        assertThat(flow.topic()).isEqualTo("/api/events/order-created");
        assertThat(flow.messageType()).isEqualTo("OrderCreatedEvent");
        assertThat(flow.broker()).isEqualTo("rest-event");
    }

    @Test
    void shouldDetectEventEndpoint_whenUrlContainsDomainEvents() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "user-service",
            ApiType.REST,
            "/domain-events/user-registered",
            "POST",
            null,
            "UserRegisteredEvent",
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("/domain-events/user-registered");
    }

    @Test
    void shouldDetectEventEndpoint_whenUrlContainsWebhooks() {
        ApiEndpoint webhookEndpoint = new ApiEndpoint(
            "payment-service",
            ApiType.REST,
            "/webhooks/payment-completed",
            "POST",
            null,
            "PaymentWebhook",
            null,
            null
        );

        ScanContext contextWithWebhook = createContextWithEndpoints(List.of(webhookEndpoint));
        ScanResult result = scanner.scan(contextWithWebhook);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("/webhooks/payment-completed");
    }

    @Test
    void shouldDetectEventEndpoint_whenUrlContainsNotifications() {
        ApiEndpoint notificationEndpoint = new ApiEndpoint(
            "notification-service",
            ApiType.REST,
            "/api/notifications/email-sent",
            "POST",
            null,
            "EmailNotification",
            null,
            null
        );

        ScanContext contextWithNotification = createContextWithEndpoints(List.of(notificationEndpoint));
        ScanResult result = scanner.scan(contextWithNotification);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("/api/notifications/email-sent");
    }

    // ========== Past-Tense Verb Detection Tests ==========

    @Test
    void shouldDetectEventEndpoint_whenPostWithPastTenseCreated() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "inventory-service",
            ApiType.REST,
            "/api/inventory-reserved",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.topic()).isEqualTo("/api/inventory-reserved");
        assertThat(flow.messageType()).isEqualTo("InventoryReservedEvent");
    }

    @Test
    void shouldDetectEventEndpoint_whenPostWithPastTenseUpdated() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "profile-service",
            ApiType.REST,
            "/api/profile-updated",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("ProfileUpdatedEvent");
    }

    @Test
    void shouldDetectEventEndpoint_whenPostWithPastTenseDeleted() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "catalog-service",
            ApiType.REST,
            "/products/product-deleted",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("ProductDeletedEvent");
    }

    @Test
    void shouldDetectEventEndpoint_whenPostWithPastTenseCompleted() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "checkout-service",
            ApiType.REST,
            "/checkout-completed",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("CheckoutCompletedEvent");
    }

    @Test
    void shouldDetectEventEndpoint_whenPostWithPastTenseRegistered() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "auth-service",
            ApiType.REST,
            "/user-registered",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("UserRegisteredEvent");
    }

    // ========== Schema Type Detection Tests ==========

    @Test
    void shouldDetectEventEndpoint_whenRequestSchemaEndsWithEvent() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "billing-service",
            ApiType.REST,
            "/api/billing/process",
            "POST",
            null,
            "InvoiceCreatedEvent",
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("InvoiceCreatedEvent");
    }

    @Test
    void shouldDetectEventEndpoint_whenRequestSchemaEndsWithNotification() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "alert-service",
            ApiType.REST,
            "/api/alerts",
            "POST",
            null,
            "SystemAlertNotification",
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("SystemAlertNotification");
    }

    @Test
    void shouldDetectEventEndpoint_whenRequestSchemaEndsWithMessage() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "messaging-service",
            ApiType.REST,
            "/api/messages",
            "POST",
            null,
            "ChatMessage",
            null,
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("ChatMessage");
    }

    @Test
    void shouldDetectEventEndpoint_whenResponseSchemaEndsWithEvent() {
        ApiEndpoint eventEndpoint = new ApiEndpoint(
            "workflow-service",
            ApiType.REST,
            "/api/workflow/trigger",
            "POST",
            null,
            null,
            "WorkflowTriggeredEvent",
            null
        );

        ScanContext contextWithEvent = createContextWithEndpoints(List.of(eventEndpoint));
        ScanResult result = scanner.scan(contextWithEvent);

        assertThat(result.messageFlows()).hasSize(1);
    }

    // ========== False Positive Avoidance Tests ==========

    @Test
    void shouldNotDetectEventEndpoint_whenRegularPostWithoutEventIndicators() {
        ApiEndpoint regularEndpoint = new ApiEndpoint(
            "user-service",
            ApiType.REST,
            "/api/users",
            "POST",
            "Create new user",
            "CreateUserRequest",
            "UserResponse",
            null
        );

        ScanContext contextWithRegular = createContextWithEndpoints(List.of(regularEndpoint));
        ScanResult result = scanner.scan(contextWithRegular);

        // Should not detect as event (no event URL pattern, no past-tense verb, no Event schema)
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldNotDetectEventEndpoint_whenGetRequest() {
        ApiEndpoint getEndpoint = new ApiEndpoint(
            "order-service",
            ApiType.REST,
            "/api/orders/created",
            "GET",
            "Get created orders",
            null,
            null,
            null
        );

        ScanContext contextWithGet = createContextWithEndpoints(List.of(getEndpoint));
        ScanResult result = scanner.scan(contextWithGet);

        // GET requests are not events (even with past-tense verb)
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldNotDetectEventEndpoint_whenLoginEndpoint() {
        ApiEndpoint loginEndpoint = new ApiEndpoint(
            "auth-service",
            ApiType.REST,
            "/api/auth/login",
            "POST",
            "User login",
            "LoginRequest",
            "TokenResponse",
            null
        );

        ScanContext contextWithLogin = createContextWithEndpoints(List.of(loginEndpoint));
        ScanResult result = scanner.scan(contextWithLogin);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldNotDetectEventEndpoint_whenFileUpload() {
        ApiEndpoint uploadEndpoint = new ApiEndpoint(
            "storage-service",
            ApiType.REST,
            "/api/files/upload",
            "POST",
            "Upload file",
            "multipart/form-data",
            null,
            null
        );

        ScanContext contextWithUpload = createContextWithEndpoints(List.of(uploadEndpoint));
        ScanResult result = scanner.scan(contextWithUpload);

        assertThat(result.messageFlows()).isEmpty();
    }

    // ========== CRUD Pattern Detection Tests ==========

    @Test
    void shouldDetectCrudPattern_whenPostAndGetOnSameResource() {
        List<ApiEndpoint> crudEndpoints = List.of(
            new ApiEndpoint("product-service", ApiType.REST, "/api/products", "POST", null, null, null, null),
            new ApiEndpoint("product-service", ApiType.REST, "/api/products/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithCrud = createContextWithEndpoints(crudEndpoints);
        ScanResult result = scanner.scan(contextWithCrud);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow crudFlow = result.messageFlows().get(0);
        assertThat(crudFlow.publisherComponentId()).isEqualTo("product-service");
        assertThat(crudFlow.subscriberComponentId()).isEqualTo("product-service");
        assertThat(crudFlow.topic()).isEqualTo("/api/products");
        assertThat(crudFlow.messageType()).isEqualTo("ProductEvent");
        assertThat(crudFlow.broker()).isEqualTo("restful-crud");
    }

    @Test
    void shouldDetectCrudPattern_whenPutAndGetOnSameResource() {
        List<ApiEndpoint> crudEndpoints = List.of(
            new ApiEndpoint("user-service", ApiType.REST, "/api/users/{id}", "PUT", null, null, null, null),
            new ApiEndpoint("user-service", ApiType.REST, "/api/users/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithCrud = createContextWithEndpoints(crudEndpoints);
        ScanResult result = scanner.scan(contextWithCrud);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("UserEvent");
    }

    @Test
    void shouldDetectCrudPattern_whenPatchAndGetOnSameResource() {
        List<ApiEndpoint> crudEndpoints = List.of(
            new ApiEndpoint("order-service", ApiType.REST, "/api/orders/{id}", "PATCH", null, null, null, null),
            new ApiEndpoint("order-service", ApiType.REST, "/api/orders/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithCrud = createContextWithEndpoints(crudEndpoints);
        ScanResult result = scanner.scan(contextWithCrud);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("OrderEvent");
    }

    @Test
    void shouldDetectCrudPattern_withMultipleHttpMethods() {
        List<ApiEndpoint> crudEndpoints = List.of(
            new ApiEndpoint("article-service", ApiType.REST, "/api/articles", "POST", null, null, null, null),
            new ApiEndpoint("article-service", ApiType.REST, "/api/articles/{id}", "GET", null, null, null, null),
            new ApiEndpoint("article-service", ApiType.REST, "/api/articles/{id}", "PUT", null, null, null, null),
            new ApiEndpoint("article-service", ApiType.REST, "/api/articles/{id}", "DELETE", null, null, null, null)
        );

        ScanContext contextWithCrud = createContextWithEndpoints(crudEndpoints);
        ScanResult result = scanner.scan(contextWithCrud);

        // Should detect one CRUD pattern for the resource
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("/api/articles");
    }

    @Test
    void shouldNotDetectCrudPattern_whenOnlyPostWithoutGet() {
        List<ApiEndpoint> endpoints = List.of(
            new ApiEndpoint("order-service", ApiType.REST, "/api/orders", "POST", null, null, null, null)
        );

        ScanContext contextWithPostOnly = createContextWithEndpoints(endpoints);
        ScanResult result = scanner.scan(contextWithPostOnly);

        // No CRUD pattern without GET
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldNotDetectCrudPattern_whenOnlyGetWithoutMutation() {
        List<ApiEndpoint> endpoints = List.of(
            new ApiEndpoint("catalog-service", ApiType.REST, "/api/catalog", "GET", null, null, null, null),
            new ApiEndpoint("catalog-service", ApiType.REST, "/api/catalog/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithGetOnly = createContextWithEndpoints(endpoints);
        ScanResult result = scanner.scan(contextWithGetOnly);

        // No CRUD pattern without mutation methods
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldHandleMultipleResourcesWithCrudPatterns() {
        List<ApiEndpoint> endpoints = List.of(
            // Products CRUD
            new ApiEndpoint("api-gateway", ApiType.REST, "/api/products", "POST", null, null, null, null),
            new ApiEndpoint("api-gateway", ApiType.REST, "/api/products/{id}", "GET", null, null, null, null),

            // Orders CRUD
            new ApiEndpoint("api-gateway", ApiType.REST, "/api/orders", "POST", null, null, null, null),
            new ApiEndpoint("api-gateway", ApiType.REST, "/api/orders/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithMultiple = createContextWithEndpoints(endpoints);
        ScanResult result = scanner.scan(contextWithMultiple);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::messageType)
            .containsExactlyInAnyOrder("ProductEvent", "OrderEvent");
    }

    // ========== Configuration Tests ==========

    @Test
    void shouldDisableCrudPatternDetection_whenConfigured() {
        List<ApiEndpoint> crudEndpoints = List.of(
            new ApiEndpoint("product-service", ApiType.REST, "/api/products", "POST", null, null, null, null),
            new ApiEndpoint("product-service", ApiType.REST, "/api/products/{id}", "GET", null, null, null, null)
        );

        ScanContext contextWithCrudDisabled = new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of("detectCrudPatterns", false), // Disable CRUD detection
            Map.of(),
            Map.of("mock-api-scanner", createMockApiResult(crudEndpoints))
        );

        ScanResult result = scanner.scan(contextWithCrudDisabled);

        // No CRUD patterns detected when disabled
        assertThat(result.messageFlows()).isEmpty();
    }

    // ========== Edge Case Tests ==========

    @Test
    void shouldHandleNullHttpMethod() {
        ApiEndpoint endpointWithNullMethod = new ApiEndpoint(
            "test-service",
            ApiType.REST,
            "/events/test",
            null, // null method
            null,
            null,
            null,
            null
        );

        ScanContext contextWithNull = createContextWithEndpoints(List.of(endpointWithNullMethod));
        ScanResult result = scanner.scan(contextWithNull);

        // Should still detect based on URL pattern
        assertThat(result.messageFlows()).hasSize(1);
    }

    @Test
    void shouldHandleMixedCaseUrls() {
        ApiEndpoint mixedCaseEndpoint = new ApiEndpoint(
            "service",
            ApiType.REST,
            "/API/Events/Order-Created",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithMixed = createContextWithEndpoints(List.of(mixedCaseEndpoint));
        ScanResult result = scanner.scan(contextWithMixed);

        assertThat(result.messageFlows()).hasSize(1);
    }

    @Test
    void shouldExtractMessageTypeFromPath() {
        ApiEndpoint endpoint = new ApiEndpoint(
            "service",
            ApiType.REST,
            "/events/payment-completed",
            "POST",
            null,
            null, // No request schema
            null,
            null
        );

        ScanContext contextWithPath = createContextWithEndpoints(List.of(endpoint));
        ScanResult result = scanner.scan(contextWithPath);

        assertThat(result.messageFlows()).hasSize(1);
        // Should convert kebab-case to PascalCase
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("PaymentCompletedEvent");
    }

    @Test
    void shouldHandlePathVariablesInEventEndpoints() {
        ApiEndpoint endpointWithPathVar = new ApiEndpoint(
            "service",
            ApiType.REST,
            "/events/{eventType}/trigger",
            "POST",
            null,
            null,
            null,
            null
        );

        ScanContext contextWithPathVar = createContextWithEndpoints(List.of(endpointWithPathVar));
        ScanResult result = scanner.scan(contextWithPathVar);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).messageType()).isEqualTo("TriggerEvent");
    }

    // ========== Helper Methods ==========

    /**
     * Creates a ScanContext with mock API endpoint results.
     */
    private ScanContext createContextWithEndpoints(List<ApiEndpoint> endpoints) {
        ScanResult mockApiResult = createMockApiResult(endpoints);

        return new ScanContext(
            tempDir,
            List.of(tempDir),
            Map.of(),
            Map.of(),
            Map.of("mock-api-scanner", mockApiResult)
        );
    }

    /**
     * Creates a mock ScanResult with API endpoints.
     */
    private ScanResult createMockApiResult(List<ApiEndpoint> endpoints) {
        return new ScanResult(
            "mock-api-scanner",
            true,
            List.of(),
            List.of(),
            endpoints,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ScanStatistics.empty()
        );
    }
}
