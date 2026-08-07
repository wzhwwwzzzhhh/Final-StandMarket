import axios from 'axios'
import router from '../router'

// 统一 axios 实例：注入管理端 token，统一处理 401
const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器：注入 Authorization
request.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => {
  return Promise.reject(error)
})

// 响应拦截器：401 统一跳转登录页
request.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    localStorage.removeItem('admin_token')
    localStorage.removeItem('adminInfo')
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
  }
  return Promise.reject(error)
})

export default request
