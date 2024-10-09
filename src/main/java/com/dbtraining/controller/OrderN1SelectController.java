package com.dbtraining.controller;

import com.dbtraining.model.Order;
import com.dbtraining.model.OrderItem;
import com.dbtraining.repository.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderN1SelectController {

    private final OrderRepository orderRepository;

    public OrderN1SelectController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    record OrderSummaryDTO(Long id, LocalDateTime orderDate, int numberOfItems, double totalAmount) {}

    @GetMapping("/user/{email}")
    public ResponseEntity<List<OrderSummaryDTO>> getUserOrders(@PathVariable("email") String email) {
        List<Order> orders = orderRepository.findByUserEmail(email);

        List<OrderSummaryDTO> summaries = orders.stream()
                .map(order -> new OrderSummaryDTO(
                        order.id(),
                        order.orderDate(),
                        order.items().size(), // This lazy loading triggers additional queries
                        calculateOrderTotal(order.items())))
                .toList();

        return ResponseEntity.ok(summaries);
    }

    private double calculateOrderTotal(List<OrderItem> items) {
        return items.stream()  // This might trigger additional queries
                .mapToDouble(item -> item.product().price().multiply(BigDecimal.valueOf(item.quantity())).doubleValue())
                .sum();
    }


}
