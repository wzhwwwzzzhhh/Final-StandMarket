<template>
  <div class="profile-container">
    <!-- 顶部背景 -->
    <div class="profile-header">
      <div class="profile-header-content">
        <span class="profile-kicker">// MEMBER PROFILE</span>
        <p>管理你的订单、权益与个人偏好</p>
      </div>
    </div>
    
    <!-- 用户信息卡片 -->
    <div class="user-card">
      <div class="user-avatar">
        <el-avatar :size="100" class="avatar" :src="userInfo.avatar">
          <template #error>
            <span class="avatar-fallback">{{ userInitial }}</span>
          </template>
        </el-avatar>
      </div>
      <div class="user-info">
        <span class="member-label">STAND / MEMBER</span>
        <div class="user-main-info">
          <h2 class="user-name">{{ userInfo.username || userInfo.name || '用户' }}</h2>
          <p class="user-phone">{{ userInfo.phone }}</p>
        </div>
        <div class="user-stats">
          <div class="stat-item">
            <span class="stat-value">{{ userInfo.orderCount || 0 }}</span>
            <span class="stat-label">总订单</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ userInfo.favoriteCount || 0 }}</span>
            <span class="stat-label">收藏</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <span class="stat-value">{{ userInfo.couponCount || 0 }}</span>
            <span class="stat-label">优惠券</span>
          </div>
        </div>
        <el-button type="primary" @click="goToProfileEdit" class="edit-btn">
          <el-icon><Edit /></el-icon>
          <span>编辑资料</span>
        </el-button>
      </div>
    </div>
    
    <!-- 订单状态概览 -->
    <div class="order-overview">
      <div class="section-header">
        <h3 class="section-title">我的订单</h3>
        <el-button type="text" @click="goToOrder('all')" class="view-all-btn">
          查看全部
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div class="order-stats">
        <div class="order-stat-item" role="button" tabindex="0" @click="goToOrder('pending')" @keydown.enter="goToOrder('pending')" @keydown.space.prevent="goToOrder('pending')">
          <div class="order-icon-container">
            <el-icon class="icon"><Timer /></el-icon>
            <span v-if="orderCounts.pending" class="order-badge">{{ orderCounts.pending }}</span>
          </div>
          <span class="order-stat-text">待付款</span>
        </div>
        <div class="order-stat-item" role="button" tabindex="0" @click="goToOrder('shipping')" @keydown.enter="goToOrder('shipping')" @keydown.space.prevent="goToOrder('shipping')">
          <div class="order-icon-container">
            <el-icon class="icon"><Van /></el-icon>
            <span v-if="orderCounts.shipping" class="order-badge">{{ orderCounts.shipping }}</span>
          </div>
          <span class="order-stat-text">待发货</span>
        </div>
        <div class="order-stat-item" role="button" tabindex="0" @click="goToOrder('delivered')" @keydown.enter="goToOrder('delivered')" @keydown.space.prevent="goToOrder('delivered')">
          <div class="order-icon-container">
            <el-icon class="icon"><Message /></el-icon>
            <span v-if="orderCounts.delivered" class="order-badge">{{ orderCounts.delivered }}</span>
          </div>
          <span class="order-stat-text">待收货</span>
        </div>
        <div class="order-stat-item" role="button" tabindex="0" @click="goToOrder('completed')" @keydown.enter="goToOrder('completed')" @keydown.space.prevent="goToOrder('completed')">
          <div class="order-icon-container">
            <el-icon class="icon"><Star /></el-icon>
            <span v-if="orderCounts.completed" class="order-badge">{{ orderCounts.completed }}</span>
          </div>
          <span class="order-stat-text">待评价</span>
        </div>
        <div class="order-stat-item" role="button" tabindex="0" @click="goToOrder('refund')" @keydown.enter="goToOrder('refund')" @keydown.space.prevent="goToOrder('refund')">
          <div class="order-icon-container">
            <el-icon class="icon"><Refresh /></el-icon>
            <span v-if="orderCounts.refund" class="order-badge">{{ orderCounts.refund }}</span>
          </div>
          <span class="order-stat-text">退款/售后</span>
        </div>
      </div>
    </div>
    
    <!-- 功能导航 -->
    <div class="feature-nav">
      <h3 class="section-title">我的服务</h3>
      <div class="feature-grid">
        <div class="feature-item" role="button" tabindex="0" @click="goToAddress" @keydown.enter="goToAddress" @keydown.space.prevent="goToAddress">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Position /></el-icon>
          </div>
          <span class="feature-text">收货地址</span>
        </div>
        <div class="feature-item" role="button" tabindex="0" @click="goToFavorites" @keydown.enter="goToFavorites" @keydown.space.prevent="goToFavorites">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Star /></el-icon>
          </div>
          <span class="feature-text">我的收藏</span>
        </div>
        <div class="feature-item" role="button" tabindex="0" @click="goToCoupons" @keydown.enter="goToCoupons" @keydown.space.prevent="goToCoupons">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Ticket /></el-icon>
            <span v-if="userInfo.couponCount && userInfo.couponCount > 0" class="feature-badge">{{ userInfo.couponCount }}</span>
          </div>
          <span class="feature-text">优惠券</span>
        </div>
        <div class="feature-item" role="button" tabindex="0" @click="goToHistory" @keydown.enter="goToHistory" @keydown.space.prevent="goToHistory">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Clock /></el-icon>
          </div>
          <span class="feature-text">浏览历史</span>
        </div>
        <div class="feature-item" role="button" tabindex="0" @click="goToCustomerService" @keydown.enter="goToCustomerService" @keydown.space.prevent="goToCustomerService">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Headset /></el-icon>
          </div>
          <span class="feature-text">客服中心</span>
        </div>
        <div class="feature-item" role="button" tabindex="0" @click="goToSettings" @keydown.enter="goToSettings" @keydown.space.prevent="goToSettings">
          <div class="feature-icon-container">
            <el-icon class="feature-icon"><Setting /></el-icon>
          </div>
          <span class="feature-text">设置</span>
        </div>
      </div>
    </div>
    
    <!-- 推荐商品 -->
    <div class="recommended-products" v-if="recommendedProducts.length > 0">
      <h3 class="section-title">为您推荐</h3>
      <div class="recommended-grid">
        <div class="recommended-item" v-for="product in recommendedProducts" :key="product.id" role="button" tabindex="0" @click="viewProductDetail(product.id)" @keydown.enter="viewProductDetail(product.id)" @keydown.space.prevent="viewProductDetail(product.id)">
          <div class="recommended-image">
            <img :src="product.image" :alt="product.name" />
          </div>
          <div class="recommended-info">
            <h4 class="recommended-name">{{ product.name }}</h4>
            <p class="recommended-price">¥{{ product.price }}</p>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 退出登录按钮 -->
    <div class="logout-section">
      <el-button type="danger" @click="logout" class="logout-btn">
        <el-icon><SwitchButton /></el-icon>
        <span>退出登录</span>
      </el-button>
    </div>
  </div>
