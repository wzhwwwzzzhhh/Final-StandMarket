<template>
  <div class="profile-edit-container">
    <!-- 顶部导航 -->
    <div class="edit-header">
      <el-button type="text" @click="goBack" class="back-button">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </el-button>
      <h2 class="profile-edit-title">编辑个人资料</h2>
      <div class="header-placeholder"></div>
    </div>
    
    <div class="edit-content">
      <el-form :model="form" :rules="rules" ref="form" label-position="top" class="profile-form">
        <!-- 头像上传 -->
        <el-form-item class="form-item avatar-item">
          <label class="form-label">头像</label>
          <div class="avatar-uploader">
            <div class="avatar-preview">
              <el-avatar :size="120" class="avatar" :src="form.avatar">{{ userInitial }}</el-avatar>
              <div class="avatar-overlay">
                <el-icon><Camera /></el-icon>
                <span>更换头像</span>
              </div>
            </div>
            <file-upload v-model="form.avatar" @upload-success="handleUploadSuccess" @upload-error="handleUploadError" :maxSize="2" class="upload-input" />
          </div>
        </el-form-item>
        
        <!-- 名称 -->
        <el-form-item prop="name" class="form-item">
          <label class="form-label">名称</label>
          <el-input v-model="form.name" placeholder="请输入名称" class="custom-input"></el-input>
        </el-form-item>
        
        <!-- 性别 -->
        <el-form-item prop="sex" class="form-item">
          <label class="form-label">性别</label>
          <el-radio-group v-model="form.sex" class="radio-group">
            <el-radio label="男" class="radio-option">男</el-radio>
            <el-radio label="女" class="radio-option">女</el-radio>
            <el-radio label="其他" class="radio-option">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        
        <!-- 手机号（不可修改） -->
        <el-form-item class="form-item">
          <label class="form-label">手机号</label>
          <el-input v-model="form.phone" disabled class="custom-input disabled-input"></el-input>
        </el-form-item>
      </el-form>
      
      <!-- 保存按钮 -->
      <div class="form-actions">
        <el-button @click="goBack" class="cancel-button">取消</el-button>
        <el-button type="primary" @click="submitForm" class="save-button" :loading="loading">
          <template v-if="!loading">
            <el-icon><Check /></el-icon>
            <span>保存</span>
          </template>
          <template v-else>保存中...</template>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { userApi } from '@/api/user'
import FileUpload from '@/components/FileUpload.vue'
import { ArrowLeft, Camera, Check } from '@element-plus/icons-vue'

export default {
  name: 'ProfileEdit',
  components: {
    FileUpload,
    ArrowLeft,
    Camera,
    Check
  },
  data() {
    return {
      form: {
        name: '',
        sex: '',
        phone: '',
        avatar: ''
      },
      userInitial: '管',
      loading: false,
      rules: {
        name: [
          { required: true, message: '请输入名称', trigger: 'blur' },
          { min: 2, max: 20, message: '名称长度在 2 到 20 个字符', trigger: 'blur' }
        ],
        sex: [
          { required: true, message: '请选择性别', trigger: 'change' }
        ]
      }
    }
  },
  mounted() {
    this.loadUserInfo()
  },
  methods: {
    async loadUserInfo() {
      try {
        // 调用后端API获取用户信息
        const response = await userApi.getUserInfo()
        if (response.data.code === 1) {
          const userInfo = response.data.data
          this.form.name = userInfo.name || ''
          this.form.sex = userInfo.sex || ''
          this.form.phone = userInfo.phone || ''
          this.form.avatar = userInfo.avatar || ''
          // 设置用户首字母
          if (this.form.name) {
            this.userInitial = this.form.name.charAt(0).toUpperCase()
          }
        } else {
          this.$message.error(response.data.msg || '获取用户信息失败')
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        this.$message.error('网络错误，请稍后重试')
      }
    },
    // 处理上传成功
    handleUploadSuccess(url) {
      console.log('头像上传成功:', url)
      this.form.avatar = url
    },
    // 处理上传失败
    handleUploadError(error) {
      console.error('头像上传失败:', error)
      this.$message.error('头像上传失败')
    },

    async submitForm() {
      this.$refs.form.validate(async (valid) => {
        if (valid) {
          this.loading = true
          try {
            // 准备用户信息数据
            const userInfo = {
              name: this.form.name,
              sex: this.form.sex,
              avatar: this.form.avatar
            }
            
            // 调用后端API更新用户信息
            const response = await userApi.updateUserInfo(userInfo)
            if (response.data.code === 1) {
              // 更新localStorage中的用户信息
              const userInfoStr = localStorage.getItem('userInfo')
              let localUserInfo = {}
              if (userInfoStr) {
                localUserInfo = JSON.parse(userInfoStr)
              }
              
              // 更新本地用户信息
              localUserInfo.username = this.form.name
              localUserInfo.name = this.form.name
              localUserInfo.sex = this.form.sex
              localUserInfo.avatar = this.form.avatar
              
              // 保存回localStorage
              localStorage.setItem('userInfo', JSON.stringify(localUserInfo))
              
              // 通知App组件更新状态
              if (this.$root && this.$root.initUserStatus) {
                this.$root.initUserStatus()
              }
              
              this.$message.success('保存成功')
              this.goBack()
            } else {
              this.$message.error(response.data.msg || '更新失败')
            }
          } catch (error) {
            console.error('更新用户信息失败:', error)
            this.$message.error('网络错误，请稍后重试')
          } finally {
            this.loading = false
          }
        } else {
          return false
        }
      })
    },
    goBack() {
      this.$router.back()
    }
  }
}
</script>

<style scoped>
/* 全局动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
  }
}

.profile-edit-container {
  max-width: 1200px;
  margin: 0 auto;
  min-height: 100vh;
  background: #f8f9fa;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 顶部导航 */
.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 30px;
  background: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  margin-bottom: 30px;
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  font-size: 16px;
  color: #333;
  font-weight: 500;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 20px;
}

