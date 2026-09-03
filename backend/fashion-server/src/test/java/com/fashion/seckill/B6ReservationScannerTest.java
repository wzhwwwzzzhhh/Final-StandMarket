package com.fashion.seckill;

import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.entity.SeckillOrder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 reservation 对账有界扫描")
class B6ReservationScannerTest {
    @Test
    @DisplayName("多实例按券租期锁竞争失败时不重复扫描且保留后续进度")
    void couponScanUsesNonBlockingDistributedLease() throws Exception {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        RedissonClient redisson = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(reader.scanRegistry("0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList("19")));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.emptyList());
        when(redisson.getLock("seckill:reconciliation:lock:19")).thenReturn(lock);
        when(lock.tryLock(0, 30, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(false);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies,
                redisson, () -> LocalDateTime.of(2026, 9, 2, 4, 0));

        assertTrue(scanner.scan(2).isEmpty());

        verify(lock).tryLock(0, 30, java.util.concurrent.TimeUnit.SECONDS);
        verify(reader, org.mockito.Mockito.never()).scanReservations(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
    }
    @Test
    @DisplayName("有效订单反向游标用点查发现 HASH/ZSET 同时缺失")
    void activeOrderCursorFindsMissingReservation() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillOrder order = new SeckillOrder();
        order.setId(51L);
        order.setUserId(7L);
        order.setCouponId(19L);
        order.setOrderNumber("9001");
        order.setStatus(1);
        when(candidates.selectOrderRowsAfter(0L, 10)).thenReturn(Collections.singletonList(order));
        when(reader.reservationToken(19L, "7")).thenReturn(null);
        when(reader.userScore(19L, "7")).thenReturn(null);

        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates);
        List<SeckillReservationSnapshot> snapshots = scanner.scanActiveOrders(10);

        assertEquals(1, snapshots.size());
        assertEquals("9001", snapshots.get(0).getOrderNumber());
        assertTrue(!snapshots.get(0).isHashTokenPresent());
        assertTrue(!snapshots.get(0).isZsetMemberPresent());
        verify(candidates).selectOrderRowsAfter(0L, 10);
    }
    @Test
    @DisplayName("registry 缺成员时合并有界 MySQL page 并续接 Redis cursor")
    void mysqlCandidateRecoversMissingRegistryMembershipWithoutFullScan() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        when(reader.scanRegistry("0", 1)).thenReturn(
                SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.singletonList(19L));
        when(reader.scanReservations(19L, "0", 1)).thenReturn(
                new SeckillRedisScanPageReader.ScanPage<>("17",
                        Collections.singletonList(new SeckillRedisScanPageReader.HashEntry("7", "9001"))));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(
                SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.scanReservations(19L, "17", 2)).thenReturn(
                SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.scanUsers(19L, "0", 2)).thenReturn(
                SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.userScore(19L, "7"))
                .thenReturn((double) (LocalDateTime.of(2026, 9, 2, 4, 0)
                        .atZone(ZoneId.systemDefault()).toEpochSecond() - 600));

        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates);
        List<SeckillReservationSnapshot> snapshots = scanner.scan(2);

        assertEquals(1, snapshots.size());
        assertEquals(19L, snapshots.get(0).getCouponId());
        assertEquals("9001", snapshots.get(0).getOrderNumber());
        assertTrue(snapshots.get(0).isHashTokenPresent());
        assertTrue(snapshots.get(0).isZsetMemberPresent());
        verify(candidates).selectCouponIdsAfter(0L, 1);
        verify(reader).scanReservations(19L, "0", 1);

        scanner.scan(2);
        verify(reader).scanReservations(19L, "17", 2);
    }

