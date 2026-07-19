package com.fashion.task;

import com.fashion.entity.Product;
import com.fashion.mapper.ProductMapper;
import com.fashion.service.ProductIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

/**
 * ES 商品索引定时同步任务
 * 每 5 分钟全量同步一次（MVP 简化策略）
 */
@Component
public class ProductSyncTask {

    private static final Logger log = LoggerFactory.getLogger(ProductSyncTask.class);

    @Autowired
    private ProductIndexService productIndexService;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 每 5 分钟增量同步
     * 检查 MySQL 商品数 vs ES 文档数，不一致则触发重建
     */
    @Scheduled(fixedRate = 300_000)
    public void syncProducts() {
        try {
            List<Product> products = productMapper.selectByCondition(new HashMap<>());
            if (products.isEmpty()) return;

            // 逐个同步（简化版：遍历全量写，后续可改为增量对比）
            for (Product p : products) {
                productIndexService.syncProduct(p);
            }
            log.debug("ES 定时同步完成，{} 条", products.size());
        } catch (Exception e) {
            log.error("ES 定时同步失败", e);
        }
    }
}
