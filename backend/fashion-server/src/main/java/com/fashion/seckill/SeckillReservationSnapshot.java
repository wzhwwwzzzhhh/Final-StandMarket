package com.fashion.seckill;

import java.time.Duration;
import java.util.Objects;

public final class SeckillReservationSnapshot {
    private final Long couponId;
    private final Long userId;
    private final String orderNumber;
    private final boolean hashTokenPresent;
    private final boolean zsetMemberPresent;
    private final Duration age;

    public SeckillReservationSnapshot(Long couponId, Long userId, String orderNumber,
                                      boolean hashTokenPresent, boolean zsetMemberPresent,
                                      Duration age) {
        this.couponId = couponId;
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.hashTokenPresent = hashTokenPresent;
        this.zsetMemberPresent = zsetMemberPresent;
        this.age = Objects.requireNonNull(age, "age");
    }

    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }
    public String getOrderNumber() { return orderNumber; }
    public boolean isHashTokenPresent() { return hashTokenPresent; }
    public boolean isZsetMemberPresent() { return zsetMemberPresent; }
    public Duration getAge() { return age; }
}
