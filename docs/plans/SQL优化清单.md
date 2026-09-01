# SQL 优化清单

> 分析范围：12 个 Mapper XML，共 50+ 条 SQL
> 说明：已加索引的标注 ✅，仍需优化的标注 ⚠️

---

## 一、product 表（商品）

### ✅ 索引覆盖完好的 SQL（无需优化）

```sql
-- ProductMapper.listByCategoryId
-- 复合索引 idx_product_category(category_id, status, sales) 完美覆盖
select * from product
where category_id = ? and status = 1
order by sales desc

-- ProductMapper.listByTag
-- 复合索引 idx_product_tag(tag, status, sales) 完美覆盖
select * from product
where tag = ? and status = 1
order by sales desc

-- ProductMapper.getById（主键查询）
select * from product where id = ? and status = 1
```

### ⚠️ 需要优化的 SQL

```sql
-- 【问题1】ProductMapper.selectByCondition —— order by 不在索引中
select * from product
<where>
    and name like concat('%', #{name}, '%')   -- 模糊匹配全表扫描
    and category_id = #{categoryId}            -- 可走复合索引
    and status = #{status}                      -- 可走复合索引
</where>
order by create_time desc   -- ⚠️ create_time 不在任何索引中 → filesort
```

**优化建议**：`create_time` 高频排序，建议加单列索引

```sql
ALTER TABLE product ADD INDEX idx_product_create_time (create_time DESC);
```

```sql
-- 【问题2】ProductMapper.listProductsByCondition —— order by 多方向
<choose>
    <when test="sortBy == 'price_asc'">order by price asc, id desc</when>
    <when test="sortBy == 'price_desc'">order by price desc, id desc</when>
    <when test="sortBy == 'sales'">order by sales desc, id desc</when>
    <otherwise>order by create_time desc, id desc</otherwise>
</choose>
```

**优化建议**：排序字段很多且是动态的，MySQL 无法走索引排序。方案有两个：

- 方案A：对高频排序加索引 `idx_product_sales`(status, sales DESC)
- 方案B：数据量不大时接受 filesort，加各排序字段的索引改善（成本较高）

```sql
-- 【问题3】ProductMapper.listTopSales
select * from product where status = 1 order by sales desc limit 10
```

**优化建议**：`sales` 没有单独索引，虽然 `idx_product_tag` 和 `idx_product_category` 都包含 sales 但它们是复合索引第三列

```sql
ALTER TABLE product ADD INDEX idx_product_sales (status, sales DESC);
```

---

## 二、orders 表（订单）

### ✅ 索引覆盖完好的 SQL

```sql
-- OrderMapper.getById（主键查询）
select * from orders where id = ?

-- OrderMapper.selectByCondition（当只查 user_id 时）
-- 可走 idx_orders_user_time(user_id, order_time DESC)

-- OrderMapper.listUserOrders（user_id + status）
-- 可走 idx_orders_user_time(user_id, order_time DESC) 的 user_id 前缀
select * from orders
where user_id = ? and status = ?
order by order_time desc
```

### ⚠️ 需要优化的 SQL

```sql
-- 【问题4】OrderMapper.listPaidOrders —— pay_status 无索引！
select * from orders where pay_status = 1 order by order_time desc
```

**优化建议**：`pay_status` 没有索引

```sql
ALTER TABLE orders ADD INDEX idx_orders_pay_status (pay_status, order_time DESC);
```

```sql
-- 【问题5】OrderMapper.selectByCondition —— number 模糊查询
select * from orders
<where>
    and number like concat('%', #{number}, '%')  -- ⚠️ 前缀模糊，走不了唯一索引
</where>
order by order_time desc
```

**优化建议**：管理端搜索用精确匹配，或加一个专门的时间索引：

```sql
ALTER TABLE orders ADD INDEX idx_orders_order_time (order_time DESC);
```

---

## 三、seckill_order 表（秒杀订单）

### ✅ 索引覆盖完好的 SQL

```sql
-- 主键、订单号、用户ID 都已有索引 ✅
selectByUserId       → idx_seckill_order_user(user_id, create_time DESC) ✅
selectByOrderNumber   → idx_seckill_order_number(唯一索引) ✅
selectByUserIdAndCouponId 已移除；B5 以 uk_seckill_order_active_user_coupon 约束活动订单唯一性 ✅
```

### ⚠️ 需要优化的 SQL

```sql
-- 【问题6】SeckillOrderMapper.getTotalSeckillSalesAmount —— status 无索引
select sum(sc.seckill_price) 
from seckill_order so
join seckill_coupon sc on so.coupon_id = sc.id
where so.status = 2
```

**优化建议**：`seckill_order.status` 没有索引

```sql
ALTER TABLE seckill_order ADD INDEX idx_sorder_status (status);
```

```sql
-- 【问题7】SeckillOrderMapper.getTodaySeckillSalesAmount —— date()函数包裹导致索引失效
select sum(sc.seckill_price) 
from seckill_order so
join seckill_coupon sc on so.coupon_id = sc.id
where so.status = 2 and date(so.pay_time) = curdate()
--                          ↑ date() 函数包裹，pay_time 索引失效
```

**优化建议**：改为范围查询

```sql
-- 原始（索引失效）：
date(so.pay_time) = curdate()

-- 优化后（索引生效）：
so.pay_time >= curdate() and so.pay_time < curdate() + interval 1 day
```

```sql
-- 【问题8】SeckillOrderMapper.selectSeckillOrderPage —— 复杂查询
-- 管理端分页：JOIN 两表 + 多条件 + 模糊搜索
select ...
from seckill_order so
left join seckill_coupon sc on so.coupon_id = sc.id
<where>
    and (so.order_number like concat('%', #{search}, '%')  -- 模糊匹配
          or so.user_id like concat('%', #{search}, '%'))
    and so.status = #{status}
    and so.create_time >= #{startTime}
</where>
order by so.create_time desc
limit #{offset}, #{pageSize}
```

