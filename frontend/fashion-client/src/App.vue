<template>
  <div class="app">
    <!-- 导航栏 -->
    <div class="navbar" :class="{ 'nav-scrolled': isScrolled }">
      <div class="navbar-container">
        <div class="logo">
          <router-link to="/" class="logo-link">时尚服装</router-link>
        </div>
        
        <!-- 桌面端菜单 -->
        <div class="desktop-menu">
          <el-menu 
            :default-active="activeIndex" 
            class="el-menu-demo" 
            mode="horizontal" 
            text-color="#fff" 
            active-text-color="#ff4d4f"
          >
            <el-menu-item index="1">
              <router-link to="/" class="nav-link">首页</router-link>
            </el-menu-item>
            <el-menu-item index="2">
              <router-link to="/product/list" class="nav-link">商品列表</router-link>
            </el-menu-item>
            <el-menu-item index="3">
              <router-link to="/seckill" class="nav-link">秒杀活动</router-link>
            </el-menu-item>
            <el-menu-item index="4">
              <router-link to="/cart" class="nav-link">
                购物车
                <el-badge v-if="cartCount > 0" :value="cartCount" class="cart-badge" />
              </router-link>
            </el-menu-item>
            <el-menu-item index="5">
              <router-link to="/order" class="nav-link">订单管理</router-link>
            </el-menu-item>
          </el-menu>
        </div>
        
        <!-- 移动端菜单按钮 -->
        <div class="mobile-menu-btn" @click="toggleMobileMenu">
          <el-icon :size="24"><Menu /></el-icon>
        </div>
        
        <!-- 移动端菜单 -->
        <div class="mobile-menu" v-if="isMobileMenuOpen">
          <div class="mobile-menu-content">
            <div class="mobile-menu-header">
              <span>菜单</span>
              <el-icon class="close-btn" @click="toggleMobileMenu"><Close /></el-icon>
            </div>
            <el-menu 
              :default-active="activeIndex" 
              class="mobile-menu-list" 
              text-color="#333" 
              active-text-color="#ff4d4f"
            >
              <el-menu-item index="1">
                <router-link to="/" class="mobile-nav-link">首页</router-link>
              </el-menu-item>
              <el-menu-item index="2">
                <router-link to="/product/list" class="mobile-nav-link">商品列表</router-link>
              </el-menu-item>
              <el-menu-item index="3">
                <router-link to="/seckill" class="mobile-nav-link">秒杀活动</router-link>
              </el-menu-item>
              <el-menu-item index="4">
                <router-link to="/cart" class="mobile-nav-link">
                  购物车
                  <el-badge v-if="cartCount > 0" :value="cartCount" class="cart-badge" />
                </router-link>
              </el-menu-item>
              <el-menu-item index="5">
                <router-link to="/order" class="mobile-nav-link">订单管理</router-link>
              </el-menu-item>
              <el-menu-item index="6" @click="goToLogin" v-if="!isLoggedIn">
                <span class="mobile-nav-link">登录</span>
              </el-menu-item>
              <el-menu-item index="7" @click="goToRegister" v-if="!isLoggedIn">
                <span class="mobile-nav-link">注册</span>
              </el-menu-item>
              <el-menu-item index="8" @click="goToProfile" v-if="isLoggedIn">
                <span class="mobile-nav-link">个人中心</span>
              </el-menu-item>
              <el-menu-item index="9" @click="goToSettings" v-if="isLoggedIn">
                <span class="mobile-nav-link">设置</span>
              </el-menu-item>
              <el-menu-item index="10" @click="logout" v-if="isLoggedIn">
                <span class="mobile-nav-link">退出登录</span>
              </el-menu-item>
            </el-menu>
          </div>
        </div>
        
        <!-- 用户下拉菜单 -->
        <div class="user-dropdown">
          <span class="user-info" @click="toggleUserDropdown">
            <el-avatar v-if="isLoggedIn" :size="32" class="user-avatar">{{ userInitial }}</el-avatar>
            <span v-else class="login-text">登录</span>
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </span>
          <div class="user-dropdown-menu" v-if="isUserDropdownOpen">
            <template v-if="isLoggedIn">
              <div class="dropdown-item" @click="goToProfile">
                <el-icon><UserFilled /></el-icon>
                <span>个人中心</span>
              </div>
              <div class="dropdown-item" @click="goToSettings">
                <el-icon><Setting /></el-icon>
                <span>设置</span>
              </div>
              <div class="dropdown-divider"></div>
              <div class="dropdown-item" @click="logout">
                <el-icon><SwitchButton /></el-icon>
                <span>退出登录</span>
              </div>
            </template>
            <template v-else>
              <div class="dropdown-item" @click="goToLogin">
                <el-icon><UserFilled /></el-icon>
                <span>登录</span>
              </div>
              <div class="dropdown-item" @click="goToRegister">
                <el-icon><User /></el-icon>
                <span>注册</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 主要内容 -->
    <div class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>
    
    <!-- 底部信息 -->
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-section">
          <h3>关于我们</h3>
          <p>时尚服装品牌，致力于为您提供高品质的服装产品</p>
          <p>我们追求时尚与舒适的完美结合，为您打造独特的个人风格</p>
        </div>
        <div class="footer-section">
          <h3>联系我们</h3>
          <p>客服电话：400-123-4567</p>
          <p>邮箱：service@fashion.com</p>
          <p>地址：北京市朝阳区时尚大厦1001室</p>
        </div>
        <div class="footer-section">
          <h3>关注我们</h3>
          <div class="social-icons">
            <a href="#" class="social-icon"><el-icon><ChatLineRound /></el-icon> 微信</a>
            <a href="#" class="social-icon"><el-icon><Link /></el-icon> 微博</a>
            <a href="#" class="social-icon"><el-icon><Position /></el-icon> 抖音</a>
            <a href="#" class="social-icon"><el-icon><VideoCamera /></el-icon> 小红书</a>
          </div>
        </div>
        <div class="footer-section">
          <h3>快速链接</h3>
          <ul class="quick-links">
            <li><a href="#">新品上市</a></li>
            <li><a href="#">热卖商品</a></li>
            <li><a href="#">会员中心</a></li>
            <li><a href="#">售后服务</a></li>
          </ul>
        </div>
      </div>
      <div class="footer-bottom">
        <p>© 2026 时尚服装. 保留所有权利.</p>
        <div class="footer-links">
          <a href="#">隐私政策</a> | <a href="#">使用条款</a> | <a href="#">配送说明</a> | <a href="#">联系我们</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script>
