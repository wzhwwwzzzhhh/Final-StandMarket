package com.fashion.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算页由服务端购物车快照计算出的可用券。
 */
@Data
public class AvailableCouponVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long templateId;
    private String templateName;
    private Integer templateType;
    private BigDecimal threshold;
    private BigDecimal discount;
    private Integer scopeType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;
    private BigDecimal discountAmount;
}
