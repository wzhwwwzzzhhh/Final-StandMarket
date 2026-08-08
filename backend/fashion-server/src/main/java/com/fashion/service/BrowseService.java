package com.fashion.service;

import com.fashion.entity.Product;

import java.util.List;

/**
 * 浏览历史服务
 * 基于 Redis List 存储，新浏览放头部、去重、截断 50 条
 */
public interface BrowseService {

    /**
     * 记录一次浏览（去重后置顶，超出 50 条截断）
     * @param userId 用户ID
     * @param productId 商品ID
     */
    void record(Long userId, Long productId);

    /**
     * 查询用户最近浏览的商品列表（时间倒序）
     * @param userId 用户ID
     * @return 商品列表
     */
    List<Product> listProducts(Long userId);

    /**
     * 清空用户浏览历史
     * @param userId 用户ID
     */
    void clear(Long userId);
}
