package com.fashion.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b2.mysql.integration", matches = "true")
@DisplayName("B2 MySQL 8 普通订单库存与状态闭环")
class OrderInventoryMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b2_it_[0-9a-f]{32}";
    private static String adminUrl;
    private static String schemaUrl;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        String configPath = System.getProperty("b2.mysql.config");
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IllegalStateException("b2.mysql.config is required");
        }
        Path path = Paths.get(configPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B2 MySQL config is missing");
        }
        Map<String, Object> datasource;
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(input);
            datasource = nestedMap(nestedMap(root, "fashion"), "datasource");
        }
        String host = value(datasource, "host");
        String port = value(datasource, "port");
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b2_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchemaName(schema);

        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(result.next());
                assertTrue(Integer.parseInt(result.getString(1).split("\\.")[0]) >= 8,
                        "B2 integration gate requires MySQL 8+");
            }
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
    }

    @AfterAll
    static void dropIsolatedSchema() throws Exception {
        if (schema == null) {
            return;
        }
        validateSchemaName(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + schema + "`");
        }
    }

    @BeforeEach
    void resetToPreB2OrdersSchema() throws Exception {
        execute("DROP TABLE IF EXISTS order_detail");
        execute("DROP TABLE IF EXISTS orders");
        execute("DROP TABLE IF EXISTS product");
        execute("CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, status INT NOT NULL DEFAULT 1, "
                + "pay_status TINYINT NOT NULL DEFAULT 0, order_time DATETIME NOT NULL, "
                + "is_seckill TINYINT NOT NULL DEFAULT 0, original_price DECIMAL(10,2) NULL) ENGINE=InnoDB");
    }

    @Test
    @DisplayName("迁移首次执行和重跑成功，CHECK 生效且超时查询使用目标索引")
    void migrationIsIdempotentAndIndexIsUsable() throws Exception {
        runMigration();
        runMigration();

        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='orders' "
                + "AND column_name IN ('stock_deducted','user_coupon_id')"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='orders' "
                + "AND index_name='idx_orders_timeout'"));
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO orders(status,pay_status,order_time,stock_deducted) VALUES (1,0,NOW(),2)"));

        execute("INSERT INTO orders(status,pay_status,is_seckill,order_time,stock_deducted) "
                + "VALUES (1,0,0,DATE_SUB(NOW(), INTERVAL 40 MINUTE),0),(1,0,0,DATE_SUB(NOW(), INTERVAL 40 MINUTE),1)");
        for (int i = 0; i < 100; i++) {
            execute("INSERT INTO orders(status,pay_status,is_seckill,order_time,stock_deducted) "
                    + "SELECT 4,1,0,NOW(),0 FROM information_schema.columns LIMIT 100");
        }
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM orders "
                + "WHERE status=1 AND pay_status=0 AND order_time<DATE_SUB(NOW(), INTERVAL 30 MINUTE)"));
        ExplainPlan plan = explain("EXPLAIN SELECT * FROM orders "
                + "WHERE status=1 AND pay_status=0 AND is_seckill=0 "
                + "AND order_time<DATE_SUB(NOW(), INTERVAL 30 MINUTE) AND id>0 ORDER BY id ASC LIMIT 100");
        assertEquals("idx_orders_timeout", plan.key);
        assertTrue(plan.rows > 0 && plan.rows < 10000);
        assertTrue(plan.extra != null && plan.extra.contains("Using"));
        System.out.println("B2 timeout EXPLAIN key=" + plan.key
                + ", rows=" + plan.rows + ", Extra=" + plan.extra);
    }

    @Test
    @DisplayName("迁移拒绝库存事实为零的历史履约中订单")
    void migrationRejectsHistoricalInFlightFulfillment() throws Exception {
        execute("INSERT INTO orders(status,pay_status,is_seckill,order_time) VALUES (2,1,0,NOW())");

        SQLException blocked = assertThrows(SQLException.class,
                OrderInventoryMysqlIntegrationTest::runMigration);

        assertEquals("45000", blocked.getSQLState());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM orders WHERE status=2"));

        execute("UPDATE orders SET status=4 WHERE status=2");
        runMigration();
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema=DATABASE() AND table_name='orders' "
                + "AND constraint_name='chk_orders_stock_deducted'"));
    }

    @Test
    @DisplayName("迁移遇到部分或错误字段定义显式失败")
    void migrationRejectsWrongExistingDefinition() throws Exception {
        execute("ALTER TABLE orders ADD COLUMN stock_deducted INT NOT NULL DEFAULT 0");

        SQLException mismatch = assertThrows(SQLException.class,
                OrderInventoryMysqlIntegrationTest::runMigration);

        assertEquals("45000", mismatch.getSQLState());
    }

    @Test
    @DisplayName("迁移补齐定义正确的部分 schema")
    void migrationCompletesCompatiblePartialSchema() throws Exception {
        execute("ALTER TABLE orders ADD COLUMN user_coupon_id BIGINT NULL");
        execute("ALTER TABLE orders ADD COLUMN stock_deducted TINYINT(1) NOT NULL DEFAULT 0");

        runMigration();

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema=DATABASE() AND table_name='orders' "
                + "AND constraint_name='chk_orders_stock_deducted' AND constraint_type='CHECK'"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='idx_orders_timeout'"));
    }

    @Test
    @DisplayName("迁移拒绝同名但表达式错误的 CHECK")
    void migrationRejectsWrongCheckDefinition() throws Exception {
        execute("ALTER TABLE orders ADD COLUMN user_coupon_id BIGINT NULL");
        execute("ALTER TABLE orders ADD COLUMN stock_deducted TINYINT(1) NOT NULL DEFAULT 0");
        execute("ALTER TABLE orders ADD CONSTRAINT chk_orders_stock_deducted CHECK (stock_deducted IN (0,2))");

        SQLException mismatch = assertThrows(SQLException.class,
                OrderInventoryMysqlIntegrationTest::runMigration);

        assertEquals("45000", mismatch.getSQLState());
    }

    @Test
    @DisplayName("迁移拒绝同名但列顺序错误的超时索引")
    void migrationRejectsWrongIndexDefinition() throws Exception {
        execute("CREATE INDEX idx_orders_timeout ON orders(pay_status,status,order_time)");

        SQLException mismatch = assertThrows(SQLException.class,
                OrderInventoryMysqlIntegrationTest::runMigration);

        assertEquals("45000", mismatch.getSQLState());
    }

    @Test
    @DisplayName("final07 的 orders 建表和历史种子可在 MySQL 8 执行")
    void baselineOrdersDefinitionAndSeedAreExecutable() throws Exception {
        String baseline = new String(Files.readAllBytes(Paths.get("..", "..", "mysql", "final07.sql")),
                StandardCharsets.UTF_8);
        String createMarker = "CREATE TABLE `orders`";
        int createStart = baseline.indexOf(createMarker);
        int createEnd = baseline.indexOf(';', createStart);
        int insertStart = baseline.indexOf("INSERT INTO `orders` VALUES", createEnd);
        int insertEnd = baseline.indexOf(';', insertStart);
        assertTrue(createStart >= 0 && createEnd > createStart && insertStart > createEnd && insertEnd > insertStart);

        execute("DROP TABLE orders");
        execute(baseline.substring(createStart, createEnd));
        execute(baseline.substring(insertStart, insertEnd));

        assertEquals(4, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM orders WHERE stock_deducted=0"));
    }

    @Test
    @DisplayName("有限库存并发争抢不超卖，成功订单和明细与库存变化一致")
    void concurrentCreationDoesNotOversell() throws Exception {
        createOperationalTables();
        execute("INSERT INTO product(id,status,stock) VALUES (1,1,5)");

        List<Boolean> results = runConcurrently(10, OrderInventoryMysqlIntegrationTest::createOneOrder);

        assertEquals(5, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(0, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(5, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(5, scalarInt("SELECT COUNT(*) FROM order_detail"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM product WHERE stock < 0"));
    }

    @Test
    @DisplayName("多商品中途失败和落单后失败都整体回滚")
    void creationFailuresRollbackAllWrites() throws Exception {
        createOperationalTables();
        execute("INSERT INTO product(id,status,stock) VALUES (1,1,2),(2,1,0)");

        assertThrows(IllegalStateException.class, OrderInventoryMysqlIntegrationTest::failOnSecondProduct);
        assertEquals(2, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM orders"));

        assertThrows(IllegalStateException.class, OrderInventoryMysqlIntegrationTest::failAfterOrderAndDetail);
        assertEquals(2, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM order_detail"));
    }

    @Test
    @DisplayName("双取消只回补一次，支付与取消竞态只有一个合法赢家")
    void cancellationAndPaymentRacesHaveOneWinner() throws Exception {
        createOperationalTables();
        seedPendingOrder();

        List<Boolean> cancellations = runConcurrently(2, OrderInventoryMysqlIntegrationTest::cancelOnce);
        assertEquals(1, cancellations.stream().filter(Boolean::booleanValue).count());
        assertEquals(5, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(5, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(0, scalarInt("SELECT stock_deducted FROM orders WHERE id=100"));

        execute("DELETE FROM order_detail");
        execute("DELETE FROM orders");
        execute("DELETE FROM product");
        seedPendingOrder();
        List<Boolean> race = runTwo(OrderInventoryMysqlIntegrationTest::payOnce,
                OrderInventoryMysqlIntegrationTest::cancelOnce);
        assertEquals(1, race.stream().filter(Boolean::booleanValue).count());
        int status = scalarInt("SELECT status FROM orders WHERE id=100");
        int stockFact = scalarInt("SELECT stock_deducted FROM orders WHERE id=100");
        int stock = scalarInt("SELECT stock FROM product WHERE id=1");
        assertTrue((status == 2 && stockFact == 1 && stock == 3)
                || (status == 5 && stockFact == 0 && stock == 5));
    }

    private static void createOperationalTables() throws Exception {
        execute("DROP TABLE orders");
        execute("CREATE TABLE product (id BIGINT PRIMARY KEY, status INT NOT NULL, stock INT NOT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL DEFAULT 7, "
                + "status INT NOT NULL, pay_status TINYINT NOT NULL, order_time DATETIME NOT NULL, "
                + "user_coupon_id BIGINT NULL, stock_deducted TINYINT(1) NOT NULL DEFAULT 0, "
                + "CONSTRAINT chk_orders_stock_deducted CHECK (stock_deducted IN (0,1))) ENGINE=InnoDB");
        execute("CREATE TABLE order_detail (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, "
                + "product_id BIGINT NOT NULL, number INT NOT NULL) ENGINE=InnoDB");
    }

    private static boolean createOneOrder() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deduct = connection.prepareStatement(
                    "UPDATE product SET stock=stock-1 WHERE id=1 AND status=1 AND stock>=1")) {
                if (deduct.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            long orderId;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO orders(status,pay_status,order_time,stock_deducted) VALUES (1,0,NOW(),1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    assertTrue(keys.next());
                    orderId = keys.getLong(1);
                }
            }
            try (PreparedStatement detail = connection.prepareStatement(
                    "INSERT INTO order_detail(order_id,product_id,number) VALUES (?,1,1)")) {
                detail.setLong(1, orderId);
                detail.executeUpdate();
            }
            connection.commit();
            return true;
        }
    }

    private static void failOnSecondProduct() throws Exception {
        inTransaction(connection -> {
            assertEquals(1, update(connection,
                    "UPDATE product SET stock=stock-1 WHERE id=1 AND status=1 AND stock>=1"));
            if (update(connection,
                    "UPDATE product SET stock=stock-1 WHERE id=2 AND status=1 AND stock>=1") != 1) {
                throw new IllegalStateException("second product unavailable");
            }
        });
    }

    private static void failAfterOrderAndDetail() throws Exception {
        inTransaction(connection -> {
            assertEquals(1, update(connection,
                    "UPDATE product SET stock=stock-1 WHERE id=1 AND status=1 AND stock>=1"));
            update(connection, "INSERT INTO orders(status,pay_status,order_time,stock_deducted) VALUES (1,0,NOW(),1)");
            update(connection, "INSERT INTO order_detail(order_id,product_id,number) VALUES (LAST_INSERT_ID(),1,1)");
            throw new IllegalStateException("injected detail-stage failure");
        });
    }

    private static void seedPendingOrder() throws Exception {
        execute("INSERT INTO product(id,status,stock) VALUES (1,1,3)");
        execute("INSERT INTO orders(id,user_id,status,pay_status,order_time,stock_deducted) VALUES (100,7,1,0,NOW(),1)");
        execute("INSERT INTO order_detail(order_id,product_id,number) VALUES (100,1,2)");
    }

    private static boolean cancelOnce() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                int status;
                int payStatus;
                int stockDeducted;
                try (PreparedStatement lock = connection.prepareStatement(
                        "SELECT status,pay_status,stock_deducted FROM orders WHERE id=100 FOR UPDATE");
                     ResultSet row = lock.executeQuery()) {
                    assertTrue(row.next());
                    status = row.getInt(1);
                    payStatus = row.getInt(2);
                    stockDeducted = row.getInt(3);
                }
                if (status != 1 || payStatus != 0) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement cancel = connection.prepareStatement(
                        "UPDATE orders SET status=5,stock_deducted=0 WHERE id=100 AND status=1 AND pay_status=0 AND stock_deducted=?")) {
                    cancel.setInt(1, stockDeducted);
                    if (cancel.executeUpdate() != 1) {
                        connection.rollback();
                        return false;
                    }
                }
                if (stockDeducted == 1) {
                    assertEquals(1, update(connection, "UPDATE product SET stock=stock+2 WHERE id=1"));
                }
                connection.commit();
                return true;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static boolean payOnce() throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("SELECT id FROM orders WHERE id=100 FOR UPDATE")) {
                    try (ResultSet row = lock.executeQuery()) {
                        assertTrue(row.next());
                    }
                }
                int rows = update(connection, "UPDATE orders SET status=2,pay_status=1 "
                        + "WHERE id=100 AND status=1 AND pay_status=0 AND stock_deducted=1");
                if (rows != 1) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static void runMigration() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_order_inventory_state.sql");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = connection(); Statement jdbc = connection.createStatement()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring("DELIMITER ".length()).trim();
                    continue;
                }
                statement.append(line).append('\n');
                if (trimmed.endsWith(delimiter)) {
                    String sql = statement.toString().trim();
                    sql = sql.substring(0, sql.length() - delimiter.length()).trim();
                    jdbc.execute(sql);
                    statement.setLength(0);
                }
            }
        }
    }

    private static void inTransaction(SqlWork work) throws Exception {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static int update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(schemaUrl, username, password);
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int scalarInt(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static ExplainPlan explain(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return new ExplainPlan(result.getString("key"), result.getLong("rows"), result.getString("Extra"));
        }
    }

    private static final class ExplainPlan {
        private final String key;
        private final long rows;
        private final String extra;

        private ExplainPlan(String key, long rows, String extra) {
            this.key = key;
            this.rows = rows;
            this.extra = extra;
        }
    }

    private static List<Boolean> runConcurrently(int count, Callable<Boolean> task) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(task);
        }
        return runTasks(tasks);
    }

    private static List<Boolean> runTwo(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(first);
        tasks.add(second);
        return runTasks(tasks);
    }

    private static List<Boolean> runTasks(List<Callable<Boolean>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void validateSchemaName(String name) {
        if (!name.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("invalid B2 temporary schema name");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        Object nested = parent.get(key);
        if (!(nested instanceof Map)) {
            throw new IllegalStateException("missing local datasource section");
        }
        return (Map<String, Object>) nested;
    }

    private static String value(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            throw new IllegalStateException("missing local datasource setting");
        }
        return String.valueOf(value);
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws Exception;
    }
}
