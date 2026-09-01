package com.fashion.controller.user;

import com.fashion.dto.OrderAmountCalculateDTO;
import com.fashion.dto.UserCouponDto;
import com.fashion.entity.SeckillOrder;
import com.fashion.result.Result;
import com.fashion.service.SeckillOrderService;
import com.fashion.vo.OrderAmountVO;
import com.fashion.vo.SeckillOrderVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端秒杀订单管理
 */
@RestController
@RequestMapping("/user/seckill/order")
@Slf4j
public class UserSeckillOrderController {

    @Autowired
    private SeckillOrderService seckillOrderService;

    /**
     * 获取用户的秒杀订单列表
     */
    @GetMapping("/list")
    public Result<List<SeckillOrderVo>> list() {
        return seckillOrderService.listAll();
    }
    
    /**
     * 获取用户的秒杀券列表
     */
    @GetMapping("/coupons")
    public Result<List<UserCouponDto>> getCoupons(@RequestParam(required = false) Integer status) {
        try {
            List<UserCouponDto> coupons = seckillOrderService.getCurrentUserCoupons(status);
            return Result.success(coupons);
            
        } catch (Exception e) {
            log.error("获取秒杀券列表失败", e);
            return Result.error("获取秒杀券列表失败");
        }
    }

    /**
     * 获取秒杀订单详情
     */
    @GetMapping("/{orderNumber}")
    public Result<SeckillOrder> detail(@PathVariable String orderNumber) {
        try {
            if (orderNumber == null || orderNumber.trim().isEmpty()) {
                return Result.error("订单号不能为空");
            }
            
            SeckillOrder order = seckillOrderService.getCurrentUserOrderByNumber(orderNumber);
            if (order == null) {
                return Result.error("订单不存在");
            }
            
            return Result.success(order);
            
        } catch (Exception e) {
            log.error("获取秒杀订单详情失败，订单号：{}", orderNumber, e);
            return Result.error("获取订单详情失败");
        }
    }

    /**
     * 取消秒杀订单
     */
    @PostMapping("/cancel/{orderNumber}")
    public Result<String> cancelOrder(@PathVariable String orderNumber) {
        try {
            if (orderNumber == null || orderNumber.trim().isEmpty()) {
                return Result.error("订单号不能为空");
            }
            
            boolean success = seckillOrderService.cancelCurrentUserOrder(orderNumber);
            if (success) {
                return Result.success("取消订单成功");
            } else {
                return Result.error("取消订单失败");
            }
            
        } catch (Exception e) {
            log.error("取消秒杀订单失败，订单号：{}", orderNumber, e);
            return Result.error("取消订单失败");
        }
    }

    /**
     * 计算订单金额（根据选择的秒杀活动和秒杀券）
     */
    @PostMapping("/calculate")
    public Result<OrderAmountVO> calculateOrderAmount(@RequestBody OrderAmountCalculateDTO calculateDTO) {
        try {
            if (calculateDTO.getTotalAmount() == null) {
                return Result.error("商品总金额不能为空");
            }
            
            log.info("计算订单金额请求：总金额={}, 活动ID={}, 券ID={}",
                    calculateDTO.getTotalAmount(), calculateDTO.getActivityId(), calculateDTO.getCouponId());
            
            return seckillOrderService.calculateOrderAmount(calculateDTO);
            
        } catch (Exception e) {
            log.error("计算订单金额接口异常", e);
            return Result.error("计算失败：" + e.getMessage());
        }
    }
}
