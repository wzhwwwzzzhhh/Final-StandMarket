package com.fashion.seckill;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Objects;

@Service
public class SeckillReservationService {
    public enum ReserveResult { RESERVED, SOLD_OUT, ENDED, NOT_STARTED, DUPLICATE, INVALID, LEDGER_CORRUPT, INFRA_FAILURE }
    public enum RollbackResult { APPLIED, APPLIED_LEDGER_INCONSISTENT, ALREADY_APPLIED, TOKEN_MISMATCH, LEDGER_CORRUPT, INVALID, INFRA_FAILURE }

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> rollbackScript;

    public SeckillReservationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.reserveScript = script("lua/seckill.lua");
        this.rollbackScript = script("lua/seckill_rollback.lua");
    }

    public ReserveResult reserve(Long couponId, Long userId, String orderNumber, long epochSeconds) {
        requireIdentity(couponId, userId, orderNumber);
        try {
            Long result = redisTemplate.execute(reserveScript, Arrays.asList(
                            stockKey(couponId), "seckill:coupon:startTime:" + couponId,
                            "seckill:coupon:endTime:" + couponId, usersKey(couponId),
                            reservationsKey(couponId), registryKey()),
                    "1", String.valueOf(epochSeconds), String.valueOf(userId), orderNumber);
            return reserveResult(result);
        } catch (RuntimeException e) {
            return ReserveResult.INFRA_FAILURE;
        }
    }

    public RollbackResult rollback(Long couponId, Long userId, String orderNumber) {
        requireIdentity(couponId, userId, orderNumber);
        try {
            Long result = redisTemplate.execute(rollbackScript, Arrays.asList(
                            stockKey(couponId), usersKey(couponId), reservationsKey(couponId), registryKey()),
                    String.valueOf(couponId), "1", String.valueOf(userId), orderNumber);
            return rollbackResult(result);
        } catch (RuntimeException e) {
            return RollbackResult.INFRA_FAILURE;
        }
    }

    private ReserveResult reserveResult(Long value) {
        if (value == null) return ReserveResult.INFRA_FAILURE;
        switch (value.intValue()) {
            case 0: return ReserveResult.RESERVED;
            case -1: return ReserveResult.SOLD_OUT;
            case -2: return ReserveResult.ENDED;
            case -3: return ReserveResult.NOT_STARTED;
            case -4: return ReserveResult.DUPLICATE;
            case -6: return ReserveResult.LEDGER_CORRUPT;
            default: return ReserveResult.INVALID;
        }
    }

    private RollbackResult rollbackResult(Long value) {
        if (value == null) return RollbackResult.INFRA_FAILURE;
        switch (value.intValue()) {
            case 2: return RollbackResult.APPLIED_LEDGER_INCONSISTENT;
            case 1: return RollbackResult.APPLIED;
            case 0: return RollbackResult.ALREADY_APPLIED;
            case -1: return RollbackResult.TOKEN_MISMATCH;
            case -2: return RollbackResult.LEDGER_CORRUPT;
            default: return RollbackResult.INVALID;
        }
    }

    private void requireIdentity(Long couponId, Long userId, String orderNumber) {
        if (couponId == null || couponId <= 0 || userId == null || userId <= 0
                || orderNumber == null || !orderNumber.matches("[0-9]{1,50}")) {
            throw new IllegalArgumentException("invalid seckill reservation identity");
        }
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }

    private String stockKey(Long couponId) { return "seckill:coupon:stock:" + couponId; }
    private String usersKey(Long couponId) { return "seckill:coupon:users:" + couponId; }
    private String reservationsKey(Long couponId) { return "seckill:coupon:reservations:" + couponId; }
    public String registryKey() { return "seckill:coupon:reservation:index"; }
}
