# 管理端API接口文档

## 1. 商品管理

### 1.1 获取商品列表

- **请求路径**: `/admin/product/page`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名      | 类型      | 必填  | 描述   | 参数格式 |
  |:-------- |:------- |:--- |:---- |:---- |
  | page     | Integer | 是   | 当前页码 | 查询参数 |
  | pageSize | Integer | 是   | 每页数量 | 查询参数 |
  | name     | String  | 否   | 商品名称 | 查询参数 |

- **响应参数**:
  
  | 参数名                     | 类型            | 描述            |
  |:----------------------- |:------------- |:------------- |
  | code                    | Integer       | 状态码(1成功，0失败)  |
  | msg                     | String        | 提示信息          |
  | data                    | Object        | 响应数据          |
  | data.total              | Integer       | 总记录数          |
  | data.list               | Array         | 商品列表          |
  | data.list[].id          | Long          | 商品ID          |
  | data.list[].name        | String        | 商品名称          |
  | data.list[].categoryId  | Long          | 分类ID          |
  | data.list[].price       | BigDecimal    | 商品价格          |
  | data.list[].image       | String        | 商品图片          |
  | data.list[].description | String        | 商品描述          |
  | data.list[].status      | Integer       | 商品状态(0禁用，1启用) |
  | data.list[].stock       | Integer       | 商品库存          |
  | data.list[].sales       | Integer       | 商品销量          |
  | data.list[].createTime  | LocalDateTime | 创建时间          |
  | data.list[].updateTime  | LocalDateTime | 更新时间          |
  | data.list[].createUser  | Long          | 创建用户ID        |
  | data.list[].updateUser  | Long          | 更新用户ID        |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "total": 10,
      "list": [
        {
          "id": 1,
          "name": "商品1",
          "categoryId": 1,
          "price": 99.99,
          "image": "https://example.com/image1.jpg",
          "description": "商品描述1",
          "status": 1,
          "stock": 100,
          "sales": 10,
          "createTime": "2026-04-14T12:00:00",
          "updateTime": "2026-04-14T12:00:00",
          "createUser": 1,
          "updateUser": 1
        }
      ]
    }
  }
  ```

### 1.2 获取商品详情

- **请求路径**: `/admin/product/getById`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 商品ID | 查询参数 |

- **响应参数**:
  
  | 参数名              | 类型            | 描述            |
  |:---------------- |:------------- |:------------- |
  | code             | Integer       | 状态码(1成功，0失败)  |
  | msg              | String        | 提示信息          |
  | data             | Object        | 商品详情          |
  | data.id          | Long          | 商品ID          |
  | data.name        | String        | 商品名称          |
  | data.categoryId  | Long          | 分类ID          |
  | data.price       | BigDecimal    | 商品价格          |
  | data.image       | String        | 商品图片          |
  | data.description | String        | 商品描述          |
  | data.status      | Integer       | 商品状态(0禁用，1启用) |
  | data.stock       | Integer       | 商品库存          |
  | data.sales       | Integer       | 商品销量          |
  | data.createTime  | LocalDateTime | 创建时间          |
  | data.updateTime  | LocalDateTime | 更新时间          |
  | data.createUser  | Long          | 创建用户ID        |
  | data.updateUser  | Long          | 更新用户ID        |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "id": 1,
      "name": "商品1",
      "categoryId": 1,
      "price": 99.99,
      "image": "https://example.com/image1.jpg",
      "description": "商品描述1",
      "status": 1,
      "stock": 100,
      "sales": 10,
      "createTime": "2026-04-14T12:00:00",
      "updateTime": "2026-04-14T12:00:00",
      "createUser": 1,
      "updateUser": 1
    }
  }
  ```

### 1.3 添加商品

- **请求路径**: `/admin/product`

- **请求方式**: POST

