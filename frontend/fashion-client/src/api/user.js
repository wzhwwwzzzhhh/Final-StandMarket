import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器，添加token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  console.log('从localStorage获取的token:', token)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
    console.log('添加到请求头的Authorization:', config.headers.Authorization)
  } else {
    console.log('localStorage中没有token')
    console.log('localStorage中的所有数据:', localStorage)
  }
  console.log('请求URL:', config.url)
  console.log('请求头:', config.headers)
  return config
}, error => {
  console.error('请求拦截器错误:', error)
  return Promise.reject(error)
})

// 响应拦截器，处理错误
api.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    // 未授权，跳转到登录页
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    window.location.href = '/login'
  }
  return Promise.reject(error)
})

// 用户相关API
export const userApi = {
  // 用户登录
  login: (data) => {
    return api.post('/user/login', data)
  },

  // 发送验证码
  sendSmsCode: (phone) => {
    return api.post('/user/sms-code', phone)
  },

  // 用户注册
  register: (data) => {
    return api.post('/user/register', data)
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
    return api.put('/user/password', null, { params: { oldPassword, newPassword } })
  },

  // 退出登录
  logout: () => {
    return api.post('/user/logout')
  }
}
