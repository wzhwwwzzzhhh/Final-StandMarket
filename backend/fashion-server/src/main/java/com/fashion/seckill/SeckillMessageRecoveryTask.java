package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.service.SeckillOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class SeckillMessageRecoveryTask {
    private static final int MAX_PUBLISH_ATTEMPTS = 5;
    private static final int MAX_TIMEOUT_FALLBACK_ATTEMPTS = 3;
    private final SeckillMessageLogMapper mapper;
    private final SeckillReliablePublisher publisher;
    private final SeckillOrderService orderService;
    private final SeckillCompensationService compensationService;
    private final SeckillBusinessDeadLetterService deadLetterService;
    private final Clock clock;

    @Autowired
    public SeckillMessageRecoveryTask(SeckillMessageLogMapper mapper,
                                      SeckillReliablePublisher publisher,
                                      SeckillOrderService orderService,
                                      SeckillCompensationService compensationService,
                                      SeckillBusinessDeadLetterService deadLetterService) {
        this(mapper, publisher, orderService, compensationService, deadLetterService,
                Clock.systemDefaultZone());
    }

    SeckillMessageRecoveryTask(SeckillMessageLogMapper mapper,
                               SeckillReliablePublisher publisher,
                               SeckillOrderService orderService,
                               SeckillCompensationService compensationService,
                               SeckillBusinessDeadLetterService deadLetterService,
                               Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.orderService = Objects.requireNonNull(orderService, "orderService");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
        this.deadLetterService = Objects.requireNonNull(deadLetterService, "deadLetterService");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${fashion.seckill.message-recovery-delay-ms:5000}")
    public void runOnce() {
        mapper.markConfirmTimeouts();
        mapper.releaseExpiredTimeoutClaims();
        int exhaustedCount = mapper.markPublishAttemptsExhausted(MAX_PUBLISH_ATTEMPTS);
        int exhaustedSources = mapper.markSourcesWithExhaustedDeadLetters();
        if (exhaustedCount > 0 || exhaustedSources > 0) {
            log.error("SECKILL_MQ_PUBLISH_EXHAUSTED messages={}, sources={}", exhaustedCount, exhaustedSources);
        }
        for (SeckillMessageLog initial : mapper.selectInitialCompensationPending(100)) {
            try {
                compensationService.requestRelease(initial.getBusinessKey(), initial.getUserId(),
                        initial.getCouponId(), "INITIAL_DELIVERY_FAILED",
                        SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
            } catch (RuntimeException failure) {
                log.warn("B6 initial delivery compensation persistence deferred, messageId={}",
                        initial.getMessageId());
            }
        }
        for (SeckillMessageLog exhausted : mapper.selectConsumeExhaustedWithoutDeadLetter(100)) {
            try {
                deadLetterService.createForExhaustedOrder(exhausted);
            } catch (RuntimeException failure) {
                log.warn("B6 publish-exhausted dead letter persistence deferred, messageId={}",
                        exhausted.getMessageId());
            }
        }
        for (SeckillMessageLog messageLog : mapper.selectRecoverable(100)) {
            try {
                if (isExpiredTimeout(messageLog)) {
                    Long orderId = parseOrderId(messageLog.getPayload());
                    orderService.cancelTimeoutOrder(orderId);
                    if (mapper.markTimeoutFallbackConsumed(messageLog.getMessageId()) != 1) {
                        throw new IllegalStateException("timeout fallback was not persisted");
                    }
                } else {
                    publisher.publish(messageLog.getMessageId(), messageLog.getPublishPurpose());
                }
            } catch (RuntimeException failure) {
                if (isExpiredTimeout(messageLog)) {
                    String summary = "invalid timeout fallback payload".equals(failure.getMessage())
                            ? "INVALID_TIMEOUT_PAYLOAD" : "TIMEOUT_FALLBACK_FAILURE";
                    int updated = mapper.recordTimeoutFallbackFailure(messageLog.getMessageId(),
                            summary, MAX_TIMEOUT_FALLBACK_ATTEMPTS);
                    if (updated != 1) {
                        log.error("B6 timeout fallback failure could not be fenced, messageId={}",
                                messageLog.getMessageId());
                    }
                }
                log.warn("B6 message recovery deferred, messageId={}, type={}, purpose={}",
                        messageLog.getMessageId(), messageLog.getMessageType(), messageLog.getPublishPurpose());
            }
        }
    }

    private boolean isExpiredTimeout(SeckillMessageLog messageLog) {
        return "ORDER_TIMEOUT".equals(messageLog.getMessageType())
                && messageLog.getDueAt() != null
                && !messageLog.getDueAt().isAfter(LocalDateTime.now(clock));
    }

    private Long parseOrderId(String payload) {
        if (payload == null || !payload.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalStateException("invalid timeout fallback payload");
        }
        return Long.valueOf(payload);
    }
}