</template>

<script>
import { Timer, Van, Message, Star, Grid, Position, Ticket, Clock, Headset, Setting, Edit, ArrowRight, SwitchButton, Refresh } from '@element-plus/icons-vue'
import favoriteApi from '@/api/favorite'
import { orderApi } from '@/api/product'
import profileImg1 from '@/assets/images/clothes/新对话 (5).png'
import profileImg2 from '@/assets/images/shoes/新对话 (10).png'
import profileImg3 from '@/assets/images/accessories/新对话 (14).png'

export default {
  name: 'Profile',
  components: {
    Timer,
    Van,
    Message,
    Star,
    Grid,
    Position,
    Ticket,
    Clock,
    Headset,
    Setting,
    Edit,
    ArrowRight,
    SwitchButton,
    Refresh
  },
  data() {
    return {
      userInfo: {},
      userInitial: '管',
      orderCounts: {
        pending: 0,
        shipping: 0,
        delivered: 0,
        completed: 0,
        refund: 0
      },
      recommendedProducts: [
        {
          id: 1,
          name: '时尚休闲T恤',
          price: 99,
          image: profileImg1
        },
        {
          id: 2,
          name: '潮流运动鞋',
          price: 299,
          image: profileImg2
        },
        {
          id: 3,
          name: '休闲牛仔裤',
          price: 199,
          image: profileImg3
        }
      ]
    }
  },
  mounted() {
    this.loadUserInfo()
    this.loadFavoriteCount()
    this.loadOrderCounts()
  },
  methods: {
    // 按状态拉取订单列表统计数量（与 Order 页 tab 状态一致）
    loadOrderCounts() {
      const statusMap = {
        pending: 1,
        shipping: 2,
        delivered: 3,
        completed: 4,
        refund: 6
      }
      Object.keys(statusMap).forEach(key => {
        orderApi.getOrderList(statusMap[key]).then(response => {
          if (response.data.code === 1) {
            this.orderCounts[key] = (response.data.data || []).length
          }
        }).catch(() => {})
      })
    },
    loadUserInfo() {
      // 从localStorage获取用户信息
      const userInfoStr = localStorage.getItem('userInfo')
      if (userInfoStr) {
        try {
          this.userInfo = JSON.parse(userInfoStr)
          // 设置用户首字母
          if (this.userInfo.username) {
            this.userInitial = this.userInfo.username.charAt(0).toUpperCase()
          } else if (this.userInfo.name) {
            this.userInitial = this.userInfo.name.charAt(0).toUpperCase()
          }
        } catch (error) {
          console.error('解析用户信息失败:', error)
        }
      }
    },
    goToSettings() {
      this.$router.push('/settings')
    },
    goToProfileEdit() {
      this.$router.push('/profile/edit')
    },
    goToOrder(status) {
      this.$router.push(`/order?status=${status}`)
    },
    goToAddress() {
      this.$router.push('/address')
    },
    goToFavorites() {
      this.$router.push('/favorite')
    },
    goToCoupons() {
      this.$router.push('/my-coupons')
    },
    goToHistory() {
      this.$message.info('浏览历史功能开发中')
    },
    loadFavoriteCount() {
      favoriteApi.count().then(response => {
        if (response.data.code === 1) {
          this.userInfo.favoriteCount = response.data.data.count
        }
      }).catch(() => {})
    },
    goToCustomerService() {
      this.$message.info('客服中心功能开发中')
    },
    viewProductDetail(productId) {
      this.$router.push(`/product/detail/${productId}`)
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

.profile-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px 40px;
  min-height: 100vh;
  background: #f8f9fa;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 顶部背景 */
.profile-header {
  height: 200px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 0 0 30px 30px;
  margin-bottom: -60px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.profile-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.1);
  z-index: 1;
}

/* 用户信息卡片 */
.user-card {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  padding: 40px;
  margin-bottom: 30px;
  position: relative;
  z-index: 2;
  animation: fadeIn 0.8s ease-out;
  transition: all 0.3s ease;
}

.user-card:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.2);
  transform: translateY(-5px);
}

