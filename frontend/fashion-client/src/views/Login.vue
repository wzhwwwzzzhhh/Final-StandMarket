<template>
  <div class="login-container">
    <div class="login-form-wrapper">
      <div class="brand-section">
        <h1 class="brand-name">
          <span class="brand-stand">STAND</span>
          <span class="brand-sep">/</span>
          <span class="brand-market">MARKET</span>
        </h1>
        <p class="brand-tagline">// AUTHENTICATION_REQUIRED</p>
      </div>
      
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
        
        <el-form-item v-if="activeTab === 'password'" prop="password" class="form-item">
          <el-input type="password" v-model="loginForm.password" placeholder="请输入密码" class="custom-input" prefix-icon="Lock">
          </el-input>
        </el-form-item>
        
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
              <span>登录</span>
              <span class="login-arrow">→</span>
            </template>
            <template v-else>登录中...</template>
          </el-button>
        </el-form-item>
        
        <div class="register-section">
          <span class="register-text">还没有账号？</span>
          <el-button type="text" @click="goRegister" class="register-button">注册新账号</el-button>
        </div>
        
        <div class="other-login">
          <div class="divider">
            <span class="divider-text">其他登录方式</span>
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
import { Phone, Lock, Message, ChatDotRound, Avatar, Position } from '@element-plus/icons-vue'

export default {
  name: 'Login',
  components: {
    Phone,
    Lock,
    Message,
    ChatDotRound,
    Avatar,
    Position
  },
  data() {
    return {
      activeTab: 'password',
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
    const savedPhone = localStorage.getItem('savedPhone')
    if (savedPhone) {
      this.loginForm.phone = savedPhone
      this.rememberMe = true
    }
  },
  methods: {
    handleTabClick() {
      this.$refs.loginForm.resetFields()
    },
    getSmsCode() {
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
      
      userApi.sendSmsCode(this.loginForm.phone).then(response => {
        if (response.data.code === 1) {
          this.$message.success('验证码发送成功')
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
            type: this.activeTab
          }
          
          if (this.activeTab === 'password') {
            loginData.password = this.loginForm.password
          } else {
            loginData.code = this.loginForm.code
          }
          
          userApi.login(loginData).then(response => {
            this.loading = false
            console.log('登录响应:', response)
            if (response.data && response.data.code === 1) {
              const { token, userInfo } = response.data.data || {}
              
              if (token && userInfo) {
                localStorage.setItem('token', token)
                localStorage.setItem('userInfo', JSON.stringify(userInfo))
                
                if (this.rememberMe) {
                  localStorage.setItem('savedPhone', this.loginForm.phone)
                } else {
                  localStorage.removeItem('savedPhone')
                }
                
                this.$message.success('登录成功')
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
          })
        } else {
          return false
        }
      })
    },
    goRegister() {
      this.$message.info('注册功能开发中')
    }
  }
}
</script>

<style scoped>
/* ============================================================
   LOGIN — 机能风登录页
   ============================================================ */

.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--bg-primary);
  position: relative;
  overflow: hidden;
}

.login-container::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: 
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent 2px,
      rgba(209, 0, 255, 0.03) 2px,
      rgba(209, 0, 255, 0.03) 4px
    );
  animation: scanline 8s linear infinite;
}

.login-form-wrapper {
  width: 420px;
  padding: 50px 48px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  z-index: 1;
  animation: floatIn 0.6s ease;
  position: relative;
}

/* === 品牌标识 === */
.brand-section {
  text-align: center;
  margin-bottom: 36px;
  animation: floatIn 0.6s ease 0.1s backwards;
}

.brand-name {
  margin: 0 0 12px 0;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
}

.brand-stand {
  font-family: var(--font-heading);
  font-size: 28px;
  font-weight: 900;
  color: var(--text-primary);
  letter-spacing: 0.08em;
}

.brand-sep {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--accent-purple);
}

.brand-market {
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
}

.brand-tagline {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
  margin: 0;
}

