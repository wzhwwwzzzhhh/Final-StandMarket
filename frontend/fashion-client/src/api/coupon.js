import api from '@/utils/request'

// 通用优惠券API
export const couponApi = {
  // 可领券列表（领券中心）
  getClaimableTemplates: () => {
    return api.get('/user/coupon/templates')
  },

  // 领取优惠券
  claimCoupon: (templateId) => {
    return api.post(`/user/coupon/claim/${templateId}`)
  },

  // 我的卡包
  getMyCoupons: (status) => {
    let url = '/user/coupon/my'
    if (status !== undefined && status !== null && status !== '') {
      url += `?status=${status}`
    }
    return api.get(url)
  },

  // 结算页可用券（后端按当前用户购物车快照计价）
  getAvailableCoupons: (params) => {
    return api.get('/user/coupon/available', { params })
  }
}