.user-avatar {
  position: relative;
  margin-right: 40px;
  flex-shrink: 0;
}

.avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-size: 40px;
  font-weight: bold;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
}

.avatar:hover {
  transform: scale(1.1);
  box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
}

.avatar-fallback { font-family: var(--font-display); font-size: 32px; font-weight: 700; }

.user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.user-main-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-name {
  font-size: 28px;
  font-weight: bold;
  margin: 0;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.user-phone {
  font-size: 16px;
  color: #666;
  margin: 0;
}

.user-stats {
  display: flex;
  align-items: center;
  gap: 30px;
  padding: 20px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  min-width: 80px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: #e0e0e0;
}

.edit-btn {
  align-self: flex-start;
  border-radius: 25px;
  padding: 10px 30px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.edit-btn:hover {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

/* 订单状态概览 */
.order-overview {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  padding: 30px;
  margin-bottom: 30px;
  animation: fadeIn 0.8s ease-out 0.2s both;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.section-title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.view-all-btn {
  color: #667eea;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 5px;
}

.view-all-btn:hover {
  color: #764ba2;
  transform: translateX(5px);
}

.order-stats {
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
  gap: 20px;
}

.order-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 25px;
  border-radius: 15px;
  transition: all 0.3s ease;
  cursor: pointer;
  min-width: 100px;
  background: #f8f9fa;
  position: relative;
}

.order-stat-item:hover {
  background: #f0f2f5;
  transform: translateY(-5px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.order-icon-container {
  position: relative;
  margin-bottom: 15px;
}

.icon {
  font-size: 32px;
  color: #667eea;
  transition: all 0.3s ease;
}

.order-stat-item:hover .icon {
  transform: scale(1.15);
  color: #764ba2;
}

.order-badge {
  position: absolute;
  top: -10px;
  right: -10px;
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(255, 77, 79, 0.3);
  animation: pulse 2s infinite;
}

.order-stat-text {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

/* 功能导航 */
.feature-nav {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  padding: 30px;
  margin-bottom: 30px;
  animation: fadeIn 0.8s ease-out 0.4s both;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 30px;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px 20px;
  border-radius: 15px;
  transition: all 0.3s ease;
  cursor: pointer;
  background: #f8f9fa;
  position: relative;
}

.feature-item:hover {
  background: #f0f2f5;
  transform: translateY(-8px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.feature-icon-container {
  position: relative;
  margin-bottom: 15px;
}

.feature-icon {
  font-size: 36px;
  color: #667eea;
  transition: all 0.3s ease;
}

.feature-item:hover .feature-icon {
  transform: scale(1.2);
  color: #764ba2;
}

.feature-badge {
  position: absolute;
  top: -10px;
  right: -10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 12px;
  min-width: 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.feature-text {
  font-size: 14px;
  color: #666;
  font-weight: 500;
  text-align: center;
}

/* 推荐商品 */
.recommended-products {
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  padding: 30px;
  margin-bottom: 30px;
  animation: fadeIn 0.8s ease-out 0.6s both;
}

.recommended-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.recommended-item {
  background: #f8f9fa;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
  cursor: pointer;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
}

.recommended-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.recommended-image {
  height: 200px;
  overflow: hidden;
  background: #e9ecef;
}

.recommended-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.recommended-item:hover .recommended-image img {
  transform: scale(1.1);
}

.recommended-info {
  padding: 15px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recommended-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0;
  height: 48px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.recommended-price {
  font-size: 18px;
  font-weight: bold;
  color: #ff4d4f;
  margin: 0;
}

/* 退出登录按钮 */
.logout-section {
  text-align: center;
  margin-top: 40px;
  animation: fadeIn 0.8s ease-out 0.8s both;
}

.logout-btn {
  width: 250px;
  height: 52px;
  border-radius: 26px;
  font-size: 18px;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 0 auto;
}

.logout-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(255, 77, 79, 0.3);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-container {
    padding: 0 15px 30px;
  }
  
  .profile-header {
    height: 150px;
    margin-bottom: -50px;
  }
  
  .user-card {
    flex-direction: column;
    text-align: center;
    padding: 30px;
  }
  
  .user-avatar {
    margin-right: 0;
    margin-bottom: 30px;
  }
  
  .user-info {
    align-items: center;
  }
  
  .edit-btn {
    align-self: center;
  }
  
  .user-stats {
    gap: 20px;
  }
  
  .order-stats {
    gap: 15px;
  }
  
  .order-stat-item {
    padding: 20px;
    min-width: 80px;
  }
  
  .feature-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 20px;
  }
  
  .feature-item {
    padding: 20px 15px;
  }
  
  .feature-icon {
    font-size: 28px;
  }
  
  .recommended-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 15px;
  }
  
  .recommended-image {
    height: 150px;
  }
}

@media (max-width: 480px) {
  .user-card {
    padding: 20px;
  }
  
  .user-name {
    font-size: 24px;
  }
  
  .user-stats {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .order-stats {
    flex-wrap: wrap;
  }
  
  .order-stat-item {
    flex: 1;
    min-width: 70px;
  }
  
  .feature-grid {
    grid-template-columns: repeat(3, 1fr);
    gap: 15px;
  }
  
  .feature-item {
    padding: 15px 10px;
  }
  
  .feature-icon {
    font-size: 24px;
  }
  
  .feature-text {
    font-size: 12px;
  }
  
  .recommended-grid {
    grid-template-columns: 1fr;
  }
  
  .logout-btn {
    width: 200px;
    height: 48px;
    font-size: 16px;
  }
}

/* === STAND/MARKET profile visual system === */
.profile-container {
  max-width: 1240px;
  padding: 0 var(--space-lg) 72px;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-body);
}
.profile-header {
  min-height: 192px;
  margin: 24px -8px -62px;
  padding: 30px 40px;
  border-radius: var(--radius-none);
  background: linear-gradient(120deg, rgba(209, 0, 255, .22), transparent 48%), linear-gradient(90deg, rgba(204, 255, 0, .08), transparent 38%), var(--bg-elevated);
  border: 1px solid var(--border-card);
  box-shadow: none;
}
.profile-header::before { background: none; }
.profile-header::after { content: ''; position: absolute; right: 36px; bottom: 26px; width: 132px; height: 1px; background: var(--accent-lime); box-shadow: 0 0 16px var(--accent-lime); }
.profile-header-content { position: relative; z-index: 1; display: grid; gap: 8px; }
.profile-kicker, .member-label { font-family: var(--font-display); font-size: 11px; font-weight: 700; letter-spacing: .13em; color: var(--accent-lime); }
.profile-header p { margin: 0; color: var(--text-secondary); font-size: 15px; }
.user-card, .order-overview, .feature-nav, .recommended-products { border-radius: var(--radius-none); border: 1px solid var(--border-card); background: var(--bg-card); box-shadow: 0 20px 46px rgba(0, 0, 0, .2); }
.user-card { display: flex; align-items: center; gap: 30px; padding: 32px; position: relative; overflow: hidden; }
.user-card::before { content: ''; position: absolute; top: 0; left: 0; width: 4px; height: 100%; background: var(--accent-purple); box-shadow: 0 0 18px var(--accent-purple); }
.user-card:hover { transform: none; box-shadow: 0 20px 46px rgba(0, 0, 0, .2); }
.user-avatar { margin-right: 0; }
.avatar { border: 2px solid rgba(204, 255, 0, .75); background: var(--bg-surface); color: var(--accent-lime); box-shadow: 0 0 0 8px rgba(204, 255, 0, .05), 0 12px 28px rgba(0, 0, 0, .3); }
.user-info { display: grid; flex: 1; grid-template-columns: minmax(180px, 1fr) auto auto; align-items: center; gap: 28px; min-width: 0; }
.member-label { grid-column: 1 / -1; margin-bottom: -18px; }
.user-main-info { gap: 6px; }
.user-name { color: var(--text-primary); font-family: var(--font-heading); font-size: clamp(26px, 3vw, 36px); font-weight: 900; letter-spacing: .04em; }
.user-phone { color: var(--text-secondary); font-family: var(--font-display); font-size: 13px; }
.user-stats { gap: 0; padding: 0; border: 1px solid var(--border-card); background: rgba(255,255,255,.025); }
.stat-item { min-width: 76px; padding: 11px 14px; }
.stat-value { color: var(--accent-lime); font-family: var(--font-display); font-size: 24px; }
.stat-label { color: var(--text-tertiary); font-size: 11px; }
.stat-divider { height: 100%; background: var(--border-card); }
.edit-btn { align-self: center; height: 42px; padding: 0 18px; border-radius: var(--radius-none); background: var(--accent-purple); border-color: var(--accent-purple); box-shadow: 0 8px 20px rgba(209, 0, 255, .22); }
.edit-btn:hover { background: #b800e6; transform: translateY(-2px); box-shadow: 0 12px 26px rgba(209, 0, 255, .35); }
.order-overview, .feature-nav, .recommended-products { padding: 26px 28px 28px; margin-bottom: 20px; }
.section-header { margin-bottom: 20px; }
.section-title { padding-left: 14px; color: var(--text-primary); font-family: var(--font-heading); font-weight: 850; letter-spacing: .04em; }
.section-title::before { content: ''; position: absolute; top: 50%; left: 0; width: 4px; height: 18px; background: var(--accent-purple); box-shadow: 0 0 12px var(--accent-purple); transform: translateY(-50%); }
.view-all-btn { color: var(--accent-lime); font-family: var(--font-heading); }
.order-stats { display: grid; grid-template-columns: repeat(5, minmax(0,1fr)); gap: 10px; }
.order-stat-item, .feature-item { border-radius: var(--radius-none); background: rgba(255,255,255,.025); border: 1px solid var(--border-card); outline: none; }
.order-stat-item { min-width: 0; min-height: 132px; padding: 18px 10px; }
.order-stat-item:hover, .order-stat-item:focus-visible, .feature-item:hover, .feature-item:focus-visible { background: rgba(209,0,255,.09); border-color: rgba(209,0,255,.68); transform: translateY(-4px); box-shadow: 0 10px 22px rgba(0,0,0,.2); }
.icon, .feature-icon { color: var(--text-secondary); }
.order-stat-item:hover .icon, .order-stat-item:focus-visible .icon, .feature-item:hover .feature-icon, .feature-item:focus-visible .feature-icon { color: var(--accent-lime); }
.order-stat-text, .feature-text { color: var(--text-secondary); font-size: 13px; font-weight: 600; }
.order-badge, .feature-badge { background: var(--accent-red); box-shadow: 0 0 14px rgba(255,42,42,.42); }
.feature-grid { grid-template-columns: repeat(6, minmax(0,1fr)); gap: 10px; }
.feature-item { min-height: 118px; padding: 16px 8px; }
.recommended-grid { grid-template-columns: repeat(3, minmax(0,1fr)); gap: 14px; }
.recommended-item { border-radius: var(--radius-none); background: var(--bg-surface); border: 1px solid var(--border-card); outline: none; }
.recommended-item:hover, .recommended-item:focus-visible { border-color: rgba(204,255,0,.64); transform: translateY(-4px); box-shadow: 0 12px 26px rgba(0,0,0,.25); }
.recommended-image { height: auto; aspect-ratio: 4 / 3; background: var(--bg-elevated); }
.recommended-info { padding: 15px; }
.recommended-name { color: var(--text-primary); font-size: 14px; }
.recommended-price { color: var(--accent-lime); font-family: var(--font-display); }
.logout-section { margin-top: 32px; }
.logout-btn { border-radius: var(--radius-none); font-family: var(--font-heading); }
.order-stat-item:focus-visible, .feature-item:focus-visible, .recommended-item:focus-visible { outline: 2px solid var(--accent-lime); outline-offset: 3px; }
@media (max-width: 1024px) { .user-info { grid-template-columns: 1fr auto; } .user-stats { grid-column: 1 / -1; } .feature-grid { grid-template-columns: repeat(3, minmax(0,1fr)); } }
@media (max-width: 768px) { .profile-container { padding: 0 var(--space-md) 48px; } .profile-header { margin: 16px 0 -46px; padding: 22px; } .user-card { gap: 20px; padding: 24px; } .order-overview, .feature-nav, .recommended-products { padding: 22px; } .order-stats { grid-template-columns: repeat(3,minmax(0,1fr)); } .recommended-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (max-width: 560px) { .user-card { flex-direction: column; align-items: center; text-align: center; } .user-info { width: 100%; grid-template-columns: 1fr; justify-items: center; gap: 20px; } .member-label { margin-bottom: -10px; } .user-stats { width: 100%; } .edit-btn { width: 100%; } .order-stats, .feature-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } .order-stat-item:last-child { grid-column: 1 / -1; } .section-header { align-items: flex-start; flex-direction: column; gap: 8px; } }
@media (max-width: 400px) { .profile-container { padding-right: 12px; padding-left: 12px; } .user-card, .order-overview, .feature-nav, .recommended-products { padding: 18px; } .user-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); } .stat-item { min-width: 0; padding: 10px 4px; } .stat-divider { display: none; } .recommended-grid { grid-template-columns: 1fr; } }
</style>