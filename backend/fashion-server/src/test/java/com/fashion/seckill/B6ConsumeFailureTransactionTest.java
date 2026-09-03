package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 消费有限重试与业务死信")
class B6ConsumeFailureTransactionTest {
    @Test
    @DisplayName("第二次失败进入退避重发且不提前死信")
    void secondFailureRetries() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(2);
        when(mapper.recordConsumeFailure(log.getMessageId(), 2, "failure")).thenReturn(1);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        SeckillConsumeFailureTransaction transaction =
                new SeckillConsumeFailureTransaction(mapper, deadLetter);

        assertEquals(SeckillConsumeFailureTransaction.Outcome.RETRY,
                transaction.record(log.getMessageId(), 2, "failure"));
        verify(deadLetter, never()).createForExhaustedOrder(log);
    }

    @Test
    @DisplayName("第三次失败建立独立业务死信与 reservation 释放事实")
    void thirdFailureCreatesDeadLetterAndCompensation() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(3);
        when(mapper.recordConsumeFailure(log.getMessageId(), 3, "failure")).thenReturn(1);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        SeckillConsumeFailureTransaction transaction =
                new SeckillConsumeFailureTransaction(mapper, deadLetter);

        assertEquals(SeckillConsumeFailureTransaction.Outcome.DEAD_LETTER,
                transaction.record(log.getMessageId(), 3, "failure"));
        verify(deadLetter).createForExhaustedOrder(log);
    }

    @Test
    @DisplayName("超时取消第三次失败进入业务死信但不误释放可能已支付订单")
    void exhaustedTimeoutCreatesTimeoutDeadLetter() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(3);
        log.setMessageId("SECKILL_ORDER_TIMEOUT:9001");
        log.setMessageType("ORDER_TIMEOUT");
        when(mapper.recordConsumeFailure(log.getMessageId(), 3, "failure")).thenReturn(1);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        SeckillConsumeFailureTransaction transaction =
                new SeckillConsumeFailureTransaction(mapper, deadLetter);

        assertEquals(SeckillConsumeFailureTransaction.Outcome.DEAD_LETTER,
                transaction.record(log.getMessageId(), 3, "failure"));
        verify(deadLetter).createForExhaustedTimeout(log);
        verify(deadLetter, never()).createForExhaustedOrder(log);
    }

    @Test
    @DisplayName("已耗尽消息的重复 broker 投递按已死信幂等成功而不触发热 requeue")
    void duplicateDeliveryAfterExhaustionIsIdempotent() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(3);
        log.setStatus("CONSUME_EXHAUSTED");
        when(mapper.recordConsumeFailure(log.getMessageId(), 3, "failure")).thenReturn(0);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        SeckillConsumeFailureTransaction transaction =
                new SeckillConsumeFailureTransaction(mapper, deadLetter);

        assertEquals(SeckillConsumeFailureTransaction.Outcome.DEAD_LETTER,
                transaction.record(log.getMessageId(), 3, "failure"));
        verify(deadLetter, never()).createForExhaustedOrder(log);
    }

    @Test
    @DisplayName("同一 incoming attempt 重复投递只复用已持久失败，不重复消耗次数")
    void repeatedIncomingAttemptDoesNotAdvanceCounter() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(1);
        log.setStatus("RETRY_PUBLISH_PENDING");
        when(mapper.recordConsumeFailure(log.getMessageId(), 1, "failure")).thenReturn(0);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        SeckillConsumeFailureTransaction transaction =
                new SeckillConsumeFailureTransaction(mapper, deadLetter);

        assertEquals(SeckillConsumeFailureTransaction.Outcome.RETRY,
                transaction.record(log.getMessageId(), 1, "failure"));
        verify(deadLetter, never()).createForExhaustedOrder(log);
    }

    @Test
    @DisplayName("defense-in-depth 不允许任意异常文本进入 last_error")
    void arbitrarySensitiveSummaryIsReplaced() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillBusinessDeadLetterService deadLetter = mock(SeckillBusinessDeadLetterService.class);
        SeckillMessageLog log = source(1);
        when(mapper.recordConsumeFailure(log.getMessageId(), 1, "CONSUME_FAILURE")).thenReturn(1);
        when(mapper.selectByMessageId(log.getMessageId())).thenReturn(log);

        new SeckillConsumeFailureTransaction(mapper, deadLetter).record(log.getMessageId(), 1,
                "jdbc:mysql://db?password=secret SELECT payload");

        verify(mapper).recordConsumeFailure(log.getMessageId(), 1, "CONSUME_FAILURE");
    }

    private SeckillMessageLog source(int attempt) {
        SeckillMessageLog log = new SeckillMessageLog();
        log.setMessageId("SECKILL_ORDER_CREATE:9001");
        log.setMessageType("ORDER_CREATE");
        log.setBusinessKey("9001");
        log.setUserId(7L);
        log.setCouponId(19L);
        log.setConsumeAttempt(attempt);
        log.setBodySha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        return log;
    }
}
