import api from '@/utils/request'

const reviewApi = {
  add: (data) => api.post('/user/review/add', data),
  list: (productId, params) => api.get(`/user/review/list/${productId}`, { params }),
  stats: (productId) => api.get(`/user/review/stats/${productId}`),
  my: (params) => api.get('/user/review/my', { params }),
  check: (orderId) => api.get(`/user/review/check/${orderId}`)
}

export default reviewApi
