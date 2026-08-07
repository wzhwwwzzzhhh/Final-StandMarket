import api from '@/utils/request'

const browseApi = {
  record: (productId) => api.post(`/user/browse/${productId}`, null, { skipAuthRedirect: true }),
  list: () => api.get('/user/browse/list'),
  clear: () => api.delete('/user/browse')
}

export default browseApi
