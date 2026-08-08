package com.fashion.controller.user;

import com.fashion.context.BaseContext;
import com.fashion.entity.Favorite;
import com.fashion.result.Result;
import com.fashion.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user/favorite")
public class UserFavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping("/add/{productId}")
    public Result<String> add(@PathVariable Long productId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        try {
            favoriteService.add(userId, productId);
            return Result.success("收藏成功");
        } catch (Exception e) {
            return Result.error("收藏失败");
        }
    }

    @DeleteMapping("/remove/{productId}")
    public Result<String> remove(@PathVariable Long productId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        try {
            favoriteService.remove(userId, productId);
            return Result.success("已取消收藏");
        } catch (Exception e) {
            return Result.error("取消收藏失败");
        }
    }

    @GetMapping("/check/{productId}")
    public Result<Map<String, Boolean>> check(@PathVariable Long productId) {
        Long userId = BaseContext.getUserId();
        Map<String, Boolean> result = new HashMap<>();
        if (userId == null) {
            result.put("favorited", false);
            return Result.success(result);
        }
        boolean favorited = favoriteService.isFavorited(userId, productId);
        result.put("favorited", favorited);
        return Result.success(result);
    }

    @GetMapping("/list")
    public Result<List<Favorite>> list() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        List<Favorite> favorites = favoriteService.list(userId);
        return Result.success(favorites);
    }

    @GetMapping("/count")
    public Result<Map<String, Integer>> count() {
        Long userId = BaseContext.getUserId();
        Map<String, Integer> result = new HashMap<>();
        if (userId == null) {
            result.put("count", 0);
            return Result.success(result);
        }
        int count = favoriteService.count(userId);
        result.put("count", count);
        return Result.success(result);
    }
}
