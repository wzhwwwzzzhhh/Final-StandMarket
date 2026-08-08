import request from '../utils/request'

// 特价商品相关API
export const specialOfferApi = {
  // 获取特价商品列表
  getOfferList: (params) => {
    return request.get('/admin/special/offer/page', { params })
  },
  
  // 获取特价商品详情
  getOfferById: (id) => {
    return request.get(`/admin/special/offer/${id}`)
  },
  
  // 添加特价商品
  addOffer: (data) => {
    return request.post('/admin/special/offer', data)
  },
  
  // 修改特价商品
  updateOffer: (data) => {
    return request.put('/admin/special/offer', data)
  },
  
  // 删除特价商品
  deleteOffer: (id) => {
    return request.delete('/admin/special/offer', { params: { id } })
  }
}
