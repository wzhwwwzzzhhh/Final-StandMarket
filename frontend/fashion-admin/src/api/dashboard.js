import request from '../utils/request'

export const dashboardApi = {
  // 销售总览
  getSales: () => {
    return request.get('/admin/statistics/sales')
  },

  // 销售趋势
  getTrend: (days) => {
    return request.get('/admin/statistics/trend', { params: { days } })
  },

  // 分类分布
  getCategory: () => {
    return request.get('/admin/statistics/category-distribution')
  },

  // 最近订单
  getRecentOrders: (params) => {
    return request.get('/admin/statistics/recent-orders', { params })
  }
}
