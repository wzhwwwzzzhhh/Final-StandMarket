package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.Product;
import com.fashion.result.Result;
import com.fashion.service.BrowseService;
import com.fashion.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端浏览历史
 */
@RestController
@RequestMapping("/user/browse")
public class UserBrowseController {

    @Autowired
    private BrowseService browseService;
    @Autowired
    private ProductService productService;

    /**
     * 记录浏览（需登录）
     */
    @PostMapping("/{productId}")
    public Result<String> record(@PathVariable Long productId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        if (productId == null || productService.getById(productId) == null) {
            return Result.error("商品不存在");
        }
        browseService.record(userId, productId);
        return Result.success("记录成功");
    }

    /**
     * 查询最近浏览商品列表（时间倒序）
     */
    @GetMapping("/list")
    public Result<List<Product>> list() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        return Result.success(browseService.listProducts(userId));
    }

    /**
     * 清空浏览历史
     */
    @DeleteMapping
    public Result<String> clear() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        browseService.clear(userId);
        return Result.success("已清空");
    }
}
