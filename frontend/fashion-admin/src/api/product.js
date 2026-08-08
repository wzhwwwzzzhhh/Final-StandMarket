import request from '../utils/request'

// 商品相关API
export const productApi = {
  // 获取商品列表
  getProductList: (params) => {
    return request.get('/admin/product', { params })
  },

  // 获取商品详情
  getProductById: (id) => {
    return request.get(`/admin/product/${id}`)
  },

  // 添加商品
  addProduct: (data) => {
    return request.post('/admin/product', data)
  },

  // 修改商品
  updateProduct: (id, data) => {
    return request.put(`/admin/product/${id}`, data)
  },

  // 删除商品
  deleteProduct: (id) => {
    return request.delete(`/admin/product/${id}`)
  },

  // 修改商品状态
  updateStatus: (id, status) => {
    return request.put(`/admin/product/${id}`, { status })
  }
}

// 分类相关API
export const categoryApi = {
  // 获取分类列表
  getCategoryList: (type) => {
    return request.get('/admin/category/list', { params: { type } })
  },

  // 分页获取分类
  getCategoryPage: (page, pageSize) => {
    return request.get('/admin/category', { params: { page, pageSize } })
  },

  // 获取分类详情
  getCategoryById: (id) => {
    return request.get(`/admin/category/${id}`)
  },

  // 添加分类
  addCategory: (data) => {
    return request.post('/admin/category', data)
  },

  // 修改分类
  updateCategory: (id, data) => {
    return request.put(`/admin/category/${id}`, data)
  },

  // 删除分类
  deleteCategory: (id) => {
    return request.delete(`/admin/category/${id}`)
  },

  // 修改分类状态
  updateStatus: (id, status) => {
    return request.put(`/admin/category/${id}`, { status })
  }
}
