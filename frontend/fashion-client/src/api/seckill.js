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

// 秒杀相关API
export const seckillApi = {
  // 获取秒杀活动列表
  getSeckillActivityList: () => {
    return api.get('/user/seckill/activity/list')
  },

  // 获取秒杀活动详情
  getSeckillActivityDetail: (id) => {
    return api.get(`/user/seckill/activity/detail/${id}`)
  },

  // 获取秒杀券列表
  getSeckillCouponList: () => {
    return api.get('/user/seckill/coupon/list')
  },

  // 获取秒杀券详情
  getSeckillCouponDetail: (id) => {
    return api.get(`/user/seckill/coupon/detail/${id}`)
  },

  // 抢购秒杀券（创建秒杀订单）
  seckillCoupon: (couponId) => {
    return api.post(`/user/seckill/coupon/${couponId}/orders`)
  },

  // 获取特价商品列表
  getSpecialOfferList: () => {
    return api.get('/user/special-offer/list')
  },

  // 获取特价商品详情
  getSpecialOfferDetail: (id) => {
    return api.get(`/user/special-offer/detail/${id}`)
  },

  // 抢购特价商品
  seckillSpecialOffer: (offerId) => {
    return api.post('/user/special-offer/seckill', { offerId })
  },

  // 获取用户的秒杀订单列表
  getSeckillOrderList: () => {
    return api.get('/user/seckill/order/list')
  },

  // 获取秒杀订单详情（根据订单号）
  getSeckillOrderDetail: (orderNumber) => {
    return api.get(`/user/seckill/order/detail/${orderNumber}`)
  },
  
  // 根据订单号查询秒杀订单
  getSeckillOrderByNumber: (orderNumber) => {
    return api.get(`/user/seckill/order/${orderNumber}`)
  },
  
  // 获取用户的秒杀券列表
  getUserCoupons: (status) => {
    let url = '/user/seckill/order/coupons'
    if (status) {
      url += `?status=${status}`
    }
    return api.get(url)
  },

  // 支付秒杀订单
  paySeckillOrder: (orderNumber) => {
    return api.post(`/user/seckill/order/pay/${orderNumber}`)
  },

  // 计算订单金额（根据选择的秒杀活动和秒杀券）
  calculateOrderAmount: (calculateData) => {
    return api.post('/user/seckill/order/calculate', calculateData)
  },

  // 获取活动列表（用于订单页面）
  getActivityList: () => {
    return api.get('/user/seckill/activity/list')
  }
}