    @Test
    @DisplayName("ZSET 单边成员通过逐项 HGET 被发现且不会要求整券物化")
    void zsetOnlyMemberIsDiscoveredByBoundedPage() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        when(reader.scanRegistry("0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList("19")));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.emptyList());
        when(reader.scanReservations(19L, "0", 1)).thenReturn(
                SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList(new SeckillRedisScanPageReader.ZEntry(
                        "7", (double) (Instant.now().getEpochSecond() - 600)))));
        when(reader.reservationToken(19L, "7")).thenReturn(null);

        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates);
        assertTrue(scanner.scan(1).isEmpty());
        List<SeckillReservationSnapshot> snapshots = scanner.scan(1);

        assertEquals(1, snapshots.size());
        assertTrue(!snapshots.get(0).isHashTokenPresent());
        assertTrue(snapshots.get(0).isZsetMemberPresent());
    }

    @Test
    @DisplayName("Redis SCAN 超额 page 通过 carryover 跨轮全部处理且完成前不标 clean")
    void overReturnedScanPageIsNotTruncated() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        when(anomalies.selectDatabaseTime()).thenReturn(LocalDateTime.of(2026, 9, 2, 4, 0));
        when(anomalies.upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(reader.scanRegistry("0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList("19")));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.emptyList());
        when(reader.scanReservations(19L, "0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Arrays.asList(new SeckillRedisScanPageReader.HashEntry("7", "9001"),
                        new SeckillRedisScanPageReader.HashEntry("8", "9002"),
                        new SeckillRedisScanPageReader.HashEntry("9", "9003"))));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.userScore(org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn((double) (LocalDateTime.of(2026, 9, 2, 4, 0)
                        .atZone(ZoneId.systemDefault()).toEpochSecond() - 600));
        when(reader.reservationCount(19L)).thenReturn(3L);
        when(reader.userCount(19L)).thenReturn(3L);
        when(reader.registryContains(19L)).thenReturn(true);
        when(reader.stockIsValid(19L)).thenReturn(true);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies);
        List<String> orders = new ArrayList<>();

        for (int run = 0; run < 3; run++) {
            List<SeckillReservationSnapshot> page = scanner.scan(1);
            assertEquals(1, page.size());
            orders.add(page.get(0).getOrderNumber());
            assertTrue(couponCompletions(scanner.drainCompletedCycles(), 19L).isEmpty());
        }
        scanner.scan(1);
        List<SeckillReservationScanner.ScanCompletion> completions =
                couponCompletions(scanner.drainCompletedCycles(), 19L);

        assertEquals(Arrays.asList("9001", "9002", "9003"), orders);
        assertEquals(1, completions.size());
        assertTrue(completions.get(0).isClean());
    }

    @Test
    @DisplayName("非法 Redis member 留下 durable anomaly 且整券周期不得标 clean")
    void invalidMemberMakesCycleUncleanAndPersistsAnomaly() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        when(anomalies.selectDatabaseTime()).thenReturn(LocalDateTime.of(2026, 9, 2, 4, 0));
        when(anomalies.upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(reader.scanRegistry("0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList("19")));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.emptyList());
        when(reader.scanReservations(19L, "0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList(new SeckillRedisScanPageReader.HashEntry("not-a-user", "9001"))));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.stockIsValid(19L)).thenReturn(true);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies);

        assertTrue(scanner.scan(1).isEmpty());
        assertTrue(scanner.scan(1).isEmpty());
        List<SeckillReservationScanner.ScanCompletion> completions =
                couponCompletions(scanner.drainCompletedCycles(), 19L);

        assertEquals(1, completions.size());
        assertTrue(!completions.get(0).isClean());
        verify(anomalies).upsert(org.mockito.ArgumentMatchers.eq("INVALID_RESERVATION_MEMBER"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("完整 HASH/ZSET 但 registry membership 丢失时留下 anomaly 且不标 clean")
    void missingRegistryMembershipMakesCycleUnclean() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        when(anomalies.selectDatabaseTime()).thenReturn(LocalDateTime.of(2026, 9, 2, 4, 0));
        when(anomalies.upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(reader.scanRegistry("0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.singletonList(19L));
        when(reader.scanReservations(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.reservationCount(19L)).thenReturn(1L);
        when(reader.userCount(19L)).thenReturn(1L);
        when(reader.registryContains(19L)).thenReturn(false);
        when(reader.stockIsValid(19L)).thenReturn(true);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies);

        scanner.scan(2);
        List<SeckillReservationScanner.ScanCompletion> completions =
                couponCompletions(scanner.drainCompletedCycles(), 19L);

        assertEquals(1, completions.size());
        assertTrue(!completions.get(0).isClean());
        verify(anomalies).upsert(org.mockito.ArgumentMatchers.eq("REGISTRY_LEDGER_MISMATCH"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("历史候选已无任何 Redis ledger 时不因 stock key 正常过期产生假异常")
    void emptyHistoricalLedgerDoesNotRequireStockKey() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        when(anomalies.selectDatabaseTime()).thenReturn(LocalDateTime.of(2026, 9, 2, 4, 0));
        when(reader.scanRegistry("0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.singletonList(19L));
        when(reader.scanReservations(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.scanUsers(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.reservationCount(19L)).thenReturn(0L);
        when(reader.userCount(19L)).thenReturn(0L);
        when(reader.registryContains(19L)).thenReturn(false);
        when(reader.stockIsValid(19L)).thenReturn(false);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies);

        scanner.scan(2);

        assertTrue(couponCompletions(scanner.drainCompletedCycles(), 19L).get(0).isClean());
        verify(anomalies, org.mockito.Mockito.never()).upsert(
                org.mockito.ArgumentMatchers.eq("REGISTRY_LEDGER_MISMATCH"),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("未来或非整数 reservation score 留下 anomaly 而不是永久等待")
    void invalidReservationScoreMakesCycleUnclean() {
        SeckillRedisScanPageReader reader = mock(SeckillRedisScanPageReader.class);
        SeckillReconciliationCandidateMapper candidates = mock(SeckillReconciliationCandidateMapper.class);
        SeckillReconciliationAnomalyMapper anomalies = mock(SeckillReconciliationAnomalyMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 2, 4, 0);
        when(anomalies.selectDatabaseTime()).thenReturn(cutoff);
        when(anomalies.upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(reader.scanRegistry("0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList("19")));
        when(candidates.selectCouponIdsAfter(0L, 1)).thenReturn(Collections.emptyList());
        when(reader.scanReservations(19L, "0", 1)).thenReturn(new SeckillRedisScanPageReader.ScanPage<>("0",
                Collections.singletonList(new SeckillRedisScanPageReader.HashEntry("7", "9001"))));
        when(reader.userScore(19L, "7")).thenReturn(
                (double) cutoff.atZone(ZoneId.systemDefault()).toEpochSecond() + 3600d);
        when(reader.scanUsers(19L, "0", 1)).thenReturn(SeckillRedisScanPageReader.ScanPage.empty("0"));
        when(reader.reservationCount(19L)).thenReturn(1L);
        when(reader.userCount(19L)).thenReturn(1L);
        when(reader.registryContains(19L)).thenReturn(true);
        when(reader.stockIsValid(19L)).thenReturn(true);
        SeckillReservationScanner scanner = new SeckillReservationScanner(reader, candidates, anomalies);

        assertTrue(scanner.scan(1).isEmpty());
        assertTrue(scanner.scan(1).isEmpty());
        assertTrue(!couponCompletions(scanner.drainCompletedCycles(), 19L).get(0).isClean());
        verify(anomalies).upsert(org.mockito.ArgumentMatchers.eq("INVALID_RESERVATION_MEMBER"),
                org.mockito.ArgumentMatchers.eq(19L), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyString());
    }

    private List<SeckillReservationScanner.ScanCompletion> couponCompletions(
            List<SeckillReservationScanner.ScanCompletion> completions, long couponId) {
        List<SeckillReservationScanner.ScanCompletion> filtered = new ArrayList<>();
        for (SeckillReservationScanner.ScanCompletion completion : completions) {
            if (completion.getCouponId() == couponId) filtered.add(completion);
        }
        return filtered;
    }
}
