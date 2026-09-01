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

@EnabledIfSystemProperty(named = "b5.integration", matches = "true")
@DisplayName("B5 MySQL 8 秒杀 schema 与迁移门禁")
class SeckillStateMigrationMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b5_migration_it_[0-9a-f]{32}";
    private static String adminUrl;
    private static String schemaUrl;
    private static String username;
    private static String password;
    private static String schema;

    @BeforeAll
    static void createSchema() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b5_migration_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(version.next());
                assertTrue(Integer.parseInt(version.getString(1).split("\\.")[0]) >= 8);
            }
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
    }

    @AfterAll
    static void dropSchema() throws Exception {
        if (schema != null) {
            validateSchema(schema);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void resetLegacySchema() throws Exception {
        execute("DROP TABLE IF EXISTS seckill_order");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, "
                + "coupon_id BIGINT NOT NULL, order_number VARCHAR(64) NOT NULL, status INT DEFAULT 1, "
                + "create_time DATETIME, pay_time DATETIME, UNIQUE KEY idx_order_number(order_number), "
                + "UNIQUE KEY idx_user_coupon(user_id,coupon_id)) ENGINE=InnoDB");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (7,19,'LEGACY-1',1,NOW())");
    }

    @Test
    @DisplayName("迁移首次执行和重跑成功且仅取消态允许重新参与")
    void migrationIsIdempotentAndReleasesOnlyCancelledOrders() throws Exception {
        runMigration();
        runMigration();

        assertThrows(SQLException.class, () -> execute("INSERT INTO seckill_order"
                + "(user_id,coupon_id,order_number,status,create_time) VALUES (7,19,'DUP-1',1,NOW())"));
        execute("UPDATE seckill_order SET status=3 WHERE order_number='LEGACY-1'");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (7,19,'ACTIVE-2',1,NOW())");
        assertThrows(SQLException.class, () -> execute("INSERT INTO seckill_order"
                + "(user_id,coupon_id,order_number,status,create_time) VALUES (7,19,'DUP-2',2,NOW())"));
        execute("UPDATE seckill_order SET status=3 WHERE order_number='ACTIVE-2'");
        execute("INSERT INTO seckill_order(user_id,coupon_id,order_number,status,create_time) "
                + "VALUES (7,19,'ACTIVE-3',2,NOW())");

        assertEquals(3, scalarInt("SELECT COUNT(*) FROM seckill_order WHERE user_id=7 AND coupon_id=19"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO seckill_order"
                + "(user_id,coupon_id,order_number,status,create_time) VALUES (8,19,'BAD-4',4,NOW())"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO seckill_order"
                + "(user_id,coupon_id,order_number,status,create_time) VALUES (8,20,'BAD-NULL',NULL,NOW())"));
    }

    @Test
    @DisplayName("NULL、未知状态和错误旧索引均在迁移前显式阻断")
    void migrationRejectsDirtyStateAndWrongLegacyDefinition() throws Exception {
        execute("UPDATE seckill_order SET status=NULL");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        execute("UPDATE seckill_order SET status=4");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        execute("ALTER TABLE seckill_order DROP INDEX idx_user_coupon");
        execute("ALTER TABLE seckill_order ADD UNIQUE INDEX idx_user_coupon(coupon_id,user_id)");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("部分迁移和同名错误 B5 定义均显式阻断")
    void migrationRejectsPartialAndWrongB5Definitions() throws Exception {
        execute("ALTER TABLE seckill_order ADD COLUMN active_marker TINYINT "
                + "GENERATED ALWAYS AS (CASE WHEN status=3 THEN NULL ELSE 1 END) STORED");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        execute("ALTER TABLE seckill_order MODIFY COLUMN status INT NOT NULL DEFAULT 1");
        execute("ALTER TABLE seckill_order ADD CONSTRAINT chk_seckill_order_status_b5 "
                + "CHECK (status IN (1,2,3))");
        execute("ALTER TABLE seckill_order ADD COLUMN active_marker TINYINT "
                + "GENERATED ALWAYS AS (CASE WHEN status=2 THEN NULL ELSE 1 END) STORED");
        execute("ALTER TABLE seckill_order ADD UNIQUE INDEX uk_seckill_order_active_user_coupon"
                + "(user_id,coupon_id,active_marker)");
        execute("ALTER TABLE seckill_order DROP INDEX idx_user_coupon");
        SQLException failure = assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration);
        assertEquals("45000", failure.getSQLState());
        assertTrue(failure.getMessage().contains("active_marker definition mismatch"));
    }

    @Test
    @DisplayName("伪装成 B5 的普通列、VIRTUAL 列和未强制 CHECK 均显式阻断")
    void migrationRejectsInvalidGeneratedColumnAndUnenforcedCheckShapes() throws Exception {
        installB5Shape("TINYINT NULL", "CHECK (status IN (1,2,3))");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        installB5Shape("TINYINT GENERATED ALWAYS AS "
                + "(CASE WHEN status=3 THEN NULL ELSE 1 END) VIRTUAL",
                "CHECK (status IN (1,2,3))");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());

        resetLegacySchema();
        installB5Shape("TINYINT GENERATED ALWAYS AS "
                + "(CASE WHEN status=3 THEN NULL ELSE 1 END) STORED",
                "CHECK (status IN (1,2,3)) NOT ENFORCED");
        assertEquals("45000", assertThrows(SQLException.class,
                SeckillStateMigrationMysqlIntegrationTest::runMigration).getSQLState());
    }

    @Test
    @DisplayName("更新后的 final07 秒杀 DDL 与带列名 dump 可从零真实导入")
    void cleanBaselineDdlAndDumpExecuteSuccessfully() throws Exception {
        execute("DROP TABLE seckill_order");
        String baseline = new String(Files.readAllBytes(Paths.get("..", "..", "mysql", "final07.sql")),
                StandardCharsets.UTF_8);
        execute(extractCreate(baseline));
        for (String insert : extractSeckillOrderInserts(baseline)) {
            execute(insert);
        }

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM seckill_order"));
        assertEquals(1, scalarInt("SELECT active_marker FROM seckill_order LIMIT 1"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_order' "
                + "AND index_name='uk_seckill_order_active_user_coupon'"));
        assertThrows(SQLException.class, () -> execute("INSERT INTO seckill_order"
                + "(user_id,coupon_id,order_number,status,create_time) VALUES (20,3,'CLEAN-DUP',1,NOW())"));
    }

    @Test
    @DisplayName("升级库与干净库的 B5 关键元数据等价")
    void migratedAndCleanSchemaHaveEquivalentB5Metadata() throws Exception {
        runMigration();
        String migrated = metadataFingerprint();

        execute("DROP TABLE seckill_order");
        String baseline = new String(Files.readAllBytes(Paths.get("..", "..", "mysql", "final07.sql")),
                StandardCharsets.UTF_8);
        execute(extractCreate(baseline));

        assertEquals(migrated, metadataFingerprint());
    }

    private static String metadataFingerprint() throws Exception {
        return scalarString("SELECT CONCAT("
                + "(SELECT CONCAT(column_type,':',is_nullable,':',column_default) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='seckill_order' AND column_name='status'), '|', "
                + "(SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(generation_expression,'`',''),' ',''),'(',''),')','')) "
                + "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='seckill_order' "
                + "AND column_name='active_marker'), '|', "
                + "(SELECT GROUP_CONCAT(CONCAT(column_name,':',non_unique) ORDER BY seq_in_index) "
                + "FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='seckill_order' "
                + "AND index_name='uk_seckill_order_active_user_coupon'), '|', "
                + "(SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(check_clause,'`',''),' ',''),'(',''),')','')) "
                + "FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='chk_seckill_order_status_b5'))");
    }

    private static void installB5Shape(String activeMarkerDefinition, String checkDefinition) throws Exception {
        execute("ALTER TABLE seckill_order MODIFY COLUMN status INT NOT NULL DEFAULT 1");
        execute("ALTER TABLE seckill_order ADD CONSTRAINT chk_seckill_order_status_b5 " + checkDefinition);
        execute("ALTER TABLE seckill_order ADD COLUMN active_marker " + activeMarkerDefinition);
        execute("ALTER TABLE seckill_order ADD UNIQUE INDEX uk_seckill_order_active_user_coupon"
                + "(user_id,coupon_id,active_marker)");
        execute("ALTER TABLE seckill_order DROP INDEX idx_user_coupon");
    }

    private static String extractCreate(String baseline) {
        String marker = "CREATE TABLE `seckill_order`";
        int start = baseline.indexOf(marker);
        int end = baseline.indexOf(';', start);
        assertTrue(start >= 0 && end > start);
        return baseline.substring(start, end);
    }

    private static List<String> extractSeckillOrderInserts(String baseline) {
        java.util.ArrayList<String> inserts = new java.util.ArrayList<>();
        for (String line : baseline.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("INSERT INTO `seckill_order`")) {
                inserts.add(trimmed.substring(0, trimmed.length() - 1));
            }
        }
        assertTrue(!inserts.isEmpty());
        return inserts;
    }

    private static void runMigration() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_seckill_state_inventory.sql");
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

    private static Map<String, Object> datasourceSettings() throws Exception {
        Path config = configPath();
        try (InputStream input = Files.newInputStream(config)) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "datasource");
        }
    }

    private static Path configPath() {
        String configured = System.getProperty("b5.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b5.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B5 config is missing");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) {
            throw new IllegalStateException("missing config section " + key);
        }
        return (Map<String, Object>) child;
    }

    private static String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) {
            throw new IllegalStateException("missing config value " + key);
        }
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

    private static String scalarString(String sql) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B5 migration schema name");
        }
    }
}
