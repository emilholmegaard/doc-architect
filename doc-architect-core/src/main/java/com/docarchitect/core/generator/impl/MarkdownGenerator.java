package com.docarchitect.core.generator.impl;

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

/**
 * Generates Markdown documentation from architecture models.
 *
 * <p>Creates comprehensive documentation including:
 * <ul>
 *   <li>Central index.md entry point with navigation</li>
 *   <li>Component-specific documentation pages</li>
 *   <li>API endpoint reference tables</li>
 *   <li>Dependency matrices</li>
 *   <li>Data entity catalogs</li>
 * </ul>
 *
 * <p>Supports organizing output into subdirectories:
 * overview/, components/, dependencies/, api/, data/, messaging/
 */
public class MarkdownGenerator implements DiagramGenerator {

    private static final Logger log = LoggerFactory.getLogger(MarkdownGenerator.class);

    // Markdown formatting constants
    private static final String H1 = "# ";
    private static final String H2 = "## ";
    private static final String H3 = "### ";
    private static final String H4 = "#### ";
    private static final String BOLD = "**";
    private static final String CODE = "`";
    private static final String PIPE = "|";
    private static final String DASH = "-";
    private static final String NEWLINE = "\n";
    private static final String DOUBLE_NEWLINE = "\n\n";

    // Section headers
    private static final String ARCHITECTURE_DOCUMENTATION = " - Architecture Documentation";
    private static final String VERSION_LABEL = "**Version:** ";
    private static final String OVERVIEW = "## Overview";
    private static final String ARCHITECTURE_STATISTICS = "### Architecture Statistics";
    private static final String DOCUMENTATION_SECTIONS = "## Documentation Sections";
    private static final String COMPONENTS_SECTION = "### Components";
    private static final String APIS_SECTION = "### APIs";
    private static final String DATA_SECTION = "### Data";
    private static final String DEPENDENCIES_SECTION = "### Dependencies";
    private static final String MESSAGING_SECTION = "### Messaging";
    private static final String COMPONENT_DETAILS = "## Component Details";
    private static final String ENDPOINT_DETAILS = "### Endpoint Details";
    private static final String DEPENDENCY_SUMMARY = "## Dependency Summary";
    private static final String FIELDS_HEADER = "### Fields";
    private static final String MESSAGE_FLOW_CATALOG = "# Message Flow Catalog";

    // Table headers and metrics
    private static final String METRIC = "Metric";
    private static final String COUNT = "Count";
    private static final String COMPONENT_COUNT = "Components";
    private static final String DEPENDENCIES = "Dependencies";
    private static final String API_ENDPOINTS = "API Endpoints";
    private static final String DATA_ENTITIES = "Data Entities";
    private static final String MESSAGE_FLOWS = "Message Flows";
    private static final String METHOD = "Method";
    private static final String PATH = "Path";
    private static final String TYPE = "Type";
    private static final String AUTHENTICATION = "Authentication";
    private static final String DESCRIPTION = "Description";
    private static final String GROUP = "Artifact Group";
    private static final String ARTIFACT = "Artifact";
    private static final String VERSION = "Version";
    private static final String SCOPE = "Scope";
    private static final String NAME = "Name";
    private static final String TECHNOLOGY = "Technology";
    private static final String REPOSITORY = "Repository";
    private static final String FIELD = "Field";
    private static final String DATA_TYPE = "Data Type";
    private static final String NULLABLE = "Nullable";
    private static final String PUBLISHER = "Publisher";
    private static final String SUBSCRIBER = "Subscriber";
    private static final String MESSAGE_TYPE = "Message Type";
    private static final String SCHEMA = "Schema";
    private static final String BROKER = "Broker";

    // Default values
    private static final String NO_FOUND = "No %s found";
    private static final String NONE = "None";
    private static final String DASH_VALUE = "-";
    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String DIRECT_DEP = "Direct";
    private static final String TRANSITIVE_DEP = "Transitive";
    private static final String UNKNOWN = "Unknown";

