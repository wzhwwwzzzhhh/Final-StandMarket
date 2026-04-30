package com.fashion.service;

import com.fashion.entity.ShoppingCart;
import java.util.List;

/**
 * 购物车Service
 */
public interface ShoppingCartService {
    
    /**
     * 添加商品到购物车
     */
    void add(ShoppingCart shoppingCart);
    
    /**
     * 获取用户购物车列表
     */
    List<ShoppingCart> list();
    
    /**
     * 更新购物车商品数量
     */
    void updateQuantity(ShoppingCart shoppingCart);
    
    /**
     * 删除购物车商品
     */
    void delete(Long id);
    
    /**
     * 批量删除购物车商品
     */
    void batchDelete(List<Long> ids);
    
    /**
     * 清空购物车
     */
    void clear();
    
    /**
     * 同步购物车商品价格和库存
     */
    void syncCartPrices();
    
    /**
     * 检查购物车商品库存
     * @return 库存不足的商品列表
     */
    List<ShoppingCart> checkStock();
    
    /**
     * 结算购物车商品
     * @param ids 选中的购物车商品ID列表
     * @return 结算结果，包含商品列表、总价、优惠等信息
     */
    com.fashion.vo.CartCheckoutVo checkout(List<Long> ids);
}