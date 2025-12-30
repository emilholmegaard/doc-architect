package com.docarchitect.core.scanner.impl.python;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractRegexScanner;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Faust stream processing applications in Python.
 *
 * <p>Faust is a Python library for building streaming applications with Kafka,
 * inspired by Apache Kafka Streams. This scanner detects Faust topics, agents,
 * and message flows.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code app.topic('topic-name', ...)} - Topic definition</li>
 *   <li>{@code @app.agent(topic)} - Agent consuming from a topic</li>
 *   <li>{@code @app.agent()} - Agent without explicit topic</li>
 *   <li>{@code topic.send(...)} - Producing to a topic</li>
 *   <li>{@code await topic.send(...)} - Async producing</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * import faust
 *
 * app = faust.App('myapp')
 * orders_topic = app.topic('orders', value_type=Order)
 *
 * @app.agent(orders_topic)
 * async def process_orders(orders):
 *     async for order in orders:
 *         # Process order
 *         yield order
 * }</pre>
 *
 * <p>Generates:
 * <ul>
 *   <li>MessageFlow: null → process_orders (topic: orders)</li>
 * </ul>
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class FaustScanner extends AbstractRegexScanner {

    private static final String SCANNER_ID = "faust-streaming";
    private static final String SCANNER_DISPLAY_NAME = "Faust Stream Processing Scanner";
    private static final String PATTERN_PYTHON_FILES = "**/*.py";
    private static final String TECHNOLOGY = "faust";

    private static final String DEFAULT_MESSAGE_TYPE = "faust.Stream";
    private static final int SCANNER_PRIORITY = 65;

    // Regex patterns for Faust API detection
    private static final Pattern TOPIC_DEFINITION_PATTERN =
        Pattern.compile("(\\w+)\\s*=\\s*app\\.topic\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    private static final Pattern AGENT_WITH_TOPIC_PATTERN =
        Pattern.compile("@app\\.agent\\s*\\(\\s*(\\w+)\\s*\\)");

    private static final Pattern AGENT_FUNCTION_PATTERN =
        Pattern.compile("(?:async\\s+)?def\\s+(\\w+)\\s*\\(");

    private static final Pattern SEND_PATTERN =
        Pattern.compile("(\\w+)\\.send\\s*\\(");

    private static final Pattern STREAM_FROM_TOPIC_PATTERN =
        Pattern.compile("app\\.stream\\s*\\(\\s*['\"]([^'\"]+)['\"]");

    @Override
    public String getId() {
        return SCANNER_ID;
    }

    @Override
    public String getDisplayName() {
        return SCANNER_DISPLAY_NAME;
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of(Technologies.PYTHON);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(PATTERN_PYTHON_FILES);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, PATTERN_PYTHON_FILES);
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Faust stream processing applications in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<Path> pythonFiles = context.findFiles(PATTERN_PYTHON_FILES).toList();

        if (pythonFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path pythonFile : pythonFiles) {
            try {
                if (!shouldScanFile(pythonFile)) {
                    continue;
                }
                parseFaustFile(pythonFile, messageFlows);
            } catch (Exception e) {
                log.warn("Failed to parse Python file: {} - {}", pythonFile, e.getMessage());
            }
        }

        log.info("Found {} Faust message flows", messageFlows.size());

        return buildSuccessResult(
            List.of(),           // No components
            List.of(),           // No dependencies
            List.of(),           // No API endpoints
            messageFlows,        // Message flows
            List.of(),           // No data entities
            List.of(),           // No relationships
            List.of()            // No warnings
        );
    }

    /**
     * Pre-filters files to only scan those containing Faust imports or usage.
     *
     * @param file path to Python file
     * @return true if file likely contains Faust code
     */
    private boolean shouldScanFile(Path file) {
        try {
            String content = readFileContent(file);
            return content.contains("import faust") ||
                   content.contains("from faust") ||
                   content.contains("app.topic") ||
                   content.contains("app.agent") ||
                   content.contains("app.stream");
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    /**
     * Parses a Python file for Faust stream processing patterns.
     *
     * @param file Python file to parse
     * @param messageFlows list to populate with discovered message flows
     */
    private void parseFaustFile(Path file, List<MessageFlow> messageFlows) throws IOException {
        List<String> lines = readFileLines(file);
        String content = String.join("\n", lines);
        String moduleName = extractModuleName(file);

        // Build topic name to variable mapping
        Map<String, String> topicVariables = extractTopicDefinitions(content);

        // Extract agent-based flows
        extractAgentFlows(lines, moduleName, topicVariables, messageFlows);

        // Extract send-based flows
        extractSendFlows(content, moduleName, topicVariables, messageFlows);

        // Extract stream-based flows
        extractStreamFlows(content, moduleName, messageFlows);
    }

    /**
     * Extracts topic definitions from the file content.
     * Maps variable names to topic names.
     *
     * @param content file content
     * @return map of variable name to topic name
     */
    private Map<String, String> extractTopicDefinitions(String content) {
        Map<String, String> topicVariables = new HashMap<>();
        Matcher matcher = TOPIC_DEFINITION_PATTERN.matcher(content);

        while (matcher.find()) {
            String varName = matcher.group(1);
            String topicName = matcher.group(2);
            topicVariables.put(varName, topicName);
            log.debug("Found Faust topic definition: {} -> {}", varName, topicName);
        }

        return topicVariables;
    }

    /**
     * Extracts message flows from @app.agent decorators.
     *
     * @param lines file lines
     * @param moduleName module name
     * @param topicVariables mapping of variable names to topic names
     * @param messageFlows list to populate
     */
    private void extractAgentFlows(List<String> lines, String moduleName,
                                   Map<String, String> topicVariables,
                                   List<MessageFlow> messageFlows) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // Check for @app.agent decorator
            Matcher agentMatcher = AGENT_WITH_TOPIC_PATTERN.matcher(line);
            if (agentMatcher.find()) {
                String topicVariable = agentMatcher.group(1);
                String topicName = topicVariables.getOrDefault(topicVariable, topicVariable);

                // Find the associated function name
                String functionName = findNextFunctionName(lines, i);
                if (functionName != null) {
                    String handlerName = moduleName + "." + functionName;

                    MessageFlow flow = new MessageFlow(
                        null,
                        handlerName,
                        topicName,
                        DEFAULT_MESSAGE_TYPE,
                        null,
                        TECHNOLOGY
                    );

                    messageFlows.add(flow);
                    log.debug("Found Faust agent: {} consuming from topic: {}", handlerName, topicName);
                }
            }
        }
    }

    /**
     * Extracts message flows from topic.send() calls.
     *
     * @param content file content
     * @param moduleName module name
     * @param topicVariables mapping of variable names to topic names
     * @param messageFlows list to populate
     */
    private void extractSendFlows(String content, String moduleName,
                                  Map<String, String> topicVariables,
                                  List<MessageFlow> messageFlows) {
        Matcher sendMatcher = SEND_PATTERN.matcher(content);

        while (sendMatcher.find()) {
            String topicVariable = sendMatcher.group(1);
            String topicName = topicVariables.getOrDefault(topicVariable, topicVariable);

            MessageFlow flow = new MessageFlow(
                moduleName,
                null,
                topicName,
                DEFAULT_MESSAGE_TYPE,
                null,
                TECHNOLOGY
            );

            messageFlows.add(flow);
            log.debug("Found Faust send: {} producing to topic: {}", moduleName, topicName);
        }
    }

    /**
     * Extracts message flows from app.stream() calls.
     *
     * @param content file content
     * @param moduleName module name
     * @param messageFlows list to populate
     */
    private void extractStreamFlows(String content, String moduleName, List<MessageFlow> messageFlows) {
        Matcher streamMatcher = STREAM_FROM_TOPIC_PATTERN.matcher(content);

        while (streamMatcher.find()) {
            String topicName = streamMatcher.group(1);

            MessageFlow flow = new MessageFlow(
                null,
                moduleName,
                topicName,
                DEFAULT_MESSAGE_TYPE,
                null,
                TECHNOLOGY
            );

            messageFlows.add(flow);
            log.debug("Found Faust stream: {} consuming from topic: {}", moduleName, topicName);
        }
    }

    /**
     * Finds the next function definition after a decorator.
     *
     * @param lines file lines
     * @param startIndex index of decorator line
     * @return function name or null if not found
     */
    private String findNextFunctionName(List<String> lines, int startIndex) {
        for (int i = startIndex + 1; i < Math.min(startIndex + 5, lines.size()); i++) {
            String line = lines.get(i).trim();
            Matcher matcher = AGENT_FUNCTION_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Extracts module name from file path.
     *
     * @param file Python file
     * @return module name
     */
    private String extractModuleName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replaceAll("\\.py$", "");
    }
}
