package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.entity.SeckillOrder;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import com.fashion.mapper.SeckillOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
public class SeckillCompensationExecutor {
    private static final long RELEASE_EVIDENCE_MASK =
            SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED
                    | SeckillCompensationService.EVIDENCE_CONSUME_EXHAUSTED
                    | SeckillCompensationService.EVIDENCE_CANCEL_COMMITTED
                    | SeckillCompensationService.EVIDENCE_ORPHAN_RECONCILED;

    private final SeckillCompensationRecordMapper mapper;
    private final SeckillOrderMapper orderMapper;
    private final SeckillReservationService reservationService;
    private final SeckillCompensationService compensationService;
    private final Supplier<String> claimTokenSupplier;

    public SeckillCompensationExecutor(SeckillCompensationRecordMapper mapper,
                                       SeckillOrderMapper orderMapper,
                                       SeckillReservationService reservationService,
                                       SeckillCompensationService compensationService) {
        this(mapper, orderMapper, reservationService, compensationService,
                () -> "b6-compensation-" + UUID.randomUUID());
    }

    SeckillCompensationExecutor(SeckillCompensationRecordMapper mapper,
                                SeckillOrderMapper orderMapper,
                                SeckillReservationService reservationService,
                                SeckillCompensationService compensationService,
                                String worker) {
        this(mapper, orderMapper, reservationService, compensationService, () -> worker);
    }

    SeckillCompensationExecutor(SeckillCompensationRecordMapper mapper,
                                SeckillOrderMapper orderMapper,
                                SeckillReservationService reservationService,
                                SeckillCompensationService compensationService,
                                Supplier<String> claimTokenSupplier) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
        this.claimTokenSupplier = Objects.requireNonNull(claimTokenSupplier, "claimTokenSupplier");
    }

    public SeckillReservationService.RollbackResult execute(String orderNumber) {
        String claimToken = Objects.requireNonNull(claimTokenSupplier.get(), "claimToken");
        SeckillCompensationRecord before = mapper.selectByOrderNumber(orderNumber);
        if (before == null) {
            throw new IllegalStateException("compensation record is unavailable");
        }
        if ("SUCCEEDED".equals(before.getStatus())) {
            compensationService.convergeInitialMessage(orderNumber);
            return SeckillReservationService.RollbackResult.ALREADY_APPLIED;
        }
        if (mapper.claimByOrder(orderNumber, claimToken) != 1) {
            SeckillCompensationRecord latest = mapper.selectByOrderNumber(orderNumber);
            if (latest != null && "SUCCEEDED".equals(latest.getStatus())) {
                compensationService.convergeInitialMessage(orderNumber);
                return SeckillReservationService.RollbackResult.ALREADY_APPLIED;
            }
            return SeckillReservationService.RollbackResult.INFRA_FAILURE;
        }

        SeckillCompensationRecord claimed = mapper.selectByOrderNumber(orderNumber);
        if (claimed == null || !Objects.equals(claimed.getId(), before.getId())) {
            throw new IllegalStateException("claimed compensation record is unavailable");
        }
        return executeClaimed(claimed, claimToken);
    }

    public boolean isClaimActiveOrSucceeded(String orderNumber) {
        SeckillCompensationRecord latest = mapper.selectByOrderNumber(orderNumber);
        if (latest == null) return false;
        if ("SUCCEEDED".equals(latest.getStatus())) {
            compensationService.convergeInitialMessage(orderNumber);
            return true;
        }
        return "IN_PROGRESS".equals(latest.getStatus())
                && latest.getLockedUntil() != null
                && latest.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private SeckillReservationService.RollbackResult executeClaimed(SeckillCompensationRecord record,
                                                                    String claimToken) {
        if (!isReleaseAuthorized(record)) {
            if (mapper.markManualRequiredOwned(record.getId(), claimToken,
                    "order state forbids reservation release") != 1) {
                throw new IllegalStateException("compensation lease is no longer owned");
            }
            return SeckillReservationService.RollbackResult.TOKEN_MISMATCH;
        }
        try {
            SeckillReservationService.RollbackResult result = reservationService.rollback(
                    record.getCouponId(), record.getUserId(), record.getOrderNumber());
            compensationService.recordRollbackResult(record, claimToken, result);
            return result;
        } catch (RuntimeException failure) {
            log.warn("B6 compensation execution deferred, orderNumber={}", record.getOrderNumber());
            try {
                compensationService.recordRollbackResult(record, claimToken,
                        SeckillReservationService.RollbackResult.INFRA_FAILURE);
            } catch (RuntimeException leaseLost) {
                log.warn("B6 compensation result not persisted by stale worker, orderNumber={}",
                        record.getOrderNumber());
            }
            return SeckillReservationService.RollbackResult.INFRA_FAILURE;
        }
    }

    private boolean isReleaseAuthorized(SeckillCompensationRecord record) {
        long evidence = record.getEvidenceMask() == null ? 0L : record.getEvidenceMask();
        SeckillOrder order = orderMapper.selectByOrderNumber(record.getOrderNumber());
        if (order == null) {
            return (evidence & RELEASE_EVIDENCE_MASK) != 0;
        }
        return Objects.equals(order.getUserId(), record.getUserId())
                && Objects.equals(order.getCouponId(), record.getCouponId())
                && Integer.valueOf(3).equals(order.getStatus())
                && (evidence & (SeckillCompensationService.EVIDENCE_CANCEL_COMMITTED
                    | SeckillCompensationService.EVIDENCE_CANCELLED_ORDER_RECONCILED)) != 0;
    }
}
