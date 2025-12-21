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
 * <p>This generator produces Mermaid.js diagram definitions embedded in Markdown files.
 * Mermaid is a JavaScript-based diagramming tool that renders diagrams from text definitions,
 * making it ideal for version control and documentation-as-code workflows.
 *
 * <h2>Supported Diagram Types</h2>
 * <ul>
 *   <li><b>C4 Context:</b> System-level view showing major components and external dependencies</li>
 *   <li><b>C4 Container:</b> Detailed view of all components with technologies</li>
 *   <li><b>C4 Component:</b> Most detailed view including component types and relationships</li>
 *   <li><b>Dependency Graph:</b> Flowchart showing component dependencies and library usage</li>
 *   <li><b>ER Diagram:</b> Entity-relationship diagram with inferred foreign key relationships</li>
 *   <li><b>Message Flow:</b> Event-driven architecture with publishers, subscribers, and topics</li>
 *   <li><b>Sequence Diagram:</b> API interaction flows showing request-response patterns</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Clean Code:</b> Each diagram type is decomposed into focused helper methods</li>
 *   <li><b>Single Responsibility:</b> Methods handle one specific rendering task</li>
 *   <li><b>Consistent Structure:</b> All diagrams follow header → content → footer pattern</li>
 *   <li><b>Safe Escaping:</b> Special characters are sanitized for Mermaid syntax</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * MermaidGenerator generator = new MermaidGenerator();
 * ArchitectureModel model = ...; // populated model
 * GeneratorConfig config = GeneratorConfig.defaults();
 *
 * GeneratedDiagram diagram = generator.generate(model, DiagramType.C4_CONTEXT, config);
 * // diagram.content() contains Markdown with embedded Mermaid code block
 * }</pre>
 *
 * <p>Output format is Markdown (*.md files) with embedded {@code ```mermaid} code blocks
 * suitable for rendering in GitHub, GitLab, documentation sites, and Mermaid Live Editor.
 *
 * @see <a href="https://mermaid.js.org/">Mermaid Documentation</a>
 * @see <a href="https://c4model.com/">C4 Model</a>
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
     *
     * <p>The Context diagram provides a high-level view of the system showing
     * major components and their relationships. It focuses on SERVICE and MODULE
     * component types to represent the system boundary.
     *
     * @param model the architecture model containing components and relationships
     * @param config generator configuration controlling diagram scope
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateC4Context(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        appendDiagramHeader(sb, "C4 Context Diagram", C4_CONTEXT, "System Context Diagram for " + model.projectName());

        if (model.components().isEmpty()) {
            appendPlaceholder(sb, "System", "No components found");
        } else {
            List<Component> systems = filterSystemComponents(model, config);
            appendC4Components(sb, systems, "System");
            appendC4Relationships(sb, model.relationships());
        }

        appendDiagramFooter(sb);
        return sb.toString();
    }

    /**
     * Filters components to include only system-level components.
     *
     * @param model the architecture model
     * @param config generator configuration
     * @return list of SERVICE or MODULE components
     */
    private List<Component> filterSystemComponents(ArchitectureModel model, GeneratorConfig config) {
        return model.components().stream()
            .filter(c -> c.type() == ComponentType.SERVICE || c.type() == ComponentType.MODULE)
            .limit(config.includeExternal() ? Integer.MAX_VALUE : 20)
            .toList();
    }

    /**
     * Appends C4 component definitions to the diagram.
     *
     * @param sb the string builder
     * @param components list of components to render
     * @param elementType C4 element type (System, Container, Component)
     */
    private void appendC4Components(StringBuilder sb, List<Component> components, String elementType) {
        for (Component comp : components) {
            String desc = getComponentDescription(comp);
            sb.append("  ").append(elementType).append("(").append(sanitizeId(comp.id())).append(", \"")
                .append(escape(comp.name())).append("\", \"")
                .append(escape(desc)).append("\")\n");
        }
        sb.append(MARKDOWN_NEWLINE);
    }

    /**
     * Gets the description for a component, falling back to technology if needed.
     *
     * @param comp the component
     * @return description or technology, or empty string
     */
    private String getComponentDescription(Component comp) {
        if (comp.description() != null) {
            return comp.description();
        }
        return comp.technology() != null ? comp.technology() : "";
    }

    /**
     * Appends C4 relationships to the diagram.
     *
     * @param sb the string builder
     * @param relationships list of relationships to render
     */
    private void appendC4Relationships(StringBuilder sb, List<Relationship> relationships) {
        for (Relationship rel : relationships) {
            String tech = rel.technology() != null ? rel.technology() : "";
            sb.append("  Rel(").append(sanitizeId(rel.sourceId())).append(", ")
                .append(sanitizeId(rel.targetId())).append(", \"")
                .append(escape(rel.type().toString())).append("\", \"")
                .append(escape(tech)).append("\")\n");
        }
    }

    /**
     * Appends diagram header with title and Mermaid code block.
     *
     * @param sb the string builder
     * @param title the diagram title
     * @param diagramType the Mermaid diagram type keyword
     * @param subtitle optional subtitle text
     */
    private void appendDiagramHeader(StringBuilder sb, String title, String diagramType, String subtitle) {
        sb.append(MARKDOWN_HEADER_PREFIX).append(title).append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(diagramType).append(MARKDOWN_NEWLINE);
        if (subtitle != null && !subtitle.isEmpty()) {
            sb.append("  title ").append(subtitle).append(MARKDOWN_NEWLINE.repeat(2));
        }
    }

    /**
     * Appends a placeholder element for empty diagrams.
     *
     * @param sb the string builder
     * @param elementType the C4 element type
     * @param message the placeholder message
     */
    private void appendPlaceholder(StringBuilder sb, String elementType, String message) {
        sb.append("  ").append(elementType).append("(placeholder, \"").append(message).append("\", \"\")\n");
    }

    /**
     * Appends diagram footer (closing code block).
     *
     * @param sb the string builder
     */
    private void appendDiagramFooter(StringBuilder sb) {
        sb.append(CODE_BLOCK_END);
    }

    /**
     * Generates a C4 Container diagram showing internal components.
     *
     * <p>The Container diagram shows all components within the system boundary,
     * including their technologies and relationships. This provides more detail
     * than the Context diagram.
     *
     * @param model the architecture model containing components and relationships
     * @param config generator configuration controlling diagram scope
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateC4Container(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        appendDiagramHeader(sb, "C4 Container Diagram", C4_CONTAINER, "Container Diagram for " + model.projectName());

        if (model.components().isEmpty()) {
            appendC4PlaceholderWithTech(sb, "Container", "No components found");
        } else {
            appendC4ContainerComponents(sb, model.components());
            appendC4Relationships(sb, model.relationships());
        }

        appendDiagramFooter(sb);
        return sb.toString();
    }

    /**
     * Appends C4 Container component definitions with technology information.
     *
     * @param sb the string builder
     * @param components list of components to render
     */
    private void appendC4ContainerComponents(StringBuilder sb, List<Component> components) {
        for (Component comp : components) {
            String tech = comp.technology() != null ? comp.technology() : "";
            String desc = comp.description() != null ? comp.description() : "";
            sb.append("  Container(").append(sanitizeId(comp.id())).append(", \"")
                .append(escape(comp.name())).append("\", \"")
                .append(escape(tech)).append("\", \"")
                .append(escape(desc)).append("\")\n");
        }
        sb.append(MARKDOWN_NEWLINE);
    }

    /**
     * Appends a placeholder element with technology field for empty diagrams.
     *
     * @param sb the string builder
     * @param elementType the C4 element type
     * @param message the placeholder message
     */
    private void appendC4PlaceholderWithTech(StringBuilder sb, String elementType, String message) {
        sb.append("  ").append(elementType).append("(placeholder, \"").append(message).append("\", \"\", \"\")\n");
    }

    /**
     * Generates a C4 Component diagram showing component-level details.
     *
     * <p>The Component diagram provides the most detailed view, showing individual
     * components with their types, technologies, and detailed relationships.
     *
     * @param model the architecture model containing components and relationships
     * @param config generator configuration (currently unused for component diagrams)
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateC4Component(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        appendDiagramHeader(sb, "C4 Component Diagram", C4_COMPONENT, "Component Diagram for " + model.projectName());

        if (model.components().isEmpty()) {
            appendC4PlaceholderWithTech(sb, "Component", "No components found");
        } else {
            appendC4ComponentDetails(sb, model.components());
            appendC4ComponentRelationships(sb, model.relationships());
        }

        appendDiagramFooter(sb);
        return sb.toString();
    }

    /**
     * Appends C4 Component definitions with technology and type information.
     *
     * @param sb the string builder
     * @param components list of components to render
     */
    private void appendC4ComponentDetails(StringBuilder sb, List<Component> components) {
        for (Component comp : components) {
            String tech = comp.technology() != null ? comp.technology() : "";
            String desc = comp.description() != null ? comp.description() : comp.type().toString();
            sb.append("  Component(").append(sanitizeId(comp.id())).append(", \"")
                .append(escape(comp.name())).append("\", \"")
                .append(escape(tech)).append("\", \"")
                .append(escape(desc)).append("\")\n");
        }
        sb.append(MARKDOWN_NEWLINE);
    }

    /**
     * Appends C4 Component relationships with descriptions.
     *
     * @param sb the string builder
     * @param relationships list of relationships to render
     */
    private void appendC4ComponentRelationships(StringBuilder sb, List<Relationship> relationships) {
        for (Relationship rel : relationships) {
            String desc = rel.description() != null ? rel.description() : rel.type().toString();
            sb.append("  Rel(").append(sanitizeId(rel.sourceId())).append(", ")
                .append(sanitizeId(rel.targetId())).append(", \"")
                .append(escape(desc)).append("\")\n");
        }
    }

    /**
     * Generates a dependency graph as a flowchart.
     *
     * <p>The dependency graph shows both component-to-component relationships
     * and external library dependencies. Component nodes are solid, while
     * relationships are shown with different line styles (solid for dependencies,
     * dotted for component relationships).
     *
     * @param model the architecture model containing components and dependencies
     * @param config generator configuration (currently unused for dependency graphs)
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateDependencyGraph(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("Dependency Graph").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(GRAPH_LR);

        if (model.components().isEmpty() && model.dependencies().isEmpty()) {
            sb.append(NO_DEPENDENCIES_NODE);
        } else {
            Set<String> addedNodes = new HashSet<>();
            appendComponentNodes(sb, model.components(), addedNodes);
            appendDependencyEdges(sb, model.dependencies(), addedNodes);
            appendComponentRelationshipEdges(sb, model.relationships());
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Appends component nodes to the dependency graph.
     *
     * @param sb the string builder
     * @param components list of components to render as nodes
     * @param addedNodes set to track which nodes have been added
     */
    private void appendComponentNodes(StringBuilder sb, List<Component> components, Set<String> addedNodes) {
        for (Component comp : components) {
            String nodeId = sanitizeId(comp.id());
            addedNodes.add(nodeId);
            sb.append("  ").append(nodeId).append("[\"").append(escape(comp.name())).append("\"]\n");
        }
    }

    /**
     * Appends dependency edges to the graph, creating nodes for external dependencies.
     *
     * @param sb the string builder
     * @param dependencies list of dependencies to render as edges
     * @param addedNodes set to track which nodes have been added
     */
    private void appendDependencyEdges(StringBuilder sb, List<Dependency> dependencies, Set<String> addedNodes) {
        Map<String, List<Dependency>> depsByComponent = dependencies.stream()
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
    }

    /**
     * Appends component-to-component relationships as dotted lines.
     *
     * @param sb the string builder
     * @param relationships list of relationships to render
     */
    private void appendComponentRelationshipEdges(StringBuilder sb, List<Relationship> relationships) {
        for (Relationship rel : relationships) {
            String sourceId = sanitizeId(rel.sourceId());
            String targetId = sanitizeId(rel.targetId());
            sb.append("  ").append(sourceId).append(" -.-> ").append(targetId).append("\n");
        }
    }

    /**
     * Generates an Entity-Relationship diagram with proper cardinality.
     *
     * <p>The ER diagram shows data entities (tables) with their fields and
     * relationships. Foreign key relationships are inferred from field naming
     * conventions (_id or Id suffixes).
     *
     * @param model the architecture model containing data entities
     * @param config generator configuration (currently unused for ER diagrams)
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateErDiagram(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("Entity-Relationship Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(ER_DIAGRAM).append(MARKDOWN_NEWLINE);

        if (model.dataEntities().isEmpty()) {
            appendErPlaceholder(sb);
        } else {
            appendEntityDefinitions(sb, model.dataEntities());
            appendInferredRelationships(sb, model.dataEntities());
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Appends a placeholder entity for empty ER diagrams.
     *
     * @param sb the string builder
     */
    private void appendErPlaceholder(StringBuilder sb) {
        sb.append("  PLACEHOLDER {\n");
        sb.append("    string note \"No data entities found\"\n");
        sb.append("  }\n");
    }

    /**
     * Appends entity definitions with field details.
     *
     * @param sb the string builder
     * @param entities list of data entities to render
     */
    private void appendEntityDefinitions(StringBuilder sb, List<DataEntity> entities) {
        for (DataEntity entity : entities) {
            sb.append("  ").append(sanitizeTableName(entity.name())).append(" {\n");

            if (entity.fields().isEmpty()) {
                sb.append("    string placeholder \"No fields defined\"\n");
            } else {
                appendEntityFields(sb, entity.fields(), entity.primaryKey());
            }

            sb.append("  }\n");
        }
    }

    /**
     * Appends field definitions for an entity.
     *
     * @param sb the string builder
     * @param fields list of fields to render
     * @param primaryKey the primary key field name (may be null)
     */
    private void appendEntityFields(StringBuilder sb, List<DataEntity.Field> fields, String primaryKey) {
        for (DataEntity.Field field : fields) {
            String dataType = field.dataType() != null ? field.dataType() : "string";
            String pkMarker = isPrimaryKeyField(field.name(), primaryKey) ? " PK" : "";
            String comment = field.description() != null ? " \"" + escape(field.description()) + "\"" : "";
            sb.append("    ").append(dataType).append(" ")
                .append(field.name()).append(pkMarker).append(comment).append("\n");
        }
    }

    /**
     * Determines if a field is the primary key.
     *
     * @param fieldName the field name
     * @param primaryKey the primary key from the entity (may be null)
     * @return true if this field is the primary key
     */
    private boolean isPrimaryKeyField(String fieldName, String primaryKey) {
        return primaryKey != null && fieldName.equals(primaryKey);
    }

    /**
     * Appends relationships inferred from foreign key naming conventions.
     *
     * <p>Looks for fields ending with "_id" or "Id" and attempts to match them
     * to existing entities. Uses one-to-many cardinality (||--o{).
     *
     * @param sb the string builder
     * @param entities list of data entities
     */
    private void appendInferredRelationships(StringBuilder sb, List<DataEntity> entities) {
        for (DataEntity entity : entities) {
            for (DataEntity.Field field : entity.fields()) {
                if (isForeignKeyField(field)) {
                    String targetEntity = findRelatedEntity(field, entities);
                    if (targetEntity != null) {
                        sb.append("  ").append(sanitizeTableName(targetEntity))
                            .append(" ||--o{ ")
                            .append(sanitizeTableName(entity.name()))
                            .append(" : \"has\"\n");
                    }
                }
            }
        }
    }

    /**
     * Checks if a field is a foreign key based on naming convention.
     *
     * @param field the field to check
     * @return true if field name ends with "_id" or "Id"
     */
    private boolean isForeignKeyField(DataEntity.Field field) {
        return field.name().endsWith("_id") || field.name().endsWith("Id");
    }

    /**
     * Finds the related entity for a foreign key field.
     *
     * @param field the foreign key field
     * @param entities list of available entities
     * @return the name of the related entity, or null if not found
     */
    private String findRelatedEntity(DataEntity.Field field, List<DataEntity> entities) {
        String potentialTarget = field.name().replaceAll("(_id|Id)$", "");

        return entities.stream()
            .filter(e -> e.name().equalsIgnoreCase(potentialTarget) ||
                        e.name().equalsIgnoreCase(potentialTarget + "s"))
            .findFirst()
            .map(DataEntity::name)
            .orElse(null);
    }

    /**
     * Generates a message flow diagram for event-driven systems.
     *
     * <p>The message flow diagram shows publishers, subscribers, and message topics
     * in a top-to-bottom layout. Topics are rendered as hexagons, components as
     * rectangles, and message types are shown as edge labels.
     *
     * @param model the architecture model containing message flows
     * @param config generator configuration (currently unused for message flows)
     * @return Markdown-formatted Mermaid diagram
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
            appendMessageFlowNodes(sb, model, addedNodes);
            sb.append(MARKDOWN_NEWLINE);
            appendMessageFlowEdges(sb, model.messageFlows());
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Appends nodes for publishers, subscribers, and topics.
     *
     * @param sb the string builder
     * @param model the architecture model
     * @param addedNodes set to track which nodes have been added
     */
    private void appendMessageFlowNodes(StringBuilder sb, ArchitectureModel model, Set<String> addedNodes) {
        for (MessageFlow flow : model.messageFlows()) {
            appendPublisherNode(sb, model, flow, addedNodes);
            appendSubscriberNode(sb, model, flow, addedNodes);
            appendTopicNode(sb, flow, addedNodes);
        }
    }

    /**
     * Appends a publisher component node if not already added.
     *
     * @param sb the string builder
     * @param model the architecture model
     * @param flow the message flow
     * @param addedNodes set to track added nodes
     */
    private void appendPublisherNode(StringBuilder sb, ArchitectureModel model, MessageFlow flow, Set<String> addedNodes) {
        if (flow.publisherComponentId() != null) {
            String pubId = sanitizeId(flow.publisherComponentId());
            if (!addedNodes.contains(pubId)) {
                String name = getComponentName(model, flow.publisherComponentId());
                sb.append("  ").append(pubId).append("[\"").append(escape(name)).append("\"]\n");
                addedNodes.add(pubId);
            }
        }
    }

    /**
     * Appends a subscriber component node if not already added.
     *
     * @param sb the string builder
     * @param model the architecture model
     * @param flow the message flow
     * @param addedNodes set to track added nodes
     */
    private void appendSubscriberNode(StringBuilder sb, ArchitectureModel model, MessageFlow flow, Set<String> addedNodes) {
        if (flow.subscriberComponentId() != null) {
            String subId = sanitizeId(flow.subscriberComponentId());
            if (!addedNodes.contains(subId)) {
                String name = getComponentName(model, flow.subscriberComponentId());
                sb.append("  ").append(subId).append("[\"").append(escape(name)).append("\"]\n");
                addedNodes.add(subId);
            }
        }
    }

    /**
     * Appends a topic node (hexagon shape) if not already added.
     *
     * @param sb the string builder
     * @param flow the message flow
     * @param addedNodes set to track added nodes
     */
    private void appendTopicNode(StringBuilder sb, MessageFlow flow, Set<String> addedNodes) {
        String topicId = sanitizeId(flow.topic());
        if (!addedNodes.contains(topicId)) {
            sb.append("  ").append(topicId).append("{{\"").append(escape(flow.topic())).append("\"}}\n");
            addedNodes.add(topicId);
        }
    }

    /**
     * Appends message flow edges connecting publishers, topics, and subscribers.
     *
     * @param sb the string builder
     * @param flows list of message flows to render
     */
    private void appendMessageFlowEdges(StringBuilder sb, List<MessageFlow> flows) {
        for (MessageFlow flow : flows) {
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

    /**
     * Generates a sequence diagram for API interactions.
     *
     * <p>The sequence diagram shows API call flows between a client and backend
     * components. Each endpoint is rendered as a request-response pair, with
     * optional notes for endpoint descriptions.
     *
     * @param model the architecture model containing API endpoints
     * @param config generator configuration (currently unused for sequence diagrams)
     * @return Markdown-formatted Mermaid diagram
     */
    private String generateSequenceDiagram(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARKDOWN_HEADER_PREFIX).append("API Sequence Diagram").append(MARKDOWN_NEWLINE.repeat(2));
        sb.append(CODE_BLOCK_START);
        sb.append(SEQUENCE_DIAGRAM).append(MARKDOWN_NEWLINE);

        if (model.apiEndpoints().isEmpty()) {
            sb.append(NO_API_ENDPOINTS_NODE);
        } else {
            Set<String> participants = collectParticipants(model);
            appendSequenceParticipants(sb, participants);
            sb.append(MARKDOWN_NEWLINE);
            appendApiCallSequences(sb, model);
        }

        sb.append(CODE_BLOCK_END);
        return sb.toString();
    }

    /**
     * Collects all participants (Client + components with endpoints) for the sequence diagram.
     *
     * @param model the architecture model
     * @return ordered set of participant names
     */
    private Set<String> collectParticipants(ArchitectureModel model) {
        Set<String> participants = new LinkedHashSet<>();
        participants.add("Client");

        for (ApiEndpoint endpoint : model.apiEndpoints()) {
            String compName = getComponentName(model, endpoint.componentId());
            participants.add(compName);
        }

        return participants;
    }

    /**
     * Appends participant declarations to the sequence diagram.
     *
     * @param sb the string builder
     * @param participants set of participant names
     */
    private void appendSequenceParticipants(StringBuilder sb, Set<String> participants) {
        for (String participant : participants) {
            sb.append("  participant ").append(sanitizeId(participant)).append(" as ")
                .append(escape(participant)).append("\n");
        }
    }

    /**
     * Appends API call sequences (request-response pairs) to the diagram.
     *
     * @param sb the string builder
     * @param model the architecture model
     */
    private void appendApiCallSequences(StringBuilder sb, ArchitectureModel model) {
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

    /**
     * Sanitizes an identifier for safe use in Mermaid diagrams.
     *
     * <p>Replaces all non-alphanumeric characters (except underscores) with underscores
     * to ensure valid Mermaid node IDs. This prevents syntax errors from special
     * characters like hyphens, dots, or spaces.
     *
     * @param id the identifier to sanitize (may be null)
     * @return sanitized identifier, or "unknown" if input is null
     */
    private String sanitizeId(String id) {
        if (id == null) {
            return "unknown";
        }
        return id.replaceAll(ID_SANITIZATION_PATTERN, "_");
    }

    /**
     * Sanitizes and formats table names for ER diagram entities.
     *
     * <p>Converts table names to uppercase and replaces special characters with
     * underscores, following SQL naming conventions commonly used in ER diagrams.
     *
     * @param name the table name to sanitize (may be null)
     * @return uppercase sanitized table name, or "UNKNOWN" if input is null
     */
    private String sanitizeTableName(String name) {
        if (name == null) {
            return "UNKNOWN";
        }
        return name.replaceAll(TABLE_NAME_SANITIZATION_PATTERN, "_").toUpperCase();
    }

    /**
     * Escapes special characters in text for safe embedding in Mermaid diagrams.
     *
     * <p>Performs two transformations:
     * <ul>
     *   <li>Replaces double quotes with single quotes (Mermaid uses quotes for labels)</li>
     *   <li>Replaces newlines with spaces (Mermaid doesn't support multiline labels)</li>
     * </ul>
     *
     * @param text the text to escape (may be null)
     * @return escaped text, or empty string if input is null
     */
    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\"", "'").replace("\n", " ");
    }

    /**
     * Retrieves a component's display name by its ID.
     *
     * <p>Searches the architecture model for a component with the given ID
     * and returns its name. If not found, returns the ID itself as fallback.
     *
     * @param model the architecture model to search
     * @param componentId the component ID to look up
     * @return component name, or the componentId if not found
     */
    private String getComponentName(ArchitectureModel model, String componentId) {
        return model.components().stream()
            .filter(c -> c.id().equals(componentId))
            .findFirst()
            .map(Component::name)
            .orElse(componentId);
    }
}
