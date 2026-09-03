package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.entity.SeckillOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 补偿恢复 lease")
class B6CompensationRecoveryTaskTest {
    @Test
    @DisplayName("只有 CAS lease 胜者执行 token rollback")
    void onlyLeaseWinnerTouchesRedis() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillCompensationRecord record = new SeckillCompensationRecord();
        record.setId(1L);
        record.setOrderNumber("9001");
        record.setUserId(7L);
        record.setCouponId(19L);
        record.setStatus("PENDING");
        record.setEvidenceMask(SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(record));
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);
        when(mapper.claimByOrder(org.mockito.ArgumentMatchers.eq("9001"), anyString())).thenReturn(1, 0);
        when(reservation.rollback(19L, 7L, "9001"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);
        SeckillCompensationRecoveryTask task =
                new SeckillCompensationRecoveryTask(mapper, orderMapper, reservation, compensation, "worker-1");

        task.runOnce();
        task.runOnce();

        verify(mapper, org.mockito.Mockito.times(2)).markExhausted(10);
        verify(reservation).rollback(19L, 7L, "9001");
        verify(compensation).recordRollbackResult(record, "worker-1",
                SeckillReservationService.RollbackResult.APPLIED);
    }

    @Test
    @DisplayName("lease 失败不得触碰 Redis")
    void leaseLoserDoesNothing() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillCompensationRecord record = new SeckillCompensationRecord();
        record.setId(1L);
        record.setOrderNumber("9001");
        record.setStatus("PENDING");
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(record));
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);
        when(mapper.claimByOrder(org.mockito.ArgumentMatchers.eq("9001"), anyString())).thenReturn(0);

        new SeckillCompensationRecoveryTask(mapper, orderMapper, reservation, compensation, "worker-2").runOnce();

        verify(mapper).markExhausted(10);
        verify(reservation, never()).rollback(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("恢复扫描先把达到补偿上限的 lease 转人工门禁")
    void exhaustedCompensationsAreQuarantinedBeforeSelection() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);

        new SeckillCompensationRecoveryTask(mapper, orderMapper, reservation, compensation, "worker-3").runOnce();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper);
        order.verify(mapper).markExhausted(10);
        order.verify(mapper).selectRecoverable(100);
        verify(reservation, never()).rollback(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("存在活动或已支付订单时补偿不得释放 Redis reservation")
    void activeOrderBlocksCompensation() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillCompensationRecord record = new SeckillCompensationRecord();
        record.setId(1L);
        record.setOrderNumber("9001");
        record.setStatus("PENDING");
        record.setUserId(7L);
        record.setCouponId(19L);
        record.setEvidenceMask(SeckillCompensationService.EVIDENCE_CONSUME_EXHAUSTED);
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber("9001");
        order.setUserId(7L);
        order.setCouponId(19L);
        order.setStatus(2);
        when(mapper.selectRecoverable(100)).thenReturn(Collections.singletonList(record));
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);
        when(mapper.claimByOrder("9001", "worker-4")).thenReturn(1);
        when(orderMapper.selectByOrderNumber("9001")).thenReturn(order);
        when(mapper.markManualRequiredOwned(1L, "worker-4", "order state forbids reservation release"))
                .thenReturn(1);

        new SeckillCompensationRecoveryTask(mapper, orderMapper, reservation, compensation, "worker-4").runOnce();

        verify(mapper).markManualRequiredOwned(1L, "worker-4",
                "order state forbids reservation release");
        verify(reservation, never()).rollback(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }
}
