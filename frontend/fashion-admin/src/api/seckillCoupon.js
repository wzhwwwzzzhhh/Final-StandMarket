import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 秒杀活动相关API
export const seckillActivityApi = {
  // 获取秒杀活动列表
  getActivityList: () => {
    return api.get('/admin/seckill/activity/list')
  },
  
  // 获取秒杀活动详情
  getActivityById: (id) => {
    return api.get(`/admin/seckill/activity/${id}`)
  },
  
  // 添加秒杀活动
  addActivity: (data) => {
    return api.post('/admin/seckill/activity', data)
  },
  
  // 修改秒杀活动
  updateActivity: (data) => {
    return api.put('/admin/seckill/activity', data)
  },
  
  // 删除秒杀活动
  deleteActivity: (id) => {
    return api.delete('/admin/seckill/activity', { params: { id } })
  }
}

// 秒杀券相关API
export const seckillCouponApi = {
  // 获取秒杀券列表
  getCouponList: (params) => {
    return api.get('/admin/seckill/coupon/page', { params })
  },
  
  // 获取秒杀券详情
  getCouponById: (id) => {
    return api.get(`/admin/seckill/coupon/${id}`)
  },
  
  // 添加秒杀券
  addCoupon: (data) => {
    return api.post('/admin/seckill/coupon', data)
  },
  
  // 修改秒杀券
  updateCoupon: (data) => {
    return api.put('/admin/seckill/coupon', data)
  },
  
  // 删除秒杀券
  deleteCoupon: (id) => {
    return api.delete('/admin/seckill/coupon', { params: { id } })
  },
  
  // 单个预热
  preheatCoupon: (id) => {
    return api.post(`/admin/seckill/coupon/preheat/${id}`)
  },
  
  // 批量预热
  preheatBatchCoupons: (ids) => {
    // 将数组转换为Spring Boot期望的参数格式
    const params = new URLSearchParams()
    ids.forEach(id => {
      params.append('ids', id)
    })
    
    return api.post('/admin/seckill/coupon/preheat/batch', null, {
      params: params
    })
  }
}

// 秒杀订单相关API
export const seckillOrderApi = {
  // 获取秒杀订单列表
  getSeckillOrderList: (params) => {
    return api.get('/admin/seckill/order/page', { params })
  },
  
  // 获取秒杀订单详情
  getSeckillOrderDetail: (orderNumber) => {
    return api.get(`/admin/seckill/order/${orderNumber}`)
  },
  
  // 确认订单支付
  confirmSeckillOrderPayment: (orderNumber) => {
    return api.post(`/admin/seckill/order/${orderNumber}/confirm-payment`)
  },
  
  // 取消秒杀订单
  cancelSeckillOrder: (orderNumber) => {
    return api.post(`/admin/seckill/order/${orderNumber}/cancel`)
  },
  
  // 删除秒杀订单
  deleteSeckillOrder: (id) => {
    return api.delete('/admin/seckill/order', { params: { id } })
  },
  
  // 获取秒杀订单统计
  getSeckillOrderStatistics: () => {
    return api.get('/admin/seckill/order/statistics')
  }
}

// 统一导出所有秒杀相关API
export const seckillApi = {
  ...seckillActivityApi,
  ...seckillCouponApi,
  ...seckillOrderApi
}
