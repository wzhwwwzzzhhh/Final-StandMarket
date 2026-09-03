package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("B6 RabbitMQ 连接失败时间上限配置")
class B6RabbitPropertiesTest {
    @Test
    void applicationConfigurationBindsTwoSecondConnectionTimeout() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
                .withPropertyValues(
                        "fashion.rabbitmq.host=127.0.0.1",
                        "fashion.rabbitmq.port=1",
                        "fashion.rabbitmq.username=b6",
                        "fashion.rabbitmq.password=b6",
                        "fashion.rabbitmq.virtual-host=/")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    CachingConnectionFactory factory = context.getBean(CachingConnectionFactory.class);
                    assertEquals(2000, factory.getRabbitConnectionFactory().getConnectionTimeout());
                });
    }
}
