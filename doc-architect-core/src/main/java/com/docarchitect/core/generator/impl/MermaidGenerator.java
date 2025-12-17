package com.docarchitect.core.generator.impl;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docarchitect.core.generator.DiagramGenerator;
import com.docarchitect.core.generator.DiagramType;
import com.docarchitect.core.generator.GeneratedDiagram;
import com.docarchitect.core.generator.GeneratorConfig;
import com.docarchitect.core.model.ApiEndpoint;
import com.docarchitect.core.model.ArchitectureModel;
import com.docarchitect.core.model.Component;
import com.docarchitect.core.model.ComponentType;
import com.docarchitect.core.model.DataEntity;
import com.docarchitect.core.model.Dependency;
import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.model.Relationship;

/**
 * Generates Mermaid diagram definitions from architecture models.
 *
 * <p>Supports multiple diagram types:
 * <ul>
 *   <li>Flowcharts for component/dependency visualization</li>
 *   <li>Entity-relationship diagrams with cardinality notation</li>
 *   <li>Sequence diagrams for API flows</li>
 *   <li>C4 model diagrams (context, container, component)</li>
 *   <li>Message flow diagrams for event-driven systems</li>
 * </ul>
 *
 * <p>Output format is Mermaid markdown (*.md files with embedded mermaid code blocks).
 *
 * @see <a href="https://mermaid.js.org/">Mermaid Documentation</a>
 */
public class MermaidGenerator implements DiagramGenerator {

    private static final Logger log = LoggerFactory.getLogger(MermaidGenerator.class);

    // Generator identification
    private static final String GENERATOR_ID = "mermaid";
    private static final String GENERATOR_DISPLAY_NAME = "Mermaid Diagram Generator";
    private static final String FILE_EXTENSION = "md";

    // Markdown formatting
    private static final String MARKDOWN_HEADER_PREFIX = "# ";
    private static final String MARKDOWN_NEWLINE = "\n";
    private static final String CODE_BLOCK_START = "```mermaid\n";
    private static final String CODE_BLOCK_END = "```\n";

    // C4 model keywords
    private static final String C4_CONTEXT = "C4Context";
    private static final String C4_CONTAINER = "C4Container";
    private static final String C4_COMPONENT = "C4Component";

    // Mermaid diagram types
    private static final String GRAPH_LR = "graph LR\n";
    private static final String GRAPH_TB = "graph TB\n";
    private static final String SEQUENCE_DIAGRAM = "sequenceDiagram\n";
    private static final String ER_DIAGRAM = "erDiagram\n";

    // Sanitization patterns
    private static final String ID_SANITIZATION_PATTERN = "[^a-zA-Z0-9_]";
    private static final String TABLE_NAME_SANITIZATION_PATTERN = "[^a-zA-Z0-9_]";

    // Placeholder nodes for empty graphs
    private static final String NO_COMPONENTS_NODE = "  A[No components found]\n";
    private static final String NO_DEPENDENCIES_NODE = "  A[No dependencies found]\n";
    private static final String NO_MESSAGE_FLOWS_NODE = "  A[No message flows found]\n";
    private static final String NO_API_ENDPOINTS_NODE = "  participant Client\n  participant System\n  Note over Client,System: No API endpoints found\n";

    @Override
    public String getId() {
        return GENERATOR_ID;
    }

    @Override
    public String getDisplayName() {
        return GENERATOR_DISPLAY_NAME;
    }

    @Override
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    @Override
    public Set<DiagramType> getSupportedDiagramTypes() {
        return Set.of(
            DiagramType.C4_CONTEXT,
            DiagramType.C4_CONTAINER,
            DiagramType.C4_COMPONENT,
            DiagramType.DEPENDENCY_GRAPH,
            DiagramType.ER_DIAGRAM,
            DiagramType.MESSAGE_FLOW,
            DiagramType.SEQUENCE
        );
    }

