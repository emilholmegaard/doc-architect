package com.docarchitect.core.scanner.impl.dotnet;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ast.AstParserFactory;
import com.docarchitect.core.scanner.ast.DotNetAst;
import com.docarchitect.core.scanner.base.AbstractAstScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.util.Technologies;

/**
 * Scanner for Kafka message flows in C# source files using AST parsing.
 *
 * <p>Detects Kafka consumers and producers using C# AST analysis for:
 * <ul>
 *   <li>[KafkaConsumer] and [Topic] attributes on methods</li>
 *   <li>IConsumer&lt;TKey, TValue&gt; field/property declarations</li>
 *   <li>IProducer&lt;TKey, TValue&gt; field/property declarations</li>
 *   <li>Method calls to ProduceAsync() and Consume()</li>
 * </ul>
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class KafkaScanner extends AbstractAstScanner<DotNetAst.CSharpClass> {

    private static final String SCANNER_ID = "dotnet-kafka-messaging";
    private static final String SCANNER_DISPLAY_NAME = "Kafka Message Flow Scanner (.NET)";
    private static final String FILE_PATTERN_NESTED = "**/*.cs";
    private static final String FILE_PATTERN_ROOT = "*.cs";
    private static final int SCANNER_PRIORITY = 70;
    private static final String TECHNOLOGY = "kafka";

    // Kafka attribute names
    private static final String KAFKA_CONSUMER_ATTRIBUTE = "KafkaConsumer";
    private static final String TOPIC_ATTRIBUTE = "Topic";

    // Kafka interface patterns
    private static final String ICONSUMER_INTERFACE = "IConsumer";
    private static final String IPRODUCER_INTERFACE = "IProducer";

    // Method names
    private static final String PRODUCE_ASYNC_METHOD = "ProduceAsync";
    private static final String CONSUME_METHOD = "Consume";

    private static final String DEFAULT_MESSAGE_TYPE = "Object";
    private static final String DEFAULT_TOPIC = "unknown-topic";
    private static final String UNKNOWN_PUBLISHER = "unknown-publisher";
    private static final String UNKNOWN_SUBSCRIBER = "unknown-subscriber";

    public KafkaScanner() {
        super(AstParserFactory.getDotNetParser());
    }

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
        return Set.of(Technologies.CSHARP);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, FILE_PATTERN_NESTED, FILE_PATTERN_ROOT);
    }

    /**
     * Pre-filter files to only scan those containing Kafka-related imports or patterns.
     *
     * <p>This avoids attempting to parse files that don't contain Kafka code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * @param file path to C# source file
     * @return true if file contains Kafka patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Skip test files unless they contain Kafka patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\") ||
                           filePath.contains("/Test/") || filePath.contains("\\Test\\") ||
                           filePath.contains(".Tests/") || filePath.contains(".Tests\\");

        try {
            String content = readFileContent(file);

            // Check for Kafka imports and patterns
            boolean hasKafkaPatterns = content.contains("using Confluent.Kafka") ||
                                      content.contains("IConsumer<") ||
                                      content.contains("IProducer<") ||
                                      content.contains("[KafkaConsumer") ||
                                      content.contains("[Topic") ||
                                      content.contains("ProduceAsync") ||
                                      content.contains(".Consume(");

            // For test files, require Kafka patterns
            // For non-test files, allow if they have Kafka patterns
            if (isTestFile) {
                return hasKafkaPatterns;
            }

            return hasKafkaPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Kafka message flows in .NET project: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        // Find C# files at both root and nested levels
        List<Path> csFiles = new ArrayList<>();
        context.findFiles(FILE_PATTERN_ROOT).forEach(csFiles::add);
        context.findFiles(FILE_PATTERN_NESTED).forEach(csFiles::add);

        statsBuilder.filesDiscovered(csFiles.size());

        if (csFiles.isEmpty()) {
            return emptyResult();
        }

        int skippedFiles = 0;

        for (Path csFile : csFiles) {
            if (!shouldScanFile(csFile)) {
                skippedFiles++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<MessageFlow> result = parseWithFallback(
                csFile,
                classes -> extractMessageFlowsFromAST(csFile, classes),
                createFallbackStrategy(),
                statsBuilder
            );

            if (result.isSuccess()) {
                messageFlows.addAll(result.getData());
            }
        }

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} Kafka message flows (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
                messageFlows.size(), statistics.getSuccessRate(), statistics.getOverallParseRate(), skippedFiles);

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            messageFlows,
            List.of(),
            List.of(),
            List.of(),
            statistics
        );
    }

    /**
     * Extracts message flows from parsed AST classes using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param csFile the C# file being parsed
     * @param classes the parsed C# classes
     * @return list of discovered message flows
     */
    private List<MessageFlow> extractMessageFlowsFromAST(Path csFile, List<DotNetAst.CSharpClass> classes) {
        List<MessageFlow> messageFlows = new ArrayList<>();

        try {
            String fileContent = readFileContent(csFile);
            String namespace = extractNamespace(fileContent);

            for (DotNetAst.CSharpClass cls : classes) {
                // Build fully qualified class name
                String className;
                if (namespace != null && !namespace.isEmpty()) {
                    className = namespace + "." + cls.name();
                } else if (!cls.namespace().isEmpty()) {
                    className = cls.namespace() + "." + cls.name();
                } else {
                    className = cls.name();
                }

                // Extract consumers from method attributes
                extractConsumersFromAttributes(cls, className, messageFlows);

                // Extract consumers/producers from field/property types (AST properties)
                extractFromProperties(cls, className, messageFlows);

                // Extract from private fields using regex (since AST doesn't capture private fields)
                extractFromFields(fileContent, className, messageFlows);

                // Extract from method calls (ProduceAsync, Consume)
                extractFromMethodCalls(fileContent, className, messageFlows);
            }
        } catch (IOException e) {
            log.debug("Failed to read file content for AST extraction: {}", csFile);
        }

        return messageFlows;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<MessageFlow> createFallbackStrategy() {
        return (file, content) -> {
            List<MessageFlow> flows = new ArrayList<>();

            // Check if file contains Kafka patterns
            if (!content.contains("IConsumer<") && !content.contains("IProducer<") &&
                !content.contains("ProduceAsync") && !content.contains(".Consume(")) {
                return flows;
            }

            // Extract class name from content
            java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
                "(?:public\\s+)?class\\s+(\\w+)"
            );
            java.util.regex.Matcher classMatcher = classPattern.matcher(content);
            if (!classMatcher.find()) {
                return flows;
            }

            String className = classMatcher.group(1);
            String namespace = extractNamespace(content);
            if (namespace != null && !namespace.isEmpty()) {
                className = namespace + "." + className;
            }

            // Extract ProduceAsync calls using regex
            java.util.regex.Pattern produceAsyncPattern = java.util.regex.Pattern.compile(
                "ProduceAsync\\s*\\(\\s*\"([^\"]+)\""
            );
            java.util.regex.Matcher produceAsyncMatcher = produceAsyncPattern.matcher(content);
            while (produceAsyncMatcher.find()) {
                String topic = produceAsyncMatcher.group(1);
                flows.add(new MessageFlow(
                    className,
                    UNKNOWN_SUBSCRIBER,
                    topic,
                    DEFAULT_MESSAGE_TYPE,
                    null,
                    TECHNOLOGY
                ));
            }

            // Extract Consume calls using regex
            java.util.regex.Pattern consumePattern = java.util.regex.Pattern.compile(
                "Consume\\s*\\(\\s*\"([^\"]+)\""
            );
            java.util.regex.Matcher consumeMatcher = consumePattern.matcher(content);
            while (consumeMatcher.find()) {
                String topic = consumeMatcher.group(1);
                flows.add(new MessageFlow(
                    UNKNOWN_PUBLISHER,
                    className,
                    topic,
                    DEFAULT_MESSAGE_TYPE,
                    null,
                    TECHNOLOGY
                ));
            }

            // Extract IConsumer/IProducer fields
            java.util.regex.Pattern consumerPattern = java.util.regex.Pattern.compile(
                "(?:private|public)\\s+IConsumer<[^>]+>\\s+(\\w+)"
            );
            java.util.regex.Matcher consumerMatcher = consumerPattern.matcher(content);
            if (consumerMatcher.find()) {
                flows.add(new MessageFlow(
                    UNKNOWN_PUBLISHER,
                    className,
                    DEFAULT_TOPIC,
                    DEFAULT_MESSAGE_TYPE,
                    null,
                    TECHNOLOGY
                ));
            }

            java.util.regex.Pattern producerPattern = java.util.regex.Pattern.compile(
                "(?:private|public)\\s+IProducer<[^>]+>\\s+(\\w+)"
            );
            java.util.regex.Matcher producerMatcher = producerPattern.matcher(content);
            if (producerMatcher.find()) {
                flows.add(new MessageFlow(
                    className,
                    UNKNOWN_SUBSCRIBER,
                    DEFAULT_TOPIC,
                    DEFAULT_MESSAGE_TYPE,
                    null,
                    TECHNOLOGY
                ));
            }

            log.debug("Fallback parsing found {} message flows in {}", flows.size(), file.getFileName());
            return flows;
        };
    }

    /**
     * @deprecated Use {@link #extractMessageFlowsFromAST(Path, List)} instead
     */
    @Deprecated
    private void parseKafkaFlows(Path csFile, List<MessageFlow> messageFlows) throws IOException {
        List<DotNetAst.CSharpClass> classes = parseAstFile(csFile);
        String fileContent = readFileContent(csFile);

        // Extract namespace from file content (C# AST parser doesn't capture it reliably)
        String namespace = extractNamespace(fileContent);

        for (DotNetAst.CSharpClass cls : classes) {
            // Build fully qualified class name
            String className;
            if (namespace != null && !namespace.isEmpty()) {
                className = namespace + "." + cls.name();
            } else if (!cls.namespace().isEmpty()) {
                className = cls.namespace() + "." + cls.name();
            } else {
                className = cls.name();
            }

            // Extract consumers from method attributes
            extractConsumersFromAttributes(cls, className, messageFlows);

            // Extract consumers/producers from field/property types (AST properties)
            extractFromProperties(cls, className, messageFlows);

            // Extract from private fields using regex (since AST doesn't capture private fields)
            extractFromFields(fileContent, className, messageFlows);

            // Extract from method calls (ProduceAsync, Consume)
            extractFromMethodCalls(fileContent, className, messageFlows);
        }
    }

    /**
     * Extract namespace from C# file content using regex.
     */
    private String extractNamespace(String fileContent) {
        // Pattern to match: namespace MyApp.Consumers;
        java.util.regex.Pattern namespacePattern = java.util.regex.Pattern.compile(
            "namespace\\s+([\\w.]+)\\s*;"
        );

        java.util.regex.Matcher matcher = namespacePattern.matcher(fileContent);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void extractConsumersFromAttributes(DotNetAst.CSharpClass cls, String className,
                                                 List<MessageFlow> messageFlows) {
        for (DotNetAst.Method method : cls.methods()) {
            for (DotNetAst.Attribute attr : method.attributes()) {
                String attrName = attr.name();

                // [KafkaConsumer("topic-name")]
                if (KAFKA_CONSUMER_ATTRIBUTE.equals(attrName) || TOPIC_ATTRIBUTE.equals(attrName)) {
                    String topic = extractTopicFromAttribute(attr);
                    if (topic != null) {
                        MessageFlow flow = new MessageFlow(
                            UNKNOWN_PUBLISHER,
                            className,
                            topic,
                            DEFAULT_MESSAGE_TYPE,
                            null,
                            TECHNOLOGY
                        );
                        messageFlows.add(flow);
                        log.debug("Found Kafka consumer (attribute): {} listening on topic: {}", className, topic);
                    }
                }
            }
        }
    }

    private void extractFromProperties(DotNetAst.CSharpClass cls, String className,
                                        List<MessageFlow> messageFlows) {
        for (DotNetAst.Property prop : cls.properties()) {
            String type = prop.type();

            // IConsumer<TKey, TValue>
            if (type.contains(ICONSUMER_INTERFACE)) {
                String messageType = extractGenericType(type);
                MessageFlow flow = new MessageFlow(
                    UNKNOWN_PUBLISHER,
                    className,
                    DEFAULT_TOPIC,
                    messageType,
                    null,
                    TECHNOLOGY
                );
                messageFlows.add(flow);
                log.debug("Found Kafka consumer (IConsumer): {} with message type: {}", className, messageType);
            }

            // IProducer<TKey, TValue>
            if (type.contains(IPRODUCER_INTERFACE)) {
                String messageType = extractGenericType(type);
                MessageFlow flow = new MessageFlow(
                    className,
                    UNKNOWN_SUBSCRIBER,
                    DEFAULT_TOPIC,
                    messageType,
                    null,
                    TECHNOLOGY
                );
                messageFlows.add(flow);
                log.debug("Found Kafka producer (IProducer): {} with message type: {}", className, messageType);
            }
        }
    }

    private String extractTopicFromAttribute(DotNetAst.Attribute attr) {
        if (attr.arguments().isEmpty()) {
            return null;
        }

        // Get first argument and remove quotes
        String arg = attr.arguments().get(0);
        return arg.replaceAll("^\"|\"$", "");
    }

    private String extractGenericType(String type) {
        // Extract second generic argument from IConsumer<TKey, TValue> or IProducer<TKey, TValue>
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');

        if (start == -1 || end == -1) {
            return DEFAULT_MESSAGE_TYPE;
        }

        String generics = type.substring(start + 1, end);
        String[] parts = generics.split(",");

        if (parts.length >= 2) {
            return parts[1].trim();
        }

        return DEFAULT_MESSAGE_TYPE;
    }

    /**
     * Extract Kafka consumers/producers from private fields using regex.
     * This is needed because C# AST parser doesn't extract private fields.
     */
    private void extractFromFields(String fileContent, String className, List<MessageFlow> messageFlows) {
        // Pattern to match: private IConsumer<TKey, TValue> _consumer;
        java.util.regex.Pattern consumerFieldPattern = java.util.regex.Pattern.compile(
            "private\\s+IConsumer<([^,]+),\\s*([^>]+)>\\s+(\\w+)"
        );

        // Pattern to match: private IProducer<TKey, TValue> _producer;
        java.util.regex.Pattern producerFieldPattern = java.util.regex.Pattern.compile(
            "private\\s+IProducer<([^,]+),\\s*([^>]+)>\\s+(\\w+)"
        );

        // Extract consumers
        java.util.regex.Matcher consumerMatcher = consumerFieldPattern.matcher(fileContent);
        while (consumerMatcher.find()) {
            String messageType = consumerMatcher.group(2).trim();
            MessageFlow flow = new MessageFlow(
                UNKNOWN_PUBLISHER,
                className,
                DEFAULT_TOPIC,
                messageType,
                null,
                TECHNOLOGY
            );
            messageFlows.add(flow);
            log.debug("Found Kafka consumer (private field): {} with message type: {}", className, messageType);
        }

        // Extract producers
        java.util.regex.Matcher producerMatcher = producerFieldPattern.matcher(fileContent);
        while (producerMatcher.find()) {
            String messageType = producerMatcher.group(2).trim();
            MessageFlow flow = new MessageFlow(
                className,
                UNKNOWN_SUBSCRIBER,
                DEFAULT_TOPIC,
                messageType,
                null,
                TECHNOLOGY
            );
            messageFlows.add(flow);
            log.debug("Found Kafka producer (private field): {} with message type: {}", className, messageType);
        }
    }

    /**
     * Extract Kafka message flows from method calls (ProduceAsync, Consume).
     */
    private void extractFromMethodCalls(String fileContent, String className, List<MessageFlow> messageFlows) {
        // Pattern to match: ProduceAsync("topic-name", ...)
        java.util.regex.Pattern produceAsyncPattern = java.util.regex.Pattern.compile(
            "ProduceAsync\\s*\\(\\s*\"([^\"]+)\""
        );

        // Pattern to match: Consume("topic-name")
        java.util.regex.Pattern consumePattern = java.util.regex.Pattern.compile(
            "Consume\\s*\\(\\s*\"([^\"]+)\""
        );

        // Extract ProduceAsync calls
        java.util.regex.Matcher produceAsyncMatcher = produceAsyncPattern.matcher(fileContent);
        while (produceAsyncMatcher.find()) {
            String topic = produceAsyncMatcher.group(1);
            MessageFlow flow = new MessageFlow(
                className,
                UNKNOWN_SUBSCRIBER,
                topic,
                DEFAULT_MESSAGE_TYPE,
                null,
                TECHNOLOGY
            );
            messageFlows.add(flow);
            log.debug("Found Kafka producer (ProduceAsync): {} publishing to topic: {}", className, topic);
        }

        // Extract Consume calls
        java.util.regex.Matcher consumeMatcher = consumePattern.matcher(fileContent);
        while (consumeMatcher.find()) {
            String topic = consumeMatcher.group(1);
            MessageFlow flow = new MessageFlow(
                UNKNOWN_PUBLISHER,
                className,
                topic,
                DEFAULT_MESSAGE_TYPE,
                null,
                TECHNOLOGY
            );
            messageFlows.add(flow);
            log.debug("Found Kafka consumer (Consume): {} consuming from topic: {}", className, topic);
        }
    }
}
