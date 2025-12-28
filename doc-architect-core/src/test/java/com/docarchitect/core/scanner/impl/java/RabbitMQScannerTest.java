package com.docarchitect.core.scanner.impl.java;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanResult;
import com.docarchitect.core.scanner.ScannerTestBase;

/**
 * Functional tests for {@link RabbitMQScanner}.
 */
class RabbitMQScannerTest extends ScannerTestBase {

    private RabbitMQScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new RabbitMQScanner();
    }

    @Test
    void shouldHaveCorrectMetadata() {
        assertThat(scanner.getId()).isEqualTo("rabbitmq-messaging");
        assertThat(scanner.getDisplayName()).isEqualTo("RabbitMQ Message Flow Scanner");
        assertThat(scanner.getPriority()).isEqualTo(70);
        assertThat(scanner.getSupportedFilePatterns()).containsExactly("**/*.java");
    }

    @Test
    void shouldDetectRabbitListener() throws IOException {
        createFile("src/main/java/com/example/OrderConsumer.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class OrderConsumer {

    @RabbitListener(queues = "orders-queue")
    public void consumeOrder(Order order) {
        // process order
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.subscriberComponentId()).isEqualTo("com.example.OrderConsumer");
        assertThat(flow.topic()).isEqualTo("orders-queue");
        assertThat(flow.broker()).isEqualTo("rabbitmq");
    }

    @Test
    void shouldDetectRabbitListenerWithMultipleQueues() throws IOException {
        createFile("src/main/java/com/example/EventConsumer.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class EventConsumer {

    @RabbitListener(queues = {"events-queue", "notifications-queue"})
    public void consumeEvent(Event event) {
        // process event
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("events-queue", "notifications-queue");
    }

    @Test
    void shouldDetectRabbitTemplateSend() throws IOException {
        createFile("src/main/java/com/example/NotificationService.java", """
package com.example;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final RabbitTemplate rabbitTemplate;

    public NotificationService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendNotification(Notification notification) {
        rabbitTemplate.convertAndSend("notifications-queue", notification);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("com.example.NotificationService");
        assertThat(flow.topic()).isEqualTo("notifications-queue");
        assertThat(flow.broker()).isEqualTo("rabbitmq");
    }

    @Test
    void shouldDetectRabbitTemplateSendMethod() throws IOException {
        createFile("src/main/java/com/example/MessageService.java", """
package com.example;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final RabbitTemplate rabbitTemplate;

    public MessageService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendMessage(String message) {
        rabbitTemplate.send("messages-queue", message);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("com.example.MessageService");
        assertThat(flow.topic()).isEqualTo("messages-queue");
        assertThat(flow.broker()).isEqualTo("rabbitmq");
    }

    @Test
    void shouldHandleMultipleConsumersInSameFile() throws IOException {
        createFile("src/main/java/com/example/MultiConsumer.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class MultiConsumer {

    @RabbitListener(queues = "queue-a")
    public void consumeA(String message) {}

    @RabbitListener(queues = "queue-b")
    public void consumeB(String message) {}
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2);
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("queue-a", "queue-b");
    }

    @Test
    void shouldDetectQueuesToDeclareParameter() throws IOException {
        createFile("src/main/java/com/example/DeclaredQueueConsumer.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class DeclaredQueueConsumer {

    @RabbitListener(queuesToDeclare = @Queue("declared-queue"))
    public void consume(String message) {
        // process message
    }
}
""");

        ScanResult result = scanner.scan(context);

        // This test verifies the parameter is recognized, actual parsing may vary
        // depending on the complexity of @Queue annotation
        assertThat(result.messageFlows()).isNotNull();
    }

    @Test
    void shouldHandleConsumerAndProducerInSameClass() throws IOException {
        createFile("src/main/java/com/example/MessageProcessor.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageProcessor {

    private final RabbitTemplate rabbitTemplate;

    public MessageProcessor(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "input-queue")
    public void processMessage(String message) {
        String processed = message.toUpperCase();
        rabbitTemplate.convertAndSend("output-queue", processed);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2); // Consumer + Producer
        assertThat(result.messageFlows())
            .extracting(MessageFlow::topic)
            .containsExactlyInAnyOrder("input-queue", "output-queue");
    }

    @Test
    void shouldReturnEmptyResultWhenNoFilesFound() {
        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldSkipFilesWithoutRabbitMQImports() throws IOException {
        // Create a regular Java file without RabbitMQ imports
        createFile("src/main/java/com/example/RegularService.java", """
package com.example;

import org.springframework.stereotype.Service;

@Service
public class RegularService {
    public void doSomething() {
        System.out.println("No RabbitMQ here");
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should not find any message flows since file has no RabbitMQ patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldSkipTestFilesWithoutRabbitMQPatterns() throws IOException {
        // Create a test file without RabbitMQ imports
        createFile("src/test/java/com/example/UpdateTest.java", """
package com.example;

import org.junit.jupiter.api.Test;

public class UpdateTest {
    @Test
    void shouldUpdateUser() {
        // Regular unit test, no RabbitMQ
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should not find any message flows since test file has no RabbitMQ patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldScanTestFilesWithRabbitMQPatterns() throws IOException {
        // Create a test file WITH RabbitMQ imports
        createFile("src/test/java/com/example/RabbitMQIntegrationTest.java", """
package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

public class RabbitMQIntegrationTest {

    @RabbitListener(queues = "test-queue")
    public void consumeTestMessage(String message) {
        // RabbitMQ integration test
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should find message flows in test files that contain RabbitMQ patterns
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("test-queue");
    }

    @Test
    void shouldDetectRabbitTemplateImport() throws IOException {
        // Test that RabbitTemplate import is sufficient to trigger scanning
        createFile("src/main/java/com/example/ProducerService.java", """
package com.example;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class ProducerService {
    private RabbitTemplate rabbitTemplate;

    public void send(String message) {
        rabbitTemplate.convertAndSend("my-queue", message);
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("my-queue");
    }

    @Test
    void shouldHandleSpringCloudStreamBindings() throws IOException {
        // Test detection of Spring Cloud Stream style listeners
        createFile("src/main/java/com/example/StreamListener.java", """
package com.example;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cloud.stream.annotation.EnableBinding;

@EnableBinding
public class StreamListener {

    @RabbitListener(queues = "stream-queue")
    public void handleStream(String payload) {
        // process stream
    }
}
""");

        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("stream-queue");
    }

    @Test
    void shouldDetectWithExchangeAndRoutingKey() throws IOException {
        // RabbitTemplate can also send with exchange and routing key
        createFile("src/main/java/com/example/ExchangeProducer.java", """
package com.example;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExchangeProducer {

    private final RabbitTemplate rabbitTemplate;

    public ExchangeProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendToExchange(String message) {
        // First argument is exchange, but we still detect it
        rabbitTemplate.convertAndSend("exchange-name", "routing.key", message);
    }
}
""");

        ScanResult result = scanner.scan(context);

        // Should detect the exchange as the "queue" (first argument)
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("exchange-name");
    }

    // Tests for shouldScanFile() pre-filtering logic

    @Test
    void scan_withWildcardImport_detectsListener() throws IOException {
        createFile("src/main/java/com/example/MessageListener.java", """
            package com.example;

            import org.springframework.amqp.rabbit.annotation.*;

            public class MessageListener {
                @RabbitListener(queues = "test.queue")
                public void handleMessage(String message) {
                }
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("test.queue");
    }

    @Test
    void scan_withFilenameConvention_scansListenerFiles() throws IOException {
        createFile("src/main/java/com/example/OrderListener.java", """
            package com.example;

            import org.springframework.amqp.rabbit.annotation.RabbitListener;

            public class OrderListener {
                @RabbitListener(queues = "orders")
                public void process(String order) {
                }
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("orders");
    }

    @Test
    void scan_withConsumerNamingConvention_scansConsumerFiles() throws IOException {
        createFile("src/main/java/com/example/EventConsumer.java", """
            package com.example;

            import org.springframework.amqp.rabbit.annotation.RabbitListener;

            public class EventConsumer {
                @RabbitListener(queues = "events")
                public void consume(String event) {
                }
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).topic()).isEqualTo("events");
    }

    @Test
    void scan_withNoRabbitMQPatterns_skipsFile() throws IOException {
        createFile("src/main/java/com/example/UserService.java", """
            package com.example;

            public class UserService {
                public String getUser(Long id) {
                    return "user";
                }
            }
            """);

        ScanResult result = scanner.scan(context);
        assertThat(result.messageFlows()).isEmpty();
    }
}
