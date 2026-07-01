import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const dashboardApi = {
  // 销售总览
  getSales: () => {
    return api.get('/admin/statistics/sales')
  },

  // 销售趋势
  getTrend: (days) => {
    return api.get('/admin/statistics/trend', { params: { days } })
  },

  // 分类分布
  getCategory: () => {
    return api.get('/admin/statistics/category-distribution')
  },

  // 最近订单
  getRecentOrders: (params) => {
    return api.get('/admin/statistics/recent-orders', { params })
  }
}
