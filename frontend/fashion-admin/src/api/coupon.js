import request from '../utils/request'

// 通用优惠券管理API
export const couponApi = {
  // 模板列表（分页）
  getTemplatePage: (params) => {
    return request.get('/admin/coupon/template/page', { params })
  },

  // 模板详情
  getTemplateById: (id) => {
    return request.get(`/admin/coupon/template/${id}`)
  },

  // 创建模板
  addTemplate: (data) => {
    return request.post('/admin/coupon/template', data)
  },

  // 更新模板
  updateTemplate: (data) => {
    return request.put('/admin/coupon/template', data)
  },

  // 删除模板（软删）
  deleteTemplate: (id) => {
    return request.delete('/admin/coupon/template', { params: { id } })
  },

  // 用户持券分页（运营管理）
  getUserCouponPage: (params) => {
    return request.get('/admin/coupon/userCoupon/page', { params })
  }
}