    @Override
    public GeneratedDiagram generate(ArchitectureModel model, DiagramType type, GeneratorConfig config) {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(config, "config must not be null");

        if (!getSupportedDiagramTypes().contains(type)) {
            throw new IllegalArgumentException("Unsupported diagram type: " + type);
        }

        log.debug("Generating Mermaid diagram for type: {}", type);

        String content = switch (type) {
            case C4_CONTEXT -> generateC4Context(model, config);
            case C4_CONTAINER -> generateC4Container(model, config);
            case C4_COMPONENT -> generateC4Component(model, config);
            case DEPENDENCY_GRAPH -> generateDependencyGraph(model, config);
            case ER_DIAGRAM -> generateErDiagram(model, config);
            case MESSAGE_FLOW -> generateMessageFlow(model, config);
            case SEQUENCE -> generateSequenceDiagram(model, config);
            default -> throw new IllegalArgumentException("Unsupported diagram type: " + type);
        };

        String diagramName = type.name().toLowerCase().replace('_', '-');
        log.info("Generated Mermaid diagram: {}", diagramName);

        return new GeneratedDiagram(diagramName, content, getFileExtension());
    }

    /**
     * Generates a C4 Context diagram showing system-level components.
     */
    private String generateC4Context(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("C4 Context Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(C4_CONTEXT).append(MARKDOWN_NEWLINE);
        sb.append("  title System Context Diagram for ").append(model.projectName()).append(MARKDOWN_NEWLINE.repeat(2));

        if (model.components().isEmpty()) {
            sb.append("  System(placeholder, \"No components found\", \"\")\n");
        } else {
            // Group by type - systems vs external
            List<Component> systems = model.components().stream()
                .filter(c -> c.type() == ComponentType.SERVICE || c.type() == ComponentType.MODULE)
                .limit(config.includeExternal() ? Integer.MAX_VALUE : 20)
                .toList();

            for (Component comp : systems) {
                String desc = comp.description() != null ? comp.description() : comp.technology() != null ? comp.technology() : "";
                sb.append("  System(").append(sanitizeId(comp.id())).append(", \"")
                    .append(escape(comp.name())).append("\", \"")
                    .append(escape(desc)).append("\")\n");
            }

            // Add relationships
            sb.append(MARKDOWN_NEWLINE);
            for (Relationship rel : model.relationships()) {
                String tech = rel.technology() != null ? rel.technology() : "";
                sb.append("  Rel(").append(sanitizeId(rel.sourceId())).append(", ")
                    .append(sanitizeId(rel.targetId())).append(", \"")
                    .append(escape(rel.type().toString())).append("\", \"")
                    .append(escape(tech)).append("\")\n");
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates a C4 Container diagram showing internal components.
     */
    private String generateC4Container(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("C4 Container Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(C4_CONTAINER).append(MARKDOWN_NEWLINE);
        sb.append("  title Container Diagram for ").append(model.projectName()).append(MARKDOWN_NEWLINE.repeat(2));

        if (model.components().isEmpty()) {
            sb.append("  Container(placeholder, \"No components found\", \"\", \"\")\n");
        } else {
            for (Component comp : model.components()) {
                String tech = comp.technology() != null ? comp.technology() : "";
                String desc = comp.description() != null ? comp.description() : "";
                sb.append("  Container(").append(sanitizeId(comp.id())).append(", \"")
                    .append(escape(comp.name())).append("\", \"")
                    .append(escape(tech)).append("\", \"")
                    .append(escape(desc)).append("\")\n");
            }

            // Add relationships
            sb.append(MARKDOWN_NEWLINE);
            for (Relationship rel : model.relationships()) {
                String tech = rel.technology() != null ? rel.technology() : "";
                sb.append("  Rel(").append(sanitizeId(rel.sourceId())).append(", ")
                    .append(sanitizeId(rel.targetId())).append(", \"")
                    .append(escape(rel.type().toString())).append("\", \"")
                    .append(escape(tech)).append("\")\n");
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates a C4 Component diagram showing component-level details.
     */
    private String generateC4Component(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("C4 Component Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(C4_COMPONENT).append(MARKDOWN_NEWLINE);
        sb.append("  title Component Diagram for ").append(model.projectName()).append(MARKDOWN_NEWLINE.repeat(2));

        if (model.components().isEmpty()) {
            sb.append("  Component(placeholder, \"No components found\", \"\", \"\")\n");
        } else {
            for (Component comp : model.components()) {
                String tech = comp.technology() != null ? comp.technology() : "";
                String desc = comp.description() != null ? comp.description() : comp.type().toString();
                sb.append("  Component(").append(sanitizeId(comp.id())).append(", \"")
                    .append(escape(comp.name())).append("\", \"")
                    .append(escape(tech)).append("\", \"")
                    .append(escape(desc)).append("\")\n");
            }

            // Add relationships
            sb.append(MARKDOWN_NEWLINE);
            for (Relationship rel : model.relationships()) {
                String desc = rel.description() != null ? rel.description() : rel.type().toString();
                sb.append("  Rel(").append(sanitizeId(rel.sourceId())).append(", ")
                    .append(sanitizeId(rel.targetId())).append(", \"")
                    .append(escape(desc)).append("\")\n");
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates a dependency graph as a flowchart.
     */
    private String generateDependencyGraph(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("Dependency Graph").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(GRAPH_LR);

        if (model.components().isEmpty() && model.dependencies().isEmpty()) {
            sb.append(NO_DEPENDENCIES_NODE);
        } else {
            // Component nodes
            Set<String> addedNodes = new HashSet<>();
            for (Component comp : model.components()) {
                String nodeId = sanitizeId(comp.id());
                addedNodes.add(nodeId);
                sb.append("  ").append(nodeId).append("[\"").append(escape(comp.name())).append("\"]\n");
            }

            // Dependency edges - group by component
            Map<String, List<Dependency>> depsByComponent = model.dependencies().stream()
                .collect(Collectors.groupingBy(Dependency::sourceComponentId));

            for (Map.Entry<String, List<Dependency>> entry : depsByComponent.entrySet()) {
                String sourceId = sanitizeId(entry.getKey());
                for (Dependency dep : entry.getValue()) {
                    String targetId = sanitizeId(dep.artifactId());
                    if (!addedNodes.contains(targetId)) {
                        sb.append("  ").append(targetId).append("[\"")
                            .append(escape(dep.artifactId())).append("\"]\n");
                        addedNodes.add(targetId);
                    }
                    sb.append("  ").append(sourceId).append(" --> ").append(targetId).append("\n");
                }
            }

            // Component relationships
            for (Relationship rel : model.relationships()) {
                String sourceId = sanitizeId(rel.sourceId());
                String targetId = sanitizeId(rel.targetId());
                sb.append("  ").append(sourceId).append(" -.-> ").append(targetId).append("\n");
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates an Entity-Relationship diagram with proper cardinality.
     */
    private String generateErDiagram(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("Entity-Relationship Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(ER_DIAGRAM).append(MARKDOWN_NEWLINE);

        if (model.dataEntities().isEmpty()) {
            sb.append("  PLACEHOLDER {\n");
            sb.append("    string note \"No data entities found\"\n");
            sb.append("  }\n");
        } else {
            // Entity definitions
            for (DataEntity entity : model.dataEntities()) {
                sb.append("  ").append(sanitizeTableName(entity.name())).append(" {\n");

                if (entity.fields().isEmpty()) {
                    sb.append("    string placeholder \"No fields defined\"\n");
                } else {
                    for (DataEntity.Field field : entity.fields()) {
                        String dataType = field.dataType() != null ? field.dataType() : "string";
                        String nullable = field.nullable() ? "" : " PK";
                        String comment = field.description() != null ? " \"" + escape(field.description()) + "\"" : "";
                        sb.append("    ").append(dataType).append(" ")
                            .append(field.name()).append(nullable).append(comment).append("\n");
                    }
                }

                sb.append("  }\n");
            }

            // Relationships between entities (inferred from naming conventions)
            // For simplicity, we'll look for foreign key patterns
            for (DataEntity entity : model.dataEntities()) {
                for (DataEntity.Field field : entity.fields()) {
                    if (field.name().endsWith("_id") || field.name().endsWith("Id")) {
                        String potentialTarget = field.name().replaceAll("(_id|Id)$", "");
                        // Check if a matching entity exists
                        boolean targetExists = model.dataEntities().stream()
                            .anyMatch(e -> e.name().equalsIgnoreCase(potentialTarget) ||
                                          e.name().equalsIgnoreCase(potentialTarget + "s"));

                        if (targetExists) {
                            String targetEntity = model.dataEntities().stream()
                                .filter(e -> e.name().equalsIgnoreCase(potentialTarget) ||
                                           e.name().equalsIgnoreCase(potentialTarget + "s"))
                                .findFirst()
                                .map(DataEntity::name)
                                .orElse(potentialTarget);

                            sb.append("  ").append(sanitizeTableName(targetEntity))
                                .append(" ||--o{ ")
                                .append(sanitizeTableName(entity.name()))
                                .append(" : \"has\"\n");
                        }
                    }
                }
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates a message flow diagram for event-driven systems.
     */
    private String generateMessageFlow(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("Message Flow Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(GRAPH_TB).append(MARKDOWN_NEWLINE);

        if (model.messageFlows().isEmpty()) {
            sb.append(NO_MESSAGE_FLOWS_NODE);
        } else {
            Set<String> addedNodes = new HashSet<>();

            // Add all components involved in messaging
            for (MessageFlow flow : model.messageFlows()) {
                if (flow.publisherComponentId() != null) {
                    String pubId = sanitizeId(flow.publisherComponentId());
                    if (!addedNodes.contains(pubId)) {
                        String name = getComponentName(model, flow.publisherComponentId());
                        sb.append("  ").append(pubId).append("[\"").append(escape(name)).append("\"]\n");
                        addedNodes.add(pubId);
                    }
                }

                if (flow.subscriberComponentId() != null) {
                    String subId = sanitizeId(flow.subscriberComponentId());
                    if (!addedNodes.contains(subId)) {
                        String name = getComponentName(model, flow.subscriberComponentId());
                        sb.append("  ").append(subId).append("[\"").append(escape(name)).append("\"]\n");
                        addedNodes.add(subId);
                    }
                }

                // Add topic as a node
                String topicId = sanitizeId(flow.topic());
                if (!addedNodes.contains(topicId)) {
                    sb.append("  ").append(topicId).append("{{\"").append(escape(flow.topic())).append("\"}}\n");
                    addedNodes.add(topicId);
                }
            }

            sb.append(MARKDOWN_NEWLINE);

            // Add flows
            for (MessageFlow flow : model.messageFlows()) {
                String topicId = sanitizeId(flow.topic());

                if (flow.publisherComponentId() != null) {
                    String pubId = sanitizeId(flow.publisherComponentId());
                    String msgType = flow.messageType() != null ? flow.messageType() : "message";
                    sb.append("  ").append(pubId).append(" -->|\"").append(escape(msgType))
                        .append("\"| ").append(topicId).append("\n");
                }

                if (flow.subscriberComponentId() != null) {
                    String subId = sanitizeId(flow.subscriberComponentId());
                    sb.append("  ").append(topicId).append(" --> ").append(subId).append("\n");
                }
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Generates a sequence diagram for API interactions.
     */
    private String generateSequenceDiagram(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("API Sequence Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(SEQUENCE_DIAGRAM).append(MARKDOWN_NEWLINE);

        if (model.apiEndpoints().isEmpty()) {
            sb.append(NO_API_ENDPOINTS_NODE);
        } else {
            // Add participants
            Set<String> participants = new LinkedHashSet<>();
            participants.add("Client");

            for (ApiEndpoint endpoint : model.apiEndpoints()) {
                String compName = getComponentName(model, endpoint.componentId());
                participants.add(compName);
            }

            for (String participant : participants) {
                sb.append("  participant ").append(sanitizeId(participant)).append(" as ")
                    .append(escape(participant)).append("\n");
            }

            sb.append(MARKDOWN_NEWLINE);

            // Add API calls as sequence
            for (ApiEndpoint endpoint : model.apiEndpoints()) {
                String compName = getComponentName(model, endpoint.componentId());
                String method = endpoint.method() != null ? endpoint.method() : "CALL";
                String path = endpoint.path();

                sb.append("  Client->>").append(sanitizeId(compName)).append(": ")
                    .append(method).append(" ").append(escape(path)).append("\n");

                if (endpoint.description() != null) {
                    sb.append("  Note over ").append(sanitizeId(compName)).append(": ")
                        .append(escape(endpoint.description())).append("\n");
                }

                sb.append("  ").append(sanitizeId(compName)).append("->>Client: Response\n");
            }
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Sanitizes an ID for use in Mermaid diagrams.
     */
    private String sanitizeId(String id) {
        if (id == null) {
            return "unknown";
        }
        // Replace special characters with underscores
        return id.replaceAll(ID_SANITIZATION_PATTERN, "_");
    }

    /**
     * Sanitizes table names for ER diagrams.
     */
    private String sanitizeTableName(String name) {
        if (name == null) {
            return "UNKNOWN";
        }
        return name.replaceAll(TABLE_NAME_SANITIZATION_PATTERN, "_").toUpperCase();
    }

    /**
     * Escapes special characters in strings for Mermaid.
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'").replace("\n", " ");
    }

    /**
     * Gets component name by ID.
     */
    private String getComponentName(ArchitectureModel model, String componentId) {
        return model.components().stream()
            .filter(c -> c.id().equals(componentId))
            .findFirst()
            .map(Component::name)
            .orElse(componentId);
    }
}
