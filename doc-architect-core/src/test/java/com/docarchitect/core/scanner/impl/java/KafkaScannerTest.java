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

    @Test
    void shouldSkipFilesWithoutKafkaImports() throws IOException {
        // Create a regular Java file without Kafka imports
        createFile("src/main/java/com/example/RegularService.java", """
package com.example;

import org.springframework.stereotype.Service;

@Service
public class RegularService {
    public void doSomething() {
        System.out.println("No Kafka here");
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should not find any message flows since file has no Kafka patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldSkipTestFilesWithoutKafkaPatterns() throws IOException {
        // Create a test file without Kafka imports
        createFile("src/test/java/com/example/UpdateTest.java", """
package com.example;

import org.junit.jupiter.api.Test;

public class UpdateTest {
    @Test
    void shouldUpdateUser() {
        // Regular unit test, no Kafka
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should not find any message flows since test file has no Kafka patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldScanTestFilesWithKafkaPatterns() throws IOException {
        // Create a test file WITH Kafka imports
        createFile("src/test/java/com/example/KafkaIntegrationTest.java", """
package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

public class KafkaIntegrationTest {

    @KafkaListener(topics = "test-topic")
    public void consumeTestMessage(String message) {
        // Kafka integration test
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should find message flows in test files that contain Kafka patterns
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("test-topic");
    }

    @Test
    void shouldDetectKafkaTemplateImport() throws IOException {
        // Test that KafkaTemplate import is sufficient to trigger scanning
        createFile("src/main/java/com/example/ProducerService.java", """
package com.example;

import org.springframework.kafka.core.KafkaTemplate;

public class ProducerService {
    private KafkaTemplate<String, String> kafkaTemplate;

    public void send(String message) {
        kafkaTemplate.send("my-topic", message);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("my-topic");
    }
}