    // Navigation links
    private static final String COMPONENT_CATALOG_LINK = "- [Component Catalog](components/catalog.md) - Complete list of all components";
    private static final String COMPONENT_RELATIONSHIPS_LINK = "- [Component Relationships](components/relationships.md) - Inter-component dependencies";
    private static final String API_CATALOG_LINK = "- [API Catalog](api/catalog.md) - All API endpoints";
    private static final String API_REFERENCE_LINK = "- [API Reference](api/reference.md) - Detailed endpoint documentation";
    private static final String DATA_CATALOG_LINK = "- [Data Entity Catalog](data/catalog.md) - Database schemas and entities";
    private static final String DATA_DICTIONARY_LINK = "- [Data Dictionary](data/dictionary.md) - Field-level documentation";
    private static final String DEPENDENCY_MATRIX_LINK = "- [Dependency Matrix](dependencies/matrix.md) - External library dependencies";
    private static final String DEPENDENCY_ANALYSIS_LINK = "- [Dependency Analysis](dependencies/analysis.md) - Dependency insights";
    private static final String MESSAGE_FLOW_LINK = "- [Message Flow Catalog](messaging/flows.md) - Event-driven communication";
    private static final String TOPIC_REFERENCE_LINK = "- [Topic Reference](messaging/topics.md) - Message topics and schemas";

    // Property labels
    private static final String TYPE_LABEL = "- **Type:** ";
    private static final String TECH_LABEL = "- **Technology:** ";
    private static final String REPO_LABEL = "- **Repository:** ";
    private static final String DESC_LABEL = "- **Description:** ";
    private static final String AUTH_LABEL = "- **Authentication:** ";
    private static final String REQUEST_SCHEMA_LABEL = "- **Request Schema:** ";
    private static final String RESPONSE_SCHEMA_LABEL = "- **Response Schema:** ";
    private static final String PRIMARY_KEY_LABEL = "- **Primary Key:** ";
    private static final String COMPONENT_LABEL = "- **Component:** ";
    private static final String METADATA_LABEL = "- **Metadata:**";
    private static final String EXPOSED_APIS_LABEL = "**Exposed APIs:** ";
    private static final String DATA_ENTITIES_LABEL = "**Data Entities:** ";
    private static final String BROKER_LABEL = "**Broker:** ";

    // Counting labels
    private static final String ENDPOINT_SUFFIX = " endpoint(s)";
    private static final String ENTITY_SUFFIX = " entity/entities";
    private static final String TABLE_DEFAULT = "Table";
    private static final String TOTAL_DEPENDENCIES = "Total Dependencies";
    private static final String DIRECT_DEPENDENCIES = "Direct Dependencies";
    private static final String TRANSITIVE_DEPENDENCIES = "Transitive Dependencies";

    // Additional formatting patterns
    private static final String EMPTY_SYSTEM_MESSAGE = " in the system.";
    private static final String SPACE = " ";
    private static final String COLON = ": ";
    private static final String IN_SYSTEM = " in the system.";
    private static final String METADATA_INDENT = "  ";

    // Common empty checks messages
    private static final String NO_COMPONENTS = "components";
    private static final String NO_API_ENDPOINTS = "API endpoints";
    private static final String NO_DATA_ENTITIES = "data entities";
    private static final String NO_EXTERNAL_DEPENDENCIES = "external dependencies";
    private static final String NO_MESSAGE_FLOWS = "message flows";

    // Catalog titles
    private static final String API_CATALOG_TITLE = "API Catalog";
    private static final String DEPENDENCY_MATRIX_TITLE = "Dependency Matrix";
    private static final String COMPONENT_CATALOG_TITLE = "Component Catalog";
    private static final String DATA_ENTITY_CATALOG_TITLE = "Data Entity Catalog";

    @Override
    public String getId() {
        return "markdown";
    }

    @Override
    public String getDisplayName() {
        return "Markdown Documentation Generator";
    }

    @Override
    public String getFileExtension() {
        return "md";
    }

