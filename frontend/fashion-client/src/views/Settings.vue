<template>
  <div class="settings-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="settings-title">
        <span>设置</span>
        <el-divider direction="horizontal"></el-divider>
      </h2>
    </div>
    
    <!-- 账号设置 -->
    <div class="settings-section" :style="{ animationDelay: '0s' }">
      <h3 class="section-title">
        <el-icon class="section-icon"><User /></el-icon>
        <span>账号设置</span>
      </h3>
      <div class="settings-item" @click="editProfile">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Avatar /></el-icon>
          <span class="settings-label">个人资料</span>
        </div>
        <div class="settings-value">
          <span>{{ userInfo.username || userInfo.name || '未设置' }}</span>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="settings-item" @click="editPhone">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Phone /></el-icon>
          <span class="settings-label">手机号</span>
        </div>
        <div class="settings-value">
          <span>{{ userInfo.phone || '未设置' }}</span>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
    
    <!-- 安全设置 -->
    <div class="settings-section" :style="{ animationDelay: '0.1s' }">
      <h3 class="section-title">
        <el-icon class="section-icon"><Lock /></el-icon>
        <span>安全设置</span>
      </h3>
      <div class="settings-item" @click="changePassword">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Key /></el-icon>
          <span class="settings-label">{{ hasPassword ? '修改密码' : '设置密码' }}</span>
        </div>
        <div class="settings-value">
          <span>{{ hasPassword ? '*******' : '未设置' }}</span>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="settings-item" @click="bindWechat">
        <div class="settings-item-left">
          <el-icon class="item-icon"><ChatLineRound /></el-icon>
          <span class="settings-label">绑定微信</span>
        </div>
        <div class="settings-value">
          <span>{{ isWechatBound ? '已绑定' : '未绑定' }}</span>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
    
    <!-- 通知设置 -->
    <div class="settings-section" :style="{ animationDelay: '0.2s' }">
      <h3 class="section-title">
        <el-icon class="section-icon"><Bell /></el-icon>
        <span>通知设置</span>
      </h3>
      <div class="settings-item">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Message /></el-icon>
          <span class="settings-label">消息通知</span>
        </div>
        <div class="settings-value">
          <el-switch v-model="notificationSettings.message" class="custom-switch" />
        </div>
      </div>
      <div class="settings-item">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Promotion /></el-icon>
          <span class="settings-label">活动通知</span>
        </div>
        <div class="settings-value">
          <el-switch v-model="notificationSettings.activity" class="custom-switch" />
        </div>
      </div>
      <div class="settings-item">
        <div class="settings-item-left">
          <el-icon class="item-icon"><ShoppingBag /></el-icon>
          <span class="settings-label">订单通知</span>
        </div>
        <div class="settings-value">
          <el-switch v-model="notificationSettings.order" class="custom-switch" />
        </div>
      </div>
    </div>
    
    <!-- 通用设置 -->
    <div class="settings-section" :style="{ animationDelay: '0.3s' }">
      <h3 class="section-title">
        <el-icon class="section-icon"><Setting /></el-icon>
        <span>通用设置</span>
      </h3>
      <div class="settings-item">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Delete /></el-icon>
          <span class="settings-label">清除缓存</span>
        </div>
        <div class="settings-value">
          <span>{{ cacheSize }}</span>
          <el-button type="primary" size="small" @click="clearCache" class="clear-cache-btn">清除</el-button>
        </div>
      </div>
      <div class="settings-item" @click="aboutUs">
        <div class="settings-item-left">
          <el-icon class="item-icon"><InfoFilled /></el-icon>
          <span class="settings-label">关于我们</span>
        </div>
        <div class="settings-value">
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="settings-item" @click="feedback">
        <div class="settings-item-left">
          <el-icon class="item-icon"><ChatDotRound /></el-icon>
          <span class="settings-label">意见反馈</span>
        </div>
        <div class="settings-value">
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
      <div class="settings-item" @click="privacyPolicy">
        <div class="settings-item-left">
          <el-icon class="item-icon"><Document /></el-icon>
          <span class="settings-label">隐私政策</span>
        </div>
        <div class="settings-value">
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
    
    <!-- 版本信息 -->
    <div class="settings-section" :style="{ animationDelay: '0.4s' }">
      <div class="settings-item">
        <div class="settings-item-left">
          <el-icon class="item-icon"><DataAnalysis /></el-icon>
          <span class="settings-label">版本信息</span>
        </div>
        <div class="settings-value">
          <span class="version-info">v1.0.0</span>
        </div>
      </div>
    </div>
    
    <!-- 退出登录按钮 -->
    <div class="logout-section" :style="{ animationDelay: '0.5s' }">
      <el-button type="danger" @click="logout" class="logout-btn">
        <el-icon><SwitchButton /></el-icon>
        <span>退出登录</span>
      </el-button>
    </div>
    
    <!-- 密码修改对话框 -->
    <el-dialog
      :title="hasPassword ? '修改密码' : '设置密码'"
      v-model="changePasswordDialogVisible"
      width="500px"
      class="password-dialog"
    >
      <el-form ref="passwordForm" :model="passwordForm" :rules="passwordRules" label-width="100px" class="password-form">
        <el-form-item label="旧密码" prop="oldPassword" v-if="hasPassword">
          <el-input type="password" v-model="passwordForm.oldPassword" placeholder="请输入旧密码" class="custom-input"></el-input>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input type="password" v-model="passwordForm.newPassword" placeholder="请输入新密码" class="custom-input"></el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input type="password" v-model="passwordForm.confirmPassword" placeholder="请确认新密码" class="custom-input"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="changePasswordDialogVisible = false" class="cancel-button">取消</el-button>
          <el-button type="primary" @click="submitPassword" class="confirm-button">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ArrowRight, User, Avatar, Phone, Lock, Key, ChatLineRound, Bell, Message, Promotion, ShoppingBag, Setting, Delete, InfoFilled, ChatDotRound, Document, DataAnalysis, SwitchButton } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'