import { ArrowDown, UserFilled, Setting, SwitchButton, User, ChatLineRound, Link, Position, VideoCamera, Menu, Close } from '@element-plus/icons-vue'

export default {
  name: 'App',
  components: {
    ArrowDown,
    UserFilled,
    Setting,
    SwitchButton,
    User,
    ChatLineRound,
    Link,
    Position,
    VideoCamera,
    Menu,
    Close
  },
  data() {
    return {
      activeIndex: '1',
      isScrolled: false,
      isLoggedIn: false,
      userInitial: '管',
      isMobileMenuOpen: false,
      isUserDropdownOpen: false
    }
  },
  computed: {
    cartCount() {
      const cart = localStorage.getItem('cart')
      if (cart) {
        const cartItems = JSON.parse(cart)
        return cartItems.reduce((total, item) => total + item.quantity, 0)
      }
      return 0
    }
  },
  mounted() {
    // 监听滚动事件
    window.addEventListener('scroll', this.handleScroll)
    // 初始化登录状态和用户信息
    this.initUserStatus()
  },
  beforeUnmount() {
    window.removeEventListener('scroll', this.handleScroll)
  },
  methods: {
    initUserStatus() {
      // 检查是否有token
      this.isLoggedIn = localStorage.getItem('token') !== null
      // 获取用户信息
      const userInfo = localStorage.getItem('userInfo')
      if (userInfo) {
        try {
          const parsedUserInfo = JSON.parse(userInfo)
          console.log('解析的用户信息:', parsedUserInfo)
          // 设置用户首字母，只使用name字段
          if (parsedUserInfo.name) {
            this.userInitial = parsedUserInfo.name.charAt(0).toUpperCase()
            console.log('设置用户首字母:', this.userInitial)
          }
        } catch (error) {
          console.error('解析用户信息失败:', error)
        }
      }
      console.log('初始化用户状态完成，isLoggedIn:', this.isLoggedIn)
    },
    handleSelect(key, keyPath) {
      this.activeIndex = key
    },
    handleMobileSelect(key, keyPath) {
      this.activeIndex = key
      this.isMobileMenuOpen = false
      // 处理移动端菜单的特殊点击
      switch(key) {
        case '6':
          this.goToLogin()
          break
        case '7':
          this.goToRegister()
          break
        case '8':
          this.goToProfile()
          break
        case '9':
          this.goToSettings()
          break
        case '10':
          this.logout()
          break
      }
    },
    handleScroll() {
      // 当滚动超过50px时，改变导航栏样式
      this.isScrolled = window.scrollY > 50
    },
    toggleMobileMenu() {
      this.isMobileMenuOpen = !this.isMobileMenuOpen
      // 关闭用户下拉菜单
      this.isUserDropdownOpen = false
    },
    toggleUserDropdown() {
      this.isUserDropdownOpen = !this.isUserDropdownOpen
      // 关闭移动端菜单
      this.isMobileMenuOpen = false
    },
    goToLogin() {
      this.$router.push('/login')
      this.isUserDropdownOpen = false
      this.isMobileMenuOpen = false
    },
    goToRegister() {
      // 假设注册页面路由
      this.$router.push('/register')
      this.isUserDropdownOpen = false
      this.isMobileMenuOpen = false
    },
    goToProfile() {
      // 假设个人中心路由
      this.$router.push('/profile')
      this.isUserDropdownOpen = false
      this.isMobileMenuOpen = false
    },
    goToSettings() {
      // 假设设置页面路由
      this.$router.push('/settings')
      this.isUserDropdownOpen = false
      this.isMobileMenuOpen = false
    },
    logout() {
      // 调用后端退出登录接口
      import('./api/user.js').then(({ userApi }) => {
        userApi.logout().then(() => {
          // 清除本地存储
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          this.isLoggedIn = false
          this.userInitial = '管'
          this.$message.success('已退出登录')
          this.isUserDropdownOpen = false
          this.isMobileMenuOpen = false
        }).catch(() => {
          // 接口调用失败，仍然清除本地存储
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          this.isLoggedIn = false
          this.userInitial = '管'
          this.$message.success('已退出登录')
          this.isUserDropdownOpen = false
          this.isMobileMenuOpen = false
        })
      })
    }
  }
}
</script>

