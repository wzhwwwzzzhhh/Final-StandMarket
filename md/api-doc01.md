# API 接口文档

## 1. 登录功能接口

### 1.1 登录接口
- **URL**: `/api/user/login`
- **方法**: POST
- **请求参数**:
  ```json
  {
    "phone": "13800138000",
    "password": "123456"
  }
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
      "userInfo": {
        "id": 1,
        "phone": "13800138000",
        "username": "测试用户",
        "avatar": "https://example.com/avatar.jpg"
      }
    }
  }
  ```

### 1.2 注册接口
- **URL**: `/api/user/register`
- **方法**: POST
- **请求参数**:
  ```json
  {
    "phone": "13800138000",
    "password": "123456",
    "username": "测试用户"
  }
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "注册成功",
    "data": {
      "id": 1,
      "phone": "13800138000",
      "username": "测试用户"
    }
  }
  ```

### 1.3 获取用户信息接口
- **URL**: `/api/user/info`
- **方法**: GET
- **请求头**:
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": {
      "id": 1,
      "phone": "13800138000",
      "username": "测试用户",
      "avatar": "https://example.com/avatar.jpg"
    }
  }
  ```

### 1.4 退出登录接口
- **URL**: `/api/user/logout`
- **方法**: POST
- **请求头**:
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "退出成功",
    "data": null
  }
  ```

## 2. 商品功能接口

### 2.1 获取商品列表
- **URL**: `/api/user/product/page`
- **方法**: GET
- **请求参数**:
  - page: 页码
  - size: 每页数量
  - categoryId: 分类ID（可选）
  - keyword: 搜索关键词（可选）
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": {
      "total": 100,
      "list": [
        {
          "id": 1,
          "name": "商品名称",
          "price": 199,
          "image": "https://example.com/image.jpg",
          "categoryId": 1,
          "categoryName": "分类名称",
          "stock": 100
        }
      ]
    }
  }
  ```

### 2.2 获取商品详情
- **URL**: `/api/user/product/getById`
- **方法**: GET
- **请求参数**:
  - id: 商品ID
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": {
      "id": 1,
      "name": "商品名称",
      "price": 199,
      "image": "https://example.com/image.jpg",
      "categoryId": 1,
      "categoryName": "分类名称",
      "stock": 100,
      "description": "商品描述",
      "skuList": [
        {
          "id": 1,
          "skuInfo": "颜色:红色,尺码:M",
          "price": 199,
          "stock": 50
        }
      ]
    }
  }
  ```

### 2.3 获取分类列表
- **URL**: `/api/user/category/list`
- **方法**: GET
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": [
      {
        "id": 1,
        "name": "分类名称",
        "parentId": 0,
        "children": []
      }
    ]
  }
  ```

### 2.4 根据分类获取商品
- **URL**: `/api/user/product/list`
- **方法**: GET
- **请求参数**:
  - categoryId: 分类ID
  - page: 页码（可选）
  - size: 每页数量（可选）
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": {
      "total": 50,
      "list": [
        {
          "id": 1,
          "name": "商品名称",
          "price": 199,
          "image": "https://example.com/image.jpg",
          "categoryId": 1,
          "categoryName": "分类名称",
          "stock": 100
        }
      ]
    }
  }
  ```

## 3. 购物车功能接口

### 3.1 添加商品到购物车
- **URL**: `/api/user/shopping-cart/add`
- **方法**: POST
- **请求参数**:
  ```json
  {
    "productId": 1,
    "skuInfo": "颜色:红色,尺码:M",
    "number": 1
  }
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "添加成功",
    "data": {
      "id": 1,
      "productId": 1,
      "skuInfo": "颜色:红色,尺码:M",
      "number": 1,
      "name": "商品名称",
      "price": 199,
      "image": "https://example.com/image.jpg",
      "amount": 199
    }
  }
  ```

### 3.2 获取购物车列表
- **URL**: `/api/user/shopping-cart/list`
- **方法**: GET
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "获取成功",
    "data": [
      {
        "id": 1,
        "productId": 1,
        "skuInfo": "颜色:红色,尺码:M",
        "number": 1,
        "name": "商品名称",
        "price": 199,
        "image": "https://example.com/image.jpg",
        "amount": 199
      }
    ]
  }
  ```

### 3.3 更新购物车商品数量
- **URL**: `/api/user/shopping-cart/update`
- **方法**: PUT
- **请求参数**:
  ```json
  {
    "id": 1,
    "number": 2
  }
  ```
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "更新成功",
    "data": {
      "id": 1,
      "productId": 1,
      "skuInfo": "颜色:红色,尺码:M",
      "number": 2,
      "name": "商品名称",
      "price": 199,
      "image": "https://example.com/image.jpg",
      "amount": 398
    }
  }
  ```

### 3.4 删除购物车商品
- **URL**: `/api/user/shopping-cart/delete/{id}`
- **方法**: DELETE
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "删除成功",
    "data": null
  }
  ```

### 3.5 清空购物车
- **URL**: `/api/user/shopping-cart/clear`
- **方法**: DELETE
- **响应格式**:
  ```json
  {
    "code": 1,
    "msg": "清空成功",
    "data": null
  }
  ```
