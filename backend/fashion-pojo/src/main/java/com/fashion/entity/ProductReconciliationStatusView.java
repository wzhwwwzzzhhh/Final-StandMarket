package com.fashion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** Sanitized operational view of the active or most recent reconciliation run. */
@Data
public class ProductReconciliationStatusView {
    private Long id;
    private String mode;
    private String phase;
    private String status;
    private boolean cursorValid;
    private Long scanCount;
    private Long driftCount;
    private Long repairCount;
    private Integer cleanVerifyCount;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedUntil;
    private String lastErrorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
}
