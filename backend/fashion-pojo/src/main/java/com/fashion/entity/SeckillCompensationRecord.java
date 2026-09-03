package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillCompensationRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String compensationAction;
    private String orderNumber;
    private Long userId;
    private Long couponId;
    private Long evidenceMask;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedUntil;
    private String lastReason;
    private String firstReason;
    private String lastResult;
    private String lastError;
    private Long version;
    private LocalDateTime redisAppliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
