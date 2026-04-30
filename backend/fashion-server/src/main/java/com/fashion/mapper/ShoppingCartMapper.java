package com.fashion.mapper;

import com.fashion.entity.ShoppingCart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车Mapper
 */
@Mapper
public interface ShoppingCartMapper {
    
    /**
     * 根据用户id和商品id查询购物车项
     */
    ShoppingCart findByUserIdAndProductIdAndSkuInfo(ShoppingCart shoppingCart);
    
    /**
     * 更新购物车项数量和金额
     */
    void updateNumberAndAmount(ShoppingCart shoppingCart);
    
    /**
     * 插入购物车项
     */
    void insert(ShoppingCart shoppingCart);
    
    /**
     * 根据用户id查询购物车列表
     */
    List<ShoppingCart> findByUserId(Long userId);
    
    /**
     * 根据id查询购物车项
     */
    ShoppingCart findById(Long id);
    
    /**
     * 根据id删除购物车项
     */
    void deleteById(Long id);
    
    /**
     * 清空用户购物车
     */
    void deleteByUserId(Long userId);
    
    /**
     * 批量删除购物车项
     */
    void batchDelete(@Param("ids") List<Long> ids);
}