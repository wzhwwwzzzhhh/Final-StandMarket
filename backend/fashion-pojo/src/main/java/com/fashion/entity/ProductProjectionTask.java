package com.fashion.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductProjectionTask {
    private Long id;
    private String target;
    private Long productId;
    private Long catalogVersion;
    private String operation;
    private String payload;
    private String payloadSha256;
    private String status;
    private Integer attemptCount;
    private Integer claimCount;
    private Integer repairCount;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedUntil;
    private String lastErrorSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private Integer manualReplayCount;
    private LocalDateTime lastReplayedAt;
}