- **请求参数**:
  
  | 参数名         | 类型         | 必填  | 描述            | 参数格式 |
  |:----------- |:---------- |:--- |:------------- |:---- |
  | name        | String     | 是   | 商品名称          | JSON |
  | categoryId  | Long       | 是   | 分类ID          | JSON |
  | price       | BigDecimal | 是   | 商品价格          | JSON |
  | stock       | Integer    | 是   | 商品库存          | JSON |
  | description | String     | 否   | 商品描述          | JSON |
  | image       | String     | 否   | 商品图片          | JSON |
  | status      | Integer    | 否   | 商品状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "name": "商品1",
    "categoryId": 1,
    "price": 99.99,
    "stock": 100,
    "description": "商品描述1",
    "image": "https://example.com/image1.jpg",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 1.4 修改商品

- **请求路径**: `/admin/product`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名         | 类型         | 必填  | 描述            | 参数格式 |
  |:----------- |:---------- |:--- |:------------- |:---- |
  | id          | Long       | 是   | 商品ID          | JSON |
  | name        | String     | 是   | 商品名称          | JSON |
  | categoryId  | Long       | 是   | 分类ID          | JSON |
  | price       | BigDecimal | 是   | 商品价格          | JSON |
  | stock       | Integer    | 是   | 商品库存          | JSON |
  | description | String     | 否   | 商品描述          | JSON |
  | image       | String     | 否   | 商品图片          | JSON |
  | status      | Integer    | 否   | 商品状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "name": "商品1",
    "categoryId": 1,
    "price": 99.99,
    "stock": 100,
    "description": "商品描述1",
    "image": "https://example.com/image1.jpg",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 1.5 删除商品

- **请求路径**: `/admin/product`

- **请求方式**: DELETE

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 商品ID | 查询参数 |

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 1.6 修改商品状态

- **请求路径**: `/admin/product`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名    | 类型      | 必填  | 描述            | 参数格式 |
  |:------ |:------- |:--- |:------------- |:---- |
  | id     | Long    | 是   | 商品ID          | JSON |
  | status | Integer | 是   | 商品状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

## 2. 分类管理

### 2.1 获取分类列表

- **请求路径**: `/admin/category/list`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名  | 类型      | 必填  | 描述   | 参数格式 |
  |:---- |:------- |:--- |:---- |:---- |
  | type | Integer | 否   | 分类类型 | 查询参数 |

