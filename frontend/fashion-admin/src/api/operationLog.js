import request from '../utils/request'

export const operationLogApi = {
  getPage: (params) => request.get('/admin/operationLog/page', { params })
}