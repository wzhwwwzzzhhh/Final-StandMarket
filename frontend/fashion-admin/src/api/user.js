import axios from 'axios'

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 用户相关API
export const userApi = {
  // 获取用户列表
  getUserList: (params) => {
    return api.get('/admin/user/page', { params })
  },
  
  // 获取用户详情
  getUserById: (id) => {
    return api.get(`/admin/user/getById`, { params: { id } })
  },
  
  // 添加用户
  addUser: (data) => {
    return api.post('/admin/user', data)
  },
  
  // 修改用户
  updateUser: (data) => {
    return api.put('/admin/user', data)
  },
  
  // 删除用户
  deleteUser: (id) => {
    return api.delete('/admin/user', { params: { id } })
  }
}
