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

@EnabledIfSystemProperty(named = "b3.mysql.integration", matches = "true")
@DisplayName("B3 MySQL 8 退款状态与迁移门禁")
class RefundStateMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b3_it_[0-9a-f]{32}";
    private static String adminUrl;
    private static String schemaUrl;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        Map<String, Object> datasource = loadDatasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b3_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchemaName(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(result.next());
                assertTrue(Integer.parseInt(result.getString(1).split("\\.")[0]) >= 8);
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
    void resetPreB3Schema() throws Exception {
        execute("DROP TABLE IF EXISTS refund");
        execute("DROP TABLE IF EXISTS orders");
        execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, status TINYINT NOT NULL, "
                + "pay_status TINYINT NOT NULL DEFAULT 1, stock_deducted TINYINT(1) NOT NULL DEFAULT 1) ENGINE=InnoDB");
        execute("CREATE TABLE refund (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, "
                + "order_detail_id BIGINT NULL, user_id BIGINT NOT NULL, refund_no VARCHAR(50) NOT NULL, "
                + "reason VARCHAR(500), amount DECIMAL(10,2) NOT NULL, status TINYINT NOT NULL DEFAULT 0, "
                + "order_status TINYINT NULL, audit_opinion VARCHAR(500), audit_time DATETIME, refund_time DATETIME, "
                + "create_time DATETIME NOT NULL, update_time DATETIME, UNIQUE KEY idx_refund_no(refund_no), "
                + "UNIQUE KEY idx_refund_order(order_id), KEY idx_refund_user(user_id)) ENGINE=InnoDB");
    }

    @Test
    @DisplayName("首次迁移与重跑成功且升级库和干净库约束等价")
    void migrationIsIdempotentAndMatchesCleanSchema() throws Exception {
        runMigration();
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,refund_time,create_time) "
                + "VALUES (301,7,'RF-WAIT',10,1,3,NULL,NOW()),(302,7,'RF-DONE',10,2,4,NOW(),NOW())");
        runMigration();

        String upgradedColumns = keyColumns();
        String upgradedChecks = keyChecks();
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema=DATABASE() AND table_name='refund' "
                + "AND constraint_name IN ('chk_refund_status_b3','chk_refund_order_status_b3') "
                + "AND constraint_type='CHECK' AND enforced='YES'"));
        assertInvalidOrderStatusRejected();

        execute("DROP TABLE refund");
        execute(cleanRefundCreateStatement());
        assertEquals(upgradedColumns, keyColumns());
        assertEquals(upgradedChecks, keyChecks());
        assertInvalidOrderStatusRejected();
    }

    @Test
    @DisplayName("首次迁移阻断旧状态 1 和 2")
    void migrationRejectsUntrustedHistoricalCompletionStates() throws Exception {
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-1',10,1,3,NOW())");
        SQLException failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());

        resetPreB3Schema();
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-2',10,2,3,NOW())");
        failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
    }

    @Test
    @DisplayName("首次迁移阻断不可恢复待审核和半完成拒绝记录")
    void migrationRejectsUnrecoverableOrderFacts() throws Exception {
        execute("INSERT INTO orders VALUES (100,7,3,1,1)");
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-P',10,0,3,NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());

        resetPreB3Schema();
        execute("INSERT INTO orders VALUES (100,7,6,1,1)");
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-R',10,3,3,NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());

        resetPreB3Schema();
        execute("INSERT INTO orders VALUES (100,7,6,1,1)");
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-NULL',10,0,NULL,NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());

        resetPreB3Schema();
        execute("INSERT INTO orders VALUES (100,7,6,1,1)");
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-BAD-ORDER',10,0,2,NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());

        resetPreB3Schema();
        execute("INSERT INTO orders VALUES (100,7,6,1,1)");
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,refund_time,create_time) "
                + "VALUES (100,7,'RF-BAD-TIME',10,0,3,NOW(),NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("单 marker 和错误双 marker 均显式失败")
    void migrationRejectsPartialAndWrongMarkers() throws Exception {
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_status_b3 CHECK (status IN (0,1,2,3))");
        assertEquals("45000", assertThrows(SQLException.class,
                RefundStateMysqlIntegrationTest::runMigration).getSQLState());

        resetPreB3Schema();
        prepareMarkerSchema();
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_status_b3 CHECK (status IN (0,1,2))");
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_order_status_b3 CHECK (order_status IN (3,4))");
        SQLException failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("chk_refund_status_b3 definition mismatch"));

        resetPreB3Schema();
        prepareMarkerSchema();
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_status_b3 "
                + "CHECK (status IN (0,1,2,3)) NOT ENFORCED");
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_order_status_b3 "
                + "CHECK (order_status IN (3,4)) NOT ENFORCED");
        failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("B3 refund CHECK marker mismatch"));

        resetPreB3Schema();
        prepareMarkerSchema();
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_status_b3 CHECK (status IN (0,12,3))");
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_order_status_b3 CHECK (order_status IN (3,4))");
        failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("chk_refund_status_b3 definition mismatch"));

        resetPreB3Schema();
        prepareMarkerSchema();
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_status_b3 CHECK (status IN (0,1,2,3))");
        execute("ALTER TABLE refund ADD CONSTRAINT chk_refund_order_status_b3 CHECK (order_status IN (34))");
        failure = assertThrows(SQLException.class, RefundStateMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("chk_refund_order_status_b3 definition mismatch"));
    }

    @Test
    @DisplayName("并发审核同意只有一个 CAS 成功")
    void concurrentApprovalsHaveOneWinner() throws Exception {
        execute("INSERT INTO orders VALUES (100,7,6,1,1)");
        runMigration();
        execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (100,7,'RF-C',10,0,3,NOW())");

        List<Integer> results = runConcurrently(10, () -> update(
                "UPDATE refund SET status=1,audit_time=NOW() WHERE id=1 AND status=0"));

        assertEquals(1, results.stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, scalarInt("SELECT status FROM refund WHERE id=1"));
    }

    private static void assertInvalidOrderStatusRejected() {
        assertThrows(SQLException.class, () -> execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (201,7,'RF-N',10,0,NULL,NOW())"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (202,7,'RF-2',10,0,2,NOW())"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO refund(order_id,user_id,refund_no,amount,status,order_status,create_time) "
                + "VALUES (205,7,'RF-5',10,0,5,NOW())"));
    }

    private static void prepareMarkerSchema() throws SQLException {
        execute("ALTER TABLE refund MODIFY COLUMN order_status TINYINT NOT NULL");
    }

    private static String keyColumns() throws Exception {
        return scalarString("SELECT GROUP_CONCAT(CONCAT(column_name,':',column_type,':',is_nullable,':',"
                + "COALESCE(column_default,'NULL')) ORDER BY ordinal_position SEPARATOR '|') "
                + "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='refund' "
                + "AND column_name IN ('status','order_status')");
    }

    private static String keyChecks() throws Exception {
        return scalarString("SELECT GROUP_CONCAT(CONCAT(tc.constraint_name,':',tc.enforced,':',"
                + "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,'`',''),' ',''),'(',''),')',''),',',''))) "
                + "ORDER BY tc.constraint_name SEPARATOR '|') FROM information_schema.table_constraints tc "
                + "JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema "
                + "AND cc.constraint_name=tc.constraint_name WHERE tc.constraint_schema=DATABASE() "
                + "AND tc.table_name='refund' AND tc.constraint_name IN "
                + "('chk_refund_status_b3','chk_refund_order_status_b3')");
    }

    private static String cleanRefundCreateStatement() throws Exception {
        String sql = new String(Files.readAllBytes(Paths.get("..", "..", "mysql", "refund_table.sql")),
                StandardCharsets.UTF_8);
        int start = sql.indexOf("CREATE TABLE");
        int end = sql.indexOf(';', start);
        assertTrue(start >= 0 && end > start);
        return sql.substring(start, end);
    }

    private static void runMigration() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_refund_review_state.sql");
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

    private static List<Integer> runConcurrently(int count, Callable<Integer> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return task.call();
            });
        }
        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> callable : tasks) {
            futures.add(pool.submit(callable));
        }
        ready.await();
        start.countDown();
        List<Integer> results = new ArrayList<>();
        for (Future<Integer> future : futures) {
            results.add(future.get());
        }
        pool.shutdownNow();
        return results;
    }

    private static int update(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private static void execute(String sql) throws SQLException {
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

    private static String scalarString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(schemaUrl, username, password);
    }

    private static Map<String, Object> loadDatasourceSettings() throws Exception {
        String configPath = System.getProperty("b3.mysql.config");
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IllegalStateException("b3.mysql.config is required");
        }
        Path path = Paths.get(configPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B3 MySQL config is missing");
        }
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(input);
            return nestedMap(nestedMap(root, "fashion"), "datasource");
        }
    }

    private static void validateSchemaName(String name) {
        if (!name.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("invalid B3 temporary schema name");
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
}
