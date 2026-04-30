package com.fashion.controller.user;

import cn.hutool.json.JSONUtil;
import com.fashion.entity.Category;
import com.fashion.result.Result;
import com.fashion.service.CategoryService;
import com.fashion.utils.CacheClient;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 用户端分类管理
 */
@RestController
@RequestMapping("/user/category")
public class UserCategoryController {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 获取分类列表
     */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        List<Category> list;
        
        String cacheData = cacheClient.get("category:list", String.class);
        if(cacheData != null && !cacheData.isEmpty()){
            try {
                list = JSONUtil.toList(cacheData, Category.class);
            } catch (Exception e) {
                // 缓存解析失败，从数据库查询
                list = categoryService.listByType(null);
            }
        } else {
            // 缓存不存在，从数据库查询
            list = categoryService.listByType(null);
        }
        
        cacheClient.set("category:list", list, 60 * 60 * 24L, TimeUnit.SECONDS);
        return Result.success(list);
    }
}
