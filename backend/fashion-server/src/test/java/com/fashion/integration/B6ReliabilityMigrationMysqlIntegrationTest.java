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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b6.integration", matches = "true")
@DisplayName("B6 MySQL 8 可靠消息迁移门禁")
class B6ReliabilityMigrationMysqlIntegrationTest {
    private static final String SCHEMA_PATTERN = "fsm_b6_migration_it_[0-9a-f]{32}";
    private static String adminUrl;
    private static String schemaUrl;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void createSchema() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        B6IntegrationSafety.requireLoopback(value(datasource, "host"), "MySQL");
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b6_migration_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(version.next());
                assertEquals(8, Integer.parseInt(version.getString(1).split("\\.")[0]));
            }
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
    }

    @AfterAll
    static void dropSchema() throws Exception {
        if (schema == null) return;
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + schema + "`");
        }
    }

    @BeforeEach
    void resetLegacySchema() throws Exception {
        execute("DROP TABLE IF EXISTS seckill_reconciliation_anomaly");
        execute("DROP TABLE IF EXISTS seckill_compensation_record");
        execute("DROP TABLE IF EXISTS seckill_message_log");
        execute("DROP TABLE IF EXISTS seckill_order");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, "
                + "coupon_id BIGINT NOT NULL, order_number VARCHAR(50) NULL, status INT NOT NULL DEFAULT 1, "
                + "create_time DATETIME, pay_time DATETIME, UNIQUE KEY idx_seckill_order_number(order_number)) "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb3");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (7,19,'9001',1,NOW())");
    }

    @Test
    @DisplayName("迁移首次执行与重复执行都成功且三张可靠性表定义保留")
    void firstRunAndRerunAreIdempotent() throws Exception {
        runMigration();
        runMigration();

        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name IN "
                + "('seckill_message_log','seckill_compensation_record','seckill_reconciliation_anomaly')"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_message_log' "
                + "AND index_name='uk_seckill_message_id' AND non_unique=0"));
    }

    @Test
    @DisplayName("非法与重复订单号在创建任何 B6 表前阻断")
    void dirtyOrderNumbersAreRejected() throws Exception {
        execute("UPDATE seckill_order SET order_number='BAD-ORDER'");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_message_log'"));

        resetLegacySchema();
        execute("ALTER TABLE seckill_order DROP INDEX idx_seckill_order_number");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (8,19,'9001',1,NOW())");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("同名部分定义和非空部分迁移都显式阻断")
    void partialAndNonEmptyPrefixAreRejected() throws Exception {
        execute("CREATE TABLE seckill_message_log(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_ORDER_CREATE:9001','ORDER_CREATE','INITIAL','9001',7,19,'{}','e','r','PREPARED')");
        execute("DROP TABLE seckill_reconciliation_anomaly");
        execute("DROP TABLE seckill_compensation_record");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("每个 DDL 中断点可由空前缀前滚，逆序对象组合一律阻断")
    void everyDdlCheckpointHasExplicitForwardOnlyRule() throws Exception {
        execute("ALTER TABLE seckill_order MODIFY order_number VARCHAR(50) NOT NULL");
        runMigration();
        assertEquals(3, reliabilityTableCount());

        resetLegacySchema();
        runMigration();
        execute("DROP TABLE seckill_reconciliation_anomaly");
        execute("DROP TABLE seckill_compensation_record");
        runMigration();
        assertEquals(3, reliabilityTableCount());

        resetLegacySchema();
        runMigration();
        execute("DROP TABLE seckill_reconciliation_anomaly");
        runMigration();
        assertEquals(3, reliabilityTableCount());

        resetLegacySchema();
        runMigration();
        execute("DROP TABLE seckill_message_log");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("DROP TABLE seckill_compensation_record");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("DROP TABLE seckill_message_log");
        execute("DROP TABLE seckill_compensation_record");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("同列数错误类型、默认值和同名错误 CHECK 均被精确预检阻断")
    void wrongExistingDefinitionsAreRejectedExactly() throws Exception {
        execute("ALTER TABLE seckill_order DROP INDEX idx_seckill_order_number");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_message_log MODIFY publish_attempt BIGINT NOT NULL DEFAULT 0");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_message_log DROP INDEX uk_seckill_message_id");
        execute("ALTER TABLE seckill_message_log ADD UNIQUE KEY uk_seckill_message_id(message_id(10))");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_message_log ALTER COLUMN payload SET INVISIBLE");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_compensation_record ALTER COLUMN status SET DEFAULT 'SUCCEEDED'");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_reconciliation_anomaly DROP CHECK chk_seckill_anomaly_counts");
        execute("ALTER TABLE seckill_reconciliation_anomaly ADD CONSTRAINT chk_seckill_anomaly_counts "
                + "CHECK (occurrence_count>=0)");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_message_log ADD CONSTRAINT chk_unexpected CHECK (publish_attempt<5)");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("ALTER TABLE seckill_message_log DROP CHECK chk_seckill_message_domains");
        execute("ALTER TABLE seckill_message_log ADD CONSTRAINT chk_seckill_message_domains "
                + "CHECK (message_type IN ('ORDER_CREATE','ORDER_TIMEOUT','BUSINESS_DEAD_LETTER','INVALID_MESSAGE') OR 1=1)");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_ORDER_CREATE:9001','ORDER_CREATE','INITIAL','9001',7,19,'{}','e','r','PREPARED')");
        assertThrows(SQLException.class,
                () -> execute("UPDATE seckill_message_log SET status='BROKEN_STATE'"));
        runMigration();
    }

    @Test
    @DisplayName("迁移保留 order_number 字符集排序和非空契约")
    void preservesOrderNumberCollationAndNullability() throws Exception {
        execute("DROP TABLE seckill_order");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,"
                + "coupon_id BIGINT NOT NULL,order_number VARCHAR(50) CHARACTER SET utf8mb3 "
                + "COLLATE utf8mb3_general_ci NULL COMMENT '订单号',status INT NOT NULL DEFAULT 1,"
                + "create_time DATETIME,pay_time DATETIME,"
                + "UNIQUE KEY idx_seckill_order_number(order_number)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status) VALUES(7,19,'9001',1)");

        runMigration();

        assertEquals("NO", scalarString("SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_order' AND column_name='order_number'"));
        assertEquals("utf8mb3_general_ci", scalarString("SELECT collation_name FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_order' AND column_name='order_number'"));
        assertEquals("订单号", scalarString("SELECT column_comment FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_order' AND column_name='order_number'"));
    }

    @Test
    @DisplayName("迁移重跑拒绝身份、callback 位和消费终态 attempt 脏事实")
    void dirtyMessageIdentityAndTerminalFactsAreRejected() throws Exception {
        runMigration();
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status,returned) VALUES "
                + "('WRONG:9001','ORDER_CREATE','INITIAL','9001',7,19,'{}','e','r','CONSUMED',2)");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,user_id,"
                + "coupon_id,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_ORDER_CREATE:9001','ORDER_CREATE','INITIAL','9001',7,19,'{}','e','r','BROKER_ACKED')");
        execute("INSERT INTO seckill_message_log(message_id,message_type,publish_purpose,business_key,"
                + "source_message_id,source_message_id_hash,payload,exchange_name,routing_key,status) VALUES "
                + "('SECKILL_DEAD:SECKILL_ORDER_CREATE:9001','BUSINESS_DEAD_LETTER','DEAD_LETTER',"
                + "'SECKILL_ORDER_CREATE:9001','SECKILL_ORDER_CREATE:9001',REPEAT('a',64),'{}','e','r','PREPARED')");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("补偿非运行态残留 lease 或终态残留 retry 时间必须阻断重跑")
    void dirtyCompensationLeaseAndTerminalRetryAreRejected() throws Exception {
        runMigration();
        execute("INSERT INTO seckill_compensation_record(compensation_action,order_number,user_id,coupon_id,"
                + "first_reason,last_reason,evidence_mask,status,next_retry_at,locked_by,locked_until) VALUES "
                + "('RELEASE_RESERVATION','9002',7,19,'TEST','TEST',1,'PENDING',NOW(3),'stale-owner',NOW(3))");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        runMigration();
        execute("INSERT INTO seckill_compensation_record(compensation_action,order_number,user_id,coupon_id,"
                + "first_reason,last_reason,evidence_mask,status,next_retry_at,redis_applied_at,completed_at) VALUES "
                + "('RELEASE_RESERVATION','9002',7,19,'TEST','TEST',1,'SUCCEEDED',NOW(3),NOW(3),NOW(3))");
        assertEquals("45000", assertThrows(SQLException.class,
                B6ReliabilityMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    private static void runMigration() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_seckill_mq_reliability.sql");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = connection(); Statement jdbc = connection.createStatement()) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
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

    private static Map<String, Object> datasourceSettings() throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "datasource");
        }
    }

    private static Path configPath() {
        String configured = System.getProperty("b6.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b6.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("B6 config is missing");
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) throw new IllegalStateException("missing config section " + key);
        return (Map<String, Object>) child;
    }

    private static String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) throw new IllegalStateException("missing config value " + key);
        return String.valueOf(result);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(schemaUrl, username, password);
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

    private static int reliabilityTableCount() throws Exception {
        return scalarInt("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                + "AND table_name IN ('seckill_message_log','seckill_compensation_record',"
                + "'seckill_reconciliation_anomaly')");
    }

    private static String scalarString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B6 migration schema name");
        }
    }
}