- **响应参数**:
  
  | 参数名               | 类型            | 描述            |
  |:----------------- |:------------- |:------------- |
  | code              | Integer       | 状态码(1成功，0失败)  |
  | msg               | String        | 提示信息          |
  | data              | Array         | 分类列表          |
  | data[].id         | Long          | 分类ID          |
  | data[].type       | Integer       | 分类类型          |
  | data[].name       | String        | 分类名称          |
  | data[].sort       | Integer       | 排序            |
  | data[].status     | Integer       | 分类状态(0禁用，1启用) |
  | data[].createTime | LocalDateTime | 创建时间          |
  | data[].updateTime | LocalDateTime | 更新时间          |
  | data[].createUser | Long          | 创建用户ID        |
  | data[].updateUser | Long          | 更新用户ID        |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": [
      {
        "id": 1,
        "type": 1,
        "name": "分类1",
        "sort": 1,
        "status": 1,
        "createTime": "2026-04-14T12:00:00",
        "updateTime": "2026-04-14T12:00:00",
        "createUser": 1,
        "updateUser": 1
      }
    ]
  }
  ```

### 2.2 添加分类

- **请求路径**: `/admin/category`

- **请求方式**: POST

- **请求参数**:
  
  | 参数名    | 类型      | 必填  | 描述            | 参数格式 |
  |:------ |:------- |:--- |:------------- |:---- |
  | type   | Integer | 是   | 分类类型          | JSON |
  | name   | String  | 是   | 分类名称          | JSON |
  | sort   | Integer | 否   | 排序            | JSON |
  | status | Integer | 否   | 分类状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "type": 1,
    "name": "分类1",
    "sort": 1,
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 2.3 修改分类

- **请求路径**: `/admin/category`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名    | 类型      | 必填  | 描述            | 参数格式 |
  |:------ |:------- |:--- |:------------- |:---- |
  | id     | Long    | 是   | 分类ID          | JSON |
  | type   | Integer | 是   | 分类类型          | JSON |
  | name   | String  | 是   | 分类名称          | JSON |
  | sort   | Integer | 否   | 排序            | JSON |
  | status | Integer | 否   | 分类状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "type": 1,
    "name": "分类1",
    "sort": 1,
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 2.4 删除分类

- **请求路径**: `/admin/category`

- **请求方式**: DELETE

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 分类ID | 查询参数 |

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 2.5 修改分类状态

- **请求路径**: `/admin/category`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名    | 类型      | 必填  | 描述            | 参数格式 |
  |:------ |:------- |:--- |:------------- |:---- |
  | id     | Long    | 是   | 分类ID          | JSON |
  | status | Integer | 是   | 分类状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

## 3. 订单管理

### 3.1 获取订单列表

- **请求路径**: `/admin/order/page`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名      | 类型      | 必填  | 描述   | 参数格式 |
  |:-------- |:------- |:--- |:---- |:---- |
  | page     | Integer | 是   | 当前页码 | 查询参数 |
  | pageSize | Integer | 是   | 每页数量 | 查询参数 |
  | number   | String  | 否   | 订单号  | 查询参数 |
  | status   | Integer | 否   | 订单状态 | 查询参数 |

- **响应参数**:
  
  | 参数名                      | 类型            | 描述           |
  |:------------------------ |:------------- |:------------ |
  | code                     | Integer       | 状态码(1成功，0失败) |
  | msg                      | String        | 提示信息         |
  | data                     | Object        | 响应数据         |
  | data.total               | Integer       | 总记录数         |
  | data.list                | Array         | 订单列表         |
  | data.list[].id           | Long          | 订单ID         |
  | data.list[].orderNumber  | String        | 订单号          |
  | data.list[].userId       | Long          | 用户ID         |
  | data.list[].amount       | BigDecimal    | 订单金额         |
  | data.list[].status       | Integer       | 订单状态         |
  | data.list[].orderTime    | LocalDateTime | 下单时间         |
  | data.list[].payTime      | LocalDateTime | 支付时间         |
  | data.list[].deliveryTime | LocalDateTime | 发货时间         |
  | data.list[].completeTime | LocalDateTime | 完成时间         |
  | data.list[].createTime   | LocalDateTime | 创建时间         |
  | data.list[].updateTime   | LocalDateTime | 更新时间         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "total": 10,
      "list": [
        {
          "id": 1,
          "orderNumber": "20260414123456",
          "userId": 1,
          "amount": 99.99,
          "status": 1,
          "orderTime": "2026-04-14T12:00:00",
          "payTime": "2026-04-14T12:01:00",
          "deliveryTime": "2026-04-14T12:02:00",
          "completeTime": "2026-04-14T12:03:00",
          "createTime": "2026-04-14T12:00:00",
          "updateTime": "2026-04-14T12:03:00"
        }
      ]
    }
  }
  ```

### 3.2 获取订单详情

- **请求路径**: `/admin/order/getById`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 订单ID | 查询参数 |

