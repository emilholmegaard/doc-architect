package com.docarchitect.core.model;

import java.util.Objects;

/**
 * Represents a message flow between components (Kafka topics, RabbitMQ queues, etc.).
 *
 * @param publisherComponentId component that publishes messages
 * @param subscriberComponentId component that consumes messages
 * @param topic topic, queue, or exchange name
 * @param messageType message type or event name
 * @param schema message schema or payload structure
 * @param broker message broker type (kafka, rabbitmq, etc.)
 */
public record MessageFlow(
    String publisherComponentId,
    String subscriberComponentId,
    String topic,
    String messageType,
    String schema,
    String broker
) {
    /**
     * Compact constructor with validation.
     */
    public MessageFlow {
        Objects.requireNonNull(topic, "topic must not be null");
        if (publisherComponentId == null && subscriberComponentId == null) {
            throw new IllegalArgumentException("Either publisherComponentId or subscriberComponentId must be non-null");
        }
    }
}
