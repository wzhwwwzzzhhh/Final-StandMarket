package com.fashion.task;

import com.fashion.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每 5 分钟分批扫描所有超时待支付普通订单，并逐单进入独立取消事务。
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedRate = 300_000)
    public void autoCancelTimeoutOrders() {
        try {
            orderService.autoCancelTimeoutOrders();
        } catch (Exception e) {
            log.error("超时订单批量扫描失败", e);
        }
    }
}
