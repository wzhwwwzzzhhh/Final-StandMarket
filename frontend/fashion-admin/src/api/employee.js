import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const employeeApi = {
  getEmployeeList: (params) => {
    return api.get('/admin/employee/page', { params })
  },

  getEmployeeById: (id) => {
    return api.get('/admin/employee/getById', { params: { id } })
  },

  addEmployee: (data) => {
    return api.post('/admin/employee', data)
  },

  updateEmployee: (data) => {
    return api.put('/admin/employee', data)
  },

  deleteEmployee: (id) => {
    return api.delete('/admin/employee', { params: { id } })
  }
}
