package com.fashion.controller.admin;

import com.fashion.entity.SeckillCoupon;
import com.fashion.entity.PageResult;
import com.fashion.result.Result;
import com.fashion.service.SeckillCouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀券管理
 */
@RestController
@RequestMapping("/admin/seckill/coupon")
public class SeckillCouponController {

    @Autowired
    private SeckillCouponService seckillCouponService;

    /**
     * 新增秒杀券
     */
    @PostMapping
    public Result<String> save(@RequestBody SeckillCoupon seckillCoupon) {
        seckillCouponService.save(seckillCoupon);
        return Result.success();
    }

    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<PageResult<SeckillCoupon>> page(int page, int pageSize, String name) {
        // 调用Service层的分页查询方法
        PageResult<SeckillCoupon> pageResult = seckillCouponService.pageCoupons(page, pageSize, name);
        return Result.success(pageResult);
    }

    /**
     * 删除秒杀券
     */
    @DeleteMapping
    public Result<String> delete(@RequestParam Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        seckillCouponService.delete(id);
        return Result.success();
    }

    /**
     * 修改秒杀券
     */
    @PutMapping
    public Result<String> update(@RequestBody SeckillCoupon seckillCoupon) {
        if(seckillCoupon.getId() == null){
            return Result.error("id不能为空");
        }
        seckillCouponService.update(seckillCoupon);
        return Result.success();
    }

    /**
     * 根据id查询秒杀券
     */
    @GetMapping("/{id}")
    public Result<SeckillCoupon> getById(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        SeckillCoupon seckillCoupon = seckillCouponService.getById(id);
        if(seckillCoupon == null){
            return Result.error("秒杀券不存在");
        }
        return Result.success(seckillCoupon);
    }

    /**
     * 数据预热
     */
    /**
     * 单个数据预热
     */
    @PostMapping("/preheat/{id}")
    public Result<String> preheat(@PathVariable Long id) {
        if(id == null){
            return Result.error("id不能为空");
        }
        seckillCouponService.preheat(id);
        return Result.success();
    }

    /**
     * 批量数据预热
     */
    @PostMapping("/preheat/batch")
    public Result<String> preheatBatch(@RequestParam List<Long> ids) {
        if(ids == null || ids.size() == 0){
            return Result.error("ids不能为空");
        }
        seckillCouponService.preheatBatch(ids);

        return Result.success();
    }
}