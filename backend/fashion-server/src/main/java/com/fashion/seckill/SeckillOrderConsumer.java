package com.fashion.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fashion.config.DirectExchangeConfig;
import com.fashion.entity.SeckillMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Component
public class SeckillOrderConsumer {
    private final ObjectMapper objectMapper;
    private final SeckillOrderConsumeTransaction consumeTransaction;
    private final SeckillConsumeFailureTransaction failureTransaction;
    private final SeckillDuplicateOrderTransaction duplicateTransaction;
    private final SeckillInvalidMessageService invalidMessageService;
    private final SeckillListenerPauser pauser;

    public SeckillOrderConsumer(ObjectMapper objectMapper,
                                SeckillOrderConsumeTransaction consumeTransaction,
                                SeckillDuplicateOrderTransaction duplicateTransaction,
                                SeckillConsumeFailureTransaction failureTransaction,
                                SeckillInvalidMessageService invalidMessageService,
                                SeckillListenerPauser pauser) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.consumeTransaction = Objects.requireNonNull(consumeTransaction, "consumeTransaction");
        this.duplicateTransaction = Objects.requireNonNull(duplicateTransaction, "duplicateTransaction");
        this.failureTransaction = Objects.requireNonNull(failureTransaction, "failureTransaction");
        this.invalidMessageService = Objects.requireNonNull(invalidMessageService, "invalidMessageService");
        this.pauser = Objects.requireNonNull(pauser, "pauser");
    }

    @RabbitListener(id = "seckillOrderConsumer", queues = DirectExchangeConfig.SeckillQueue,
            containerFactory = "seckillManualAckContainerFactory")
    public void consume(Message envelope, Channel channel) throws IOException {
        long tag = envelope.getMessageProperties().getDeliveryTag();
        String messageId = envelope.getMessageProperties().getMessageId();
        SeckillMessage message;
        int incomingAttempt;
        try {
            if (envelope.getBody().length > 64 * 1024) {
                throw new IllegalArgumentException("payload exceeds quarantine limit");
            }
            message = objectMapper.readValue(envelope.getBody(), SeckillMessage.class);
            if (messageId == null || !messageId.matches("SECKILL_ORDER_CREATE:[0-9]{1,50}")
                    || !Objects.equals(messageId, "SECKILL_ORDER_CREATE:" + message.getOrderNumber())) {
                throw new IllegalArgumentException("invalid ORDER_CREATE identity");
            }
            incomingAttempt = SeckillEnvelopeContract.require(
                    envelope, "ORDER_CREATE", message.getOrderNumber());
        } catch (RuntimeException | IOException invalidEnvelope) {
            try {
                invalidMessageService.record(envelope, invalidClassification(invalidEnvelope));
                channel.basicAck(tag, false);
            } catch (RuntimeException persistenceFailure) {
                pauseAndRequeue(channel, tag, messageId);
            }
            return;
        }

        try {
            consumeTransaction.validateSourceIdentity(message, messageId);
        } catch (SeckillPermanentEnvelopeException permanentInvalid) {
            quarantineOrRequeue(envelope, channel, tag, messageId, "SOURCE_IDENTITY_MISMATCH");
            return;
        } catch (org.springframework.dao.DataAccessException persistenceUnavailable) {
            pauseAndRequeue(channel, tag, messageId);
            return;
        }

        try {
            consumeTransaction.consume(message, messageId, incomingAttempt);
        } catch (DuplicateKeyException concurrentDuplicate) {
            try {
                duplicateTransaction.resolve(message, messageId);
            } catch (RuntimeException classificationFailure) {
                if (!persistFailureOrRequeue(channel, tag, messageId, incomingAttempt,
                        classificationFailure)) return;
            }
        } catch (RuntimeException consumeFailure) {
            if (!persistFailureOrRequeue(channel, tag, messageId, incomingAttempt, consumeFailure)) return;
        }
        channel.basicAck(tag, false);
    }

    private boolean persistFailureOrRequeue(Channel channel, long tag, String messageId,
                                            int incomingAttempt,
                                            RuntimeException failure) throws IOException {
        try {
            failureTransaction.record(messageId, incomingAttempt, safeSummary(failure));
            return true;
        } catch (RuntimeException persistenceFailure) {
            pauseAndRequeue(channel, tag, messageId);
            return false;
        }
    }

    private void pauseAndRequeue(Channel channel, long tag, String messageId) throws IOException {
        log.error("B6 consumer persistence unavailable, listener paused, messageId={}", messageId);
        channel.basicReject(tag, true);
        pauser.pause("seckillOrderConsumer");
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

    private String invalidClassification(Exception failure) {
        if (failure instanceof IOException) return "PAYLOAD_INVALID";
        if ("payload exceeds quarantine limit".equals(failure.getMessage())) return "PAYLOAD_TOO_LARGE";
        if ("invalid ORDER_CREATE identity".equals(failure.getMessage())) return "IDENTITY_INVALID";
        return "ENVELOPE_INVALID";
    }

    private String safeSummary(Exception exception) {
        return exception instanceof org.springframework.dao.DataAccessException
                ? "DATABASE_FAILURE" : "CONSUME_FAILURE";
    }
}
