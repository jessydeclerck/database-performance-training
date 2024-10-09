package com.dbtraining.repository;

import com.dbtraining.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByOrderDateDesc(Long userId);

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate")
    List<Order> findOrdersInDateRange(LocalDateTime startDate, LocalDateTime endDate);

//    @EntityGraph(value = "Order.withItemsAndProducts")
    List<Order> findByUserEmail(String email);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.items i " +
            "LEFT JOIN FETCH i.product " +
            "LEFT JOIN FETCH o.user " +
            "WHERE o.user.email = :email")
    List<Order> findByUserEmailWithItems(@Param("email") String email);

}