export default {
  name: 'Settings',
  components: {
    ArrowRight,
    User,
    Avatar,
    Phone,
    Lock,
    Key,
    ChatLineRound,
    Bell,
    Message,
    Promotion,
    ShoppingBag,
    Setting,
    Delete,
    InfoFilled,
    ChatDotRound,
    Document,
    DataAnalysis,
    SwitchButton
  },
  data() {
    return {
      userInfo: {},
      hasPassword: false,
      isWechatBound: false,
      notificationSettings: {
        message: true,
        activity: true,
        order: true
      },
      cacheSize: '0.5MB',
      changePasswordDialogVisible: false,
      passwordForm: {
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      },
      passwordRules: {
        oldPassword: [
          { required: true, message: '请输入旧密码', trigger: 'blur' }
        ],
        newPassword: [
          { required: true, message: '请输入新密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
        ],
        confirmPassword: [
          { required: true, message: '请确认新密码', trigger: 'blur' },
          {
            validator: (rule, value, callback) => {
              if (value !== this.passwordForm.newPassword) {
                callback(new Error('两次输入密码不一致'))
              } else {
                callback()
              }
            },
            trigger: 'blur'
          }
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
          this.userInfo = userInfo
          // 检查是否有密码
          this.hasPassword = userInfo.password !== undefined && userInfo.password !== ''
        } else {
          // 从localStorage获取用户信息作为备份
          const userInfoStr = localStorage.getItem('userInfo')
          if (userInfoStr) {
            try {
              this.userInfo = JSON.parse(userInfoStr)
              this.hasPassword = this.userInfo.password !== undefined && this.userInfo.password !== ''
            } catch (error) {
              console.error('解析用户信息失败:', error)
            }
          }
        }
      } catch (error) {
        console.error('获取用户信息失败:', error)
        // 从localStorage获取用户信息作为备份
        const userInfoStr = localStorage.getItem('userInfo')
        if (userInfoStr) {
          try {
            this.userInfo = JSON.parse(userInfoStr)
            this.hasPassword = this.userInfo.password !== undefined && this.userInfo.password !== ''
          } catch (error) {
            console.error('解析用户信息失败:', error)
          }
        }
      }
    },
    editProfile() {
      this.$router.push('/profile/edit')
    },
    editPhone() {
      this.$message.info('修改手机号功能开发中')
    },
    changePassword() {
      this.changePasswordDialogVisible = true
      // 重置密码表单
      this.$refs.passwordForm && this.$refs.passwordForm.resetFields()
    },
    async submitPassword() {
      this.$refs.passwordForm.validate(async (valid) => {
        if (valid) {
          try {
            console.log('密码表单数据:', this.passwordForm)
            console.log('是否已有密码:', this.hasPassword)
            
            if (this.hasPassword) {
              // 修改密码
              console.log('调用修改密码接口')
              const response = await userApi.changePassword(this.passwordForm.oldPassword, this.passwordForm.newPassword)
              console.log('修改密码接口响应:', response)
              if (response.data.code === 1) {
                this.$message.success('密码修改成功')
                this.changePasswordDialogVisible = false
              } else {
                this.$message.error(response.data.msg || '密码修改失败')
              }
            } else {
              // 设置密码
              console.log('调用设置密码接口')
              const response = await userApi.updateUserInfo({
                password: this.passwordForm.newPassword
              })
              console.log('设置密码接口响应:', response)
              if (response.data.code === 1) {
                this.$message.success('密码设置成功')
                this.hasPassword = true
                this.changePasswordDialogVisible = false
              } else {
                this.$message.error(response.data.msg || '密码设置失败')
              }
            }
          } catch (error) {
            console.error('操作失败:', error)
            this.$message.error('网络错误，请稍后重试')
          }
        } else {
          return false
        }
      })
    },
    bindWechat() {
      this.$message.info('绑定微信功能开发中')
    },
    clearCache() {
      // 模拟清除缓存
      this.cacheSize = '0MB'
      this.$message.success('缓存已清除')
    },
    aboutUs() {
      this.$message.info('关于我们功能开发中')
    },
    feedback() {
      this.$message.info('意见反馈功能开发中')
    },
    privacyPolicy() {
      this.$message.info('隐私政策功能开发中')
    },
    logout() {
      // 清除localStorage中的数据
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      // 通知App组件更新状态
      if (this.$root && this.$root.initUserStatus) {
        this.$root.initUserStatus()
      }
      this.$message.success('已退出登录')
      this.$router.push('/login')
    }
  }
}
</script>

<style scoped>
/* 全局样式 */
.settings-container {
  min-height: 100vh;
  padding: 30px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
}

/* 页面标题 */
.page-header {
  max-width: 800px;
  margin: 0 auto 30px;
  text-align: center;
}

.settings-title {
  font-size: 28px;
  font-weight: bold;
  color: white;
  margin: 0;
  position: relative;
  display: inline-block;
}

.settings-title span {
  position: relative;
  z-index: 2;
}

.settings-title::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  width: 120px;
  height: 4px;
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border-radius: 2px;
  z-index: 1;
}

