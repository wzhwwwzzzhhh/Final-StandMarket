package com.fashion.integration;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.mapper.OrderMapper;
import com.fashion.service.impl.CouponServiceImpl;
import com.fashion.service.impl.OrderCancellationService;
import com.fashion.service.impl.OrderServiceImpl;
import com.fashion.service.impl.PaymentServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b2.mysql.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B2 Spring/MyBatis/MySQL 真实事务门禁")
class OrderInventorySpringMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b2_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private OrderServiceImpl orderService;
    private OrderCancellationService cancellationService;
    private PaymentServiceImpl paymentService;

    @BeforeAll
    void createSchemaAndSpringContext() throws Exception {
        Map<String, Object> datasource = loadDatasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b2_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchemaName(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b2.test.jdbc-url", schemaUrl);
        properties.put("b2.test.username", username);
        properties.put("b2.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b2Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();

        orderService = context.getBean(OrderServiceImpl.class);
        cancellationService = context.getBean(OrderCancellationService.class);
        paymentService = context.getBean(PaymentServiceImpl.class);
        assertTrue(AopUtils.isAopProxy(orderService));
        assertTrue(AopUtils.isAopProxy(cancellationService));
        assertTrue(AopUtils.isAopProxy(paymentService));
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
        execute("DROP TABLE IF EXISTS order_detail");
        execute("DROP TABLE IF EXISTS payment");
        execute("DROP TABLE IF EXISTS orders");
        execute("DROP TABLE IF EXISTS shopping_cart");
        execute("DROP TABLE IF EXISTS user_coupon");
        execute("DROP TABLE IF EXISTS coupon_template");
        execute("DROP TABLE IF EXISTS product");

        execute("CREATE TABLE product (id BIGINT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2) NOT NULL, "
                + "stock INT NOT NULL, status INT NOT NULL, category_id BIGINT NULL) ENGINE=InnoDB");
        execute("CREATE TABLE shopping_cart (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, "
                + "name VARCHAR(100), image VARCHAR(255), sku_info VARCHAR(100), number INT NOT NULL, "
                + "amount DECIMAL(10,2), create_time DATETIME) ENGINE=InnoDB");
        execute("CREATE TABLE coupon_template (id BIGINT PRIMARY KEY, name VARCHAR(100), type INT, "
                + "threshold DECIMAL(10,2), discount DECIMAL(10,2), scope_type INT, apply_category_id BIGINT, "
                + "apply_product_ids VARCHAR(255), status INT, valid_type INT, start_time DATETIME, end_time DATETIME) ENGINE=InnoDB");
        execute("CREATE TABLE user_coupon (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, template_id BIGINT NOT NULL, "
                + "status INT NOT NULL, obtain_time DATETIME, expire_time DATETIME, use_order_id BIGINT, use_time DATETIME) ENGINE=InnoDB");
        execute("CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, number VARCHAR(50), status INT NOT NULL, "
                + "user_id BIGINT NOT NULL, order_time DATETIME NOT NULL, checkout_time DATETIME, pay_method INT, "
                + "pay_status INT NOT NULL, amount DECIMAL(10,2), remark VARCHAR(255), phone VARCHAR(30), "
                + "address VARCHAR(255), user_name VARCHAR(100), consignee VARCHAR(100), cancel_reason VARCHAR(255), "
                + "rejection_reason VARCHAR(255), cancel_time DATETIME, estimated_delivery_time DATETIME, "
                + "delivery_status INT, delivery_time DATETIME, shipping_fee DECIMAL(10,2), address_book_id BIGINT, "
                + "tracking_company VARCHAR(100), tracking_number VARCHAR(100), user_coupon_id BIGINT, "
                + "original_price DECIMAL(10,2), seckill_activity_id BIGINT, seckill_coupon_id BIGINT, "
                + "is_seckill INT NOT NULL DEFAULT 0, seckill_price DECIMAL(10,2), "
                + "stock_deducted TINYINT(1) NOT NULL DEFAULT 0, "
                + "KEY idx_orders_timeout(status,pay_status,order_time)) ENGINE=InnoDB");
        execute("CREATE TABLE order_detail (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), image VARCHAR(255), "
                + "order_id BIGINT NOT NULL, product_id BIGINT NOT NULL, combination_id BIGINT, sku_info VARCHAR(100), "
                + "number INT NOT NULL, amount DECIMAL(10,2)) ENGINE=InnoDB");
        execute("CREATE TABLE payment (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, order_type INT NOT NULL, "
                + "pay_no VARCHAR(100), amount DECIMAL(10,2), pay_method INT, status INT, create_time DATETIME, "
                + "trade_no VARCHAR(100), pay_time DATETIME) ENGINE=InnoDB");
        BaseContext.setUserId(7L);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("第二商品条件扣减失败会回滚第一商品库存")
    void secondProductFailureRollsBackFirstDeduction() throws Exception {
        seedProductAndCart(1, 10, 2);
        seedProductAndCart(2, 11, 2);
        execute("CREATE TRIGGER fail_second_product BEFORE UPDATE ON product FOR EACH ROW BEGIN "
                + "IF NEW.id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='second product failure'; END IF; END");

        assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(null, 10L, 11L)));

        assertCreationWasRolledBack(2, null);
        assertEquals(2, scalarInt("SELECT stock FROM product WHERE id=2"));
    }

    @Test
    @DisplayName("锁券失败会回滚库存且不留订单")
    void couponLockFailureRollsBackAllWrites() throws Exception {
        seedProductAndCart(1, 10, 2);
        seedAvailableCoupon(55L);
        execute("CREATE TRIGGER fail_coupon_lock BEFORE UPDATE ON user_coupon FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='coupon lock failure'");

        assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(55L, 10L)));

        assertCreationWasRolledBack(2, 55L);
    }

    @Test
    @DisplayName("订单插入失败会回滚库存和已锁优惠券")
    void orderInsertFailureRollsBackAllWrites() throws Exception {
        seedProductAndCart(1, 10, 2);
        seedAvailableCoupon(55L);
        execute("CREATE TRIGGER fail_order_insert BEFORE INSERT ON orders FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='order insert failure'");

        assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(55L, 10L)));

        assertCreationWasRolledBack(2, 55L);
    }

    @Test
    @DisplayName("优惠券绑定零行会回滚订单、库存和券锁")
    void couponBindZeroRowsRollsBackAllWrites() throws Exception {
        seedProductAndCart(1, 10, 2);
        seedAvailableCoupon(55L);
        execute("CREATE TRIGGER poison_coupon_binding BEFORE UPDATE ON user_coupon FOR EACH ROW "
                + "SET NEW.use_order_id=IF(OLD.status=0 AND NEW.status=3,999,NEW.use_order_id)");

        assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(55L, 10L)));

        assertCreationWasRolledBack(2, 55L);
    }

    @Test
    @DisplayName("明细插入失败会回滚订单、库存和券锁")
    void detailInsertFailureRollsBackAllWrites() throws Exception {
        seedProductAndCart(1, 10, 2);
        seedAvailableCoupon(55L);
        execute("CREATE TRIGGER fail_detail_insert BEFORE INSERT ON order_detail FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='detail insert failure'");

        assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(55L, 10L)));

        assertCreationWasRolledBack(2, 55L);
    }

    @Test
    @DisplayName("优惠券释放零行会回滚取消状态和库存回补")
    void couponReleaseZeroRowsRollsBackCancellation() throws Exception {
        seedPendingOrder(100, 1, 55L, 1);

        assertThrows(IllegalStateException.class, () -> cancellationService.cancelForUser(100L, 7L));

        assertEquals(1, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(1, scalarInt("SELECT stock_deducted FROM orders WHERE id=100"));
        assertEquals(3, scalarInt("SELECT stock FROM product WHERE id=1"));
    }

    @Test
    @DisplayName("优惠券核销零行会回滚支付记录和订单状态")
    void couponMarkUsedZeroRowsRollsBackPayment() throws Exception {
        seedPendingOrder(100, 1, 55L, 1);
        execute("INSERT INTO payment(id,order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (10,100,0,'PAY-100',20.00,2,0,NOW())");

        assertThrows(IllegalStateException.class, () -> orderService.handlePayCallback(
                100L, 10L, "TRADE-100", LocalDateTime.now()));

        assertEquals(1, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(0, scalarInt("SELECT pay_status FROM orders WHERE id=100"));
        assertEquals(0, scalarInt("SELECT status FROM payment WHERE id=10"));
    }

    @Test
    @DisplayName("历史库存零标识订单取消不回补且支付发起被拒绝")
    void historicalPendingOrderCancelsWithoutRestockAndCannotPay() throws Exception {
        seedPendingOrder(100, 1, null, 0);

        cancellationService.cancelForUser(100L, 7L);

        assertEquals(5, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(3, scalarInt("SELECT stock FROM product WHERE id=1"));

        seedPendingOrder(101, 2, null, 0);
        assertThrows(IllegalStateException.class, () -> paymentService.createAlipayPayment(101L));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM payment WHERE order_id=101"));
    }

    @Test
    @DisplayName("超时混合批次通过 REQUIRES_NEW 独立提交并在下轮重试失败订单")
    void timeoutBatchCommitsAroundFailureAndRetriesNextRun() throws Exception {
        seedPendingOrder(100, 1, null, 1);
        seedPendingOrder(101, 2, 55L, 1);
        seedPendingOrder(102, 3, null, 1);

        orderService.autoCancelTimeoutOrders();

        assertEquals(5, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(1, scalarInt("SELECT status FROM orders WHERE id=101"));
        assertEquals(5, scalarInt("SELECT status FROM orders WHERE id=102"));
        assertEquals(5, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(3, scalarInt("SELECT stock FROM product WHERE id=2"));
        assertEquals(5, scalarInt("SELECT stock FROM product WHERE id=3"));

        seedLockedCoupon(55L, 101L);
        orderService.autoCancelTimeoutOrders();

        assertEquals(5, scalarInt("SELECT status FROM orders WHERE id=101"));
        assertEquals(5, scalarInt("SELECT stock FROM product WHERE id=2"));
        assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=55"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM orders WHERE status=1"));
    }

    private void seedProductAndCart(long productId, long cartId, int stock) throws Exception {
        execute("INSERT INTO product(id,name,price,stock,status,category_id) VALUES (" + productId
                + ",'product-" + productId + "',10.00," + stock + ",1,1)");
        execute("INSERT INTO shopping_cart(id,user_id,product_id,name,number,amount,create_time) VALUES ("
                + cartId + ",7," + productId + ",'product-" + productId + "',1,10.00,NOW())");
    }

    private void seedAvailableCoupon(long couponId) throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,scope_type,status,valid_type) "
                + "VALUES (1,'coupon',1,0,1,0,1,2)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES ("
                + couponId + ",7,1,0,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY))");
    }

    private void seedLockedCoupon(long couponId, long orderId) throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,scope_type,status,valid_type) "
                + "VALUES (1,'coupon',1,0,1,0,1,2)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time,use_order_id) VALUES ("
                + couponId + ",7,1,3,NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY)," + orderId + ")");
    }

    private void seedPendingOrder(long orderId, long productId, Long couponId, int stockDeducted) throws Exception {
        execute("INSERT INTO product(id,name,price,stock,status) VALUES (" + productId
                + ",'product-" + productId + "',10.00,3,1)");
        execute("INSERT INTO orders(id,number,status,user_id,order_time,pay_method,pay_status,amount,is_seckill,"
                + "user_coupon_id,original_price,stock_deducted) VALUES (" + orderId + ",'ORD-" + orderId
                + "',1,7,DATE_SUB(NOW(),INTERVAL 40 MINUTE),1,0,20.00,0,"
                + (couponId == null ? "NULL" : couponId) + ",20.00," + stockDeducted + ")");
        execute("INSERT INTO order_detail(order_id,product_id,name,number,amount) VALUES (" + orderId + ","
                + productId + ",'product-" + productId + "',2,20.00)");
    }

    private OrderCreateDTO orderRequest(Long couponId, Long... cartIds) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Arrays.asList(cartIds));
        request.setUserCouponId(couponId);
        return request;
    }

    private void assertCreationWasRolledBack(int expectedStock, Long couponId) throws Exception {
        assertEquals(expectedStock, scalarInt("SELECT stock FROM product WHERE id=1"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM order_detail"));
        if (couponId != null) {
            assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=" + couponId));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM user_coupon WHERE id=" + couponId
                    + " AND use_order_id IS NOT NULL"));
        }
    }

    private Map<String, Object> loadDatasourceSettings() throws Exception {
        String configPath = System.getProperty("b2.mysql.config");
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IllegalStateException("b2.mysql.config is required");
        }
        Path path = Paths.get(configPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B2 MySQL config is missing");
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
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
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

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.fashion.mapper")
    static class SpringMysqlConfig {

        @Bean
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(environment.getRequiredProperty("b2.test.jdbc-url"));
            dataSource.setUsername(environment.getRequiredProperty("b2.test.username"));
            dataSource.setPassword(environment.getRequiredProperty("b2.test.password"));
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
        OrderServiceImpl orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        OrderCancellationService cancellationService() {
            return new OrderCancellationService();
        }

        @Bean
        CouponServiceImpl couponService() {
            return new CouponServiceImpl();
        }

        @Bean
        PaymentServiceImpl paymentService() {
            return new PaymentServiceImpl();
        }

        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
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
