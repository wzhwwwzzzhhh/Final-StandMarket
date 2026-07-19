import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const refundApi = {
  // 退款列表
  list: (params) => api.get('/admin/refund/list', { params }),
  // 同意退款
  approve: (data) => api.put('/admin/refund/approve', data),
  // 拒绝退款
  reject: (data) => api.put('/admin/refund/reject', data)
}
