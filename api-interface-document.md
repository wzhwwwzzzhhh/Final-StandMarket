# 前后端API接口文档

## 简介

本文档列出了前端和后端的所有API接口，包括前端调用方式和后端接收方式，以便查看哪些参数需要优化。

## 1. 用户相关接口

| 接口路径 | 前端调用方式 | 后端接收方式 | 备注 |
|---------|-------------|-------------|------|
| `/user/login` | `api.post('/user/login', data)` | `@RequestBody UserLoginDto userLoginDto` | 前端传递包含phone、password、code、type的对象 |
| `/user/send-sms-code` | `api.post('/user/send-sms-code', phone)` | `@RequestBody String phone` | 已简化，前端直接传递phone字符串 |
| `/user/register` | `api.post('/user/register', data)` | `@RequestBody User user` | 前端传递包含phone、password、name等的对象 |
| `/user/info` | `api.get('/user/info')` | `@RequestHeader("Authorization") String token` | 从请求头获取token |
| `/user/update` | `api.put('/user/update', userInfo)` | `@RequestHeader("Authorization") String token, @RequestBody UserUpdateDTO userUpdateDTO` | 前端传递包含name、avatar、sex的对象 |
| `/user/change-password` | `api.post('/user/change-password', { oldPassword, newPassword })` | `@RequestHeader("Authorization") String token, @RequestBody ChangePasswordDTO changePasswordDTO` | 可以简化为直接接收两个字符串参数 |
| `/user/logout` | `api.post('/user/logout')` | `@RequestHeader("Authorization") String token` | 从请求头获取token |

## 2. 商品相关接口

| 接口路径 | 前端调用方式 | 后端接收方式 | 备注 |
|---------|-------------|-------------|------|
| `/user/product/page` | `api.get('/user/product/page', { params })` | `@RequestParam int page, @RequestParam int pageSize, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String sortBy, @RequestParam(required = false) String keyword, @RequestParam(required = false) String tag` | 前端传递包含page、pageSize等的params对象 |
| `/user/product/getById` | `api.get('/user/product/getById', { params: { id } })` | `@RequestParam Long id` | 前端传递id参数 |
| `/user/category/list` | `api.get('/user/category/list')` | 无参数 | 无 |

## 3. 购物车相关接口

| 接口路径 | 前端调用方式 | 后端接收方式 | 备注 |
|---------|-------------|-------------|------|
| `/user/shopping-cart/add` | `api.post('/user/shopping-cart/add', data)` | `@RequestBody ShoppingCartDTO shoppingCartDTO` | 前端传递包含productId、number、skuInfo的对象 |
| `/user/shopping-cart/list` | `api.get('/user/shopping-cart/list')` | 无参数 | 无 |
| `/user/shopping-cart/update` | `api.put('/user/shopping-cart/update', data)` | `@RequestBody ShoppingCartDTO shoppingCartDTO` | 前端传递包含productId、number的对象 |
| `/user/shopping-cart/delete/{id}` | `api.delete(`/user/shopping-cart/delete/${id}``) | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/shopping-cart/clear` | `api.delete('/user/shopping-cart/clear')` | 无参数 | 无 |
| `/user/shopping-cart/batch-delete` | `api.delete('/user/shopping-cart/batch-delete', { data: ids })` | `@RequestBody List<Long> ids` | 前端传递ids数组 |
| `/user/shopping-cart/checkout` | `api.post('/user/shopping-cart/checkout', ids)` | `@RequestBody List<Long> ids` | 前端传递ids数组 |
| `/user/shopping-cart/check-stock` | `api.get('/user/shopping-cart/check-stock')` | 无参数 | 无 |
| `/user/shopping-cart/sync-prices` | `api.post('/user/shopping-cart/sync-prices')` | 无参数 | 无 |

## 4. 订单相关接口

| 接口路径 | 前端调用方式 | 后端接收方式 | 备注 |
|---------|-------------|-------------|------|
| `/user/order/create` | `api.post('/user/order/create', data, config)` | `@RequestBody OrderCreateDTO orderCreateDTO` | 前端传递包含productIds的对象 |
| `/user/order/list` | `api.get('/user/order/list', { params: { status }, ...config })` | `@RequestParam(required = false) Integer status` | 前端传递status参数 |
| `/user/order/detail/{id}` | `api.get(`/user/order/detail/${id}`, config)` | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/order/cancel/{id}` | `api.put(`/user/order/cancel/${id}`, null, config)` | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/order/pay/{id}` | `api.put(`/user/order/pay/${id}`, null, config)` | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/order/confirm/{id}` | `api.put(`/user/order/confirm/${id}`, null, config)` | `@PathVariable Long id` | 前端传递id路径参数 |

## 5. 地址相关接口

| 接口路径 | 前端调用方式 | 后端接收方式 | 备注 |
|---------|-------------|-------------|------|
| `/user/address` | `api.post('/user/address', address, config)` | `@RequestBody AddressBookDTO addressBookDTO` | 前端传递包含consignee、phone、provinceCode等的对象 |
| `/user/address/{id}` | `api.delete(`/user/address/${id}`, config)` | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/address` | `api.put('/user/address', address, config)` | `@RequestBody AddressBookDTO addressBookDTO` | 前端传递包含id、consignee、phone等的对象 |
| `/user/address/{id}` | `api.get(`/user/address/${id}`, config)` | `@PathVariable Long id` | 前端传递id路径参数 |
| `/user/address/list` | `api.get('/user/address/list', config)` | 无参数 | 无 |
| `/user/address/default` | `api.get('/user/address/default', config)` | 无参数 | 无 |
| `/user/address/default/{id}` | `api.put(`/user/address/default/${id}`, null, config)` | `@PathVariable Long id` | 前端传递id路径参数 |

## 优化建议

1. **修改密码接口**：后端可以直接接收两个字符串参数（oldPassword, newPassword），而不是通过ChangePasswordDTO对象。

2. **更新用户信息接口**：后端可以直接接收各个字段作为参数，而不是通过UserUpdateDTO对象。

3. **添加/更新购物车商品接口**：后端可以直接接收productId, number, skuInfo等参数，而不是通过ShoppingCartDTO对象。

4. **创建订单接口**：后端可以直接接收productIds列表，而不是通过OrderCreateDTO对象。

5. **添加/更新地址接口**：后端可以直接接收各个地址字段作为参数，而不是通过AddressBookDTO对象。

6. **统一参数传递方式**：对于简单参数，前后端应保持一致的传递方式，避免不必要的对象包装。

7. **添加接口文档**：建议使用Swagger等工具生成接口文档，方便前后端开发人员参考。
