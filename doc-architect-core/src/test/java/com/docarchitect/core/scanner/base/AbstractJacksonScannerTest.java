package com.docarchitect.core.scanner.base;

import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractJacksonScanner} JSON and XML parsing utilities.
 *
 * <p>This test validates the Jackson-based parsing functionality:
 * <ul>
 *   <li>XML parsing via XmlMapper</li>
 *   <li>JSON parsing via ObjectMapper</li>
 *   <li>JsonNode navigation and attribute extraction</li>
 *   <li>Array normalization for XML elements</li>
 * </ul>
 *
 * @since 1.0.0
 */
class AbstractJacksonScannerTest extends ScannerTestBase {

    private TestJacksonScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new TestJacksonScanner();
    }

    // ==================== XML Parsing ====================

    @Test
    void parseXml_withValidXml_returnsJsonNode() throws IOException {
        // Given: An XML file
        Path xmlFile = createFile("test.xml", """
            <root>
                <name>TestProject</name>
                <version>1.0.0</version>
            </root>
            """);

        // When: XML is parsed
        JsonNode node = scanner.parseXml(xmlFile);

        // Then: Should return JSON representation of XML
        assertThat(node).isNotNull();
        assertThat(node.has("name")).isTrue();
        assertThat(node.get("name").asText()).isEqualTo("TestProject");
        assertThat(node.get("version").asText()).isEqualTo("1.0.0");
    }

    @Test
    void parseXmlContent_withXmlString_returnsJsonNode() throws IOException {
        // Given: XML content as string
        String xml = """
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-app</artifactId>
            </project>
            """;

        // When: XML content is parsed
        JsonNode node = scanner.parseXmlContent(xml);

        // Then: Should return JSON representation
        assertThat(node.get("groupId").asText()).isEqualTo("com.example");
        assertThat(node.get("artifactId").asText()).isEqualTo("my-app");
    }

    // ==================== JSON Parsing ====================

    @Test
    void parseJson_withValidJson_returnsJsonNode() throws IOException {
        // Given: A JSON file
        Path jsonFile = createFile("test.json", """
            {
                "name": "my-package",
                "version": "2.0.0",
                "dependencies": {
                    "express": "^4.18.0"
                }
            }
            """);

        // When: JSON is parsed
        JsonNode node = scanner.parseJson(jsonFile);

        // Then: Should return JSON tree
        assertThat(node.get("name").asText()).isEqualTo("my-package");
        assertThat(node.get("version").asText()).isEqualTo("2.0.0");
        assertThat(node.get("dependencies").get("express").asText()).isEqualTo("^4.18.0");
    }

    @Test
    void parseJsonContent_withJsonString_returnsJsonNode() throws IOException {
        // Given: JSON content as string
        String json = "{\"key\": \"value\", \"number\": 42}";

        // When: JSON content is parsed
        JsonNode node = scanner.parseJsonContent(json);

        // Then: Should return JSON tree
        assertThat(node.get("key").asText()).isEqualTo("value");
        assertThat(node.get("number").asInt()).isEqualTo(42);
    }

    // ==================== JsonNode Navigation ====================

    @Test
    void extractAttribute_withExistingAttribute_returnsValue() throws IOException {
        // Given: JSON node with attributes
        JsonNode node = scanner.parseJsonContent("{\"name\": \"test\", \"version\": \"1.0\"}");

        // When: Attribute is extracted
        String name = scanner.extractAttribute(node, "name");
        String version = scanner.extractAttribute(node, "version");

        // Then: Should return attribute values
        assertThat(name).isEqualTo("test");
        assertThat(version).isEqualTo("1.0");
    }

    @Test
    void extractAttribute_withMissingAttribute_returnsNull() throws IOException {
        // Given: JSON node without the attribute
        JsonNode node = scanner.parseJsonContent("{\"name\": \"test\"}");

        // When: Missing attribute is extracted
        String missing = scanner.extractAttribute(node, "missing");

        // Then: Should return null
        assertThat(missing).isNull();
    }

    @Test
    void extractAttribute_withNullNode_returnsNull() {
        // When: extractAttribute is called on null node
        String result = scanner.extractAttribute(null, "name");

        // Then: Should return null
        assertThat(result).isNull();
    }

    @Test
    void extractText_withChildNode_returnsText() throws IOException {
        // Given: JSON node with child
        JsonNode node = scanner.parseJsonContent("{\"child\": \"value\"}");

        // When: Text is extracted from child
        String text = scanner.extractText(node, "child");

        // Then: Should return child text
        assertThat(text).isEqualTo("value");
    }

    @Test
    void extractText_withMissingChild_returnsNull() throws IOException {
        // Given: JSON node without the child
        JsonNode node = scanner.parseJsonContent("{\"name\": \"test\"}");

        // When: Missing child is extracted
        String text = scanner.extractText(node, "missing");

        // Then: Should return null
        assertThat(text).isNull();
    }

    @Test
    void getTextOrDefault_withTextNode_returnsText() throws IOException {
        // Given: Text node
        JsonNode node = scanner.parseJsonContent("\"test-value\"");

        // When: Text or default is retrieved
        String result = scanner.getTextOrDefault(node, "default");

        // Then: Should return actual text
        assertThat(result).isEqualTo("test-value");
    }

    @Test
    void getTextOrDefault_withNullNode_returnsDefault() {
        // When: getTextOrDefault is called on null node
        String result = scanner.getTextOrDefault(null, "default-value");

        // Then: Should return default
        assertThat(result).isEqualTo("default-value");
    }

    @Test
    void getTextOrDefault_withNonTextNode_returnsDefault() throws IOException {
        // Given: Non-text node (object)
        JsonNode node = scanner.parseJsonContent("{\"key\": \"value\"}");

        // When: getTextOrDefault is called
        String result = scanner.getTextOrDefault(node, "default");

        // Then: Should return default
        assertThat(result).isEqualTo("default");
    }

    // ==================== Array Handling ====================

    @Test
    void isArray_withArrayNode_returnsTrue() throws IOException {
        // Given: Array node
        JsonNode arrayNode = scanner.parseJsonContent("[1, 2, 3]");

        // When: isArray is checked
        boolean result = scanner.isArray(arrayNode);

        // Then: Should return true
        assertThat(result).isTrue();
    }

    @Test
    void isArray_withNonArrayNode_returnsFalse() throws IOException {
        // Given: Object node
        JsonNode objectNode = scanner.parseJsonContent("{\"key\": \"value\"}");

        // When: isArray is checked
        boolean result = scanner.isArray(objectNode);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void isArray_withNullNode_returnsFalse() {
        // When: isArray is checked on null
        boolean result = scanner.isArray(null);

        // Then: Should return false
        assertThat(result).isFalse();
    }

    @Test
    void normalizeToArray_withSingleElement_wrapsInArray() throws IOException {
        // Given: Single element node
        JsonNode singleNode = scanner.parseJsonContent("{\"key\": \"value\"}");

        // When: Node is normalized to array
        JsonNode arrayNode = scanner.normalizeToArray(singleNode);

        // Then: Should wrap in array
        assertThat(arrayNode.isArray()).isTrue();
        assertThat(arrayNode).hasSize(1);
        assertThat(arrayNode.get(0).get("key").asText()).isEqualTo("value");
    }

    @Test
    void normalizeToArray_withExistingArray_returnsAsIs() throws IOException {
        // Given: Already an array
        JsonNode arrayNode = scanner.parseJsonContent("[{\"key\": \"value1\"}, {\"key\": \"value2\"}]");

        // When: Node is normalized to array
        JsonNode result = scanner.normalizeToArray(arrayNode);

        // Then: Should return as-is
        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(2);
    }

    @Test
    void normalizeToArray_withNull_returnsEmptyArray() {
        // When: Null is normalized to array
        JsonNode result = scanner.normalizeToArray(null);

        // Then: Should return empty array
        assertThat(result.isArray()).isTrue();
        assertThat(result).isEmpty();
    }

    // ==================== Test Scanner Implementation ====================

    /**
     * Concrete test implementation of AbstractJacksonScanner for testing.
     */
    private static class TestJacksonScanner extends AbstractJacksonScanner {

        @Override
        public String getId() {
            return "test-jackson-scanner";
        }

        @Override
        public String getDisplayName() {
            return "Test Jackson Scanner";
        }

        @Override
        public Set<String> getSupportedLanguages() {
            return Set.of("test");
        }

        @Override
        public Set<String> getSupportedFilePatterns() {
            return Set.of("**/*.xml", "**/*.json");
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public boolean appliesTo(ScanContext context) {
            return hasAnyFiles(context, "**/*.xml", "**/*.json");
        }

        @Override
        public ScanResult scan(ScanContext context) {
            return emptyResult();
        }
    }
}
