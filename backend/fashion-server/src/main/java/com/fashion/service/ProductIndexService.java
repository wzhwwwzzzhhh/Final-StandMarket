package com.fashion.service;

import com.fashion.entity.Product;

import java.util.Map;

public interface ProductIndexService {
    /**
     * 全量重建索引（删除 + 创建 + 同步所有商品）
     */
    void rebuildIndex();

    /**
     * 单个商品同步（新增/更新时调用）
     */
    void syncProduct(Product product);

    /**
     * 从 ES 删除商品
     */
    void deleteProduct(Long productId);

    /**
     * 获取索引状态
     */
    Map<String, Object> getIndexStatus();
}
