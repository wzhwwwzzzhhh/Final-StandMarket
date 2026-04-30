<template>
  <div class="app">
    <el-container style="height: 100vh">
      <el-aside :width="isSidebarCollapsed ? '64px' : '240px'" class="sidebar" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
        <!-- 品牌标识 -->
        <div class="brand" :class="{ 'brand-collapsed': isSidebarCollapsed }">
          <h1 class="brand-name">{{ isSidebarCollapsed ? 'FS' : '时尚管理系统' }}</h1>
          <p class="brand-desc" v-if="!isSidebarCollapsed">Fashion Admin</p>
        </div>
        
        <!-- 侧边菜单 -->
        <el-menu
          :default-active="activeIndex"
          class="sidebar-menu"
          @select="handleSelect"
          background-color="#2c3e50"
          text-color="#ecf0f1"
          active-text-color="#3498db"
          :collapse="isSidebarCollapsed"
          :collapse-transition="true"
        >
          <el-menu-item index="1">
            <el-icon><HomeFilled /></el-icon>
            <template #title>
              <span class="menu-text">首页</span>
            </template>
          </el-menu-item>
          
          <el-sub-menu index="2">
            <template #title>
              <el-icon><Goods /></el-icon>
              <span class="menu-text">商品管理</span>
            </template>
            <el-menu-item index="2-1">
              <template #title>
                <span class="menu-text">商品列表</span>
              </template>
            </el-menu-item>
            <el-menu-item index="2-2">
              <template #title>
                <span class="menu-text">添加商品</span>
              </template>
            </el-menu-item>
            <el-menu-item index="2-3">
              <template #title>
                <span class="menu-text">分类管理</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
          
          <el-sub-menu index="3">
            <template #title>
              <el-icon><ShoppingCart /></el-icon>
              <span class="menu-text">订单管理</span>
            </template>
            <el-menu-item index="3-1">
              <template #title>
                <span class="menu-text">订单列表</span>
              </template>
            </el-menu-item>
            <el-menu-item index="3-2">
              <template #title>
                <span class="menu-text">订单统计</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
          
          <el-sub-menu index="4">
            <template #title>
              <el-icon><Timer /></el-icon>
              <span class="menu-text">秒杀管理</span>
            </template>
            <el-menu-item index="4-1">
              <template #title>
                <span class="menu-text">秒杀活动</span>
              </template>
            </el-menu-item>
            <el-menu-item index="4-2">
              <template #title>
                <span class="menu-text">秒杀券管理</span>
              </template>
            </el-menu-item>
            <el-menu-item index="4-3">
              <template #title>
                <span class="menu-text">秒杀订单</span>
              </template>
            </el-menu-item>
            <el-menu-item index="4-4">
              <template #title>
                <span class="menu-text">特价商品</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
          
          <el-sub-menu index="5">
            <template #title>
              <el-icon><User /></el-icon>
              <span class="menu-text">用户管理</span>
            </template>
            <el-menu-item index="5-1">
              <template #title>
                <span class="menu-text">用户列表</span>
              </template>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>
      
      <el-container class="main-container">
        <!-- 顶部导航 -->
        <el-header class="top-header">
          <div class="header-left">
            <el-button type="text" class="menu-toggle" @click="toggleSidebar">
              <el-icon><Menu /></el-icon>
            </el-button>
            <div class="breadcrumb-container" v-if="!isSidebarCollapsed">
              <el-breadcrumb separator="/">
                <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
                <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
              </el-breadcrumb>
            </div>
            <h2 class="page-title">{{ pageTitle }}</h2>
          </div>
          <div class="header-right">
            <div class="search-container" v-if="!isSidebarCollapsed">
              <el-input
                v-model="searchQuery"
                placeholder="搜索..."
                class="search-input"
                prefix-icon="Search"
                @keyup.enter="handleSearch"
              />
            </div>
            <el-dropdown class="notification-dropdown">
              <el-badge :value="notificationCount" class="notification-badge">
                <el-button type="text" class="notification-btn">
                  <el-icon><Bell /></el-icon>
                </el-button>
              </el-badge>
              <template #dropdown>
                <el-dropdown-menu class="notification-menu">
                  <div class="notification-header">
                    <h3>通知中心</h3>
                    <el-button type="text" size="small" @click="markAllAsRead">全部已读</el-button>
                  </div>
                  <el-dropdown-item v-for="(notification, index) in notifications" :key="index" class="notification-item">
                    <div class="notification-content">
                      <div class="notification-title">{{ notification.title }}</div>
                      <div class="notification-desc">{{ notification.description }}</div>
                      <div class="notification-time">{{ notification.time }}</div>
                    </div>
                  </el-dropdown-item>
                  <el-dropdown-item divided class="view-all-notifications">
                    <span class="view-all-text">查看全部通知</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown class="user-dropdown">
              <span class="user-info">
                <el-avatar :size="32" class="user-avatar">管</el-avatar>
                <span class="user-name" v-if="!isSidebarCollapsed">管理员</span>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="goToProfile">
                    <el-icon><UserFilled /></el-icon>
                    <span>个人中心</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="goToSettings">
                    <el-icon><Setting /></el-icon>
                    <span>系统设置</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="logout">
                    <el-icon><SwitchButton /></el-icon>
                    <span>退出登录</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>
        
        <!-- 主内容区域 -->
        <el-main class="content-area">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { HomeFilled, Goods, ShoppingCart, Timer, User, ArrowDown, Menu, Bell, UserFilled, Setting, SwitchButton, Search } from '@element-plus/icons-vue'

