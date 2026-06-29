<template>
  <div class="app-shell">
    <!-- 霓虹光束背景层 -->
    <div class="neon-beam-layer">
      <div class="neon-beam" v-for="n in 3" :key="n" :style="beamStyle(n)"></div>
    </div>

    <!-- 导航栏 -->
    <nav class="navbar" :class="{ 'nav-scrolled': isScrolled }">
      <div class="navbar-inner">
        <router-link to="/" class="logo-link">
          <span class="logo-text">STAND</span>
          <span class="logo-slash">/</span>
          <span class="logo-sub">MARKET</span>
        </router-link>

        <!-- 桌面菜单 -->
        <div class="desktop-nav">
          <router-link to="/" class="nav-item" :class="{ active: $route.path === '/' }">
            <span class="nav-label">首页</span>
            <span class="nav-line"></span>
          </router-link>
          <router-link to="/product/list" class="nav-item" :class="{ active: $route.path.startsWith('/product') }">
            <span class="nav-label">商品</span>
            <span class="nav-line"></span>
          </router-link>
          <router-link to="/seckill" class="nav-item" :class="{ active: $route.path === '/seckill' }">
            <span class="nav-label">秒杀</span>
            <span class="nav-line"></span>
          </router-link>
          <router-link to="/special-offer" class="nav-item" :class="{ active: $route.path === '/special-offer' }">
            <span class="nav-label">特价</span>
            <span class="nav-line"></span>
          </router-link>
          <router-link to="/cart" class="nav-item nav-cart" :class="{ active: $route.path === '/cart' }">
            <span class="nav-label">购物车</span>
            <span class="cart-dot" v-if="cartCount > 0">{{ cartCount }}</span>
            <span class="nav-line"></span>
          </router-link>
          <router-link to="/order" class="nav-item" :class="{ active: $route.path === '/order' }">
            <span class="nav-label">订单</span>
            <span class="nav-line"></span>
          </router-link>
        </div>

        <!-- 用户区域 -->
        <div class="user-zone">
          <div v-if="isLoggedIn" class="user-menu" @click="toggleUserDropdown">
            <span class="user-avatar">{{ userInitial }}</span>
            <span class="user-name">{{ userName }}</span>
            <span class="dropdown-arrow" :class="{ open: isUserDropdownOpen }">▾</span>
          </div>
          <router-link v-else to="/login" class="login-link">登录</router-link>

          <div class="user-dropdown-panel" v-if="isUserDropdownOpen && isLoggedIn">
            <div class="dropdown-item" @click="goToProfile">个人中心</div>
            <div class="dropdown-item" @click="goToSettings">设置</div>
            <div class="dropdown-divider"></div>
            <div class="dropdown-item dropdown-danger" @click="logout">退出登录</div>
          </div>
        </div>

        <!-- 移动端切换 -->
        <div class="mobile-toggle" @click="toggleMobileMenu">
          <span class="toggle-bar"></span>
          <span class="toggle-bar"></span>
          <span class="toggle-bar"></span>
        </div>
      </div>
    </nav>

    <!-- 移动端菜单 -->
    <div class="mobile-overlay" v-if="isMobileMenuOpen" @click.self="toggleMobileMenu">
      <div class="mobile-panel">
        <router-link v-for="item in mobileNavItems" :key="item.path" :to="item.path"
          class="mobile-nav-item" @click="toggleMobileMenu">
          {{ item.label }}
        </router-link>
        <div class="mobile-divider"></div>
        <router-link v-if="!isLoggedIn" to="/login" class="mobile-nav-item" @click="toggleMobileMenu">登录</router-link>
        <router-link v-if="isLoggedIn" to="/profile" class="mobile-nav-item" @click="toggleMobileMenu">个人中心</router-link>
        <div v-if="isLoggedIn" class="mobile-nav-item mobile-logout" @click="logout">退出登录</div>
      </div>
    </div>

    <!-- 主要内容 -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-divider"></div>
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">STAND/MARKET</span>
          <span class="footer-tagline">STREET TECHWEAR / 街头机能</span>
        </div>
        <div class="footer-links">
          <div class="footer-col">
            <span class="footer-heading">// 导航</span>
            <router-link to="/product/list">全部商品</router-link>
            <router-link to="/seckill">限时秒杀</router-link>
            <router-link to="/special-offer">特价商品</router-link>
            <router-link to="/cart">购物车</router-link>
          </div>
          <div class="footer-col">
            <span class="footer-heading">// 服务</span>
            <a href="#">配送说明</a>
            <a href="#">退换政策</a>
            <a href="#">尺码指南</a>
          </div>
          <div class="footer-col">
            <span class="footer-heading">// 联系</span>
            <span class="footer-text">service@standmarket.com</span>
            <span class="footer-text">400-888-0000</span>
            <span class="footer-text">MON-FRI 10:00-22:00</span>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        <span>© 2026 STAND/MARKET</span>
        <span class="footer-sep">|</span>
        <a href="#">PRIVACY</a>
        <span class="footer-sep">|</span>
        <a href="#">TERMS</a>
      </div>
    </footer>

      <!-- AI 智能导购 -->
      <AgentChat v-if="isLoggedIn" />
  </div>
