import request from '../utils/request'

// 用户相关API
export const userApi = {
  // 获取用户列表
  getUserList: (params) => {
    return request.get('/admin/user/page', { params })
  },
  
  // 获取用户详情
  getUserById: (id) => {
    return request.get(`/admin/user/getById`, { params: { id } })
  },
  
  // 添加用户
  addUser: (data) => {
    return request.post('/admin/user', data)
  },
  
  // 修改用户
  updateUser: (data) => {
    return request.put('/admin/user', data)
  },
  
  // 删除用户
  deleteUser: (id) => {
    return request.delete('/admin/user', { params: { id } })
  }
}
