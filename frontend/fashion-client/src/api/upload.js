import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 30000, // 请求超时时间，上传文件需要更长时间
  headers: {
    'Content-Type': 'multipart/form-data'
  }
})

// 请求拦截器，添加token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
}, error => {
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

// 文件上传相关API
export const uploadApi = {
  // 上传文件到阿里云OSS
  uploadFile: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/upload/oss', formData)
  },
  
  // 获取OSS上传签名（如果需要）
  getOssSignature: () => {
    return api.get('/upload/oss/signature')
  }
}
