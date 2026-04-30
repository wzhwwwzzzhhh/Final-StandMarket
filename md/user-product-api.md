# 用户端商品管理接口文档

## 1. 商品列表查询（分页）

- **接口路径**：`GET /user/product/page`
- **请求参数**：

| 参数名        | 类型     | 必填  | 说明                           |
| ---------- | ------ | --- | ---------------------------- |
| page       | int    | 是   | 当前页码                         |
| pageSize   | int    | 是   | 每页条数                         |
| categoryId | Long   | 否   | 分类ID                         |
| keyword    | String | 否   | 搜索关键词                        |
| tag        | String | 否   | 商品标签                         |
| sortBy     | String | 否   | 排序字段（price/sales/createTime） |

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": {
    "total": 100,
    "records": [
      {
        "id": 1,
        "name": "潮流T恤",
        "categoryId": 1,
        "price": 99.00,
        "image": "https://xxx.com/image.jpg",
        "description": "时尚百搭",
        "status": 1,
        "stock": 100,
        "sales": 50,
        "tag": "热销",
        "createTime": "2024-01-01 10:00:00"
      }
    ]
  }
  }
  ```

---

## 2. 商品详情查询

- **接口路径**：`GET /user/product/getById`
- **请求参数**：

| 参数名 | 类型   | 必填  | 说明   |
| --- | ---- | --- | ---- |
| id  | Long | 是   | 商品ID |

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": {
    "id": 1,
    "name": "潮流T恤",
    "categoryId": 1,
    "price": 99.00,
    "image": "https://xxx.com/image.jpg",
    "description": "时尚百搭，舒适面料",
    "status": 1,
    "stock": 100,
    "sales": 50,
    "tag": "热销",
    "createTime": "2024-01-01 10:00:00"
  }
  }
  ```

---

## 3. 商品分类列表

- **接口路径**：`GET /user/category/list`

