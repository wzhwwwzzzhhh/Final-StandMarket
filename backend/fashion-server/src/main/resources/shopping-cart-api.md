# 购物车接口文档

## 1. 接口概览

| 接口路径 | 方法 | 功能描述 |
|---------|------|----------|
| `/user/shopping-cart/add` | POST | 添加商品到购物车 |
| `/user/shopping-cart/list` | GET | 获取购物车列表 |
| `/user/shopping-cart/update` | PUT | 更新购物车商品数量 |
| `/user/shopping-cart/delete/{id}` | DELETE | 删除购物车商品 |
| `/user/shopping-cart/clear` | DELETE | 清空购物车 |
| `/user/shopping-cart/check-stock` | GET | 检查购物车商品库存 |
| `/user/shopping-cart/sync-prices` | POST | 同步购物车商品价格 |

## 2. 接口详情

### 2.1 添加商品到购物车

**接口路径**: `/user/shopping-cart/add`
**请求方法**: POST
**请求体**:
```json
{
  "productId": 1,
  "number": 2,
  "skuInfo": "颜色:红色,尺码:M"
}
```

**响应**:
```json
{
  "code": 1,
  "msg": "添加成功",
  "data": null
}
```

**错误响应**:
```json
{
  "code": 0,
  "msg": "商品不存在",
  "data": null
}
```

```json
{
  "code": 0,
  "msg": "商品库存不足",
  "data": null
}
```

### 2.2 获取购物车列表

**接口路径**: `/user/shopping-cart/list`
**请求方法**: GET
**响应**:
```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "商品名称",
      "image": "商品图片URL",
      "userId": 1,
      "productId": 1,
      "combinationId": null,
      "skuInfo": "颜色:红色,尺码:M",
      "number": 2,
      "amount": 200.00,
      "createTime": "2026-04-15T21:00:00"
    }
  ]
}
```

### 2.3 更新购物车商品数量

**接口路径**: `/user/shopping-cart/update`
**请求方法**: PUT
**请求体**:
```json
{
  "id": 1,
  "number": 3
}
```

**响应**:
```json
{
  "code": 1,
  "msg": "更新成功",
  "data": null
}
```

**错误响应**:
```json
{
  "code": 0,
  "msg": "商品库存不足",
  "data": null
}
```

### 2.4 删除购物车商品

**接口路径**: `/user/shopping-cart/delete/{id}`
**请求方法**: DELETE
**路径参数**:
- `id`: 购物车商品ID

**响应**:
```json
{
  "code": 1,
  "msg": "删除成功",
  "data": null
}
```

### 2.5 清空购物车

**接口路径**: `/user/shopping-cart/clear`
**请求方法**: DELETE
**响应**:
```json
{
  "code": 1,
  "msg": "清空成功",
  "data": null
}
```

### 2.6 检查购物车商品库存

**接口路径**: `/user/shopping-cart/check-stock`
**请求方法**: GET
**响应**:
```json
{
  "code": 1,
  "msg": "success",
  "data": [
    {
      "id": 1,
      "name": "商品名称",
      "image": "商品图片URL",
      "userId": 1,
      "productId": 1,
      "combinationId": null,
      "skuInfo": "颜色:红色,尺码:M",
      "number": 5,
      "amount": 500.00,
      "createTime": "2026-04-15T21:00:00"
    }
  ]
}
```

### 2.7 同步购物车商品价格

**接口路径**: `/user/shopping-cart/sync-prices`
**请求方法**: POST
**响应**:
```json
{
  "code": 1,
  "msg": "价格同步成功",
  "data": null
}
```

## 3. 业务逻辑说明

### 3.1 库存检查
- 添加商品到购物车时，会检查商品库存是否足够
- 更新购物车商品数量时，会检查商品库存是否足够
- 库存不足时会返回错误信息

### 3.2 价格同步
- 获取购物车列表时，会自动同步商品价格
- 可以通过 `/user/shopping-cart/sync-prices` 接口手动同步价格
- 价格同步会更新购物车中商品的金额

### 3.3 用户关联
- 购物车商品会关联到当前登录用户
- 如果未登录，默认使用用户ID为1

### 3.4 商品规格处理
- 相同商品ID但不同规格的商品会作为不同的购物车项处理
- 通过 `skuInfo` 字段区分商品规格

## 4. 错误处理

| 错误信息 | 原因 | 解决方案 |
|---------|------|----------|
| 商品不存在 | 商品ID不存在或已删除 | 检查商品ID是否正确 |
| 商品库存不足 | 商品库存小于请求数量 | 减少购买数量或选择其他商品 |

## 5. 注意事项

1. 购物车操作需要用户登录
2. 商品价格可能会随时间变化，建议在结算前调用价格同步接口
3. 库存信息可能会实时变化，建议在结算前检查库存
4. 商品规格信息需要按照约定格式传递，以便正确区分不同规格的商品
