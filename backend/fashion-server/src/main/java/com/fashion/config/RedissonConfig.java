package com.fashion.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String host;
    
    @Value("${spring.redis.port}")
    private int port;
    
    @Value("${spring.redis.password:}")
    private String password;
    
    @Value("${spring.redis.database:0}")
    private int database;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(password.isEmpty() ? null : password);
        return Redisson.create(config);
    }
}
