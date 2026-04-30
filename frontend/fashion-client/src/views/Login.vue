<template>
  <div class="login-container">
    <!-- 装饰元素 -->
    <div class="decorative-element top-left"></div>
    <div class="decorative-element bottom-right"></div>
    <div class="decorative-element top-right"></div>
    <div class="decorative-element bottom-left"></div>
    
    <div class="login-form-wrapper">
      <!-- 品牌标识 -->
      <div class="brand-logo">
        <h1 class="brand-name">时尚商城</h1>
        <p class="brand-tagline">让时尚触手可及</p>
      </div>
      
      <!-- 登录方式切换 -->
      <div class="login-tabs">
        <el-tabs v-model="activeTab" @tab-click="handleTabClick" class="custom-tabs">
          <el-tab-pane label="密码登录" name="password"></el-tab-pane>
          <el-tab-pane label="短信登录" name="sms"></el-tab-pane>
        </el-tabs>
      </div>
      
      <el-form :model="loginForm" :rules="loginRules" ref="loginForm" label-position="top" class="login-form">
        <el-form-item prop="phone" class="form-item">
          <el-input v-model="loginForm.phone" placeholder="请输入手机号" class="custom-input" prefix-icon="Phone">
          </el-input>
        </el-form-item>
        
        <!-- 密码登录 -->
        <el-form-item v-if="activeTab === 'password'" prop="password" class="form-item">
          <el-input type="password" v-model="loginForm.password" placeholder="请输入密码" class="custom-input" prefix-icon="Lock">
          </el-input>
        </el-form-item>
        
        <!-- 短信登录 -->
        <el-form-item v-else prop="code" class="form-item">
          <el-input v-model="loginForm.code" placeholder="请输入验证码" class="custom-input" prefix-icon="Message">
            <template #append>
              <el-button @click="getSmsCode" :disabled="countdown > 0" class="code-button" :class="{ 'counting': countdown > 0 }">
                {{ countdown > 0 ? `${countdown}秒` : '获取验证码' }}
              </el-button>
            </template>
          </el-input>
        </el-form-item>
        
        <div class="form-options">
          <el-checkbox v-model="rememberMe" class="remember-checkbox">记住我</el-checkbox>
          <el-link type="primary" :underline="false" class="forgot-password">忘记密码？</el-link>
        </div>
        
        <el-form-item class="form-item">
          <el-button type="primary" @click="submitForm" class="login-button" :loading="loading">
            <template v-if="!loading">
              <el-icon class="login-icon"><Check /></el-icon>
              <span>登录</span>
            </template>
            <template v-else>登录中...</template>
          </el-button>
        </el-form-item>
        
        <div class="register-section">
          <span class="register-text">还没有账号？</span>
          <el-button type="text" @click="goRegister" class="register-button">注册新账号</el-button>
        </div>
        
        <!-- 其他登录方式 -->
        <div class="other-login">
          <div class="divider">
            <span>其他登录方式</span>
          </div>
          <div class="social-login">
            <el-button type="text" class="social-button wechat">
              <el-icon><ChatDotRound /></el-icon>
              <span>微信</span>
            </el-button>
            <el-button type="text" class="social-button qq">
              <el-icon><Avatar /></el-icon>
              <span>QQ</span>
            </el-button>
            <el-button type="text" class="social-button weibo">
              <el-icon><Position /></el-icon>
              <span>微博</span>
            </el-button>
          </div>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script>
import { userApi } from '@/api/user'
import { Phone, Lock, Message, Check, ChatDotRound, Avatar, Position } from '@element-plus/icons-vue'

