package com.fashion.vo;

import com.fashion.entity.ShoppingCart;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车结算结果VO
 */
@Data
public class CartCheckoutVo {
    /**
     * 选中的购物车商品列表
     */
    private List<ShoppingCart> items;
    
    /**
     * 总价
     */
    private BigDecimal totalPrice;
    
    /**
     * 优惠金额
     */
    private BigDecimal discount;
    
    /**
     * 最终价格
     */
    private BigDecimal finalPrice;
    
    /**
     * 选中的购物车商品ID列表
     */
    private List<Long> cartItemIds;
}
