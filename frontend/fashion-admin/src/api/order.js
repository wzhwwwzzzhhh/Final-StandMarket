import request from '../utils/request'

// 订单相关API
export const orderApi = {
  // 获取订单列表
  getOrderList: (params) => {
    return request.get('/admin/order', { params })
  },

  // 获取订单详情
  getOrderById: (id) => {
    return request.get(`/admin/order/${id}`)
  },

  // 修改订单状态
  updateOrderStatus: (id, data) => {
    return request.put(`/admin/order/${id}/status`, data)
  },

  // 查询支付信息
  getPaymentInfo: (id) => {
    return request.get(`/admin/order/${id}/payment`)
  },

  // 发货
  deliver: (data) => {
    return request.put('/admin/order/deliver', data)
  }
}