export default {
  name: 'App',
  components: {
    HomeFilled,
    Goods,
    ShoppingCart,
    Timer,
    User,
    ArrowDown,
    Menu,
    Bell,
    UserFilled,
    Setting,
    SwitchButton,
    Search
  },
  data() {
    return {
      activeIndex: '1',
      isSidebarCollapsed: false,
      notificationCount: 5,
      pageTitle: '首页',
      searchQuery: '',
      notifications: [
        {
          title: '新订单',
          description: '有一笔新的订单需要处理',
          time: '5分钟前'
        },
        {
          title: '商品库存不足',
          description: '多个商品库存不足，需要补货',
          time: '1小时前'
        },
        {
          title: '系统通知',
          description: '系统将于明天进行维护',
          time: '3小时前'
        }
      ]
    }
  },
  methods: {
    handleSelect(key, keyPath) {
      this.activeIndex = key
      // 根据选择的菜单项进行路由跳转
      switch(key) {
        case '1':
          this.$router.push('/')
          this.pageTitle = '首页'
          break
        case '2-1':
          this.$router.push('/product/list')
          this.pageTitle = '商品列表'
          break
        case '2-2':
          this.$router.push('/product/add')
          this.pageTitle = '添加商品'
          break
        case '2-3':
          this.$router.push('/category')
          this.pageTitle = '分类管理'
          break
        case '3-1':
          this.$router.push('/order/list')
          this.pageTitle = '订单列表'
          break
        case '4-1':
          this.$router.push('/seckill/activity')
          this.pageTitle = '秒杀活动'
          break
        case '4-2':
          this.$router.push('/seckill/coupon')
          this.pageTitle = '秒杀券管理'
          break
        case '4-3':
          this.$router.push('/seckill/orders')
          this.pageTitle = '秒杀订单管理'
          break
        case '4-4':
          this.$router.push('/seckill/offer')
          this.pageTitle = '特价商品'
          break
        case '5-1':
          this.$router.push('/user/list')
          this.pageTitle = '用户列表'
          break
      }
    },
    toggleSidebar() {
      this.isSidebarCollapsed = !this.isSidebarCollapsed
    },
    handleSearch() {
      console.log('搜索:', this.searchQuery)
      // 实现搜索逻辑
    },
    markAllAsRead() {
      this.notificationCount = 0
      this.$message.success('所有通知已标记为已读')
    },
    goToProfile() {
      console.log('个人中心')
      // 跳转到个人中心
    },
    goToSettings() {
      console.log('系统设置')
      // 跳转到系统设置
    },
    logout() {
      this.$confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        // 实现退出登录逻辑
        this.$message.success('已退出登录')
      }).catch(() => {
        // 取消退出
      })
    }
  }
}
</script>

<style scoped>
.app {
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #333;
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
  transition: background 0.3s ease;
}

::-webkit-scrollbar-thumb:hover {
  background: #555;
}

/* 侧边栏样式 */
.sidebar {
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  transition: all 0.3s ease;
  overflow: hidden;
  box-shadow: 2px 0 15px rgba(0, 0, 0, 0.2);
  z-index: 100;
}

.sidebar-collapsed {
  box-shadow: 1px 0 8px rgba(0, 0, 0, 0.15);
}

.brand {
  padding: 35px 20px;
  text-align: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 25px;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.05);
}

.brand-collapsed {
  padding: 25px 0;
}

