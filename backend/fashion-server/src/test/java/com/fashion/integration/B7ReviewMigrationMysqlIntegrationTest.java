package com.fashion.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b7.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B7 真实 MySQL 评价迁移矩阵")
class B7ReviewMigrationMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b7_review_migration_[0-9a-f]{32}";

    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;

    @BeforeAll
    void createSchema() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        String host = value(datasource, "host");
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host))) {
            throw new IllegalStateException("B7 migration test refuses non-loopback MySQL");
        }
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT VERSION()")) {
            assertTrue(version.next());
            assertTrue(version.getString(1).startsWith("8.0."));
        }
        schema = "fsm_b7_review_migration_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
    }

    @AfterAll
    void dropSchema() throws Exception {
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
        execute("DROP TABLE IF EXISTS review_baseline_final");
        execute("DROP TABLE IF EXISTS review_baseline_standalone");
        execute("DROP TABLE IF EXISTS review");
        execute("DROP TABLE IF EXISTS order_detail");
        execute("DROP TABLE IF EXISTS orders");
        execute("DROP TABLE IF EXISTS product");
        execute("CREATE TABLE orders(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE product(id BIGINT PRIMARY KEY) ENGINE=InnoDB");
        execute("CREATE TABLE order_detail(id BIGINT PRIMARY KEY AUTO_INCREMENT,order_id BIGINT NOT NULL,product_id BIGINT NOT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE review(id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,"
                + "order_id BIGINT NOT NULL,product_id BIGINT NOT NULL) ENGINE=InnoDB");
        execute("INSERT INTO orders(id,user_id) VALUES(10,7),(11,8)");
        execute("INSERT INTO product(id) VALUES(20),(21),(22)");
        execute("INSERT INTO order_detail(order_id,product_id) VALUES(10,20),(10,21),(11,20)");
    }

    @Test
    @DisplayName("合法 legacy 首次升级和重复执行得到同一正确唯一键")
    void cleanLegacyAndSecondRunSucceed() throws Exception {
        execute("INSERT INTO review(user_id,order_id,product_id) VALUES(7,10,20),(7,10,21)");

        B7MigrationRunner.run(schemaUrl, username, password);
        B7MigrationRunner.run(schemaUrl, username, password);

        assertEquals("order_id,product_id", scalarString(
                "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
                        + "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                        + "AND table_name='review' AND index_name='uk_review_order_product'"));
        assertEquals(0, scalarInt("SELECT MIN(non_unique) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='review' AND index_name='uk_review_order_product'"));
        assertEquals("1:order_id:0:NULL:BTREE:YES,2:product_id:0:NULL:BTREE:YES",
                uniqueIndexSignature("review"));

        createReviewBaseline("../../mysql/review_table.sql", "review_baseline_standalone");
        createReviewBaseline("../../mysql/final07.sql", "review_baseline_final");
        assertEquals(uniqueIndexSignature("review"), uniqueIndexSignature("review_baseline_standalone"));
        assertEquals(uniqueIndexSignature("review"), uniqueIndexSignature("review_baseline_final"));
    }

    @Test
    @DisplayName("同名非唯一、单列或不可见索引均显式阻断")
    void wrongOrPartialIndexDefinitionsAreRejected() throws Exception {
        assertWrongIndex("CREATE INDEX uk_review_order_product ON review(order_id)");
        resetLegacySchema();
        assertWrongIndex("CREATE INDEX uk_review_order_product ON review(order_id,product_id)");
        resetLegacySchema();
        execute("CREATE UNIQUE INDEX uk_review_order_product ON review(order_id,product_id)");
        execute("ALTER TABLE review ALTER INDEX uk_review_order_product INVISIBLE");
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B7MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("错误或部分评价引用列定义在 DDL 前阻断")
    void wrongOrPartialColumnDefinitionsAreRejected() throws Exception {
        execute("ALTER TABLE review MODIFY user_id BIGINT NULL");
        assertDefinitionRejected();
        resetLegacySchema();
        execute("ALTER TABLE review MODIFY order_id VARCHAR(20) NOT NULL");
        assertDefinitionRejected();
        resetLegacySchema();
        execute("ALTER TABLE review DROP COLUMN product_id");
        assertDefinitionRejected();
    }

    @Test
    @DisplayName("重复、NULL、孤立引用、归属错误和订单外商品在 DDL 前阻断")
    void dirtyDataMatrixIsRejectedBeforeDdl() throws Exception {
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(7,10,20),(7,10,20)", 2);
        resetLegacySchema();
        execute("ALTER TABLE review MODIFY order_id BIGINT NULL");
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(7,NULL,20)", 1);
        resetLegacySchema();
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(7,999,20)", 1);
        resetLegacySchema();
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(7,10,999)", 1);
        resetLegacySchema();
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(8,10,20)", 1);
        resetLegacySchema();
        assertDirty("INSERT INTO review(user_id,order_id,product_id) VALUES(7,10,22)", 1);
    }

    private void assertWrongIndex(String ddl) throws Exception {
        execute(ddl);
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B7MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    private void createReviewBaseline(String sourcePath, String targetTable) throws Exception {
        if (!("review_baseline_standalone".equals(targetTable)
                || "review_baseline_final".equals(targetTable))) {
            throw new IllegalArgumentException("unsafe baseline table");
        }
        String source = new String(Files.readAllBytes(Paths.get(sourcePath)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(
                "(?is)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`review`\\s*\\(.*?\\)\\s+ENGINE=InnoDB[^;]*;")
                .matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("review baseline DDL missing from " + sourcePath);
        }
        String ddl = matcher.group().replaceFirst(
                "(?is)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`review`",
                "CREATE TABLE `" + targetTable + "`");
        execute(ddl);
    }

    private String uniqueIndexSignature(String table) throws Exception {
        if (!("review".equals(table) || "review_baseline_standalone".equals(table)
                || "review_baseline_final".equals(table))) {
            throw new IllegalArgumentException("unsafe signature table");
        }
        return scalarString("SELECT GROUP_CONCAT(CONCAT(seq_in_index,':',column_name,':',non_unique,':',"
                + "COALESCE(sub_part,'NULL'),':',index_type,':',is_visible) "
                + "ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='" + table + "' "
                + "AND index_name='uk_review_order_product'");
    }

    private void assertDefinitionRejected() throws Exception {
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B7MigrationRunner.run(schemaUrl, username, password)).getSQLState());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='review' "
                + "AND index_name='uk_review_order_product'"));
    }

    private void assertDirty(String seed, int expectedRows) throws Exception {
        execute(seed);
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B7MigrationRunner.run(schemaUrl, username, password)).getSQLState());
        assertEquals(expectedRows, scalarInt("SELECT COUNT(*) FROM review"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='review' "
                + "AND index_name='uk_review_order_product'"));
    }

    private Map<String, Object> datasourceSettings() throws Exception {
        try (InputStream input = Files.newInputStream(configPath())) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), "datasource");
        }
    }

    private Path configPath() {
        String configured = System.getProperty("b7.mysql.config");
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException("b7.mysql.config is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B7 MySQL config is missing");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        if (!(child instanceof Map)) {
            throw new IllegalStateException("missing config section " + key);
        }
        return (Map<String, Object>) child;
    }

    private String value(Map<String, Object> values, String key) {
        Object result = values.get(key);
        if (result == null) {
            throw new IllegalStateException("missing config value " + key);
        }
        return String.valueOf(result);
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B7 migration schema");
        }
    }

    private void execute(String sql) throws Exception {
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
