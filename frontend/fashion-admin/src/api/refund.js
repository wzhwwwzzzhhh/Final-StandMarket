import request from '../utils/request'

export const refundApi = {
  // 退款列表
  list: (params) => request.get('/admin/refund/list', { params }),
  // 同意退款
  approve: (data) => request.put('/admin/refund/approve', data),
  // 拒绝退款
  reject: (data) => request.put('/admin/refund/reject', data)
}
