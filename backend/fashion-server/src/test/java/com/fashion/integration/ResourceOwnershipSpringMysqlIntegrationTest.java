package com.fashion.integration;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.mapper.AddressBookMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.PaymentMapper;
import com.fashion.mapper.ReviewMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.service.CouponService;
import com.fashion.service.PaymentService;
import com.fashion.service.impl.AddressBookServiceImpl;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b4.mysql.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B4 Spring/MyBatis/MySQL 资源归属门禁")
class ResourceOwnershipSpringMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b4_it_[0-9a-f]{32}";
    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private AddressBookServiceImpl addressService;
    private OrderServiceImpl orderService;
    private AddressBookMapper addressMapper;
    private OrderMapper orderMapper;
    private PaymentMapper paymentMapper;
    private ReviewMapper reviewMapper;
    private SeckillOrderMapper seckillOrderMapper;

    @BeforeAll
    void createSchemaAndSpringContext() throws Exception {
        Map<String, Object> datasource = loadDatasourceSettings();
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + value(datasource, "host") + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b4_it_" + UUID.randomUUID().toString().replace("-", "");
        validateSchemaName(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b4.test.jdbc-url", schemaUrl);
        properties.put("b4.test.username", username);
        properties.put("b4.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b4Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();

        addressService = context.getBean(AddressBookServiceImpl.class);
        orderService = context.getBean(OrderServiceImpl.class);
        addressMapper = context.getBean(AddressBookMapper.class);
        orderMapper = context.getBean(OrderMapper.class);
        paymentMapper = context.getBean(PaymentMapper.class);
        reviewMapper = context.getBean(ReviewMapper.class);
        seckillOrderMapper = context.getBean(SeckillOrderMapper.class);
        assertTrue(AopUtils.isAopProxy(addressService));
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
        execute("DROP TRIGGER IF EXISTS fail_second_default_write");
        execute("DROP TABLE IF EXISTS seckill_order");
        execute("DROP TABLE IF EXISTS review");
        execute("DROP TABLE IF EXISTS payment");
        execute("DROP TABLE IF EXISTS orders");
        execute("DROP TABLE IF EXISTS address_book");
        execute("CREATE TABLE address_book (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, "
                + "consignee VARCHAR(50), sex TINYINT, phone VARCHAR(20), province_code VARCHAR(20), "
                + "province_name VARCHAR(50), city_code VARCHAR(20), city_name VARCHAR(50), "
                + "district_code VARCHAR(20), district_name VARCHAR(50), detail VARCHAR(255), "
                + "label VARCHAR(50), is_default TINYINT NOT NULL DEFAULT 0) ENGINE=InnoDB");
        execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, number VARCHAR(50), user_id BIGINT NOT NULL, "
                + "status TINYINT NOT NULL, pay_status TINYINT DEFAULT 0) ENGINE=InnoDB");
        execute("CREATE TABLE payment (id BIGINT PRIMARY KEY, pay_no VARCHAR(64) NOT NULL, "
                + "order_id BIGINT NOT NULL, order_type TINYINT NOT NULL, status TINYINT DEFAULT 0) ENGINE=InnoDB");
        execute("CREATE TABLE review (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT, "
                + "order_id BIGINT NOT NULL, rating TINYINT, content VARCHAR(255), images VARCHAR(255), "
                + "status TINYINT, create_time DATETIME, update_time DATETIME) ENGINE=InnoDB");
        execute("CREATE TABLE seckill_order (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, coupon_id BIGINT, "
                + "order_number VARCHAR(64) NOT NULL, status TINYINT NOT NULL, create_time DATETIME, "
                + "pay_time DATETIME) ENGINE=InnoDB");

        execute("INSERT INTO address_book(id,user_id,consignee,phone,province_name,city_name,district_name,detail,is_default) "
                + "VALUES (11,7,'甲','13000000001','浙','杭','西湖','一号',1),(12,7,'乙','13000000002','浙','杭','西湖','二号',0),"
                + "(21,8,'丙','13000000003','沪','沪','浦东','三号',1)");
        execute("INSERT INTO orders VALUES (101,'ORD-A',7,1,0),(201,'ORD-B',8,1,0)");
        execute("INSERT INTO payment VALUES (301,'PAY-A',101,0,1),(302,'PAY-B',201,0,1)");
        execute("INSERT INTO review VALUES (401,7,1,101,5,'A',NULL,1,NOW(),NULL),"
                + "(402,8,1,201,4,'B',NULL,1,NOW(),NULL)");
        execute("INSERT INTO seckill_order VALUES (501,7,1,'SEC-A',1,NOW(),NULL),"
                + "(502,8,1,'SEC-B',1,NOW(),NULL)");
        BaseContext.setUserId(7L);
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("生产 Mapper 对地址订单支付评价和秒杀查询执行双用户隔离")
    void productionMappersDoNotReturnAnotherUsersResources() {
        assertNotNull(addressMapper.getByIdAndUserId(11L, 7L));
        assertNull(addressMapper.getByIdAndUserId(21L, 7L));
        assertNotNull(orderMapper.getByIdAndUserId(101L, 7L));
        assertNull(orderMapper.getByIdAndUserId(201L, 7L));
        assertNotNull(paymentMapper.getByPayNoAndUserId("PAY-A", 7L));
        assertNull(paymentMapper.getByPayNoAndUserId("PAY-B", 7L));
        assertNotNull(reviewMapper.selectByOrderIdAndUserId(101L, 7L));
        assertNull(reviewMapper.selectByOrderIdAndUserId(201L, 7L));
        assertNotNull(seckillOrderMapper.selectByOrderNumberAndUserId("SEC-A", 7L));
        assertNull(seckillOrderMapper.selectByOrderNumberAndUserId("SEC-B", 7L));
    }

    @Test
    @DisplayName("秒杀取消的同一条生产 SQL 同时校验用户和待支付状态")
    void seckillCancellationCannotCrossOwnerBoundary() throws Exception {
        assertEquals(0, seckillOrderMapper.cancelPendingByOrderNumberAndUserId("SEC-B", 7L));
        assertEquals(1, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-B'"));
        assertEquals(1, seckillOrderMapper.cancelPendingByOrderNumberAndUserId("SEC-A", 7L));
        assertEquals(3, scalarInt("SELECT status FROM seckill_order WHERE order_number='SEC-A'"));
        assertEquals(0, seckillOrderMapper.cancelPendingByOrderNumberAndUserId("SEC-A", 7L));
    }

    @Test
    @DisplayName("默认地址第二步失败时真实 Spring 事务回滚第一步重置")
    void defaultAddressResetRollsBackWhenTargetWriteFails() throws Exception {
        execute("CREATE TRIGGER fail_second_default_write BEFORE UPDATE ON address_book FOR EACH ROW "
                + "BEGIN IF NEW.id=12 AND OLD.is_default=0 AND NEW.is_default=1 THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced target failure'; END IF; END");

        assertThrows(RuntimeException.class, () -> addressService.setDefault(12L));

        assertEquals(1, scalarInt("SELECT is_default FROM address_book WHERE id=11"));
        assertEquals(0, scalarInt("SELECT is_default FROM address_book WHERE id=12"));
    }

    @Test
    @DisplayName("真实订单事务在越权地址校验处失败且不写入订单")
    void orderCreationRejectsAnotherUsersAddressBeforeWrites() throws Exception {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setAddressId(21L);
        request.setProductIds(Arrays.asList(9001L));

        assertThrows(IllegalStateException.class, () -> orderService.create(request));

        assertEquals(2, scalarInt("SELECT COUNT(*) FROM orders"));
    }

    private Map<String, Object> loadDatasourceSettings() throws Exception {
        String configPath = System.getProperty("b4.mysql.config");
        if (configPath == null || configPath.trim().isEmpty()) {
            throw new IllegalStateException("b4.mysql.config is required");
        }
        Path path = Paths.get(configPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("B4 MySQL config is missing");
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
            return ((Number) result.getObject(1)).intValue();
        }
    }

    private static void validateSchemaName(String name) {
        if (!name.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("invalid B4 temporary schema name");
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
            dataSource.setUrl(environment.getRequiredProperty("b4.test.jdbc-url"));
            dataSource.setUsername(environment.getRequiredProperty("b4.test.username"));
            dataSource.setPassword(environment.getRequiredProperty("b4.test.password"));
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
        AddressBookServiceImpl addressBookService() {
            return new AddressBookServiceImpl();
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
