import axios from 'axios'
import router from '../router'

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
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
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push('/login')
  }
  return Promise.reject(error)
})

// 通用优惠券API
export const couponApi = {
  // 可领券列表（领券中心）
  getClaimableTemplates: () => {
    return api.get('/user/coupon/templates')
  },

  // 领取优惠券
  claimCoupon: (templateId) => {
    return api.post(`/user/coupon/claim/${templateId}`)
  },

  // 我的卡包
  getMyCoupons: (status) => {
    let url = '/user/coupon/my'
    if (status !== undefined && status !== null && status !== '') {
      url += `?status=${status}`
    }
    return api.get(url)
  },

  // 结算页可用券（后端按当前用户购物车快照计价）
  getAvailableCoupons: (params) => {
    return api.get('/user/coupon/available', { params })
  }
}
