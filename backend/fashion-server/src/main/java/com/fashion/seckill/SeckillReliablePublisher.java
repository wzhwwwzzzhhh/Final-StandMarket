package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
public class SeckillReliablePublisher {
    private final SeckillMessageLogMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final Clock clock;

    @Autowired
    public SeckillReliablePublisher(SeckillMessageLogMapper mapper, RabbitTemplate rabbitTemplate) {
        this(mapper, rabbitTemplate, Clock.systemDefaultZone());
    }

    SeckillReliablePublisher(SeckillMessageLogMapper mapper,
                             RabbitTemplate rabbitTemplate,
                             Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SeckillMessageLog publish(String messageId, String purpose) {
        if (mapper.claimNextPublishAttempt(messageId, purpose) != 1) {
            throw new IllegalStateException("message is not publishable");
        }
        SeckillMessageLog log = mapper.selectByMessageId(messageId);
        if (log == null || log.getCurrentCorrelationId() == null) {
            throw new IllegalStateException("claimed message is unavailable");
        }
        MessageBuilder builder = MessageBuilder.withBody(log.getPayload().getBytes(StandardCharsets.UTF_8));
        builder.setMessageId(log.getMessageId());
        builder.setContentType("application/json");
        builder.setContentEncoding(StandardCharsets.UTF_8.name());
        builder.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        builder.setHeader("fsm-message-type", log.getMessageType());
        builder.setHeader("fsm-schema-version", log.getPayloadSchemaVersion());
        builder.setHeader("fsm-business-key", log.getBusinessKey());
        builder.setHeader("fsm-publish-attempt", log.getPublishAttempt());
        builder.setHeader("fsm-consume-attempt", log.getConsumeAttempt() + 1);
        if (log.getDueAt() != null) {
            Instant dueAt = log.getDueAt().atZone(clock.getZone()).toInstant();
            builder.setExpiration(String.valueOf(SeckillDelayPolicy.remainingMillis(dueAt, clock.instant())));
        }
        Message message = builder.build();
        try {
            rabbitTemplate.send(log.getExchangeName(), log.getRoutingKey(), message,
                    new CorrelationData(log.getCurrentCorrelationId()));
            return log;
        } catch (AmqpException e) {
            int failed = mapper.markSynchronousFailure(log.getMessageId(), log.getCurrentCorrelationId(),
                    "broker unavailable");
            if (failed != 1) {
                SeckillMessageLog current = mapper.selectByMessageId(log.getMessageId());
                if (current != null && ("PROCESSING".equals(current.getStatus())
                        || "CONSUMED".equals(current.getStatus())
                        || "BROKER_ACKED".equals(current.getStatus()))) {
                    return current;
                }
            }
            throw e;
        }
    }
}
