package com.docarchitect.core.scanner.impl.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;

/**
 * Functional tests for {@link KafkaScanner}.
 */
class KafkaScannerTest extends ScannerTestBase {

    private KafkaScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new KafkaScanner();
    }

    @Test
    void shouldHaveCorrectMetadata() {
        assertThat(scanner.getId()).isEqualTo("kafka-messaging");
        assertThat(scanner.getDisplayName()).isEqualTo("Kafka Message Flow Scanner");
        assertThat(scanner.getPriority()).isEqualTo(70);
        assertThat(scanner.getSupportedFilePatterns()).containsExactly("**/*.java");
    }

    @Test
    void shouldDetectKafkaListener() throws IOException {
        createFile("src/main/java/com/example/OrderConsumer.java", """
package com.example;

import org.springframework.kafka.annotation.KafkaListener;

public class OrderConsumer {

    @KafkaListener(topics = "orders-topic")
    public void consumeOrder(Order order) {
        // process order
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.subscriberComponentId()).isEqualTo("com.example.OrderConsumer");
        assertThat(flow.topic()).isEqualTo("orders-topic");
        assertThat(flow.broker()).isEqualTo("kafka");
    }

    @Test
    void shouldDetectKafkaListenerWithMultipleTopics() throws IOException {
        createFile("src/main/java/com/example/EventConsumer.java", """
package com.example;

import org.springframework.kafka.annotation.KafkaListener;

public class EventConsumer {

    @KafkaListener(topics = {"events-topic", "notifications-topic"})
    public void consumeEvent(Event event) {
        // process event
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("events-topic", "notifications-topic");
    }

    @Test
    void shouldDetectSendToAnnotation() throws IOException {
        createFile("src/main/java/com/example/MessageProcessor.java", """
package com.example;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;

public class MessageProcessor {

    @KafkaListener(topics = "input-topic")
    @SendTo("output-topic")
    public ProcessedMessage process(Message msg) {
        return new ProcessedMessage();
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2); // Consumer + Producer
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("input-topic", "output-topic");
    }

    @Test
    void shouldDetectKafkaTemplateSend() throws IOException {
        createFile("src/main/java/com/example/NotificationService.java", """
package com.example;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final KafkaTemplate<String, Notification> kafkaTemplate;

    public NotificationService(KafkaTemplate<String, Notification> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendNotification(Notification notification) {
        kafkaTemplate.send("notifications-topic", notification);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("com.example.NotificationService");
        assertThat(flow.topic()).isEqualTo("notifications-topic");
        assertThat(flow.broker()).isEqualTo("kafka");
    }

    @Test
    void shouldHandleMultipleConsumersInSameFile() throws IOException {
        createFile("src/main/java/com/example/MultiConsumer.java", """
package com.example;

import org.springframework.kafka.annotation.KafkaListener;

public class MultiConsumer {

    @KafkaListener(topics = "topic-a")
    public void consumeA(String message) {}

    @KafkaListener(topics = "topic-b")
    public void consumeB(String message) {}
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("topic-a", "topic-b");
    }

    @Test
    void shouldReturnEmptyResultWhenNoFilesFound() {
        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).isEmpty();
    }
}