.back-button:hover {
  background: #f0f2f5;
  color: #667eea;
  transform: translateX(-5px);
}

.profile-edit-title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.header-placeholder {
  width: 100px;
}

/* 编辑内容 */
.edit-content {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 30px 40px;
  animation: fadeIn 0.8s ease-out;
}

.profile-form {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  padding: 40px;
  margin-bottom: 30px;
  transition: all 0.3s ease;
}

.profile-form:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.2);
}

.form-item {
  margin-bottom: 30px;
}

.form-label {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  display: block;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 头像上传 */
.avatar-item {
  text-align: center;
  margin-bottom: 40px;
}

.avatar-uploader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.avatar-preview {
  position: relative;
  cursor: pointer;
  transition: all 0.3s ease;
}

.avatar-preview:hover {
  transform: scale(1.05);
}

.avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-size: 48px;
  font-weight: bold;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  opacity: 0;
  transition: all 0.3s ease;
}

.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}

.avatar-overlay .el-icon {
  font-size: 24px;
  margin-bottom: 8px;
}

.avatar-overlay span {
  font-size: 14px;
  font-weight: 500;
}

.upload-input {
  width: 100%;
  max-width: 300px;
}

/* 自定义输入框 */
.custom-input {
  border-radius: 12px;
  border: 2px solid #f0f0f0;
  padding: 15px 20px;
  font-size: 16px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}

.custom-input:hover {
  border-color: #e0e0e0;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}

.custom-input:focus {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.disabled-input {
  background: #f8f9fa;
  color: #999;
  cursor: not-allowed;
}

/* 性别选择 */
.radio-group {
  display: flex;
  gap: 30px;
  flex-wrap: wrap;
}

.radio-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-radius: 25px;
  border: 2px solid #f0f0f0;
  transition: all 0.3s ease;
  cursor: pointer;
  font-size: 16px;
  font-weight: 500;
}

.radio-option:hover {
  border-color: #667eea;
  background: #f8f9fa;
}

.radio-option.is-checked {
  border-color: #667eea;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

/* 表单操作 */
.form-actions {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 40px;
  padding-top: 30px;
  border-top: 1px solid #f0f0f0;
}

.cancel-button {
  width: 150px;
  height: 50px;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  border: 2px solid #e0e0e0;
  background: #fff;
}

.cancel-button:hover {
  border-color: #667eea;
  color: #667eea;
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.2);
}

.save-button {
  width: 150px;
  height: 50px;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.save-button:hover:not(:loading) {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .edit-header {
    padding: 15px 20px;
  }
  
  .profile-edit-title {
    font-size: 18px;
  }
  
  .edit-content {
    padding: 0 20px 30px;
  }
  
  .profile-form {
    padding: 30px;
  }
  
  .form-item {
    margin-bottom: 25px;
  }
  
  .radio-group {
    gap: 20px;
  }
  
  .radio-option {
    padding: 10px 20px;
    font-size: 14px;
  }
  
  .form-actions {
    flex-direction: column;
    align-items: center;
    gap: 15px;
  }
  
  .cancel-button,
  .save-button {
    width: 200px;
  }
}

@media (max-width: 480px) {
  .edit-header {
    padding: 12px 15px;
  }
  
  .back-button {
    font-size: 14px;
    padding: 6px 12px;
  }
  
  .profile-edit-title {
    font-size: 16px;
  }
  
  .header-placeholder {
    width: 80px;
  }
  
  .profile-form {
    padding: 20px;
  }
  
  .avatar {
    size: 100px;
    font-size: 40px;
  }
  
  .custom-input {
    padding: 12px 16px;
    font-size: 14px;
  }
  
  .radio-group {
    flex-direction: column;
    gap: 10px;
  }
  
  .radio-option {
    width: 100%;
    justify-content: center;
  }
  
  .cancel-button,
  .save-button {
    width: 100%;
    height: 48px;
    font-size: 16px;
  }
}
</style>