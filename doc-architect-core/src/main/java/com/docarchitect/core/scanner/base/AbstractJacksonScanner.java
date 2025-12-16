package com.docarchitect.core.scanner.base;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstract base class for scanners that parse XML or JSON files using Jackson.
 *
 * <p>This class provides pre-configured Jackson mappers and utility methods for:
 * <ul>
 *   <li>XML parsing via XmlMapper</li>
 *   <li>JSON parsing via ObjectMapper</li>
 *   <li>JsonNode navigation and attribute extraction</li>
 *   <li>Type-safe value retrieval with defaults</li>
 * </ul>
 *
 * @see AbstractScanner
 * @since 1.0.0
 */
public abstract class AbstractJacksonScanner extends AbstractScanner {

    /**
     * XML mapper for parsing XML files.
     * Thread-safe and reusable across parse operations.
     */
    protected final XmlMapper xmlMapper;

    /**
     * JSON mapper for parsing JSON files.
     * Thread-safe and reusable across parse operations.
     */
    protected final ObjectMapper objectMapper;

    /**
     * Constructor that initializes both XML and JSON mappers.
     */
    protected AbstractJacksonScanner() {
        super();
        this.xmlMapper = new XmlMapper();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== XML Parsing ====================

    /**
     * Parses an XML file into a JsonNode tree.
     *
     * @param file path to XML file
     * @return root JsonNode of parsed XML
     * @throws IOException if file cannot be read or parsed
     */
    protected JsonNode parseXml(Path file) throws IOException {
        String content = readFileContent(file);
        return xmlMapper.readTree(content);
    }

    /**
     * Parses XML content string into a JsonNode tree.
     *
     * @param xmlContent XML content as string
     * @return root JsonNode of parsed XML
     * @throws IOException if content cannot be parsed
     */
    protected JsonNode parseXmlContent(String xmlContent) throws IOException {
        return xmlMapper.readTree(xmlContent);
    }

    // ==================== JSON Parsing ====================

    /**
     * Parses a JSON file into a JsonNode tree.
     *
     * @param file path to JSON file
     * @return root JsonNode of parsed JSON
     * @throws IOException if file cannot be read or parsed
     */
    protected JsonNode parseJson(Path file) throws IOException {
        String content = readFileContent(file);
        return objectMapper.readTree(content);
    }

    /**
     * Parses JSON content string into a JsonNode tree.
     *
     * @param jsonContent JSON content as string
     * @return root JsonNode of parsed JSON
     * @throws IOException if content cannot be parsed
     */
    protected JsonNode parseJsonContent(String jsonContent) throws IOException {
        return objectMapper.readTree(jsonContent);
    }

    // ==================== JsonNode Navigation Utilities ====================

    /**
     * Extracts an attribute value from a JsonNode.
     *
     * <p>This handles both XML attributes and JSON object properties.
     *
     * @param node JsonNode to extract from
     * @param attributeName attribute/property name
     * @return attribute value as string, or null if not found
     */
    protected String extractAttribute(JsonNode node, String attributeName) {
        if (node == null) {
            return null;
        }

        JsonNode attrNode = node.get(attributeName);
        if (attrNode != null && attrNode.isValueNode()) {
            return attrNode.asText();
        }

        return null;
    }

    /**
     * Extracts a text value from a child node.
     *
     * @param node parent JsonNode
     * @param childName child element name
     * @return text content of child, or null if not found
     */
    protected String extractText(JsonNode node, String childName) {
        if (node == null) {
            return null;
        }

        JsonNode childNode = node.get(childName);
        if (childNode == null) {
            return null;
        }

        return childNode.asText();
    }

    /**
     * Safely gets a text value with a default fallback.
     *
     * @param node JsonNode to extract from
     * @param defaultValue value to return if node is null or not textual
     * @return text value or default
     */
    protected String getTextOrDefault(JsonNode node, String defaultValue) {
        if (node == null || !node.isTextual()) {
            return defaultValue;
        }
        return node.asText();
    }

    /**
     * Checks if a JsonNode represents an array.
     *
     * @param node JsonNode to check
     * @return true if node is an array
     */
    protected boolean isArray(JsonNode node) {
        return node != null && node.isArray();
    }

    /**
     * Normalizes a JsonNode to always be an array.
     *
     * <p>If the node is already an array, returns it as-is.
     * If the node is a single object, wraps it in an array.
     * Useful for handling XML elements that can appear once or multiple times.
     *
     * @param node JsonNode to normalize
     * @return array JsonNode
     */
    protected JsonNode normalizeToArray(JsonNode node) {
        if (node == null) {
            return xmlMapper.createArrayNode();
        }
        if (node.isArray()) {
            return node;
        }
        return xmlMapper.createArrayNode().add(node);
    }
}
