package com.fashion.seckill;

import com.fashion.mapper.SeckillReconciliationCandidateMapper;
import com.fashion.mapper.SeckillReconciliationAnomalyMapper;
import com.fashion.entity.SeckillOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

@Component
public class SeckillReservationScanner {
    private static final int MAX_PENDING_COUPONS = 5000;
    private static final int MAX_OVERFLOW_SNAPSHOTS = 5000;
    private static final long MIN_REASONABLE_EPOCH_SECOND = 946684800L;
    private static final long MAX_FUTURE_CLOCK_SKEW_SECONDS = 60L;
    private final SeckillRedisScanPageReader reader;
    private final SeckillReconciliationCandidateMapper candidates;
    private final SeckillReconciliationAnomalyMapper anomalyMapper;
    private final Supplier<LocalDateTime> databaseTime;
    private final RedissonClient redissonClient;
    private final Deque<Long> couponQueue = new ArrayDeque<>();
    private final Set<Long> queuedCoupons = new HashSet<>();
    private final Map<Long, String> reservationCursors = new HashMap<>();
    private final Map<Long, String> userCursors = new HashMap<>();
    private final Set<Long> reservationCycleDone = new HashSet<>();
    private final Set<Long> userCycleDone = new HashSet<>();
    private final Map<Long, Boolean> hashTurn = new HashMap<>();
    private final Map<Long, LocalDateTime> cycleStartedAt = new HashMap<>();
    private final Set<Long> uncleanCycles = new HashSet<>();
    private final Deque<ScanCompletion> completedCycles = new ArrayDeque<>();
    private final Deque<OverflowSnapshot> overflowSnapshots = new ArrayDeque<>();
    private final Map<Long, Integer> overflowByCoupon = new HashMap<>();
    private String registryCursor = "0";
    private long databaseCursor;
    private long activeOrderCursor;
    private boolean registryDiscoveryTurn = true;
    private LocalDateTime registryCycleStartedAt;
    private boolean registryCycleUnclean;

    @Autowired
    public SeckillReservationScanner(SeckillRedisScanPageReader reader,
                                      SeckillReconciliationCandidateMapper candidates,
                                      SeckillReconciliationAnomalyMapper anomalyMapper,
                                      RedissonClient redissonClient) {
        this(reader, candidates, anomalyMapper, redissonClient, anomalyMapper::selectDatabaseTime);
    }

    public SeckillReservationScanner(SeckillRedisScanPageReader reader,
                                      SeckillReconciliationCandidateMapper candidates) {
        this(reader, candidates, null, null, LocalDateTime::now);
    }

    public SeckillReservationScanner(SeckillRedisScanPageReader reader,
                                      SeckillReconciliationCandidateMapper candidates,
                                      SeckillReconciliationAnomalyMapper anomalyMapper) {
        this(reader, candidates, anomalyMapper, null, anomalyMapper::selectDatabaseTime);
    }

    SeckillReservationScanner(SeckillRedisScanPageReader reader,
                              SeckillReconciliationCandidateMapper candidates,
                              SeckillReconciliationAnomalyMapper anomalyMapper,
                              Supplier<LocalDateTime> databaseTime) {
        this(reader, candidates, anomalyMapper, null, databaseTime);
    }