/* 设置区块 */
.settings-section {
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
  overflow: hidden;
  animation: fadeIn 0.5s ease-out;
  animation-fill-mode: both;
  opacity: 0;
  transform: translateY(20px);
}

/* 区块标题 */
.section-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: bold;
  margin: 0;
  padding: 18px 25px;
  background: linear-gradient(90deg, #f5f7fa 0%, #c3cfe2 100%);
  color: #333;
  border-bottom: 1px solid #e8e8e8;
}

.section-icon {
  margin-right: 10px;
  font-size: 18px;
  color: #409eff;
}

/* 设置项 */
.settings-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 25px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.settings-item:hover {
  background-color: #f9f9f9;
  transform: translateX(5px);
}

.settings-item:last-child {
  border-bottom: none;
}

/* 设置项左侧 */
.settings-item-left {
  display: flex;
  align-items: center;
  flex: 1;
}

.item-icon {
  margin-right: 15px;
  font-size: 20px;
  color: #409eff;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 8px;
  padding: 4px;
}

.settings-label {
  font-size: 15px;
  color: #333;
  font-weight: 500;
}

/* 设置项右侧 */
.settings-value {
  display: flex;
  align-items: center;
  gap: 15px;
}

.settings-value span {
  font-size: 14px;
  color: #999;
  transition: color 0.3s ease;
}

