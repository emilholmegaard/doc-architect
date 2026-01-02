package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

/**
 * Scanner for Kafka message flows in Java source files.
 *
 * <p>Uses JavaParser to extract Kafka consumers (@KafkaListener) and producers
 * (@SendTo, KafkaTemplate.send()).
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class KafkaScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "kafka-messaging";
    private static final String SCANNER_DISPLAY_NAME = "Kafka Message Flow Scanner";
    private static final String FILE_PATTERN = "**/*.java";
    private static final int SCANNER_PRIORITY = 70;
    private static final String TECHNOLOGY = "kafka";
    
    private static final String KAFKA_LISTENER_ANNOTATION = "KafkaListener";
    private static final String SEND_TO_ANNOTATION = "SendTo";
    private static final String KAFKA_TEMPLATE_METHOD = "send";
    private static final String KAFKA_TEMPLATE_IDENTIFIER = "kafkaTemplate";
    private static final String KAFKA_TEMPLATE_CLASS_IDENTIFIER = "KafkaTemplate";
    
    private static final String TOPICS_PARAM_NAME = "topics";
    private static final String DEFAULT_MESSAGE_TYPE = "Object";
    private static final String DEFAULT_TOPIC = "unknown-topic";
    private static final String QUOTE_REGEX = "\"";
    
    private static final char ARRAY_START = '{';
    private static final char ARRAY_END = '}';
    private static final String ARRAY_DELIMITER = ",";
    private static final int FIRST_ARGUMENT_INDEX = 0;
    private static final int MIN_ARGUMENTS_FOR_TOPIC = 1;

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final Pattern LISTENER_PATTERN =
        Pattern.compile("@KafkaListener\\s*\\([^)]*topics\\s*=\\s*[{\"']([^}\"']+)[}\"']");

    private static final Pattern SEND_TO_PATTERN =
        Pattern.compile("@SendTo\\s*\\(\\s*[\"']([^\"']+)[\"']");

    private static final Pattern TEMPLATE_PATTERN =
        Pattern.compile("kafkaTemplate\\.send\\s*\\(\\s*[\"']([^\"']+)[\"']");

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
            .and(ApplicabilityStrategies.hasKafka()
                .or(ApplicabilityStrategies.hasFileContaining("org.springframework.kafka", "@KafkaListener", "KafkaTemplate")));
    }

    /**
     * Pre-filter files to only scan those containing Kafka-related imports or annotations.
     *
     * <p>This avoids attempting to parse files that don't contain Kafka code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Listener.java, *Consumer.java, *Producer.java, *Config.java</li>
     *   <li>Apache Kafka package imports: org.apache.kafka</li>
     *   <li>Spring Kafka package imports: org.springframework.kafka</li>
     *   <li>Direct Kafka annotations: @KafkaListener, @EnableKafka, @SendTo</li>
     *   <li>Kafka classes: KafkaTemplate</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains Kafka patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Listener.java") ||
            fileName.endsWith("Consumer.java") ||
            fileName.endsWith("Producer.java") ||
            fileName.endsWith("MessageHandler.java") ||
            fileName.contains("Kafka") ||
            (fileName.contains("Config") && fileName.endsWith(".java"))) {
            log.debug("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain Kafka patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Priority 2-5: Check for Kafka imports, annotations, and classes
            boolean hasApacheKafkaImport = content.contains("org.apache.kafka");
            boolean hasSpringKafkaImport = content.contains("org.springframework.kafka");
            boolean hasKafkaAnnotations =
                content.contains("@KafkaListener") ||
                content.contains("@EnableKafka") ||
                content.contains("@SendTo");
            boolean hasKafkaClasses = content.contains("KafkaTemplate");

            boolean hasKafkaPatterns = hasApacheKafkaImport || hasSpringKafkaImport ||
                                      hasKafkaAnnotations || hasKafkaClasses;

            if (hasKafkaPatterns) {
                log.debug("Including file with Kafka patterns: {} (apacheImport={}, springImport={}, annotations={}, classes={})",
                    fileName, hasApacheKafkaImport, hasSpringKafkaImport, hasKafkaAnnotations, hasKafkaClasses);
            } else {
                log.debug("Skipping file without Kafka patterns: {}", fileName);
            }

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
        log.info("Scanning Kafka message flows in: {}", context.rootPath());

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
        log.info("Found {} Kafka message flows (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
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

            classDecl.getMethods().forEach(method -> {
                extractKafkaListenerFlows(method, fullyQualifiedName, messageFlows);
                extractSendToFlows(method, fullyQualifiedName, messageFlows);
                extractKafkaTemplateFlows(method, fullyQualifiedName, messageFlows);
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
     *   <li>@KafkaListener annotations with topics</li>
     *   <li>@SendTo annotations</li>
     *   <li>KafkaTemplate.send() method calls</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<MessageFlow> createFallbackStrategy() {
        return (file, content) -> {
            List<MessageFlow> flows = new ArrayList<>();

            // Check if file contains Kafka patterns
            if (!content.contains("KafkaListener") && !content.contains("SendTo") &&
                !content.contains("KafkaTemplate")) {
                return flows;
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract @KafkaListener flows (consumers) using pre-compiled pattern
            Matcher listenerMatcher = LISTENER_PATTERN.matcher(content);
            while (listenerMatcher.find()) {
                String topics = listenerMatcher.group(1);
                for (String topic : topics.split(",")) {
                    topic = topic.trim().replaceAll("\"", "");
                    if (!topic.isEmpty()) {
                        flows.add(new MessageFlow(null, fullyQualifiedName, topic,
                            DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
                    }
                }
            }

            // Extract @SendTo flows (producers) using pre-compiled pattern
            Matcher sendToMatcher = SEND_TO_PATTERN.matcher(content);
            while (sendToMatcher.find()) {
                String topic = sendToMatcher.group(1);
                flows.add(new MessageFlow(fullyQualifiedName, null, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            }

            // Extract KafkaTemplate.send() flows (producers) using pre-compiled pattern
            Matcher templateMatcher = TEMPLATE_PATTERN.matcher(content);
            while (templateMatcher.find()) {
                String topic = templateMatcher.group(1);
                flows.add(new MessageFlow(fullyQualifiedName, null, topic,
                    DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
            }

            log.debug("Fallback parsing found {} message flows in {}", flows.size(), file.getFileName());
            return flows;
        };
    }

    /**
     * @deprecated Use {@link #extractMessageFlowsFromAST(CompilationUnit)} instead
     */
    @Deprecated
    private void parseKafkaFlows(Path javaFile, List<MessageFlow> messageFlows) throws IOException {
        Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
        if (cuOpt.isEmpty()) {
            return;
        }

        CompilationUnit cu = cuOpt.get();

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            String className = classDecl.getNameAsString();
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            classDecl.getMethods().forEach(method -> {
                extractKafkaListenerFlows(method, fullyQualifiedName, messageFlows);
                extractSendToFlows(method, fullyQualifiedName, messageFlows);
                extractKafkaTemplateFlows(method, fullyQualifiedName, messageFlows);
            });
        });
    }

    private void extractKafkaListenerFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        Optional<AnnotationExpr> kafkaListener = method.getAnnotations().stream()
            .filter(ann -> KAFKA_LISTENER_ANNOTATION.equals(ann.getNameAsString()))
            .findFirst();

        if (kafkaListener.isEmpty()) {
            return;
        }

        List<String> topics = extractTopicsFromAnnotation(kafkaListener.get());
        String messageType = method.getParameters().isEmpty()
            ? DEFAULT_MESSAGE_TYPE
            : method.getParameters().get(0).getType().asString();

        for (String topic : topics) {
            MessageFlow flow = new MessageFlow(
                null,
                className,
                topic,
                messageType,
                null,
                TECHNOLOGY
            );

            messageFlows.add(flow);
            log.debug("Found Kafka consumer: {} listening on topic: {}", className, topic);
        }
    }

    private void extractSendToFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        Optional<AnnotationExpr> sendTo = method.getAnnotations().stream()
            .filter(ann -> SEND_TO_ANNOTATION.equals(ann.getNameAsString()))
            .findFirst();

        if (sendTo.isEmpty()) {
            return;
        }

        String topic = extractTopicFromSendTo(sendTo.get());
        String messageType = method.getType().asString();

        MessageFlow flow = new MessageFlow(
            className,
            null,
            topic,
            messageType,
            null,
            TECHNOLOGY
        );

        messageFlows.add(flow);
        log.debug("Found Kafka producer (@SendTo): {} sending to topic: {}", className, topic);
    }

    private void extractKafkaTemplateFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (!KAFKA_TEMPLATE_METHOD.equals(call.getNameAsString())) {
                return;
            }

            call.getScope().ifPresent(scope -> {
                String scopeStr = scope.toString();
                if (scopeStr.contains(KAFKA_TEMPLATE_IDENTIFIER) || scopeStr.contains(KAFKA_TEMPLATE_CLASS_IDENTIFIER)) {
                    if (call.getArguments().size() >= MIN_ARGUMENTS_FOR_TOPIC) {
                        String topic = call.getArguments().get(FIRST_ARGUMENT_INDEX).toString().replaceAll(QUOTE_REGEX, "");

                        MessageFlow flow = new MessageFlow(
                            className,
                            null,
                            topic,
                            DEFAULT_MESSAGE_TYPE,
                            null,
                            TECHNOLOGY
                        );

                        messageFlows.add(flow);
                        log.debug("Found Kafka producer (KafkaTemplate): {} sending to topic: {}", className, topic);
                    }
                }
            });
        });
    }

    private List<String> extractTopicsFromAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            String value = single.getMemberValue().toString();
            return parseTopicsValue(value);
        }

        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                .filter(pair -> TOPICS_PARAM_NAME.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> parseTopicsValue(pair.getValue().toString()))
                .orElse(List.of());
        }

        return List.of();
    }

    private List<String> parseTopicsValue(String value) {
        value = value.trim();
        if (value.startsWith(String.valueOf(ARRAY_START)) && value.endsWith(String.valueOf(ARRAY_END))) {
            return Arrays.stream(value.substring(1, value.length() - 1).split(ARRAY_DELIMITER))
                .map(String::trim)
                .map(s -> s.replaceAll(QUOTE_REGEX, ""))
                .filter(s -> !s.isEmpty())
                .toList();
        } else {
            return List.of(value.replaceAll(QUOTE_REGEX, ""));
        }
    }

    private String extractTopicFromSendTo(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return single.getMemberValue().toString().replaceAll(QUOTE_REGEX, "");
        }
        return DEFAULT_TOPIC;
    }
}
