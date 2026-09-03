package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 publisher callback 持久收敛")
class B6PublisherCallbackHandlerTest {
    private SeckillMessageLogMapper mapper;
    private SeckillPublisherCallbackHandler handler;
    private SeckillCompensationService compensationService;
    private SeckillMessageLog log;

    @BeforeEach
    void setUp() {
        mapper = mock(SeckillMessageLogMapper.class);
        compensationService = mock(SeckillCompensationService.class);
        handler = new SeckillPublisherCallbackHandler(mapper, new SeckillPublishCallbackPolicy(),
                compensationService);
        log = new SeckillMessageLog();
        log.setMessageId("SECKILL_ORDER_CREATE:9001");
        log.setMessageType("ORDER_CREATE");
        log.setPublishPurpose("INITIAL");
        log.setCurrentCorrelationId("SECKILL_ORDER_CREATE:9001:P2");
        log.setStatus("SENT");
        log.setConfirmStatus("PENDING");
        log.setReturned(false);
        log.setBusinessKey("9001");
        log.setUserId(7L);
        log.setCouponId(19L);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);
        when(mapper.applyCallbackAction(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.nullable(String.class))).thenReturn(1);
    }

    @Test
    @DisplayName("当前 attempt ack 只单调标记 broker 已接管")
    void currentAckIsPersisted() {
        assertEquals(SeckillPublishCallbackPolicy.Action.MARK_ACKED,
                handler.handleConfirm("SECKILL_ORDER_CREATE:9001:P2", true, null));

        verify(mapper).applyCallbackAction(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P2", "MARK_ACKED", null);
    }

    @Test
    @DisplayName("旧 attempt nack 只追加审计不改变当前状态")
    void staleNackIsAuditOnly() {
        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                handler.handleConfirm("SECKILL_ORDER_CREATE:9001:P1", false, "old nack"));

        verify(mapper).appendCallbackAudit(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P1", "old nack");
    }

    @Test
    @DisplayName("当前 return 对未消费初始消息形成补偿动作")
    void currentReturnDominatesLaterAck() {
        when(mapper.recordReturn(log.getMessageId(), "SECKILL_ORDER_CREATE:9001:P2",
                312, "NO_ROUTE", "market.direct", "missing")).thenReturn(1);
        log.setReturned(true);

        assertEquals(SeckillPublishCallbackPolicy.Action.COMPENSATE_RESERVATION,
                handler.handleReturn(log.getMessageId(), 2, 312, "NO_ROUTE",
                        "market.direct", "missing"));

        verify(mapper).applyCallbackAction(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P2", "COMPENSATE_RESERVATION", "NO_ROUTE");
        verify(compensationService).requestRelease("9001", 7L, 19L,
                "INITIAL_DELIVERY_FAILED", SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
    }

    @Test
    @DisplayName("业务死信当前 attempt ack 在同一事务标记源消息 dead_letter_status=ACKED")
    void deadLetterAckClosesSourceStatus() {
        log.setMessageId("SECKILL_BUSINESS_DLQ:abc");
        log.setMessageType("BUSINESS_DEAD_LETTER");
        log.setPublishPurpose("DEAD_LETTER");
        log.setSourceMessageId("SECKILL_ORDER_CREATE:9001");
        log.setCurrentCorrelationId("SECKILL_BUSINESS_DLQ:abc:P1");
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);
        when(mapper.updateSourceDeadLetterStatus(log.getSourceMessageId(), "ACKED")).thenReturn(1);

        assertEquals(SeckillPublishCallbackPolicy.Action.MARK_ACKED,
                handler.handleConfirm("SECKILL_BUSINESS_DLQ:abc:P1", true, null));

        verify(mapper).updateSourceDeadLetterStatus("SECKILL_ORDER_CREATE:9001", "ACKED");
    }

    @Test
    @DisplayName("同 attempt 重复 ack 与 nack 只审计一次性结果")
    void duplicateConfirmCallbacksAreAuditOnly() {
        log.setStatus("BROKER_ACKED");
        log.setConfirmStatus("ACK");
        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                handler.handleConfirm("SECKILL_ORDER_CREATE:9001:P2", true, null));

        log.setStatus("COMPENSATION_PENDING");
        log.setConfirmStatus("NACK");
        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                handler.handleConfirm("SECKILL_ORDER_CREATE:9001:P2", false, "duplicate nack"));
    }

    @Test
    @DisplayName("同 attempt 重复 return 不重置补偿或重试时钟")
    void duplicateReturnIsAuditOnly() {
        when(mapper.recordReturn(log.getMessageId(), "SECKILL_ORDER_CREATE:9001:P2",
                312, "NO_ROUTE", "market.direct", "missing")).thenReturn(0);

        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                handler.handleReturn(log.getMessageId(), 2, 312, "NO_ROUTE",
                        "market.direct", "missing"));

        verify(mapper).appendCallbackAudit(log.getMessageId(),
                "SECKILL_ORDER_CREATE:9001:P2", "NO_ROUTE");
    }

    @Test
    @DisplayName("timeout fallback 终态收到旧 confirm 只审计且不会因 purpose 无法解析")
    void timeoutFallbackLateConfirmIsAuditOnly() {
        log.setMessageId("SECKILL_ORDER_TIMEOUT:9001");
        log.setMessageType("ORDER_TIMEOUT");
        log.setPublishPurpose("TIMEOUT_FALLBACK");
        log.setCurrentCorrelationId("SECKILL_ORDER_TIMEOUT:9001:P5");
        log.setStatus("MANUAL_REQUIRED");
        log.setConfirmStatus("TIMEOUT");
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        assertEquals(SeckillPublishCallbackPolicy.Action.AUDIT_ONLY,
                handler.handleConfirm("SECKILL_ORDER_TIMEOUT:9001:P4", true, null));

        verify(mapper).appendCallbackAudit(log.getMessageId(),
                "SECKILL_ORDER_TIMEOUT:9001:P4", null);
    }

    @Test
    @DisplayName("当前 attempt 先 ack 后 return 仍由 return 失败占优")
    void returnAfterAckIsFailureDominant() {
        log.setStatus("BROKER_ACKED");
        log.setConfirmStatus("ACK");
        log.setReturned(true);
        when(mapper.recordReturn(log.getMessageId(), "SECKILL_ORDER_CREATE:9001:P2",
                312, "NO_ROUTE", "market.direct", "missing")).thenReturn(1);

        assertEquals(SeckillPublishCallbackPolicy.Action.COMPENSATE_RESERVATION,
                handler.handleReturn(log.getMessageId(), 2, 312, "NO_ROUTE",
                        "market.direct", "missing"));
    }
}
