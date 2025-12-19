package com.example.orderservice.service;

import com.example.orderservice.model.Order;
import java.util.List;
import java.util.Optional;

/**
 * Service interface for order operations.
 */
public interface OrderService {
    List<Order> findAll();
    Optional<Order> findById(Long id);
    Order create(Order order);
    Optional<Order> update(Long id, Order order);
    void delete(Long id);
    List<Order> findByCustomerId(String customerId);
}
