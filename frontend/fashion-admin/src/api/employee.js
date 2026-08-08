import request from '../utils/request'

export const employeeApi = {
  getEmployeeList: (params) => {
    return request.get('/admin/employee/page', { params })
  },

  getEmployeeById: (id) => {
    return request.get('/admin/employee/getById', { params: { id } })
  },

  addEmployee: (data) => {
    return request.post('/admin/employee', data)
  },

  updateEmployee: (data) => {
    return request.put('/admin/employee', data)
  },

  deleteEmployee: (id) => {
    return request.delete('/admin/employee', { params: { id } })
  }
}
