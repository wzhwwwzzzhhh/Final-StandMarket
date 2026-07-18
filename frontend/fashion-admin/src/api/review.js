import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const reviewApi = {
  getList: (params) => api.get('/admin/review/list', { params }),
  updateStatus: (data) => api.put('/admin/review/status', data),
  deleteReview: (id) => api.delete(`/admin/review/${id}`)
}
