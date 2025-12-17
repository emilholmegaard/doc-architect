package com.docarchitect.core.generator.impl;

import com.docarchitect.core.generator.DiagramType;
import com.docarchitect.core.generator.GeneratedDiagram;
import com.docarchitect.core.generator.GeneratorConfig;
import com.docarchitect.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MarkdownGenerator}.
 */
class MarkdownGeneratorTest {

    private MarkdownGenerator generator;
    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        generator = new MarkdownGenerator();
        config = GeneratorConfig.defaults();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(generator.getId()).isEqualTo("markdown");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(generator.getDisplayName()).isEqualTo("Markdown Documentation Generator");
    }

    @Test
    void getFileExtension_returnsMd() {
        assertThat(generator.getFileExtension()).isEqualTo("md");
    }

    @Test
    void getSupportedDiagramTypes_returnsDocumentationTypes() {
        var types = generator.getSupportedDiagramTypes();

        assertThat(types).contains(
            DiagramType.API_CATALOG,
            DiagramType.DEPENDENCY_GRAPH,
            DiagramType.C4_COMPONENT
        );
    }

    @Test
    void generate_withNullModel_throwsException() {
        assertThatThrownBy(() ->
            generator.generate(null, DiagramType.API_CATALOG, config)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void generate_withNullType_throwsException() {
        ArchitectureModel model = createSimpleModel();

        assertThatThrownBy(() ->
            generator.generate(model, null, config)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void generate_withNullConfig_throwsException() {
        ArchitectureModel model = createSimpleModel();

        assertThatThrownBy(() ->
            generator.generate(model, DiagramType.API_CATALOG, null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void generate_withUnsupportedType_throwsException() {
        ArchitectureModel model = createSimpleModel();

        assertThatThrownBy(() ->
            generator.generate(model, DiagramType.DEPLOYMENT, config)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Unsupported diagram type");
    }

    @Test
    void generateIndex_withEmptyModel_generatesBasicIndex() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        String index = generator.generateIndex(model);

        assertThat(index)
            .contains("# TestProject - Architecture Documentation")
            .contains("**Version:** 1.0")
            .contains("## Architecture Statistics")
            .contains("| Components | 0 |")
            .contains("No components found");
    }

    @Test
    void generateIndex_withFullModel_generatesCompleteIndex() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "svc1", ApiType.REST, "/users", "GET", "Get users", null, null, null
        );

        DataEntity.Field field = new DataEntity.Field("id", "bigint", false, "Primary key");
        DataEntity entity = new DataEntity("svc1", "users", "table", List.of(field), "id", null);

        Dependency dep = new Dependency(
            "svc1", "org.springframework", "spring-web", "5.3.0", "compile", true
        );

        MessageFlow flow = new MessageFlow(
            "svc1", null, "user.created", "UserCreatedEvent", null, "kafka"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(dep),
            List.of(), List.of(endpoint), List.of(flow), List.of(entity)
        );

        String index = generator.generateIndex(model);

        assertThat(index)
            .contains("| Components | 1 |")
            .contains("| Dependencies | 1 |")
            .contains("| API Endpoints | 1 |")
            .contains("| Data Entities | 1 |")
            .contains("| Message Flows | 1 |")
            .contains("Component Catalog")
            .contains("API Catalog")
            .contains("Data Entity Catalog")
            .contains("Dependency Matrix")
            .contains("Message Flow Catalog");
    }

    @Test
    void generate_apiCatalog_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.API_CATALOG, config);

        assertThat(diagram.name()).isEqualTo("api-catalog");
        assertThat(diagram.fileExtension()).isEqualTo("md");
        assertThat(diagram.content())
            .contains("# API Catalog")
            .contains("No API endpoints found");
    }

    @Test
    void generate_apiCatalog_withEndpoints_generatesValidCatalog() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        ApiEndpoint endpoint1 = new ApiEndpoint(
            "svc1", ApiType.REST, "/users", "GET", "Get all users",
            null, "UserList", "Bearer"
        );

        ApiEndpoint endpoint2 = new ApiEndpoint(
            "svc1", ApiType.REST, "/users/{id}", "GET", "Get user by ID",
            null, "User", "Bearer"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(endpoint1, endpoint2), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.API_CATALOG, config);

        assertThat(diagram.content())
            .contains("# API Catalog")
            .contains("## UserService")
            .contains("| Method | Path | Type | Authentication | Description |")
            .contains("| GET | /users | REST | Bearer | Get all users |")
            .contains("| GET | /users/{id} | REST | Bearer | Get user by ID |")
            .contains("### Endpoint Details")
            .contains("#### GET /users")
            .contains("**Response Schema:** `UserList`");
    }

    @Test
    void generate_dependencyMatrix_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.name()).isEqualTo("dependency-graph");
        assertThat(diagram.content())
            .contains("# Dependency Matrix")
            .contains("No external dependencies found");
    }

    @Test
    void generate_dependencyMatrix_withDependencies_generatesValidMatrix() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        Dependency dep1 = new Dependency(
            "svc1", "org.springframework", "spring-web", "5.3.0", "compile", true
        );

        Dependency dep2 = new Dependency(
            "svc1", "org.springframework", "spring-data-jpa", "5.3.0", "compile", true
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(dep1, dep2),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.content())
            .contains("# Dependency Matrix")
            .contains("## UserService")
            .contains("| Artifact Group | Artifact | Version | Scope | Type |")
            .contains("| org.springframework | spring-web | 5.3.0 | compile | Direct |")
            .contains("| org.springframework | spring-data-jpa | 5.3.0 | compile | Direct |")
            .contains("## Dependency Summary")
            .contains("| Total Dependencies | 2 |")
            .contains("| Direct Dependencies | 2 |");
    }

    @Test
    void generate_componentCatalog_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.name()).isEqualTo("c4-component");
        assertThat(diagram.content())
            .contains("# Component Catalog")
            .contains("No components found");
    }

    @Test
    void generate_componentCatalog_withComponents_generatesValidCatalog() {
        Component service1 = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of("version", "1.0")
        );

        Component service2 = new Component(
            "svc2", "OrderService", ComponentType.SERVICE,
            "Manages orders", "Spring Boot", "repo2", Map.of("version", "2.0")
        );

        Component database = new Component(
            "db1", "PostgreSQL", ComponentType.DATABASE,
            "Main database", "PostgreSQL 14", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service1, service2, database), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("# Component Catalog")
            .contains("## SERVICEs")
            .contains("| Name | Technology | Repository | Description |")
            .contains("| UserService | Spring Boot | repo1 | Manages users |")
            .contains("| OrderService | Spring Boot | repo2 | Manages orders |")
            .contains("## DATABASEs")
            .contains("| PostgreSQL | PostgreSQL 14 | repo1 | Main database |")
            .contains("## Component Details")
            .contains("### UserService")
            .contains("**Metadata:**")
            .contains("version: 1.0");
    }

    @Test
    void generateDataCatalog_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        String catalog = generator.generateDataCatalog(model);

        assertThat(catalog)
            .contains("# Data Entity Catalog")
            .contains("No data entities found");
    }

    @Test
    void generateDataCatalog_withEntities_generatesValidCatalog() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        DataEntity.Field field1 = new DataEntity.Field("id", "bigint", false, "Primary key");
        DataEntity.Field field2 = new DataEntity.Field("name", "varchar", false, "User name");
        DataEntity.Field field3 = new DataEntity.Field("email", "varchar", true, "User email");

        DataEntity entity = new DataEntity(
            "svc1", "users", "table", List.of(field1, field2, field3), "id", "User table"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of(entity)
        );

        String catalog = generator.generateDataCatalog(model);

        assertThat(catalog)
            .contains("# Data Entity Catalog")
            .contains("## users")
            .contains("User table")
            .contains("**Primary Key:** id")
            .contains("**Component:** UserService")
            .contains("### Fields")
            .contains("| Field | Data Type | Nullable | Description |")
            .contains("| id | bigint | No | Primary key |")
            .contains("| name | varchar | No | User name |")
            .contains("| email | varchar | Yes | User email |");
    }

    @Test
    void generateMessageFlowCatalog_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        String catalog = generator.generateMessageFlowCatalog(model);

        assertThat(catalog)
            .contains("# Message Flow Catalog")
            .contains("No message flows found");
    }

    @Test
    void generateMessageFlowCatalog_withFlows_generatesValidCatalog() {
        Component publisher = new Component(
            "pub1", "Publisher", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        Component subscriber = new Component(
            "sub1", "Subscriber", ComponentType.SERVICE,
            null, "Spring Boot", "repo2", Map.of()
        );

        MessageFlow flow1 = new MessageFlow(
            "pub1", "sub1", "user.created", "UserCreatedEvent", "UserSchema", "kafka"
        );

        MessageFlow flow2 = new MessageFlow(
            "pub1", null, "user.created", "UserUpdatedEvent", "UserSchema", "kafka"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(publisher, subscriber), List.of(),
            List.of(), List.of(), List.of(flow1, flow2), List.of()
        );

        String catalog = generator.generateMessageFlowCatalog(model);

        assertThat(catalog)
            .contains("# Message Flow Catalog")
            .contains("## user.created")
            .contains("**Broker:** kafka")
            .contains("| Publisher | Subscriber | Message Type | Schema |")
            .contains("| Publisher | Subscriber | UserCreatedEvent | UserSchema |")
            .contains("| Publisher | - | UserUpdatedEvent | UserSchema |");
    }

    @Test
    void generate_escapeSpecialCharacters_handlesMarkdownSpecialChars() {
        Component service = new Component(
            "svc1", "User|Service", ComponentType.SERVICE,
            "Manages\nusers", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        // Should escape pipes in table cells
        assertThat(diagram.content())
            .contains("User\\|Service");
    }

    /**
     * Helper method to create a simple model for testing.
     */
    private ArchitectureModel createSimpleModel() {
        return new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );
    }
}
