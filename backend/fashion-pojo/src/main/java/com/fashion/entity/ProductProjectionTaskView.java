package com.fashion.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductProjectionTaskView {
    private Long id;
    private String target;
    private Long productId;
    private Long catalogVersion;
    private String operation;
    private String status;
    private Integer attemptCount;
    private Integer claimCount;
    private Integer repairCount;
    private Integer manualReplayCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedUntil;
    private String lastErrorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastReplayedAt;
}