/* === 标签页 === */
.login-tabs {
  animation: floatIn 0.6s ease 0.15s backwards;
}

.custom-tabs :deep(.el-tabs__header) {
  margin: 0 0 28px 0;
}

.custom-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-subtle);
  height: 1px;
}

.custom-tabs :deep(.el-tabs__item) {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-tertiary);
  padding: 0 24px 12px;
  letter-spacing: 0.04em;
  height: auto;
  line-height: 1;
  transition: var(--transition-fast);
}

.custom-tabs :deep(.el-tabs__item:hover) {
  color: var(--text-secondary);
}

.custom-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent-purple);
}

.custom-tabs :deep(.el-tabs__active-bar) {
  background: var(--accent-purple);
  height: 2px;
}

/* === 表单 === */
.login-form {
  animation: floatIn 0.6s ease 0.2s backwards;
}

.form-item {
  margin-bottom: 20px;
}

.custom-input :deep(.el-input__wrapper) {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  box-shadow: none;
  padding: 12px 16px;
  transition: var(--transition-fast);
}

.custom-input :deep(.el-input__wrapper:hover) {
  border-color: var(--accent-purple);
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent-purple);
  box-shadow: none;
}

.custom-input :deep(.el-input__inner) {
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: 14px;
}

.custom-input :deep(.el-input__inner)::placeholder {
  color: var(--text-tertiary);
  font-family: var(--font-display);
  font-size: 12px;
}

.custom-input :deep(.el-input__prefix-inner) {
  color: var(--text-tertiary);
}

/* === 验证码按钮 === */
.code-button {
  background: var(--accent-purple) !important;
  color: #fff !important;
  border: none !important;
  font-family: var(--font-display) !important;
  font-size: 12px !important;
  font-weight: 700 !important;
  letter-spacing: 0.04em !important;
  padding: 0 16px !important;
  transition: var(--transition-fast);
}

.code-button:hover:not(:disabled) {
  box-shadow: 0 0 16px var(--accent-purple-dim);
}

.code-button.counting {
  background: var(--bg-surface) !important;
  color: var(--text-tertiary) !important;
}

/* === 表单选项 === */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.remember-checkbox {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.forgot-password {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
  transition: var(--transition-fast);
}

.forgot-password:hover {
  color: var(--accent-purple);
}

/* === 登录按钮 === */
.login-button {
  width: 100%;
  padding: 14px;
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  font-family: var(--font-heading) !important;
  font-weight: 800 !important;
  font-size: 15px !important;
  letter-spacing: 0.06em !important;
  transition: var(--transition-base);
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.login-button::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.login-button:hover:not(:disabled)::after {
  transform: translateX(100%);
}

.login-button:hover:not(:disabled) {
  box-shadow: 0 0 24px var(--accent-purple-dim);
}

.login-arrow {
  font-family: var(--font-display);
  font-size: 16px;
  transition: transform 0.3s;
}

.login-button:hover .login-arrow {
  transform: translateX(4px);
}

/* === 注册部分 === */
.register-section {
  text-align: center;
  margin: 24px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.register-text {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.register-button {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-purple);
  font-weight: 700;
  padding: 0;
}

.register-button:hover {
  color: var(--accent-lime);
}

/* === 其他登录方式 === */
.other-login {
  margin-top: 32px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-subtle);
}

.divider-text {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.social-login {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.social-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-family: var(--font-display) !important;
  font-size: 10px !important;
  color: var(--text-tertiary) !important;
  padding: 12px 20px !important;
  border: 1px solid var(--border-subtle) !important;
  transition: var(--transition-fast);
}

.social-button:hover {
  border-color: var(--accent-purple) !important;
  color: var(--accent-purple) !important;
}

.social-button .el-icon {
  font-size: 20px;
}

/* === 响应式 === */
@media (max-width: 480px) {
  .login-form-wrapper {
    width: 100%;
    margin: 0 16px;
    padding: 40px 24px;
  }

  .brand-stand {
    font-size: 24px;
  }
}
</style>