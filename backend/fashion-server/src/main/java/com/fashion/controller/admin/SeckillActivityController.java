package com.fashion.controller.admin;

import com.fashion.entity.SeckillActivity;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.SeckillActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀活动管理
 */
@RestController
@RequestMapping("/admin/seckill/activity")
public class SeckillActivityController {

    @Autowired
    private SeckillActivityService seckillActivityService;

    /**
     * 新增秒杀活动
     */
    @PostMapping
    public Result<String> save(@RequestBody SeckillActivity seckillActivity) {
        seckillActivityService.save(seckillActivity);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<SeckillActivity>> page(int page, int pageSize, String name) {
        // 调用Service层的分页查询方法
        PageResult<SeckillActivity> pageResult = seckillActivityService.pageActivities(page, pageSize, name);
        return Result.success(pageResult);
    }

    /**
     * 删除秒杀活动
     */
    @DeleteMapping
    public Result<String> delete(@RequestParam Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        boolean flag = seckillActivityService.removeById(id);
        if (!flag) {
            return Result.error("删除失败");
        }
        return Result.success();
    }

    /**
     * 修改秒杀活动
     */
    @PutMapping
    public Result<String> update(@RequestBody SeckillActivity seckillActivity) {
        if (seckillActivity.getId() == null) {
            return Result.error("id不能为空");
        }
        boolean flag = seckillActivityService.update(seckillActivity);
        if (!flag) {
            return Result.error("修改失败");
        }
        return Result.success();
    }

    /**
     * 根据id查询秒杀活动
     */
    @GetMapping("/{id}")
    public Result<SeckillActivity> getById(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        SeckillActivity seckillActivity = seckillActivityService.getById(id);
        if (seckillActivity == null) {
            return Result.error("秒杀活动不存在");
        }
        return Result.success(seckillActivity);
    }
}
