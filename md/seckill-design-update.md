# 秒杀券和秒杀活动独立设计文档

## 设计变更说明

### 变更原因
根据业务需求，秒杀活动和秒杀券需要独立存在，在订单结算时可以叠加使用。这样可以提供更灵活的优惠策略，让用户同时享受活动优惠和券优惠。

### 变更内容

#### 1. 数据库表结构
**秒杀券表 (seckill_coupon)**
- 移除了与秒杀活动的关联字段 `activity_id`
- 秒杀券现在独立存在，不与任何活动绑定

**秒杀活动表 (seckill_activity)**
- 保持原有结构不变
- 活动独立管理秒杀商品

**订单表 (orders) 和订单明细表 (order_detail)**
- 保留了秒杀相关字段：`seckill_activity_id`, `seckill_coupon_id`, `is_seckill`, `seckill_price`, `original_price`
- 支持同时记录活动优惠和券优惠

#### 2. 后端实体类
**SeckillCoupon 实体类**
```java
@Data
public class SeckillCoupon {
    private Long id;
    private String name;
    private Double originalPrice;
    private Double seckillPrice;
    private Integer stock;
    private Integer limitPerUser;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

**关键变化**：移除了 `activityId` 字段

#### 3. 前端页面
**秒杀券管理页面 (SeckillCoupon.vue)**
- 移除了活动ID的显示列
- 移除了活动选择的下拉框
- 简化了表单验证逻辑
- 移除了活动相关的数据加载

#### 4. API接口
**管理端API** (`/admin/seckill/coupon`)
- `POST /` - 新增秒杀券（无需关联活动）
- `GET /page` - 分页查询秒杀券
- `DELETE /` - 删除秒杀券
- `PUT /` - 修改秒杀券
- `GET /getById` - 根据ID查询秒杀券

**用户端API** (`/user/seckill/coupon`)
- `GET /list` - 获取秒杀券列表
- `GET /detail/{id}` - 获取秒杀券详情

## 叠加使用逻辑设计

### 订单结算流程
1. **用户选择商品**：用户选择普通商品或秒杀商品
2. **选择秒杀券**：用户可以选择适用的秒杀券
3. **计算优惠**：
   - 如果商品参与秒杀活动，先应用活动优惠价
   - 再叠加秒杀券的优惠
   - 最终价格 = 活动优惠价 - 秒杀券优惠

### 数据结构示例
```json
{
  "order": {
    "id": 1,
    "totalAmount": 200.00,
    "seckillActivityId": 2,      // 参与的活动ID
    "seckillCouponId": 1,        // 使用的秒杀券ID
    "isSeckill": true,
    "originalPrice": 300.00,     // 商品原价
    "seckillPrice": 200.00,      // 活动优惠价
    "finalPrice": 150.00         // 叠加券后最终价
  }
}
```

## 业务规则

### 1. 优惠叠加规则
- 秒杀活动优惠和秒杀券优惠可以叠加使用
- 先计算活动优惠，再计算券优惠
- 最终价格不能低于0

### 2. 使用限制
- 每个秒杀券有独立的库存限制
- 每个用户对每个秒杀券有购买限制
- 秒杀券有独立的时间有效性
- 秒杀活动有独立的时间有效性

### 3. 状态管理
- 秒杀券状态：0-无效，1-有效
- 秒杀活动状态：0-未开始，1-进行中，2-已结束
- 订单秒杀状态：0-普通订单，1-秒杀订单

## 技术实现要点

### 1. 数据库设计
- 秒杀券和秒杀活动独立存储
- 订单表同时记录活动和券信息
- 支持灵活的优惠组合

### 2. 业务逻辑
- 独立的券管理和活动管理
- 叠加优惠计算逻辑
- 库存和限购验证

### 3. 前端交互
- 独立的券选择界面
- 清晰的优惠展示
- 实时价格计算

## 优势

1. **灵活性**：券和活动可以独立配置，满足不同营销需求
2. **叠加优惠**：用户可以获得更多优惠，提升购买意愿
3. **易于管理**：券和活动分开管理，职责清晰
4. **扩展性**：支持未来增加更多优惠类型

## 后续开发建议

1. **完善订单结算逻辑**：实现叠加优惠的具体计算
2. **增加优惠验证**：确保优惠组合的合理性
3. **优化性能**：考虑使用缓存提升秒杀性能
4. **增加监控**：监控秒杀活动的库存和性能指标

---

**文档版本**：v1.0  
**更新日期**：2026-04-20  
**更新说明**：根据业务需求调整秒杀券和活动为独立设计，支持优惠叠加