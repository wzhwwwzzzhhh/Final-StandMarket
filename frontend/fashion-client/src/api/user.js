import api from '@/utils/request'

// 用户相关API
export const userApi = {
  // 用户登录
  login: (data) => {
    return api.post('/user/login', data, { skipAuthRedirect: true })
  },

  // 发送验证码
  sendSmsCode: (phone) => {
    return api.post('/user/sms-code', phone, { skipAuthRedirect: true })
  },

  // 用户注册
  register: (data) => {
    return api.post('/user/register', data, { skipAuthRedirect: true })
  },

  // 获取用户信息
  getUserInfo: () => {
    return api.get('/user/me')
  },

  // 更新用户信息
  updateUserInfo: (userInfo) => {
    return api.put('/user', userInfo)
  },

  // 修改密码
  changePassword: (oldPassword, newPassword) => {
    return api.put('/user/password', { oldPassword, newPassword })
  },

  // 退出登录
  logout: () => {
    return api.post('/user/logout')
  }
}
