package com.example.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderEventListener {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderEventListener(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "order-events", groupId = "order-service")
    public void handleOrderEvent(String message) {
        System.out.println("Received: " + message);
    }

    @KafkaListener(topics = {"payment-events", "shipping-events"}, groupId = "fulfillment-service")
    public void handleFulfillmentEvents(String message) {
        System.out.println("Fulfillment: " + message);
    }

    public void sendOrderCreated(String orderId) {
        kafkaTemplate.send("order-created", orderId);
    }

    public void sendWithResult(String topic, String key, String message) {
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, message);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Sent successfully");
            }
        });
    }
}
