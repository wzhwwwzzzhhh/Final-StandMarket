package com.fashion.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductCatalogRevision {
    private Long productId;
    private Long itemVersion;
    private String itemState;
    private String esLockedBy;
    private LocalDateTime esLockedUntil;
    private LocalDateTime updatedAt;
}
