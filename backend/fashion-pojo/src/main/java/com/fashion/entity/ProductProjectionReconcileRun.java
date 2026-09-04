package com.fashion.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductProjectionReconcileRun {
    private Long id;
    private String mode;
    private String phase;
    private String status;
    private String cursorPayload;
    private Long scanCount;
    private Long driftCount;
    private Long repairCount;
    private Integer cleanVerifyCount;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedUntil;
    private String lastErrorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
