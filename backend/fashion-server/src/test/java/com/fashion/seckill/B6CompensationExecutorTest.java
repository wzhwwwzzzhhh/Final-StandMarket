package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 统一补偿执行器")
class B6CompensationExecutorTest {
    @Test
    @DisplayName("所有即时释放先按订单号领取唯一补偿 lease")
    void immediateReleaseMustOwnTheDurableClaim() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillCompensationRecord record = record("9001");
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);
        when(mapper.claimByOrder("9001", "worker-1")).thenReturn(1);
        when(reservation.rollback(19L, 7L, "9001"))
                .thenReturn(SeckillReservationService.RollbackResult.APPLIED);

        SeckillCompensationExecutor executor = new SeckillCompensationExecutor(
                mapper, orderMapper, reservation, compensation, "worker-1");

        assertEquals(SeckillReservationService.RollbackResult.APPLIED,
                executor.execute("9001"));
        verify(mapper).claimByOrder("9001", "worker-1");
        verify(reservation).rollback(19L, 7L, "9001");
        verify(compensation).recordRollbackResult(record, "worker-1",
                SeckillReservationService.RollbackResult.APPLIED);
    }

    @Test
    @DisplayName("lease 竞争失败的即时路径不得绕过 claim 直接触碰 Redis")
    void claimLoserDoesNotTouchRedis() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillCompensationRecord record = record("9001");
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);

        SeckillCompensationExecutor executor = new SeckillCompensationExecutor(
                mapper, orderMapper, reservation, compensation, "worker-2");

        assertEquals(SeckillReservationService.RollbackResult.INFRA_FAILURE,
                executor.execute("9001"));
        verify(reservation, never()).rollback(19L, 7L, "9001");
    }

    @Test
    @DisplayName("补偿已成功但源消息未收敛时重跑先补 COMPENSATED 再返回")
    void succeededCompensationRetryConvergesSourceMessage() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillCompensationRecord record = record("9001");
        record.setStatus("SUCCEEDED");
        when(mapper.selectByOrderNumber("9001")).thenReturn(record);

        SeckillCompensationExecutor executor = new SeckillCompensationExecutor(
                mapper, orderMapper, reservation, compensation, "worker-3");

        assertEquals(SeckillReservationService.RollbackResult.ALREADY_APPLIED,
                executor.execute("9001"));
        verify(compensation).convergeInitialMessage("9001");
        verify(reservation, never()).rollback(19L, 7L, "9001");
    }

    @Test
    @DisplayName("claim 败者二次观察到成功时也补做源消息收敛")
    void claimLoserObservingSucceededConvergesSourceMessage() {
        SeckillCompensationRecordMapper mapper = mock(SeckillCompensationRecordMapper.class);
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillReservationService reservation = mock(SeckillReservationService.class);
        SeckillCompensationService compensation = mock(SeckillCompensationService.class);
        SeckillCompensationRecord inProgress = record("9001");
        inProgress.setStatus("IN_PROGRESS");
        SeckillCompensationRecord succeeded = record("9001");
        succeeded.setStatus("SUCCEEDED");
        when(mapper.selectByOrderNumber("9001")).thenReturn(inProgress, succeeded);

        SeckillCompensationExecutor executor = new SeckillCompensationExecutor(
                mapper, orderMapper, reservation, compensation, "worker-4");

        assertEquals(SeckillReservationService.RollbackResult.ALREADY_APPLIED,
                executor.execute("9001"));
        verify(compensation).convergeInitialMessage("9001");
        verify(reservation, never()).rollback(19L, 7L, "9001");
    }

    private SeckillCompensationRecord record(String orderNumber) {
        SeckillCompensationRecord record = new SeckillCompensationRecord();
        record.setId(1L);
        record.setOrderNumber(orderNumber);
        record.setUserId(7L);
        record.setCouponId(19L);
        record.setEvidenceMask(SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        record.setStatus("PENDING");
        return record;
    }
}
