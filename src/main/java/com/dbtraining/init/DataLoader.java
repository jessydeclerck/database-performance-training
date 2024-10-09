package com.dbtraining.init;

import jakarta.persistence.EntityManager;
import net.datafaker.Faker;
import org.hibernate.Session;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DataLoader {

    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);
    public static final String FLAG_FILE_NAME = "data-generated.delete-me-to-regenerate";

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final Faker faker = new Faker();
    private final Random random = new Random();

    private static final int NUM_USERS = 100_000;
    private static final int NUM_PRODUCTS = 100_000;
    private static final int NUM_ORDERS = 1_000_000;
    public static final int NUM_ORDER_ITEMS = NUM_ORDERS * 3;

    private final AtomicBoolean isInserting = new AtomicBoolean(false);
    private Thread progressLoggerThread;

    @Autowired
    public DataLoader(EntityManager entityManager, TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    public void run() {
        if (isDataAlreadyGenerated()) {
            logger.info("Data has already been generated. Skipping regeneration.");
            return;
        }

        long startTime = System.currentTimeMillis();
        logger.info("Starting data generation...");

        try {
            disableSynchronousCommit();
            logger.info("Disabled synchronous commit for bulk loading");
            cleanupDatabase();
            generateUsers();
            generateProducts();
            generateOrders();
            generateOrderItems();

            createFlagFile();

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data generation completed in {} seconds", duration / 1000);
        } catch (Exception e) {
            logger.error("Error during data generation", e);
        }
    }

    private boolean isDataAlreadyGenerated() {
        File flagFile = new File(FLAG_FILE_NAME);
        return flagFile.exists();
    }

    private void createFlagFile() {
        try {
            Path flagFilePath = Paths.get(FLAG_FILE_NAME);
            Files.createFile(flagFilePath);
            logger.info("Flag file created: {}", FLAG_FILE_NAME);
        } catch (IOException e) {
            logger.error("Error creating flag file", e);
        }
    }

    private void cleanupDatabase() {
        logger.info("Cleaning up database...");
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    // Drop content of tables
                    stmt.executeUpdate("TRUNCATE TABLE order_items CASCADE");
                    stmt.executeUpdate("TRUNCATE TABLE orders CASCADE");
                    stmt.executeUpdate("TRUNCATE TABLE products CASCADE");
                    stmt.executeUpdate("TRUNCATE TABLE users CASCADE");

                    // Reset sequences
                    stmt.executeUpdate("ALTER SEQUENCE order_item_sequence RESTART WITH 1");
                    stmt.executeUpdate("ALTER SEQUENCE order_sequence RESTART WITH 1");
                    stmt.executeUpdate("ALTER SEQUENCE product_sequence RESTART WITH 1");
                    stmt.executeUpdate("ALTER SEQUENCE user_sequence RESTART WITH 1");
                }
            });
            return null;
        });
        logger.info("Database cleanup completed.");
    }

    private void generateUsers() {
        logger.info("Generating {} users", NUM_USERS);
        List<String> usernames = new ArrayList<>(NUM_USERS);
        List<String> emails = new ArrayList<>(NUM_USERS);

        // Prepare data
        startProgressLogger("Preparing users data", NUM_USERS);
        for (int i = 0; i < NUM_USERS; i++) {
            usernames.add(faker.internet().username());
            emails.add(faker.internet().emailAddress());
        }
        stopProgressLogger();


        String sql = """
                    INSERT INTO users (id, username, email)
                    SELECT nextval('user_sequence'), u.username, u.email
                    FROM UNNEST(?::text[], ?::text[]) AS u(username, email)
                """;
        startProgressLogger("Inserting users", NUM_USERS);
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                PgConnection pgConn = connection.unwrap(PgConnection.class);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    Array usernameArray = pgConn.createArrayOf("text", usernames.toArray());
                    Array emailArray = pgConn.createArrayOf("text", emails.toArray());
                    ps.setArray(1, usernameArray);
                    ps.setArray(2, emailArray);
                    int inserted = ps.executeUpdate();
                    logger.info("Inserted {} users", inserted);
                }
            });
            return null;
        });
        stopProgressLogger();
        logger.info("User generation completed. Total users: {}", NUM_USERS);
    }

    private void generateProducts() {
        logger.info("Generating {} products", NUM_PRODUCTS);
        List<String> names = new ArrayList<>(NUM_PRODUCTS);
        List<BigDecimal> prices = new ArrayList<>(NUM_PRODUCTS);

        // Prepare data
        startProgressLogger("Preparing products data", NUM_PRODUCTS);
        for (int i = 0; i < NUM_PRODUCTS; i++) {
            names.add(faker.commerce().productName());
            prices.add(new BigDecimal(faker.commerce().price().replace(",", ".")));
        }
        stopProgressLogger();


        String sql = """
                    INSERT INTO products (id, name, price)
                    SELECT nextval('product_sequence'), p.name, p.price
                    FROM UNNEST(?::text[], ?::numeric[]) AS p(name, price)
                """;

        startProgressLogger("Inserting products", NUM_PRODUCTS);
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                PgConnection pgConn = connection.unwrap(PgConnection.class);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    Array nameArray = pgConn.createArrayOf("text", names.toArray());
                    Array priceArray = pgConn.createArrayOf("numeric", prices.toArray());
                    ps.setArray(1, nameArray);
                    ps.setArray(2, priceArray);
                    int inserted = ps.executeUpdate();
                    logger.info("Inserted {} products", inserted);
                }
            });
            return null;
        });
        stopProgressLogger();

        logger.info("Product generation completed. Total products: {}", NUM_PRODUCTS);
    }

    private void generateOrders() {
        logger.info("Generating {} orders", NUM_ORDERS);
        List<Timestamp> dates = new ArrayList<>(NUM_ORDERS);
        List<Long> userIds = new ArrayList<>(NUM_ORDERS);

        // Prepare data
        startProgressLogger("Preparing orders data", NUM_ORDERS);
        for (int i = 0; i < NUM_ORDERS; i++) {
            dates.add(Timestamp.valueOf(LocalDateTime.now().minusDays(random.nextInt(365))));
            userIds.add((long) (random.nextInt(NUM_USERS) + 1));
        }
        stopProgressLogger();

        String sql = """
                    INSERT INTO orders (id, order_date, user_id)
                    SELECT nextval('order_sequence'), o.order_date, o.user_id
                    FROM UNNEST(?::timestamp[], ?::bigint[]) AS o(order_date, user_id)
                """;

        startProgressLogger("Inserting orders", NUM_ORDERS);
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                PgConnection pgConn = connection.unwrap(PgConnection.class);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    Array dateArray = pgConn.createArrayOf("timestamp", dates.toArray());
                    Array userIdArray = pgConn.createArrayOf("bigint", userIds.toArray());
                    ps.setArray(1, dateArray);
                    ps.setArray(2, userIdArray);
                    int inserted = ps.executeUpdate();
                    logger.info("Inserted {} orders", inserted);
                }
            });
            return null;
        });
        stopProgressLogger();
        logger.info("Order generation completed. Total orders: {}", NUM_ORDERS);
    }

    private void generateOrderItems() {
        logger.info("Generating {} order items", NUM_ORDER_ITEMS);
        List<Long> orderIds = new ArrayList<>(NUM_ORDER_ITEMS);
        List<Long> productIds = new ArrayList<>(NUM_ORDER_ITEMS);
        List<Integer> quantities = new ArrayList<>(NUM_ORDER_ITEMS);

        // Prepare data
        startProgressLogger("Preparing order items data", NUM_ORDER_ITEMS);
        for (int i = 0; i < NUM_ORDER_ITEMS; i++) {
            orderIds.add((long) (random.nextInt(NUM_ORDERS) + 1));
            productIds.add((long) (random.nextInt(NUM_PRODUCTS) + 1));
            quantities.add(random.nextInt(5) + 1);
        }
        stopProgressLogger();
        String sql = """
                    INSERT INTO order_items (id, order_id, product_id, quantity)
                    SELECT nextval('order_item_sequence'), oi.order_id, oi.product_id, oi.quantity
                    FROM UNNEST(?::bigint[], ?::bigint[], ?::integer[]) AS oi(order_id, product_id, quantity)
                """;

        startProgressLogger("Inserting order items", NUM_ORDER_ITEMS);
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                PgConnection pgConn = connection.unwrap(PgConnection.class);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    Array orderIdArray = pgConn.createArrayOf("bigint", orderIds.toArray());
                    Array productIdArray = pgConn.createArrayOf("bigint", productIds.toArray());
                    Array quantityArray = pgConn.createArrayOf("integer", quantities.toArray());
                    ps.setArray(1, orderIdArray);
                    ps.setArray(2, productIdArray);
                    ps.setArray(3, quantityArray);
                    int inserted = ps.executeUpdate();
                    logger.info("Inserted {} order items", inserted);
                }
            });
            return null;
        });
        stopProgressLogger();
        logger.info("Order items generation completed. Total order items: {}", NUM_ORDER_ITEMS);
    }

    private void disableSynchronousCommit() {
        transactionTemplate.execute(status -> {
            Session session = entityManager.unwrap(Session.class);
            session.doWork(connection -> {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET synchronous_commit = off");
                }
            });
            return null;
        });
    }

    private void startProgressLogger(String operation, int totalRecords) {
        isInserting.set(true);

        progressLoggerThread = new Thread(() -> {
            while (isInserting.get()) {
                try {
                    Thread.sleep(3000);
                    logger.info("{}: generating {} records",
                            operation, totalRecords);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        progressLoggerThread.setDaemon(true);
        progressLoggerThread.start();
    }

    private void stopProgressLogger() {
        isInserting.set(false);
        if (progressLoggerThread != null) {
            progressLoggerThread.interrupt();
            try {
                progressLoggerThread.join(1000); // Wait up to 1 second for the thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}