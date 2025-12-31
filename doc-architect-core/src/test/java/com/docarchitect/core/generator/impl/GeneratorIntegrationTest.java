package com.docarchitect.core.generator.impl;

import com.docarchitect.core.generator.DiagramType;
import com.docarchitect.core.generator.GeneratedDiagram;
import com.docarchitect.core.generator.GeneratorConfig;
import com.docarchitect.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for generators using realistic Spring Boot microservices architecture.
 *
 * <p>This test simulates a complete e-commerce platform with:
 * <ul>
 *   <li>6 microservices (User, Order, Product, Payment, Notification, Inventory)</li>
 *   <li>3 databases (PostgreSQL, MongoDB, Redis)</li>
 *   <li>Message broker (Kafka)</li>
 *   <li>API Gateway</li>
 *   <li>Multiple REST APIs, message flows, and data entities</li>
 * </ul>
 */
class GeneratorIntegrationTest {

    private MermaidGenerator mermaidGenerator;
    private MarkdownGenerator markdownGenerator;
    private SokratesGenerator sokratesGenerator;
    private GeneratorConfig config;
    private ArchitectureModel complexModel;

    @BeforeEach
    void setUp() {
        mermaidGenerator = new MermaidGenerator();
        markdownGenerator = new MarkdownGenerator();
        sokratesGenerator = new SokratesGenerator();
        config = GeneratorConfig.defaults();
        complexModel = buildComplexSpringBootArchitecture();
    }

