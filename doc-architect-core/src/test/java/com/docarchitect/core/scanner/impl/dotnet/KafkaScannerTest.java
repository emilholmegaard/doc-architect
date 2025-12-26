package com.docarchitect.core.scanner.impl.dotnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.docarchitect.core.model.MessageFlow;
import com.docarchitect.core.scanner.ScanContext;
import com.docarchitect.core.scanner.ScanResult;

import  com.docarchitect.core.scanner.ScannerTestBase;

class KafkaScannerTest extends ScannerTestBase {

    private KafkaScanner scanner;

    @BeforeEach
    void setUpScanner() {
        scanner = new KafkaScanner();
    }

    @Test
    void shouldHaveCorrectMetadata() {
        assertThat(scanner.getId()).isEqualTo("kafka-messaging");
        assertThat(scanner.getDisplayName()).isEqualTo("Kafka Message Flow Scanner (.NET)");
        assertThat(scanner.getPriority()).isEqualTo(70);
        assertThat(scanner.getSupportedFilePatterns()).containsExactlyInAnyOrder("**/*.cs", "*.cs");
    }

    @Test
    void shouldDetectKafkaConsumerAttribute() throws IOException {
        String content = """
            namespace MyApp.Consumers;
            
            public class OrderConsumer
            {
                [KafkaConsumer("orders-topic")]
                public void ConsumeOrder(Order order)
                {
                    // process order
                }
            }
            """;

        createFile("OrderConsumer.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("unknown-publisher");
        assertThat(flow.subscriberComponentId()).isEqualTo("MyApp.Consumers.OrderConsumer");
        assertThat(flow.topic()).isEqualTo("orders-topic");
        assertThat(flow.broker()).isEqualTo("kafka");
    }

    @Test
    void shouldDetectTopicAttribute() throws IOException {
        String content = """
            namespace MyApp.Consumers;
            
            public class PaymentConsumer
            {
                [Topic("payments-topic")]
                public void HandlePayment(Payment payment)
                {
                    // process payment
                }
            }
            """;

        createFile("PaymentConsumer.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.subscriberComponentId()).isEqualTo("MyApp.Consumers.PaymentConsumer");
        assertThat(flow.topic()).isEqualTo("payments-topic");
    }

    @Test
    void shouldDetectIConsumerInterface() throws IOException {
        String content = """
            using Confluent.Kafka;
            
            namespace MyApp.Services;
            
            public class MessageConsumerService
            {
                private IConsumer<string, OrderEvent> _consumer;
                
                public void Initialize()
                {
                    _consumer = new ConsumerBuilder<string, OrderEvent>().Build();
                }
            }
            """;

        createFile("MessageConsumerService.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.subscriberComponentId()).isEqualTo("MyApp.Services.MessageConsumerService");
        assertThat(flow.messageType()).isEqualTo("OrderEvent");
    }

    @Test
    void shouldDetectIProducerInterface() throws IOException {
        String content = """
            using Confluent.Kafka;
            
            namespace MyApp.Services;
            
            public class MessageProducerService
            {
                private IProducer<string, PaymentEvent> _producer;
                
                public void Initialize()
                {
                    _producer = new ProducerBuilder<string, PaymentEvent>().Build();
                }
            }
            """;

        createFile("MessageProducerService.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        MessageFlow flow = result.messageFlows().get(0);
        assertThat(flow.publisherComponentId()).isEqualTo("MyApp.Services.MessageProducerService");
        assertThat(flow.subscriberComponentId()).isEqualTo("unknown-subscriber");
        assertThat(flow.messageType()).isEqualTo("PaymentEvent");
    }

    @Test
    void shouldDetectProduceAsyncMethod() throws IOException {
        String content = """
            namespace MyApp.Producers;
            
            public class OrderProducer
            {
                private IProducer<string, Order> _producer;
                
                public async Task PublishOrder(Order order)
                {
                    await _producer.ProduceAsync("orders-topic", new Message<string, Order>
                    {
                        Key = order.Id,
                        Value = order
                    });
                }
            }
            """;

        createFile("OrderProducer.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2); // IProducer + ProduceAsync
        
        MessageFlow produceAsyncFlow = result.messageFlows().stream()
            .filter(f -> "orders-topic".equals(f.topic()))
            .findFirst()
            .orElseThrow();
            
        assertThat(produceAsyncFlow.publisherComponentId()).isEqualTo("MyApp.Producers.OrderProducer");
        assertThat(produceAsyncFlow.topic()).isEqualTo("orders-topic");
    }

    @Test
    void shouldDetectConsumeMethod() throws IOException {
        String content = """
            namespace MyApp.Consumers;
            
            public class EventConsumer
            {
                private IConsumer<string, Event> _consumer;
                
                public void StartConsuming()
                {
                    _consumer.Consume("events-topic");
                }
            }
            """;

        createFile("EventConsumer.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(2); // IConsumer + Consume
        
        MessageFlow consumeFlow = result.messageFlows().stream()
            .filter(f -> "events-topic".equals(f.topic()))
            .findFirst()
            .orElseThrow();
            
        assertThat(consumeFlow.subscriberComponentId()).isEqualTo("MyApp.Consumers.EventConsumer");
        assertThat(consumeFlow.topic()).isEqualTo("events-topic");
    }

    @Test
    void shouldHandleMultiplePatterns() throws IOException {
        String content = """
            using Confluent.Kafka;
            
            namespace MyApp.Services;
            
            public class KafkaService
            {
                private IConsumer<string, Order> _consumer;
                private IProducer<string, Notification> _producer;
                
                [KafkaConsumer("orders-in")]
                public void ProcessOrder(Order order)
                {
                    // process
                    _producer.ProduceAsync("notifications-out", notification);
                }
            }
            """;

        createFile("KafkaService.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(4); // IConsumer + IProducer + attribute + ProduceAsync
        
        List<MessageFlow> consumers = result.messageFlows().stream()
            .filter(f -> !"unknown-subscriber".equals(f.subscriberComponentId()))
            .toList();
        assertThat(consumers).hasSizeGreaterThanOrEqualTo(2);
        
        List<MessageFlow> producers = result.messageFlows().stream()
            .filter(f -> !"unknown-publisher".equals(f.publisherComponentId()))
            .toList();
        assertThat(producers).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldHandlePartialClasses() throws IOException {
        String content = """
            namespace MyApp;
            
            public partial class PartialConsumer
            {
                [KafkaConsumer("partial-topic")]
                public void Consume(Message msg) { }
            }
            """;

        createFile("PartialConsumer.cs", content);
        ScanResult result = scanner.scan( context        );

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).subscriberComponentId()).isEqualTo("MyApp.PartialConsumer");
    }

    @Test
    void shouldHandleClassWithoutNamespace() throws IOException {
        String content = """
            public class GlobalConsumer
            {
                [Topic("global-topic")]
                public void Process() { }
            }
            """;

        createFile("GlobalConsumer.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSize(1);
        assertThat(result.messageFlows().get(0).subscriberComponentId()).isEqualTo("GlobalConsumer");
    }

    @Test
    void shouldReturnEmptyResultWhenNoFilesFound() {
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldHandleInvalidCsFile() throws IOException {
        createFile("Invalid.cs", "this is not valid C# code {{{");

        ScanResult result = scanner.scan(context);

        // Should not throw, just log debug message
        assertThat(result.messageFlows()).isNotNull();
    }

    @Test
    void shouldSkipFilesWithoutKafkaImports() throws IOException {
        // Create a regular C# file without Kafka imports
        String content = """
            namespace MyApp.Services;

            public class RegularService
            {
                public void DoSomething()
                {
                    Console.WriteLine("No Kafka here");
                }
            }
            """;

        createFile("RegularService.cs", content);
        ScanResult result = scanner.scan(context);

        // Should not find any message flows since file has no Kafka patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldSkipTestFilesWithoutKafkaPatterns() throws IOException {
        // Create a test file without Kafka imports
        String content = """
            namespace MyApp.Tests;

            using Xunit;

            public class UpdateTest
            {
                [Fact]
                public void ShouldUpdateUser()
                {
                    // Regular unit test, no Kafka
                }
            }
            """;

        createFile("MyApp.Tests/UpdateTest.cs", content);
        ScanResult result = scanner.scan(context);

        // Should not find any message flows since test file has no Kafka patterns
        assertThat(result.messageFlows()).isEmpty();
    }

    @Test
    void shouldScanTestFilesWithKafkaPatterns() throws IOException {
        // Create a test file WITH Kafka imports
        String content = """
            using Confluent.Kafka;
            using Xunit;

            namespace MyApp.Tests;

            public class KafkaIntegrationTest
            {
                private IConsumer<string, TestEvent> _consumer;

                [Fact]
                public void ShouldConsumeMessage()
                {
                    _consumer.Consume("test-topic");
                }
            }
            """;

        createFile("MyApp.Tests/KafkaIntegrationTest.cs", content);
        ScanResult result = scanner.scan(context);

        // Should find message flows in test files that contain Kafka patterns
        assertThat(result.messageFlows()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldDetectConfluentKafkaImport() throws IOException {
        // Test that Confluent.Kafka import is sufficient to trigger scanning
        String content = """
            using Confluent.Kafka;

            namespace MyApp.Producers;

            public class ProducerService
            {
                private IProducer<string, string> _producer;

                public void Send(string message)
                {
                    _producer.ProduceAsync("my-topic", new Message<string, string> { Value = message });
                }
            }
            """;

        createFile("ProducerService.cs", content);
        ScanResult result = scanner.scan(context);

        assertThat(result.messageFlows()).hasSizeGreaterThanOrEqualTo(1);
    }
}