- **响应参数**:
  
  | 参数名               | 类型            | 描述           |
  |:----------------- |:------------- |:------------ |
  | code              | Integer       | 状态码(1成功，0失败) |
  | msg               | String        | 提示信息         |
  | data              | Object        | 订单详情         |
  | data.id           | Long          | 订单ID         |
  | data.orderNumber  | String        | 订单号          |
  | data.userId       | Long          | 用户ID         |
  | data.amount       | BigDecimal    | 订单金额         |
  | data.status       | Integer       | 订单状态         |
  | data.orderTime    | LocalDateTime | 下单时间         |
  | data.payTime      | LocalDateTime | 支付时间         |
  | data.deliveryTime | LocalDateTime | 发货时间         |
  | data.completeTime | LocalDateTime | 完成时间         |
  | data.createTime   | LocalDateTime | 创建时间         |
  | data.updateTime   | LocalDateTime | 更新时间         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "id": 1,
      "orderNumber": "20260414123456",
      "userId": 1,
      "amount": 99.99,
      "status": 1,
      "orderTime": "2026-04-14T12:00:00",
      "payTime": "2026-04-14T12:01:00",
      "deliveryTime": "2026-04-14T12:02:00",
      "completeTime": "2026-04-14T12:03:00",
      "createTime": "2026-04-14T12:00:00",
      "updateTime": "2026-04-14T12:03:00"
    }
  }
  ```

### 3.3 修改订单状态

- **请求路径**: `/admin/order`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名    | 类型      | 必填  | 描述   | 参数格式 |
  |:------ |:------- |:--- |:---- |:---- |
  | id     | Long    | 是   | 订单ID | JSON |
  | status | Integer | 是   | 订单状态 | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "status": 2
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

## 4. 秒杀活动管理

### 4.1 获取秒杀活动列表

- **请求路径**: `/admin/seckill/activity/page`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名      | 类型      | 必填  | 描述   | 参数格式 |
  |:-------- |:------- |:--- |:---- |:---- |
  | page     | Integer | 是   | 当前页码 | 查询参数 |
  | pageSize | Integer | 是   | 每页数量 | 查询参数 |
  | name     | String  | 否   | 活动名称 | 查询参数 |

- **响应参数**:
  
  | 参数名                    | 类型            | 描述            |
  |:---------------------- |:------------- |:------------- |
  | code                   | Integer       | 状态码(1成功，0失败)  |
  | msg                    | String        | 提示信息          |
  | data                   | Object        | 响应数据          |
  | data.total             | Integer       | 总记录数          |
  | data.list              | Array         | 秒杀活动列表        |
  | data.list[].id         | Long          | 活动ID          |
  | data.list[].name       | String        | 活动名称          |
  | data.list[].startTime  | LocalDateTime | 开始时间          |
  | data.list[].endTime    | LocalDateTime | 结束时间          |
  | data.list[].status     | Integer       | 活动状态(0禁用，1启用) |
  | data.list[].createTime | LocalDateTime | 创建时间          |
  | data.list[].updateTime | LocalDateTime | 更新时间          |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "total": 10,
      "list": [
        {
          "id": 1,
          "name": "秒杀活动1",
          "startTime": "2026-04-14T12:00:00",
          "endTime": "2026-04-14T13:00:00",
          "status": 1,
          "createTime": "2026-04-14T11:00:00",
          "updateTime": "2026-04-14T11:00:00"
        }
      ]
    }
  }
  ```

### 4.2 获取秒杀活动详情

- **请求路径**: `/admin/seckill/activity/getById`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 活动ID | 查询参数 |

