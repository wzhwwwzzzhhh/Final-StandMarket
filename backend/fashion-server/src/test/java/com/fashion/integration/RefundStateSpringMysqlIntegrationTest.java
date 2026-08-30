package com.fashion.integration;

import com.fashion.context.BaseContext;
import com.fashion.service.CouponService;
import com.fashion.service.PaymentService;
import com.fashion.service.impl.OrderCancellationService;
import com.fashion.service.impl.OrderServiceImpl;
import com.fashion.service.impl.RefundServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b3.mysql.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B3 Spring/MyBatis/MySQL 退款事务门禁")
class RefundStateSpringMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b3_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private RefundServiceImpl refundService;
    private OrderServiceImpl orderService;

    @BeforeAll
    void createSchemaAndSpringContext() throws Exception {
        Map<String, Object> datasource = loadDatasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b3_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchemaName(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b3.test.jdbc-url", schemaUrl);
        properties.put("b3.test.username", username);
        properties.put("b3.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b3Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();

        refundService = context.getBean(RefundServiceImpl.class);
        orderService = context.getBean(OrderServiceImpl.class);
        assertTrue(AopUtils.isAopProxy(refundService));
        assertTrue(AopUtils.isAopProxy(orderService));
    }

    @AfterAll
    void closeContextAndDropSchema() throws Exception {
        if (context != null) {
            context.close();
        }
        if (schema != null) {
            validateSchemaName(schema);
            try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
        }
    }

    @BeforeEach
    void resetTables() throws Exception {
        execute("DROP TABLE IF EXISTS refund");
        execute("DROP TABLE IF EXISTS product");
        execute("DROP TABLE IF EXISTS orders");
        execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, number VARCHAR(50), user_id BIGINT NOT NULL, "
                + "status TINYINT NOT NULL, pay_status TINYINT NOT NULL DEFAULT 1, "
                + "stock_deducted TINYINT(1) NOT NULL DEFAULT 1, amount DECIMAL(10,2), "
                + "delivery_time DATETIME) ENGINE=InnoDB");
        execute("CREATE TABLE product (id BIGINT PRIMARY KEY, stock INT NOT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE refund (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, "
                + "order_detail_id BIGINT NULL, user_id BIGINT NOT NULL, refund_no VARCHAR(50) NOT NULL, "
                + "reason VARCHAR(500), amount DECIMAL(10,2) NOT NULL, status TINYINT NOT NULL DEFAULT 0, "
                + "order_status TINYINT NOT NULL, audit_opinion VARCHAR(500), audit_time DATETIME, refund_time DATETIME, "
                + "create_time DATETIME NOT NULL, update_time DATETIME, UNIQUE KEY idx_refund_no(refund_no), "
                + "UNIQUE KEY idx_refund_order(order_id), CONSTRAINT chk_refund_status_b3 CHECK(status IN (0,1,2,3)), "
                + "CONSTRAINT chk_refund_order_status_b3 CHECK(order_status IN (3,4))) ENGINE=InnoDB");
        BaseContext.setUserId(7L);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("审核同意只写状态 1 且订单支付库存和完成时间不变")
    void approvalHasNoOrderPaymentOrInventorySideEffects() throws Exception {
        seedOrder(100, 6);
        execute("INSERT INTO product VALUES (1,5)");
        seedRefund(10, 100, 0, 3);

        refundService.approve(10L, "同意");

        assertEquals(1, scalarInt("SELECT status FROM refund WHERE id=10"));
        assertNull(scalarObject("SELECT refund_time FROM refund WHERE id=10"));
        assertEquals(6, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(1, scalarInt("SELECT pay_status FROM orders WHERE id=100"));
        assertEquals(1, scalarInt("SELECT stock_deducted FROM orders WHERE id=100"));
        assertEquals(5, scalarInt("SELECT stock FROM product WHERE id=1"));
    }

    @Test
    @DisplayName("拒绝正常提交且订单恢复零行时退款 CAS 回滚")
    void rejectionCommitsTogetherAndRollsBackTogether() throws Exception {
        seedOrder(100, 6);
        seedRefund(10, 100, 0, 3);

        refundService.reject(10L, "拒绝");

        assertEquals(3, scalarInt("SELECT status FROM refund WHERE id=10"));
        assertEquals(3, scalarInt("SELECT status FROM orders WHERE id=100"));

        resetTables();
        seedOrder(100, 4);
        seedRefund(10, 100, 0, 3);
        assertThrows(IllegalStateException.class, () -> refundService.reject(10L, "拒绝"));
        assertEquals(0, scalarInt("SELECT status FROM refund WHERE id=10"));
        assertEquals(4, scalarInt("SELECT status FROM orders WHERE id=100"));
    }

    @Test
    @DisplayName("并发同意只有一个请求成功")
    void concurrentApprovalsHaveOneWinner() throws Exception {
        seedOrder(100, 6);
        seedRefund(10, 100, 0, 3);

        List<Boolean> results = runConcurrently(8, () -> succeeds(() -> refundService.approve(10L, "同意")));

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, scalarInt("SELECT status FROM refund WHERE id=10"));
    }

    @Test
    @DisplayName("并发同意与拒绝最多一个成功并形成一个合法终态")
    void approvalAndRejectionRaceHasOneLegalWinner() throws Exception {
        seedOrder(100, 6);
        seedRefund(10, 100, 0, 3);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(() -> succeeds(() -> refundService.approve(10L, "同意")));
        tasks.add(() -> succeeds(() -> refundService.reject(10L, "拒绝")));
        List<Boolean> results = runConcurrently(tasks);

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        int refundStatus = scalarInt("SELECT status FROM refund WHERE id=10");
        int orderStatus = scalarInt("SELECT status FROM orders WHERE id=100");
        assertTrue((refundStatus == 1 && orderStatus == 6) || (refundStatus == 3 && orderStatus == 3));
    }

    @Test
    @DisplayName("退款申请与确认收货竞态只有一个真实代理调用成功")
    void refundApplicationAndConfirmationRaceHasOneWinner() throws Exception {
        seedOrder(100, 3);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        tasks.add(() -> withUser(() -> refundService.apply(100L, "不合适")));
        tasks.add(() -> withUser(() -> orderService.confirm(100L)));
        List<Boolean> results = runConcurrently(tasks);

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        int orderStatus = scalarInt("SELECT status FROM orders WHERE id=100");
        int refundCount = scalarInt("SELECT COUNT(*) FROM refund");
        assertTrue((orderStatus == 6 && refundCount == 1) || (orderStatus == 4 && refundCount == 0));
    }

    private boolean withUser(Runnable action) {
        BaseContext.setUserId(7L);
        try {
            return succeeds(action);
        } finally {
            BaseContext.removeUserId();
        }
    }

    private boolean succeeds(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException expected) {
            return false;
        }
    }

    private List<Boolean> runConcurrently(int count, Callable<Boolean> task) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }

    private List<Boolean> runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> future : futures) {
            results.add(future.get());
        }
        pool.shutdownNow();
        return results;
    }

    private void seedOrder(long id, int status) throws Exception {
        execute("INSERT INTO orders(id,number,user_id,status,pay_status,stock_deducted,amount) VALUES ("
                + id + ",'ORD-" + id + "',7," + status + ",1,1,88.00)");
    }

    private void seedRefund(long id, long orderId, int status, int orderStatus) throws Exception {
        execute("INSERT INTO refund(id,order_id,user_id,refund_no,amount,status,order_status,create_time) VALUES ("
                + id + "," + orderId + ",7,'RF-" + id + "',88.00," + status + "," + orderStatus + ",NOW())");
    }

    private Map<String, Object> loadDatasourceSettings() throws Exception {
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

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        Object value = scalarObject(sql);
        if (value instanceof Boolean) {
            return (Boolean) value ? 1 : 0;
        }
        return ((Number) value).intValue();
    }

    private Object scalarObject(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getObject(1);
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

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.fashion.mapper")
    static class SpringMysqlConfig {

        @Bean
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(environment.getRequiredProperty("b3.test.jdbc-url"));
            dataSource.setUsername(environment.getRequiredProperty("b3.test.username"));
            dataSource.setPassword(environment.getRequiredProperty("b3.test.password"));
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
        RefundServiceImpl refundService() {
            return new RefundServiceImpl();
        }

        @Bean
        OrderServiceImpl orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        CouponService couponService() {
            return mock(CouponService.class);
        }

        @Bean
        OrderCancellationService orderCancellationService() {
            return mock(OrderCancellationService.class);
        }

        @Bean
        @SuppressWarnings("unchecked")
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate template = mock(StringRedisTemplate.class);
            ValueOperations<String, String> operations = mock(ValueOperations.class);
            AtomicLong sequence = new AtomicLong();
            when(template.opsForValue()).thenReturn(operations);
            when(operations.increment(anyString())).thenAnswer(invocation -> sequence.incrementAndGet());
            return template;
        }
    }
}
