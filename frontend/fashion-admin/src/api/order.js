import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 订单相关API
export const orderApi = {
  // 获取订单列表
  getOrderList: (params) => {
    return api.get('/admin/order', { params })
  },

  // 获取订单详情
  getOrderById: (id) => {
    return api.get(`/admin/order/${id}`)
  },

  // 修改订单状态
  updateOrderStatus: (id, data) => {
    return api.put(`/admin/order/${id}/status`, data)
  }
}
