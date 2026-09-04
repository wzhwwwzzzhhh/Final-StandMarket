package com.fashion.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.entity.Product;
import com.fashion.mapper.ProductCatalogMapper;
import com.fashion.mapper.ProductMapper;
import com.fashion.product.ProductCacheKeys;
import com.fashion.product.ProductCacheProperties;
import com.fashion.product.ProductCacheTtlPolicy;
import com.fashion.product.ProductCatalogCacheService;
import com.fashion.product.ProductCatalogVersionGate;
import com.fashion.service.ProductService;
import com.fashion.utils.CacheClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "b8.integration", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("B8 真实 MySQL + Redis 缓存一致性竞态")
class B8ProductCacheConsistencyIntegrationTest {

    private static final String SCHEMA_PATTERN = "fsm_b8_cache_[0-9a-f]{32}";

    private String adminUrl;
    private String schemaUrl;
    private String username;
    private String password;
    private String schema;
    private SqlSessionTemplate sql;
    private ProductCatalogMapper catalogMapper;
    private ProductMapper productMapper;
    private LettuceConnectionFactory redisFactory;
    private StringRedisTemplate redis;
    private CacheClient cache;

    @BeforeAll
    void connectExclusiveDependencies() throws Exception {
        connectMysql();
        connectRedis();
    }

    @BeforeEach
    void resetExactFacts() throws Exception {
        execute("DELETE FROM product_projection_reconcile_run");
        execute("DELETE FROM product_projection_task");
        execute("DELETE FROM product_catalog_revision");
        execute("DELETE FROM product");
        execute("UPDATE product_catalog_state SET list_version=100 WHERE id=1");
        assertEquals(0L, redisFactory.getConnection().serverCommands().dbSize());
    }