</template>

<script>

export default {
  name: 'App',
  data() {
    return {
      isScrolled: false,
      isLoggedIn: false,
      userInitial: '',
      userName: '',
      isMobileMenuOpen: false,
      isUserDropdownOpen: false,
      mobileNavItems: [
        { path: '/', label: '首页' },
        { path: '/product/list', label: '商品' },
        { path: '/seckill', label: '秒杀' },
        { path: '/special-offer', label: '特价' },
        { path: '/cart', label: '购物车' },
        { path: '/order', label: '订单' }
      ]
    }
  },
  computed: {
    cartCount() {
      const cart = localStorage.getItem('cart')
      if (cart) {
        try {
          return JSON.parse(cart).reduce((t, i) => t + (i.quantity || 0), 0)
        } catch {
          return 0
        }
      }
      return 0
    }
  },
  mounted() {
    window.addEventListener('scroll', this.handleScroll)
    document.addEventListener('click', this.handleClickOutside)
    this.initUserStatus()
  },
  beforeUnmount() {
    window.removeEventListener('scroll', this.handleScroll)
    document.removeEventListener('click', this.handleClickOutside)
  },
  methods: {
    initUserStatus() {
      const token = localStorage.getItem('token')
      this.isLoggedIn = token !== null
      const raw = localStorage.getItem('userInfo')
      if (raw) {
        try {
          const info = JSON.parse(raw)
          this.userName = info.name || info.username || ''
          this.userInitial = this.userName ? this.userName.charAt(0).toUpperCase() : '?'
        } catch {
          /* ignore */
        }
      }
    },
    handleScroll() {
      this.isScrolled = window.scrollY > 30
    },
    handleClickOutside(e) {
      if (!e.target.closest('.user-zone')) {
        this.isUserDropdownOpen = false
      }
    },
    toggleMobileMenu() {
      this.isMobileMenuOpen = !this.isMobileMenuOpen
      if (this.isMobileMenuOpen) {
        document.body.style.overflow = 'hidden'
      } else {
        document.body.style.overflow = ''
      }
    },
    toggleUserDropdown() {
      this.isUserDropdownOpen = !this.isUserDropdownOpen
    },
    goToProfile() {
      this.$router.push('/profile')
      this.isUserDropdownOpen = false
    },
    goToSettings() {
      this.$router.push('/settings')
      this.isUserDropdownOpen = false
    },
    logout() {
      import('./api/user.js').then(({ userApi }) => {
        userApi.logout().finally(() => {
          localStorage.removeItem('token')
          localStorage.removeItem('userInfo')
          this.isLoggedIn = false
          this.userInitial = ''
          this.userName = ''
          this.isUserDropdownOpen = false
          this.isMobileMenuOpen = false
          this.$router.push('/')
        })
      })
    },
    beamStyle(n) {
      const delay = (n - 1) * 5 + Math.random() * 3
      const duration = 6 + Math.random() * 4
      const top = 15 + (n - 1) * 30 + Math.random() * 10
      return {
        animationDelay: `${delay}s`,
        animationDuration: `${duration}s`,
        top: `${top}%`
      }
    }
  }
}
</script>

