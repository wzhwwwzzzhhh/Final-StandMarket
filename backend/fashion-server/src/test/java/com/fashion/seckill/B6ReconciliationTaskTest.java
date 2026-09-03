package com.fashion.seckill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;

@DisplayName("B6 定时对账入口")
class B6ReconciliationTaskTest {
    @Test
    @DisplayName("每轮只处理 scanner 给出的有界 reservation 快照")
    void scansAndReconcilesBoundedSnapshots() {
        SeckillReservationScanner scanner = mock(SeckillReservationScanner.class);
        SeckillReconciliationService service = mock(SeckillReconciliationService.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        SeckillReservationSnapshot first = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        SeckillReservationSnapshot second = new SeckillReservationSnapshot(
                20L, 8L, "9002", true, false, Duration.ofMinutes(10));
        when(scanner.scan(500)).thenReturn(Arrays.asList(first, second));
        java.time.LocalDateTime started = java.time.LocalDateTime.now().minusMinutes(1);
        when(scanner.drainCompletedCycles()).thenReturn(java.util.Collections.singletonList(
                new SeckillReservationScanner.ScanCompletion(19L, started)));

        new SeckillReconciliationTask(scanner, service, anomalies).runOnce();

        verify(scanner).scan(500);
        verify(service).reconcile(first);
        verify(service).reconcile(second);
        verify(anomalies).markCleanScan(19L, started);
    }

    @Test
    @DisplayName("本轮任一 snapshot 处理失败时不推进该券 anomaly clean scan")
    void failedSnapshotDoesNotAdvanceCleanScan() {
        SeckillReservationScanner scanner = mock(SeckillReservationScanner.class);
        SeckillReconciliationService service = mock(SeckillReconciliationService.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, true, Duration.ofMinutes(10));
        java.time.LocalDateTime started = java.time.LocalDateTime.now().minusMinutes(1);
        when(scanner.scan(500)).thenReturn(java.util.Collections.singletonList(snapshot));
        when(service.reconcile(snapshot)).thenThrow(new IllegalStateException("mysql unavailable"));
        when(scanner.drainCompletedCycles()).thenReturn(java.util.Collections.singletonList(
                new SeckillReservationScanner.ScanCompletion(19L, started)));

        new SeckillReconciliationTask(scanner, service, anomalies).runOnce();

        verify(scanner).markCycleUnclean(19L);
        verify(anomalies, never()).markCleanScan(19L, started);
    }

    @Test
    @DisplayName("本轮发现 durable anomaly 时不推进该券 clean scan")
    void anomalyResultDoesNotAdvanceCleanScan() {
        SeckillReservationScanner scanner = mock(SeckillReservationScanner.class);
        SeckillReconciliationService service = mock(SeckillReconciliationService.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(
                19L, 7L, "9001", true, false, Duration.ofMinutes(10));
        java.time.LocalDateTime started = java.time.LocalDateTime.now().minusMinutes(1);
        when(scanner.scan(500)).thenReturn(java.util.Collections.singletonList(snapshot));
        when(service.reconcile(snapshot)).thenReturn(SeckillReconciliationPolicy.Action.MANUAL_REQUIRED);
        when(scanner.drainCompletedCycles()).thenReturn(java.util.Collections.singletonList(
                new SeckillReservationScanner.ScanCompletion(19L, started)));

        new SeckillReconciliationTask(scanner, service, anomalies).runOnce();

        verify(scanner).markCycleUnclean(19L);
        verify(anomalies, never()).markCleanScan(19L, started);
    }
}