.settings-item:hover .settings-value span {
  color: #409eff;
}

.arrow-icon {
  font-size: 16px;
  color: #999;
  transition: all 0.3s ease;
}

.settings-item:hover .arrow-icon {
  color: #409eff;
  transform: translateX(5px);
}

/* 清除缓存按钮 */
.clear-cache-btn {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  border-radius: 20px;
  padding: 4px 16px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.clear-cache-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(79, 172, 254, 0.4);
}

/* 版本信息 */
.version-info {
  font-size: 14px;
  color: #409eff;
  font-weight: 500;
}

/* 退出登录按钮 */
.logout-section {
  text-align: center;
  margin-top: 40px;
  margin-bottom: 40px;
  animation: fadeIn 0.5s ease-out;
  animation-fill-mode: both;
  opacity: 0;
  transform: translateY(20px);
}

.logout-btn {
  width: 240px;
  height: 52px;
  border-radius: 26px;
  font-size: 16px;
  font-weight: 500;
  background: linear-gradient(90deg, #ff6b6b 0%, #ee5a6f 100%);
  border: none;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
}

.logout-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(255, 107, 107, 0.4);
}

.logout-btn el-icon {
  margin-right: 8px;
  font-size: 18px;
}

/* 自定义开关 */
.custom-switch .el-switch__core {
  border-radius: 15px;
  width: 50px;
  height: 28px;
}

.custom-switch .el-switch__core .el-switch__button {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.custom-switch .el-switch.is-checked .el-switch__core {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
}

/* 密码对话框 */
.password-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.password-dialog .el-dialog__header {
  background: linear-gradient(90deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 20px 24px;
  border-bottom: 1px solid #e8e8e8;
}

.password-dialog .el-dialog__title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.password-form {
  padding: 20px 0;
}

.custom-input {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.3s ease;
}

.custom-input:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.dialog-footer {
  text-align: right;
  padding: 10px 24px 20px;
  background: #f9f9f9;
  border-top: 1px solid #e8e8e8;
}

.cancel-button, .confirm-button {
  border-radius: 20px;
  padding: 6px 20px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.confirm-button {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  margin-left: 10px;
}

.confirm-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(79, 172, 254, 0.4);
}

/* 动画效果 */
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

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-container {
    padding: 20px 15px;
  }

  .settings-title {
    font-size: 24px;
  }

  .settings-section {
    border-radius: 12px;
  }

  .section-title {
    padding: 15px 20px;
    font-size: 14px;
  }

  .settings-item {
    padding: 15px 20px;
  }

  .item-icon {
    font-size: 18px;
    margin-right: 12px;
  }

  .settings-label {
    font-size: 14px;
  }

  .settings-value span {
    font-size: 13px;
  }

  .logout-btn {
    width: 200px;
    height: 48px;
    font-size: 15px;
  }

  .password-dialog {
    width: 90% !important;
  }
}

@media (max-width: 480px) {
  .settings-item-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
  }

  .item-icon {
    margin-right: 0;
  }

  .settings-value {
    flex-direction: column;
    align-items: flex-end;
    gap: 5px;
  }

  .logout-btn {
    width: 160px;
    height: 44px;
    font-size: 14px;
  }
}
</style>