<style scoped>
/* ============================================================
   APP SHELL — 街头机能风全局布局
   ============================================================ */

.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
  position: relative;
}

/* === 霓虹光束背景 === */
.neon-beam-layer {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
  overflow: hidden;
}

.neon-beam {
  position: absolute;
  left: -100%;
  width: 60%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(204, 255, 0, 0.08),
    rgba(204, 255, 0, 0.15),
    rgba(204, 255, 0, 0.08),
    transparent
  );
  animation: beamSweep 10s linear infinite;
  filter: blur(1px);
}

/* ============================================================
   NAVBAR — 悬浮导航，无边界
   ============================================================ */

.navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--z-navbar);
  height: 64px;
  background-color: rgba(10, 10, 10, 0.92);
  backdrop-filter: blur(12px);
  transition: var(--transition-base);
}

.navbar-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* === LOGO === */
.logo-link {
  display: flex;
  align-items: baseline;
  gap: 2px;
  text-decoration: none;
  transform: skewX(-8deg);
  transition: var(--transition-base);
}

.logo-link:hover {
  transform: skewX(-8deg) scale(1.05);
}

.logo-text {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 22px;
  color: var(--text-primary);
  letter-spacing: 0.08em;
}

.logo-slash {
  color: var(--accent-purple);
  font-weight: 400;
  font-size: 22px;
  animation: neonPulse 2s ease-in-out infinite;
}

.logo-sub {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: 0.15em;
}

/* === 桌面导航 === */
.desktop-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  position: relative;
  padding: 8px 20px;
  text-decoration: none;
  transition: var(--transition-base);
}

.nav-label {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
  transition: var(--transition-base);
}

.nav-line {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 2px;
  background-color: var(--accent-purple);
  transition: var(--transition-base);
}

.nav-item:hover .nav-label,
.nav-item.active .nav-label {
  color: var(--text-primary);
}

.nav-item:hover .nav-line,
.nav-item.active .nav-line {
  width: 60%;
}

.nav-cart {
  position: relative;
}

.cart-dot {
  position: absolute;
  top: 0;
  right: 8px;
  min-width: 18px;
  height: 18px;
  background-color: var(--accent-lime);
  color: var(--bg-primary);
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  border-radius: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  animation: neonPulse 2s ease-in-out infinite;
}

/* === 用户区域 === */
.user-zone {
  position: relative;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  cursor: pointer;
  transition: var(--transition-base);
  border: 1px solid transparent;
}

.user-menu:hover {
  border-color: var(--border-subtle);
}

.user-avatar {
  width: 30px;
  height: 30px;
  background-color: var(--accent-purple);
  color: #fff;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 13px;
  color: var(--text-secondary);
}

.dropdown-arrow {
  color: var(--text-tertiary);
  font-size: 12px;
  transition: var(--transition-fast);
}

.dropdown-arrow.open {
  transform: rotate(180deg);
  color: var(--accent-purple);
}

.login-link {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-secondary);
  text-decoration: none;
  padding: 8px 18px;
  border: 1px solid var(--border-subtle);
  transition: var(--transition-base);
}

.login-link:hover {
  color: var(--text-primary);
  border-color: var(--accent-purple);
}

/* 用户下拉面板 */
.user-dropdown-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  min-width: 180px;
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-subtle);
  z-index: var(--z-overlay);
  animation: floatIn 0.2s ease;
}

