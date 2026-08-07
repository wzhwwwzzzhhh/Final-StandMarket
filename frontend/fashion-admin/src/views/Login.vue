<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <h1>时尚管理系统</h1>
        <p>Fashion Admin</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script>
import { authApi } from '../api/auth'

export default {
  name: 'AdminLogin',
  data() {
    return {
      loading: false,
      loginForm: {
        username: '',
        password: ''
      },
      loginRules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  methods: {
    handleLogin() {
      this.$refs.loginFormRef.validate(async (valid) => {
        if (!valid) return
        this.loading = true
        try {
          const res = await authApi.login(this.loginForm)
          if (res.data.code === 1 && res.data.data && res.data.data.token) {
            localStorage.setItem('admin_token', res.data.data.token)
            localStorage.setItem('adminInfo', JSON.stringify(res.data.data))
            this.$message.success('登录成功')
            this.$router.push('/')
          } else {
            this.$message.error(res.data.msg || '登录失败')
          }
        } catch (error) {
          this.$message.error('登录失败，请检查账号密码')
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
}

.login-card {
  width: 400px;
  padding: 50px 45px 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.login-brand {
  text-align: center;
  margin-bottom: 36px;
}

.login-brand h1 {
  font-size: 24px;
  font-weight: bold;
  color: #2c3e50;
  margin: 0 0 8px 0;
}

.login-brand p {
  color: #999;
  font-size: 13px;
  margin: 0;
  letter-spacing: 2px;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}
</style>
