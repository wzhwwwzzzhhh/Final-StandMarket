package com.fashion.integration;

import com.fashion.constant.RedisKey;
import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.entity.Orders;
import com.fashion.mapper.AddressBookMapper;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.service.CouponService;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import com.fashion.service.impl.OrderCancellationService;
import com.fashion.service.impl.OrderServiceImpl;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "b7.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B7 真实 MySQL + Redis 普通订单非干扰")
class B7CouponRedisNonInterferenceIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b7_redis_joint_[0-9a-f]{32}";
    private static final long COUPON_ID = 870000000001L;
    private static final long USER_ID = 870000000002L;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private OrderService orderService;

    @BeforeAll
    void connectToDedicatedDependencies() throws Exception {
        connectRedisBeforeAnyWrite();
        connectMySqlAndCreateIsolatedSchema();
        createSpringContext();
    }

    @BeforeEach
    void resetMysqlAndSeedExactRedisSentinels() throws Exception {
        execute("DROP TRIGGER IF EXISTS b7_redis_joint_fail_order");
        execute("DELETE FROM order_detail");
        execute("DELETE FROM orders");
        execute("DELETE FROM shopping_cart");
        execute("DELETE FROM product");
        execute("INSERT INTO product(id,name,price,stock,status,category_id) "
                + "VALUES(41,'B7 Redis isolation product',12.50,10,1,100)");
        execute("INSERT INTO shopping_cart(id,user_id,product_id,name,number) "
                + "VALUES(31," + USER_ID + ",41,'B7 Redis isolation product',2)");

        redis.delete(Arrays.asList(stockKey(), usersKey(), reservationsKey(), orderSequenceKey()));
        redis.opsForValue().set(stockKey(), "17");
        redis.opsForZSet().add(usersKey(), String.valueOf(USER_ID), 123456789D);
        redis.opsForHash().put(reservationsKey(), String.valueOf(USER_ID), "ORD-SENTINEL");
    }

    @AfterEach
    void cleanupExactTestState() throws Exception {
        BaseContext.removeUserId();
        if (redis != null) {
            redis.delete(Arrays.asList(stockKey(), usersKey(), reservationsKey(), orderSequenceKey()));
        }
        if (schemaUrl != null) {
            execute("DROP TRIGGER IF EXISTS b7_redis_joint_fail_order");
        }
    }

    @AfterAll
    void cleanupIsolatedDependencies() throws Exception {
        try {
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
        } finally {
            if (redis != null) {
                redis.delete(Arrays.asList(stockKey(), usersKey(), reservationsKey(), orderSequenceKey()));
            }
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    @Test
    @DisplayName("真实 MySQL 成功提交时普通订单仅使用订单序号且秒杀哨兵逐值不变")
    void committedOrdinaryOrderLeavesSeckillKeysUntouched() throws Exception {
        BaseContext.setUserId(USER_ID);

        Orders created = orderService.create(forgedOrdinaryRequest());

        assertEquals(new BigDecimal("25.00"), created.getAmount());
        assertEquals(Integer.valueOf(0), created.getIsSeckill());
        assertNull(created.getSeckillActivityId());
        assertNull(created.getSeckillCouponId());
        assertEquals(8, scalarInt("SELECT stock FROM product WHERE id=41"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM order_detail"));
        assertSentinelsUnchanged();
        assertTrue(Boolean.TRUE.equals(redis.hasKey(orderSequenceKey())));
    }

    @Test
    @DisplayName("真实 MySQL 回滚时库存订单明细回滚且秒杀哨兵逐值不变")
    void rolledBackOrdinaryOrderLeavesMysqlAndSeckillKeysUntouched() throws Exception {
        execute("CREATE TRIGGER b7_redis_joint_fail_order BEFORE INSERT ON orders FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='b7 joint rollback'");
        BaseContext.setUserId(USER_ID);

        assertThrows(RuntimeException.class, () -> orderService.create(forgedOrdinaryRequest()));

        assertEquals(10, scalarInt("SELECT stock FROM product WHERE id=41"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM orders"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM order_detail"));
        assertSentinelsUnchanged();
        assertTrue(Boolean.TRUE.equals(redis.hasKey(orderSequenceKey())));
    }

    private void connectRedisBeforeAnyWrite() throws Exception {
        Map<String, Object> settings = sectionSettings("b7.redis.config", "redis");
        B6IntegrationSafety.requireLoopback(value(settings, "host"), "Redis");
        String database = setting(settings, "database", "b7.redis.database");
        String exclusive = setting(settings, "exclusive", "b7.redis.exclusive");
        B6IntegrationSafety.requireDedicatedRedisDatabase(database);
        B6IntegrationSafety.requireExclusiveRedisDatabase(exclusive);

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(value(settings, "host"));
        configuration.setPort(Integer.parseInt(value(settings, "port")));
        configuration.setDatabase(Integer.parseInt(database));
        String redisPassword = value(settings, "password");
        if (!redisPassword.isEmpty()) {
            configuration.setPassword(RedisPassword.of(redisPassword));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        Properties server = connectionFactory.getConnection().serverCommands().info("server");
        B6IntegrationSafety.requireRedisVersion(
                server == null ? null : server.getProperty("redis_version"));
        B6IntegrationSafety.requireEmptyRedisDatabase(
                connectionFactory.getConnection().serverCommands().dbSize());
    }

    private void connectMySqlAndCreateIsolatedSchema() throws Exception {
        Map<String, Object> datasource = sectionSettings("b7.mysql.config", "datasource");
        String host = value(datasource, "host");
        B6IntegrationSafety.requireLoopback(host, "MySQL");
        username = requiredValue(datasource, "username");
        password = requiredValue(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + requiredValue(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT VERSION()")) {
            assertTrue(version.next());
            assertTrue(version.getString(1).startsWith("8.0."), "B7 requires MySQL 8.0.x");
        }
        schema = "fsm_b7_redis_joint_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        createTables();
    }

    private void createSpringContext() {
        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b7.test.jdbc-url", schemaUrl);
        properties.put("b7.test.username", username);
        properties.put("b7.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("b7RedisJoint", properties));
        context.getBeanFactory().registerSingleton("stringRedisTemplate", redis);
        context.register(JointConfig.class);
        context.refresh();
        orderService = context.getBean(OrderService.class);
    }

    private void createTables() throws Exception {
        execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(64),price DECIMAL(10,2),"
                + "stock INT,status INT,category_id BIGINT) ENGINE=InnoDB");
        execute("CREATE TABLE shopping_cart(id BIGINT PRIMARY KEY,user_id BIGINT,product_id BIGINT,name VARCHAR(64),"
                + "image VARCHAR(255),combination_id BIGINT,sku_info VARCHAR(255),number INT,amount DECIMAL(10,2),"
                + "create_time DATETIME(3)) ENGINE=InnoDB");
        execute("CREATE TABLE orders(id BIGINT PRIMARY KEY AUTO_INCREMENT,number VARCHAR(50),status INT NOT NULL,"
                + "user_id BIGINT NOT NULL,order_time DATETIME(3),checkout_time DATETIME(3),pay_method INT,"
                + "pay_status INT,amount DECIMAL(10,2),remark VARCHAR(255),phone VARCHAR(32),address VARCHAR(255),"
                + "user_name VARCHAR(64),consignee VARCHAR(64),cancel_reason VARCHAR(255),rejection_reason VARCHAR(255),"
                + "cancel_time DATETIME(3),estimated_delivery_time DATETIME(3),delivery_status INT,"
                + "delivery_time DATETIME(3),shipping_fee DECIMAL(10,2),address_book_id BIGINT,"
                + "tracking_company VARCHAR(64),tracking_number VARCHAR(64),user_coupon_id BIGINT,"
                + "original_price DECIMAL(10,2),seckill_activity_id BIGINT,seckill_coupon_id BIGINT,"
                + "is_seckill INT,seckill_price DECIMAL(10,2),stock_deducted INT) ENGINE=InnoDB");
        execute("CREATE TABLE order_detail(id BIGINT PRIMARY KEY AUTO_INCREMENT,name VARCHAR(64),image VARCHAR(255),"
                + "order_id BIGINT NOT NULL,product_id BIGINT NOT NULL,combination_id BIGINT,sku_info VARCHAR(255),"
                + "number INT,amount DECIMAL(10,2)) ENGINE=InnoDB");
    }

    private OrderCreateDTO forgedOrdinaryRequest() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(Collections.singletonList(31L));
        request.setAmount(new BigDecimal("0.01"));
        request.setActivityId(999L);
        request.setCouponId(998L);
        return request;
    }

    private void assertSentinelsUnchanged() {
        assertEquals("17", redis.opsForValue().get(stockKey()));
        assertEquals(Double.valueOf(123456789D),
                redis.opsForZSet().score(usersKey(), String.valueOf(USER_ID)));
        assertEquals("ORD-SENTINEL",
                redis.opsForHash().get(reservationsKey(), String.valueOf(USER_ID)));
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

    private Map<String, Object> sectionSettings(String property, String section) throws Exception {
        try (InputStream input = Files.newInputStream(configPath(property))) {
            Map<String, Object> root = new Yaml().load(input);
            return nested(nested(root, "fashion"), section);
        }
    }

    private Path configPath(String property) {
        String configured = System.getProperty(property);
        if (configured == null || configured.trim().isEmpty()) {
            throw new IllegalStateException(property + " is required");
        }
        Path path = Paths.get(configured);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B7 dependency config is missing");
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
        return result == null ? "" : String.valueOf(result);
    }

    private String requiredValue(Map<String, Object> values, String key) {
        String result = value(values, key);
        if (result.isEmpty()) {
            throw new IllegalStateException("missing config value " + key);
        }
        return result;
    }

    private String setting(Map<String, Object> values, String key, String property) {
        String override = System.getProperty(property);
        return override == null || override.trim().isEmpty() ? value(values, key) : override;
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B7 Redis joint schema");
        }
    }

    private String stockKey() {
        return "seckill:coupon:stock:" + COUPON_ID;
    }

    private String usersKey() {
        return "seckill:coupon:users:" + COUPON_ID;
    }

    private String reservationsKey() {
        return "seckill:coupon:reservations:" + COUPON_ID;
    }

    private String orderSequenceKey() {
        return RedisKey.ORDER_NUMBER_SEQ_KEY + ":"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {OrderMapper.class, OrderDetailMapper.class,
            AddressBookMapper.class, ShoppingCartMapper.class, ProductMapper.class})
    static class JointConfig {

        @Bean
        DataSource dataSource(org.springframework.core.env.Environment environment) {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(environment.getRequiredProperty("b7.test.jdbc-url"));
            source.setUsername(environment.getRequiredProperty("b7.test.username"));
            source.setPassword(environment.getRequiredProperty("b7.test.password"));
            return source;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath:mapper/*.xml"));
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        CouponService couponService() {
            return mock(CouponService.class);
        }

        @Bean
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        OrderCancellationService orderCancellationService() {
            return mock(OrderCancellationService.class);
        }
    }
}
