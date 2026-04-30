# 管理端商品管理接口文档

## 1. 商品列表查询（分页）

- **接口路径**：`POST /admin/product/page`
- **请求方式**：`POST`
- **Content-Type**：`application/json`
- **请求参数**（JSON body）：

| 参数名        | 类型     | 必填  | 说明                           |
| ---------- | ------ | --- | ---------------------------- |
| page       | int    | 是   | 当前页码（默认1）                    |
| pageSize   | int    | 是   | 每页条数（默认10）                    |
| categoryId | Long   | 否   | 分类ID                         |
| keyword    | String | 否   | 搜索关键词                        |
| tag        | String | 否   | 商品标签                         |
| sortBy     | String | 否   | 排序字段（price_asc/price_desc/sales/default） |
| isSale     | Boolean | 否   | 是否起售（管理端无需传递，默认查询所有状态）       |

- **请求示例**：
  
  ```json
  {
    "page": 1,
    "pageSize": 10,
    "categoryId": null,
    "keyword": "",
    "tag": "",
    "sortBy": "default"
  }
  ```

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

- **接口路径**：`GET /admin/product/getById`
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

## 3. 新增商品

- **接口路径**：`POST /admin/product`
- **请求方式**：`POST`
- **Content-Type**：`application/json`
- **请求参数**（JSON body）：

| 参数名        | 类型     | 必填  | 说明       |
| ---------- | ------ | --- | -------- |
| name       | String | 是   | 商品名称     |
| description | String | 否   | 商品描述     |
| price      | Double | 是   | 商品价格     |
| stock      | Integer | 是   | 商品库存     |
| image      | String | 是   | 商品图片URL  |
| categoryId | Long   | 是   | 分类ID     |
| tag        | String | 否   | 商品标签     |
| status     | Integer | 是   | 商品状态（1-起售，0-下架） |

- **请求示例**：
  
  ```json
  {
    "name": "潮流T恤",
    "description": "时尚百搭，舒适面料",
    "price": 99.00,
    "stock": 100,
    "image": "https://xxx.com/image.jpg",
    "categoryId": 1,
    "tag": "热销",
    "status": 1
  }
  ```

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": null
  }
  ```

---

## 4. 修改商品

- **接口路径**：`PUT /admin/product`
- **请求方式**：`PUT`
- **Content-Type**：`application/json`
- **请求参数**（JSON body）：

| 参数名        | 类型     | 必填  | 说明       |
| ---------- | ------ | --- | -------- |
| id         | Long   | 是   | 商品ID     |
| name       | String | 是   | 商品名称     |
| description | String | 否   | 商品描述     |
| price      | Double | 是   | 商品价格     |
| stock      | Integer | 是   | 商品库存     |
| image      | String | 是   | 商品图片URL  |
| categoryId | Long   | 是   | 分类ID     |
| tag        | String | 否   | 商品标签     |
| status     | Integer | 是   | 商品状态（1-起售，0-下架） |

- **请求示例**：
  
  ```json
  {
    "id": 1,
    "name": "潮流T恤（升级版）",
    "description": "时尚百搭，舒适面料，升级版",
    "price": 129.00,
    "stock": 150,
    "image": "https://xxx.com/new-image.jpg",
    "categoryId": 1,
    "tag": "热销",
    "status": 1
  }
  ```

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": null
  }
  ```

---

## 5. 删除商品

- **接口路径**：`DELETE /admin/product`
- **请求参数**：

| 参数名 | 类型   | 必填  | 说明   |
| --- | ---- | --- | ---- |
| id  | Long | 是   | 商品ID |

- **响应示例**：
  
  ```json
  {
  "code": 1,
  "msg": "success",
  "data": null
  }
  ```

---

## 6. 商品分类管理

- **接口路径**：`GET /admin/category/list`
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

## 对接说明

现有后端接口已实现：

- ✅ `POST /admin/product/page` - 分页查询商品列表（支持isSale参数，管理端无需传递）
- ✅ `GET /admin/product/getById` - 根据ID查询商品详情
- ✅ `POST /admin/product` - 新增商品
- ✅ `PUT /admin/product` - 修改商品
- ✅ `DELETE /admin/product` - 删除商品
- ✅ `GET /admin/category/list` - 获取分类列表

---

## 注意事项

1. **isSale参数处理**：
   - 管理端：无需传递isSale参数，默认查询所有状态的商品
   - 用户端：必须传递isSale=true，只查询已起售的商品

2. **缓存清理**：
   - 新增、修改、删除商品时，会自动清理商品列表缓存
   - 缓存键格式：`productPage:*`

3. **参数验证**：
   - 所有接口都有基本的参数验证
   - ID参数不能为空
   - 新增和修改商品时，必填字段必须提供