package com.fashion.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ProductProjectionStatusSummary {
    private String target;
    private String status;
    private Long taskCount;
    private LocalDateTime oldestCreatedAt;
    private Integer maxAttempts;
    private Long totalAttempts;
    private LocalDateTime nextRetryAt;
    private String lastErrorSummary;
}