**优化建议**：

- `order_number` 模糊搜索改精确匹配
  
  ```sql
  ALTER TABLE seckill_order ADD INDEX idx_sorder_create_time (create_time DESC);
  ALTER TABLE seckill_order ADD INDEX idx_sorder_status_create (status, create_time DESC);
  ```

---

## 四、seckill_coupon 表（秒杀券）

### ✅ 索引覆盖完好的 SQL（已加索引）

```sql
-- listCoupons 已有 idx_seckill_coupon_active(status, start_time, end_time) ✅
```

### ⚠️ 需要优化的 SQL

```sql
-- 【问题9】SeckillCouponMapper.listCoupons —— 范围查询冲突
select * from seckill_coupon
where status = 1
  and start_time <= now()    -- 范围查询1
  and end_time >= now()      -- 范围查询2
order by start_time desc
```

**分析**：MySQL 复合索引中只能使用**一个**范围查询。`idx_seckill_coupon_active(status, start_time, end_time)`，`status` 等值匹配后，`start_time` 范围查询已用掉范围能力，`end_time` 无法再用索引过滤。

**优化建议**：调整索引顺序，把最常用的 `start_time` 放前面

```sql
-- 保留原来的，加一个新索引
ALTER TABLE seckill_coupon ADD INDEX idx_sc_active_new (status, start_time DESC, end_time);
```

---

## 五、seckill_activity 表（秒杀活动）

### ⚠️ 需要优化的 SQL（整张表缺少索引）

| SQL                 | 位置                    | 说明                        |
| ------------------- | --------------------- | ------------------------- |
| `selectByCondition` | SeckillActivityMapper | 按 name、status、时间查，**无索引** |
| `listActivities`    | SeckillActivityMapper | 按 name 模糊查，**无索引**        |
| `getById`           | SeckillActivityMapper | 主键查询，✅                    |

**优化建议**：

```sql
ALTER TABLE seckill_activity ADD INDEX idx_sactivity_status_time (status, start_time, end_time);
```

---

## 六、shopping_cart 表（购物车）

### ✅ 索引覆盖完好的 SQL

```sql
-- findByUserId → idx_cart_user(user_id) ✅
-- deleteByUserId → idx_cart_user(user_id) ✅
```

### ⚠️ 需要优化的 SQL

```sql
-- 【问题10】ShoppingCartMapper.findByUserIdAndProductIdAndSkuInfo
select * from shopping_cart 
where user_id = ? and product_id = ? and sku_info = ?
```

**优化建议**：虽然有 `idx_cart_user(user_id)`，但复合查询多列，建议加复合索引

```sql
ALTER TABLE shopping_cart ADD INDEX idx_cart_user_product (user_id, product_id, sku_info);
```

---

## 七、address_book 表（地址）

### ✅ 索引覆盖完好的 SQL

```sql
-- listByUserId → idx_address_user(user_id) ✅
-- getDefaultByUserId → idx_address_user(user_id) ✅
```

无需优化。

---

## 八、优化汇总

### 需要添加的索引（共 8 条）

```sql
-- 1. product 表 - create_time 排序
ALTER TABLE product ADD INDEX idx_product_create_time (create_time DESC);

-- 2. product 表 - status + sales 排行
ALTER TABLE product ADD INDEX idx_product_sales (status, sales DESC);

-- 3. orders 表 - pay_status 查询
ALTER TABLE orders ADD INDEX idx_orders_pay_status (pay_status, order_time DESC);

-- 4. orders 表 - order_time 排序
ALTER TABLE orders ADD INDEX idx_orders_order_time (order_time DESC);

-- 5. seckill_order 表 - status 查询
ALTER TABLE seckill_order ADD INDEX idx_sorder_status (status);

-- 6. seckill_order 表 - create_time 排序
ALTER TABLE seckill_order ADD INDEX idx_sorder_create_time (create_time DESC);
ALTER TABLE seckill_order ADD INDEX idx_sorder_status_create (status, create_time DESC);

-- 7. seckill_coupon 表 - 活动查询优化
ALTER TABLE seckill_coupon ADD INDEX idx_sc_active_new (status, start_time DESC, end_time);

-- 8. seckill_activity 表 - 活动查询
ALTER TABLE seckill_activity ADD INDEX idx_sactivity_status_time (status, start_time, end_time);

-- 9. shopping_cart 表 - 复合查询
ALTER TABLE shopping_cart ADD INDEX idx_cart_user_product (user_id, product_id, sku_info);
```

### 需要修改的 SQL（共 2 条）

```sql
-- 【改1】SeckillOrderMapper.getTodaySeckillSalesAmount
-- 改前：date(so.pay_time) = curdate()    -- 函数包裹，索引失效 ❌
-- 改后：so.pay_time >= curdate() and so.pay_time < curdate() + interval 1 day  ✅

-- 【改2】OrderMapper.listPaidOrders 
-- 改后可走 idx_orders_pay_status 新索引 ✅
```

---

## 九、执行顺序建议

| 步骤  | 操作                                     | 耗时   | 优先级         |
|:---:| -------------------------------------- |:----:|:-----------:|
| 1   | 执行 9 条 `ALTER TABLE` 加索引               | 5分钟  | 🔴 高        |
| 2   | 修改 `SeckillOrderMapper` 中的 `date()` 函数 | 5分钟  | 🔴 高（能命中索引） |
| 3   | 用 EXPLAIN 验证修改效果                       | 10分钟 | 🟡 中        |
