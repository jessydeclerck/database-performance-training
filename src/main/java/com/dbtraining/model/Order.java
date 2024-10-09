package com.dbtraining.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@NamedEntityGraph(
        name = "Order.withItemsAndProducts",
        attributeNodes = {
                @NamedAttributeNode(value = "items", subgraph = "items"),
                @NamedAttributeNode("user")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "items",
                        attributeNodes = {
                                @NamedAttributeNode("product")
                        }
                )
        }
)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_sequence")
    @SequenceGenerator(name = "order_sequence", sequenceName = "order_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime orderDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    public Long id() {
        return id;
    }

    public Order setId(Long id) {
        this.id = id;
        return this;
    }

    public User user() {
        return user;
    }

    public Order setUser(User user) {
        this.user = user;
        return this;
    }

    public LocalDateTime orderDate() {
        return orderDate;
    }

    public Order setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public List<OrderItem> items() {
        return items;
    }

    public Order setItems(List<OrderItem> items) {
        this.items = items;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Order order = (Order) o;
        return id.equals(order.id) && user.equals(order.user) && orderDate.equals(order.orderDate);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + user.hashCode();
        result = 31 * result + orderDate.hashCode();
        return result;
    }
}
