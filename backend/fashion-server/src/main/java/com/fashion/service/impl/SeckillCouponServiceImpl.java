package com.fashion.service.impl;
import com.fashion.config.DirectExchangeConfig;
import com.fashion.context.BaseContext;
import com.fashion.dto.SeckillSubmitResult;
import com.fashion.entity.*;
import com.fashion.mapper.SeckillCouponMapper;
import com.fashion.mapper.SeckillOrderMapper;
import com.fashion.result.Result;
import com.fashion.service.SeckillCouponService;
import com.fashion.utils.UniqueID;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀券服务实现类
 */
@Service
public class SeckillCouponServiceImpl implements SeckillCouponService {

    @Autowired
    private SeckillCouponMapper seckillCouponMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private UniqueID uniqueID;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private SeckillOrderMapper seckillOrderMapper;
    @Autowired
    private DirectExchangeConfig directExchangeConfig;



    private DefaultRedisScript<Long> seckillScript;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ConnectionFactory connectionFactory;

    @PostConstruct
    public void init() {
        seckillScript = new DefaultRedisScript<>();
        seckillScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill.lua")));
        seckillScript.setResultType(Long.class);
    }

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
    public Result<SeckillSubmitResult> seckillCoupon(Long couponId) {
        //先获取分布锁
        Long userId = BaseContext.getUserId();
        String lockkey = "seckill:lock:" + userId;
        RLock lock = redissonClient.getLock(lockkey);
        if(!lock.tryLock()){
            return Result.error("请勿重复抢购");
        }
        SeckillSubmitResult resultDto = null;


        try {
            //通过Lua脚本操作redis，即查看库存是否充足，是否过期，是否存在
            String stockKey = "seckill:coupon:stock:" + couponId;
            String startTimeKey = "seckill:coupon:startTime:" + couponId;
            String endTimeKey = "seckill:coupon:endTime:" + couponId;
            String usersKey = "seckill:coupon:users:" + couponId;
            long currentTime = System.currentTimeMillis() / 1000;
            Long result = stringRedisTemplate.execute(
                    seckillScript,
                    Arrays.asList(stockKey, startTimeKey, endTimeKey, usersKey),
                    "1",String.valueOf(currentTime),userId.toString()
            );


            //判断是否开始秒杀
            if(result != null && result == -3){
                return Result.error("秒杀未开始");
            }
            //判断是否结束秒杀
            if(result != null && result == -2){
                return Result.error("秒杀已结束");
            }
            //判断是否重复购买
            if(result != null && result == -4){
                return Result.error("请勿重复抢购");
            }
            //判断库存
            if(result != null && result < 0){
                return Result.error("秒杀券已售罄");
            }

            //生成订单号
            Long orderNumber = uniqueID.nextId("seckill:order");

            //生产者发送消息
            SeckillMessage message = new SeckillMessage();
            message.setUserId(userId);
            message.setCouponId(couponId);
            message.setOrderNumber(orderNumber.toString());

            rabbitTemplate.convertAndSend(DirectExchangeConfig.SeckillExchange, DirectExchangeConfig.SeckillQueue,message);

            // 创建简化响应对象
            resultDto = new SeckillSubmitResult();
            resultDto.setOrderNumber(orderNumber.toString());
            resultDto.setStatus(0); // 处理中
            resultDto.setMessage("抢购请求已提交，请等待处理");
            resultDto.setCouponId(couponId);
        } catch (AmqpException e) {
            throw new RuntimeException(e);
        }finally {
            lock.unlock();
        }
        return Result.success(resultDto);
    }

    @RabbitListener(queues = DirectExchangeConfig.SeckillQueue)
    @Transactional
    public void handleSeckillOrder(SeckillMessage message) {
        try {
            //先判断消息是否为空
            if (message == null) {
                return;
            }

            //二次检验，避免重复
            SeckillOrder existOrder = seckillOrderMapper.selectByOrderNumber(message.getOrderNumber());
            if (existOrder != null) {
                return ;
            }
            //生成秒杀订单（用户与券的关系记录）
            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setUserId(message.getUserId());
            seckillOrder.setCouponId(message.getCouponId());
            seckillOrder.setOrderNumber(message.getOrderNumber());
            seckillOrder.setStatus(1);
            seckillOrder.setCreateTime(LocalDateTime.now());
            seckillOrderMapper.insert(seckillOrder);
            //发送订单消息到延迟队列
            rabbitTemplate.convertAndSend(DirectExchangeConfig.delayExchange, DirectExchangeConfig.delayRoutingKey, seckillOrder.getId());

            //秒杀卷库存减1，用数据库锁解决
            seckillCouponMapper.reduceStock(message.getCouponId());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RabbitListener(queues = DirectExchangeConfig.deadQueue)
    public void handleDeadQueue(Long orderId) {
        //获取秒杀订单id
        SeckillOrder seckillOrder = seckillOrderMapper.selectById(orderId);
        if (seckillOrder == null) {
            return;
        }
        //先根据秒杀订单id检查支付状态
        if (seckillOrder.getStatus() != 1) {
            return;
        }
        //如果未支付，则取消订单（CAS实现数据库乐观锁）
        boolean success = seckillOrderMapper.updateStatus(seckillOrder.getOrderNumber(), 3);
        if (!success) {
            return ;
        }
        //还原库存
        seckillCouponMapper.addStock(seckillOrder.getCouponId());
        //redis更新
        stringRedisTemplate.opsForValue().decrement("seckill:coupon:stock:" + seckillOrder.getCouponId());
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
        stringRedisTemplate.opsForValue().set(startTimeKey, coupon.getStartTime().toString());
        stringRedisTemplate.opsForValue().set(endTimeKey, coupon.getEndTime().toString());
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