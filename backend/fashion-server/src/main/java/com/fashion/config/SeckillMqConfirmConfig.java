package com.fashion.config;

import com.fashion.seckill.SeckillPublisherCallbackHandler;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class SeckillMqConfirmConfig {
    private final SeckillPublisherCallbackHandler handler;

    public SeckillMqConfirmConfig(SeckillPublisherCallbackHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    @Autowired
    public void configure(RabbitTemplate rabbitTemplate) {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData != null) {
                try {
                    ReturnedMessage returned = correlationData.getReturned();
                    if (returned != null) persistReturn(returned);
                    handler.handleConfirm(correlationData.getId(), ack && returned == null,
                            returned == null ? cause : "mandatory return");
                } catch (RuntimeException persistenceFailure) {
                    log.error("SECKILL_MQ_CALLBACK_PERSISTENCE_DEFERRED callback=confirm");
                }
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            try {
                persistReturn(returned);
            } catch (RuntimeException persistenceFailure) {
                log.error("SECKILL_MQ_CALLBACK_PERSISTENCE_DEFERRED callback=return");
            }
        });
    }

    private void persistReturn(ReturnedMessage returned) {
        Message message = returned.getMessage();
        String messageId = message.getMessageProperties().getMessageId();
        Object attemptHeader = message.getMessageProperties().getHeaders().get("fsm-publish-attempt");
        if (messageId == null || !(attemptHeader instanceof Number)) return;
        handler.handleReturn(messageId, ((Number) attemptHeader).intValue(),
                returned.getReplyCode(), returned.getReplyText(),
                returned.getExchange(), returned.getRoutingKey());
    }

    @Bean("seckillManualAckContainerFactory")
    public SimpleRabbitListenerContainerFactory seckillManualAckContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(10);
        factory.setConcurrentConsumers(5);
        factory.setMaxConcurrentConsumers(20);
        return factory;
    }
}
