package com.fashion.controller.user;

import com.fashion.dto.ProductQueryDTO;
import com.fashion.entity.Product;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.product.ProductCatalogCacheService;

import org.springframework.web.bind.annotation.*;


/**
 * 用户端商品管理
 */
@RestController
@RequestMapping("/user/product")
public class UserProductController {

    private final ProductCatalogCacheService catalogCacheService;

    public UserProductController(ProductCatalogCacheService catalogCacheService) {
        this.catalogCacheService = catalogCacheService;
    }

    /**
     * 分页查询商品列表
     */
    @GetMapping
    public Result<PageResult<Product>> page(ProductQueryDTO query) {
        try {
            return Result.success(catalogCacheService.page(query));
        } catch (IllegalArgumentException invalid) {
            return Result.error("商品查询参数错误");
        }
    }

    /**
     * 根据id查询商品详情
     */
    @GetMapping("/{id}")
    public Result<Product> getById(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        Product product = catalogCacheService.detail(id);

        if(product == null){
            return Result.error("商品不存在");
        }
        return Result.success(product);
    }
}
