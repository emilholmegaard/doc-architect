package com.docarchitect.core.scanner.impl.java;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
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
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, FILE_PATTERN);
    }

    /**
     * Pre-filter files to only scan those containing RabbitMQ-related imports or annotations.
     *
     * <p>This avoids attempting to parse files that don't contain RabbitMQ code,
     * reducing unnecessary WARN logs and improving performance.
     *
     * @param file path to Java source file
     * @return true if file contains RabbitMQ patterns, false otherwise
     */
    @Override
    protected boolean shouldScanFile(Path file) {
        // Skip test files unless they contain RabbitMQ patterns
        String filePath = file.toString();
        boolean isTestFile = filePath.contains("/test/") || filePath.contains("\\test\\");

        try {
            String content = readFileContent(file);

            // Check for RabbitMQ imports and annotations
            boolean hasRabbitMQPatterns = content.contains("org.springframework.amqp") ||
                                         content.contains("com.rabbitmq") ||
                                         content.contains("@RabbitListener") ||
                                         content.contains("@RabbitHandler") ||
                                         content.contains("@EnableRabbit") ||
                                         content.contains("RabbitTemplate");

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
        List<Path> javaFiles = context.findFiles(FILE_PATTERN).toList();

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path javaFile : javaFiles) {
            try {
                parseRabbitMQFlows(javaFile, messageFlows);
            } catch (Exception e) {
                // Files without RabbitMQ patterns are already filtered by shouldScanFile()
                // Any remaining parse failures are logged at DEBUG level
                log.debug("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.info("Found {} RabbitMQ message flows", messageFlows.size());

        return buildSuccessResult(
            List.of(),
            List.of(),
            List.of(),
            messageFlows,
            List.of(),
            List.of(),
            List.of()
        );
    }

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