<style scoped>
.app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #f0f2f5;
  background-image: url("data:image/svg+xml,%3Csvg width='100' height='100' viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath d='M11 18c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm48 25c3.866 0 7-3.134 7-7s-3.134-7-7-7-7 3.134-7 7 3.134 7 7 7zm-43-7c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zm63 31c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zM34 90c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zm56-76c1.657 0 3-1.343 3-3s-1.343-3-3-3-3 1.343-3 3 1.343 3 3 3zM12 86c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm28-65c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm23-11c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm-6 60c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm29 22c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zM32 63c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm57-13c2.76 0 5-2.24 5-5s-2.24-5-5-5-5 2.24-5 5 2.24 5 5 5zm-9-21c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM60 91c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM35 41c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2zM12 60c1.105 0 2-.895 2-2s-.895-2-2-2-2 .895-2 2 .895 2 2 2z' fill='%239C92AC' fill-opacity='0.1' fill-rule='evenodd'/%3E%3C/svg%3E");
}

/* 全局动画类 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.3s ease;
}

.slide-enter-from {
  transform: translateX(-100%);
}

.slide-leave-to {
  transform: translateX(100%);
}

.bounce-enter-active {
  animation: bounce-in 0.5s;
}

@keyframes bounce-in {
  0% {
    transform: scale(0.8);
    opacity: 0;
  }
  60% {
    transform: scale(1.05);
    opacity: 1;
  }
  100% {
    transform: scale(1);
  }
}

.pulse {
  animation: pulse 2s infinite;
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

.shake {
  animation: shake 0.5s ease-in-out;
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-5px);
  }
  75% {
    transform: translateX(5px);
  }
}

.zoom-enter-active,
.zoom-leave-active {
  transition: transform 0.3s ease, opacity 0.3s ease;
}

.zoom-enter-from,
.zoom-leave-to {
  transform: scale(0.9);
  opacity: 0;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 4px;
}

::-webkit-scrollbar-thumb:hover {
  background: #555;
}

/* 导航栏样式 */
.navbar {
  border-bottom: none;
  transition: all 0.3s ease;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: 60px;
  background-color: #1a1a1a;
}