    @Override
    public Set<DiagramType> getSupportedDiagramTypes() {
        // Markdown generator creates documentation for these aspects
        return Set.of(
            DiagramType.API_CATALOG,
            DiagramType.DEPENDENCY_GRAPH,
            DiagramType.C4_COMPONENT
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

        log.debug("Generating Markdown documentation for type: {}", type);

        String content = switch (type) {
            case API_CATALOG -> generateApiCatalog(model, config);
            case DEPENDENCY_GRAPH -> generateDependencyMatrix(model, config);
            case C4_COMPONENT -> generateComponentCatalog(model, config);
            default -> throw new IllegalArgumentException("Unsupported diagram type: " + type);
        };

        String docName = type.name().toLowerCase().replace('_', '-');
        log.info("Generated Markdown documentation: {}", docName);

        return new GeneratedDiagram(docName, content, getFileExtension());
    }

    /**
     * Generates the main index page with navigation.
     */
    public String generateIndex(ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, 1, model.projectName() + ARCHITECTURE_DOCUMENTATION);
        appendVersionIfPresent(sb, model);
        appendSection(sb, OVERVIEW, "This documentation provides a comprehensive view of the " 
            + model.projectName() + " architecture.");

        // Statistics
        appendStatisticsTable(sb, model);

        // Navigation
        sb.append(DOCUMENTATION_SECTIONS).append(DOUBLE_NEWLINE);
        appendNavigationSection(sb, COMPONENTS_SECTION, model.components().isEmpty(),
            NO_COMPONENTS, COMPONENT_CATALOG_LINK, COMPONENT_RELATIONSHIPS_LINK);
        appendNavigationSection(sb, APIS_SECTION, model.apiEndpoints().isEmpty(),
            NO_API_ENDPOINTS, API_CATALOG_LINK, API_REFERENCE_LINK);
        appendNavigationSection(sb, DATA_SECTION, model.dataEntities().isEmpty(),
            NO_DATA_ENTITIES, DATA_CATALOG_LINK, DATA_DICTIONARY_LINK);
        appendNavigationSection(sb, DEPENDENCIES_SECTION, model.dependencies().isEmpty(),
            NO_EXTERNAL_DEPENDENCIES, DEPENDENCY_MATRIX_LINK, DEPENDENCY_ANALYSIS_LINK);
        appendNavigationSection(sb, MESSAGING_SECTION, model.messageFlows().isEmpty(),
            NO_MESSAGE_FLOWS, MESSAGE_FLOW_LINK, TOPIC_REFERENCE_LINK);

        return sb.toString();
    }

    /**
     * Generates API catalog with all endpoints.
     */
    private String generateApiCatalog(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, 1, API_CATALOG_TITLE);

        if (appendEmptyMessage(sb, model.apiEndpoints().isEmpty(), NO_API_ENDPOINTS)) {
            return sb.toString();
        }

        // Group endpoints by component
        Map<String, List<ApiEndpoint>> endpointsByComponent = model.apiEndpoints().stream()
            .collect(Collectors.groupingBy(ApiEndpoint::componentId));

        for (Map.Entry<String, List<ApiEndpoint>> entry : endpointsByComponent.entrySet()) {
            String componentName = getComponentName(model, entry.getKey());
            appendHeader(sb, 2, componentName);

            appendTableRow(sb, METHOD, PATH, TYPE, AUTHENTICATION, DESCRIPTION);
            appendTableDivider(sb, 5);

            for (ApiEndpoint endpoint : entry.getValue()) {
                appendTableRow(sb,
                    nullSafeValue(endpoint.method(), DASH_VALUE),
                    escapeMarkdown(endpoint.path()),
                    endpoint.type().toString(),
                    nullSafeValue(endpoint.authentication(), NONE),
                    escapeMarkdown(nullSafeValue(endpoint.description(), DASH_VALUE))
                );
            }

            sb.append(DOUBLE_NEWLINE);
            appendEndpointDetails(sb, entry.getValue());
        }

