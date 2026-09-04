import api from '@/utils/request'

// 文件上传相关API
export const uploadApi = {
  // 上传文件到阿里云OSS
  uploadFile: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/upload/oss', formData, { timeout: 30000 })
  },
  
  // 获取OSS上传签名（如果需要）
  getOssSignature: () => {
    return api.get('/upload/oss/signature')
  }
}
