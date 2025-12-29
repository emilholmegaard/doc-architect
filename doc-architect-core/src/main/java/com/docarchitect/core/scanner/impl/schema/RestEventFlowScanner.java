package com.docarchitect.core.scanner.impl.schema;

import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.Scanner;
import com.docarchitect.core.util.Technologies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scanner for detecting REST-based event flows and RESTful CRUD patterns.
 *
 * <p>This scanner operates as a post-processor that runs after all API endpoint scanners
 * (Spring REST, ASP.NET Core, FastAPI, etc.). It analyzes discovered {@link ApiEndpoint}
 * instances to identify event-driven patterns and RESTful CRUD operations communicated
 * via REST APIs rather than message queues.
 *
 * <p><b>Detection Strategy:</b>
 * <ol>
 *   <li><b>URL Pattern Detection:</b> Identifies event endpoints via URL patterns
 *       (/events/*, /webhooks/*, /domain-events/*, /notifications/*)</li>
 *   <li><b>HTTP Method Analysis:</b> POST endpoints with past-tense verbs
 *       (order-created, user-registered, payment-completed)</li>
 *   <li><b>Schema Analysis:</b> Request/response schemas ending with "Event", "Notification", or "Message"</li>
 *   <li><b>CRUD Pattern Matching:</b> Correlates POST endpoints with corresponding GET endpoints
 *       to detect RESTful CRUD operations that might represent event flows</li>
 * </ol>
 *
 * <p><b>Execution Priority:</b> This scanner runs at priority 150 (after all API scanners
 * which typically run at 50-100) to ensure all ApiEndpoint data is available.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // After API scanners have run and collected endpoints:
 * // - POST /events/order-created → detected as event endpoint
 * // - POST /webhooks/payment-completed → detected as webhook
 * // - POST /api/orders + GET /api/orders/{id} → detected as CRUD pattern
 *
 * Scanner scanner = new RestEventFlowScanner();
 * ScanResult result = scanner.scan(context);
 * List<MessageFlow> eventFlows = result.messageFlows();
 * }</pre>
 *
 * <p><b>Configuration:</b>
 * <pre>{@code
 * scanners:
 *   rest-event-flow:
 *     enabled: true
 *     urlPatterns:
 *       - "/events/**"
 *       - "/domain-events/**"
 *       - "/webhooks/**"
 *       - "/notifications/**"
 *     pastTenseVerbs:
 *       - "created"
 *       - "updated"
 *       - "deleted"
 *       - "completed"
 *     detectCrudPatterns: true
 * }</pre>
 *
 * @see ApiEndpoint
 * @see MessageFlow
 * @see Scanner
 * @since 1.0.0
 */
public class RestEventFlowScanner implements Scanner {

    private static final Logger logger = LoggerFactory.getLogger(RestEventFlowScanner.class);

    private static final String SCANNER_ID = "rest-event-flow";
    private static final String DISPLAY_NAME = "REST Event Flow Scanner";
    private static final int SCANNER_PRIORITY = 150; // Run after all API scanners

    // Event URL pattern detection
    private static final List<String> DEFAULT_EVENT_URL_PATTERNS = List.of(
        "/events/",
        "/domain-events/",
        "/webhooks/",
        "/notifications/",
        "-events/",
        "/event/"
    );

    // Past-tense verbs indicating event operations
    private static final List<String> DEFAULT_PAST_TENSE_VERBS = List.of(
        "created", "updated", "deleted", "removed",
        "completed", "processed", "confirmed", "approved",
        "rejected", "cancelled", "failed", "succeeded",
        "registered", "activated", "deactivated", "suspended",
        "reserved", "released", "allocated", "assigned"
    );

    // Schema type suffixes indicating event payloads
    private static final List<String> EVENT_SCHEMA_SUFFIXES = List.of(
        "Event", "Notification", "Message", "Webhook"
    );

    // CRUD HTTP methods
    private static final String HTTP_POST = "POST";
    private static final String HTTP_GET = "GET";
    private static final String HTTP_PUT = "PUT";
    private static final String HTTP_PATCH = "PATCH";
    private static final String HTTP_DELETE = "DELETE";

    private static final String REST_EVENT_BROKER_TYPE = "rest-event";

    // Compiled patterns for performance
    private final Pattern pastTensePattern;

    public RestEventFlowScanner() {
        // Compile regex for past-tense verb detection
        String verbRegex = String.join("|", DEFAULT_PAST_TENSE_VERBS);
        this.pastTensePattern = Pattern.compile(".*[-_/](" + verbRegex + ")(?:[-_/]|$)", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        // Language-agnostic: works with any API scanner output
        return Set.of(
            Technologies.JAVA,
            Technologies.PYTHON,
            Technologies.CSHARP,
            Technologies.GO,
            Technologies.JAVASCRIPT,
            Technologies.TYPESCRIPT,
            Technologies.RUBY
        );
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        // No file scanning; operates on previous scan results
        return Set.of();
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        // Apply if any previous scanner found API endpoints
        return context.previousResults().values().stream()
            .anyMatch(result -> !result.apiEndpoints().isEmpty());
    }

    @Override
    public ScanResult scan(ScanContext context) {
        logger.info("Analyzing API endpoints for REST-based event flows and CRUD patterns");

        // Collect all API endpoints from previous scanners
        List<ApiEndpoint> allEndpoints = context.previousResults().values().stream()
            .flatMap(result -> result.apiEndpoints().stream())
            .toList();

        if (allEndpoints.isEmpty()) {
            logger.debug("No API endpoints found in previous scan results");
            return ScanResult.empty(SCANNER_ID);
        }

        logger.debug("Found {} API endpoints from previous scanners", allEndpoints.size());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Detect event endpoints
        boolean detectCrudPatterns = context.getConfigOrDefault("detectCrudPatterns", true);

        for (ApiEndpoint endpoint : allEndpoints) {
            Optional<MessageFlow> eventFlow = detectEventEndpoint(endpoint);
            eventFlow.ifPresent(messageFlows::add);
        }

        // Detect CRUD patterns if enabled
        if (detectCrudPatterns) {
            List<MessageFlow> crudFlows = detectCrudPatterns(allEndpoints);
            messageFlows.addAll(crudFlows);
        }

        if (messageFlows.isEmpty()) {
            logger.debug("No REST-based event flows or CRUD patterns detected");
        } else {
            logger.info("Detected {} REST-based event flows", messageFlows.size());
        }

        return new ScanResult(
            SCANNER_ID,
            true,
            List.of(), // No new components
            List.of(), // No new dependencies
            List.of(), // No new API endpoints
            messageFlows,
            List.of(), // No new data entities
            List.of(), // No new relationships
            warnings,
            List.of()
        );
    }

    /**
     * Detects if an API endpoint represents an event-driven pattern.
     *
     * <p>Uses multiple heuristics:
     * <ol>
     *   <li>URL pattern matching (/events/*, /webhooks/*, etc.)</li>
     *   <li>Past-tense verb detection in path (order-created, user-deleted)</li>
     *   <li>Event schema types in request/response (OrderCreatedEvent, PaymentNotification)</li>
     * </ol>
     *
     * @param endpoint API endpoint to analyze
     * @return Optional containing MessageFlow if event pattern detected, empty otherwise
     */
    private Optional<MessageFlow> detectEventEndpoint(ApiEndpoint endpoint) {
        String path = endpoint.path().toLowerCase();
        String method = endpoint.method() != null ? endpoint.method().toUpperCase() : "";

        // Heuristic 1: URL pattern matching
        boolean hasEventUrlPattern = DEFAULT_EVENT_URL_PATTERNS.stream()
            .anyMatch(path::contains);

        // Heuristic 2: POST with past-tense verb
        boolean hasPastTenseVerb = HTTP_POST.equals(method) && pastTensePattern.matcher(path).matches();

        // Heuristic 3: Event schema types
        boolean hasEventSchema = hasEventSchemaType(endpoint.requestSchema()) ||
                                 hasEventSchemaType(endpoint.responseSchema());

        // Require at least one heuristic to match to avoid false positives
        if (!hasEventUrlPattern && !hasPastTenseVerb && !hasEventSchema) {
            return Optional.empty();
        }

        // Extract topic (use full path as topic)
        String topic = endpoint.path();

        // Extract message type from schema or path
        String messageType = extractMessageType(endpoint);

        // Create MessageFlow (subscriber = component hosting endpoint, publisher = unknown)
        MessageFlow flow = new MessageFlow(
            null, // Publisher unknown (external HTTP client)
            endpoint.componentId(), // Subscriber (component hosting this endpoint)
            topic, // Topic is the REST path
            messageType, // Event type
            endpoint.requestSchema(), // Schema from request body
            REST_EVENT_BROKER_TYPE // Special broker type for REST events
        );

        logger.debug("Detected REST event endpoint: {} {} → MessageFlow topic='{}'",
            method, endpoint.path(), topic);

        return Optional.of(flow);
    }

    /**
     * Detects RESTful CRUD patterns by correlating POST endpoints with GET endpoints.
     *
     * <p>Example patterns:
     * <ul>
     *   <li>POST /api/orders + GET /api/orders/{id} → CRUD create/read pattern</li>
     *   <li>PUT /api/users/{id} + GET /api/users/{id} → CRUD update/read pattern</li>
     *   <li>DELETE /api/products/{id} → CRUD delete pattern</li>
     * </ul>
     *
     * @param allEndpoints all API endpoints to analyze
     * @return list of MessageFlow instances for detected CRUD patterns
     */
    private List<MessageFlow> detectCrudPatterns(List<ApiEndpoint> allEndpoints) {
        List<MessageFlow> crudFlows = new ArrayList<>();

        // Group endpoints by base path (without path variables)
        Map<String, List<ApiEndpoint>> endpointsByBasePath = allEndpoints.stream()
            .collect(Collectors.groupingBy(this::extractBasePath));

        for (Map.Entry<String, List<ApiEndpoint>> entry : endpointsByBasePath.entrySet()) {
            String basePath = entry.getKey();
            List<ApiEndpoint> endpoints = entry.getValue();

            // Look for CRUD operations on the same resource
            boolean hasPost = endpoints.stream().anyMatch(e -> HTTP_POST.equals(e.method()));
            boolean hasGet = endpoints.stream().anyMatch(e -> HTTP_GET.equals(e.method()));
            boolean hasPut = endpoints.stream().anyMatch(e -> HTTP_PUT.equals(e.method()));
            boolean hasPatch = endpoints.stream().anyMatch(e -> HTTP_PATCH.equals(e.method()));
            boolean hasDelete = endpoints.stream().anyMatch(e -> HTTP_DELETE.equals(e.method()));

            // If we have both POST and GET, it's a potential CRUD pattern
            if ((hasPost || hasPut || hasPatch) && hasGet) {
                // Find the component ID (should be same for all endpoints on this resource)
                String componentId = endpoints.get(0).componentId();

                // Extract resource name from base path
                String resourceName = extractResourceName(basePath);

                // Create a MessageFlow representing the CRUD event
                String messageType = resourceName + "Event";
                String topic = basePath;

                MessageFlow crudFlow = new MessageFlow(
                    componentId, // Publisher (same component for CRUD)
                    componentId, // Subscriber (same component for CRUD)
                    topic, // Topic is the base path
                    messageType, // Event type derived from resource name
                    null, // No specific schema
                    "restful-crud" // Special broker type for CRUD patterns
                );

                crudFlows.add(crudFlow);

                logger.debug("Detected RESTful CRUD pattern on {}: POST={}, GET={}, PUT={}, PATCH={}, DELETE={}",
                    basePath, hasPost, hasGet, hasPut, hasPatch, hasDelete);
            }
        }

        return crudFlows;
    }

    /**
     * Extracts the base path from an endpoint path by removing path variables.
     *
     * <p>Examples:
     * <ul>
     *   <li>/api/orders/{id} → /api/orders</li>
     *   <li>/users/{userId}/posts/{postId} → /users</li>
     *   <li>/products → /products</li>
     * </ul>
     *
     * @param endpoint API endpoint
     * @return base path without path variables
     */
    private String extractBasePath(ApiEndpoint endpoint) {
        String path = endpoint.path();

        // Remove path variables in {} or : format
        path = path.replaceAll("/\\{[^}]+\\}", "");
        path = path.replaceAll("/:[^/]+", "");

        // Remove trailing slash
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        return path.isEmpty() ? "/" : path;
    }

    /**
     * Extracts a resource name from a base path.
     *
     * <p>Examples:
     * <ul>
     *   <li>/api/orders → Order</li>
     *   <li>/users → User</li>
     *   <li>/api/v1/products → Product</li>
     * </ul>
     *
     * @param basePath base path without variables
     * @return capitalized resource name
     */
    private String extractResourceName(String basePath) {
        String[] parts = basePath.split("/");

        // Get the last non-empty segment
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (!part.isEmpty() && !part.matches("v\\d+") && !part.equals("api")) {
                // Singularize (simple heuristic: remove trailing 's')
                if (part.endsWith("s") && part.length() > 1) {
                    part = part.substring(0, part.length() - 1);
                }
                // Capitalize first letter
                return part.substring(0, 1).toUpperCase() + part.substring(1);
            }
        }

        return "Resource";
    }

    /**
     * Checks if a schema type name indicates an event payload.
     *
     * @param schemaType schema type name
     * @return true if schema type ends with Event, Notification, Message, or Webhook
     */
    private boolean hasEventSchemaType(String schemaType) {
        if (schemaType == null || schemaType.isEmpty()) {
            return false;
        }

        return EVENT_SCHEMA_SUFFIXES.stream()
            .anyMatch(schemaType::endsWith);
    }

    /**
     * Extracts message type from endpoint schema or path.
     *
     * @param endpoint API endpoint
     * @return extracted message type
     */
    private String extractMessageType(ApiEndpoint endpoint) {
        // Prefer request schema if available
        if (endpoint.requestSchema() != null && !endpoint.requestSchema().isEmpty()) {
            return endpoint.requestSchema();
        }

        // Derive from path (extract last segment)
        String[] pathParts = endpoint.path().split("/");
        if (pathParts.length > 0) {
            String lastSegment = pathParts[pathParts.length - 1];
            if (!lastSegment.isEmpty() && !lastSegment.startsWith("{")) {
                // Convert kebab-case or snake_case to PascalCase
                return toPascalCase(lastSegment) + "Event";
            }
        }

        return "UnknownEvent";
    }

    /**
     * Converts kebab-case or snake_case to PascalCase.
     *
     * <p>Examples:
     * <ul>
     *   <li>order-created → OrderCreated</li>
     *   <li>user_registered → UserRegistered</li>
     *   <li>payment-completed → PaymentCompleted</li>
     * </ul>
     *
     * @param input input string
     * @return PascalCase output
     */
    private String toPascalCase(String input) {
        String[] parts = input.split("[-_]");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase());
            }
        }

        return result.toString();
    }
}
