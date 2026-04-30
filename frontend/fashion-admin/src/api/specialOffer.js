import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 特价商品相关API
export const specialOfferApi = {
  // 获取特价商品列表
  getOfferList: (params) => {
    return api.get('/admin/special/offer/page', { params })
  },
  
  // 获取特价商品详情
  getOfferById: (id) => {
    return api.get(`/admin/special/offer/${id}`)
  },
  
  // 添加特价商品
  addOffer: (data) => {
    return api.post('/admin/special/offer', data)
  },
  
  // 修改特价商品
  updateOffer: (data) => {
    return api.put('/admin/special/offer', data)
  },
  
  // 删除特价商品
  deleteOffer: (id) => {
    return api.delete('/admin/special/offer', { params: { id } })
  }
}
