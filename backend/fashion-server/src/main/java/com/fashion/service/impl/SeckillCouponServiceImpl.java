package com.fashion.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fashion.context.BaseContext;
import com.fashion.dto.SeckillSubmitResult;
import com.fashion.entity.*;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.result.Result;
import com.fashion.service.SeckillCouponService;
import com.fashion.seckill.SeckillSubmitOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀券服务实现类
 */
@Slf4j
@Service
public class SeckillCouponServiceImpl implements SeckillCouponService {

    @Autowired
    private SeckillCouponMapper seckillCouponMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private SeckillSubmitOrchestrator seckillSubmitOrchestrator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新增秒杀券
     * @param seckillCoupon 秒杀券信息
     */
    @Override
    public void save(SeckillCoupon seckillCoupon) {
        // 设置创建和更新时间
        seckillCoupon.setCreateTime(LocalDateTime.now());
        seckillCoupon.setUpdateTime(LocalDateTime.now());
        // 如果没有设置起止时间，设置默认值
        if (seckillCoupon.getStartTime() == null) {
            seckillCoupon.setStartTime(LocalDateTime.now());
        }
        if (seckillCoupon.getEndTime() == null) {
            seckillCoupon.setEndTime(LocalDateTime.now().plusDays(7));
        }
        // 保存秒杀券
        seckillCouponMapper.insert(seckillCoupon);
    }

    /**
     * 删除秒杀券
     * @param id 秒杀券id
     */
    @Override
    public void delete(Long id) {
        seckillCouponMapper.deleteById(id);
    }

    /**
     * 修改秒杀券
     * @param seckillCoupon 秒杀券信息
     */
    @Override
    public void update(SeckillCoupon seckillCoupon) {
        // 设置更新时间
        seckillCoupon.setUpdateTime(LocalDateTime.now());
        seckillCouponMapper.update(seckillCoupon);
    }

    /**
     * 根据id查询秒杀券
     * @param id 秒杀券id
     * @return
     */
    @Override
    public SeckillCoupon getById(Long id) {
        return seckillCouponMapper.selectById(id);
    }

