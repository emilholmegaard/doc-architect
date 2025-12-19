package com.example.orderservice.kafka;

import com.example.orderservice.model.Order;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for order events.
 */
@Component
public class OrderEventProducer {

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_UPDATED_TOPIC = "order.updated";

    private final KafkaTemplate<String, Order> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Order> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrderCreated(Order order) {
        kafkaTemplate.send(ORDER_CREATED_TOPIC, order.getId().toString(), order);
    }

    public void sendOrderUpdated(Order order) {
        kafkaTemplate.send(ORDER_UPDATED_TOPIC, order.getId().toString(), order);
    }
}