export default {
  name: 'Login',
  components: {
    Phone,
    Lock,
    Message,
    Check,
    ChatDotRound,
    Avatar,
    Position
  },
  data() {
    return {
      activeTab: 'password', // 默认密码登录
      loginForm: {
        phone: '',
        password: '',
        code: ''
      },
      rememberMe: false,
      loading: false,
      countdown: 0,
      loginRules: {
        phone: [
          { required: true, message: '请输入手机号', trigger: 'blur' },
          { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ],
        code: [
          { required: true, message: '请输入验证码', trigger: 'blur' },
          { length: 6, message: '验证码长度为6位', trigger: 'blur' }
        ]
      }
    }
  },
  mounted() {
    // 从localStorage中获取记住的手机号
    const savedPhone = localStorage.getItem('savedPhone')
    if (savedPhone) {
      this.loginForm.phone = savedPhone
      this.rememberMe = true
    }
  },
  methods: {
    handleTabClick() {
      // 切换登录方式时重置表单
      this.$refs.loginForm.resetFields()
    },
    getSmsCode() {
      // 验证手机号
      const phoneRule = this.loginRules.phone
      for (const rule of phoneRule) {
        if (rule.required && !this.loginForm.phone) {
          this.$message.error(rule.message)
          return
        }
        if (rule.pattern && !rule.pattern.test(this.loginForm.phone)) {
          this.$message.error(rule.message)
          return
        }
      }
      
      // 发送验证码
      userApi.sendSmsCode(this.loginForm.phone).then(response => {
        if (response.data.code === 1) {
          this.$message.success('验证码发送成功')
          // 开始倒计时
          this.countdown = 60
          const timer = setInterval(() => {
            this.countdown--
            if (this.countdown <= 0) {
              clearInterval(timer)
            }
          }, 1000)
        } else {
          this.$message.error(response.data.msg || '验证码发送失败')
        }
      }).catch(error => {
        this.$message.error('网络错误，请稍后重试')
        console.error('发送验证码失败:', error)
      })
    },
    submitForm() {
      this.$refs.loginForm.validate((valid) => {
        if (valid) {
          this.loading = true
          const loginData = {
            phone: this.loginForm.phone,
            type: this.activeTab // 登录类型：password或sms
          }
          
          if (this.activeTab === 'password') {
            loginData.password = this.loginForm.password
          } else {
            loginData.code = this.loginForm.code
          }
          
          console.log('发送的登录请求参数:', loginData)
          userApi.login(loginData).then(response => {
            this.loading = false
            console.log('登录响应:', response)
            if (response.data && response.data.code === 1) {
              // 登录成功
              console.log('登录响应data:', response.data)
              console.log('登录响应data.data:', response.data.data)
              const { token, userInfo } = response.data.data || {}
              console.log('获取的token:', token)
              console.log('获取的userInfo:', userInfo)
              
              if (token && userInfo) {
                // 保存token和用户信息到localStorage
                localStorage.setItem('token', token)
                localStorage.setItem('userInfo', JSON.stringify(userInfo))
                console.log('token已保存到localStorage:', localStorage.getItem('token'))
                console.log('localStorage中的所有数据:', localStorage)
                
                // 记住手机号
                if (this.rememberMe) {
                  localStorage.setItem('savedPhone', this.loginForm.phone)
                } else {
                  localStorage.removeItem('savedPhone')
                }
                
                this.$message.success('登录成功')
                // 通知App组件更新用户状态
                if (this.$root && this.$root.initUserStatus) {
                  this.$root.initUserStatus()
                }
                this.$router.push('/')
              } else {
                this.$message.error('登录失败：未返回token或用户信息')
              }
            } else {
              this.$message.error(response.data?.msg || '登录失败')
            }
          }).catch(error => {
            this.loading = false
            this.$message.error('网络错误，请稍后重试')
            console.error('登录失败:', error)
            console.error('错误响应:', error.response)
          })
        } else {
          console.log('登录表单验证失败')
          return false
        }
      })
    },
    goRegister() {
      console.log('跳转到注册页面')
      // 这里可以跳转到注册页面
      this.$message.info('注册功能开发中')
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

@keyframes float {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-size: cover;
  background-position: center;
  position: relative;
  overflow: hidden;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 装饰元素 */
.decorative-element {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  animation: float 6s ease-in-out infinite;
  z-index: 0;
}

.top-left {
  top: 10%;
  left: 10%;
  width: 200px;
  height: 200px;
  animation-delay: 0s;
}

.bottom-right {
  bottom: 10%;
  right: 10%;
  width: 250px;
  height: 250px;
  animation-delay: 2s;
}

.top-right {
  top: 20%;
  right: 15%;
  width: 150px;
  height: 150px;
  animation-delay: 1s;
}

.bottom-left {
  bottom: 20%;
  left: 15%;
  width: 180px;
  height: 180px;
  animation-delay: 3s;
}

.login-form-wrapper {
  width: 420px;
  padding: 50px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(10px);
  z-index: 1;
  animation: fadeIn 0.8s ease-out;
  transition: all 0.3s ease;
}

.login-form-wrapper:hover {
  box-shadow: 0 25px 70px rgba(0, 0, 0, 0.25);
  transform: translateY(-5px);
}

/* 品牌标识 */
.brand-logo {
  text-align: center;
  margin-bottom: 40px;
  animation: fadeIn 1s ease-out 0.2s both;
}

.brand-name {
  font-size: 32px;
  font-weight: bold;
  color: #333;
  margin: 0 0 10px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-tagline {
  font-size: 16px;
  color: #666;
  margin: 0;
  font-weight: 300;
}

/* 标签页样式 */
.custom-tabs {
  margin-bottom: 30px;
  animation: fadeIn 1s ease-out 0.4s both;
}

.custom-tabs .el-tabs__header {
  margin: 0 0 30px 0;
}

.custom-tabs .el-tabs__nav {
  justify-content: center;
  border-bottom: 2px solid #f0f0f0;
}

.custom-tabs .el-tabs__item {
  font-size: 16px;
  font-weight: 600;
  color: #666;
  padding: 15px 30px;
  margin: 0 20px;
  transition: all 0.3s ease;
  position: relative;
}

.custom-tabs .el-tabs__item:hover {
  color: #667eea;
}

.custom-tabs .el-tabs__active-bar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  height: 3px;
  border-radius: 3px;
}

.custom-tabs .el-tabs__item.is-active {
  color: #667eea;
  font-weight: bold;
}

/* 表单样式 */
.login-form {
  animation: fadeIn 1s ease-out 0.6s both;
}

.form-item {
  margin-bottom: 25px;
}

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

.custom-input .el-input__prefix {
  color: #999;
  font-size: 18px;
  transition: color 0.3s ease;
}

.custom-input:focus .el-input__prefix {
  color: #667eea;
}

/* 验证码按钮 */
.code-button {
  border-radius: 8px;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
}

.code-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.code-button.counting {
  background: #999;
  cursor: not-allowed;
}

/* 表单选项 */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  animation: fadeIn 1s ease-out 0.8s both;
}

.remember-checkbox .el-checkbox__label {
  font-size: 14px;
  color: #666;
  transition: color 0.3s ease;
}

.remember-checkbox .el-checkbox__input.is-checked .el-checkbox__inner {
  background-color: #667eea;
  border-color: #667eea;
}

.forgot-password {
  font-size: 14px;
  color: #667eea;
  font-weight: 500;
  transition: all 0.3s ease;
}

.forgot-password:hover {
  color: #764ba2;
  text-decoration: underline;
}

/* 登录按钮 */
.login-button {
  width: 100%;
  padding: 15px;
  font-size: 18px;
  font-weight: bold;
  border-radius: 12px;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  animation: fadeIn 1s ease-out 1s both;
}

.login-button:hover:not(:loading) {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.login-icon {
  font-size: 20px;
}

/* 注册部分 */
.register-section {
  text-align: center;
  margin: 30px 0;
  animation: fadeIn 1s ease-out 1.2s both;
}

.register-text {
  font-size: 14px;
  color: #666;
  margin-right: 10px;
}

.register-button {
  font-size: 14px;
  color: #667eea;
  font-weight: 600;
  transition: all 0.3s ease;
}

.register-button:hover {
  color: #764ba2;
  text-decoration: underline;
}

/* 其他登录方式 */
.other-login {
  animation: fadeIn 1s ease-out 1.4s both;
}

.divider {
  display: flex;
  align-items: center;
  margin: 30px 0;
  position: relative;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e0e0e0;
}

.divider span {
  padding: 0 20px;
  font-size: 14px;
  color: #999;
  white-space: nowrap;
}

.social-login {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-top: 30px;
}

.social-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  border-radius: 12px;
  transition: all 0.3s ease;
  background: #f8f9fa;
  min-width: 80px;
}

.social-button:hover {
  background: #e9ecef;
  transform: translateY(-5px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.social-button .el-icon {
  font-size: 24px;
  margin-bottom: 5px;
}

.social-button.wechat .el-icon {
  color: #07C160;
}

.social-button.qq .el-icon {
  color: #12B7F5;
}

.social-button.weibo .el-icon {
  color: #E6162D;
}

.social-button span {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-form-wrapper {
    width: 90%;
    max-width: 400px;
    padding: 40px;
  }
  
  .decorative-element {
    transform: scale(0.7);
  }
  
  .brand-name {
    font-size: 28px;
  }
  
  .custom-tabs .el-tabs__item {
    padding: 12px 20px;
    margin: 0 10px;
  }
  
  .social-login {
    gap: 30px;
  }
  
  .social-button {
    padding: 15px;
    min-width: 70px;
  }
}

@media (max-width: 480px) {
  .login-form-wrapper {
    width: 95%;
    padding: 30px;
  }
  
  .brand-name {
    font-size: 24px;
  }
  
  .brand-tagline {
    font-size: 14px;
  }
  
  .custom-input {
    padding: 12px 16px;
    font-size: 14px;
  }
  
  .login-button {
    padding: 12px;
    font-size: 16px;
  }
  
  .social-login {
    gap: 20px;
  }
  
  .social-button {
    padding: 12px;
    min-width: 60px;
  }
  
  .social-button .el-icon {
    font-size: 20px;
  }
  
  .social-button span {
    font-size: 12px;
  }
}
</style>
