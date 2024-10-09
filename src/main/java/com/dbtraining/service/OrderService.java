package com.dbtraining.service;

import com.dbtraining.model.Order;
import com.dbtraining.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    public OrderService(OrderRepository orderRepository, EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void insertSingleOrder(Order order) {
        orderRepository.save(order);
    }

    @Transactional
    public void placeOrder(Order order) {
        // This method acquires a pessimistic write lock on the user
        // It could lead to performance issues if many orders are being placed concurrently
        entityManager.lock(order.user(), LockModeType.PESSIMISTIC_WRITE);
        order.setOrderDate(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> generateMonthlySalesReport(LocalDateTime startDate, LocalDateTime endDate) {
        // This query might cause lock contention if run concurrently with order placement
        return orderRepository.findOrdersInDateRange(startDate, endDate);
    }
}