.dropdown-item {
  padding: 12px 20px;
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.dropdown-item:hover {
  background-color: rgba(209, 0, 255, 0.1);
  color: var(--text-primary);
}

.dropdown-divider {
  height: 1px;
  background-color: var(--border-subtle);
}

.dropdown-danger {
  color: var(--accent-red);
}

.dropdown-danger:hover {
  background-color: rgba(255, 42, 42, 0.1);
}

/* === 移动端切换按钮 === */
.mobile-toggle {
  display: none;
  flex-direction: column;
  gap: 5px;
  cursor: pointer;
  padding: 8px;
}

.toggle-bar {
  width: 22px;
  height: 2px;
  background-color: var(--text-primary);
  transition: var(--transition-base);
}

/* === 移动端覆盖面板 === */
.mobile-overlay {
  position: fixed;
  inset: 0;
  top: 64px;
  background-color: rgba(0, 0, 0, 0.85);
  backdrop-filter: blur(8px);
  z-index: var(--z-overlay);
  display: flex;
  flex-direction: column;
}

.mobile-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--space-lg);
  gap: 0;
}

.mobile-nav-item {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 18px;
  color: var(--text-secondary);
  text-decoration: none;
  padding: 16px 0;
  border-bottom: 1px solid var(--border-card);
  transition: var(--transition-base);
}

.mobile-nav-item:hover {
  color: var(--accent-purple);
  padding-left: 12px;
}

.mobile-divider {
  height: 1px;
  background-color: var(--border-subtle);
  margin: 8px 0;
}

.mobile-logout {
  color: var(--accent-red);
  cursor: pointer;
}

/* ============================================================
   MAIN CONTENT
   ============================================================ */

.main-content {
  flex: 1;
  padding-top: 64px;
  position: relative;
  z-index: 1;
}

/* 页面切换动画 */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* ============================================================
   FOOTER — 极简工业风
   ============================================================ */

.footer {
  position: relative;
  z-index: 1;
  padding: 0;
  margin-top: var(--space-2xl);
}

.footer-divider {
  width: 100%;
  height: 1px;
  background-color: var(--border-subtle);
}

.footer-inner {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--space-xl) var(--space-lg);
  display: flex;
  justify-content: space-between;
  gap: var(--space-xl);
  flex-wrap: wrap;
}

.footer-brand {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.footer-logo {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 18px;
  color: var(--text-primary);
  letter-spacing: 0.08em;
}

.footer-tagline {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--accent-purple);
  letter-spacing: 0.2em;
}

.footer-links {
  display: flex;
  gap: var(--space-xl);
  flex-wrap: wrap;
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.footer-heading {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-purple);
  font-weight: 700;
  letter-spacing: 0.1em;
  margin-bottom: 4px;
}

.footer-col a {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  transition: var(--transition-fast);
}

.footer-col a:hover {
  color: var(--text-primary);
}

.footer-text {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 500;
  color: var(--text-tertiary);
}

.footer-bottom {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--border-card);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.footer-bottom a {
  color: var(--text-tertiary);
  transition: var(--transition-fast);
}

.footer-bottom a:hover {
  color: var(--accent-purple);
}

.footer-sep {
  color: var(--text-tertiary);
  opacity: 0.4;
}

/* ============================================================
   RESPONSIVE
   ============================================================ */

@media (max-width: 768px) {
  .desktop-nav {
    display: none;
  }

  .mobile-toggle {
    display: flex;
  }

  .user-zone .user-menu,
  .user-zone .login-link {
    display: none;
  }

  .logo-text {
    font-size: 18px;
  }

  .logo-sub {
    font-size: 11px;
  }

  .footer-inner {
    flex-direction: column;
    gap: var(--space-lg);
  }

  .footer-links {
    gap: var(--space-lg);
  }

  .footer-bottom {
    flex-wrap: wrap;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .navbar-inner {
    padding: 0 var(--space-md);
  }

  .logo-link {
    transform: skewX(-6deg);
  }

  .footer-inner {
    padding: var(--space-lg) var(--space-md);
  }
}
</style>