- **请求参数**：无

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "上衣",
      "type": 1,
      "sort": 1,
      "status": 1
    },
    {
      "id": 2,
      "name": "裤子",
      "type": 1,
      "sort": 2,
      "status": 1
    }
  ]
  }
  ```

---

## 4. 分类下的商品列表（建议新增）

- **接口路径**：`GET /user/product/listByCategory`
- **请求参数**：

| 参数名        | 类型   | 必填  | 说明         |
| ---------- | ---- | --- | ---------- |
| categoryId | Long | 是   | 分类ID       |
| page       | int  | 否   | 页码（默认1）    |
| pageSize   | int  | 否   | 每页条数（默认10） |

---

## 5. 商品搜索（建议新增）

- **接口路径**：`GET /user/product/search`
- **请求参数**：

| 参数名      | 类型     | 必填  | 说明         |
| -------- | ------ | --- | ---------- |
| keyword  | String | 是   | 搜索关键词      |
| page     | int    | 否   | 页码（默认1）    |
| pageSize | int    | 否   | 每页条数（默认10） |

---

## 6. 热销商品推荐（建议新增）

- **接口路径**：`GET /user/product/hot`
- **请求参数**：

| 参数名   | 类型  | 必填  | 说明         |
| ----- | --- | --- | ---------- |
| limit | int | 否   | 返回数量（默认10） |

---

## 7. 新品上市（建议新增）

- **接口路径**：`GET /user/product/new`
- **请求参数**：

| 参数名   | 类型  | 必填  | 说明         |
| ----- | --- | --- | ---------- |
| limit | int | 否   | 返回数量（默认10） |

---

## 8. 商品评价列表（建议新增）

- **接口路径**：`GET /user/product/{id}/comments`
- **请求参数**：

| 参数名      | 类型   | 必填  | 说明         |
| -------- | ---- | --- | ---------- |
| id       | Long | 是   | 商品ID（路径参数） |
| page     | int  | 否   | 页码（默认1）    |
| pageSize | int  | 否   | 每页条数（默认10） |

---

## 对接说明

现有后端接口已实现：

- ✅ `/user/product/page` - 分页查询商品列表
- ✅ `/user/product/getById` - 根据ID查询商品详情
- ✅ `/user/category/list` - 获取分类列表

建议新增接口：

- ❌ `/user/product/listByCategory` - 分类商品列表
- ❌ `/user/product/search` - 商品搜索
- ❌ `/user/product/hot` - 热销商品
- ❌ `/user/product/new` - 新品上市
- ❌ `/user/product/{id}/comments` - 商品评价











## Redis数据结构选择指南（按场景）

根据你的项目需求，我整理了每个场景下最合适的Redis数据结构及其理由：

| 业务场景           | 推荐数据结构                          | 示例Key                                  | 理由                          |
| -------------- | ------------------------------- | -------------------------------------- | --------------------------- |
| **普通商品详情缓存**   | String                          | `product:detail:{id}`                  | 简单KV，存储JSON字符串，支持TTL        |
| **普通商品列表缓存**   | String（JSON数组）                  | `product:list:page:{page}:size:{size}` | 列表分页结果整体缓存，避免多次查询           |
| **商品分类/菜单**    | Hash                            | `product:category:{id}`                | 存储分类的多个字段（名称、排序、状态等）        |
| **特价商品信息**     | String                          | `seckill:product:{id}`                 | 活动商品信息（价格、库存、起止时间）          |
| **防击穿互斥锁**     | String（SET NX）                  | `lock:product:{id}`                    | 用Redisson或SET NX实现，带过期时间    |
| **防穿透空值缓存**    | String                          | `product:null:{id}`                    | 缓存空对象，TTL较短（如60秒）           |
| **布隆过滤器**      | 布隆过滤器模块                         | `bloom:product`                        | 过滤无效商品ID，需引入RedisBloom插件    |
| **秒杀库存**       | String（数值）                      | `seckill:stock:{id}`                   | 用INCR/DECR原子操作，Lua脚本扣减      |
| **用户抢购记录**     | Set                             | `seckill:users:{id}`                   | 记录已抢购用户ID，SISMEMBER防重复      |
| **限流计数器**      | String（带TTL）                    | `rate:limit:user:{id}:action`          | INCR + EXPIRE，实现固定窗口限流      |
| **购物车**        | Hash                            | `cart:user:{userId}:shop:{shopId}`     | 字段为商品ID，值为数量；支持HINCRBY      |
| **用户签到**       | BitMap                          | `sign:user:{userId}:{year}{month}`     | 位图存储签到状态，内存占用极小             |
| **商品销量榜**      | ZSet                            | `rank:sales:week`                      | member为商品ID，score为销量，支持实时更新 |
| **商品人气榜（浏览量）** | ZSet                            | `rank:views:month`                     | 浏览量作为score，支持TOP N查询        |
| **附近商家**       | GEO                             | `shop:geo`                             | 存储店铺经纬度，支持GEORADIUS按距离查询    |
| **UV统计**       | HyperLogLog                     | `uv:page:home:{date}`                  | 误差约0.81%，内存固定12KB，适合大流量     |
| **优惠券领取记录**    | Set                             | `coupon:users:{couponId}`              | 存储已领用户ID，防重复领取              |
| **用户会话Token**  | String                          | `token:{jti或userId}`                   | 存储用户信息JSON，设置TTL（如30分钟）     |
| **短信验证码**      | String                          | `sms:code:{phone}:{type}`              | 存储验证码，TTL 5分钟               |
| **订单号生成器**     | String（INCR）                    | `order:seq:{date}`                     | 原子自增，生成日期+序号订单号             |
| **订单超时监听**     | String + Keyspace Notifications | `order:timeout:{orderId}`              | SETEX设置过期时间，订阅过期事件          |
| **幂等性Token**   | String（SET NX）                  | `idempotent:token:{token}`             | 请求前生成，提交时删除，防重复提交           |