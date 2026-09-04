package com.fashion.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 匿名商品评价接口的最小公开字段集合。
 */
@Data
public class ReviewPublicVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer rating;
    private String content;
    private String images;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    private String displayName;
}
