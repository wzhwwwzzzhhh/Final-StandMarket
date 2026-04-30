<template>
  <div class="upload-test">
    <h1>文件上传测试</h1>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>上传图片到阿里云OSS</span>
        </div>
      </template>
      <div class="upload-container">
        <file-upload v-model="imageUrl" @upload-success="handleUploadSuccess" @upload-error="handleUploadError" />
        <div v-if="imageUrl" class="upload-result">
          <h3>上传成功！</h3>
          <p>图片URL：{{ imageUrl }}</p>
          <img :src="imageUrl" class="preview-image" alt="预览" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import FileUpload from '../components/FileUpload.vue'

const imageUrl = ref('')

const handleUploadSuccess = (url) => {
  console.log('上传成功，图片URL：', url)
  // 可以在这里处理上传成功后的逻辑，比如保存到数据库等
}

const handleUploadError = (error) => {
  console.error('上传失败：', error)
  // 可以在这里处理上传失败后的逻辑
}
</script>

<style scoped>
.upload-test {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-container {
  margin-top: 20px;
}

.upload-result {
  margin-top: 20px;
  padding: 15px;
  background-color: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
}

.preview-image {
  max-width: 300px;
  max-height: 300px;
  margin-top: 10px;
  border-radius: 4px;
}
</style>
