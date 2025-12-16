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

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Kafka message flows in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<Path> javaFiles = context.findFiles(FILE_PATTERN).toList();

        if (javaFiles.isEmpty()) {
            return emptyResult();
        }

        for (Path javaFile : javaFiles) {
            try {
                parseKafkaFlows(javaFile, messageFlows);
            } catch (Exception e) {
                log.warn("Failed to parse Java file: {} - {}", javaFile, e.getMessage());
            }
        }

        log.info("Found {} Kafka message flows", messageFlows.size());

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