.nav-scrolled {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  background-color: rgba(26, 26, 26, 0.95);
}

.navbar-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 100%;
}

.logo {
  margin-right: 40px;
}

.logo-link {
  color: #fff;
  text-decoration: none;
  font-size: 20px;
  font-weight: bold;
  display: block;
  height: 60px;
  line-height: 60px;
  padding: 0 20px;
  transition: all 0.3s ease;
  border-radius: 8px;
}

.logo-link:hover {
  color: #ff4d4f;
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

/* 桌面端菜单 */
.desktop-menu {
  flex: 1;
  display: flex;
  justify-content: center;
}

.desktop-menu .el-menu {
  width: 100%;
}

.desktop-menu .el-menu-item {
  margin: 0 10px;
  font-size: 14px;
  height: 60px;
  line-height: 60px;
}

.el-menu-demo {
  border-bottom: none;
  width: 100%;
  background-color: transparent;
}

.nav-link {
  color: #fff;
  text-decoration: none;
  display: block;
  padding: 0 20px;
  height: 60px;
  line-height: 60px;
  transition: all 0.3s ease;
  border-radius: 8px;
  margin: 0 5px;
}

.nav-link:hover {
  color: #ff4d4f;
  background-color: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

.el-menu-item {
  background-color: transparent !important;
  border-radius: 8px;
  margin: 0 10px;
  transition: all 0.3s ease;
}

.el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1) !important;
  transform: translateY(-2px);
}

.el-menu-item.is-active {
  background-color: rgba(255, 77, 79, 0.2) !important;
  color: #ff4d4f !important;
  font-weight: bold;
}

/* 移动端菜单按钮 */
.mobile-menu-btn {
  display: none;
  color: #fff;
  cursor: pointer;
  padding: 10px;
  border-radius: 5px;
  transition: background-color 0.3s ease;
}

.mobile-menu-btn:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

/* 移动端菜单 */
.mobile-menu {
  position: fixed;
  top: 60px;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  z-index: 999;
  animation: slideInDown 0.3s ease;
}

@keyframes slideInDown {
  from {
    transform: translateY(-100%);
  }
  to {
    transform: translateY(0);
  }
}

