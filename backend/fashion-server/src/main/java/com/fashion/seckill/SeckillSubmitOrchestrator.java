package com.fashion.seckill;

import com.fashion.entity.SeckillMessageLog;
import com.fashion.utils.UniqueID;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

@Service
@Slf4j
public class SeckillSubmitOrchestrator {
    public enum Outcome {
        PROCESSING, SOLD_OUT, ENDED, NOT_STARTED, DUPLICATE, RESERVATION_FAILED, DELIVERY_FAILED
    }

    public static final class Submission {
        private final Outcome outcome;
        private final String orderNumber;

        Submission(Outcome outcome, String orderNumber) {
            this.outcome = outcome;
            this.orderNumber = orderNumber;
        }

        public Outcome getOutcome() { return outcome; }
        public String getOrderNumber() { return orderNumber; }
    }

    private final UniqueID uniqueID;
    private final SeckillReservationService reservationService;
    private final SeckillMessagePrepareTransaction prepareTransaction;
    private final SeckillReliablePublisher publisher;
    private final SeckillCompensationService compensationService;
    private final SeckillCompensationExecutor compensationExecutor;

    public SeckillSubmitOrchestrator(UniqueID uniqueID,
                                     SeckillReservationService reservationService,
                                     SeckillMessagePrepareTransaction prepareTransaction,
                                     SeckillReliablePublisher publisher,
                                     SeckillCompensationService compensationService,
                                     SeckillCompensationExecutor compensationExecutor) {
        this.uniqueID = Objects.requireNonNull(uniqueID, "uniqueID");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
        this.prepareTransaction = Objects.requireNonNull(prepareTransaction, "prepareTransaction");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.compensationService = Objects.requireNonNull(compensationService, "compensationService");
        this.compensationExecutor = Objects.requireNonNull(compensationExecutor, "compensationExecutor");
    }

    public Submission submit(Long userId, Long couponId, long epochSeconds) {
        String orderNumber = String.valueOf(uniqueID.nextId("seckill:order"));
        SeckillReservationService.ReserveResult reserved =
                reservationService.reserve(couponId, userId, orderNumber, epochSeconds);
        Outcome failure = reserveFailure(reserved);
        if (failure != null) {
            return new Submission(failure, null);
        }

        String payload = "{\"userId\":" + userId + ",\"couponId\":" + couponId
                + ",\"orderNumber\":\"" + orderNumber + "\"}";
        SeckillMessageLog log;
        try {
            log = prepareTransaction.prepareOrderCreate(orderNumber, userId, couponId, payload);
            publisher.publish(log.getMessageId(), "INITIAL");
            return new Submission(Outcome.PROCESSING, orderNumber);
        } catch (RuntimeException failureCause) {
            try {
                compensateInitialFailure(orderNumber, userId, couponId);
            } catch (RuntimeException compensationFailure) {
                compensationFailure.addSuppressed(failureCause);
                throw compensationFailure;
            }
            return new Submission(Outcome.DELIVERY_FAILED, orderNumber);
        }
    }

    private void compensateInitialFailure(String orderNumber, Long userId, Long couponId) {
        RuntimeException persistenceFailure = null;
        try {
            compensationService.requestRelease(orderNumber, userId, couponId,
                    "INITIAL_DELIVERY_FAILED", SeckillCompensationService.EVIDENCE_INITIAL_DELIVERY_FAILED);
        } catch (RuntimeException failure) {
            persistenceFailure = failure;
        }
        if (persistenceFailure == null) {
            compensationExecutor.execute(orderNumber);
        } else {
            reservationService.rollback(couponId, userId, orderNumber);
            log.error("SECKILL_INITIAL_ROLLBACK_WITHOUT_DURABLE_EVIDENCE orderNumber={} userId={} couponId={}",
                    orderNumber, userId, couponId);
            throw persistenceFailure;
        }
    }

    private Outcome reserveFailure(SeckillReservationService.ReserveResult result) {
        switch (result) {
            case RESERVED: return null;
            case SOLD_OUT: return Outcome.SOLD_OUT;
            case ENDED: return Outcome.ENDED;
            case NOT_STARTED: return Outcome.NOT_STARTED;
            case DUPLICATE: return Outcome.DUPLICATE;
            default: return Outcome.RESERVATION_FAILED;
        }
    }
}
