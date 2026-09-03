package com.fashion.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SeckillMessageLog implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String messageId;
    private String messageType;
    private String publishPurpose;
    private String businessKey;
    private String sourceMessageId;
    private String sourceMessageIdHash;
    private String sourceMessageIdPrefix;
    private String bodySha256;
    private Long bodySize;
    private Long userId;
    private Long couponId;
    private String payload;
    private Integer payloadSchemaVersion;
    private String exchangeName;
    private String routingKey;
    private String status;
    private String deadLetterStatus;
    private String confirmStatus;
    private Boolean returned;
    private Integer returnReplyCode;
    private String returnReplyText;
    private String currentCorrelationId;
    private Integer publishAttempt;
    private Integer consumeAttempt;
    private Integer processingAttempt;
    private Integer fallbackAttempt;
    private LocalDateTime dueAt;
    private LocalDateTime nextRetryAt;
    private String lockedBy;
    private LocalDateTime lockedUntil;
    private Long version;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime consumedAt;
}
