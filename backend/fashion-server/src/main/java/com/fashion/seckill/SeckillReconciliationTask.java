package com.fashion.seckill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class SeckillReconciliationTask {
    private final SeckillReservationScanner scanner;
    private final SeckillReconciliationService service;
    private final com.fashion.mapper.SeckillReconciliationAnomalyMapper anomalyMapper;

    public SeckillReconciliationTask(SeckillReservationScanner scanner,
                                     SeckillReconciliationService service,
                                     com.fashion.mapper.SeckillReconciliationAnomalyMapper anomalyMapper) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.service = Objects.requireNonNull(service, "service");
        this.anomalyMapper = Objects.requireNonNull(anomalyMapper, "anomalyMapper");
    }

    @Scheduled(fixedDelayString = "${fashion.seckill.reconciliation-delay-ms:60000}")
    public void runOnce() {
        Set<Long> uncleanThisRun = new HashSet<>();
        reconcileSnapshots(scanner.scanActiveOrders(100), uncleanThisRun);
        reconcileSnapshots(scanner.scan(500), uncleanThisRun);
        for (SeckillReservationScanner.ScanCompletion completion : scanner.drainCompletedCycles()) {
            if (completion.isClean() && !uncleanThisRun.contains(completion.getCouponId())) {
                anomalyMapper.markCleanScan(completion.getCouponId(), completion.getCycleStartedAt());
            }
        }
    }

    private void reconcileSnapshots(java.util.List<SeckillReservationSnapshot> snapshots,
                                    Set<Long> uncleanThisRun) {
        for (SeckillReservationSnapshot snapshot : snapshots) {
            try {
                if (service.reconcile(snapshot) == SeckillReconciliationPolicy.Action.MANUAL_REQUIRED) {
                    uncleanThisRun.add(snapshot.getCouponId());
                    scanner.markCycleUnclean(snapshot.getCouponId());
                }
            } catch (RuntimeException failure) {
                uncleanThisRun.add(snapshot.getCouponId());
                scanner.markCycleUnclean(snapshot.getCouponId());
                log.error("B6 reconciliation failed, couponId={}, userId={}, orderNumber={}",
                        snapshot.getCouponId(), snapshot.getUserId(), snapshot.getOrderNumber());
            }
        }
    }
}