    SeckillReservationScanner(SeckillRedisScanPageReader reader,
                              SeckillReconciliationCandidateMapper candidates,
                              SeckillReconciliationAnomalyMapper anomalyMapper,
                              RedissonClient redissonClient,
                              Supplier<LocalDateTime> databaseTime) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.anomalyMapper = anomalyMapper;
        this.redissonClient = redissonClient;
        this.databaseTime = Objects.requireNonNull(databaseTime, "databaseTime");
    }

    public synchronized List<SeckillReservationSnapshot> scan(int limit) {
        if (limit < 1 || limit > 5000) throw new IllegalArgumentException("invalid reconciliation limit");
        Map<String, SeckillReservationSnapshot> snapshots = new LinkedHashMap<>();
        drainOverflow(limit, snapshots);
        discoverCoupons(limit);
        Set<Long> visitedThisRun = new HashSet<>();
        int couponVisits = 0;
        int maxCouponVisits = Math.min(limit, couponQueue.size());
        while (!couponQueue.isEmpty() && snapshots.size() < limit && couponVisits < maxCouponVisits) {
            Long couponId = couponQueue.removeFirst();
            queuedCoupons.remove(couponId);
            if (!visitedThisRun.add(couponId)) {
                enqueue(couponId);
                break;
            }
            couponVisits++;
            cycleStartedAt.computeIfAbsent(couponId, ignored ->
                    Objects.requireNonNull(databaseTime.get(), "databaseTime"));
            RLock lock = null;
            boolean acquired = redissonClient == null;
            try {
                if (redissonClient != null) {
                    lock = redissonClient.getLock("seckill:reconciliation:lock:" + couponId);
                    acquired = lock.tryLock(0, 30, TimeUnit.SECONDS);
                }
                if (!acquired) {
                    enqueue(couponId);
                    continue;
                }
                scanCouponPage(couponId, limit, snapshots);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                recordInvalidMember(couponId, "RECONCILIATION_LOCK_INTERRUPTED");
                enqueue(couponId);
                continue;
            } catch (RuntimeException lockFailure) {
                recordInvalidMember(couponId, "RECONCILIATION_LOCK_FAILURE");
                enqueue(couponId);
                continue;
            } finally {
                if (acquired && lock != null && lock.isHeldByCurrentThread()) lock.unlock();
            }
            if (!reservationCycleDone.contains(couponId) || !userCycleDone.contains(couponId)
                    || overflowByCoupon.containsKey(couponId)) {
                enqueue(couponId);
            } else {
                validateCompletedLedger(couponId);
                completedCycles.addLast(new ScanCompletion(couponId, cycleStartedAt.remove(couponId),
                        !uncleanCycles.remove(couponId)));
                reservationCursors.remove(couponId);
                userCursors.remove(couponId);
                reservationCycleDone.remove(couponId);
                userCycleDone.remove(couponId);
                hashTurn.remove(couponId);
            }
        }
        return new ArrayList<>(snapshots.values());
    }

    public synchronized List<ScanCompletion> drainCompletedCycles() {
        List<ScanCompletion> result = new ArrayList<>(completedCycles);
        completedCycles.clear();
        return result;
    }

    public synchronized List<SeckillReservationSnapshot> scanActiveOrders(int limit) {
        if (limit < 1 || limit > 5000) throw new IllegalArgumentException("invalid active order limit");
        List<SeckillOrder> orders = candidates.selectOrderRowsAfter(activeOrderCursor, limit);
        if (orders == null || orders.isEmpty()) {
            activeOrderCursor = 0L;
            return java.util.Collections.emptyList();
        }
        List<SeckillReservationSnapshot> snapshots = new ArrayList<>(orders.size());
        for (SeckillOrder order : orders) {
            if (order == null || order.getId() == null || order.getId() <= activeOrderCursor) continue;
            activeOrderCursor = order.getId();
            if (order.getStatus() == null || (order.getStatus() != 1 && order.getStatus() != 2)) continue;
            if (order.getCouponId() == null || order.getCouponId() <= 0 || order.getUserId() == null
                    || order.getUserId() <= 0 || !validOrder(order.getOrderNumber())) {
                recordActiveOrderLookupFailure(order, "INVALID_ACTIVE_ORDER_IDENTITY");
                continue;
            }
            try {
                String userId = String.valueOf(order.getUserId());
                String reservation = reader.reservationToken(order.getCouponId(), userId);
                Double score = reader.userScore(order.getCouponId(), userId);
                snapshots.add(new SeckillReservationSnapshot(order.getCouponId(), order.getUserId(),
                        order.getOrderNumber(), order.getOrderNumber().equals(reservation),
                        score != null, Duration.ZERO));
            } catch (RuntimeException infrastructureFailure) {
                recordActiveOrderLookupFailure(order, "ACTIVE_ORDER_LOOKUP_FAILURE");
            }
        }
        return snapshots;
    }

    public synchronized void markCycleUnclean(Long couponId) {
        if (couponId != null && cycleStartedAt.containsKey(couponId)) {
            uncleanCycles.add(couponId);
        }
    }

    private void discoverCoupons(int limit) {
        int capacity = MAX_PENDING_COUPONS - couponQueue.size();
        int budget = Math.min(limit, Math.max(0, capacity));
        if (budget == 0) return;
        int registryBudget;
        if (budget == 1) {
            registryBudget = registryDiscoveryTurn ? 1 : 0;
            registryDiscoveryTurn = !registryDiscoveryTurn;
        } else {
            registryBudget = (budget + 1) / 2;
        }
        int databaseBudget = budget - registryBudget;
        if (registryBudget > 0) {
            if (registryCycleStartedAt == null) {
                registryCycleStartedAt = Objects.requireNonNull(databaseTime.get(), "databaseTime");
            }
            try {
                SeckillRedisScanPageReader.ScanPage<String> registry =
                        reader.scanRegistry(registryCursor, registryBudget);
                registryCursor = registry.getNextCursor();
                for (String value : registry.getEntries()) {
                    Long couponId = parsePositiveLong(value);
                    if (couponId == null) recordInvalidRegistryMember(value);
                    else enqueue(couponId);
                }
                if ("0".equals(registryCursor)) {
                    completedCycles.addLast(new ScanCompletion(0L, registryCycleStartedAt,
                            !registryCycleUnclean));
                    registryCycleStartedAt = null;
                    registryCycleUnclean = false;
                }
            } catch (RuntimeException registryFailure) {
                registryCursor = "0";
                recordInvalidRegistryMember("REGISTRY_SCAN_FAILURE");
                completedCycles.addLast(new ScanCompletion(0L, registryCycleStartedAt, false));
                registryCycleStartedAt = null;
                registryCycleUnclean = false;
            }
        }

        if (databaseBudget > 0) {
            List<Long> database = candidates.selectCouponIdsAfter(databaseCursor, databaseBudget);
            if (database == null || database.isEmpty()) {
                databaseCursor = 0L;
                return;
            }
            for (Long couponId : database) {
                if (couponId != null && couponId > databaseCursor) {
                    databaseCursor = couponId;
                    enqueue(couponId);
                }
            }
        }
    }

    private void scanCouponPage(Long couponId, int limit,
                                Map<String, SeckillReservationSnapshot> snapshots) {
        int remaining = limit - snapshots.size();
        boolean hashPending = !reservationCycleDone.contains(couponId);
        boolean userPending = !userCycleDone.contains(couponId);
        int hashBudget;
        if (!hashPending) hashBudget = 0;
        else if (!userPending) hashBudget = remaining;
        else if (remaining == 1) hashBudget = hashTurn.getOrDefault(couponId, true) ? 1 : 0;
        else hashBudget = (remaining + 1) / 2;
        int userBudget = userPending ? remaining - hashBudget : 0;
        hashTurn.put(couponId, hashBudget == 0);

        if (hashBudget > 0) {
            String hashCursor = reservationCursors.getOrDefault(couponId, "0");
            SeckillRedisScanPageReader.ScanPage<SeckillRedisScanPageReader.HashEntry> hashPage;
            try {
                hashPage = reader.scanReservations(couponId, hashCursor, hashBudget);
            } catch (RuntimeException wrongType) {
                recordInvalidMember(couponId, "RESERVATION_SCAN_FAILURE");
                reservationCycleDone.add(couponId);
                hashPage = SeckillRedisScanPageReader.ScanPage.empty("0");
            }
            reservationCursors.put(couponId, hashPage.getNextCursor());
            if ("0".equals(hashPage.getNextCursor())) reservationCycleDone.add(couponId);
            for (SeckillRedisScanPageReader.HashEntry entry : hashPage.getEntries()) {
                if (!validUser(entry.getUserId()) || !validOrder(entry.getOrderNumber())) {
                    recordInvalidMember(couponId, entry.getUserId() + ":" + entry.getOrderNumber());
                    continue;
                }
                Double score;
                try {
                    score = reader.userScore(couponId, entry.getUserId());
                } catch (RuntimeException wrongType) {
                    recordInvalidMember(couponId, "USER_SCORE_FAILURE:" + entry.getUserId());
                    continue;
                }
                if (score != null && !validScore(couponId, score)) {
                    recordInvalidMember(couponId, entry.getUserId() + ":" + score);
                    continue;
                }
                offerSnapshot(snapshots, limit, couponId, entry.getUserId(), entry.getOrderNumber(),
                        true, score != null, score);
            }
        }

        if (userBudget > 0) {
            String zsetCursor = userCursors.getOrDefault(couponId, "0");
            SeckillRedisScanPageReader.ScanPage<SeckillRedisScanPageReader.ZEntry> userPage;
            try {
                userPage = reader.scanUsers(couponId, zsetCursor, userBudget);
            } catch (RuntimeException wrongType) {
                recordInvalidMember(couponId, "USER_SCAN_FAILURE");
                userCycleDone.add(couponId);
                userPage = SeckillRedisScanPageReader.ScanPage.empty("0");
            }
            userCursors.put(couponId, userPage.getNextCursor());
            if ("0".equals(userPage.getNextCursor())) userCycleDone.add(couponId);
            for (SeckillRedisScanPageReader.ZEntry entry : userPage.getEntries()) {
                if (!validUser(entry.getUserId())) {
                    recordInvalidMember(couponId, entry.getUserId());
                    continue;
                }
                String token;
                try {
                    token = reader.reservationToken(couponId, entry.getUserId());
                } catch (RuntimeException wrongType) {
                    recordInvalidMember(couponId, "RESERVATION_TOKEN_FAILURE:" + entry.getUserId());
                    continue;
                }
                if (token != null && !validOrder(token)) {
                    recordInvalidMember(couponId, entry.getUserId() + ":" + token);
                    continue;
                }
                if (!validScore(couponId, entry.getScore())) {
                    recordInvalidMember(couponId, entry.getUserId() + ":" + entry.getScore());
                    continue;
                }
                offerSnapshot(snapshots, limit, couponId, entry.getUserId(), token,
                        token != null, true, entry.getScore());
            }
        }
    }

    private void offerSnapshot(Map<String, SeckillReservationSnapshot> snapshots, int limit,
                               Long couponId, String userId, String orderNumber,
                               boolean hashPresent, boolean zsetPresent, Double score) {
        String key = couponId + ":" + userId;
        long cutoffEpochSecond = cycleStartedAt.get(couponId)
                .atZone(ZoneId.systemDefault()).toEpochSecond();
        long ageSeconds = score == null ? 0L
                : Math.max(0L, cutoffEpochSecond - score.longValue());
        SeckillReservationSnapshot snapshot = new SeckillReservationSnapshot(couponId,
                Long.valueOf(userId), orderNumber, hashPresent, zsetPresent,
                Duration.ofSeconds(ageSeconds));
        if (snapshots.containsKey(key)) {
            snapshots.put(key, snapshot);
        } else if (snapshots.size() < limit) {
            snapshots.put(key, snapshot);
        } else {
            if (overflowSnapshots.size() >= MAX_OVERFLOW_SNAPSHOTS) {
                recordInvalidMember(couponId, "SCAN_PAGE_LIMIT_EXCEEDED");
                return;
            }
            overflowSnapshots.addLast(new OverflowSnapshot(key, snapshot));
            overflowByCoupon.put(couponId, overflowByCoupon.getOrDefault(couponId, 0) + 1);
        }
    }

    private boolean validScore(Long couponId, Double score) {
        if (score == null || !Double.isFinite(score) || score.doubleValue() != Math.rint(score)) return false;
        LocalDateTime cutoff = cycleStartedAt.get(couponId);
        if (cutoff == null) return false;
        long epochSecond = score.longValue();
        long cutoffEpochSecond = cutoff.atZone(ZoneId.systemDefault()).toEpochSecond();
        return epochSecond >= MIN_REASONABLE_EPOCH_SECOND
                && epochSecond <= cutoffEpochSecond + MAX_FUTURE_CLOCK_SKEW_SECONDS;
    }

    private void drainOverflow(int limit, Map<String, SeckillReservationSnapshot> snapshots) {
        while (!overflowSnapshots.isEmpty() && snapshots.size() < limit) {
            OverflowSnapshot overflow = overflowSnapshots.removeFirst();
            Long couponId = overflow.snapshot.getCouponId();
            int remaining = overflowByCoupon.getOrDefault(couponId, 1) - 1;
            if (remaining == 0) overflowByCoupon.remove(couponId);
            else overflowByCoupon.put(couponId, remaining);
            snapshots.putIfAbsent(overflow.key, overflow.snapshot);
        }
    }

    private void recordInvalidMember(Long couponId, String raw) {
        uncleanCycles.add(couponId);
        if (anomalyMapper != null) {
            String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                    String.valueOf(raw).getBytes(StandardCharsets.UTF_8))
                    .substring("INVALID:".length());
            if (anomalyMapper.upsert("INVALID_RESERVATION_MEMBER", couponId,
                    null, null, hash) < 1) {
                throw new IllegalStateException("invalid reservation member anomaly was not persisted");
            }
        }
    }

    private void recordInvalidRegistryMember(String raw) {
        registryCycleUnclean = true;
        if (anomalyMapper != null) {
            String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                    String.valueOf(raw).getBytes(StandardCharsets.UTF_8))
                    .substring("INVALID:".length());
            if (anomalyMapper.upsert("INVALID_REGISTRY_MEMBER", 0L,
                    null, null, hash) < 1) {
                throw new IllegalStateException("invalid registry member anomaly was not persisted");
            }
        }
    }

    private void recordActiveOrderLookupFailure(SeckillOrder order, String type) {
        if (anomalyMapper == null || order == null || order.getCouponId() == null) return;
        uncleanCycles.add(order.getCouponId());
        String raw = type + ":" + order.getId() + ":" + order.getOrderNumber();
        String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                raw.getBytes(StandardCharsets.UTF_8)).substring("INVALID:".length());
        if (anomalyMapper.upsert(type, order.getCouponId(), order.getUserId(),
                order.getOrderNumber(), hash) < 1) {
            throw new IllegalStateException("active order reservation anomaly was not persisted");
        }
    }

    private void validateCompletedLedger(Long couponId) {
        long hashCount;
        long userCount;
        boolean registered;
        boolean stockValid;
        try {
            hashCount = reader.reservationCount(couponId);
            userCount = reader.userCount(couponId);
            registered = reader.registryContains(couponId);
            stockValid = reader.stockIsValid(couponId);
        } catch (RuntimeException wrongType) {
            recordInvalidMember(couponId, "LEDGER_VALIDATION_FAILURE");
            return;
        }
        boolean stockRequired = hashCount > 0 || userCount > 0 || registered;
        boolean consistent = hashCount == userCount && registered == (hashCount > 0)
                && (!stockRequired || stockValid);
        if (consistent) return;
        uncleanCycles.add(couponId);
        if (anomalyMapper != null) {
            String raw = couponId + ":" + hashCount + ":" + userCount + ":" + registered;
            String hash = SeckillMessageIdentity.quarantineKey("", "", "",
                    raw.getBytes(StandardCharsets.UTF_8)).substring("INVALID:".length());
            if (anomalyMapper.upsert("REGISTRY_LEDGER_MISMATCH", couponId,
                    null, null, hash) < 1) {
                throw new IllegalStateException("registry ledger anomaly was not persisted");
            }
        }
    }

    private boolean validUser(String userId) {
        return parsePositiveLong(userId) != null;
    }

    private boolean validOrder(String orderNumber) {
        return orderNumber != null && orderNumber.matches("[0-9]{1,50}");
    }

    private Long parsePositiveLong(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return null;
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private void enqueue(Long couponId) {
        if (couponId != null && couponId > 0 && couponQueue.size() < MAX_PENDING_COUPONS
                && queuedCoupons.add(couponId)) {
            couponQueue.addLast(couponId);
        }
    }

    public static final class ScanCompletion {
        private final Long couponId;
        private final LocalDateTime cycleStartedAt;
        private final boolean clean;

        public ScanCompletion(Long couponId, LocalDateTime cycleStartedAt) {
            this(couponId, cycleStartedAt, true);
        }

        public ScanCompletion(Long couponId, LocalDateTime cycleStartedAt, boolean clean) {
            this.couponId = Objects.requireNonNull(couponId, "couponId");
            this.cycleStartedAt = Objects.requireNonNull(cycleStartedAt, "cycleStartedAt");
            this.clean = clean;
        }

        public Long getCouponId() { return couponId; }
        public LocalDateTime getCycleStartedAt() { return cycleStartedAt; }
        public boolean isClean() { return clean; }
    }

    private static final class OverflowSnapshot {
        private final String key;
        private final SeckillReservationSnapshot snapshot;

        private OverflowSnapshot(String key, SeckillReservationSnapshot snapshot) {
            this.key = key;
            this.snapshot = snapshot;
        }
    }
}
