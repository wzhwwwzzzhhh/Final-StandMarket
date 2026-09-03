package com.fashion.service.impl;

import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 秒杀订单物理删除补偿证据门禁")
class B6SeckillDeletionEvidenceGateTest {
    private SeckillOrderServiceImpl service;
    private SeckillOrderMapper orderMapper;
    private SeckillCompensationRecordMapper compensationMapper;

    @BeforeEach
    void setUp() {
        service = new SeckillOrderServiceImpl();
        orderMapper = mock(SeckillOrderMapper.class);
        compensationMapper = mock(SeckillCompensationRecordMapper.class);
        ReflectionTestUtils.setField(service, "seckillOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "seckillCompensationRecordMapper", compensationMapper);
    }

    @Test
    @DisplayName("取消状态但无已完成回补证据时拒绝删除")
    void rejectsCancelledOrderWithoutCompletedEvidence() {
        SeckillOrder order = cancelledOrder();
        when(orderMapper.selectById(5L)).thenReturn(order);
        when(compensationMapper.hasCompletedCancellationEvidence("9001")).thenReturn(0);

        assertFalse(service.deleteOrder(5L));

        verify(orderMapper, never()).deleteById(5L);
    }

    @Test
    @DisplayName("取消状态且存在已完成取消回补证据时允许删除")
    void deletesOnlyWithCompletedEvidence() {
        SeckillOrder order = cancelledOrder();
        when(orderMapper.selectById(5L)).thenReturn(order);
        when(compensationMapper.hasCompletedCancellationEvidence("9001")).thenReturn(1);

        assertTrue(service.deleteOrder(5L));

        verify(orderMapper).deleteById(5L);
    }

    private SeckillOrder cancelledOrder() {
        SeckillOrder order = new SeckillOrder();
        order.setId(5L);
        order.setOrderNumber("9001");
        order.setStatus(3);
        return order;
    }
}
