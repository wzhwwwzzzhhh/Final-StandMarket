package com.fashion.integration;

import com.fashion.context.BaseContext;
import com.fashion.entity.Payment;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.PaymentMapper;
import com.fashion.service.CouponService;
import com.fashion.service.impl.OrderServiceImpl;
import com.fashion.service.impl.PaymentServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

@EnabledIfSystemProperty(named = "b1.mysql.integration", matches = "true")
@DisplayName("B1 MySQL 8 支付事务集成")
class PaymentMysqlIntegrationTest {

    private static String adminUrl;
    private static String schemaUrl;
    private static String username;
    private static String password;
    private static String schema;
    private static DataSource dataSource;

    private PaymentMapper paymentMapper;
    private OrderMapper orderMapper;
    private PaymentServiceImpl paymentService;
    private OrderServiceImpl orderService;
    private CouponService couponService;
    private TransactionTemplate transactions;

    @BeforeAll
    static void createIsolatedSchema() throws Exception {
        Map<String, Object> datasource;
        try (InputStream input = new ClassPathResource("application-dev.yml").getInputStream()) {
            Map<String, Object> root = new Yaml().load(input);
            datasource = nestedMap(nestedMap(root, "fashion"), "datasource");
        }
        String host = value(datasource, "host");
        String port = value(datasource, "port");
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b1_it_" + UUID.randomUUID().toString().replace("-", "");
        if (!schema.matches("fsm_b1_it_[a-f0-9]{32}")) {
            throw new IllegalStateException("invalid temporary schema name");
        }

        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT VERSION()")) {
                assertTrue(result.next());
                assertTrue(Integer.parseInt(result.getString(1).split("\\.")[0]) >= 8,
                        "B1 integration gate requires MySQL 8+");
            }
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        DriverManagerDataSource configured = new DriverManagerDataSource();
        configured.setUrl(schemaUrl);
        configured.setUsername(username);
        configured.setPassword(password);
        dataSource = configured;
    }

    @AfterAll
    static void dropIsolatedSchema() throws Exception {
        if (schema == null || !schema.matches("fsm_b1_it_[a-f0-9]{32}")) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + schema + "`");
        }
    }

    @BeforeEach
    void resetSchemaAndServices() throws Exception {
        resetTables();
        configureMappersAndServices();
    }

    @AfterEach
    void clearContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("迁移拒绝历史冲突，清理后可重试且成功重跑为 no-op")
    void migrationIsRetryableAndIdempotent() throws Exception {
        execute("INSERT INTO payment(order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (100,0,'P1',10.00,2,0,NOW()),(100,0,'P2',10.00,2,1,NOW())");

        SQLException conflict = assertThrows(SQLException.class, PaymentMysqlIntegrationTest::runMigration);
        assertEquals("45000", conflict.getSQLState());

        execute("DELETE FROM payment WHERE pay_no='P2'");
        runMigration();
        runMigration();

        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='payment' "
                + "AND column_name IN ('active_order_id','active_order_type')"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema=DATABASE() AND table_name='payment' "
                + "AND index_name='uk_payment_active_order'"));
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO payment(order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                        + "VALUES (100,0,'P3',10.00,2,0,NOW())"));

        execute("UPDATE payment SET status=3 WHERE pay_no='P1'");
        execute("INSERT INTO payment(order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (100,0,'P4',10.00,2,0,NOW())");
    }

    @Test
    @DisplayName("迁移拒绝只有生成列或缺少索引的部分 schema")
    void migrationRejectsPartialSchema() throws Exception {
        execute("ALTER TABLE payment ADD COLUMN active_order_id BIGINT GENERATED ALWAYS AS "
                + "(CASE WHEN status IN (0,1) THEN order_id ELSE NULL END) STORED");

        SQLException partial = assertThrows(SQLException.class, PaymentMysqlIntegrationTest::runMigration);

        assertEquals("45000", partial.getSQLState());
    }

    @Test
    @DisplayName("迁移拒绝名称齐全但活动状态表达式错误的 schema")
    void migrationRejectsWrongGeneratedExpressions() throws Exception {
        execute("ALTER TABLE payment "
                + "ADD COLUMN active_order_id BIGINT GENERATED ALWAYS AS "
                + "(CASE WHEN status IN (2) THEN order_id ELSE NULL END) STORED, "
                + "ADD COLUMN active_order_type TINYINT GENERATED ALWAYS AS "
                + "(CASE WHEN status IN (2) THEN order_type ELSE NULL END) STORED, "
                + "ADD UNIQUE INDEX uk_payment_active_order(active_order_id,active_order_type)");

        SQLException malformed = assertThrows(SQLException.class, PaymentMysqlIntegrationTest::runMigration);

        assertEquals("45000", malformed.getSQLState());
    }

    @Test
    @DisplayName("同秒多条终态历史流水按 id 确定性返回最新记录")
    void latestPaymentUsesIdAsTieBreaker() throws Exception {
        execute("INSERT INTO payment(id,order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (10,100,0,'PAY-OLD',10.00,2,3,'2026-08-28 12:00:00'),"
                + "(11,100,0,'PAY-NEW',10.00,2,3,'2026-08-28 12:00:00')");

        Payment latest = paymentMapper.getByOrderIdAndType(100L, 0);

        assertEquals(11L, latest.getId());
    }

    @Test
    @DisplayName("并发 Service 创建在订单行锁内复用唯一活动流水")
    void concurrentServiceCreationReturnsOnePayment() throws Exception {
        runMigration();
        insertPendingOrder();
        int workers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                futures.add(executor.submit(() -> {
                    BaseContext.setUserId(7L);
                    try {
                        start.await();
                        return transactions.execute(status -> paymentService.createAlipayPayment(100L).getPayNo());
                    } finally {
                        BaseContext.removeUserId();
                    }
                }));
            }
            start.countDown();
            String payNo = futures.get(0).get();
            for (Future<String> future : futures) {
                assertEquals(payNo, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM payment WHERE status IN (0,1)"));
    }

    @Test
    @DisplayName("外部事务抢先提交后创建方通过唯一冲突当前读收敛到赢家")
    void duplicateInsertRecoveryReadsCommittedWinner() throws Exception {
        runMigration();
        insertPendingOrder();
        CountDownLatch emptyReadCompleted = new CountDownLatch(1);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        PaymentMapper actualMapper = paymentMapper;
        PaymentMapper racingMapper = mock(PaymentMapper.class, delegatesTo(actualMapper));
        doAnswer(invocation -> {
            Payment existing = actualMapper.getActiveByOrderIdAndType(100L, 0);
            assertNull(existing);
            emptyReadCompleted.countDown();
            assertTrue(winnerCommitted.await(5, TimeUnit.SECONDS));
            return null;
        }).when(racingMapper).getActiveByOrderIdAndType(100L, 0);
        ReflectionTestUtils.setField(paymentService, "paymentMapper", racingMapper);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> winner = executor.submit(() -> {
            assertTrue(emptyReadCompleted.await(5, TimeUnit.SECONDS));
            try {
                execute("INSERT INTO payment(order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                        + "VALUES (100,0,'PAY-WINNER',10.00,2,0,NOW())");
                return true;
            } finally {
                winnerCommitted.countDown();
            }
        });
        try {
            BaseContext.setUserId(7L);
            Payment result = transactions.execute(status -> paymentService.createAlipayPayment(100L));

            assertTrue(winner.get());
            assertNotNull(result);
            assertEquals("PAY-WINNER", result.getPayNo());
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM payment WHERE status IN (0,1)"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("数据库唯一约束下并发直写只有一个活动流水成功")
    void concurrentDirectInsertsHaveOneWinner() throws Exception {
        runMigration();

        List<Boolean> outcomes = runConcurrently(
                () -> tryInsertActivePayment("PAY-DIRECT-A"),
                () -> tryInsertActivePayment("PAY-DIRECT-B"));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM payment WHERE status IN (0,1)"));
    }

    @Test
    @DisplayName("双回调只迁移并核销一次，后续失败会整体回滚")
    void callbackIsIdempotentAndRollbackIsAtomic() throws Exception {
        runMigration();
        insertPendingOrder();
        execute("INSERT INTO payment(id,order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (10,100,0,'PAY-100',10.00,2,0,NOW())");

        runConcurrently(
                () -> {
                    inTransaction(() -> orderService.handlePayCallback(
                            100L, 10L, "TRADE-100", LocalDateTime.now()));
                    return true;
                },
                () -> {
                    inTransaction(() -> orderService.handlePayCallback(
                            100L, 10L, "TRADE-100", LocalDateTime.now()));
                    return true;
                });

        assertEquals(2, scalarInt("SELECT status FROM payment WHERE id=10"));
        assertEquals(2, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(1, scalarInt("SELECT pay_status FROM orders WHERE id=100"));
        assertEquals(1, mockingDetails(couponService).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("markUsed")).count());

        resetTables();
        configureMappersAndServices();
        runMigration();
        insertPendingOrder();
        execute("INSERT INTO payment(id,order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (10,100,0,'PAY-ROLLBACK',10.00,2,0,NOW())");
        doThrow(new IllegalStateException("coupon failure"))
                .when(couponService).markUsed(anyLong(), anyLong());

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> orderService.handlePayCallback(
                100L, 10L, "TRADE-ROLLBACK", LocalDateTime.now())));

        assertEquals(0, scalarInt("SELECT status FROM payment WHERE id=10"));
        assertEquals(1, scalarInt("SELECT status FROM orders WHERE id=100"));
        assertEquals(0, scalarInt("SELECT pay_status FROM orders WHERE id=100"));
    }

    @Test
    @DisplayName("支付回调与用户取消竞争只有一个赢家且无死锁")
    void callbackAndCancellationHaveOneWinner() throws Exception {
        runMigration();
        insertPendingOrder();
        execute("INSERT INTO payment(id,order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                + "VALUES (10,100,0,'PAY-RACE',10.00,2,0,NOW())");

        List<Boolean> outcomes = runConcurrently(
                () -> attempt(() -> orderService.handlePayCallback(
                        100L, 10L, "TRADE-RACE", LocalDateTime.now())),
                () -> attempt(() -> {
                    BaseContext.setUserId(7L);
                    try {
                        orderService.cancel(100L);
                    } finally {
                        BaseContext.removeUserId();
                    }
                }));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        int orderStatus = scalarInt("SELECT status FROM orders WHERE id=100");
        int payStatus = scalarInt("SELECT pay_status FROM orders WHERE id=100");
        int paymentStatus = scalarInt("SELECT status FROM payment WHERE id=10");
        assertTrue((orderStatus == 2 && payStatus == 1 && paymentStatus == 2)
                || (orderStatus == 5 && payStatus == 0 && paymentStatus == 0));
        long sideEffects = mockingDetails(couponService).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("markUsed")
                        || invocation.getMethod().getName().equals("release"))
                .count();
        assertEquals(1, sideEffects);
    }

    private void configureMappersAndServices() throws Exception {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(
                resolver.getResource("classpath:mapper/OrderMapper.xml"),
                resolver.getResource("classpath:mapper/PaymentMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertNotNull(factory);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        orderMapper = template.getMapper(OrderMapper.class);
        paymentMapper = template.getMapper(PaymentMapper.class);

        paymentService = new PaymentServiceImpl();
        ReflectionTestUtils.setField(paymentService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(paymentService, "paymentMapper", paymentMapper);
        couponService = mock(CouponService.class);
        orderService = new OrderServiceImpl();
        ReflectionTestUtils.setField(orderService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(orderService, "paymentService", paymentService);
        ReflectionTestUtils.setField(orderService, "couponService", couponService);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private static void resetTables() throws Exception {
        execute("DROP TABLE IF EXISTS payment");
        execute("DROP TABLE IF EXISTS orders");
        execute("CREATE TABLE orders (id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, number VARCHAR(50), "
                + "amount DECIMAL(10,2) NOT NULL, status INT NOT NULL, pay_status INT NOT NULL, "
                + "checkout_time DATETIME NULL, cancel_time DATETIME NULL) ENGINE=InnoDB");
        execute("CREATE TABLE payment (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, "
                + "order_type TINYINT NOT NULL, pay_no VARCHAR(64) NOT NULL, amount DECIMAL(10,2) NOT NULL, "
                + "pay_method TINYINT NOT NULL, status TINYINT NOT NULL, trade_no VARCHAR(64), "
                + "pay_time DATETIME, create_time DATETIME NOT NULL, UNIQUE KEY uk_pay_no(pay_no)) ENGINE=InnoDB");
    }

    private static void insertPendingOrder() throws Exception {
        execute("INSERT INTO orders(id,user_id,number,amount,status,pay_status) "
                + "VALUES (100,7,'ORD-100',10.00,1,0)");
    }

    private static void runMigration() throws Exception {
        Path path = Paths.get("..", "..", "mysql", "add_payment_active_unique.sql");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement jdbc = connection.createStatement()) {
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

    private void inTransaction(Runnable action) {
        transactions.executeWithoutResult(status -> action.run());
    }

    private boolean attempt(Runnable action) {
        try {
            inTransaction(action);
            return true;
        } catch (RuntimeException expectedLoser) {
            return false;
        }
    }

    private boolean tryInsertActivePayment(String payNo) throws Exception {
        try {
            execute("INSERT INTO payment(order_id,order_type,pay_no,amount,pay_method,status,create_time) "
                    + "VALUES (100,0,'" + payNo + "',10.00,2,0,NOW())");
            return true;
        } catch (SQLException duplicate) {
            return false;
        }
    }

    @SafeVarargs
    private final <T> List<T> runConcurrently(Callable<T>... tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map)) {
            throw new IllegalStateException("missing local datasource section: " + key);
        }
        return (Map<String, Object>) value;
    }

    private static String value(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            throw new IllegalStateException("missing local datasource setting");
        }
        return String.valueOf(value);
    }

    private static void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int scalarInt(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
