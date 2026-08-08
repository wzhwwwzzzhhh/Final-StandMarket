import request from '@/utils/request'

export const refundApi = {
  // 申请退款
  apply: (data) => request.post('/user/refund/apply', data),
  // 获取退款记录列表
  list: () => request.get('/user/refund/list')
}