    @AfterAll
    void closeAndDropExactSchema() throws Exception {
        if (redisFactory != null) {
            redisFactory.destroy();
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
    @DisplayName("旧读在提交后恢复也不能写入旧代际，新读只看到新代际且物理 TTL 有界")
    void staleReaderCannotFillAcrossCommittedGeneration() throws Exception {
        execute("INSERT INTO product(id,name,price,stock,sales,status) VALUES(1,'old',10.00,8,0,1)");
        execute("INSERT INTO product_catalog_revision(product_id,item_version,item_state) VALUES(1,100,'ACTIVE')");
        cache.publishMaxVersion(ProductCacheKeys.detailPublishedVersion(1L), 100L);

        CountDownLatch oldRowRead = new CountDownLatch(1);
        CountDownLatch committed = new CountDownLatch(1);
        AtomicBoolean firstRead = new AtomicBoolean(true);
        ProductMapper delayedMapper = mock(ProductMapper.class, delegatesTo(productMapper));
        doAnswer(invocation -> {
            Product old = productMapper.getByIdIncludingInactive(invocation.getArgument(0));
            if (firstRead.compareAndSet(true, false)) {
                oldRowRead.countDown();
                assertTrue(committed.await(5, TimeUnit.SECONDS));
            }
            return old;
        }).when(delayedMapper).getByIdIncludingInactive(1L);
        ProductCatalogCacheService service = cacheService(delayedMapper, cache);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Product> oldRequest = executor.submit(() -> service.detail(1L));
            assertTrue(oldRowRead.await(5, TimeUnit.SECONDS));
            try (Connection connection = DriverManager.getConnection(schemaUrl, username, password)) {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE product SET name='new',stock=7 WHERE id=1");
                    statement.executeUpdate("UPDATE product_catalog_state SET list_version=101 WHERE id=1");
                    statement.executeUpdate("UPDATE product_catalog_revision SET item_version=101 WHERE product_id=1");
                }
                connection.commit();
            }
            cache.publishMaxVersion(ProductCacheKeys.detailPublishedVersion(1L), 101L);
            committed.countDown();

            assertEquals("old", oldRequest.get(5, TimeUnit.SECONDS).getName());
            assertNull(redis.opsForValue().get(ProductCacheKeys.detail(1L, 100L)));
            Product fresh = service.detail(1L);
            assertEquals("new", fresh.getName());
            assertEquals(7, fresh.getStock());
            String newKey = ProductCacheKeys.detail(1L, 101L);
            assertNotNull(redis.opsForValue().get(newKey));
            Long ttl = redis.getExpire(newKey, TimeUnit.MILLISECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= Duration.ofMinutes(30).toMillis());
            redis.delete(newKey);
            redis.delete(ProductCacheKeys.detailPublishedVersion(1L));
        } finally {
            committed.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("真实客户端连接不可达 Redis 时从 MySQL 安全读取且不把依赖故障伪装为无商品")
    void unreachableRedisFailsSafeToMysqlAuthority() throws Exception {
        execute("INSERT INTO product(id,name,price,stock,sales,status) VALUES(2,'mysql-only',20.00,5,0,1)");
        execute("INSERT INTO product_catalog_revision(product_id,item_version,item_state) VALUES(2,100,'ACTIVE')");
        LettuceConnectionFactory unavailableFactory = unavailableRedisFactory();
        try {
            StringRedisTemplate unavailable = new StringRedisTemplate(unavailableFactory);
            unavailable.afterPropertiesSet();
            ProductCatalogCacheService service = cacheService(productMapper, new CacheClient(unavailable));

            Product product = service.detail(2L);

            assertEquals("mysql-only", product.getName());
            assertEquals(5, product.getStock());
            assertFalse(redis.hasKey(ProductCacheKeys.detail(2L, 100L)));
        } finally {
            unavailableFactory.destroy();
        }
    }

    private ProductCatalogCacheService cacheService(ProductMapper mapper, CacheClient selectedCache) {
        ProductCacheProperties properties = new ProductCacheProperties();
        properties.setActualJitter(Duration.ZERO);
        ProductCatalogVersionGate gate = new ProductCatalogVersionGate(catalogMapper, selectedCache);
        return new ProductCatalogCacheService(mock(ProductService.class), mapper, gate, selectedCache,
                properties, new ProductCacheTtlPolicy(() -> 0L), new ObjectMapper().findAndRegisterModules(),
                Runnable::run);
    }

    private void connectMysql() throws Exception {
        Map<String, Object> datasource = B8IntegrationSettings.section("datasource");
        String host = B8IntegrationSettings.value(datasource, "host");
        B8IntegrationSettings.requireLoopback(host, "MySQL");
        username = B8IntegrationSettings.value(datasource, "username");
        password = B8IntegrationSettings.value(datasource, "password");
        adminUrl = "jdbc:mysql://" + host + ":" + B8IntegrationSettings.value(datasource, "port")
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        schema = "fsm_b8_cache_" + UUID.randomUUID().toString().replace("-", "");
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        schemaUrl = adminUrl.replace("/?", "/" + schema + "?");
        execute("CREATE TABLE product(id BIGINT NOT NULL AUTO_INCREMENT,name VARCHAR(100) NOT NULL,"
                + "description TEXT NULL,price DECIMAL(10,2) NOT NULL,stock INT NOT NULL,sales INT NULL DEFAULT 0,"
                + "image VARCHAR(500) NULL,category_id BIGINT NULL,status INT NOT NULL,tag VARCHAR(100) NULL,"
                + "create_time DATETIME(3) NULL,update_time DATETIME(3) NULL,create_user BIGINT NULL,"
                + "update_user BIGINT NULL,PRIMARY KEY(id)) ENGINE=InnoDB");
        B8MigrationRunner.run(schemaUrl, username, password);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(schemaUrl, username, password);
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        SqlSessionFactory sessionFactory = factory.getObject();
        assertNotNull(sessionFactory);
        sql = new SqlSessionTemplate(sessionFactory);
        catalogMapper = sql.getMapper(ProductCatalogMapper.class);
        productMapper = sql.getMapper(ProductMapper.class);
    }

    private void connectRedis() throws Exception {
        Map<String, Object> settings = B8IntegrationSettings.section("redis");
        String host = B8IntegrationSettings.value(settings, "host");
        B8IntegrationSettings.requireLoopback(host, "Redis");
        assertEquals("true", B8IntegrationSettings.exclusive("redis", settings));
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(host);
        standalone.setPort(Integer.parseInt(B8IntegrationSettings.value(settings, "port")));
        standalone.setDatabase(Integer.parseInt(B8IntegrationSettings.value(settings, "database")));
        String redisPassword = B8IntegrationSettings.value(settings, "password");
        if (!redisPassword.isEmpty()) {
            standalone.setPassword(RedisPassword.of(redisPassword));
        }
        redisFactory = new LettuceConnectionFactory(standalone);
        redisFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisFactory);
        redis.afterPropertiesSet();
        assertTrue(redisFactory.getConnection().serverCommands().info("server")
                .getProperty("redis_version").startsWith("7.0."));
        assertEquals(0L, redisFactory.getConnection().serverCommands().dbSize());
        cache = new CacheClient(redis);
    }

    private LettuceConnectionFactory unavailableRedisFactory() {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration("127.0.0.1", 1);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(250)).build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client);
        factory.afterPropertiesSet();
        return factory;
    }

    private void execute(String statementSql) throws Exception {
        validateSchema(schema);
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(statementSql);
        }
    }

    private long scalarLong(String statementSql) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaUrl, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(statementSql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void validateSchema(String candidate) {
        if (candidate == null || !candidate.matches(SCHEMA_PATTERN)) {
            throw new IllegalStateException("unsafe B8 cache schema");
        }
    }
}