    /**
     * 分页查询秒杀券
     * @param page 页码
     * @param pageSize 页大小
     * @param name 秒杀券名称
     * @return
     */
    @Override
    public PageResult<SeckillCoupon> pageCoupons(int page, int pageSize, String name) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;
        // 查询数据
        List<SeckillCoupon> coupons = seckillCouponMapper.list(offset, pageSize, name);
        // 查询总数
        int total = seckillCouponMapper.count(name);
        // 构建分页结果
        return new PageResult<>(total, coupons);
    }

    /**
     * 查询所有秒杀券
     * @return
     */
    @Override
    public List<SeckillCoupon> listCoupons() {
        return seckillCouponMapper.listCoupons();
    }

    /**
     * 抢购秒杀券（异步秒杀，基于RabbitMq）
     * @param couponId
     * @return
     */
    @Override
    @SentinelResource(value = "seckill", blockHandler = "seckillBlockHandler", fallback = "seckillFallback")
    public Result<SeckillSubmitResult> seckillCoupon(Long couponId) {
        //先获取分布锁
        Long userId = BaseContext.getUserId();
        String lockkey = "seckill:lock:" + userId;
        RLock lock = redissonClient.getLock(lockkey);
        if(!lock.tryLock()){
            return Result.error("请勿重复抢购");
        }
        try {
            long currentTime = System.currentTimeMillis() / 1000;
            SeckillSubmitOrchestrator.Submission submission =
                    seckillSubmitOrchestrator.submit(userId, couponId, currentTime);
            switch (submission.getOutcome()) {
                case PROCESSING:
                    return Result.success(new SeckillSubmitResult(submission.getOrderNumber(), 0,
                            "抢购请求已提交，请等待处理", couponId));
                case NOT_STARTED:
                    return Result.error("秒杀未开始");
                case ENDED:
                    return Result.error("秒杀已结束");
                case DUPLICATE:
                    return Result.error("请勿重复抢购");
                case SOLD_OUT:
                    return Result.error("秒杀券已售罄");
                case DELIVERY_FAILED:
                    return Result.error("消息投递失败，请稍后重试");
                default:
                    return Result.error("系统繁忙，请稍后再试");
            }
        }finally {
            lock.unlock();
        }
    }

    public Result<SeckillSubmitResult> seckillBlockHandler(Long couponId, BlockException exception) {
        log.warn("秒杀接口触发限流，couponId={}", couponId);
        return Result.error("系统繁忙，请稍后再试");
    }

    public Result<SeckillSubmitResult> seckillFallback(Long couponId, Throwable exception) {
        log.error("秒杀接口降级，couponId={}", couponId, exception);
        return Result.error("系统异常，请稍后重试");
    }

    @Override
    public Result<SeckillOrder> createSeckillOrder(Long couponId) {
        return null;
    }

    /**
     * 数据预热
     * @param id
     */
    @Override
    public void preheat(Long id) {
        //根据id获取秒杀卷信息，然后根据key，将数据预热到redis中
        SeckillCoupon coupon = seckillCouponMapper.selectById(id);
        if (coupon == null) {
            return;
        }
        //需要把库存信息、开始时间、结束时间预热到redis中
        String stockKey = "seckill:coupon:stock:" + coupon.getId();
        String startTimeKey = "seckill:coupon:startTime:" + coupon.getId();
        String endTimeKey = "seckill:coupon:endTime:" + coupon.getId();
        stringRedisTemplate.opsForValue().set(stockKey, coupon.getStock().toString());
        if (coupon.getStartTime() != null) {
            long startTime = coupon.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
            stringRedisTemplate.opsForValue().set(startTimeKey, String.valueOf(startTime));
        }
        if (coupon.getEndTime() != null) {
            long endTime = coupon.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond();
            stringRedisTemplate.opsForValue().set(endTimeKey, String.valueOf(endTime));
        }
    }

    @Override
    public void preheatBatch(List<Long> ids) {
        List<SeckillCoupon> coupons = ids.stream()
                .map(seckillCouponMapper::selectById)
                .filter(coupon -> coupon != null)
                .collect(Collectors.toList());
        stringRedisTemplate.executePipelined((RedisCallback<Object> )connection->{
            for (SeckillCoupon coupon : coupons) {
                //key
                String stockKey = "seckill:coupon:stock:" + coupon.getId();
                String startTimeKey = "seckill:coupon:startTime:" + coupon.getId();
                String endTimeKey = "seckill:coupon:endTime:" + coupon.getId();
                byte[] stockKeyBytes = stockKey.getBytes();
                byte[] startTimeKeyBytes = startTimeKey.getBytes();
                byte[] endTimeKeyBytes = endTimeKey.getBytes();

                connection.set(stockKeyBytes, coupon.getStock().toString().getBytes());
                if(coupon.getStartTime() != null){
                    long startTime = coupon.getStartTime().atZone(ZoneId.systemDefault()).toEpochSecond();
                    connection.set(startTimeKeyBytes, String.valueOf(startTime).getBytes());
                }
                if(coupon.getEndTime() != null){
                    long endTime = coupon.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond();
                    connection.set(endTimeKeyBytes, String.valueOf(endTime).getBytes());
                }
            }
            return null;
        });

    }

    /**
     * 抢购秒杀券
     * @param couponId 秒杀券id
     * @return
     *
     * 分布锁：Redisson形式
     */
    /*@Override
    public Result<SeckillOrder> seckillCoupon(Long couponId) {
        //用户id
        Long userId = BaseContext.getUserId();
        //获取秒杀券
        SeckillCoupon coupon = seckillCouponMapper.selectById(couponId);
        if (coupon == null) {
            return Result.error("秒杀券不存在");
        }
        if (coupon.getStatus() != 1) {
            return Result.error("秒杀券已下架");
        }
        if (coupon.getStartTime().isAfter(LocalDateTime.now())) {
            return Result.error("秒杀券未开始");
        }
        if (coupon.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.error("秒杀券已结束");
        }
        if (coupon.getStock() <= 0) {
            return Result.error("秒杀券已售罄");
        }
        //获取分布锁
        String lockkey = "seckill:lock:" + couponId;
        Boolean lock = redissonClient.getLock(lockkey).tryLock();
        if (!lock) {
            return Result.error(MessageConstant.SECKILL_ORDER_EXIST);
        }
        try {
            SeckillCouponService proxy = (SeckillCouponService) AopContext.currentProxy();
            return proxy.createSeckillOrder(couponId);
        }catch (Exception e){
            return Result.error("秒杀券抢购失败");
        }finally {
            redissonClient.getLock(lockkey).unlock();
        }

    }

    @Override
    public Result<SeckillOrder> createSeckillOrder(Long couponId) {
        Long userId = BaseContext.getUserId();
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setCouponId(couponId);
        Long orderId = uniqueID.nextId("order"+userId);
        order.setOrderNumber(orderId.toString());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        seckillOrderMapper.insert(order);
        return Result.success(order);
    }*/


}
