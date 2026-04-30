import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 商品相关API
export const productApi = {
  // 获取商品列表
  getProductList: (params) => {
    return api.get('/admin/product', { params })
  },

  // 获取商品详情
  getProductById: (id) => {
    return api.get(`/admin/product/${id}`)
  },

  // 添加商品
  addProduct: (data) => {
    return api.post('/admin/product', data)
  },

  // 修改商品
  updateProduct: (id, data) => {
    return api.put(`/admin/product/${id}`, data)
  },

  // 删除商品
  deleteProduct: (id) => {
    return api.delete(`/admin/product/${id}`)
  },

  // 修改商品状态
  updateStatus: (id, status) => {
    return api.put(`/admin/product/${id}`, { status })
  }
}

// 分类相关API
export const categoryApi = {
  // 获取分类列表
  getCategoryList: (type) => {
    return api.get('/admin/category/list', { params: { type } })
  },

  // 分页获取分类
  getCategoryPage: (page, pageSize) => {
    return api.get('/admin/category', { params: { page, pageSize } })
  },

  // 获取分类详情
  getCategoryById: (id) => {
    return api.get(`/admin/category/${id}`)
  },

  // 添加分类
  addCategory: (data) => {
    return api.post('/admin/category', data)
  },

  // 修改分类
  updateCategory: (id, data) => {
    return api.put(`/admin/category/${id}`, data)
  },

  // 删除分类
  deleteCategory: (id) => {
    return api.delete(`/admin/category/${id}`)
  },

  // 修改分类状态
  updateStatus: (id, status) => {
    return api.put(`/admin/category/${id}`, { status })
  }
}
