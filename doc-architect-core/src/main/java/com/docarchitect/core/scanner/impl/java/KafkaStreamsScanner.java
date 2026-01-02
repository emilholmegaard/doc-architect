package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ApplicabilityStrategies;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScanStatistics;
import com.docarchitect.core.scanner.ScannerApplicabilityStrategy;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.docarchitect.core.scanner.base.FallbackParsingStrategy;
import com.docarchitect.core.scanner.base.RegexPatterns;
import com.docarchitect.core.util.Technologies;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

/**
 * Scanner for Kafka Streams API topology in Java source files.
 *
 * <p>Detects stream processing topologies using the Kafka Streams DSL API,
 * extracting message flows from stream sources and sinks.
 *
 * <p><b>Supported Patterns</b></p>
 * <ul>
 *   <li>{@code builder.stream("topic")} - KStream source from topic</li>
 *   <li>{@code builder.table("topic")} - KTable source from topic</li>
 *   <li>{@code builder.globalTable("topic")} - Global KTable source</li>
 *   <li>{@code stream.to("topic")} - Sink to topic</li>
 *   <li>{@code stream.through("topic")} - Through operator (consume + produce)</li>
 *   <li>{@code stream.repartition()} - Repartition operation</li>
 * </ul>
 *
 * <p><b>AST Analysis (Tier 1 - HIGH confidence)</b></p>
 * <ul>
 *   <li>Parses Java files with JavaParser</li>
 *   <li>Analyzes method call chains (fluent API)</li>
 *   <li>Extracts topic names from string literals</li>
 *   <li>Links stream operations to topics</li>
 * </ul>
 *
 * <p><b>Regex Fallback (Tier 2 - MEDIUM confidence)</b></p>
 * <ul>
 *   <li>Pattern: {@code builder\\.stream\\s*\\(\\s*"([^"]+)"}</li>
 *   <li>Pattern: {@code builder\\.table\\s*\\(\\s*"([^"]+)"}</li>
 *   <li>Pattern: {@code \\.to\\s*\\(\\s*"([^"]+)"}</li>
 *   <li>Pattern: {@code \\.through\\s*\\(\\s*"([^"]+)"}</li>
 * </ul>
 *
 * <p><b>Example Detection</b></p>
 * <pre>{@code
 * StreamsBuilder builder = new StreamsBuilder();
 * KStream<String, Order> orders = builder.stream("orders");
 * KTable<String, Product> products = builder.table("products");
 *
 * orders
 *     .join(products, ...)
 *     .to("enriched-orders");
 * }</pre>
 *
 * <p>Generates:
 * <ul>
 *   <li>MessageFlow: null → ClassName (topic: orders, type: KStream)</li>
 *   <li>MessageFlow: null → ClassName (topic: products, type: KTable)</li>
 *   <li>MessageFlow: ClassName → null (topic: enriched-orders)</li>
 * </ul>
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class KafkaStreamsScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "kafka-streams";
    private static final String SCANNER_DISPLAY_NAME = "Kafka Streams Topology Scanner";
    private static final String FILE_PATTERN = "**/*.java";
    private static final int SCANNER_PRIORITY = 75;
    private static final String TECHNOLOGY = "kafka-streams";

    // Kafka Streams API methods
    private static final String METHOD_STREAM = "stream";
    private static final String METHOD_TABLE = "table";
    private static final String METHOD_GLOBAL_TABLE = "globalTable";
    private static final String METHOD_TO = "to";
    private static final String METHOD_THROUGH = "through";
    private static final String METHOD_REPARTITION = "repartition";

    private static final String DEFAULT_MESSAGE_TYPE = "KStream<?,?>";
    private static final String KTABLE_TYPE = "KTable<?,?>";
    private static final String QUOTE_REGEX = "\"";

    private static final int FIRST_ARGUMENT_INDEX = 0;
    private static final int MIN_ARGUMENTS_FOR_TOPIC = 1;

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final Pattern STREAM_PATTERN =
        Pattern.compile("builder\\.stream\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern TABLE_PATTERN =
        Pattern.compile("builder\\.(?:table|globalTable)\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern TO_PATTERN =
        Pattern.compile("\\.to\\s*\\(\\s*\"([^\"]+)\"");

    private static final Pattern THROUGH_PATTERN =
        Pattern.compile("\\.through\\s*\\(\\s*\"([^\"]+)\"");

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
        return Set.of(Technologies.JAVA);
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of(FILE_PATTERN);
    }

    @Override
    public int getPriority() {
        return SCANNER_PRIORITY;
    }

    @Override
    public ScannerApplicabilityStrategy getApplicabilityStrategy() {
        return ApplicabilityStrategies.hasJavaFiles()
            .and(ApplicabilityStrategies.hasKafkaStreams()
                .or(ApplicabilityStrategies.hasFileContaining("org.apache.kafka.streams", "StreamsBuilder", "KStream")));
    }

    /**
     * Pre-filter files to only scan those containing Kafka Streams API usage.
     *
     * <p>This avoids attempting to parse files that don't contain Kafka Streams code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Topology.java, *Stream*.java, *Processor.java</li>
     *   <li>Kafka Streams imports: org.apache.kafka.streams</li>
     *   <li>Kafka Streams classes: StreamsBuilder, KStream, KTable, Topology</li>
     *   <li>Kafka Streams methods: .stream(), .table(), .to()</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains Kafka Streams patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Topology.java") ||
            fileName.endsWith("Processor.java") ||
            fileName.contains("Stream") ||
            fileName.contains("Kafka")) {
            log.debug("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain Kafka Streams patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Priority 2-4: Check for Kafka Streams imports, classes, and methods
            boolean hasStreamsImport = content.contains("org.apache.kafka.streams");
            boolean hasStreamsClasses =
                content.contains("StreamsBuilder") ||
                content.contains("KStream") ||
                content.contains("KTable") ||
                content.contains("Topology");
            boolean hasStreamsMethods =
                content.contains(".stream(") ||
                content.contains(".table(") ||
                content.contains(".to(");

            boolean hasStreamsPatterns = hasStreamsImport || hasStreamsClasses || hasStreamsMethods;

            if (hasStreamsPatterns) {
                log.debug("Including file with Kafka Streams patterns: {} (import={}, classes={}, methods={})",
                    fileName, hasStreamsImport, hasStreamsClasses, hasStreamsMethods);
            } else {
                log.debug("Skipping file without Kafka Streams patterns: {}", fileName);
            }

            // For test files, require Kafka Streams patterns
            // For non-test files, allow if they have Kafka Streams patterns
            if (isTestFile) {
                return hasStreamsPatterns;
            }

            return hasStreamsPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Kafka Streams topologies in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        ScanStatistics.Builder statsBuilder = new ScanStatistics.Builder();

        List<Path> javaFiles = context.findFiles(FILE_PATTERN).toList();
        statsBuilder.filesDiscovered(javaFiles.size());

        log.debug("Found {} total Java files to examine", javaFiles.size());

        if (javaFiles.isEmpty()) {
            log.debug("No Java files found in: {}", context.rootPath());
            return emptyResult();
        }

        int skippedCount = 0;

        for (Path javaFile : javaFiles) {
            if (!shouldScanFile(javaFile)) {
                skippedCount++;
                continue;
            }

            statsBuilder.incrementFilesScanned();

            // Use three-tier parsing with fallback
            FileParseResult<MessageFlow> result = parseWithFallback(
                javaFile,
                cu -> extractMessageFlowsFromAST(cu),
                createFallbackStrategy(),
                statsBuilder
            );

            if (result.isSuccess()) {
                messageFlows.addAll(result.getData());
            }
        }

        ScanStatistics statistics = statsBuilder.build();
        log.info("Found {} Kafka Streams message flows (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
                 messageFlows.size(), statistics.getSuccessRate(), statistics.getOverallParseRate(), skippedCount);

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
     * Extracts message flows from a parsed CompilationUnit using AST analysis.
     * This is the Tier 1 (HIGH confidence) parsing strategy.
     *
     * @param cu the parsed CompilationUnit
     * @return list of discovered message flows
     */
    private List<MessageFlow> extractMessageFlowsFromAST(CompilationUnit cu) {
        List<MessageFlow> messageFlows = new ArrayList<>();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Find all method call expressions in this class
            classDecl.findAll(MethodCallExpr.class).forEach(methodCall -> {
                extractStreamSourceFlows(methodCall, fullyQualifiedName, messageFlows);
                extractStreamSinkFlows(methodCall, fullyQualifiedName, messageFlows);
            });
        });

        return messageFlows;
    }

    /**
     * Creates a regex-based fallback parsing strategy for when AST parsing fails.
     * This is the Tier 2 (MEDIUM confidence) parsing strategy.
     *
     * <p>The fallback strategy uses regex patterns to extract:
     * <ul>
     *   <li>builder.stream() calls</li>
     *   <li>builder.table() calls</li>
     *   <li>.to() calls</li>
     *   <li>.through() calls</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<MessageFlow> createFallbackStrategy() {
        return (file, content) -> {
            List<MessageFlow> flows = new ArrayList<>();

            // Check if file contains Kafka Streams patterns
            if (!content.contains("StreamsBuilder") && !content.contains("KStream") &&
                !content.contains("KTable")) {
                return flows;
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract builder.stream() flows (consumers)
            Matcher streamMatcher = STREAM_PATTERN.matcher(content);
            while (streamMatcher.find()) {
                String topic = streamMatcher.group(1);
                flows.add(new MessageFlow(null, fullyQualifiedName, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            }

            // Extract builder.table() flows (consumers)
            Matcher tableMatcher = TABLE_PATTERN.matcher(content);
            while (tableMatcher.find()) {
                String topic = tableMatcher.group(1);
                flows.add(new MessageFlow(null, fullyQualifiedName, topic,
                    KTABLE_TYPE, null, TECHNOLOGY));
            }

            // Extract .to() flows (producers)
            Matcher toMatcher = TO_PATTERN.matcher(content);
            while (toMatcher.find()) {
                String topic = toMatcher.group(1);
                flows.add(new MessageFlow(fullyQualifiedName, null, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            }

            // Extract .through() flows (both consumer and producer)
            Matcher throughMatcher = THROUGH_PATTERN.matcher(content);
            while (throughMatcher.find()) {
                String topic = throughMatcher.group(1);
                // through() is both a consumer and producer
                flows.add(new MessageFlow(null, fullyQualifiedName, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
                flows.add(new MessageFlow(fullyQualifiedName, null, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            }

            log.debug("Fallback parsing found {} message flows in {}", flows.size(), file.getFileName());
            return flows;
        };
    }

    /**
     * Extracts stream source flows (stream, table, globalTable) from method calls.
     * These are consumers that read from Kafka topics.
     */
    private void extractStreamSourceFlows(MethodCallExpr methodCall, String className, List<MessageFlow> messageFlows) {
        String methodName = methodCall.getNameAsString();

        if (!METHOD_STREAM.equals(methodName) &&
            !METHOD_TABLE.equals(methodName) &&
            !METHOD_GLOBAL_TABLE.equals(methodName)) {
            return;
        }

        // Check if this is called on a StreamsBuilder instance
        if (!isStreamBuilderMethod(methodCall)) {
            return;
        }

        // Extract topic name from first argument
        if (methodCall.getArguments().size() >= MIN_ARGUMENTS_FOR_TOPIC) {
            String topicArg = methodCall.getArguments().get(FIRST_ARGUMENT_INDEX).toString();
            String topic = topicArg.replaceAll(QUOTE_REGEX, "");

            String messageType = METHOD_TABLE.equals(methodName) || METHOD_GLOBAL_TABLE.equals(methodName)
                ? KTABLE_TYPE
                : DEFAULT_MESSAGE_TYPE;

            MessageFlow flow = new MessageFlow(
                null,
                className,
                topic,
                messageType,
                null,
                TECHNOLOGY
            );

            messageFlows.add(flow);
            log.debug("Found Kafka Streams source: {} consuming from topic: {} ({})", className, topic, methodName);
        }
    }

    /**
     * Extracts stream sink flows (to, through) from method calls.
     * These are producers that write to Kafka topics.
     */
    private void extractStreamSinkFlows(MethodCallExpr methodCall, String className, List<MessageFlow> messageFlows) {
        String methodName = methodCall.getNameAsString();

        if (!METHOD_TO.equals(methodName) && !METHOD_THROUGH.equals(methodName)) {
            return;
        }

        // Extract topic name from first argument
        if (methodCall.getArguments().size() >= MIN_ARGUMENTS_FOR_TOPIC) {
            String topicArg = methodCall.getArguments().get(FIRST_ARGUMENT_INDEX).toString();
            String topic = topicArg.replaceAll(QUOTE_REGEX, "");

            // For .to(), add producer flow
            MessageFlow producerFlow = new MessageFlow(
                className,
                null,
                topic,
                DEFAULT_MESSAGE_TYPE,
                null,
                TECHNOLOGY
            );
            messageFlows.add(producerFlow);
            log.debug("Found Kafka Streams sink: {} producing to topic: {} ({})", className, topic, methodName);

            // For .through(), also add consumer flow (it's both read and write)
            if (METHOD_THROUGH.equals(methodName)) {
                MessageFlow consumerFlow = new MessageFlow(
                    null,
                    className,
                    topic,
                    DEFAULT_MESSAGE_TYPE,
                    null,
                    TECHNOLOGY
                );
                messageFlows.add(consumerFlow);
                log.debug("Found Kafka Streams through: {} also consuming from topic: {}", className, topic);
            }
        }
    }

    /**
     * Checks if a method call is invoked on a StreamsBuilder instance.
     * Looks for patterns like: builder.stream(), streamsBuilder.table(), etc.
     */
    private boolean isStreamBuilderMethod(MethodCallExpr methodCall) {
        return methodCall.getScope()
            .map(scope -> {
                String scopeStr = scope.toString().toLowerCase();
                return scopeStr.contains("builder") || scopeStr.contains("streams");
            })
            .orElse(false);
    }
}
