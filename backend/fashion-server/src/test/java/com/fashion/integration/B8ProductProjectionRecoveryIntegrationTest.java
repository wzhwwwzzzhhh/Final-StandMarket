package com.fashion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.Product;
import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.mapper.ProductCatalogMapper;
import com.fashion.product.*;
import com.fashion.service.ProductService;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B8 真实 MySQL task→worker→ES→reconciliation 恢复链路")
class B8ProductProjectionRecoveryIntegrationTest {
    private static final String SCHEMA_PATTERN = "fsm_b8_recovery_[0-9a-f]{32}";

    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private String index;
    private AnnotationConfigApplicationContext context;
    private RestClient es;

    @BeforeAll
    void createIsolatedMysqlAndEsFacts() throws Exception {
        Map<String, Object> datasource = B8IntegrationSettings.section("datasource");
        String host = B8IntegrationSettings.value(datasource, "host");
        B8IntegrationSettings.requireLoopback(host, "MySQL");
        username = B8IntegrationSettings.value(datasource, "username");
        password = B8IntegrationSettings.value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + B8IntegrationSettings.value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b8_recovery_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(schema.matches(SCHEMA_PATTERN));
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        execute("CREATE TABLE product(id BIGINT NOT NULL AUTO_INCREMENT,name VARCHAR(100) NOT NULL,"
                + "description TEXT NULL,price DECIMAL(10,2) NOT NULL,stock INT NOT NULL,sales INT NULL DEFAULT 0,"
                + "image VARCHAR(500) NULL,category_id BIGINT NULL,status INT NOT NULL,tag VARCHAR(100) NULL,"
                + "create_time DATETIME(3) NULL,update_time DATETIME(3) NULL,create_user BIGINT NULL,"
                + "update_user BIGINT NULL,PRIMARY KEY(id)) ENGINE=InnoDB");
        B8MigrationRunner.run(schemaUrl, username, password);

        URI uri = URI.create(System.getProperty("b8.es-url"));
        B8IntegrationSettings.requireLoopback(uri.getHost(), "Elasticsearch");
        assertEquals("true", System.getProperty("b8.es-exclusive"));
        es = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())).build();
        index = "products_b8_recovery_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(index.matches("products_b8_recovery_[0-9a-f]{32}"));
        Request create = new Request("PUT", "/" + index);
        create.setJsonEntity("{\"settings\":{\"index.gc_deletes\":\"5m\"},\"mappings\":{\"properties\":{" +
                "\"id\":{\"type\":\"long\"},\"catalogVersion\":{\"type\":\"long\"}," +
                "\"projectionHash\":{\"type\":\"keyword\"}}}}");
        es.performRequest(create);

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b8.test.jdbc-url", schemaUrl);
        properties.put("b8.test.username", username);
        properties.put("b8.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b8Recovery", properties));
        context.register(B8ProductCatalogSpringMysqlIntegrationTest.SpringMysqlConfig.class);
        context.refresh();
    }

    @AfterAll
    void removeOnlyIsolatedFacts() throws Exception {
        if (context != null) context.close();
        if (es != null) {
            try {
                if (index != null && index.matches("products_b8_recovery_[0-9a-f]{32}")) {
                    es.performRequest(new Request("DELETE", "/" + index));
                }
            } finally {
                es.close();
            }
        }
        if (schema != null && schema.matches(SCHEMA_PATTERN)) {
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @Test
    void durableTaskDeliveryAndReconciliationRepairConvergeAfterInjectedDrift() throws Exception {
        Product product = new Product();
        product.setName("恢复测试商品");
        product.setDescription("description");
        product.setPrice(new BigDecimal("88.00"));
        product.setStock(5);
        product.setSales(0);
        product.setStatus(1);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        assertTrue(context.getBean(ProductService.class).save(product));

        ProductCatalogMapper mapper = context.getBean(ProductCatalogMapper.class);
        ProductProjectionProperties properties = new ProductProjectionProperties();
        properties.setIndexName(index);
        ProductProjectionMetrics metrics = new ProductProjectionMetrics();
        MybatisProductProjectionTaskRepository tasks =
                new MybatisProductProjectionTaskRepository(mapper, properties, metrics);
        ElasticsearchProductProjectionDelivery delivery =
                new ElasticsearchProductProjectionDelivery(es, new ObjectMapper(), properties);
        ProductProjectionWorker projectionWorker = new ProductProjectionWorker(
                tasks, Collections.singletonList(delivery), mapper, properties, Runnable::run);

        assertTrue(projectionWorker.processOne("ES"));
        ElasticsearchProductProjectionInventory inventory =
                new ElasticsearchProductProjectionInventory(es, new ObjectMapper(), properties);
        IndexedProductProjection original = inventory.read(product.getId());
        assertNotNull(original);

        Request corrupt = new Request("PUT", "/" + index + "/_doc/" + product.getId());
        corrupt.addParameter("version", Long.toString(original.getCatalogVersion()));
        corrupt.addParameter("version_type", "external_gte");
        corrupt.setJsonEntity("{\"id\":" + product.getId() + ",\"catalogVersion\":"
                + original.getCatalogVersion() + ",\"projectionHash\":\"injected-drift\"}");
        es.performRequest(corrupt);

        ProductReconciliationService reconciliation = new ProductReconciliationService(
                mapper, inventory, productId -> false, productId -> null, properties, metrics);
        MybatisProductReconcileRunRepository runs =
                new MybatisProductReconcileRunRepository(mapper, properties);
        ProductReconciliationWorker reconcileWorker =
                new ProductReconciliationWorker(runs, reconciliation, properties);
        reconciliation.start("CUTOVER");

        ProductProjectionReconcileRun latest = null;
        for (int i = 0; i < 20; i++) {
            reconcileWorker.poll();
            projectionWorker.processOne("ES");
            latest = mapper.readLatestReconcileRun();
            if (latest != null && "SUCCEEDED".equals(latest.getStatus())) break;
        }

        assertNotNull(latest);
        assertEquals("SUCCEEDED", latest.getStatus());
        assertTrue(latest.getDriftCount() >= 1);
        assertTrue(latest.getRepairCount() >= 1);
        IndexedProductProjection repaired = inventory.read(product.getId());
        assertEquals(original.getProjectionHash(), repaired.getProjectionHash());
        assertTrue(metrics.count("reconcile.drift_detected") >= 1);
    }

    private void execute(String sql) throws Exception {
        if (schema == null || !schema.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B8 recovery schema");
        }
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
