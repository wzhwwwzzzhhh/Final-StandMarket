package com.fashion.integration;

import com.fashion.context.BaseContext;
import com.fashion.dto.OrderCreateDTO;
import com.fashion.dto.ReviewCreateDTO;
import com.fashion.entity.CouponTemplate;
import com.fashion.exception.BaseException;
import com.fashion.mapper.CouponTemplateMapper;
import com.fashion.mapper.OrderDetailMapper;
import com.fashion.mapper.OrderMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.mapper.ReviewMapper;
import com.fashion.mapper.ShoppingCartMapper;
import com.fashion.mapper.UserCouponMapper;
import com.fashion.service.CouponService;
import com.fashion.service.OrderService;
import com.fashion.service.PaymentService;
import com.fashion.service.ReviewService;
import com.fashion.service.impl.CouponServiceImpl;
import com.fashion.service.impl.OrderCancellationService;
import com.fashion.service.impl.OrderServiceImpl;
import com.fashion.service.impl.ReviewServiceImpl;
import com.fashion.vo.ReviewPublicVO;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "b7.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B7 真实 MySQL 优惠券与评价事务")
class B7CouponReviewMysqlIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b7_coupon_review_[0-9a-f]{32}";

    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private CouponService couponService;
    private CouponTemplateMapper couponTemplateMapper;
    private ReviewService reviewService;
    private OrderService orderService;
    private TransactionTemplate transactions;

    @BeforeAll
    void createSchemaAndContext() throws Exception {
        Map<String, Object> datasource = datasourceSettings();
        String host = value(datasource, "host");
        requireLoopback(host);
        username = value(datasource, "username");
        password = value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("select version()")) {
            assertTrue(version.next());
            assertTrue(version.getString(1).startsWith("8.0."), "B7 requires MySQL 8.0.x");
        }
        schema = "fsm_b7_coupon_review_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        createTables();
        B7MigrationRunner.run(schemaUrl, username, password);

        context = new AnnotationConfigApplicationContext();
        Map<String, Object> properties = new HashMap<>();
        properties.put("b7.test.jdbc-url", schemaUrl);
        properties.put("b7.test.username", username);
        properties.put("b7.test.password", password);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("b7Mysql", properties));
        context.register(SpringMysqlConfig.class);
        context.refresh();
        couponService = context.getBean(CouponService.class);
        couponTemplateMapper = context.getBean(CouponTemplateMapper.class);
        reviewService = context.getBean(ReviewService.class);
        orderService = context.getBean(OrderService.class);
        transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    }

    @AfterAll
    void closeAndDrop() throws Exception {
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

    @BeforeEach
    void resetData() throws Exception {
        execute("DROP TRIGGER IF EXISTS b7_fail_orders");
        execute("DROP TRIGGER IF EXISTS b7_fail_coupon_bind");
        execute("DROP TRIGGER IF EXISTS b7_fail_detail");
        execute("DELETE FROM review");
        execute("DELETE FROM user_coupon");
        execute("DELETE FROM coupon_template");
        execute("DELETE FROM shopping_cart");
        execute("DELETE FROM order_detail");
        execute("DELETE FROM orders");
        execute("DELETE FROM product");
        execute("DELETE FROM `user`");
        execute("INSERT INTO `user`(id,name) VALUES(7,'😀用户'),(8,'他人')");
        execute("INSERT INTO product(id,name,price,stock,status,category_id) VALUES"
                + "(20,'外套',30.00,10,1,100),(21,'裤子',40.00,10,1,101)");
        execute("INSERT INTO orders(id,user_id,status) VALUES(10,7,4),(11,8,4),(12,7,3)");
        execute("INSERT INTO order_detail(id,order_id,product_id) VALUES"
                + "(1,10,20),(2,10,21),(3,11,20),(4,12,20)");
        execute("INSERT INTO shopping_cart(id,user_id,product_id,name,number) VALUES"
                + "(31,7,20,'外套',2),(32,7,20,'外套',2)");
    }

    @AfterEach
    void cleanContext() {
        BaseContext.removeUserId();
    }

    @Test
    @DisplayName("本人已完成订单商品可评价，同订单不同商品可分别评价，重复键稳定失败")
    void reviewAuthorizationAndUniqueKeyAreEnforced() throws Exception {
        BaseContext.setUserId(7L);
        reviewService.addReview(review(10L, 20L));
        reviewService.addReview(review(10L, 21L));
        BaseException duplicate = assertThrows(BaseException.class,
                () -> reviewService.addReview(review(10L, 20L)));

        assertEquals("该订单商品已评价", duplicate.getMessage());
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM review WHERE order_id=10"));
    }

    @Test
    @DisplayName("他人订单、未完成订单和订单外商品均由受约束写入拒绝")
    void reviewEligibilityFailsClosed() throws Exception {
        BaseContext.setUserId(7L);
        for (ReviewCreateDTO request : Arrays.asList(
                review(11L, 20L), review(12L, 20L), review(10L, 999L))) {
            BaseException error = assertThrows(BaseException.class,
                    () -> reviewService.addReview(request));
            assertEquals("订单不存在、未完成或商品不属于订单", error.getMessage());
        }
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM review"));
    }

    @Test
    @DisplayName("并发评价同一订单商品只有一个成功")
    void concurrentDuplicateReviewsCreateOneRow() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> submitReviewConcurrently(ready, start));
            Future<Boolean> second = pool.submit(() -> submitReviewConcurrently(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM review WHERE order_id=10 AND product_id=20"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("公开列表和统计只计同一商品的显示评价并按 Unicode code point 脱敏")
    void publicReviewQueryHidesInternalAndHiddenRows() throws Exception {
        execute("INSERT INTO review(user_id,product_id,order_id,rating,content,status,create_time) VALUES"
                + "(7,20,10,5,'显示',1,NOW(3)),(8,20,11,1,'隐藏',0,NOW(3))");

        java.util.List<ReviewPublicVO> records =
                reviewService.getProductReviews(20L, 1, 10, null).getRecords();
        Map<String, Object> stats = reviewService.getReviewStats(20L);

        assertEquals(1, records.size());
        assertEquals("😀**", records.get(0).getDisplayName());
        assertEquals(1, ((Number) stats.get("total_count")).intValue());
        assertEquals(1, ((Number) stats.get("star5")).intValue());
        assertEquals(0, ((Number) stats.get("star1")).intValue());
    }

    @Test
    @DisplayName("可领取模板列表与领取资格使用相同的合法域和半开时间区间")
    void claimableTemplateListUsesTheSameEligibilityDomain() throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,valid_days,start_time,end_time,scope_type,status) VALUES"
                + "(2,'滚动有效',1,0,5,10,1,2,3,NULL,NULL,0,1),"
                + "(3,'非法滚动',1,0,5,10,1,2,0,NULL,NULL,0,1),"
                + "(4,'固定有效',1,0,5,10,1,1,NULL,DATE_SUB(NOW(3),INTERVAL 1 MINUTE),"
                + "DATE_ADD(NOW(3),INTERVAL 1 MINUTE),0,1),"
                + "(5,'固定已结束',1,0,5,10,1,1,NULL,DATE_SUB(NOW(3),INTERVAL 2 MINUTE),"
                + "DATE_SUB(NOW(3),INTERVAL 1 MINUTE),0,1)");

        List<Long> ids = new ArrayList<>();
        for (CouponTemplate template : couponTemplateMapper.listClaimable()) {
            ids.add(template.getId());
        }

        assertEquals(Arrays.asList(4L, 2L), ids);
    }

    @Test
    @DisplayName("他人券、非未使用状态、持有券过期和固定期无效均不能锁券")
    void holderAndFixedWindowStateMatrixFailsClosed() throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,valid_days,scope_type,status) VALUES(1,'滚动券',1,0,5,20,5,2,7,0,1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES"
                + "(1,8,1,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY)),"
                + "(2,7,1,1,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY)),"
                + "(3,7,1,2,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY)),"
                + "(4,7,1,3,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY)),"
                + "(5,7,1,0,DATE_SUB(NOW(3),INTERVAL 2 DAY),DATE_SUB(NOW(3),INTERVAL 1 DAY))");
        for (long holderId = 1; holderId <= 5; holderId++) {
            final long candidate = holderId;
            assertThrows(BaseException.class, () -> transactions.execute(status ->
                    couponService.lockAndDiscount(7L, candidate, new BigDecimal("100.00"),
                            Arrays.asList(20L))));
        }

        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,start_time,end_time,scope_type,status) VALUES"
                + "(6,'未开始',1,0,5,10,1,1,DATE_ADD(NOW(3),INTERVAL 1 DAY),"
                + "DATE_ADD(NOW(3),INTERVAL 2 DAY),0,1),"
                + "(7,'已结束',1,0,5,10,1,1,DATE_SUB(NOW(3),INTERVAL 2 DAY),"
                + "DATE_SUB(NOW(3),INTERVAL 1 DAY),0,1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES"
                + "(6,7,6,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 2 DAY)),"
                + "(7,7,7,0,DATE_SUB(NOW(3),INTERVAL 2 DAY),DATE_ADD(NOW(3),INTERVAL 1 DAY))");
        for (long holderId = 6; holderId <= 7; holderId++) {
            final long candidate = holderId;
            assertThrows(BaseException.class, () -> transactions.execute(status ->
                    couponService.lockAndDiscount(7L, candidate, new BigDecimal("100.00"),
                            Arrays.asList(20L))));
        }
        int[] expectedStatuses = {0, 1, 2, 3, 0, 0, 0};
        for (int index = 0; index < expectedStatuses.length; index++) {
            long holderId = index + 1L;
            assertEquals(expectedStatuses[index],
                    scalarInt("SELECT status FROM user_coupon WHERE id=" + holderId));
        }
    }

    @Test
    @DisplayName("分类券和商品券必须覆盖订单全部商品")
    void couponScopeUsesTheCompleteOrderProductSet() throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,valid_days,scope_type,apply_category_id,apply_product_ids,status) VALUES"
                + "(1,'分类券',1,0,5,10,1,2,7,1,100,NULL,1),"
                + "(2,'商品券',1,0,5,10,1,2,7,2,NULL,'20',1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES"
                + "(1,7,1,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY)),"
                + "(2,7,2,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY))");
        for (long holderId = 1; holderId <= 2; holderId++) {
            final long candidate = holderId;
            assertThrows(BaseException.class, () -> transactions.execute(status ->
                    couponService.lockAndDiscount(7L, candidate, new BigDecimal("100.00"),
                            Arrays.asList(20L, 21L))));
        }
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM user_coupon WHERE status=3"));
    }

    @Test
    @DisplayName("JVM 默认时区偏移不影响数据库时间资格判断")
    void jvmDefaultTimezoneDoesNotControlEligibility() throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,start_time,end_time,scope_type,status) VALUES"
                + "(1,'固定券',1,0,5,10,1,1,DATE_SUB(NOW(3),INTERVAL 1 MINUTE),"
                + "DATE_ADD(NOW(3),INTERVAL 1 MINUTE),0,1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES"
                + "(1,7,1,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY))");
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-12:00"));
            BigDecimal discount = transactions.execute(status -> {
                BigDecimal result = couponService.lockAndDiscount(
                        7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L));
                status.setRollbackOnly();
                return result;
            });
            assertEquals(new BigDecimal("5.00"), discount);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("模板 X 锁跨越开始与结束边界后以释放锁后的 DB 时间决定资格")
    void databaseTimeIsReadAfterTemplateLockAcrossBoundaries() throws Exception {
        assertEligibilityAfterTemplateXLockBoundary(true);
        resetData();
        assertEligibilityAfterTemplateXLockBoundary(false);
    }

    @Test
    @DisplayName("持有模板共享锁至事务提交会阻塞管理更新")
    void templateManagementUpdateWaitsForCouponTransaction() throws Exception {
        insertUsableCoupon(1L, 1L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch sharedLockHeld = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        try {
            Future<Boolean> use = pool.submit(() -> Boolean.TRUE.equals(transactions.execute(status -> {
                couponService.lockAndDiscount(7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L));
                sharedLockHeld.countDown();
                try {
                    if (!allowCommit.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("commit barrier timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return true;
            })));
            assertTrue(sharedLockHeld.await(5, TimeUnit.SECONDS));
            Future<Integer> update = pool.submit(() -> updateTemplateStatus(1L, 0, updateStarted));
            assertTrue(updateStarted.await(5, TimeUnit.SECONDS),
                    "management update did not reach executeUpdate");
            Thread.sleep(250L);
            assertFalse(update.isDone(), "management update must wait for the shared template lock");
            allowCommit.countDown();
            assertTrue(use.get(5, TimeUnit.SECONDS));
            assertEquals(1, update.get(5, TimeUnit.SECONDS));
        } finally {
            allowCommit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("持有券 CAS 与订单外层事务同生共死")
    void couponLockRollsBackWithOuterTransaction() throws Exception {
        insertUsableCoupon(1L, 1L);
        BigDecimal discount = transactions.execute(status -> {
            BigDecimal value = couponService.lockAndDiscount(
                    7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L));
            status.setRollbackOnly();
            return value;
        });

        assertEquals(new BigDecimal("10.00"), discount);
        assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
    }

    @Test
    @DisplayName("锁券和绑定订单在外层事务外失败关闭")
    void couponMutationRequiresOuterTransaction() throws Exception {
        insertUsableCoupon(1L, 1L);

        assertThrows(IllegalTransactionStateException.class, () ->
                couponService.lockAndDiscount(
                        7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L)));
        assertThrows(IllegalTransactionStateException.class, () ->
                couponService.bindUseOrder(7L, 1L, 10L));
        assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
    }

    @Test
    @DisplayName("模板停用时共享锁快照拒绝 CAS")
    void disabledTemplateCannotLockCoupon() throws Exception {
        insertUsableCoupon(1L, 1L);
        execute("UPDATE coupon_template SET status=0 WHERE id=1");

        assertThrows(BaseException.class, () -> transactions.execute(status ->
                couponService.lockAndDiscount(7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L))));

        assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
    }

    @Test
    @DisplayName("同一持有券并发 CAS 只有一个事务成功")
    void concurrentCouponUseHasOneWinner() throws Exception {
        insertUsableCoupon(1L, 1L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> lockCouponConcurrently(1L, ready, start, null));
            Future<Boolean> second = pool.submit(() -> lockCouponConcurrently(1L, ready, start, null));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(3, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("完整下单在订单、券绑定或明细故障时整体回滚")
    void orderCreationRollsBackAtEveryPostLockFailurePoint() throws Exception {
        insertUsableCoupon(1L, 1L);

        assertOrderRollbackWithTrigger("b7_fail_orders",
                "BEFORE INSERT ON orders FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='b7 order failure'");
        assertOrderRollbackWithTrigger("b7_fail_coupon_bind",
                "BEFORE UPDATE ON user_coupon FOR EACH ROW BEGIN "
                        + "IF NEW.use_order_id IS NOT NULL THEN "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='b7 coupon bind failure'; END IF; END");
        assertOrderRollbackWithTrigger("b7_fail_detail",
                "BEFORE INSERT ON order_detail FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='b7 detail failure'");
    }

    @Test
    @DisplayName("完整下单并发使用同一持有券最多一个事务提交")
    void concurrentOrdersUsingSameCouponHaveOneCommit() throws Exception {
        insertUsableCoupon(1L, 1L);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> createOrderConcurrently(31L, ready, start));
            Future<Boolean> second = pool.submit(() -> createOrderConcurrently(32L, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);

            assertEquals(1, successes);
            assertEquals(8, scalarInt("SELECT stock FROM product WHERE id=20"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM orders WHERE id NOT IN (10,11,12)"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM order_detail WHERE id NOT IN (1,2,3,4)"));
            assertEquals(3, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM user_coupon WHERE id=1 AND use_order_id IS NOT NULL"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("同一模板的不同持有券可同时越过共享锁")
    void sharedTemplateLockDoesNotSerializeDifferentHolders() throws Exception {
        insertUsableCoupon(1L, 1L);
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) "
                + "VALUES(2,7,1,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY))");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CyclicBarrier afterLock = new CyclicBarrier(2);
        try {
            Future<Boolean> first = pool.submit(() -> lockCouponConcurrently(1L, ready, start, afterLock));
            Future<Boolean> second = pool.submit(() -> lockCouponConcurrently(2L, ready, start, afterLock));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(first.get());
            assertTrue(second.get());
            assertEquals(2, scalarInt("SELECT COUNT(*) FROM user_coupon WHERE status=3"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("领券的 obtain_time 与 expire_time 来自 MySQL 时间")
    void claimUsesDatabaseTime() throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,valid_days,scope_type,status) VALUES"
                + "(2,'领取券',1,0,5,10,1,2,3,0,1)");
        long before = scalarLong("SELECT UNIX_TIMESTAMP(NOW(3))*1000");

        couponService.claim(7L, 2L);

        long obtain = scalarLong("SELECT UNIX_TIMESTAMP(obtain_time)*1000 FROM user_coupon WHERE template_id=2");
        long after = scalarLong("SELECT UNIX_TIMESTAMP(NOW(3))*1000");
        assertTrue(obtain >= before && obtain <= after);
        assertEquals(3, scalarInt("SELECT DATEDIFF(expire_time,obtain_time) FROM user_coupon WHERE template_id=2"));
    }

    private void assertEligibilityAfterTemplateXLockBoundary(boolean becomesValid) throws Exception {
        String start = becomesValid
                ? "DATE_ADD(NOW(3),INTERVAL 3 SECOND)"
                : "DATE_SUB(NOW(3),INTERVAL 1 MINUTE)";
        String end = becomesValid
                ? "DATE_ADD(NOW(3),INTERVAL 1 MINUTE)"
                : "DATE_ADD(NOW(3),INTERVAL 3 SECOND)";
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,start_time,end_time,scope_type,status) VALUES"
                + "(1,'边界券',1,0,5,10,1,1," + start + "," + end + ",0,1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES"
                + "(1,7,1,0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY))");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch useStarted = new CountDownLatch(1);
        try (Connection manager = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = manager.createStatement()) {
            manager.setAutoCommit(false);
            try (ResultSet ignored = statement.executeQuery(
                    "SELECT id FROM coupon_template WHERE id=1 FOR UPDATE")) {
                assertTrue(ignored.next());
            }
            long boundaryAt = scalarLong(manager, "SELECT UNIX_TIMESTAMP("
                    + (becomesValid ? "start_time" : "end_time")
                    + ")*1000 FROM coupon_template WHERE id=1");
            long lockedAt = scalarLong(manager, "SELECT UNIX_TIMESTAMP(NOW(3))*1000");
            assertTrue(lockedAt < boundaryAt,
                    "template X lock must be acquired before the tested DB-time boundary");
            Future<Boolean> use = pool.submit(() -> {
                useStarted.countDown();
                try {
                    return Boolean.TRUE.equals(transactions.execute(status -> {
                        couponService.lockAndDiscount(
                                7L, 1L, new BigDecimal("100.00"), Arrays.asList(20L));
                        status.setRollbackOnly();
                        return true;
                    }));
                } catch (BaseException unavailable) {
                    assertEquals("优惠券不可用", unavailable.getMessage());
                    return false;
                }
            });
            assertTrue(useStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(100L);
            assertFalse(use.isDone(), "coupon transaction must wait for the template X lock");
            long releaseAt = scalarLong(manager, "SELECT UNIX_TIMESTAMP(NOW(3))*1000");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (releaseAt < boundaryAt && System.nanoTime() < deadline) {
                Thread.sleep(Math.min(100L, Math.max(1L, boundaryAt - releaseAt)));
                releaseAt = scalarLong(manager, "SELECT UNIX_TIMESTAMP(NOW(3))*1000");
            }
            assertTrue(releaseAt >= boundaryAt,
                    "template X lock must remain held until DB time crosses the tested boundary");
            assertFalse(use.isDone(), "coupon transaction must still wait after the DB-time boundary");
            manager.commit();
            assertEquals(becomesValid, use.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
    }

    private int updateTemplateStatus(long templateId, int status,
                                     CountDownLatch updateStarted) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "UPDATE coupon_template SET status=? WHERE id=?")) {
            statement.setInt(1, status);
            statement.setLong(2, templateId);
            updateStarted.countDown();
            return statement.executeUpdate();
        }
    }

    private void createTables() throws Exception {
        execute("CREATE TABLE `user`(id BIGINT PRIMARY KEY,name VARCHAR(64)) ENGINE=InnoDB");
        execute("CREATE TABLE product(id BIGINT PRIMARY KEY,name VARCHAR(64),price DECIMAL(10,2),"
                + "stock INT,status INT,category_id BIGINT) ENGINE=InnoDB");
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
        execute("CREATE TABLE shopping_cart(id BIGINT PRIMARY KEY,user_id BIGINT,product_id BIGINT,name VARCHAR(64),"
                + "image VARCHAR(255),combination_id BIGINT,sku_info VARCHAR(255),number INT,amount DECIMAL(10,2),"
                + "create_time DATETIME(3)) ENGINE=InnoDB");
        execute("CREATE TABLE coupon_template(id BIGINT PRIMARY KEY,name VARCHAR(64),type INT,threshold DECIMAL(10,2),"
                + "discount DECIMAL(10,2),total_count INT,per_user_limit INT,valid_type INT,valid_days INT,"
                + "start_time DATETIME(3),end_time DATETIME(3),scope_type INT,apply_category_id BIGINT,"
                + "apply_product_ids VARCHAR(1000),status INT,create_time DATETIME(3),update_time DATETIME(3)) ENGINE=InnoDB");
        execute("CREATE TABLE user_coupon(id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,"
                + "template_id BIGINT NOT NULL,status INT NOT NULL,obtain_time DATETIME(3) NOT NULL,"
                + "expire_time DATETIME(3) NOT NULL,use_order_id BIGINT,use_time DATETIME(3)) ENGINE=InnoDB");
        execute("CREATE TABLE review(id BIGINT PRIMARY KEY AUTO_INCREMENT,user_id BIGINT NOT NULL,"
                + "product_id BIGINT NOT NULL,order_id BIGINT NOT NULL,rating TINYINT NOT NULL,"
                + "content VARCHAR(500),images VARCHAR(1000),status TINYINT NOT NULL DEFAULT 1,"
                + "create_time DATETIME(3) NOT NULL,update_time DATETIME(3)) ENGINE=InnoDB");
    }

    private void insertUsableCoupon(long templateId, long holderId) throws Exception {
        execute("INSERT INTO coupon_template(id,name,type,threshold,discount,total_count,per_user_limit,"
                + "valid_type,valid_days,scope_type,status) VALUES(" + templateId
                + ",'满减券',1,50,10,10,1,2,7,0,1)");
        execute("INSERT INTO user_coupon(id,user_id,template_id,status,obtain_time,expire_time) VALUES("
                + holderId + ",7," + templateId + ",0,NOW(3),DATE_ADD(NOW(3),INTERVAL 1 DAY))");
    }

    private ReviewCreateDTO review(long orderId, long productId) {
        ReviewCreateDTO review = new ReviewCreateDTO();
        review.setOrderId(orderId);
        review.setProductId(productId);
        review.setRating(5);
        review.setContent("很好");
        return review;
    }

    private boolean submitReviewConcurrently(CountDownLatch ready, CountDownLatch start) throws Exception {
        BaseContext.setUserId(7L);
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            reviewService.addReview(review(10L, 20L));
            return true;
        } catch (BaseException duplicate) {
            assertEquals("该订单商品已评价", duplicate.getMessage());
            return false;
        } finally {
            BaseContext.removeUserId();
        }
    }

    private boolean lockCouponConcurrently(long holderId, CountDownLatch ready,
                                           CountDownLatch start, CyclicBarrier afterLock) throws Exception {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        try {
            return Boolean.TRUE.equals(transactions.execute(status -> {
                couponService.lockAndDiscount(7L, holderId, new BigDecimal("100.00"), Arrays.asList(20L));
                if (afterLock != null) {
                    try {
                        afterLock.await(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException("shared template barrier failed", e);
                    }
                }
                return true;
            }));
        } catch (BaseException unavailable) {
            assertEquals("优惠券不可用", unavailable.getMessage());
            return false;
        }
    }

    private void assertOrderRollbackWithTrigger(String triggerName, String triggerBody) throws Exception {
        execute("CREATE TRIGGER " + triggerName + " " + triggerBody);
        try {
            BaseContext.setUserId(7L);
            assertThrows(RuntimeException.class, () -> orderService.create(orderRequest(31L)));
            assertEquals(10, scalarInt("SELECT stock FROM product WHERE id=20"));
            assertEquals(0, scalarInt("SELECT status FROM user_coupon WHERE id=1"));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM user_coupon WHERE id=1 AND use_order_id IS NOT NULL"));
            assertEquals(3, scalarInt("SELECT COUNT(*) FROM orders"));
            assertEquals(4, scalarInt("SELECT COUNT(*) FROM order_detail"));
        } finally {
            BaseContext.removeUserId();
            execute("DROP TRIGGER IF EXISTS " + triggerName);
        }
    }

    private boolean createOrderConcurrently(long cartItemId, CountDownLatch ready,
                                            CountDownLatch start) throws Exception {
        BaseContext.setUserId(7L);
        try {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            orderService.create(orderRequest(cartItemId));
            return true;
        } catch (BaseException unavailable) {
            assertEquals("优惠券不可用", unavailable.getMessage());
            return false;
        } finally {
            BaseContext.removeUserId();
        }
    }

    private OrderCreateDTO orderRequest(long cartItemId) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setProductIds(java.util.Collections.singletonList(cartItemId));
        request.setUserCouponId(1L);
        request.setAmount(new BigDecimal("0.01"));
        request.setActivityId(999L);
        request.setCouponId(998L);
        return request;
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

    private void requireLoopback(String host) {
        if (!("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host))) {
            throw new IllegalStateException("B7 integration refuses non-loopback MySQL");
        }
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B7 schema");
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int scalarInt(String sql) throws Exception {
        return (int) scalarLong(sql);
    }

    private long scalarLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private long scalarLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {CouponTemplateMapper.class, UserCouponMapper.class,
            ProductMapper.class, ShoppingCartMapper.class, ReviewMapper.class})
    static class SpringMysqlConfig {
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
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
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
        RedissonClient redissonClient() throws Exception {
            RedissonClient client = mock(RedissonClient.class);
            RLock lock = mock(RLock.class);
            when(client.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(true);
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            return client;
        }

        @Bean
        CouponService couponService() {
            return new CouponServiceImpl();
        }

        @Bean
        ReviewService reviewService() {
            return new ReviewServiceImpl();
        }

        @Bean
        OrderService orderService() {
            return new OrderServiceImpl();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            @SuppressWarnings("unchecked")
            ValueOperations<String, String> values = mock(ValueOperations.class);
            AtomicLong sequence = new AtomicLong(1000L);
            when(redis.opsForValue()).thenReturn(values);
            when(values.increment(anyString())).thenAnswer(invocation -> sequence.incrementAndGet());
            return redis;
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
