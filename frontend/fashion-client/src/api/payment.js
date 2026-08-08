import request from '@/utils/request'

/**
 * 支付相关 API
 */
export const paymentApi = {
  /**
   * 发起支付宝支付
   * @param {number} orderId 订单ID
   * @returns {Promise} { code, data: { orderId, payNo, form } }
   */
  alipayPay: (orderId) => {
    return request.post(`/user/pay/alipay/${orderId}`)
  },

  /**
   * 查询支付状态
   * @param {number} orderId 订单ID
   * @returns {Promise} { code, data: { payStatus, payNo } }
   */
  payStatus: (orderId) => {
    return request.get(`/user/pay/status/${orderId}`)
  },

  /**
   * 支付宝同步回跳验签
   * @param {Object} params 支付宝回跳携带的参数
   * @returns {Promise} { code, data: { payStatus, payNo } }
   */
  verifyReturn: (params) => {
    return request.post('/user/pay/alipay/verify', params)
  }
}
