package com.fashion.integration;

import com.fashion.entity.Product;
import com.fashion.entity.ProductProjectionTask;
import com.fashion.entity.ProductProjectionStatusSummary;
import com.fashion.mapper.ProductCatalogMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.product.AfterCommitRegistrar;
import com.fashion.product.MybatisProductProjectionTaskRepository;
import com.fashion.product.ProductCatalogMutationCoordinator;
import com.fashion.product.ProductProjectionDelivery;
import com.fashion.product.ProductProjectionMetrics;
import com.fashion.product.ProductProjectionProperties;
import com.fashion.product.ProductProjectionReadyEvent;
import com.fashion.product.ProductProjectionTaskRepository;
import com.fashion.product.ProductProjectionWorker;
import com.fashion.product.SpringAfterCommitRegistrar;
import com.fashion.service.ProductService;
import com.fashion.service.impl.ProductServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B8 Spring/MyBatis/MySQL 商品目录事务门禁")
class B8ProductCatalogSpringMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b8_spring_[0-9a-f]{32}";

    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private ProductService productService;
    private final List<ProductProjectionReadyEvent> readyEvents = new CopyOnWriteArrayList<>();

    @BeforeAll
    void createSchemaAndRealSpringContext() throws Exception {
        Map<String, Object> datasource = B8IntegrationSettings.section("datasource");
        String host = B8IntegrationSettings.value(datasource, "host");
        B8IntegrationSettings.requireLoopback(host, "MySQL");
        username = B8IntegrationSettings.value(datasource, "username");
        password = B8IntegrationSettings.value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + B8IntegrationSettings.value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b8_spring_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        createProductTable();
        B8MigrationRunner.run(schemaUrl, username, password);

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b8.test.jdbc-url", schemaUrl);
        properties.put("b8.test.username", username);
        properties.put("b8.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b8Mysql", properties));
        context.addApplicationListener(event -> {
            if (event instanceof PayloadApplicationEvent
                    && ((PayloadApplicationEvent<?>) event).getPayload() instanceof ProductProjectionReadyEvent) {
                readyEvents.add((ProductProjectionReadyEvent) ((PayloadApplicationEvent<?>) event).getPayload());
            }
        });
        context.register(SpringMysqlConfig.class);
        context.refresh();
        productService = context.getBean(ProductService.class);
    }

    @BeforeEach
    void resetFacts() throws Exception {
        execute("DROP TRIGGER IF EXISTS fail_b8_version");
        execute("DROP TRIGGER IF EXISTS fail_b8_revision");
        execute("DROP TRIGGER IF EXISTS fail_b8_redis_task");
        execute("DROP TRIGGER IF EXISTS fail_b8_es_task");
        execute("DELETE FROM product_projection_reconcile_run");
        execute("DELETE FROM product_projection_task");
        execute("DELETE FROM product_catalog_revision");
        execute("DELETE FROM product");
        execute("UPDATE product_catalog_state SET list_version=100 WHERE id=1");
        readyEvents.clear();
    }

    @AfterAll
    void closeAndDropExactSchema() throws Exception {
        if (context != null) {
            context.close();
        }
        if (schema != null) {
            validateSchema(schema);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @Test
    @DisplayName("真实事务代理在提交中原子写商品、版本、revision、双投影任务，提交后才发布事件")
    void committedSaveCreatesOneCatalogFactAndAfterCommitWakeup() throws Exception {
        assertTrue(AopUtils.isAopProxy(productService));
        Product product = product("外套😀", 8, null);

        assertTrue(productService.save(product));

        assertEquals(1, scalarLong("SELECT COUNT(*) FROM product WHERE id=" + product.getId()
                + " AND image='https://example.invalid/a.jpg' AND sales=0"));
        assertEquals(101, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals("ACTIVE:101", scalarString("SELECT CONCAT(item_state,':',item_version) "
                + "FROM product_catalog_revision WHERE product_id=" + product.getId()));
        assertEquals("ES:UPSERT:1,REDIS:PUBLISH:1", scalarString(
                "SELECT GROUP_CONCAT(CONCAT(target,':',operation,':',fact_count) ORDER BY target) FROM "
                        + "(SELECT target,operation,COUNT(*) fact_count FROM product_projection_task "
                        + "WHERE product_id=" + product.getId() + " GROUP BY target,operation) facts"));
        assertEquals(1, readyEvents.size());
        assertEquals(product.getId().longValue(), readyEvents.get(0).getProductId());
        assertEquals(101, readyEvents.get(0).getCatalogVersion());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("catalogMutationFailureStages")
    @DisplayName("任一目录一致性事实写入失败都回滚整个真实 Spring 事务")
    void everyCatalogFactFailureRollsBackEntireMutation(String stage, String triggerSql) throws Exception {
        execute(triggerSql);

        assertThrows(RuntimeException.class, () -> productService.save(product("回滚款-" + stage, 3, 0)));

        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product"));
        assertEquals(100, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product_catalog_revision"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product_projection_task"));
        assertTrue(readyEvents.isEmpty());
    }

    static Stream<Arguments> catalogMutationFailureStages() {
        return Stream.of(
                Arguments.of("version", "CREATE TRIGGER fail_b8_version BEFORE UPDATE ON product_catalog_state "
                        + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected version failure'"),
                Arguments.of("revision", "CREATE TRIGGER fail_b8_revision BEFORE INSERT ON product_catalog_revision "
                        + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected revision failure'"),
                Arguments.of("redis-task", "CREATE TRIGGER fail_b8_redis_task BEFORE INSERT ON product_projection_task "
                        + "FOR EACH ROW IF NEW.target='REDIS' THEN SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT='injected Redis task failure'; END IF"),
                Arguments.of("es-task", "CREATE TRIGGER fail_b8_es_task BEFORE INSERT ON product_projection_task "
                        + "FOR EACH ROW IF NEW.target='ES' THEN SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT='injected ES task failure'; END IF")
        );
    }

    @Test
    @DisplayName("纯库存更新不发布目录版本，混合更新只发布一次")
    void stockOnlyIsExcludedAndMixedMutationAdvancesExactlyOnce() throws Exception {
        execute("INSERT INTO product(id,name,description,price,stock,sales,image,category_id,status,tag,"
                + "create_time,update_time) VALUES(9,'旧名','d',10.00,8,0,'i',2,1,'t',NOW(3),NOW(3))");
        Product stockOnly = new Product();
        stockOnly.setId(9L);
        stockOnly.setStock(7);
        assertTrue(productService.update(stockOnly));
        assertEquals(100, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals(0, scalarLong("SELECT COUNT(*) FROM product_projection_task"));

        Product mixed = new Product();
        mixed.setId(9L);
        mixed.setStock(6);
        mixed.setName("新名");
        assertTrue(productService.update(mixed));
        assertEquals(101, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM product_projection_task WHERE product_id=9"));
        assertEquals("新名:6", scalarString("SELECT CONCAT(name,':',stock) FROM product WHERE id=9"));
        assertEquals(1, readyEvents.size());
    }

    @Test
    @DisplayName("已有 revision 的后续目录更新仍只推进一次并产生新版本任务")
    void secondCatalogMutationUpdatesExistingRevision() throws Exception {
        Product created = product("第一版", 8, 0);
        assertTrue(productService.save(created));
        Product changed = new Product();
        changed.setId(created.getId());
        changed.setName("第二版");

        assertTrue(productService.update(changed));

        assertEquals(102, scalarLong("SELECT list_version FROM product_catalog_state WHERE id=1"));
        assertEquals(102, scalarLong("SELECT item_version FROM product_catalog_revision WHERE product_id="
                + created.getId()));
        assertEquals(4, scalarLong("SELECT COUNT(*) FROM product_projection_task WHERE product_id="
                + created.getId()));
        assertEquals(2, readyEvents.size());
    }

    @Test
    @DisplayName("旧 ES 任务在真实租约下立即让位给当前版本且不调用投递器")
    void olderEsTaskSupersedesAndReleasesLeaseForCurrentRevision() throws Exception {
        Product created = product("第一版", 8, 0);
        assertTrue(productService.save(created));
        Product changed = new Product();
        changed.setId(created.getId());
        changed.setName("第二版");
        assertTrue(productService.update(changed));

        ProductProjectionTaskRepository repository = context.getBean(ProductProjectionTaskRepository.class);
        ProductCatalogMapper mapper = context.getBean(ProductCatalogMapper.class);
        ProductProjectionProperties properties = context.getBean(ProductProjectionProperties.class);
        AtomicInteger deliveries = new AtomicInteger();
        ProductProjectionDelivery delivery = new ProductProjectionDelivery() {
            @Override
            public String target() {
                return "ES";
            }

            @Override
            public void deliver(ProductProjectionTask task) {
                deliveries.incrementAndGet();
            }
        };
        ProductProjectionWorker worker = new ProductProjectionWorker(repository, Collections.singletonList(delivery),
                mapper, properties, Runnable::run);

        assertTrue(worker.processOne("ES"));
        assertEquals(0, deliveries.get());
        assertEquals("SUPERSEDED", scalarString("SELECT status FROM product_projection_task WHERE target='ES' "
                + "AND product_id=" + created.getId() + " AND catalog_version=101"));

        ProductProjectionTask current = repository.claim("ES");
        assertEquals(102L, current.getCatalogVersion().longValue());
        assertEquals("PROCESSING", scalarString("SELECT status FROM product_projection_task WHERE id="
                + current.getId()));
        repository.succeed(current);
    }

    @Test
    @DisplayName("同商品不同版本 ES task 被双 worker 选中时只有一个取得 revision lease")
    void differentVersionTasksCompeteForOneRevisionLease() throws Exception {
        Product created = product("双版本租约款", 8, 0);
        assertTrue(productService.save(created));
        Product changed = new Product();
        changed.setId(created.getId());
        changed.setName("双版本租约款-v2");
        assertTrue(productService.update(changed));
        ProductProjectionTaskRepository repository = context.getBean(ProductProjectionTaskRepository.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        ProductProjectionTask first;
        ProductProjectionTask second;
        try {
            Future<ProductProjectionTask> left = pool.submit(() -> {
                start.await();
                return repository.claim("ES");
            });
            Future<ProductProjectionTask> right = pool.submit(() -> {
                start.await();
                return repository.claim("ES");
            });
            start.countDown();
            first = left.get(10, TimeUnit.SECONDS);
            second = right.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertTrue((first == null) ^ (second == null));
        ProductProjectionTask owner = first == null ? second : first;
        assertEquals("PENDING:1,PROCESSING:1", scalarString("SELECT GROUP_CONCAT(CONCAT(status,':',fact_count) "
                + "ORDER BY status) FROM (SELECT status,COUNT(*) fact_count FROM product_projection_task "
                + "WHERE target='ES' AND product_id=" + created.getId() + " GROUP BY status) facts"));
        assertEquals("0:1,1:1", scalarString("SELECT GROUP_CONCAT(CONCAT(attempt_count,':',fact_count) "
                + "ORDER BY attempt_count) FROM (SELECT attempt_count,COUNT(*) fact_count "
                + "FROM product_projection_task WHERE target='ES' AND product_id=" + created.getId()
                + " GROUP BY attempt_count) facts"));

        if (owner.getCatalogVersion() == 101L) {
            repository.supersede(owner);
        } else {
            repository.succeed(owner);
        }
        ProductProjectionTask nextOwner = repository.claim("ES");
        assertTrue(nextOwner != null);
        assertTrue(!owner.getId().equals(nextOwner.getId()));
        assertEquals(1, nextOwner.getAttemptCount().intValue());
        repository.succeed(nextOwner);
    }

    @Test
    @DisplayName("真实 MySQL 租约过期后可接管且旧 owner 完成无效")
    void leaseExpiryAllowsTakeoverAndFencesStaleOwner() throws Exception {
        Product created = product("过期租约款", 8, 0);
        assertTrue(productService.save(created));
        ProductProjectionTaskRepository repository = context.getBean(ProductProjectionTaskRepository.class);
        ProductProjectionTask originalOwner = repository.claim("ES");
        assertTrue(originalOwner != null);
        execute("UPDATE product_projection_task SET locked_until=DATE_SUB(NOW(3),INTERVAL 1 SECOND) "
                + "WHERE id=" + originalOwner.getId());
        execute("UPDATE product_catalog_revision SET es_locked_until=DATE_SUB(NOW(3),INTERVAL 1 SECOND) "
                + "WHERE product_id=" + created.getId());

        ProductProjectionTask replacementOwner = repository.claim("ES");
        assertTrue(replacementOwner != null);
        assertTrue(!originalOwner.getLockedBy().equals(replacementOwner.getLockedBy()));
        assertEquals(2, replacementOwner.getAttemptCount().intValue());
        repository.succeed(originalOwner);
        assertEquals("PROCESSING", scalarString("SELECT status FROM product_projection_task WHERE id="
                + originalOwner.getId()));
        repository.succeed(replacementOwner);
        assertEquals("SUCCEEDED:2:2", scalarString("SELECT CONCAT(status,':',attempt_count,':',claim_count) "
                + "FROM product_projection_task WHERE id=" + originalOwner.getId()));
    }

    @Test
    @DisplayName("幂等补任务只忽略唯一事实冲突，非法新任务必须报错")
    void idempotentInsertDoesNotSwallowConstraintViolations() {
        ProductProjectionTask invalid = new ProductProjectionTask();
        invalid.setTarget("REDIS");
        invalid.setProductId(77L);
        invalid.setCatalogVersion(101L);
        invalid.setOperation("PUBLISH");
        invalid.setPayload("{}");
        invalid.setPayloadSha256("0f5f025aaecf7b6e237e5f72c3e196b6f79d1b301ca8c7e629b87e09b86c8aeb");
        invalid.setStatus("INVALID");
        invalid.setAttemptCount(0);
        invalid.setClaimCount(0);
        invalid.setRepairCount(0);

        ProductCatalogMapper mapper = context.getBean(ProductCatalogMapper.class);
        assertThrows(RuntimeException.class, () -> mapper.insertProjectionTaskIfAbsent(invalid));
        assertEquals(0, uncheckedScalarLong("SELECT COUNT(*) FROM product_projection_task"));
    }

    @Test
    @DisplayName("状态汇总返回分组中最近一次错误而不是字典序最大错误")
    void statusSummaryReportsLatestError() throws Exception {
        String hash = "0000000000000000000000000000000000000000000000000000000000000000";
        execute("INSERT INTO product_projection_task(target,product_id,catalog_version,operation,payload,"
                + "payload_sha256,status,attempt_count,claim_count,repair_count,last_error_summary,created_at,updated_at) "
                + "VALUES('ES',1,101,'UPSERT','{}','" + hash + "','RETRY_WAIT',1,1,0,'z_old',"
                + "DATE_SUB(NOW(3),INTERVAL 1 MINUTE),DATE_SUB(NOW(3),INTERVAL 1 MINUTE)),"
                + "('ES',2,102,'UPSERT','{}','" + hash + "','RETRY_WAIT',1,1,0,'a_new',NOW(3),NOW(3))");

        ProductProjectionStatusSummary summary = context.getBean(ProductCatalogMapper.class)
                .summarizeProjectionTasks().stream()
                .filter(item -> "ES".equals(item.getTarget()) && "RETRY_WAIT".equals(item.getStatus()))
                .findFirst().orElseThrow(AssertionError::new);

        assertEquals(2, summary.getTaskCount());
        assertEquals("a_new", summary.getLastErrorSummary());
    }

    private Product product(String name, int stock, Integer sales) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("描述");
        product.setPrice(new BigDecimal("99.90"));
        product.setStock(stock);
        product.setSales(sales);
        product.setImage("https://example.invalid/a.jpg");
        product.setCategoryId(2L);
        product.setStatus(1);
        product.setTag("新品");
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        return product;
    }

    private void createProductTable() throws Exception {
        execute("CREATE TABLE product(id BIGINT NOT NULL AUTO_INCREMENT,name VARCHAR(100) NOT NULL,"
                + "description TEXT NULL,price DECIMAL(10,2) NOT NULL,stock INT NOT NULL,sales INT NULL DEFAULT 0,"
                + "image VARCHAR(500) NULL,category_id BIGINT NULL,status INT NOT NULL,tag VARCHAR(100) NULL,"
                + "create_time DATETIME(3) NULL,update_time DATETIME(3) NULL,create_user BIGINT NULL,"
                + "update_user BIGINT NULL,PRIMARY KEY(id)) ENGINE=InnoDB");
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

    private long uncheckedScalarLong(String sql) {
        try {
            return scalarLong(sql);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
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
            throw new IllegalStateException("unsafe B8 Spring schema");
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan(basePackageClasses = {ProductMapper.class, ProductCatalogMapper.class})
    static class SpringMysqlConfig {
        @Bean
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(environment.getRequiredProperty("b8.test.jdbc-url"));
            dataSource.setUsername(environment.getRequiredProperty("b8.test.username"));
            dataSource.setPassword(environment.getRequiredProperty("b8.test.password"));
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        AfterCommitRegistrar afterCommitRegistrar(org.springframework.context.ApplicationEventPublisher publisher) {
            return new SpringAfterCommitRegistrar(publisher);
        }

        @Bean
        ProductCatalogMutationCoordinator productCatalogMutationCoordinator(ProductCatalogMapper mapper,
                                                                              AfterCommitRegistrar registrar) {
            return new ProductCatalogMutationCoordinator(mapper, registrar);
        }

        @Bean
        ProductProjectionProperties productProjectionProperties() {
            ProductProjectionProperties properties = new ProductProjectionProperties();
            properties.validate();
            return properties;
        }

        @Bean
        ProductProjectionMetrics productProjectionMetrics() {
            return new ProductProjectionMetrics();
        }

        @Bean
        ProductProjectionTaskRepository productProjectionTaskRepository(ProductCatalogMapper mapper,
                                                                          ProductProjectionProperties properties,
                                                                          ProductProjectionMetrics metrics) {
            return new MybatisProductProjectionTaskRepository(mapper, properties, metrics);
        }

        @Bean
        ProductService productService(ProductMapper mapper, ProductCatalogMutationCoordinator coordinator) {
            return new ProductServiceImpl(mapper, coordinator);
        }
    }
}
