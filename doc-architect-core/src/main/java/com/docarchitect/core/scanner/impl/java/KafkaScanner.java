package com.docarchitect.core.scanner.impl.java;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.base.AbstractJavaParserScanner;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

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

    @Override
    public String getId() {
        return "kafka-messaging";
    }

    @Override
    public String getDisplayName() {
        return "Kafka Message Flow Scanner";
    }

    @Override
    public Set<String> getSupportedLanguages() {
        return Set.of("java");
    }

    @Override
    public Set<String> getSupportedFilePatterns() {
        return Set.of("**/*.java");
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean appliesTo(ScanContext context) {
        return hasAnyFiles(context, "**/*.java");
    }

    @Override
    public ScanResult scan(ScanContext context) {
        log.info("Scanning Kafka message flows in: {}", context.rootPath());

        List<MessageFlow> messageFlows = new ArrayList<>();
        List<Path> javaFiles = context.findFiles("**/*.java").toList();

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
            .filter(ann -> "KafkaListener".equals(ann.getNameAsString()))
            .findFirst();

        if (kafkaListener.isEmpty()) {
            return;
        }

        List<String> topics = extractTopicsFromAnnotation(kafkaListener.get());
        String messageType = method.getParameters().isEmpty()
            ? "Object"
            : method.getParameters().get(0).getType().asString();

        for (String topic : topics) {
            MessageFlow flow = new MessageFlow(
                null, // No publisher (this is a consumer)
                className,
                topic,
                messageType,
                null,
                "kafka"
            );

            messageFlows.add(flow);
            log.debug("Found Kafka consumer: {} listening on topic: {}", className, topic);
        }
    }

    private void extractSendToFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        Optional<AnnotationExpr> sendTo = method.getAnnotations().stream()
            .filter(ann -> "SendTo".equals(ann.getNameAsString()))
            .findFirst();

        if (sendTo.isEmpty()) {
            return;
        }

        String topic = extractTopicFromSendTo(sendTo.get());
        String messageType = method.getType().asString();

        MessageFlow flow = new MessageFlow(
            className,
            null, // No subscriber (this is a producer)
            topic,
            messageType,
            null,
            "kafka"
        );

        messageFlows.add(flow);
        log.debug("Found Kafka producer (@SendTo): {} sending to topic: {}", className, topic);
    }

    private void extractKafkaTemplateFlows(MethodDeclaration method, String className, List<MessageFlow> messageFlows) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"send".equals(call.getNameAsString())) {
                return;
            }

            call.getScope().ifPresent(scope -> {
                String scopeStr = scope.toString();
                if (scopeStr.contains("kafkaTemplate") || scopeStr.contains("KafkaTemplate")) {
                    if (call.getArguments().size() >= 1) {
                        String topic = call.getArguments().get(0).toString().replaceAll("\"", "");

                        MessageFlow flow = new MessageFlow(
                            className,
                            null,
                            topic,
                            "Object",
                            null,
                            "kafka"
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
                .filter(pair -> "topics".equals(pair.getNameAsString()))
                .findFirst()
                .map(pair -> parseTopicsValue(pair.getValue().toString()))
                .orElse(List.of());
        }

        return List.of();
    }

    private List<String> parseTopicsValue(String value) {
        value = value.trim();
        if (value.startsWith("{") && value.endsWith("}")) {
            // Array: {"topic1", "topic2"}
            return Arrays.stream(value.substring(1, value.length() - 1).split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("\"", ""))
                .filter(s -> !s.isEmpty())
                .toList();
        } else {
            // Single topic: "topic1"
            return List.of(value.replaceAll("\"", ""));
        }
    }

    private String extractTopicFromSendTo(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return single.getMemberValue().toString().replaceAll("\"", "");
        }
        return "unknown-topic";
    }
}
