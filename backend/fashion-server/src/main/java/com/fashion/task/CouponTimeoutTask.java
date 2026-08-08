package com.fashion.task;

import com.fashion.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通用优惠券超时订单定时任务
 * 每 5 分钟扫描：绑定通用券且超时 30 分钟未支付的订单 → 自动取消并释放券
 */
@Component
public class CouponTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(CouponTimeoutTask.class);

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedRate = 300_000)
    public void autoCancelTimeoutCouponOrders() {
        try {
            orderService.autoCancelTimeoutCouponOrders();
        } catch (Exception e) {
            log.error("超时订单自动取消失败", e);
        }
    }
}
