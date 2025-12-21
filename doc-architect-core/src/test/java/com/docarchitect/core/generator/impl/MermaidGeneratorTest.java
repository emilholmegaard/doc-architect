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
 * Unit tests for {@link MermaidGenerator}.
 */
class MermaidGeneratorTest {

    private MermaidGenerator generator;
    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        generator = new MermaidGenerator();
        config = GeneratorConfig.defaults();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(generator.getId()).isEqualTo("mermaid");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(generator.getDisplayName()).isEqualTo("Mermaid Diagram Generator");
    }

    @Test
    void getFileExtension_returnsMd() {
        assertThat(generator.getFileExtension()).isEqualTo("md");
    }

    @Test
    void getSupportedDiagramTypes_returnsAllSupportedTypes() {
        var types = generator.getSupportedDiagramTypes();

        assertThat(types).contains(
            DiagramType.C4_CONTEXT,
            DiagramType.C4_CONTAINER,
            DiagramType.C4_COMPONENT,
            DiagramType.DEPENDENCY_GRAPH,
            DiagramType.ER_DIAGRAM,
            DiagramType.MESSAGE_FLOW,
            DiagramType.SEQUENCE
        );
    }

    @Test
    void generate_withNullModel_throwsException() {
        assertThatThrownBy(() ->
            generator.generate(null, DiagramType.C4_CONTEXT, config)
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
            generator.generate(model, DiagramType.C4_CONTEXT, null)
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
    void generate_c4Context_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_CONTEXT, config);

        assertThat(diagram.name()).isEqualTo("c4-context");
        assertThat(diagram.fileExtension()).isEqualTo("md");
        assertThat(diagram.content())
            .contains("```mermaid")
            .contains("C4Context")
            .contains("No components found");
    }

    @Test
    void generate_c4Context_withComponents_generatesValidDiagram() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        Relationship rel = new Relationship(
            "svc1", "db1", RelationshipType.USES, "Stores data", "JDBC"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(rel), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_CONTEXT, config);

        assertThat(diagram.content())
            .contains("C4Context")
            .contains("UserService")
            .contains("Rel(");
    }

    @Test
    void generate_c4Container_withComponents_generatesValidDiagram() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_CONTAINER, config);

        assertThat(diagram.name()).isEqualTo("c4-container");
        assertThat(diagram.content())
            .contains("C4Container")
            .contains("UserService")
            .contains("Spring Boot");
    }

    @Test
    void generate_c4Component_withComponents_generatesValidDiagram() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.name()).isEqualTo("c4-component");
        assertThat(diagram.content())
            .contains("C4Component")
            .contains("UserService");
    }

    @Test
    void generate_dependencyGraph_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.name()).isEqualTo("dependency-graph");
        assertThat(diagram.content())
            .contains("graph LR")
            .contains("No dependencies found");
    }

    @Test
    void generate_dependencyGraph_withDependencies_generatesValidDiagram() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        Dependency dep = new Dependency(
            "svc1", "org.springframework", "spring-web", "5.3.0", "compile", true
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(dep),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.DEPENDENCY_GRAPH, config);

        assertThat(diagram.content())
            .contains("graph LR")
            .contains("UserService")
            .contains("spring-web")
            .contains("-->");
    }

    @Test
    void generate_erDiagram_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.ER_DIAGRAM, config);

        assertThat(diagram.name()).isEqualTo("er-diagram");
        assertThat(diagram.content())
            .contains("erDiagram")
            .contains("No data entities found");
    }

    @Test
    void generate_erDiagram_withEntities_generatesValidDiagram() {
        DataEntity.Field field1 = new DataEntity.Field("id", "bigint", false, "Primary key");
        DataEntity.Field field2 = new DataEntity.Field("name", "varchar", false, "User name");

        DataEntity entity = new DataEntity(
            "svc1", "users", "table", List.of(field1, field2), "id", "User table"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(entity)
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.ER_DIAGRAM, config);

        assertThat(diagram.content())
            .contains("erDiagram")
            .contains("USERS")
            .contains("bigint id")
            .contains("varchar name");
    }

    @Test
    void generate_erDiagram_onlyPrimaryKeyFieldMarkedAsPK() {
        // Create entity fields - only Id should be marked as PK
        DataEntity.Field idField = new DataEntity.Field("Id", "INTEGER", false, null);
        DataEntity.Field nameField = new DataEntity.Field("Name", "NVARCHAR", false, null);
        DataEntity.Field descField = new DataEntity.Field("Description", "NVARCHAR", true, null);
        DataEntity.Field priceField = new DataEntity.Field("Price", "DECIMAL", false, null);

        DataEntity entity = new DataEntity(
            "CatalogItem",
            "CatalogItems",
            "table",
            List.of(idField, nameField, descField, priceField),
            "Id",  // Primary key
            "Catalog item entity"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(entity)
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.ER_DIAGRAM, config);

        String content = diagram.content();

        // Verify only Id field is marked with PK
        assertThat(content)
            .contains("erDiagram")
            .contains("CATALOGITEMS")
            .contains("INTEGER Id PK")
            .doesNotContain("NVARCHAR Name PK")
            .doesNotContain("NVARCHAR Description PK")
            .doesNotContain("DECIMAL Price PK");

        // Verify regular fields are present without PK marker
        assertThat(content)
            .contains("NVARCHAR Name")
            .contains("NVARCHAR Description")
            .contains("DECIMAL Price");
    }

    @Test
    void generate_erDiagram_noPrimaryKeyDefined_noFieldsMarkedAsPK() {
        DataEntity.Field field1 = new DataEntity.Field("name", "varchar", false, null);
        DataEntity.Field field2 = new DataEntity.Field("email", "varchar", false, null);

        DataEntity entity = new DataEntity(
            "svc1", "contacts", "table", List.of(field1, field2), null, "Contact table"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(entity)
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.ER_DIAGRAM, config);

        String content = diagram.content();

        // Verify no fields are marked as PK
        assertThat(content)
            .contains("erDiagram")
            .contains("CONTACTS")
            .contains("varchar name")
            .contains("varchar email")
            .doesNotContain(" PK");
    }

    @Test
    void generate_messageFlow_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.MESSAGE_FLOW, config);

        assertThat(diagram.name()).isEqualTo("message-flow");
        assertThat(diagram.content())
            .contains("graph TB")
            .contains("No message flows found");
    }

    @Test
    void generate_messageFlow_withFlows_generatesValidDiagram() {
        Component publisher = new Component(
            "pub1", "Publisher", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        Component subscriber = new Component(
            "sub1", "Subscriber", ComponentType.SERVICE,
            null, "Spring Boot", "repo2", Map.of()
        );

        MessageFlow flow = new MessageFlow(
            "pub1", "sub1", "user.created", "UserCreatedEvent", "UserSchema", "kafka"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(publisher, subscriber), List.of(),
            List.of(), List.of(), List.of(flow), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.MESSAGE_FLOW, config);

        assertThat(diagram.content())
            .contains("graph TB")
            .contains("Publisher")
            .contains("Subscriber")
            .contains("user.created");
    }

    @Test
    void generate_sequence_withEmptyModel_generatesPlaceholder() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.SEQUENCE, config);

        assertThat(diagram.name()).isEqualTo("sequence");
        assertThat(diagram.content())
            .contains("sequenceDiagram")
            .contains("No API endpoints found");
    }

    @Test
    void generate_sequence_withEndpoints_generatesValidDiagram() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            null, "Spring Boot", "repo1", Map.of()
        );

        ApiEndpoint endpoint = new ApiEndpoint(
            "svc1", ApiType.REST, "/users", "GET", "Get all users", null, null, "Bearer"
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(endpoint), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.SEQUENCE, config);

        assertThat(diagram.content())
            .contains("sequenceDiagram")
            .contains("Client")
            .contains("UserService")
            .contains("/users");
    }

    @Test
    void generate_escapeSpecialCharacters_handlesQuotesAndNewlines() {
        Component service = new Component(
            "svc1", "User \"Service\"", ComponentType.SERVICE,
            "Manages\nusers", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_CONTEXT, config);

        // Should escape quotes and replace newlines with spaces
        assertThat(diagram.content())
            .contains("User 'Service'")
            .doesNotContain("User \"Service\"")
            .contains("Manages users");
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
