package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * Scanner for RabbitMQ message flows in Java source files.
 *
 * <p>Uses JavaParser to extract RabbitMQ consumers (@RabbitListener) and producers
 * (RabbitTemplate.convertAndSend()).
 *
 * @see MessageFlow
 * @since 1.0.0
 */
public class RabbitMQScanner extends AbstractJavaParserScanner {

    private static final String SCANNER_ID = "rabbitmq-messaging";
    private static final String SCANNER_DISPLAY_NAME = "RabbitMQ Message Flow Scanner";
    private static final String FILE_PATTERN = "**/*.java";
    private static final int SCANNER_PRIORITY = 70;
    private static final String TECHNOLOGY = "rabbitmq";

    private static final String RABBIT_LISTENER_ANNOTATION = "RabbitListener";
    private static final String RABBIT_HANDLER_ANNOTATION = "RabbitHandler";
    private static final String RABBIT_TEMPLATE_METHOD = "convertAndSend";
    private static final String RABBIT_TEMPLATE_SEND_METHOD = "send";
    private static final String RABBIT_TEMPLATE_IDENTIFIER = "rabbitTemplate";
    private static final String RABBIT_TEMPLATE_CLASS_IDENTIFIER = "RabbitTemplate";

    private static final String QUEUES_PARAM_NAME = "queues";
    private static final String QUEUE_TO_DECLARE_PARAM = "queuesToDeclare";
    private static final String BINDINGS_PARAM_NAME = "bindings";
    private static final String DEFAULT_MESSAGE_TYPE = "Object";
    private static final String DEFAULT_QUEUE = "unknown-queue";
    private static final String QUOTE_REGEX = "\"";

    private static final char ARRAY_START = '{';
    private static final char ARRAY_END = '}';
    private static final String ARRAY_DELIMITER = ",";
    private static final int FIRST_ARGUMENT_INDEX = 0;
    private static final int MIN_ARGUMENTS_FOR_QUEUE = 1;

    // Regex patterns for fallback parsing (compiled once for performance)
    private static final java.util.regex.Pattern LISTENER_PATTERN =
        java.util.regex.Pattern.compile("@RabbitListener\\s*\\([^)]*queues\\s*=\\s*[{\"']([^}\"']+)[}\"']");

