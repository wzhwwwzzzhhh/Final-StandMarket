package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.service.SeckillOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 消息恢复任务")
class B6MessageRecoveryTaskTest {
    @Test
    @DisplayName("confirm timeout 先持久转待恢复，再按消息类型使用正确发布轨道")
    void recoversEachMessageTypeWithItsOwnPurpose() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog order = log("ORDER_CREATE:1", "ORDER_CREATE", "CONSUME_RETRY");
        SeckillMessageLog timeout = log("ORDER_TIMEOUT:1", "ORDER_TIMEOUT", "TIMEOUT_RECOVERY");
        SeckillMessageLog dead = log("DLQ:1", "BUSINESS_DEAD_LETTER", "DEAD_LETTER");
        when(mapper.selectRecoverable(100)).thenReturn(Arrays.asList(order, timeout, dead));

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter).runOnce();

        verify(mapper).markConfirmTimeouts();
        verify(mapper).markPublishAttemptsExhausted(5);
        verify(mapper).markSourcesWithExhaustedDeadLetters();
        verify(mapper).selectRecoverable(100);
        verify(publisher).publish("ORDER_CREATE:1", "CONSUME_RETRY");
        verify(publisher).publish("ORDER_TIMEOUT:1", "TIMEOUT_RECOVERY");
        verify(publisher).publish("DLQ:1", "DEAD_LETTER");
    }

    @Test
    @DisplayName("恢复扫描先把达到发布上限的消息和业务死信源转人工门禁")
    void exhaustedPublishAttemptsAreQuarantinedBeforeRecoverySelection() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter).runOnce();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).markConfirmTimeouts();
        order.verify(mapper).releaseExpiredTimeoutClaims();
        order.verify(mapper).markPublishAttemptsExhausted(5);
        order.verify(mapper).markSourcesWithExhaustedDeadLetters();
        order.verify(mapper).selectRecoverable(100);
        verify(publisher, org.mockito.Mockito.never()).publish(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("恢复任务先把过期 timeout PROCESSING lease 转为持久重发轨道")
    void expiredTimeoutProcessingLeaseIsDurablyReleasedBeforeSelection() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter).runOnce();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).markConfirmTimeouts();
        order.verify(mapper).releaseExpiredTimeoutClaims();
        order.verify(mapper).markPublishAttemptsExhausted(5);
        order.verify(mapper).markSourcesWithExhaustedDeadLetters();
        order.verify(mapper).selectRecoverable(100);
    }

    @Test
    @DisplayName("INITIAL confirm timeout 转补偿后由恢复任务幂等建立释放事实")
    void initialConfirmTimeoutCreatesCompensationFact() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog initial = log("SECKILL_ORDER_CREATE:9001", "ORDER_CREATE", "INITIAL");
        initial.setBusinessKey("9001");
        initial.setUserId(7L);
        initial.setCouponId(19L);
        when(mapper.selectInitialCompensationPending(100))
                .thenReturn(java.util.Collections.singletonList(initial));

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter).runOnce();

        verify(compensation).requestRelease("9001", 7L, 19L,
                "INITIAL_DELIVERY_FAILED",
                SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
    }

    @Test
    @DisplayName("ORDER_TIMEOUT 已过 immutable dueAt 时直接可信取消而不重置延迟")
    void expiredTimeoutUsesDirectFallback() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog timeout = log("SECKILL_ORDER_TIMEOUT:9001", "ORDER_TIMEOUT", "TIMEOUT_RECOVERY");
        timeout.setPayload("41");
        timeout.setDueAt(LocalDateTime.of(2026, 9, 2, 3, 59));
        when(mapper.selectRecoverable(100)).thenReturn(java.util.Collections.singletonList(timeout));
        when(mapper.markTimeoutFallbackConsumed(timeout.getMessageId())).thenReturn(1);
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T04:00:00Z"), ZoneOffset.UTC);

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter, clock).runOnce();

        verify(orderService).cancelTimeoutOrder(41L);
        verify(mapper).markTimeoutFallbackConsumed(timeout.getMessageId());
        verify(publisher, org.mockito.Mockito.never()).publish(timeout.getMessageId(), "TIMEOUT_RECOVERY");
    }

    @Test
    @DisplayName("ORDER_TIMEOUT 消费耗尽后业务死信与 dueAt 取消兜底保持独立")
    void exhaustedTimeoutStillUsesDueFallback() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog timeout = log("SECKILL_ORDER_TIMEOUT:9001", "ORDER_TIMEOUT", "TIMEOUT_RECOVERY");
        timeout.setStatus("CONSUME_EXHAUSTED");
        timeout.setPayload("41");
        timeout.setDueAt(LocalDateTime.of(2026, 9, 2, 3, 59));
        when(mapper.selectRecoverable(100)).thenReturn(java.util.Collections.singletonList(timeout));
        when(mapper.markTimeoutFallbackConsumed(timeout.getMessageId())).thenReturn(1);
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T04:00:00Z"), ZoneOffset.UTC);

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter, clock).runOnce();

        verify(orderService).cancelTimeoutOrder(41L);
        verify(mapper).markTimeoutFallbackConsumed(timeout.getMessageId());
        verify(publisher, org.mockito.Mockito.never()).publish(timeout.getMessageId(), "TIMEOUT_RECOVERY");
    }

    @Test
    @DisplayName("timeout 兜底永久失败持久递增并在阈值后退出高频恢复集合")
    void timeoutFallbackFailureIsDurablyBounded() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillReliablePublisher publisher = mock(SeckillReliablePublisher.class);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog timeout = log("SECKILL_ORDER_TIMEOUT:9001", "ORDER_TIMEOUT", "TIMEOUT_FALLBACK");
        timeout.setStatus("CONSUME_EXHAUSTED");
        timeout.setPayload("not-an-order-id");
        timeout.setDueAt(LocalDateTime.of(2026, 9, 2, 3, 59));
        when(mapper.selectRecoverable(100)).thenReturn(java.util.Collections.singletonList(timeout));
        when(mapper.recordTimeoutFallbackFailure(org.mockito.ArgumentMatchers.eq(timeout.getMessageId()),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(3))).thenReturn(1);
        Clock clock = Clock.fixed(Instant.parse("2026-09-02T04:00:00Z"), ZoneOffset.UTC);

        new SeckillMessageRecoveryTask(mapper, publisher, orderService, compensation, deadLetter, clock).runOnce();

        verify(mapper).recordTimeoutFallbackFailure(org.mockito.ArgumentMatchers.eq(timeout.getMessageId()),
                org.mockito.ArgumentMatchers.eq("INVALID_TIMEOUT_PAYLOAD"),
                org.mockito.ArgumentMatchers.eq(3));
        verify(orderService, org.mockito.Mockito.never()).cancelTimeoutOrder(
                org.mockito.ArgumentMatchers.anyLong());
    }

    private SeckillMessageLog log(String id, String type, String purpose) {
        SeckillMessageLog log = new SeckillMessageLog();
        log.setMessageId(id);
        log.setMessageType(type);
        log.setPublishPurpose(purpose);
        return log;
    }
}