.mobile-menu-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.mobile-menu-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.mobile-menu-header span {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.close-btn {
  color: #333;
  cursor: pointer;
  padding: 5px;
  border-radius: 5px;
  transition: background-color 0.3s ease;
}

.close-btn:hover {
  background-color: #f0f0f0;
}

.mobile-menu-list {
  flex: 1;
  border-right: none;
}

.mobile-nav-link {
  color: #333;
  text-decoration: none;
  display: block;
  width: 100%;
  height: 100%;
  padding: 15px 20px;
  transition: all 0.3s ease;
}

.mobile-nav-link:hover {
  color: #ff4d4f;
}

/* 用户下拉菜单 */
.user-dropdown {
  position: relative;
  cursor: pointer;
  margin-left: auto;
  margin-right: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 15px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.user-info:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.user-avatar {
  background-color: #ff4d4f;
}

.login-text {
  color: #fff;
  font-size: 14px;
}

.user-dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 10px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 180px;
  z-index: 1001;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 15px;
  color: #333;
  cursor: pointer;
  transition: background-color 0.3s ease;
}

.dropdown-item:hover {
  background-color: #f0f0f0;
}

.dropdown-divider {
  height: 1px;
  background-color: #f0f0f0;
  margin: 5px 0;
}

.cart-badge {
  position: absolute;
  top: 10px;
  right: 10px;
}

/* 主要内容样式 */
.main-content {
  flex: 1;
  padding: 80px 0 40px;
}

/* 页脚样式 */
.footer {
  background-color: #1a1a1a;
  color: #fff;
  padding: 60px 0 30px;
  margin-top: 40px;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 40px;
}

.footer-section {
  margin-bottom: 30px;
  min-width: 200px;
  flex: 1;
  max-width: 280px;
}

.footer-section h3 {
  margin-bottom: 20px;
  color: #ff4d4f;
  font-size: 16px;
  font-weight: bold;
  position: relative;
  padding-bottom: 10px;
}

.footer-section h3::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 40px;
  height: 2px;
  background-color: #ff4d4f;
}

.footer-section p {
  margin: 10px 0;
  color: #ccc;
  font-size: 14px;
  line-height: 1.5;
}

.social-icons {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 15px;
}

.social-icon {
  color: #ccc;
  text-decoration: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 0;
  transition: all 0.3s ease;
}

.social-icon:hover {
  color: #ff4d4f;
  transform: translateX(5px);
}

.quick-links {
  list-style: none;
  padding: 0;
  margin: 15px 0 0;
}

.quick-links li {
  margin-bottom: 10px;
}

.quick-links a {
  color: #ccc;
  text-decoration: none;
  font-size: 14px;
  transition: all 0.3s ease;
  display: block;
  padding: 5px 0;
}

.quick-links a:hover {
  color: #ff4d4f;
  transform: translateX(5px);
}

.footer-bottom {
  text-align: center;
  padding-top: 30px;
  border-top: 1px solid #333;
  margin-top: 30px;
  color: #999;
}

.footer-bottom p {
  margin-bottom: 15px;
  font-size: 14px;
}

.footer-links {
  font-size: 12px;
}

.footer-links a {
  color: #999;
  text-decoration: none;
  margin: 0 5px;
  transition: color 0.3s ease;
}

.footer-links a:hover {
  color: #ff4d4f;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .desktop-menu {
    display: none;
  }
  
  .mobile-menu-btn {
    display: block;
  }
  
  .logo {
    margin-right: 20px;
  }
  
  .logo-link {
    font-size: 18px;
    padding: 0 10px;
  }
  
  .footer-content {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .footer-section {
    max-width: 100%;
  }
  
  .footer-section h3::after {
    left: 50%;
    transform: translateX(-50%);
  }
  
  .social-icon {
    justify-content: center;
  }
  
  .quick-links li {
    text-align: center;
  }
  
  .quick-links a:hover {
    transform: none;
  }
}

@media (max-width: 480px) {
  .navbar-container {
    padding: 0 10px;
  }
  
  .logo-link {
    font-size: 16px;
  }
  
  .user-info {
    padding: 5px 10px;
  }
  
  .login-text {
    font-size: 12px;
  }
  
  .footer-content {
    gap: 20px;
  }
  
  .footer-section {
    min-width: 150px;
  }
  
  .footer-section h3 {
    font-size: 14px;
  }
  
  .footer-section p {
    font-size: 12px;
  }
  
  .quick-links a {
    font-size: 12px;
  }
  
  .footer-bottom p {
    font-size: 12px;
  }
  
  .footer-links {
    font-size: 10px;
  }
}
</style>