- **响应参数**:
  
  | 参数名             | 类型            | 描述            |
  |:--------------- |:------------- |:------------- |
  | code            | Integer       | 状态码(1成功，0失败)  |
  | msg             | String        | 提示信息          |
  | data            | Object        | 秒杀活动详情        |
  | data.id         | Long          | 活动ID          |
  | data.name       | String        | 活动名称          |
  | data.startTime  | LocalDateTime | 开始时间          |
  | data.endTime    | LocalDateTime | 结束时间          |
  | data.status     | Integer       | 活动状态(0禁用，1启用) |
  | data.createTime | LocalDateTime | 创建时间          |
  | data.updateTime | LocalDateTime | 更新时间          |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "id": 1,
      "name": "秒杀活动1",
      "startTime": "2026-04-14T12:00:00",
      "endTime": "2026-04-14T13:00:00",
      "status": 1,
      "createTime": "2026-04-14T11:00:00",
      "updateTime": "2026-04-14T11:00:00"
    }
  }
  ```

### 4.3 添加秒杀活动

- **请求路径**: `/admin/seckill/activity`

- **请求方式**: POST

- **请求参数**:
  
  | 参数名       | 类型            | 必填  | 描述            | 参数格式 |
  |:--------- |:------------- |:--- |:------------- |:---- |
  | name      | String        | 是   | 活动名称          | JSON |
  | startTime | LocalDateTime | 是   | 开始时间          | JSON |
  | endTime   | LocalDateTime | 是   | 结束时间          | JSON |
  | status    | Integer       | 否   | 活动状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "name": "秒杀活动1",
    "startTime": "2026-04-14T12:00:00",
    "endTime": "2026-04-14T13:00:00",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 4.4 修改秒杀活动

- **请求路径**: `/admin/seckill/activity`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名       | 类型            | 必填  | 描述            | 参数格式 |
  |:--------- |:------------- |:--- |:------------- |:---- |
  | id        | Long          | 是   | 活动ID          | JSON |
  | name      | String        | 是   | 活动名称          | JSON |
  | startTime | LocalDateTime | 是   | 开始时间          | JSON |
  | endTime   | LocalDateTime | 是   | 结束时间          | JSON |
  | status    | Integer       | 否   | 活动状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "name": "秒杀活动1",
    "startTime": "2026-04-14T12:00:00",
    "endTime": "2026-04-14T13:00:00",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 4.5 删除秒杀活动

- **请求路径**: `/admin/seckill/activity`

- **请求方式**: DELETE

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述   | 参数格式 |
  |:--- |:---- |:--- |:---- |:---- |
  | id  | Long | 是   | 活动ID | 查询参数 |

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

## 5. 秒杀优惠券管理

### 5.1 获取秒杀优惠券列表

- **请求路径**: `/admin/seckill/coupon/page`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名      | 类型      | 必填  | 描述   | 参数格式 |
  |:-------- |:------- |:--- |:---- |:---- |
  | page     | Integer | 是   | 当前页码 | 查询参数 |
  | pageSize | Integer | 是   | 每页数量 | 查询参数 |

- **响应参数**:
  
  | 参数名                    | 类型            | 描述             |
  |:---------------------- |:------------- |:-------------- |
  | code                   | Integer       | 状态码(1成功，0失败)   |
  | msg                    | String        | 提示信息           |
  | data                   | Object        | 响应数据           |
  | data.total             | Integer       | 总记录数           |
  | data.list              | Array         | 秒杀优惠券列表        |
  | data.list[].id         | Long          | 优惠券ID          |
  | data.list[].name       | String        | 优惠券名称          |
  | data.list[].type       | Integer       | 优惠券类型          |
  | data.list[].value      | BigDecimal    | 优惠券价值          |
  | data.list[].minSpend   | BigDecimal    | 最低消费           |
  | data.list[].startTime  | LocalDateTime | 开始时间           |
  | data.list[].endTime    | LocalDateTime | 结束时间           |
  | data.list[].status     | Integer       | 优惠券状态(0禁用，1启用) |
  | data.list[].createTime | LocalDateTime | 创建时间           |
  | data.list[].updateTime | LocalDateTime | 更新时间           |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "total": 10,
      "list": [
        {
          "id": 1,
          "name": "秒杀优惠券1",
          "type": 1,
          "value": 10.00,
          "minSpend": 100.00,
          "startTime": "2026-04-14T12:00:00",
          "endTime": "2026-04-14T13:00:00",
          "status": 1,
          "createTime": "2026-04-14T11:00:00",
          "updateTime": "2026-04-14T11:00:00"
        }
      ]
    }
  }
  ```

### 5.2 获取秒杀优惠券详情

- **请求路径**: `/admin/seckill/coupon/getById`

- **请求方式**: GET

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述    | 参数格式 |
  |:--- |:---- |:--- |:----- |:---- |
  | id  | Long | 是   | 优惠券ID | 查询参数 |

