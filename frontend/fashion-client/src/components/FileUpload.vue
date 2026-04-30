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
      <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
    </el-upload>
    <el-message v-if="uploading" type="info" :message="uploadingMessage" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadApi } from '../api/upload'

const props = defineProps({
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
})

const emit = defineEmits(['update:modelValue', 'upload-success', 'upload-error'])

const imageUrl = ref(props.modelValue)
const uploading = ref(false)
const uploadingMessage = ref('')

// 计算上传URL
const uploadUrl = computed(() => {
  return '/api/upload/oss'
})

// 计算请求头，添加token
const headers = computed(() => {
  const token = localStorage.getItem('token')
  return {
    Authorization: token ? `Bearer ${token}` : ''
  }
})

// 上传前校验
const beforeUpload = (file) => {
  // 校验文件类型
  const acceptTypes = props.accept.split(',')
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
    ElMessage.error(`只允许上传 ${props.accept} 类型的文件`)
    return false
  }
  
  // 校验文件大小
  const fileSize = file.size / 1024 / 1024 // 转换为MB
  if (fileSize > props.maxSize) {
    ElMessage.error(`文件大小不能超过 ${props.maxSize}MB`)
    return false
  }
  
  uploading.value = true
  uploadingMessage.value = '上传中，请稍候...'
  return true
}

// 上传成功处理
const handleUploadSuccess = (response) => {
  uploading.value = false
  
  console.log('上传响应:', response)
  
  // 检查响应结构
  if (!response) {
    ElMessage.error('上传失败：响应为空')
    emit('upload-error', response)
    return
  }
  
  // 尝试不同的响应结构
  let res = response
  if (response.data) {
    res = response.data
    console.log('使用response.data作为响应:', res)
  }
  
  console.log('处理后的响应:', res)
  console.log('res.code:', res.code)
  console.log('res.data:', res.data)
  console.log('res.msg:', res.msg)
  
  // 检查响应状态
  if (res.code === 1) {
    if (res.data) {
      imageUrl.value = res.data
      emit('update:modelValue', res.data)
      emit('upload-success', res.data)
      ElMessage.success('上传成功')
    } else {
      ElMessage.error('上传失败：返回数据为空')
      emit('upload-error', res)
    }
  } else {
    ElMessage.error('上传失败：' + (res.msg || '未知错误'))
    emit('upload-error', res)
  }
}

// 上传失败处理
const handleUploadError = (error) => {
  uploading.value = false
  ElMessage.error('上传失败：' + (error.message || '网络错误'))
  emit('upload-error', error)
}

// 暴露方法
defineExpose({
  uploadImage: (file) => {
    // 手动触发上传
    return uploadApi.uploadFile(file)
  }
})
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
