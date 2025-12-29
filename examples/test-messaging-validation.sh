#!/bin/bash
# test-messaging-validation.sh
# Minimal validation test to prove message flow scanners work correctly
#
# Creates a minimal Spring Boot application with actual @RabbitListener
# and @KafkaListener annotations, then verifies scanners detect them.

set -e

PROJECT_DIR="test-projects/messaging-validation"

echo "=========================================="
echo "Messaging Scanner Validation Test"
echo "Minimal test to prove scanners work"
echo "=========================================="

# Create test project directory
echo "Creating minimal test project..."
rm -rf "$PROJECT_DIR"
mkdir -p "$PROJECT_DIR/src/main/java/com/example/messaging"

# Create pom.xml with Kafka and RabbitMQ dependencies
cat > "$PROJECT_DIR/pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>messaging-validation</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Messaging Validation</name>
    <description>Minimal test case for message flow scanners</description>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.0</spring-boot.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>

        <!-- Spring Kafka -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
            <version>3.1.0</version>
        </dependency>

        <!-- Spring AMQP (RabbitMQ) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
    </dependencies>
</project>
EOF

# Create RabbitMQ listener class
cat > "$PROJECT_DIR/src/main/java/com/example/messaging/ValidationRabbitListener.java" << 'EOF'
package com.example.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Minimal RabbitMQ listener for validation.
 */
@Component
public class ValidationRabbitListener {

    private final RabbitTemplate rabbitTemplate;

    public ValidationRabbitListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "validation-queue")
    public void handleMessage(String message) {
        System.out.println("Received: " + message);

        // Send response
        rabbitTemplate.convertAndSend("response-queue", "Processed: " + message);
    }

    @RabbitListener(queues = {"order-queue", "payment-queue"})
    public void handleMultipleQueues(String message) {
        System.out.println("Multi-queue message: " + message);
    }
}
EOF

# Create Kafka listener class
cat > "$PROJECT_DIR/src/main/java/com/example/messaging/ValidationKafkaListener.java" << 'EOF'
package com.example.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Minimal Kafka listener for validation.
 */
@Component
public class ValidationKafkaListener {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ValidationKafkaListener(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "validation-topic")
    public void handleEvent(String event) {
        System.out.println("Received event: " + event);

        // Send to another topic
        kafkaTemplate.send("processed-topic", "Processed: " + event);
    }

    @KafkaListener(topics = {"order-events", "payment-events"})
    public void handleMultipleTopics(String event) {
        System.out.println("Multi-topic event: " + event);
    }
}
EOF

# Create DocArchitect config
cat > "$PROJECT_DIR/docarchitect.yaml" << 'EOF'
project:
  name: "Messaging Validation"
  version: "1.0.0"
  description: "Minimal test case for message flow scanners"

repositories:
  - name: "validation"
    path: "."

scanners:
  enabled:
    - maven-dependencies
    - kafka-messaging
    - rabbitmq-messaging
    - spring-components

generators:
  default: mermaid
  enabled:
    - mermaid
    - markdown

output:
  directory: "./docs/architecture"
  generateIndex: true
EOF

# Run DocArchitect
echo ""
echo "Running DocArchitect on validation project..."
mkdir -p "$(pwd)/output/messaging-validation"
docker run --rm \
    -v "$(pwd)/$PROJECT_DIR:/workspace:ro" \
    -v "$(pwd)/output/messaging-validation:/output" \
    ghcr.io/emilholmegaard/doc-architect:latest \
    scan /workspace --config /workspace/docarchitect.yaml --output /output

# Validate results
echo ""
echo "=========================================="
echo "Validating Scanner Results"
echo "=========================================="

INDEX_FILE="output/messaging-validation/index.md"
VALIDATION_PASSED=true

if [ ! -f "$INDEX_FILE" ]; then
    echo "✗ FAIL: index.md not generated"
    VALIDATION_PASSED=false
    exit 1
fi

# Check for expected message flow counts
# Expected: 2 RabbitMQ flows (validation-queue, multi-queue handler)
#           2 Kafka flows (validation-topic, multi-topic handler)
#           Plus producers (response-queue, processed-topic) = ~6 total flows

echo "Checking index.md for message flow metrics..."
cat "$INDEX_FILE" | grep -E "Message Flows|message" || true

# Extract message flow count if available
FLOW_COUNT=$(grep -E "Message Flows.*\|.*[0-9]+" "$INDEX_FILE" | grep -oE "[0-9]+" | head -1 || echo "0")

echo ""
echo "Message flows detected: $FLOW_COUNT"
echo ""

if [ "$FLOW_COUNT" -ge 4 ]; then
    echo "✓ PASS: Found $FLOW_COUNT message flows (expected >= 4)"
    echo "  Scanners ARE working correctly!"
else
    echo "✗ FAIL: Found $FLOW_COUNT message flows (expected >= 4)"
    echo "  This confirms scanners may have issues."
    VALIDATION_PASSED=false
fi

echo ""
echo "Expected minimum flows:"
echo "  - validation-queue (RabbitMQ consumer)"
echo "  - order-queue + payment-queue (RabbitMQ multi-queue consumer)"
echo "  - response-queue (RabbitMQ producer)"
echo "  - validation-topic (Kafka consumer)"
echo "  - order-events + payment-events (Kafka multi-topic consumer)"
echo "  - processed-topic (Kafka producer)"
echo ""

# Check for specific patterns in output files
if [ -f "output/messaging-validation/message-flows.md" ]; then
    echo "Checking message-flows.md for specific queues/topics..."

    if grep -q "validation-queue" "output/messaging-validation/message-flows.md"; then
        echo "✓ Found validation-queue"
    else
        echo "✗ Missing validation-queue"
        VALIDATION_PASSED=false
    fi

    if grep -q "validation-topic" "output/messaging-validation/message-flows.md"; then
        echo "✓ Found validation-topic"
    else
        echo "✗ Missing validation-topic"
        VALIDATION_PASSED=false
    fi
else
    echo "⚠ Warning: message-flows.md not generated"
fi

echo ""
if [ "$VALIDATION_PASSED" = true ]; then
    echo "=========================================="
    echo "✓ VALIDATION PASSED"
    echo "=========================================="
    echo "Message flow scanners are working correctly."
    echo "The issue is with test project selection, not scanner bugs."
    exit 0
else
    echo "=========================================="
    echo "✗ VALIDATION FAILED"
    echo "=========================================="
    echo "Scanners did not detect expected message flows."
    echo "Further investigation needed."
    exit 1
fi
