package com.fashion.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B8 真实 MySQL 商品一致性迁移矩阵")
class B8ProductCacheMigrationMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b8_migration_[0-9a-f]{32}";

    private String adminUrl;
    private String username;
    private String password;
    private String schema;
    private String schemaUrl;

    @BeforeAll
    void connectToLoopbackMysql() throws Exception {
        Map<String, Object> datasource = B8IntegrationSettings.section("datasource");
        String host = B8IntegrationSettings.value(datasource, "host");
        B8IntegrationSettings.requireLoopback(host, "MySQL");
        username = B8IntegrationSettings.value(datasource, "username");
        password = B8IntegrationSettings.value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + B8IntegrationSettings.value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT VERSION()")) {
            assertTrue(version.next());
            assertTrue(version.getString(1).startsWith("8.0."), "B8 requires MySQL 8.0.x");
        }
    }

    @AfterEach
    void dropExactTemporarySchema() throws Exception {
        if (schema == null) {
            return;
        }
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + schema + "`");
        } finally {
            schema = null;
            schemaUrl = null;
        }
    }

    @Test
    @DisplayName("首次执行和重复执行归一化 sales 并保持同一事实")
    void firstAndRepeatedMigrationAreIdempotent() throws Exception {
        createLegacySchema();
        execute("INSERT INTO product(id,status,sales) VALUES(1,1,NULL),(2,0,7)");

        B8MigrationRunner.run(schemaUrl, username, password);
        long version = scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1");
        B8MigrationRunner.run(schemaUrl, username, password);

        assertEquals(version, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product WHERE sales IS NULL"));
        assertEquals("1:ACTIVE:" + version + ",2:INACTIVE:" + version,
                scalarString("SELECT GROUP_CONCAT(CONCAT(product_id,':',item_state,':',item_version) "
                        + "ORDER BY product_id) FROM product_catalog_revision"));
        assertEquals(4, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name IN "
                + "('product_catalog_state','product_catalog_revision','product_projection_task',"
                + "'product_projection_reconcile_run')"));
    }

    @Test
    @DisplayName("仅精确且为空的正向表前缀可以恢复")
    void exactEmptyForwardPrefixesAreRecoverable() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("TRUNCATE product_projection_reconcile_run");
        execute("TRUNCATE product_projection_task");
        execute("TRUNCATE product_catalog_revision");
        execute("TRUNCATE product_catalog_state");

        for (String lastTable : new String[]{"product_catalog_state", "product_catalog_revision", "product_projection_task"}) {
            dropTablesAfter(lastTable);
            B8MigrationRunner.run(schemaUrl, username, password);
            assertEquals(4, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name IN "
                    + "('product_catalog_state','product_catalog_revision','product_projection_task',"
                    + "'product_projection_reconcile_run')"));
            execute("TRUNCATE product_projection_reconcile_run");
            execute("TRUNCATE product_projection_task");
            execute("TRUNCATE product_catalog_revision");
            execute("TRUNCATE product_catalog_state");
        }
    }

    @Test
    @DisplayName("错误或部分对象定义在任何继续 DDL 前阻断")
    void wrongOrPartialDefinitionsAreRejectedBeforeFurtherDdl() throws Exception {
        createLegacySchema();
        execute("CREATE TABLE product_catalog_state(id TINYINT UNSIGNED NOT NULL,"
                + "list_version VARCHAR(32) NOT NULL,updated_at DATETIME(3) NOT NULL,"
                + "PRIMARY KEY(id),CHECK(id=1),CHECK(length(list_version)>0)) ENGINE=InnoDB");

        SQLException failure = assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password));
        assertEquals("45000", failure.getSQLState());
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'"));
    }

    @Test
    @DisplayName("负销量脏数据在创建 B8 状态表前阻断")
    void dirtyProductDataIsRejectedBeforeDdl() throws Exception {
        createLegacySchema();
        execute("INSERT INTO product(id,status,sales) VALUES(1,1,-1)");

        SQLException failure = assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password));
        assertEquals("45000", failure.getSQLState());
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='product_catalog_state'"));
    }

    @Test
    @DisplayName("完整表中的 payload 哈希不匹配会阻断重复迁移")
    void payloadHashMismatchIsRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        long version = scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1");
        execute("INSERT INTO product_projection_task(target,product_id,catalog_version,operation,payload,"
                + "payload_sha256,status,attempt_count,claim_count,repair_count) VALUES"
                + "('REDIS',1," + version + ",'PUBLISH','{}',"
                + "'0000000000000000000000000000000000000000000000000000000000000000',"
                + "'PENDING',0,0,0)");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("同数量但定义被弱化的 CHECK 约束会阻断重复迁移")
    void weakenedCheckDefinitionIsRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("ALTER TABLE product_projection_task DROP CHECK chk_product_projection_version");
        execute("ALTER TABLE product_projection_task ADD CONSTRAINT chk_product_projection_version "
                + "CHECK(catalog_version>0)");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("额外列或缺失恢复索引会被完整元数据签名阻断")
    void extraColumnOrMissingRecoveryIndexIsRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("ALTER TABLE product_catalog_state ADD COLUMN unexpected_flag INT NULL");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());

        execute("ALTER TABLE product_catalog_state DROP COLUMN unexpected_flag");
        execute("ALTER TABLE product_projection_task DROP INDEX idx_product_projection_recovery");
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("同数量但弱化的 revision/reconcile CHECK 会被阻断")
    void weakenedRevisionAndReconcileChecksAreRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("ALTER TABLE product_catalog_revision DROP CHECK chk_product_revision_state");
        execute("ALTER TABLE product_catalog_revision ADD CONSTRAINT chk_product_revision_state "
                + "CHECK(item_state IS NOT NULL)");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());

        execute("ALTER TABLE product_catalog_revision DROP CHECK chk_product_revision_state");
        execute("ALTER TABLE product_catalog_revision ADD CONSTRAINT chk_product_revision_state "
                + "CHECK(BINARY item_state IN ('ACTIVE','INACTIVE','DELETED'))");
        execute("ALTER TABLE product_projection_reconcile_run DROP CHECK chk_product_reconcile_domain");
        execute("ALTER TABLE product_projection_reconcile_run ADD CONSTRAINT chk_product_reconcile_domain "
                + "CHECK(status IS NOT NULL)");
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("同 token 但 OR 1=1 或额外 domain 的弱化约束必须阻断")
    void tokenPreservingWeakenedChecksAreRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("ALTER TABLE product_catalog_state DROP CHECK chk_product_catalog_singleton");
        execute("ALTER TABLE product_catalog_state ADD CONSTRAINT chk_product_catalog_singleton "
                + "CHECK(id=1 OR id=2)");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());

        execute("ALTER TABLE product_catalog_state DROP CHECK chk_product_catalog_singleton");
        execute("ALTER TABLE product_catalog_state ADD CONSTRAINT chk_product_catalog_singleton CHECK(id=1)");
        execute("ALTER TABLE product_catalog_revision DROP CHECK chk_product_revision_state");
        execute("ALTER TABLE product_catalog_revision ADD CONSTRAINT chk_product_revision_state "
                + "CHECK(BINARY item_state IN ('ACTIVE','INACTIVE','DELETED','EXTRA'))");
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("错误 default 或 generated expression 必须阻断")
    void wrongDefaultOrGeneratedExpressionIsRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("ALTER TABLE product_projection_task ALTER COLUMN status SET DEFAULT 'RETRY_WAIT'");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());

        execute("ALTER TABLE product_projection_task ALTER COLUMN status SET DEFAULT 'PENDING'");
        execute("ALTER TABLE product_projection_reconcile_run DROP INDEX uk_product_reconcile_active");
        execute("ALTER TABLE product_projection_reconcile_run DROP COLUMN active_slot");
        execute("ALTER TABLE product_projection_reconcile_run ADD COLUMN active_slot TINYINT "
                + "GENERATED ALWAYS AS (CASE WHEN status IN "
                + "('PENDING','RUNNING','RETRY_WAIT','SUCCEEDED') THEN 1 ELSE NULL END) STORED");
        execute("ALTER TABLE product_projection_reconcile_run ADD UNIQUE KEY "
                + "uk_product_reconcile_active(active_slot)");
        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
    }

    @Test
    @DisplayName("迁移生成的 CHECK 与 generated expression 具有稳定规范化签名")
    void canonicalConstraintExpressionsHaveStableSignatures() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);

        String signatures = scalarString("SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                + "check_clause,' ',''),'`',''),'_utf8mb4',''),'cast(',''),'ascharcharsetbinary)',''),'\\\\','')) "
                + "FROM information_schema.check_constraints WHERE constraint_schema=DATABASE() "
                + "AND constraint_name='chk_product_reconcile_domain'");
        String generated = scalarString("SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE("
                + "generation_expression,' ',''),'`',''),'_utf8mb4',''),'\\\\','')) "
                + "FROM information_schema.columns WHERE table_schema=DATABASE() "
                + "AND table_name='product_projection_reconcile_run' AND column_name='active_slot'");

        assertEquals("(casewhen(statusin('pending','running','retry_wait'))"
                + "then1elsenullend)", generated);
        assertEquals("((modein('cutover','periodic'))and(phasein('mysql_scan','es_scan','verify'))"
                + "and(statusin('pending','running','retry_wait','succeeded','failed_terminal')))", signatures);
    }

    @Test
    @DisplayName("完整表缺少 singleton 时即使 revision 非空也不得自动补 seed")
    void missingSingletonInCompleteNonEmptySchemaIsRejected() throws Exception {
        createLegacySchema();
        B8MigrationRunner.run(schemaUrl, username, password);
        execute("DELETE FROM product_catalog_state");
        execute("INSERT INTO product(id,status,sales) VALUES(1,1,0)");
        execute("INSERT INTO product_catalog_revision(product_id,item_version,item_state) "
                + "VALUES(1,2,'ACTIVE')");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product_catalog_state"));
    }

    @Test
    @DisplayName("非空的正向迁移前缀必须阻断")
    void nonEmptyForwardPrefixIsRejected() throws Exception {
        createLegacySchema();
        execute("CREATE TABLE product_catalog_state("
                + "id TINYINT UNSIGNED NOT NULL,list_version BIGINT UNSIGNED NOT NULL,"
                + "updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),"
                + "PRIMARY KEY(id),CONSTRAINT chk_product_catalog_singleton CHECK(id=1),"
                + "CONSTRAINT chk_product_catalog_version CHECK(list_version BETWEEN 1 AND 9007199254740991)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
        execute("INSERT INTO product_catalog_state(id,list_version) VALUES(1,1)");

        assertEquals("45000", assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password)).getSQLState());
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='product_catalog_revision'"));
    }

    @Test
    @DisplayName("逆序的部分迁移对象必须在任何继续 DDL 前阻断")
    void reversePartialOrderIsRejectedBeforeFurtherDdl() throws Exception {
        createLegacySchema();
        execute("CREATE TABLE product_catalog_revision(product_id BIGINT PRIMARY KEY) ENGINE=InnoDB");

        SQLException failure = assertThrows(SQLException.class,
                () -> B8MigrationRunner.run(schemaUrl, username, password));

        assertEquals("45000", failure.getSQLState());
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='product_catalog_state'"));
    }

    private void createLegacySchema() throws Exception {
        schema = "fsm_b8_migration_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        execute("CREATE TABLE product(id BIGINT NOT NULL,status INT NOT NULL,"
                + "sales INT NULL DEFAULT 0,PRIMARY KEY(id)) ENGINE=InnoDB");
    }

    private void dropTablesAfter(String lastTable) throws Exception {
        if ("product_catalog_state".equals(lastTable)) {
            execute("DROP TABLE product_projection_reconcile_run,product_projection_task,product_catalog_revision");
        } else if ("product_catalog_revision".equals(lastTable)) {
            execute("DROP TABLE product_projection_reconcile_run,product_projection_task");
        } else if ("product_projection_task".equals(lastTable)) {
            execute("DROP TABLE product_projection_reconcile_run");
        } else {
            throw new IllegalArgumentException("unsafe B8 prefix");
        }
    }

    private void execute(String sql) throws Exception {
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long scalarLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
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

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B8 migration schema");
        }
    }
}