        return sb.toString();
    }

    /**
     * Generates dependency matrix showing all external dependencies.
     */
    private String generateDependencyMatrix(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, 1, DEPENDENCY_MATRIX_TITLE);

        if (appendEmptyMessage(sb, model.dependencies().isEmpty(), NO_EXTERNAL_DEPENDENCIES)) {
            return sb.toString();
        }

        // Group by component
        Map<String, List<Dependency>> depsByComponent = model.dependencies().stream()
            .collect(Collectors.groupingBy(Dependency::sourceComponentId));

        for (Map.Entry<String, List<Dependency>> entry : depsByComponent.entrySet()) {
            String componentName = getComponentName(model, entry.getKey());
            appendHeader(sb, 2, componentName);

            appendTableRow(sb, GROUP, ARTIFACT, VERSION, SCOPE, TYPE);
            appendTableDivider(sb, 5);

            for (Dependency dep : entry.getValue()) {
                appendTableRow(sb,
                    dep.groupId(),
                    dep.artifactId(),
                    nullSafeValue(dep.version(), DASH_VALUE),
                    dep.scope(),
                    dep.direct() ? DIRECT_DEP : TRANSITIVE_DEP
                );
            }

            sb.append(DOUBLE_NEWLINE);
        }

        appendDependencySummary(sb, model);
        return sb.toString();
    }

    /**
     * Generates component catalog with detailed information.
     */
    private String generateComponentCatalog(ArchitectureModel model, GeneratorConfig config) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, 1, COMPONENT_CATALOG_TITLE);

        if (appendEmptyMessage(sb, model.components().isEmpty(), NO_COMPONENTS)) {
            return sb.toString();
        }

        // Group by type
        Map<ComponentType, List<Component>> componentsByType = model.components().stream()
            .collect(Collectors.groupingBy(Component::type));

        for (Map.Entry<ComponentType, List<Component>> entry : componentsByType.entrySet()) {
            appendHeader(sb, 2, entry.getKey().toString() + "s");

            appendTableRow(sb, NAME, TECHNOLOGY, REPOSITORY, DESCRIPTION);
            appendTableDivider(sb, 4);

            for (Component comp : entry.getValue()) {
                appendTableRow(sb,
                    escapeMarkdown(comp.name()),
                    nullSafeValue(comp.technology(), DASH_VALUE),
                    nullSafeValue(comp.repository(), DASH_VALUE),
                    escapeMarkdown(nullSafeValue(comp.description(), DASH_VALUE))
                );
            }

            sb.append(DOUBLE_NEWLINE);
        }

        sb.append(COMPONENT_DETAILS).append(DOUBLE_NEWLINE);

        for (Component comp : model.components()) {
            appendComponentDetails(sb, model, comp);
        }

        return sb.toString();
    }

    /**
     * Generates data entity catalog with field details.
     */
    public String generateDataCatalog(ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb, 1, DATA_ENTITY_CATALOG_TITLE);

        if (appendEmptyMessage(sb, model.dataEntities().isEmpty(), NO_DATA_ENTITIES)) {
            return sb.toString();
        }

        for (DataEntity entity : model.dataEntities()) {
            appendHeader(sb, 2, entity.name());

            if (entity.description() != null) {
                sb.append(entity.description()).append(DOUBLE_NEWLINE);
            }

            sb.append(TYPE_LABEL).append(nullSafeValue(entity.type(), TABLE_DEFAULT)).append(NEWLINE);
            if (entity.primaryKey() != null) {
                sb.append(PRIMARY_KEY_LABEL).append(entity.primaryKey()).append(NEWLINE);
            }

            String componentName = getComponentName(model, entity.componentId());
            sb.append(COMPONENT_LABEL).append(componentName).append(DOUBLE_NEWLINE);

            appendDataEntityFields(sb, entity);
        }

        return sb.toString();
    }

    /**
     * Generates message flow catalog.
     */
    public String generateMessageFlowCatalog(ArchitectureModel model) {
        StringBuilder sb = new StringBuilder();

        sb.append(MESSAGE_FLOW_CATALOG).append(DOUBLE_NEWLINE);

        if (appendEmptyMessage(sb, model.messageFlows().isEmpty(), NO_MESSAGE_FLOWS)) {
            return sb.toString();
        }

        // Group by topic
        Map<String, List<MessageFlow>> flowsByTopic = model.messageFlows().stream()
            .collect(Collectors.groupingBy(MessageFlow::topic));

        for (Map.Entry<String, List<MessageFlow>> entry : flowsByTopic.entrySet()) {
            appendHeader(sb, 2, entry.getKey());

            // Get broker info
            String broker = entry.getValue().stream()
                .map(MessageFlow::broker)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(UNKNOWN);

            sb.append(BROKER_LABEL).append(broker).append(DOUBLE_NEWLINE);

            appendTableRow(sb, PUBLISHER, SUBSCRIBER, MESSAGE_TYPE, SCHEMA);
            appendTableDivider(sb, 4);

            for (MessageFlow flow : entry.getValue()) {
                appendTableRow(sb,
                    getComponentNameOrDash(model, flow.publisherComponentId()),
                    getComponentNameOrDash(model, flow.subscriberComponentId()),
                    escapeMarkdown(nullSafeValue(flow.messageType(), DASH_VALUE)),
                    escapeMarkdown(nullSafeValue(flow.schema(), DASH_VALUE))
                );
            }

            sb.append(DOUBLE_NEWLINE);
        }

        return sb.toString();
    }

    // Helper methods for reducing duplication

    private void appendHeader(StringBuilder sb, int level, String title) {
        String prefix = switch (level) {
            case 1 -> H1;
            case 2 -> H2;
            case 3 -> H3;
            case 4 -> H4;
            default -> "";
        };
        sb.append(prefix).append(title).append(DOUBLE_NEWLINE);
    }

    private void appendVersionIfPresent(StringBuilder sb, ArchitectureModel model) {
        if (model.projectVersion() != null && !model.projectVersion().equals("unknown")) {
            sb.append(VERSION_LABEL).append(model.projectVersion()).append(DOUBLE_NEWLINE);
        }
    }

    private void appendSection(StringBuilder sb, String header, String content) {
        sb.append(header).append(DOUBLE_NEWLINE).append(content).append(DOUBLE_NEWLINE);
    }

    private void appendStatisticsTable(StringBuilder sb, ArchitectureModel model) {
        sb.append(ARCHITECTURE_STATISTICS).append(DOUBLE_NEWLINE);
        appendTableRow(sb, METRIC, COUNT);
        appendTableDivider(sb, 2);
        appendTableRow(sb, COMPONENT_COUNT, String.valueOf(model.components().size()));
        appendTableRow(sb, DEPENDENCIES, String.valueOf(model.dependencies().size()));
        appendTableRow(sb, API_ENDPOINTS, String.valueOf(model.apiEndpoints().size()));
        appendTableRow(sb, DATA_ENTITIES, String.valueOf(model.dataEntities().size()));
        appendTableRow(sb, MESSAGE_FLOWS, String.valueOf(model.messageFlows().size()));
        sb.append(DOUBLE_NEWLINE);
    }

    private void appendNavigationSection(StringBuilder sb, String sectionTitle, boolean isEmpty,
                                        String emptyLabel, String... links) {
        sb.append(sectionTitle).append(DOUBLE_NEWLINE);
        if (isEmpty) {
            sb.append(String.format(NO_FOUND, emptyLabel)).append(DOUBLE_NEWLINE);
        } else {
            for (String link : links) {
                sb.append(link).append(NEWLINE);
            }
            sb.append(DOUBLE_NEWLINE);
        }
    }

    private boolean appendEmptyMessage(StringBuilder sb, boolean isEmpty, String itemType) {
        if (isEmpty) {
            sb.append(String.format(NO_FOUND, itemType)).append(IN_SYSTEM).append(NEWLINE);
            return true;
        }
        return false;
    }

    private void appendEndpointDetails(StringBuilder sb, List<ApiEndpoint> endpoints) {
        sb.append(ENDPOINT_DETAILS).append(DOUBLE_NEWLINE);
        for (ApiEndpoint endpoint : endpoints) {
            appendHeader(sb, 4, nullSafeValue(endpoint.method(), "") + SPACE + endpoint.path());

            if (endpoint.description() != null) {
                sb.append(endpoint.description()).append(DOUBLE_NEWLINE);
            }

            sb.append(TYPE_LABEL).append(endpoint.type()).append(NEWLINE);
            if (endpoint.authentication() != null) {
                sb.append(AUTH_LABEL).append(endpoint.authentication()).append(NEWLINE);
            }
            if (endpoint.requestSchema() != null) {
                sb.append(REQUEST_SCHEMA_LABEL).append(CODE).append(endpoint.requestSchema())
                    .append(CODE).append(NEWLINE);
            }
            if (endpoint.responseSchema() != null) {
                sb.append(RESPONSE_SCHEMA_LABEL).append(CODE).append(endpoint.responseSchema())
                    .append(CODE).append(NEWLINE);
            }

            sb.append(DOUBLE_NEWLINE);
        }
    }

    private void appendDependencySummary(StringBuilder sb, ArchitectureModel model) {
        sb.append(DEPENDENCY_SUMMARY).append(DOUBLE_NEWLINE);

        long directCount = model.dependencies().stream().filter(Dependency::direct).count();
        long transitiveCount = model.dependencies().stream().filter(d -> !d.direct()).count();

        appendTableRow(sb, METRIC, COUNT);
        appendTableDivider(sb, 2);
        appendTableRow(sb, TOTAL_DEPENDENCIES, String.valueOf(model.dependencies().size()));
        appendTableRow(sb, DIRECT_DEPENDENCIES, String.valueOf(directCount));
        appendTableRow(sb, TRANSITIVE_DEPENDENCIES, String.valueOf(transitiveCount));
    }

    private void appendComponentDetails(StringBuilder sb, ArchitectureModel model, Component comp) {
        appendHeader(sb, 3, comp.name());

        sb.append(TYPE_LABEL).append(comp.type()).append(NEWLINE);
        if (comp.technology() != null) {
            sb.append(TECH_LABEL).append(comp.technology()).append(NEWLINE);
        }
        if (comp.repository() != null) {
            sb.append(REPO_LABEL).append(comp.repository()).append(NEWLINE);
        }
        if (comp.description() != null) {
            sb.append(DESC_LABEL).append(comp.description()).append(NEWLINE);
        }

        if (!comp.metadata().isEmpty()) {
            sb.append(METADATA_LABEL).append(NEWLINE);
            for (Map.Entry<String, String> meta : comp.metadata().entrySet()) {
                sb.append(METADATA_INDENT).append(DASH).append(SPACE).append(meta.getKey())
                    .append(COLON).append(meta.getValue()).append(NEWLINE);
            }
        }

        List<ApiEndpoint> apis = model.apiEndpoints().stream()
            .filter(api -> api.componentId().equals(comp.id()))
            .toList();

        if (!apis.isEmpty()) {
            sb.append(DOUBLE_NEWLINE).append(EXPOSED_APIS_LABEL).append(apis.size())
                .append(ENDPOINT_SUFFIX).append(NEWLINE);
        }

        List<DataEntity> entities = model.dataEntities().stream()
            .filter(entity -> entity.componentId().equals(comp.id()))
            .toList();

        if (!entities.isEmpty()) {
            sb.append(DOUBLE_NEWLINE).append(DATA_ENTITIES_LABEL).append(entities.size())
                .append(ENTITY_SUFFIX).append(NEWLINE);
        }

        sb.append(DOUBLE_NEWLINE);
    }

    private void appendDataEntityFields(StringBuilder sb, DataEntity entity) {
        if (!entity.fields().isEmpty()) {
            sb.append(FIELDS_HEADER).append(DOUBLE_NEWLINE);
            appendTableRow(sb, FIELD, DATA_TYPE, NULLABLE, DESCRIPTION);
            appendTableDivider(sb, 4);

            for (DataEntity.Field field : entity.fields()) {
                appendTableRow(sb,
                    field.name(),
                    field.dataType(),
                    field.nullable() ? YES : NO,
                    escapeMarkdown(nullSafeValue(field.description(), DASH_VALUE))
                );
            }

            sb.append(DOUBLE_NEWLINE);
        }
    }

    private String nullSafeValue(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String getComponentNameOrDash(ArchitectureModel model, String componentId) {
        return componentId != null ? getComponentName(model, componentId) : DASH_VALUE;
    }

    /**
     * Escapes markdown special characters.
     */
    private String escapeMarkdown(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", " ");
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

    private void appendTableRow(StringBuilder sb, String... columns) {
        sb.append(PIPE);
        for (String col : columns) {
            sb.append(SPACE).append(col).append(SPACE).append(PIPE);
        }
        sb.append(NEWLINE);
    }

    private void appendTableDivider(StringBuilder sb, int columnCount) {
        sb.append(PIPE);
        for (int i = 0; i < columnCount; i++) {
            sb.append("--------|");
        }
        sb.append(NEWLINE);
    }
}
