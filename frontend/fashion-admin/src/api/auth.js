import request from '../utils/request'

// 认证相关 API
export const authApi = {
  // 登录
  login: (data) => {
    return request.post('/admin/employee/login', data)
  },

  // 退出登录（管理端为服务端无状态 token，前端清除即可）
  logout: () => {
    return Promise.resolve({ data: { code: 1, msg: '退出成功' } })
  }
}
