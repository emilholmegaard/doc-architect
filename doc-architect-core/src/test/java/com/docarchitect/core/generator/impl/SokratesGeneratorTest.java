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
 * Unit tests for {@link SokratesGenerator}.
 */
class SokratesGeneratorTest {

    private SokratesGenerator generator;
    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        generator = new SokratesGenerator();
        config = GeneratorConfig.defaults();
    }

    @Test
    void getId_returnsCorrectId() {
        assertThat(generator.getId()).isEqualTo("sokrates");
    }

    @Test
    void getDisplayName_returnsCorrectName() {
        assertThat(generator.getDisplayName()).isEqualTo("Sokrates Configuration Generator");
    }

    @Test
    void getFileExtension_returnsJson() {
        assertThat(generator.getFileExtension()).isEqualTo("json");
    }

    @Test
    void getSupportedDiagramTypes_returnsComponentType() {
        var types = generator.getSupportedDiagramTypes();

        assertThat(types).contains(DiagramType.C4_COMPONENT);
        assertThat(types).hasSize(1);
    }

    @Test
    void generate_withNullModel_throwsException() {
        assertThatThrownBy(() ->
            generator.generate(null, DiagramType.C4_COMPONENT, config)
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
            generator.generate(model, DiagramType.C4_COMPONENT, null)
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
    void generate_withEmptyModel_generatesValidJson() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.name()).isEqualTo("sokrates");
        assertThat(diagram.fileExtension()).isEqualTo("json");
        assertThat(diagram.content())
            .contains("\"name\": \"TestProject\"")
            .contains("\"version\": \"1.0\"")
            .contains("\"extensions\":")
            .contains("\"scope\":")
            .contains("\"logicalComponents\":")
            .contains("\"crossCuttingConcerns\":")
            .contains("\"analysis\":")
            .contains("\"goals\":");
    }

    @Test
    void generate_withUnknownVersion_omitsVersionField() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "unknown", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"name\": \"TestProject\"")
            .doesNotContain("\"version\": \"unknown\"");
    }

    @Test
    void generate_withNoComponents_createsDefaultComponent() {
        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"logicalComponents\": [")
            .contains("\"name\": \"Default\"")
            .contains("\"pathPatterns\": [\"**/*\"]");
    }

    @Test
    void generate_withJavaComponents_includesJavaExtension() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"extensions\": [")
            .contains("\"java\"");
    }

    @Test
    void generate_withPythonComponents_includesPyExtension() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "FastAPI", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"extensions\": [")
            .contains("\"py\"");
    }

    @Test
    void generate_withJavaScriptComponents_includesJsExtension() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Express", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"extensions\": [")
            .contains("\"js\"");
    }

    @Test
    void generate_withDotNetComponents_includesCsExtension() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "ASP.NET", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"extensions\": [")
            .contains("\"cs\"");
    }

    @Test
    void generate_withComponents_createsLogicalComponents() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        Component library = new Component(
            "lib1", "UtilsLibrary", ComponentType.LIBRARY,
            "Utility functions", "Java", "repo2", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service, library), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"logicalComponents\": [")
            .contains("\"name\": \"UserService\"")
            .contains("\"description\": \"Manages users\"")
            .contains("\"name\": \"UtilsLibrary\"")
            .contains("\"description\": \"Utility functions\"")
            .contains("\"pathPatterns\":");
    }

    @Test
    void generate_withServiceComponent_generatesServicePathPatterns() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "user-repo", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"pathPatterns\": [")
            .contains("**/user")
            .contains("user-repo/**");
    }

    @Test
    void generate_withDatabaseComponent_generatesDatabasePathPatterns() {
        Component database = new Component(
            "db1", "PostgreSQL", ComponentType.DATABASE,
            "Main database", "PostgreSQL 14", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(database), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"pathPatterns\": [")
            .contains("migrations")
            .contains("schema");
    }

    @Test
    void generate_withApiGatewayComponent_generatesApiGatewayPathPatterns() {
        Component gateway = new Component(
            "gw1", "APIGateway", ComponentType.API_GATEWAY,
            "API Gateway", "Kong", "gateway-repo", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(gateway), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"pathPatterns\": [")
            .contains("gateway")
            .contains("api");
    }

    @Test
    void generate_withComponentMetadata_includesMetadata() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "user-repo", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"metadata\": {")
            .contains("\"technology\": \"Spring Boot\"")
            .contains("\"repository\": \"user-repo\"");
    }

    @Test
    void generate_includesScope_withIgnorePatterns() {
        ArchitectureModel model = createSimpleModel();

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"scope\": {")
            .contains("\"srcRoot\": \".\"")
            .contains("\"ignore\": [")
            .contains("\"target/**\"")
            .contains("\"build/**\"")
            .contains("\"node_modules/**\"")
            .contains("\"*.test.*\"");
    }

    @Test
    void generate_includesCrossCuttingConcerns() {
        ArchitectureModel model = createSimpleModel();

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"crossCuttingConcerns\": [")
            .contains("\"name\": \"Test Code\"")
            .contains("\"name\": \"Generated Code\"");
    }

    @Test
    void generate_includesAnalysisConfiguration() {
        ArchitectureModel model = createSimpleModel();

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"analysis\": {")
            .contains("\"skipDuplication\": false")
            .contains("\"skipDependencies\": false")
            .contains("\"cacheSourceFiles\": true");
    }

    @Test
    void generate_includesGoals() {
        ArchitectureModel model = createSimpleModel();

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        assertThat(diagram.content())
            .contains("\"goals\": {")
            .contains("\"main\": [")
            .contains("\"description\": \"Maintain component separation\"")
            .contains("\"type\": \"METRIC\"")
            .contains("\"target\": \"COMPONENT_DEPENDENCIES\"");
    }

    @Test
    void generate_escapeSpecialCharacters_handlesJsonSpecialChars() {
        Component service = new Component(
            "svc1", "User\"Service", ComponentType.SERVICE,
            "Manages\\users\nwith\ttabs", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        // Should escape quotes, backslashes, newlines, and tabs
        assertThat(diagram.content())
            .contains("User\\\"Service")
            .contains("Manages\\\\users\\nwith\\ttabs");
    }

    @Test
    void generate_validJson_canBeParsed() {
        Component service = new Component(
            "svc1", "UserService", ComponentType.SERVICE,
            "Manages users", "Spring Boot", "repo1", Map.of()
        );

        ArchitectureModel model = new ArchitectureModel(
            "TestProject", "1.0", List.of(), List.of(service), List.of(),
            List.of(), List.of(), List.of(), List.of()
        );

        GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_COMPONENT, config);

        // Basic JSON structure validation
        String content = diagram.content();
        assertThat(content)
            .startsWith("{")
            .endsWith("}\n")
            .contains("\"name\":")
            .contains("\"logicalComponents\":");

        // Count braces to ensure they're balanced
        long openBraces = content.chars().filter(ch -> ch == '{').count();
        long closeBraces = content.chars().filter(ch -> ch == '}').count();
        assertThat(openBraces).isEqualTo(closeBraces);

        // Count brackets to ensure they're balanced
        long openBrackets = content.chars().filter(ch -> ch == '[').count();
        long closeBrackets = content.chars().filter(ch -> ch == ']').count();
        assertThat(openBrackets).isEqualTo(closeBrackets);
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