    private static final java.util.regex.Pattern TEMPLATE_PATTERN =
        java.util.regex.Pattern.compile("rabbitTemplate\\.(convertAndSend|send)\\s*\\(\\s*[\"']([^\"']+)[\"']");

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
        return ApplicabilityStrategies.hasJavaFiles().and(ApplicabilityStrategies.hasRabbitMQ());
    }

    /**
     * Pre-filter files to only scan those containing RabbitMQ-related imports or annotations.
     *
     * <p>This avoids attempting to parse files that don't contain RabbitMQ code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * <p><b>Detection Strategy (ordered by likelihood):</b>
     * <ol>
     *   <li>Filename convention: *Listener.java, *Consumer.java, *Producer.java, *Config.java</li>
     *   <li>Spring AMQP package imports (loose pattern for wildcards): springframework + amqp</li>
     *   <li>RabbitMQ package imports: rabbitmq</li>
     *   <li>Direct RabbitMQ annotations: @RabbitListener, @EnableRabbit, etc.</li>
     *   <li>RabbitMQ classes: RabbitTemplate, Queue, Exchange, Binding</li>
     * </ol>
     *
     * @param file path to Java source file
     * @return true if file contains RabbitMQ patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Priority 1: Filename convention (fastest check, no I/O)
        String fileName = file.getFileName().toString();
        if (fileName.endsWith("Listener.java") ||
            fileName.endsWith("Consumer.java") ||
            fileName.endsWith("Producer.java") ||
            fileName.endsWith("MessageHandler.java") ||
            fileName.contains("RabbitMQ") ||
            fileName.contains("Rabbit") ||
            (fileName.contains("Config") && fileName.endsWith(".java"))) {
            log.debug("Including file by naming convention: {}", fileName);
            return true;
        }

        // Skip test files unless they contain RabbitMQ patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Priority 2: Check for Spring AMQP package imports (loose pattern for wildcards)
            // Matches: org.springframework.amqp.* OR individual imports
            boolean hasSpringAmqpImport =
                (content.contains("springframework") && content.contains("amqp")) ||
                (content.contains("springframework") && content.contains("rabbit"));

            // Priority 3: Check for RabbitMQ package imports
            boolean hasRabbitMQImport =
                content.contains("rabbitmq") ||
                content.contains("com.rabbitmq");

            // Priority 4: Check for direct RabbitMQ annotations
            boolean hasRabbitMQAnnotations =
                content.contains("@RabbitListener") ||
                content.contains("@RabbitHandler") ||
                content.contains("@EnableRabbit") ||
                content.contains("@Queue") ||
                content.contains("@Exchange") ||
                content.contains("@QueueBinding");

            // Priority 5: Check for RabbitMQ classes (catches usage even without annotations)
            boolean hasRabbitMQClasses =
                content.contains("RabbitTemplate") ||
                (content.contains("Queue") && content.contains("amqp")) ||
                (content.contains("Exchange") && content.contains("amqp")) ||
                (content.contains("Binding") && content.contains("amqp")) ||
                content.contains("SimpleMessageListenerContainer") ||
                content.contains("RabbitAdmin");

            boolean hasRabbitMQPatterns = hasSpringAmqpImport || hasRabbitMQImport ||
                                         hasRabbitMQAnnotations || hasRabbitMQClasses;

            if (hasRabbitMQPatterns) {
                log.debug("Including file with RabbitMQ patterns: {} (amqpImport={}, rabbitImport={}, annotations={}, classes={})",
                    fileName, hasSpringAmqpImport, hasRabbitMQImport, hasRabbitMQAnnotations, hasRabbitMQClasses);
            } else {
                log.debug("Skipping file without RabbitMQ patterns: {}", fileName);
            }

            // For test files, require RabbitMQ patterns
            // For non-test files, allow if they have RabbitMQ patterns
            if (isTestFile) {
                return hasRabbitMQPatterns;
            }

            return hasRabbitMQPatterns;
        } catch (IOException e) {
            log.debug("Failed to read file for pre-filtering: {}", file);
            return false;
        }
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning RabbitMQ message flows in: {}", context.rootPath());

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
        log.info("Found {} RabbitMQ message flows (success rate: {:.1f}%, overall parse rate: {:.1f}%, skipped {} files)",
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
                extractRabbitListenerFlows(method, fullyQualifiedName, messageFlows);
                extractRabbitTemplateFlows(method, fullyQualifiedName, messageFlows);
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
     *   <li>@RabbitListener annotations with queues</li>
     *   <li>RabbitTemplate.convertAndSend() and RabbitTemplate.send() method calls</li>
     * </ul>
     *
     * @return fallback parsing strategy
     */
    private FallbackParsingStrategy<MessageFlow> createFallbackStrategy() {
        return (file, content) -> {
            List<MessageFlow> flows = new ArrayList<>();

            // Check if file contains RabbitMQ patterns
            if (!content.contains("RabbitListener") && !content.contains("RabbitTemplate")) {
                return flows;
            }

            // Extract class name and package using shared utility
            String className = RegexPatterns.extractClassName(content, file);
            String packageName = RegexPatterns.extractPackageName(content);
            String fullyQualifiedName = RegexPatterns.buildFullyQualifiedName(packageName, className);

            // Extract @RabbitListener flows (consumers) using pre-compiled pattern
            java.util.regex.Matcher listenerMatcher = LISTENER_PATTERN.matcher(content);
            while (listenerMatcher.find()) {
                String queues = listenerMatcher.group(1);
                for (String queue : queues.split(",")) {
                    queue = queue.trim().replaceAll("\"", "");
                    if (!queue.isEmpty()) {
                        flows.add(new MessageFlow(null, fullyQualifiedName, queue,
                            DEFAULT_MESSAGE_TYPE, null, TECHNOLOGY));
                    }
                }
            }

            // Extract RabbitTemplate flows (producers) using pre-compiled pattern
            java.util.regex.Matcher templateMatcher = TEMPLATE_PATTERN.matcher(content);
            while (templateMatcher.find()) {
                String queue = templateMatcher.group(2); // Group 2 is the queue name
                flows.add(new MessageFlow(fullyQualifiedName, null, queue,
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
    private void parseRabbitMQFlows(Path javaFile, List<MessageFlow> messageFlows) throws IOException {
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
                extractRabbitListenerFlows(method, fullyQualifiedName, messageFlows);
                extractRabbitTemplateFlows(method, fullyQualifiedName, messageFlows);
            });
        });
    }

    private void extractRabbitListenerFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        Optional<AnnotationExpr> rabbitListener = method.getAnnotations().stream()
            .filter(ann -> RABBIT_LISTENER_ANNOTATION.equals(ann.getNameAsString()))
            .findFirst();

        if (rabbitListener.isEmpty()) {
            return;
        }

        List<String> queues = extractQueuesFromAnnotation(rabbitListener.get());
        String messageType = method.getParameters().isEmpty()
            ? DEFAULT_MESSAGE_TYPE
            : method.getParameters().get(0).getType().asString();

        for (String queue : queues) {
            MessageFlow flow = new MessageFlow(
                null,
                className,
                queue,
                messageType,
                null,
                TECHNOLOGY
            );

            messageFlows.add(flow);
            log.debug("Found RabbitMQ consumer: {} listening on queue: {}", className, queue);
        }
    }

    private void extractRabbitTemplateFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String methodName = call.getNameAsString();
            if (!RABBIT_TEMPLATE_METHOD.equals(methodName) && !RABBIT_TEMPLATE_SEND_METHOD.equals(methodName)) {
                return;
            }

            call.getScope().ifPresent(scope -> {
                String scopeStr = scope.toString();
                if (scopeStr.contains(RABBIT_TEMPLATE_IDENTIFIER) || scopeStr.contains(RABBIT_TEMPLATE_CLASS_IDENTIFIER)) {
                    if (call.getArguments().size() >= MIN_ARGUMENTS_FOR_QUEUE) {
                        String queue = call.getArguments().get(FIRST_ARGUMENT_INDEX).toString().replaceAll(QUOTE_REGEX, "");

                        MessageFlow flow = new MessageFlow(
                            className,
                            null,
                            queue,
                            DEFAULT_MESSAGE_TYPE,
                            null,
                            TECHNOLOGY
                        );

                        messageFlows.add(flow);
                        log.debug("Found RabbitMQ producer (RabbitTemplate): {} sending to queue: {}", className, queue);
                    }
                }
            });
        });
    }

    private List<String> extractQueuesFromAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            String value = single.getMemberValue().toString();
            return parseQueuesValue(value);
        }

        if (annotation instanceof NormalAnnotationExpr normal) {
            // First try "queues" parameter
            Optional<List<String>> queues = normal.getPairs().stream()
                .filter(pair -> QUEUES_PARAM_NAME.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> parseQueuesValue(pair.getValue().toString()));

            if (queues.isPresent() && !queues.get().isEmpty()) {
                return queues.get();
            }

            // Try "queuesToDeclare" parameter
            return normal.getPairs().stream()
                .filter(pair -> QUEUE_TO_DECLARE_PARAM.equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> parseQueuesValue(pair.getValue().toString()))
                .orElse(List.of());
        }

        return List.of();
    }

    private List<String> parseQueuesValue(String value) {
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
}
