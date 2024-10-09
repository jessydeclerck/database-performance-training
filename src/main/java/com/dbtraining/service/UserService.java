package com.dbtraining.service;

import com.dbtraining.model.Order;
import com.dbtraining.model.User;
import com.dbtraining.repository.OrderRepository;
import com.dbtraining.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<Order> getUserOrders(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        List<Order> orders = orderRepository.findByUserIdOrderByOrderDateDesc(userId);
        // N+1 problem: fetching order items for each order
        orders.forEach(order -> order.items().size());
        return orders;
    }
}

