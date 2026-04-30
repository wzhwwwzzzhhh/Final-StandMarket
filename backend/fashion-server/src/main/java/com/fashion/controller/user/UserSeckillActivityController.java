package com.fashion.controller.user;

import com.fashion.entity.SeckillActivity;
import com.fashion.result.Result;
import com.fashion.service.SeckillActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端秒杀活动管理
 */
@RestController
@RequestMapping("/user/seckill/activity")
public class UserSeckillActivityController {

    @Autowired
    private SeckillActivityService seckillActivityService;

    /**
     * 获取秒杀活动列表
     */
    @GetMapping("/list")
    public Result<List<SeckillActivity>> list() {
        List<SeckillActivity> activities = seckillActivityService.listActivities(null);
        return Result.success(activities);
    }

    /**
     * 获取秒杀活动详情
     */
    @GetMapping("/{id}")
    public Result<SeckillActivity> detail(@PathVariable Long id) {
        if (id == null) {
            return Result.error("id不能为空");
        }
        SeckillActivity activity = seckillActivityService.getById(id);
        if (activity == null) {
            return Result.error("秒杀活动不存在");
        }
        return Result.success(activity);
    }
}