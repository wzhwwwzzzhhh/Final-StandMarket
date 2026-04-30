package com.fashion.controller.user;

import com.fashion.dto.ProductQueryDTO;
import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.ProductService;
import com.fashion.utils.CacheClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * 用户端商品管理
 */
@RestController
@RequestMapping("/user/product")
public class UserProductController {

    @Autowired
    private ProductService productService;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 分页查询商品列表
     */
    @GetMapping
    public Result<PageResult<Product>> page(ProductQueryDTO query) {
        // 构建参数Map
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", query.getPage());
        params.put("pageSize", query.getPageSize());
        params.put("categoryId", query.getCategoryId());
        params.put("sortBy", query.getSortBy());
        params.put("keyword", query.getKeyword());
        params.put("tag", query.getTag());
        params.put("isSale", query.getIsSale());

        // 生成缓存键
        String cacheKey = "productPage:" + generateCacheKey(params);

        // 从缓存中获取分页结果
        PageResult<Product> pageResult = cacheClient.get(cacheKey, PageResult.class);
        // 调用Service层的分页查询方法
        if(pageResult == null){
            pageResult = productService.pageProducts(query);
            cacheClient.set(cacheKey, pageResult, 60 * 60 * 24L, TimeUnit.SECONDS);
        }
        return Result.success(pageResult);
    }

    private String generateCacheKey(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append(entry.getKey()).append(":")
              .append(entry.getValue() != null ? entry.getValue() : "null").append(":");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * 根据id查询商品详情
     */
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        Product product =cacheClient.queryWithLogicalExpire("product:", id, Product.class,
                productService::getById,
                60 * 60 * 24L, TimeUnit.SECONDS);

        if(product == null){
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }
}