- **响应参数**:
  
  | 参数名             | 类型            | 描述             |
  |:--------------- |:------------- |:-------------- |
  | code            | Integer       | 状态码(1成功，0失败)   |
  | msg             | String        | 提示信息           |
  | data            | Object        | 秒杀优惠券详情        |
  | data.id         | Long          | 优惠券ID          |
  | data.name       | String        | 优惠券名称          |
  | data.type       | Integer       | 优惠券类型          |
  | data.value      | BigDecimal    | 优惠券价值          |
  | data.minSpend   | BigDecimal    | 最低消费           |
  | data.startTime  | LocalDateTime | 开始时间           |
  | data.endTime    | LocalDateTime | 结束时间           |
  | data.status     | Integer       | 优惠券状态(0禁用，1启用) |
  | data.createTime | LocalDateTime | 创建时间           |
  | data.updateTime | LocalDateTime | 更新时间           |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": {
      "id": 1,
      "name": "秒杀优惠券1",
      "type": 1,
      "value": 10.00,
      "minSpend": 100.00,
      "startTime": "2026-04-14T12:00:00",
      "endTime": "2026-04-14T13:00:00",
      "status": 1,
      "createTime": "2026-04-14T11:00:00",
      "updateTime": "2026-04-14T11:00:00"
    }
  }
  ```

### 5.3 添加秒杀优惠券

- **请求路径**: `/admin/seckill/coupon`

- **请求方式**: POST

- **请求参数**:
  
  | 参数名       | 类型            | 必填  | 描述             | 参数格式 |
  |:--------- |:------------- |:--- |:-------------- |:---- |
  | name      | String        | 是   | 优惠券名称          | JSON |
  | type      | Integer       | 是   | 优惠券类型          | JSON |
  | value     | BigDecimal    | 是   | 优惠券价值          | JSON |
  | minSpend  | BigDecimal    | 否   | 最低消费           | JSON |
  | startTime | LocalDateTime | 是   | 开始时间           | JSON |
  | endTime   | LocalDateTime | 是   | 结束时间           | JSON |
  | status    | Integer       | 否   | 优惠券状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "name": "秒杀优惠券1",
    "type": 1,
    "value": 10.00,
    "minSpend": 100.00,
    "startTime": "2026-04-14T12:00:00",
    "endTime": "2026-04-14T13:00:00",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 5.4 修改秒杀优惠券

- **请求路径**: `/admin/seckill/coupon`

- **请求方式**: PUT

- **请求参数**:
  
  | 参数名       | 类型            | 必填  | 描述             | 参数格式 |
  |:--------- |:------------- |:--- |:-------------- |:---- |
  | id        | Long          | 是   | 优惠券ID          | JSON |
  | name      | String        | 是   | 优惠券名称          | JSON |
  | type      | Integer       | 是   | 优惠券类型          | JSON |
  | value     | BigDecimal    | 是   | 优惠券价值          | JSON |
  | minSpend  | BigDecimal    | 否   | 最低消费           | JSON |
  | startTime | LocalDateTime | 是   | 开始时间           | JSON |
  | endTime   | LocalDateTime | 是   | 结束时间           | JSON |
  | status    | Integer       | 否   | 优惠券状态(0禁用，1启用) | JSON |

- **请求示例**:
  
  ```json
  {
    "id": 1,
    "name": "秒杀优惠券1",
    "type": 1,
    "value": 10.00,
    "minSpend": 100.00,
    "startTime": "2026-04-14T12:00:00",
    "endTime": "2026-04-14T13:00:00",
    "status": 1
  }
  ```

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```

### 5.5 删除秒杀优惠券

- **请求路径**: `/admin/seckill/coupon`

- **请求方式**: DELETE

- **请求参数**:
  
  | 参数名 | 类型   | 必填  | 描述    | 参数格式 |
  |:--- |:---- |:--- |:----- |:---- |
  | id  | Long | 是   | 优惠券ID | 查询参数 |

- **响应参数**:
  
  | 参数名  | 类型      | 描述           |
  |:---- |:------- |:------------ |
  | code | Integer | 状态码(1成功，0失败) |
  | msg  | String  | 提示信息         |
  | data | String  | 响应数据         |

- **响应示例**:
  
  ```json
  {
    "code": 1,
    "msg": null,
    "data": null
  }
  ```