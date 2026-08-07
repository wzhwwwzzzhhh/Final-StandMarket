import request from '../utils/request'

export const reviewApi = {
  getList: (params) => request.get('/admin/review/list', { params }),
  updateStatus: (data) => request.put('/admin/review/status', data),
  deleteReview: (id) => request.delete(`/admin/review/${id}`)
}