    /**
     * Builds a realistic Spring Boot microservices architecture model.
     */
    private ArchitectureModel buildComplexSpringBootArchitecture() {
        List<Component> components = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        List<ApiEndpoint> apiEndpoints = new ArrayList<>();
        List<MessageFlow> messageFlows = new ArrayList<>();
        List<DataEntity> dataEntities = new ArrayList<>();

        // === COMPONENTS ===

        // API Gateway
        components.add(new Component(
            "api-gateway-1",
            "API Gateway",
            ComponentType.API_GATEWAY,
            "Kong API Gateway for routing and authentication",
            "Kong 3.0",
            "api-gateway-repo",
            Map.of("version", "3.0.1", "port", "8080")
        ));

        // Microservices
        components.add(new Component(
            "user-service",
            "User Service",
            ComponentType.SERVICE,
            "Manages user accounts and authentication",
            "Spring Boot 3.2",
            "user-service-repo",
            Map.of("version", "1.5.0", "port", "8081")
        ));

        components.add(new Component(
            "order-service",
            "Order Service",
            ComponentType.SERVICE,
            "Processes customer orders and order lifecycle",
            "Spring Boot 3.2",
            "order-service-repo",
            Map.of("version", "2.1.0", "port", "8082")
        ));

        components.add(new Component(
            "product-service",
            "Product Service",
            ComponentType.SERVICE,
            "Manages product catalog and inventory",
            "Spring Boot 3.2",
            "product-service-repo",
            Map.of("version", "1.8.0", "port", "8083")
        ));

        components.add(new Component(
            "payment-service",
            "Payment Service",
            ComponentType.SERVICE,
            "Handles payment processing via Stripe",
            "Spring Boot 3.2",
            "payment-service-repo",
            Map.of("version", "3.0.0", "port", "8084")
        ));

        components.add(new Component(
            "notification-service",
            "Notification Service",
            ComponentType.SERVICE,
            "Sends email and SMS notifications",
            "Spring Boot 3.2",
            "notification-service-repo",
            Map.of("version", "1.2.0", "port", "8085")
        ));

        components.add(new Component(
            "inventory-service",
            "Inventory Service",
            ComponentType.SERVICE,
            "Tracks product stock levels",
            "Spring Boot 3.2",
            "inventory-service-repo",
            Map.of("version", "1.0.0", "port", "8086")
        ));

        // Databases
        components.add(new Component(
            "user-db",
            "User Database",
            ComponentType.DATABASE,
            "PostgreSQL database for user data",
            "PostgreSQL 15",
            "user-service-repo",
            Map.of("version", "15.2")
        ));

        components.add(new Component(
            "order-db",
            "Order Database",
            ComponentType.DATABASE,
            "PostgreSQL database for orders",
            "PostgreSQL 15",
            "order-service-repo",
            Map.of("version", "15.2")
        ));

        components.add(new Component(
            "product-db",
            "Product Database",
            ComponentType.DATABASE,
            "MongoDB for product catalog",
            "MongoDB 6.0",
            "product-service-repo",
            Map.of("version", "6.0.3")
        ));

        components.add(new Component(
            "cache",
            "Redis Cache",
            ComponentType.CACHE,
            "Redis for caching and sessions",
            "Redis 7.0",
            "infrastructure-repo",
            Map.of("version", "7.0.8")
        ));

        // Message Broker
        components.add(new Component(
            "kafka",
            "Kafka Cluster",
            ComponentType.MESSAGE_BROKER,
            "Event streaming platform",
            "Apache Kafka 3.4",
            "infrastructure-repo",
            Map.of("version", "3.4.0", "brokers", "3")
        ));

        // === DEPENDENCIES ===

        // User Service dependencies
        dependencies.add(new Dependency("user-service", "org.springframework.boot", "spring-boot-starter-web", "3.2.0", "compile", true));
        dependencies.add(new Dependency("user-service", "org.springframework.boot", "spring-boot-starter-data-jpa", "3.2.0", "compile", true));
        dependencies.add(new Dependency("user-service", "org.springframework.boot", "spring-boot-starter-security", "3.2.0", "compile", true));
        dependencies.add(new Dependency("user-service", "org.postgresql", "postgresql", "42.6.0", "runtime", true));
        dependencies.add(new Dependency("user-service", "org.springframework.kafka", "spring-kafka", "3.1.0", "compile", true));
        dependencies.add(new Dependency("user-service", "io.jsonwebtoken", "jjwt-api", "0.12.3", "compile", true));

        // Order Service dependencies
        dependencies.add(new Dependency("order-service", "org.springframework.boot", "spring-boot-starter-web", "3.2.0", "compile", true));
        dependencies.add(new Dependency("order-service", "org.springframework.boot", "spring-boot-starter-data-jpa", "3.2.0", "compile", true));
        dependencies.add(new Dependency("order-service", "org.postgresql", "postgresql", "42.6.0", "runtime", true));
        dependencies.add(new Dependency("order-service", "org.springframework.kafka", "spring-kafka", "3.1.0", "compile", true));
        dependencies.add(new Dependency("order-service", "org.springframework.cloud", "spring-cloud-starter-openfeign", "4.1.0", "compile", true));

        // Product Service dependencies
        dependencies.add(new Dependency("product-service", "org.springframework.boot", "spring-boot-starter-web", "3.2.0", "compile", true));
        dependencies.add(new Dependency("product-service", "org.springframework.boot", "spring-boot-starter-data-mongodb", "3.2.0", "compile", true));
        dependencies.add(new Dependency("product-service", "org.springframework.data", "spring-data-redis", "3.2.0", "compile", true));
        dependencies.add(new Dependency("product-service", "org.springframework.kafka", "spring-kafka", "3.1.0", "compile", true));

        // === RELATIONSHIPS ===

        // Gateway to services
        relationships.add(new Relationship("api-gateway-1", "user-service", RelationshipType.USES, "Routes user requests", "HTTP/REST"));
        relationships.add(new Relationship("api-gateway-1", "order-service", RelationshipType.USES, "Routes order requests", "HTTP/REST"));
        relationships.add(new Relationship("api-gateway-1", "product-service", RelationshipType.USES, "Routes product requests", "HTTP/REST"));
        relationships.add(new Relationship("api-gateway-1", "payment-service", RelationshipType.USES, "Routes payment requests", "HTTP/REST"));

        // Services to databases
        relationships.add(new Relationship("user-service", "user-db", RelationshipType.USES, "Persists user data", "JDBC"));
        relationships.add(new Relationship("order-service", "order-db", RelationshipType.USES, "Persists order data", "JDBC"));
        relationships.add(new Relationship("product-service", "product-db", RelationshipType.USES, "Persists product data", "MongoDB Driver"));

        // Services to cache
        relationships.add(new Relationship("user-service", "cache", RelationshipType.USES, "Caches user sessions", "Redis Client"));
        relationships.add(new Relationship("product-service", "cache", RelationshipType.USES, "Caches product data", "Redis Client"));

        // Inter-service communication
        relationships.add(new Relationship("order-service", "user-service", RelationshipType.USES, "Validates users", "Feign Client"));
        relationships.add(new Relationship("order-service", "product-service", RelationshipType.USES, "Checks inventory", "Feign Client"));
        relationships.add(new Relationship("order-service", "payment-service", RelationshipType.USES, "Processes payments", "Feign Client"));
        relationships.add(new Relationship("payment-service", "notification-service", RelationshipType.USES, "Sends payment confirmations", "Kafka"));

        // === API ENDPOINTS ===

        // User Service APIs
        apiEndpoints.add(new ApiEndpoint("user-service", ApiType.REST, "/api/v1/users", "POST", "Create new user", "CreateUserRequest", "UserResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("user-service", ApiType.REST, "/api/v1/users/{id}", "GET", "Get user by ID", null, "UserResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("user-service", ApiType.REST, "/api/v1/users/{id}", "PUT", "Update user", "UpdateUserRequest", "UserResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("user-service", ApiType.REST, "/api/v1/auth/login", "POST", "User login", "LoginRequest", "TokenResponse", null));
        apiEndpoints.add(new ApiEndpoint("user-service", ApiType.REST, "/api/v1/auth/refresh", "POST", "Refresh token", "RefreshRequest", "TokenResponse", "JWT"));

        // Order Service APIs
        apiEndpoints.add(new ApiEndpoint("order-service", ApiType.REST, "/api/v1/orders", "POST", "Create order", "CreateOrderRequest", "OrderResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("order-service", ApiType.REST, "/api/v1/orders/{id}", "GET", "Get order details", null, "OrderResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("order-service", ApiType.REST, "/api/v1/orders", "GET", "List user orders", null, "OrderListResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("order-service", ApiType.REST, "/api/v1/orders/{id}/cancel", "POST", "Cancel order", null, "OrderResponse", "JWT"));

        // Product Service APIs
        apiEndpoints.add(new ApiEndpoint("product-service", ApiType.REST, "/api/v1/products", "GET", "List products", null, "ProductListResponse", null));
        apiEndpoints.add(new ApiEndpoint("product-service", ApiType.REST, "/api/v1/products/{id}", "GET", "Get product details", null, "ProductResponse", null));
        apiEndpoints.add(new ApiEndpoint("product-service", ApiType.REST, "/api/v1/products", "POST", "Create product", "CreateProductRequest", "ProductResponse", "JWT (Admin)"));
        apiEndpoints.add(new ApiEndpoint("product-service", ApiType.REST, "/api/v1/products/{id}", "PUT", "Update product", "UpdateProductRequest", "ProductResponse", "JWT (Admin)"));

        // Payment Service APIs
        apiEndpoints.add(new ApiEndpoint("payment-service", ApiType.REST, "/api/v1/payments", "POST", "Process payment", "PaymentRequest", "PaymentResponse", "JWT"));
        apiEndpoints.add(new ApiEndpoint("payment-service", ApiType.REST, "/api/v1/payments/{id}", "GET", "Get payment status", null, "PaymentResponse", "JWT"));

        // === MESSAGE FLOWS ===

        // User events
        messageFlows.add(new MessageFlow("user-service", null, "user.created", "UserCreatedEvent", "UserEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("user-service", null, "user.updated", "UserUpdatedEvent", "UserEventSchema", "kafka"));

        // Order events
        messageFlows.add(new MessageFlow("order-service", "payment-service", "order.created", "OrderCreatedEvent", "OrderEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("order-service", "inventory-service", "order.created", "OrderCreatedEvent", "OrderEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("order-service", "notification-service", "order.confirmed", "OrderConfirmedEvent", "OrderEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("order-service", null, "order.cancelled", "OrderCancelledEvent", "OrderEventSchema", "kafka"));

        // Payment events
        messageFlows.add(new MessageFlow("payment-service", "order-service", "payment.completed", "PaymentCompletedEvent", "PaymentEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("payment-service", "notification-service", "payment.completed", "PaymentCompletedEvent", "PaymentEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("payment-service", null, "payment.failed", "PaymentFailedEvent", "PaymentEventSchema", "kafka"));

        // Inventory events
        messageFlows.add(new MessageFlow("inventory-service", "product-service", "inventory.updated", "InventoryUpdatedEvent", "InventoryEventSchema", "kafka"));
        messageFlows.add(new MessageFlow("inventory-service", "notification-service", "inventory.low", "LowInventoryEvent", "InventoryEventSchema", "kafka"));

        // === DATA ENTITIES ===

        // User Service entities
        List<DataEntity.Field> userFields = List.of(
            new DataEntity.Field("id", "bigint", false, "Primary key"),
            new DataEntity.Field("email", "varchar(255)", false, "User email (unique)"),
            new DataEntity.Field("password_hash", "varchar(255)", false, "BCrypt password hash"),
            new DataEntity.Field("first_name", "varchar(100)", false, "First name"),
            new DataEntity.Field("last_name", "varchar(100)", false, "Last name"),
            new DataEntity.Field("phone", "varchar(20)", true, "Phone number"),
            new DataEntity.Field("created_at", "timestamp", false, "Creation timestamp"),
            new DataEntity.Field("updated_at", "timestamp", false, "Last update timestamp"),
            new DataEntity.Field("is_active", "boolean", false, "Account status")
        );
        dataEntities.add(new DataEntity("user-service", "users", "table", userFields, "id", "User accounts"));

        List<DataEntity.Field> addressFields = List.of(
            new DataEntity.Field("id", "bigint", false, "Primary key"),
            new DataEntity.Field("user_id", "bigint", false, "Foreign key to users"),
            new DataEntity.Field("street", "varchar(255)", false, "Street address"),
            new DataEntity.Field("city", "varchar(100)", false, "City"),
            new DataEntity.Field("state", "varchar(50)", false, "State/Province"),
            new DataEntity.Field("postal_code", "varchar(20)", false, "Postal code"),
            new DataEntity.Field("country", "varchar(50)", false, "Country"),
            new DataEntity.Field("is_default", "boolean", false, "Default address flag")
        );
        dataEntities.add(new DataEntity("user-service", "addresses", "table", addressFields, "id", "User addresses"));

        // Order Service entities
        List<DataEntity.Field> orderFields = List.of(
            new DataEntity.Field("id", "bigint", false, "Primary key"),
            new DataEntity.Field("user_id", "bigint", false, "Foreign key to users"),
            new DataEntity.Field("order_number", "varchar(50)", false, "Unique order number"),
            new DataEntity.Field("status", "varchar(20)", false, "Order status (PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED)"),
            new DataEntity.Field("total_amount", "decimal(10,2)", false, "Total order amount"),
            new DataEntity.Field("currency", "varchar(3)", false, "Currency code (USD/EUR/etc)"),
            new DataEntity.Field("shipping_address_id", "bigint", false, "Shipping address"),
            new DataEntity.Field("created_at", "timestamp", false, "Order creation time"),
            new DataEntity.Field("updated_at", "timestamp", false, "Last update time")
        );
        dataEntities.add(new DataEntity("order-service", "orders", "table", orderFields, "id", "Customer orders"));

        List<DataEntity.Field> orderItemFields = List.of(
            new DataEntity.Field("id", "bigint", false, "Primary key"),
            new DataEntity.Field("order_id", "bigint", false, "Foreign key to orders"),
            new DataEntity.Field("product_id", "bigint", false, "Product reference"),
            new DataEntity.Field("quantity", "int", false, "Quantity ordered"),
            new DataEntity.Field("unit_price", "decimal(10,2)", false, "Price per unit"),
            new DataEntity.Field("total_price", "decimal(10,2)", false, "Total line item price")
        );
        dataEntities.add(new DataEntity("order-service", "order_items", "table", orderItemFields, "id", "Order line items"));

        // Product Service entities (MongoDB)
        List<DataEntity.Field> productFields = List.of(
            new DataEntity.Field("_id", "ObjectId", false, "MongoDB document ID"),
            new DataEntity.Field("sku", "string", false, "Stock keeping unit"),
            new DataEntity.Field("name", "string", false, "Product name"),
            new DataEntity.Field("description", "string", true, "Product description"),
            new DataEntity.Field("category", "string", false, "Product category"),
            new DataEntity.Field("price", "decimal", false, "Product price"),
            new DataEntity.Field("currency", "string", false, "Currency code"),
            new DataEntity.Field("images", "array", true, "Product image URLs"),
            new DataEntity.Field("attributes", "object", true, "Product attributes"),
            new DataEntity.Field("created_at", "date", false, "Creation date"),
            new DataEntity.Field("updated_at", "date", false, "Last update date")
        );
        dataEntities.add(new DataEntity("product-service", "products", "collection", productFields, "_id", "Product catalog"));

        return new ArchitectureModel(
            "E-Commerce Platform",
            "2.0.0",
            List.of("user-service-repo", "order-service-repo", "product-service-repo",
                   "payment-service-repo", "notification-service-repo", "inventory-service-repo"),
            components,
            dependencies,
            relationships,
            apiEndpoints,
            messageFlows,
            dataEntities,
            null,
            null
        );
    }

    // === MERMAID GENERATOR TESTS ===

    @Test
    void mermaidGenerator_complexArchitecture_c4Context_generatesValidDiagram() {
        GeneratedDiagram diagram = mermaidGenerator.generate(complexModel, DiagramType.C4_CONTEXT, config);

        assertThat(diagram.name()).isEqualTo("c4-context");
        assertThat(diagram.fileExtension()).isEqualTo("md");
        assertThat(diagram.content())
            .contains("C4Context")
            .contains("E-Commerce Platform")
            .contains("User Service")
            .contains("Order Service")
            .contains("Product Service")
            .contains("Payment Service")
            .contains("Notification Service")
            .contains("Inventory Service");
    }

    @Test
    void mermaidGenerator_complexArchitecture_dependencyGraph_includesAllComponents() {
        GeneratedDiagram diagram = mermaidGenerator.generate(complexModel, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.content())
            .contains("graph LR")
            .contains("User Service")
            .contains("Order Service")
            .contains("spring-boot-starter-web")
            .contains("spring-boot-starter-data-jpa")
            .contains("spring-kafka")
            .contains("postgresql")
            .contains("-->");

        // Verify large number of dependencies are handled
        long arrowCount = diagram.content().lines()
            .filter(line -> line.contains("-->"))
            .count();
        assertThat(arrowCount).isGreaterThan(10);
    }

    @Test
    void mermaidGenerator_complexArchitecture_erDiagram_includesAllEntities() {
        GeneratedDiagram diagram = mermaidGenerator.generate(complexModel, DiagramType.ER_DIAGRAM, config);

        assertThat(diagram.content())
            .contains("erDiagram")
            .contains("USERS")
            .contains("ORDERS")
            .contains("ORDER_ITEMS")
            .contains("ADDRESSES")
            .contains("PRODUCTS")
            // Field examples
            .contains("email")
            .contains("order_number")
            .contains("sku");

        // Verify relationships are inferred
        assertThat(diagram.content()).containsAnyOf("||--o{", "has");
    }

    @Test
    void mermaidGenerator_complexArchitecture_messageFlow_showsKafkaTopology() {
        GeneratedDiagram diagram = mermaidGenerator.generate(complexModel, DiagramType.MESSAGE_FLOW, config);

        assertThat(diagram.content())
            .contains("graph TB")
            .contains("order.created")
            .contains("payment.completed")
            .contains("user.created")
            .contains("inventory.updated")
            .contains("User Service")
            .contains("Order Service")
            .contains("Payment Service");

        // Verify message flow arrows
        long flowCount = diagram.content().lines()
            .filter(line -> line.contains("-->") || line.contains("|"))
            .count();
        assertThat(flowCount).isGreaterThan(5);
    }

    @Test
    void mermaidGenerator_complexArchitecture_sequence_showsApiCalls() {
        GeneratedDiagram diagram = mermaidGenerator.generate(complexModel, DiagramType.SEQUENCE, config);

        assertThat(diagram.content())
            .contains("sequenceDiagram")
            .contains("Client")
            .contains("/api/v1/users")
            .contains("/api/v1/orders")
            .contains("/api/v1/products")
            .contains("/api/v1/payments");

        // Verify multiple endpoints are shown
        long endpointCount = diagram.content().lines()
            .filter(line -> line.contains("/api/v"))
            .count();
        assertThat(endpointCount).isGreaterThanOrEqualTo(3);
    }

    // === MARKDOWN GENERATOR TESTS ===

    @Test
    void markdownGenerator_complexArchitecture_index_providesOverview() {
        String index = markdownGenerator.generateIndex(complexModel);

        assertThat(index)
            .contains("# E-Commerce Platform - Architecture Documentation")
            .contains("**Version:** 2.0.0")
            .contains("| Components | 12 |")
            .contains("| API Endpoints | 15 |")
            .contains("| Data Entities | 5 |")
            .contains("| Message Flows | 11 |")
            .contains("Component Catalog")
            .contains("API Catalog")
            .contains("Data Entity Catalog")
            .contains("Dependency Matrix")
            .contains("Message Flow Catalog");
    }

    @Test
    void markdownGenerator_complexArchitecture_apiCatalog_listsAllEndpoints() {
        GeneratedDiagram diagram = markdownGenerator.generate(complexModel, DiagramType.API_CATALOG, config);

        assertThat(diagram.content())
            .contains("# API Catalog")
            .contains("## User Service")
            .contains("## Order Service")
            .contains("## Product Service")
            .contains("## Payment Service")
            // Verify specific endpoints
            .contains("POST | /api/v1/users")
            .contains("POST | /api/v1/orders")
            .contains("GET | /api/v1/products")
            .contains("POST | /api/v1/payments")
            // Verify authentication details
            .contains("JWT")
            .contains("JWT (Admin)");

        // Count number of endpoint rows
        long endpointCount = diagram.content().lines()
            .filter(line -> line.contains("| POST |") || line.contains("| GET |") || line.contains("| PUT |"))
            .count();
        assertThat(endpointCount).isGreaterThanOrEqualTo(15);
    }

    @Test
    void markdownGenerator_complexArchitecture_dependencyMatrix_showsAllDependencies() {
        GeneratedDiagram diagram = markdownGenerator.generate(complexModel, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.content())
            .contains("# Dependency Matrix")
            .contains("## User Service")
            .contains("## Order Service")
            .contains("## Product Service")
            .contains("spring-boot-starter-web")
            .contains("spring-boot-starter-data-jpa")
            .contains("spring-kafka")
            .contains("postgresql")
            .contains("spring-data-redis")
            .contains("spring-cloud-starter-openfeign");

        // Verify dependency counts
        assertThat(diagram.content())
            .contains("## Dependency Summary")
            .containsPattern("\\| Total Dependencies \\| \\d{2} \\|");
    }

    @Test
    void markdownGenerator_complexArchitecture_dataCatalog_documentsAllEntities() {
        String catalog = markdownGenerator.generateDataCatalog(complexModel);

        assertThat(catalog)
            .contains("# Data Entity Catalog")
            .contains("## users")
            .contains("## orders")
            .contains("## order_items")
            .contains("## products")
            .contains("## addresses")
            // Verify field details
            .contains("email")
            .contains("order_number")
            .contains("sku")
            .contains("user_id")
            // Verify data types
            .contains("varchar")
            .contains("bigint")
            .contains("timestamp")
            .contains("decimal");

        // Verify all entities are present
        assertThat(catalog).contains("## users", "## orders", "## order_items", "## products", "## addresses");
    }

    @Test
    void markdownGenerator_complexArchitecture_messageFlowCatalog_documentsKafkaTopics() {
        String catalog = markdownGenerator.generateMessageFlowCatalog(complexModel);

        assertThat(catalog)
            .contains("# Message Flow Catalog")
            .contains("## order.created")
            .contains("## payment.completed")
            .contains("## user.created")
            .contains("## inventory.updated")
            .contains("**Broker:** kafka")
            .contains("Order Service")
            .contains("Payment Service")
            .contains("Inventory Service");

        // Verify key topics are documented
        assertThat(catalog).containsAnyOf("## order.created", "## payment.completed", "## user.created", "## inventory.updated");
    }

    // === SOKRATES GENERATOR TESTS ===

    @Test
    void sokratesGenerator_complexArchitecture_generatesValidConfiguration() {
        GeneratedDiagram diagram = sokratesGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.name()).isEqualTo("sokrates");
        assertThat(diagram.fileExtension()).isEqualTo("json");
        assertThat(diagram.content())
            .contains("\"name\": \"E-Commerce Platform\"")
            .contains("\"version\": \"2.0.0\"")
            .contains("\"extensions\":")
            .contains("\"java\"")
            .contains("\"logicalComponents\":")
            .contains("User Service")
            .contains("Order Service")
            .contains("Product Service");

        // Verify JSON is properly balanced
        String content = diagram.content();
        long openBraces = content.chars().filter(ch -> ch == '{').count();
        long closeBraces = content.chars().filter(ch -> ch == '}').count();
        assertThat(openBraces).isEqualTo(closeBraces);
    }

    @Test
    void sokratesGenerator_complexArchitecture_includesAllComponents() {
        GeneratedDiagram diagram = sokratesGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);

        String content = diagram.content();

        // Verify all 6 microservices are included
        assertThat(content).contains("User Service");
        assertThat(content).contains("Order Service");
        assertThat(content).contains("Product Service");
        assertThat(content).contains("Payment Service");
        assertThat(content).contains("Notification Service");
        assertThat(content).contains("Inventory Service");

        // Verify infrastructure components
        assertThat(content).contains("API Gateway");
        assertThat(content).contains("Kafka Cluster");

        // Count logical components
        long componentCount = content.lines()
            .filter(line -> line.contains("\"name\":") && !line.contains("E-Commerce"))
            .count();
        assertThat(componentCount).isGreaterThanOrEqualTo(12);
    }

    @Test
    void sokratesGenerator_complexArchitecture_generatesPathPatterns() {
        GeneratedDiagram diagram = sokratesGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"pathPatterns\":")
            .contains("**/user")
            .contains("**/order")
            .contains("**/product")
            // Repository-based patterns
            .contains("user-service-repo/**")
            .contains("order-service-repo/**");
    }

    @Test
    void sokratesGenerator_complexArchitecture_includesMetadata() {
        GeneratedDiagram diagram = sokratesGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"metadata\":")
            .contains("\"technology\": \"Spring Boot 3.2\"")
            .contains("\"repository\":");

        // Verify cross-cutting concerns
        assertThat(diagram.content())
            .contains("\"crossCuttingConcerns\":")
            .contains("Test Code")
            .contains("Generated Code");

        // Verify analysis configuration
        assertThat(diagram.content())
            .contains("\"analysis\":")
            .contains("\"skipDuplication\": false")
            .contains("\"skipDependencies\": false");
    }

    // === PERFORMANCE TESTS ===

    @Test
    void generators_complexModel_performWithinReasonableTime() {
        long startTime = System.currentTimeMillis();

        // Generate all diagram types with Mermaid
        mermaidGenerator.generate(complexModel, DiagramType.C4_CONTEXT, config);
        mermaidGenerator.generate(complexModel, DiagramType.C4_CONTAINER, config);
        mermaidGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);
        mermaidGenerator.generate(complexModel, DiagramType.DEPENDENCY_GRAPH, config);
        mermaidGenerator.generate(complexModel, DiagramType.ER_DIAGRAM, config);
        mermaidGenerator.generate(complexModel, DiagramType.MESSAGE_FLOW, config);
        mermaidGenerator.generate(complexModel, DiagramType.SEQUENCE, config);

        // Generate Markdown documentation
        markdownGenerator.generateIndex(complexModel);
        markdownGenerator.generate(complexModel, DiagramType.API_CATALOG, config);
        markdownGenerator.generate(complexModel, DiagramType.DEPENDENCY_GRAPH, config);
        markdownGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);
        markdownGenerator.generateDataCatalog(complexModel);
        markdownGenerator.generateMessageFlowCatalog(complexModel);

        // Generate Sokrates config
        sokratesGenerator.generate(complexModel, DiagramType.C4_COMPONENT, config);

        long duration = System.currentTimeMillis() - startTime;

        // Should complete all generations in under 5 seconds for complex model
        assertThat(duration).isLessThan(5000);
    }
}
