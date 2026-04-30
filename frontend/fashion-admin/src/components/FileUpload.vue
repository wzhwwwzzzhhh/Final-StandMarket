<template>
  <div class="file-upload">
    <el-upload
      class="avatar-uploader"
      :action="uploadUrl"
      :show-file-list="false"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :before-upload="beforeUpload"
      :headers="headers"
      name="file"
    >
      <img v-if="imageUrl" :src="imageUrl" class="avatar" />
      <div v-else class="avatar-uploader-icon">
        <i class="el-icon-plus"></i>
      </div>
    </el-upload>
    <div v-if="uploading" class="uploading-message">{{ uploadingMessage }}</div>
  </div>
</template>

<script>
export default {
  name: 'FileUpload',
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    maxSize: {
      type: Number,
      default: 5 // 5MB
    },
    accept: {
      type: String,
      default: 'image/*'
    }
  },
  data() {
    return {
      imageUrl: this.modelValue,
      uploading: false,
      uploadingMessage: ''
    }
  },
  computed: {
    // 计算上传URL
    uploadUrl() {
      return '/api/upload/oss'
    },
    // 计算请求头，添加token
    headers() {
      const token = localStorage.getItem('token')
      return {
        Authorization: token ? `Bearer ${token}` : ''
      }
    }
  },
  watch: {
    modelValue: {
      handler(newValue) {
        this.imageUrl = newValue
      },
      immediate: true
    }
  },
  methods: {
    // 上传前校验
    beforeUpload(file) {
      // 校验文件类型
      const acceptTypes = this.accept.split(',')
      let isAccept = false
      
      for (const type of acceptTypes) {
        const trimmedType = type.trim()
        if (trimmedType === '*' || trimmedType === file.type) {
          isAccept = true
          break
        }
        if (trimmedType.endsWith('/*')) {
          const baseType = trimmedType.substring(0, trimmedType.length - 2)
          if (file.type.startsWith(baseType + '/')) {
            isAccept = true
            break
          }
        }
      }
      
      if (!isAccept) {
        this.$message.error(`只允许上传 ${this.accept} 类型的文件`)
        return false
      }
      
      // 校验文件大小
      const fileSize = file.size / 1024 / 1024 // 转换为MB
      if (fileSize > this.maxSize) {
        this.$message.error(`文件大小不能超过 ${this.maxSize}MB`)
        return false
      }
      
      this.uploading = true
      this.uploadingMessage = '上传中，请稍候...'
      return true
    },
    
    // 上传成功处理
    handleUploadSuccess(response) {
      this.uploading = false
      
      console.log('上传响应:', response)
      
      // el-upload的on-success回调直接接收后端返回的数据
      const res = response
      
      console.log('处理后的响应:', res)
      console.log('res.code:', res.code)
      console.log('res.data:', res.data)
      
      if (res.code === 1 && res.data) {
        this.imageUrl = res.data
        this.$emit('update:modelValue', res.data)
        this.$emit('upload-success', res.data)
        this.$message.success('上传成功')
      } else {
        this.$message.error('上传失败：' + (res.msg || '未知错误'))
        this.$emit('upload-error', res)
      }
    },
    
    // 上传失败处理
    handleUploadError(error) {
      this.uploading = false
      this.$message.error('上传失败：' + (error.message || '网络错误'))
      this.$emit('upload-error', error)
    }
  }
}
</script>

<style scoped>
.file-upload {
  position: relative;
}

.avatar-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}

.avatar-uploader:hover {
  border-color: #409eff;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 128px;
  height: 128px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar {
  width: 128px;
  height: 128px;
  display: block;
  object-fit: cover;
}
</style>
