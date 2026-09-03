package com.fashion.seckill;

import com.fashion.config.DirectExchangeConfig;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.service.SeckillOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class SeckillTimeoutConsumer {
    private final SeckillOrderService orderService;
    private final SeckillMessageLogMapper messageMapper;
    private final SeckillConsumeFailureTransaction failureTransaction;
    private final SeckillInvalidMessageService invalidMessageService;
    private final SeckillListenerPauser pauser;

    public SeckillTimeoutConsumer(SeckillOrderService orderService,
                                  SeckillMessageLogMapper messageMapper,
                                  SeckillConsumeFailureTransaction failureTransaction,
                                  SeckillInvalidMessageService invalidMessageService,
                                  SeckillListenerPauser pauser) {
        this.orderService = Objects.requireNonNull(orderService, "orderService");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
        this.failureTransaction = Objects.requireNonNull(failureTransaction, "failureTransaction");
        this.invalidMessageService = Objects.requireNonNull(invalidMessageService, "invalidMessageService");
        this.pauser = Objects.requireNonNull(pauser, "pauser");
    }

    @RabbitListener(id = "seckillTimeoutConsumer", queues = DirectExchangeConfig.deadQueue,
            containerFactory = "seckillManualAckContainerFactory")
    public void consume(Message envelope, Channel channel) throws IOException {
        long tag = envelope.getMessageProperties().getDeliveryTag();
        String messageId = envelope.getMessageProperties().getMessageId();
        Long orderId;
        int incomingAttempt;
        try {
            byte[] body = envelope.getBody();
            if (body.length > 32) {
                throw new IllegalArgumentException("payload exceeds quarantine limit");
            }
            String payload = new String(body, StandardCharsets.UTF_8);
            if (!payload.matches("[1-9][0-9]{0,18}")
                    || messageId == null
                    || !messageId.matches("SECKILL_ORDER_TIMEOUT:[0-9]{1,50}")) {
                throw new IllegalArgumentException("invalid ORDER_TIMEOUT identity");
            }
            orderId = Long.valueOf(payload);
            incomingAttempt = SeckillEnvelopeContract.require(envelope, "ORDER_TIMEOUT",
                    messageId.substring("SECKILL_ORDER_TIMEOUT:".length()));
        } catch (RuntimeException invalidEnvelope) {
            quarantineOrRequeue(envelope, channel, tag, messageId,
                    invalidClassification(invalidEnvelope));
            return;
        }

        String claimToken = "b6-timeout-" + UUID.randomUUID();
        try {
            String businessKey = messageId.substring("SECKILL_ORDER_TIMEOUT:".length());
            String expectedPayload = String.valueOf(orderId);
            int claimed;
            try {
                claimed = messageMapper.claimTimeoutConsumeAttempt(
                        messageId, incomingAttempt, businessKey, expectedPayload, claimToken);
            } catch (org.springframework.dao.DataAccessException persistenceUnavailable) {
                pauseAndRequeue(channel, tag, messageId);
                return;
            }
            if (claimed != 1) {
                com.fashion.entity.SeckillMessageLog persisted = messageMapper.selectByMessageId(messageId);
                if (!equivalentTimeoutIdentity(persisted, businessKey, expectedPayload)) {
                    quarantineOrRequeue(envelope, channel, tag, messageId,
                            "SOURCE_IDENTITY_MISMATCH");
                    return;
                }
                if (persisted != null && "PROCESSING".equals(persisted.getStatus())) {
                    // The durable lease owns recovery. Acking an equivalent broker duplicate avoids
                    // a hot requeue loop; an abandoned lease is moved back to the publish track by
                    // SeckillMessageRecoveryTask after locked_until.
                    channel.basicAck(tag, false);
                    return;
                }
                if (persisted != null && ("CONSUMED".equals(persisted.getStatus())
                        || "CONSUME_EXHAUSTED".equals(persisted.getStatus())
                        || "MANUAL_REQUIRED".equals(persisted.getStatus())
                        || (persisted.getConsumeAttempt() != null
                            && persisted.getConsumeAttempt() >= incomingAttempt))) {
                    channel.basicAck(tag, false);
                    return;
                }
                throw new IllegalStateException("ORDER_TIMEOUT attempt could not be claimed");
            }
            orderService.cancelTimeoutOrder(orderId);
            if (messageMapper.markTimeoutConsumedAttempt(messageId, incomingAttempt, claimToken) != 1) {
                throw new IllegalStateException("timeout consume result could not be persisted");
            }
        } catch (RuntimeException consumeFailure) {
            try {
                failureTransaction.recordTimeout(messageId, incomingAttempt,
                        safeSummary(consumeFailure), claimToken);
            } catch (RuntimeException persistenceFailure) {
                pauseAndRequeue(channel, tag, messageId);
                return;
            }
        }
        channel.basicAck(tag, false);
    }

    private void quarantineOrRequeue(Message envelope, Channel channel, long tag,
                                     String messageId, String classification) throws IOException {
        try {
            invalidMessageService.record(envelope, classification);
            channel.basicAck(tag, false);
        } catch (RuntimeException persistenceFailure) {
            pauseAndRequeue(channel, tag, messageId);
        }
    }

    private String invalidClassification(RuntimeException failure) {
        if ("payload exceeds quarantine limit".equals(failure.getMessage())) return "PAYLOAD_TOO_LARGE";
        if ("invalid ORDER_TIMEOUT identity".equals(failure.getMessage())) return "IDENTITY_INVALID";
        return "ENVELOPE_INVALID";
    }

    private void pauseAndRequeue(Channel channel, long tag, String messageId) throws IOException {
        log.error("B6 timeout consumer persistence unavailable, listener paused, messageId={}", messageId);
        channel.basicReject(tag, true);
        pauser.pause("seckillTimeoutConsumer");
    }

    private String safeSummary(Exception exception) {
        return exception instanceof org.springframework.dao.DataAccessException
                ? "DATABASE_FAILURE" : "CONSUME_FAILURE";
    }

    private boolean equivalentTimeoutIdentity(com.fashion.entity.SeckillMessageLog persisted,
                                              String businessKey, String payload) {
        return persisted != null && "ORDER_TIMEOUT".equals(persisted.getMessageType())
                && Objects.equals(businessKey, persisted.getBusinessKey())
                && Objects.equals(payload, persisted.getPayload());
    }
}
