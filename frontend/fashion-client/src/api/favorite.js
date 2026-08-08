import api from '@/utils/request'

const favoriteApi = {
  add: (productId) => api.post(`/user/favorite/add/${productId}`),
  remove: (productId) => api.delete(`/user/favorite/remove/${productId}`),
  check: (productId) => api.get(`/user/favorite/check/${productId}`),
  list: () => api.get('/user/favorite/list'),
  count: () => api.get('/user/favorite/count')
}

export default favoriteApi
