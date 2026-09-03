package com.fashion.seckill;

import com.fashion.entity.SeckillMessage;
import com.fashion.entity.SeckillOrder;
import com.fashion.entity.SeckillMessageLog;
import com.fashion.mapper.SeckillMessageLogMapper;
import com.fashion.mapper.SeckillOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class SeckillDuplicateOrderTransaction {
    private final SeckillOrderMapper orderMapper;
    private final SeckillMessageLogMapper messageMapper;

    public SeckillDuplicateOrderTransaction(SeckillOrderMapper orderMapper,
                                            SeckillMessageLogMapper messageMapper) {
        this.orderMapper = Objects.requireNonNull(orderMapper, "orderMapper");
        this.messageMapper = Objects.requireNonNull(messageMapper, "messageMapper");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resolve(SeckillMessage message, String sourceMessageId) {
        if (message == null || message.getOrderNumber() == null
                || !Objects.equals("SECKILL_ORDER_CREATE:" + message.getOrderNumber(), sourceMessageId)) {
            throw new IllegalArgumentException("invalid duplicate message identity");
        }
        SeckillOrder existing = orderMapper.selectByOrderNumberForUpdate(message.getOrderNumber());
        if (existing == null || !Objects.equals(existing.getUserId(), message.getUserId())
                || !Objects.equals(existing.getCouponId(), message.getCouponId())) {
            throw new IllegalStateException("order unique conflict is not an equivalent delivery");
        }
        SeckillMessageLog source = messageMapper.selectByMessageId(sourceMessageId);
        if (source == null || !"CONSUMED".equals(source.getStatus())
                || !Objects.equals(source.getBusinessKey(), message.getOrderNumber())
                || !Objects.equals(source.getUserId(), message.getUserId())
                || !Objects.equals(source.getCouponId(), message.getCouponId())) {
            throw new IllegalStateException("duplicate source is not an equivalent consumed delivery");
        }
    }
}
