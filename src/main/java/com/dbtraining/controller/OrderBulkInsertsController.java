package com.dbtraining.controller;

import com.dbtraining.model.Order;
import com.dbtraining.model.OrderItem;
import com.dbtraining.repository.OrderItemRepository;
import com.dbtraining.repository.OrderRepository;
import com.dbtraining.repository.ProductRepository;
import com.dbtraining.repository.UserRepository;
import com.dbtraining.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import net.datafaker.Faker;
import org.hibernate.Session;
import org.postgresql.jdbc.PgConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static com.dbtraining.init.DataLoader.FLAG_FILE_NAME;

@RestController
@RequestMapping("/api/orders/bulk-inserts")
public class OrderBulkInsertsController {
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;
    private final Faker faker = new Faker();
    private final List<Long> existingUserIds = new ArrayList<>();
    private final List<Long> existingProductIds = new ArrayList<>();
    private final Random random = new Random();

    @PostConstruct
    public void waitForDataLoader() {
        CompletableFuture.runAsync(() -> {
            while (!new File(FLAG_FILE_NAME).exists()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for data initialization");
                }
            }
            initializeExistingIds();
        });
    }

    public OrderBulkInsertsController(OrderService orderService, OrderRepository orderRepository, OrderItemRepository orderItemRepository, UserRepository userRepository, ProductRepository productRepository, EntityManager entityManager) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    record BulkInsertRequest(int numberOfOrders, int itemsPerOrder) {}
    record BenchmarkResult(String strategy, int totalRecords, long executionTimeMs) {}

    @PostMapping("/multiple-transactions")
    public ResponseEntity<BenchmarkResult> insertWithMultipleTransactions(@RequestBody BulkInsertRequest request) {
        var startTime = System.currentTimeMillis();

        for (int i = 0; i < request.numberOfOrders(); i++) {
            // Create order with random user
            var user = userRepository.findById(getRandomUserId()).get();
            var order = new Order()
                    .setOrderDate(LocalDateTime.now())
                    .setUser(user);

            // Create order items with random products
            var items = new ArrayList<OrderItem>();
            for (int j = 0; j < request.itemsPerOrder(); j++) {
                var product = productRepository.findById(getRandomProductId()).get();
                items.add(new OrderItem()
                        .setOrder(order)
                        .setProduct(product)
                        .setQuantity(faker.number().numberBetween(1, 10)));
            }
            order.setItems(items);

            // Save in a new transaction
            orderService.insertSingleOrder(order);
        }

        var executionTime = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new BenchmarkResult(
                "Multiple Transactions",
                request.numberOfOrders() * (1 + request.itemsPerOrder()),
                executionTime
        ));
    }

    @PostMapping("/single-transaction")
    @Transactional
    public ResponseEntity<BenchmarkResult> insertWithSingleTransaction(@RequestBody BulkInsertRequest request) {
        var startTime = System.currentTimeMillis();

        for (int i = 0; i < request.numberOfOrders(); i++) {
            // Create order with random user
            var user = userRepository.findById(getRandomUserId()).get();
            var order = new Order()
                    .setOrderDate(LocalDateTime.now())
                    .setUser(user);

            // Persist order first
            orderRepository.save(order);

            // Create and persist items
            for (int j = 0; j < request.itemsPerOrder(); j++) {
                var product = productRepository.findById(getRandomProductId()).get();
                var item = new OrderItem()
                        .setOrder(order)
                        .setProduct(product)
                        .setQuantity(faker.number().numberBetween(1, 10));
                orderItemRepository.save(item);
            }
        }

        var executionTime = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new BenchmarkResult(
                "Single Transaction",
                request.numberOfOrders() * (1 + request.itemsPerOrder()),
                executionTime
        ));
    }

    @PostMapping("/batch-values")
    @Transactional
    public ResponseEntity<BenchmarkResult> insertBatchValues(@RequestBody BulkInsertRequest request) {
        var startTime = System.currentTimeMillis();

        var ordersSql = new StringBuilder("INSERT INTO orders (id, order_date, user_id) VALUES ");
        var itemsSql = new StringBuilder("INSERT INTO order_items (id, quantity, order_id, product_id) VALUES ");

        for (int i = 0; i < request.numberOfOrders(); i++) {
            // Generate order values
            var userId = getRandomUserId();
            if (i > 0) ordersSql.append(",");
            ordersSql.append(String.format("(nextval('order_sequence'), '%s', %d)",
                    LocalDateTime.now(),
                    userId
            ));

            // Generate items values
            for (int j = 0; j < request.itemsPerOrder(); j++) {
                if (i > 0 || j > 0) itemsSql.append(",");
                var productId = getRandomProductId();
                var quantity = faker.number().numberBetween(1, 10);
                itemsSql.append(String.format("(nextval('order_item_sequence'), %d, currval('order_sequence'), %d)",
                        quantity,
                        productId
                ));
            }
        }

        // Execute batch inserts
        entityManager.createNativeQuery(ordersSql.toString()).executeUpdate();
        entityManager.createNativeQuery(itemsSql.toString()).executeUpdate();

        var executionTime = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new BenchmarkResult(
                "Batch VALUES",
                request.numberOfOrders() * (1 + request.itemsPerOrder()),
                executionTime
        ));
    }

    @PostMapping("/batch-unnest")
    @Transactional
    public ResponseEntity<BenchmarkResult> insertBatchUnnest(@RequestBody BulkInsertRequest request) {
        var startTime = System.currentTimeMillis();

        // Prepare arrays for orders
        var orderDates = new ArrayList<LocalDateTime>();
        var userRefs = new ArrayList<Long>();

        // Prepare arrays for items
        var quantities = new ArrayList<Integer>();
        var productRefs = new ArrayList<Long>();

        // Generate data
        for (int i = 0; i < request.numberOfOrders(); i++) {
            orderDates.add(LocalDateTime.now());
            userRefs.add(getRandomUserId());

            for (int j = 0; j < request.itemsPerOrder(); j++) {
                quantities.add(faker.number().numberBetween(1, 10));
                productRefs.add(getRandomProductId());
            }
        }

        // Execute UNNEST inserts
        var session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            var pgConn = connection.unwrap(PgConnection.class);

            // Insert orders
            var ordersSql = """
                INSERT INTO orders (id, order_date, user_id)
                SELECT nextval('order_sequence'), o.order_date, o.user_id
                FROM UNNEST(?::timestamp[], ?::bigint[]) AS o(order_date, user_id)
                """;

            try (var ps = pgConn.prepareStatement(ordersSql)) {
                ps.setArray(1, pgConn.createArrayOf("timestamp", orderDates.toArray()));
                ps.setArray(2, pgConn.createArrayOf("bigint", userRefs.toArray()));
                ps.executeUpdate();
            }

            // Insert items
            var itemsSql = """
                INSERT INTO order_items (id, quantity, order_id, product_id)
                SELECT nextval('order_item_sequence'), oi.quantity, oi.order_id, oi.product_id
                FROM UNNEST(?::integer[], ?::bigint[], ?::bigint[])\s
                AS oi(quantity, order_id, product_id)
                """;

            try (var ps = pgConn.prepareStatement(itemsSql)) {
                ps.setArray(1, pgConn.createArrayOf("bigint", quantities.toArray()));
                ps.setArray(2, pgConn.createArrayOf("bigint", quantities.toArray()));
                ps.setArray(3, pgConn.createArrayOf("bigint", productRefs.toArray()));
                ps.executeUpdate();
            }
        });

        var executionTime = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(new BenchmarkResult(
                "Batch UNNEST",
                request.numberOfOrders() * (1 + request.itemsPerOrder()),
                executionTime
        ));
    }

    private void initializeExistingIds() {
        // Cache existing user IDs
        existingUserIds.addAll(entityManager.createNativeQuery("SELECT id FROM users")
                .getResultList()
                .stream()
                .map(id -> ((Number) id).longValue())
                .toList());

        if (existingUserIds.isEmpty()) {
            throw new IllegalStateException("No existing users found in the database");
        }

        // Cache existing product IDs
        existingProductIds.addAll(entityManager.createNativeQuery("SELECT id FROM products")
                .getResultList()
                .stream()
                .map(id -> ((Number) id).longValue())
                .toList());

        if (existingProductIds.isEmpty()) {
            throw new IllegalStateException("No existing products found in the database");
        }
    }

    private Long getRandomUserId() {
        return existingUserIds.get(random.nextInt(existingUserIds.size()));
    }

    private Long getRandomProductId() {
        return existingProductIds.get(random.nextInt(existingProductIds.size()));
    }

}