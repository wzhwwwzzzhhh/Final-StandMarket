package com.fashion.seckill;

import com.fashion.entity.SeckillMessage;
import com.fashion.entity.SeckillOrder;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("B6 并发订单唯一冲突分类事务")
class B6DuplicateOrderTransactionTest {
    @Test
    @DisplayName("唯一冲突后新事务锁读到相同身份则按等价重复消费")
    void equivalentIdentityIsConsumed() {
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillMessageLogMapper messageMapper = mock(SeckillMessageLogMapper.class);
        SeckillOrder existing = order("9001", 7L, 19L);
        when(orderMapper.selectByOrderNumberForUpdate("9001")).thenReturn(existing);
        SeckillMessageLog source = new SeckillMessageLog();
        source.setStatus("CONSUMED");
        source.setBusinessKey("9001");
        source.setUserId(7L);
        source.setCouponId(19L);
        when(messageMapper.selectByMessageId("SECKILL_ORDER_CREATE:9001")).thenReturn(source);

        new SeckillDuplicateOrderTransaction(orderMapper, messageMapper)
                .resolve(message("9001", 7L, 19L), "SECKILL_ORDER_CREATE:9001");

        verify(messageMapper).selectByMessageId("SECKILL_ORDER_CREATE:9001");
    }

    @Test
    @DisplayName("唯一冲突对应不同身份时不得吞掉数据冲突")
    void conflictingIdentityIsRejected() {
        SeckillOrderMapper orderMapper = mock(SeckillOrderMapper.class);
        SeckillMessageLogMapper messageMapper = mock(SeckillMessageLogMapper.class);
        when(orderMapper.selectByOrderNumberForUpdate("9001"))
                .thenReturn(order("9001", 8L, 19L));

        assertThrows(IllegalStateException.class,
                () -> new SeckillDuplicateOrderTransaction(orderMapper, messageMapper)
                        .resolve(message("9001", 7L, 19L), "SECKILL_ORDER_CREATE:9001"));
    }

    private SeckillMessage message(String orderNumber, Long userId, Long couponId) {
        SeckillMessage message = new SeckillMessage();
        message.setOrderNumber(orderNumber);
        message.setUserId(userId);
        message.setCouponId(couponId);
        return message;
    }

    private SeckillOrder order(String orderNumber, Long userId, Long couponId) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNumber(orderNumber);
        order.setUserId(userId);
        order.setCouponId(couponId);
        return order;
    }
}
