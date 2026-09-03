package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@DisplayName("B6 publish 前持久消息事务")
class B6MessagePrepareTransactionTest {

    @Test
    @DisplayName("ORDER_CREATE PREPARED 在独立事务中先持久化")
    void orderCreateIsPreparedInRequiresNewTransaction() throws Exception {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillCompensationRecordMapper compensationMapper = mock(SeckillCompensationRecordMapper.class);
        when(mapper.insert(any(SeckillMessageLog.class))).thenReturn(1);
        SeckillMessagePrepareTransaction transaction = new SeckillMessagePrepareTransaction(mapper, compensationMapper);

        SeckillMessageLog log = transaction.prepareOrderCreate("9001", 7L, 19L, "{\"orderNumber\":\"9001\"}");

        assertEquals("SECKILL_ORDER_CREATE:9001", log.getMessageId());
        assertEquals("ORDER_CREATE", log.getMessageType());
        assertEquals("INITIAL", log.getPublishPurpose());
        assertEquals("PREPARED", log.getStatus());
        assertEquals("9001", log.getBusinessKey());
        assertEquals(0, log.getPublishAttempt());
        verify(mapper).insert(log);
        verify(mapper, org.mockito.Mockito.never())
                .selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001");

        Method method = SeckillMessagePrepareTransaction.class.getMethod(
                "prepareOrderCreate", String.class, Long.class, Long.class, String.class);
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation());
        assertEquals(Isolation.REPEATABLE_READ, annotation.isolation());
    }

    @Test
    @DisplayName("对账已建立释放事实后迟到 PREPARED 在唯一写入前被阻断")
    void compensatedReservationCannotBePublishedLater() {
        SeckillMessageLogMapper mapper = mock(SeckillMessageLogMapper.class);
        SeckillCompensationRecordMapper compensationMapper = mock(SeckillCompensationRecordMapper.class);
        when(compensationMapper.selectByOrderNumber("9001"))
                .thenReturn(new com.fashion.entity.SeckillCompensationRecord());
        SeckillMessagePrepareTransaction transaction =
                new SeckillMessagePrepareTransaction(mapper, compensationMapper);

        assertThrows(IllegalStateException.class,
                () -> transaction.prepareOrderCreate("9001", 7L, 19L, "{}"));
        verify(mapper, org.mockito.Mockito.never())
                .selectByMessageIdForUpdate("SECKILL_ORDER_CREATE:9001");
        verify(mapper, org.mockito.Mockito.never()).insert(any(SeckillMessageLog.class));
    }
}