.brand-name {
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  margin: 0 0 8px 0;
  transition: all 0.3s ease;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.brand-desc {
  color: #bdc3c7;
  font-size: 13px;
  margin: 0;
  transition: all 0.3s ease;
  font-weight: 300;
}

.sidebar-menu {
  border-right: none;
}

.menu-text {
  font-size: 14px;
  transition: all 0.3s ease;
  font-weight: 500;
}

.sidebar-menu .el-menu-item {
  height: 55px;
  line-height: 55px;
  margin: 0 12px;
  border-radius: 10px;
  margin-bottom: 8px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.sidebar-menu .el-menu-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #3498db 0%, #2980b9 100%);
  transform: scaleY(0);
  transition: transform 0.3s ease;
  border-radius: 0 2px 2px 0;
}

.sidebar-menu .el-menu-item:hover {
  background-color: rgba(52, 152, 219, 0.25);
  transform: translateX(8px);
}

.sidebar-menu .el-menu-item.is-active {
  background-color: rgba(52, 152, 219, 0.35);
}

.sidebar-menu .el-menu-item.is-active::before {
  transform: scaleY(1);
}

.sidebar-menu .el-sub-menu__title {
  height: 55px;
  line-height: 55px;
  margin: 0 12px;
  border-radius: 10px;
  margin-bottom: 8px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.sidebar-menu .el-sub-menu__title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: linear-gradient(180deg, #3498db 0%, #2980b9 100%);
  transform: scaleY(0);
  transition: transform 0.3s ease;
  border-radius: 0 2px 2px 0;
}

.sidebar-menu .el-sub-menu__title:hover {
  background-color: rgba(52, 152, 219, 0.25);
  transform: translateX(8px);
}

.sidebar-menu .el-sub-menu.is-active .el-sub-menu__title {
  background-color: rgba(52, 152, 219, 0.35);
}

.sidebar-menu .el-sub-menu.is-active .el-sub-menu__title::before {
  transform: scaleY(1);
}

/* 主容器样式 */
.main-container {
  background-color: #f8f9fa;
  transition: all 0.3s ease;
}

/* 顶部导航样式 */
.top-header {
  background: linear-gradient(90deg, #fff 0%, #f8f9fa 100%);
  box-shadow: 0 3px 15px rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 25px;
  height: 65px;
  transition: all 0.3s ease;
  border-bottom: 1px solid #e0e0e0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 25px;
  flex: 1;
}

.menu-toggle {
  font-size: 22px;
  color: #333;
  transition: all 0.3s ease;
  padding: 8px;
  border-radius: 8px;
  background: #f0f2f5;
}

.menu-toggle:hover {
  background-color: #e0e0e0;
  transform: rotate(90deg);
}

.breadcrumb-container {
  flex: 1;
  max-width: 500px;
}

.page-title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #333;
  transition: all 0.3s ease;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 25px;
}

.search-container {
  position: relative;
  transition: all 0.3s ease;
}

.search-input {
  width: 280px;
  transition: all 0.3s ease;
  border-radius: 25px;
  border: 1px solid #e0e0e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.search-input:focus {
  width: 320px;
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.2);
  border-color: #3498db;
}

.notification-dropdown {
  position: relative;
}

.notification-badge {
  position: relative;
}

.notification-btn {
  font-size: 22px;
  color: #666;
  transition: all 0.3s ease;
  padding: 8px;
  border-radius: 8px;
  background: #f0f2f5;
}

.notification-btn:hover {
  background-color: #e0e0e0;
  color: #3498db;
  transform: scale(1.1);
}

.notification-menu {
  width: 380px;
  max-height: 450px;
  overflow-y: auto;
  border-radius: 12px;
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
  border: none;
}

.notification-header {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8f9fa;
  border-radius: 12px 12px 0 0;
}

.notification-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.notification-item {
  padding: 20px;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
  cursor: pointer;
}

.notification-item:hover {
  background-color: #f9f9f9;
  transform: translateX(5px);
}

.notification-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.notification-desc {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}

.notification-time {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
}

.view-all-notifications {
  text-align: center;
  padding: 15px;
  cursor: pointer;
  background: #f8f9fa;
  border-radius: 0 0 12px 12px;
}

.view-all-text {
  color: #3498db;
  font-size: 16px;
  transition: color 0.3s ease;
  font-weight: 500;
}

.view-all-text:hover {
  color: #2980b9;
  text-decoration: underline;
}

.user-dropdown {
  cursor: pointer;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 20px;
  border-radius: 25px;
  transition: all 0.3s ease;
  background: #f0f2f5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.user-info:hover {
  background-color: #e0e0e0;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.user-avatar {
  background: linear-gradient(135deg, #3498db 0%, #2980b9 100%);
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(52, 152, 219, 0.3);
}

.user-info:hover .user-avatar {
  background: linear-gradient(135deg, #2980b9 0%, #3498db 100%);
  transform: scale(1.15);
  box-shadow: 0 4px 12px rgba(52, 152, 219, 0.5);
}

.user-name {
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 内容区域样式 */
.content-area {
  background-color: #f8f9fa;
  padding: 30px;
  min-height: calc(100vh - 65px);
  transition: all 0.3s ease;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100vh;
    z-index: 1000;
  }
  
  .breadcrumb-container {
    display: none;
  }
  
  .search-container {
    display: none;
  }
  
  .user-name {
    display: none;
  }
  
  .main-container {
    margin-left: 0;
  }
  
  .top-header {
    padding: 0 20px;
  }
  
  .header-left {
    gap: 15px;
  }
  
  .header-right {
    gap: 15px;
  }
}

@media (max-width: 480px) {
  .page-title {
    font-size: 18px;
  }
  
  .header-left {
    gap: 12px;
  }
  
  .header-right {
    gap: 12px;
  }
  
  .content-area {
    padding: 20px;
  }
  
  .menu-toggle {
    font-size: 20px;
    padding: 6px;
  }
  
  .user-info {
    padding: 6px 15px;
  }
}
</style>
