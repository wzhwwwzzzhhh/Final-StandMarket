package com.fashion.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Favorite implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    private LocalDateTime createTime;
}
