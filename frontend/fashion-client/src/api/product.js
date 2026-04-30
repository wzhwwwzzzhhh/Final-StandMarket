import axios from 'axios'
import router from '../router'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器，添加token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => {
  return Promise.reject(error)
})

// 响应拦截器，处理错误
api.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    // 未授权，跳转到登录页
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push('/login')
  }
  return Promise.reject(error)
})

// 商品相关API
export const productApi = {
  // 获取商品列表
  getProductList: (params) => {
    return api.get('/user/product', { params })
  },

  // 获取商品详情
  getProductById: (id) => {
    return api.get(`/user/product/${id}`)
  },

  // 获取分类列表
  getCategoryList: () => {
    return api.get('/user/category/list')
  }
}

// 购物车相关API
export const cartApi = {
  // 添加商品到购物车
  addToCart: (data) => {
    return api.post('/user/shopping-cart/add', null, { params: { productId: data.productId, number: data.number, skuInfo: data.skuInfo } })
  },

  // 获取购物车列表
  getCartList: () => {
    return api.get('/user/shopping-cart/list')
  },

  // 更新购物车商品数量
  updateCartItem: (data) => {
    return api.put('/user/shopping-cart/update', null, { params: { productId: data.productId, number: data.number } })
  },

  // 删除购物车商品
  deleteCartItem: (id) => {
    return api.delete(`/user/shopping-cart/delete/${id}`)
  },

  // 清空购物车
  clearCart: () => {
    return api.delete('/user/shopping-cart/clear')
  },

  // 批量删除购物车商品
  batchDeleteCartItems: (ids) => {
    return api.delete('/user/shopping-cart/batch-delete', { data: ids })
  },

  // 结算购物车商品
  checkoutCart: (ids) => {
    return api.post('/user/shopping-cart/checkout', ids)
  }
}

// 地址相关API
export const addressApi = {
  // 获取用户地址列表
  getList: (config) => {
    return api.get('/user/address/list', config)
  },
  // 获取默认地址
  getDefault: (config) => {
    return api.get('/user/address/default', config)
  }
}

// 秒杀活动API
export const seckillApi = {
  // 获取秒杀活动列表
  getActivityList: (config) => {
    return api.get('/user/seckill/activity/list', config)
  },
  // 获取用户秒杀券列表
  getUserCoupons: (status, config) => {
    return api.get('/user/seckill/order/coupons', { params: { status }, ...config })
  },
  // 计算订单金额（根据选择的秒杀活动和秒杀券）
  calculateOrderAmount: (calculateData, config) => {
    return api.post('/user/seckill/order/calculate', calculateData, config)
  }
}

// 订单相关API
export const orderApi = {
  // 创建订单
  createOrder: (orderData, config) => {
    return api.post('/user/order/create', orderData, config)
  },

  // 创建订单列表
  createOrders: (productIds, config) => {
    return api.post('/user/order/createOrders', productIds, config)
  },

  // 获取用户订单列表
  getOrderList: (status, config) => {
    return api.get('/user/order/list', { params: { status }, ...config })
  },

  // 获取订单详情
  getOrderDetail: (id, config) => {
    return api.get(`/user/order/detail/${id}`, config)
  },

  // 取消订单
  cancelOrder: (id, config) => {
    return api.put(`/user/order/cancel/${id}`, null, config)
  },

  // 支付订单
  payOrder: (id, config) => {
    return api.put(`/user/order/pay/${id}`, null, config)
  },

  // 确认收货
  confirmOrder: (id, config) => {
    return api.put(`/user/order/confirm/${id}`, null, config)
  }
}