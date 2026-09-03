package com.fashion.seckill;

import com.fashion.entity.SeckillCompensationRecord;
import com.fashion.mapper.SeckillCompensationRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
public class SeckillCompensationRecoveryTask {
    private static final int MAX_COMPENSATION_ATTEMPTS = 10;
    private final SeckillCompensationRecordMapper mapper;
    private final SeckillCompensationExecutor executor;

    public SeckillCompensationRecoveryTask(SeckillCompensationRecordMapper mapper,
                                           SeckillCompensationExecutor executor) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    SeckillCompensationRecoveryTask(SeckillCompensationRecordMapper mapper,
                                    com.fashion.mapper.SeckillOrderMapper orderMapper,
                                    SeckillReservationService reservationService,
                                    SeckillCompensationService compensationService,
                                    String worker) {
        this(mapper, new SeckillCompensationExecutor(mapper, orderMapper, reservationService,
                compensationService, worker));
    }

    @Scheduled(fixedDelayString = "${fashion.seckill.compensation-recovery-delay-ms:5000}")
    public void runOnce() {
        int exhausted = mapper.markExhausted(MAX_COMPENSATION_ATTEMPTS);
        if (exhausted > 0) {
            log.error("SECKILL_COMPENSATION_EXHAUSTED records={}", exhausted);
        }
        for (SeckillCompensationRecord record : mapper.selectRecoverable(100)) {
            try {
                executor.execute(record.getOrderNumber());
            } catch (RuntimeException failure) {
                log.warn("B6 compensation recovery deferred, orderNumber={}", record.getOrderNumber());
            }
        }
    }
}
