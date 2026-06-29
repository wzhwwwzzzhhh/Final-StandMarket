<template>
  <div class="home">
    <!-- HERO 非对称大图区域 -->
    <section class="hero">
      <div class="hero-grid">
        <div class="hero-main" @click="viewAllProducts">
          <img :src="carouselItems[0].image" :alt="carouselItems[0].title" class="hero-img" />
          <div class="hero-img-overlay"></div>
          <div class="hero-label">// 01</div>
        </div>
        <div class="hero-side">
          <div class="hero-side-top">
            <span class="hero-tag">NEW DROP</span>
            <h1 class="hero-title">{{ carouselItems[0].title }}</h1>
            <p class="hero-sub">{{ carouselItems[0].subtitle }}</p>
            <button class="hero-cta" @click="viewAllProducts">
              <span>立即抢购</span>
              <span class="cta-arrow">→</span>
            </button>
          </div>
          <div class="hero-side-bottom" @click="$router.push('/seckill')">
            <img :src="carouselItems[1].image" :alt="carouselItems[1].title" class="hero-thumb" />
            <div class="hero-thumb-overlay">
              <span class="hero-thumb-label">{{ carouselItems[1].title }}</span>
              <span class="hero-thumb-sub">{{ carouselItems[1].subtitle }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 全屏分割线 -->
    <div class="section-divider"></div>

    <!-- 商品分类 -->
    <section class="category-section">
      <h2 class="section-heading">
        <span class="heading-slash">//</span>
        <span class="heading-text">分类</span>
        <span class="heading-line"></span>
      </h2>
      <div class="category-grid">
        <div class="category-card" v-for="(cat, i) in categories" :key="cat.id"
          :style="{ transform: `translateY(${(i % 2) * 12}px)` }"
          @click="goToCategory(cat.id)">
          <div class="category-img-wrap">
            <img :src="cat.image" :alt="cat.name" class="category-img" />
            <div class="category-grain"></div>
          </div>
          <div class="category-label">
            <span class="cat-index">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="cat-name">{{ cat.name }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 全屏分割线 -->
    <div class="section-divider"></div>

    <!-- 热门商品 -->
    <section class="products-section">
      <h2 class="section-heading">
        <span class="heading-slash">//</span>
        <span class="heading-text">热销</span>
        <span class="heading-line"></span>
        <button class="heading-cta" @click="viewAllProducts">查看全部 →</button>
      </h2>
      <div class="product-grid-asym">
        <div class="product-card" v-for="(product, i) in hotProducts" :key="product.id"
          :class="[`card-pos-${i % 4}`]"
          :style="{ animationDelay: `${i * 0.1}s` }">
          <div class="card-img-wrap" @click="viewProductDetail(product.id)">
            <img :src="product.image" :alt="product.name" class="card-img" />
            <div class="card-img-filter"></div>
            <div class="card-price-tag">
              <span class="price-symbol">¥</span>
              <span class="price-value">{{ product.price }}</span>
            </div>
          </div>
          <div class="card-body">
            <h3 class="card-name" @click="viewProductDetail(product.id)">{{ product.name }}</h3>
            <div class="card-meta">
              <span class="card-sales">{{ product.sales || 0 }} SOLD</span>
              <div class="card-rating">
                <span class="rating-star" v-for="n in 5" :key="n"
                  :class="{ filled: n <= (product.rating || 0) }">★</span>
              </div>
            </div>
            <button class="card-add-btn" @click="addProductToCart(product.id)">
              加入购物车
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- 全屏分割线 -->
    <div class="section-divider"></div>

    <!-- 秒杀活动 -->
    <section class="seckill-section">
      <div class="seckill-header">
        <div class="seckill-heading">
          <span class="seckill-icon">⚡</span>
          <h2 class="seckill-title">FLASH SALE</h2>
          <span class="seckill-sub">限时秒杀</span>
        </div>
        <div class="countdown-block">
          <span class="countdown-label">ENDS IN</span>
          <div class="countdown-digits">
            <el-countdown :value="endTime" format="HH:mm:ss" @finish="handleCountdownFinish" />
          </div>
        </div>
      </div>
      <div class="seckill-grid">
        <div class="seckill-card" v-for="coupon in seckillCoupons" :key="coupon.id">
          <div class="seckill-card-top">
            <span class="coupon-name">{{ coupon.name }}</span>
            <span class="coupon-stock" :class="{ low: coupon.stock < 10 }">
              {{ coupon.stock > 0 ? `STOCK:${coupon.stock}` : 'SOLD OUT' }}
            </span>
          </div>
          <div class="seckill-card-price">
            <span class="price-original">¥{{ coupon.originalPrice }}</span>
            <span class="price-seckill">¥{{ coupon.seckillPrice }}</span>
          </div>
          <div class="seckill-card-bar">
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: progressPct(coupon) + '%' }"></div>
            </div>
            <span class="bar-label">{{ Math.round((1 - coupon.seckillPrice / coupon.originalPrice) * 100) }}% OFF</span>
          </div>
          <button class="seckill-btn" :class="{ soldout: coupon.stock <= 0 }"
            :disabled="coupon.stock <= 0" @click="seckillCoupon(coupon.id)">
            {{ coupon.stock > 0 ? '立即抢购' : '已售罄' }}
          </button>
        </div>
      </div>
    </section>

    <!-- 全屏分割线 -->
    <div class="section-divider"></div>

    <!-- 品牌故事 -->
    <section class="brand-section">
      <div class="brand-grid">
        <div class="brand-visual">
          <div class="brand-img-placeholder">
            <span class="brand-big-text">STAND</span>
            <span class="brand-big-sub">/MARKET</span>
          </div>
        </div>
        <div class="brand-content">
          <h2 class="brand-heading">
            <span class="heading-slash">//</span>
            ABOUT
          </h2>
          <p class="brand-desc">
            我们是一家专注于街头机能风格的服装品牌，致力于将工业美学与日常穿着相结合。
            采用优质面料与精湛工艺，为城市探索者打造独特的个人风格。
          </p>
          <p class="brand-desc">
            从地下文化汲取灵感，以解构主义重塑时装。每一件单品都是对常规的反叛。
          </p>
          <button class="brand-cta" @click="viewAboutUs">了解更多 →</button>
        </div>
      </div>
    </section>

    <!-- 尺码选择弹窗 -->
    <el-dialog v-model="cartDialogVisible" title="// 选择规格" width="420px" :close-on-click-modal="false" class="size-dialog">
      <div v-if="selectedProduct" class="dialog-body">
        <div class="dialog-product">
          <img :src="selectedProduct.image" :alt="selectedProduct.name" class="dialog-img" />
          <div class="dialog-info">
            <h4>{{ selectedProduct.name }}</h4>
            <span class="dialog-price">¥{{ selectedProduct.price }}</span>
          </div>
        </div>
        <div class="dialog-size">
          <span class="dialog-label">尺码</span>
          <div class="size-options">
            <button v-for="size in sizes" :key="size"
              :class="['size-btn', { active: selectedSize === size }]"
              @click="selectedSize = size">{{ size }}</button>
          </div>
        </div>
        <div class="dialog-qty">
          <span class="dialog-label">数量</span>
          <el-input-number v-model="quantity" :min="1" :max="99" size="small" />
        </div>
      </div>
      <template #footer>
        <button class="dialog-cancel" @click="cartDialogVisible = false">取消</button>
        <button class="dialog-confirm" @click="confirmAddToCart">加入购物车</button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { productApi, cartApi } from '@/api/product'
import banner1 from '@/assets/images/promotions/新对话.png'
import banner2 from '@/assets/images/promotions/新对话 (1).png'
import banner3 from '@/assets/images/promotions/新对话 (2).png'
import category1 from '@/assets/images/clothes/新对话 (3).png'
import category2 from '@/assets/images/clothes/新对话 (5).png'
import category3 from '@/assets/images/shoes/新对话 (10).png'
import category4 from '@/assets/images/accessories/新对话 (13).png'

export default {
  name: 'Home',
  data() {
    return {
      carouselItems: [
        { image: banner1, title: '机能风系列', subtitle: '全场满300减50' },
        { image: banner2, title: '限时秒杀', subtitle: '低至5折' },
        { image: banner3, title: '会员专享', subtitle: '额外9折优惠' }
      ],
      endTime: new Date().getTime() + 60 * 60 * 1000,
      categories: [
        { id: 1, name: '衣服', image: category1 },
        { id: 2, name: '裤子', image: category2 },
        { id: 3, name: '鞋子', image: category3 },
        { id: 4, name: '配饰', image: category4 }
      ],
      hotProducts: [],
      seckillCoupons: [
        { id: 1, name: '夏季T恤秒杀券', originalPrice: 199, seckillPrice: 99, stock: 50, totalStock: 100 },
        { id: 2, name: '运动鞋秒杀券', originalPrice: 399, seckillPrice: 199, stock: 30, totalStock: 80 },
        { id: 3, name: '休闲裤秒杀券', originalPrice: 299, seckillPrice: 149, stock: 40, totalStock: 90 }
      ],
      cartDialogVisible: false,
      selectedProduct: null,
      selectedSize: 'M',
      sizes: ['S', 'M', 'L', 'XL', 'XXL'],
      quantity: 1
    }
  },
  created() {
    this.getHotProducts()
  },
  methods: {
    getHotProducts() {
      productApi.getProductList({ page: 1, pageSize: 4, sortBy: 'sales' })
        .then(res => {
          if (res.data.code === 1) {
            this.hotProducts = res.data.data.records
          }
        })
        .catch(err => console.error('获取热门商品失败:', err))
    },
    goToCategory(id) {
      const tagMap = { 1: '衣服', 2: '裤子', 3: '鞋子', 4: '配饰' }
      this.$router.push({ path: '/product/list', query: { tag: tagMap[id] || '' } })
    },
    viewAllProducts() {
      this.$router.push('/product/list')
    },
    viewProductDetail(id) {
      this.$router.push(`/product/detail/${id}`)
    },
    addProductToCart(id) {
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      const product = this.hotProducts.find(p => p.id === id)
      if (product) {
        this.selectedProduct = product
        this.selectedSize = 'M'
        this.quantity = 1
        this.cartDialogVisible = true
      }
    },
    confirmAddToCart() {
      if (!this.selectedProduct) return
      const data = {
        name: this.selectedProduct.name,
        image: this.selectedProduct.image,
        productId: this.selectedProduct.id,
        skuInfo: this.selectedSize,
        number: this.quantity,
        amount: this.selectedProduct.price * this.quantity
      }
      cartApi.addToCart(data).then(res => {
        if (res.data.code === 1) {
          this.$message.success(`已添加 "${this.selectedProduct.name}" (${this.selectedSize}码) × ${this.quantity}`)
          this.cartDialogVisible = false
        } else {
          this.$message.error(res.data.msg || '添加失败')
        }
      }).catch(() => {
        this.$message.error('网络错误')
      })
    },
    seckillCoupon(id) {
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      this.$message.success('抢购成功！')
    },
    handleCountdownFinish() {},
    viewAboutUs() {},
    progressPct(coupon) {
      return coupon.totalStock ? 100 - Math.round((coupon.stock / coupon.totalStock) * 100) : 0
    }
  }
}
</script>

<style scoped>
/* ============================================================
   HOME — 街头机能风首页
   ============================================================ */

.home {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  animation: floatIn 0.5s ease;
}

/* === 全屏分割线 === */
.section-divider {
  width: 100%;
  height: 1px;
  background-color: var(--border-subtle);
  margin: var(--space-xl) 0;
}

/* === SECTION HEADING === */
.section-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: var(--space-xl);
}

.heading-slash {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 700;
  color: var(--accent-purple);
}

.heading-text {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 28px;
  color: var(--text-primary);
  letter-spacing: 0.08em;
}

.heading-line {
  flex: 1;
  height: 1px;
  background-color: var(--border-subtle);
  margin-left: 12px;
}

.heading-cta {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  background: none;
  border: 1px solid var(--border-subtle);
  padding: 8px 18px;
  cursor: pointer;
  transition: var(--transition-base);
}

.heading-cta:hover {
  color: var(--accent-purple);
  border-color: var(--accent-purple);
}

/* ============================================================
   HERO — 非对称网格 (2:1)
   ============================================================ */

.hero {
  margin-top: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.hero-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 4px;
}

.hero-main {
  position: relative;
  cursor: pointer;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  background-color: var(--bg-card);
}

.hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-slow);
}

.hero-main:hover .hero-img {
  transform: scale(1.05);
  filter: grayscale(40%) brightness(0.7);
}

.hero-img-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(45deg, rgba(10,10,10,0.7) 0%, transparent 60%);
}

.hero-label {
  position: absolute;
  bottom: 24px;
  left: 24px;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--accent-lime);
  letter-spacing: 0.15em;
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-side-top {
  flex: 1;
  background-color: var(--bg-elevated);
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-lg);
  border: 1px solid var(--border-card);
}

.hero-tag {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-lime);
  letter-spacing: 0.3em;
  margin-bottom: 12px;
}

.hero-title {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 32px;
  color: var(--text-primary);
  line-height: 1.1;
  margin-bottom: 8px;
  letter-spacing: 0.04em;
}

.hero-sub {
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.hero-cta {
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--accent-purple);
  color: #fff;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 14px;
  letter-spacing: 0.06em;
  border: none;
  padding: 12px 28px;
  cursor: pointer;
  transition: var(--transition-base);
  position: relative;
  overflow: hidden;
}

.hero-cta::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.hero-cta:hover::after {
  transform: translateX(100%);
}

.hero-cta:hover {
  box-shadow: 0 0 40px var(--accent-purple-dim);
}

.cta-arrow {
  transition: var(--transition-fast);
}

.hero-cta:hover .cta-arrow {
  transform: translateX(4px);
}

.hero-side-bottom {
  flex: 1;
  position: relative;
  cursor: pointer;
  overflow: hidden;
  background-color: var(--bg-card);
}

.hero-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-slow);
}

.hero-side-bottom:hover .hero-thumb {
  transform: scale(1.08);
  filter: brightness(0.6);
}

.hero-thumb-overlay {
  position: absolute;
  bottom: 16px;
  left: 16px;
  right: 16px;
}

.hero-thumb-label {
  display: block;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 18px;
  color: #fff;
}

.hero-thumb-sub {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-lime);
}

/* ============================================================
   CATEGORIES — 错位排列
   ============================================================ */

.category-section {
  margin: var(--space-xl) 0;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
}

.category-card {
  cursor: pointer;
  transition: var(--transition-base);
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
}

.category-card:hover {
  border-color: var(--accent-purple);
  z-index: 2;
}

.category-img-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
}

.category-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-slow);
}

.category-card:hover .category-img {
  transform: scale(1.12);
  filter: grayscale(60%);
}

.category-grain {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(209,0,255,0.15) 0%, transparent 50%);
  opacity: 0;
  transition: var(--transition-base);
}

.category-card:hover .category-grain {
  opacity: 1;
}

.category-label {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-card);
}

.cat-index {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-purple);
  font-weight: 700;
}

.cat-name {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

/* ============================================================
   PRODUCTS — 不规则拼贴卡片
   ============================================================ */

.products-section {
  margin: var(--space-xl) 0;
}

.product-grid-asym {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}

.product-card {
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
  transition: var(--transition-base);
  animation: floatIn 0.5s ease backwards;
}

.product-card:hover {
  border-color: var(--accent-purple);
  z-index: 3;
}

/* 错位变体 */
.card-pos-0 { margin-top: 0; }
.card-pos-1 { margin-top: 24px; }
.card-pos-2 { margin-top: -12px; }
.card-pos-3 { margin-top: 16px; }

.card-img-wrap {
  position: relative;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  cursor: pointer;
  background-color: var(--bg-surface);
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-slow);
}

.product-card:hover .card-img {
  transform: scale(1.1);
  filter: grayscale(100%);
}

.card-img-filter {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(10,10,10,0.8) 0%, transparent 40%);
  opacity: 0;
  transition: var(--transition-base);
}

.product-card:hover .card-img-filter {
  opacity: 1;
}

/* 价格标签——底部滑入 */
.card-price-tag {
  position: absolute;
  bottom: -60px;
  left: 0;
  right: 0;
  background-color: var(--accent-lime);
  color: var(--bg-primary);
  padding: 8px 12px;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 2px;
  transition: bottom var(--transition-base);
  font-family: var(--font-display);
}

.product-card:hover .card-price-tag {
  bottom: 0;
}

.price-symbol {
  font-size: 14px;
  font-weight: 700;
}

.price-value {
  font-size: 24px;
  font-weight: 800;
  animation: priceFlicker 3s infinite;
}

/* 卡片内容 */
.card-body {
  padding: 16px;
}

.card-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  cursor: pointer;
  margin-bottom: 10px;
  line-height: 1.3;
  transition: var(--transition-fast);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-name:hover {
  color: var(--accent-purple);
}

.card-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.card-sales {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.card-rating {
  display: flex;
  gap: 2px;
}

.rating-star {
  font-size: 13px;
  color: var(--border-subtle);
}

.rating-star.filled {
  color: var(--accent-lime);
}

.card-add-btn {
  width: 100%;
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
  background: transparent;
  border: 1px solid var(--border-subtle);
  padding: 10px;
  cursor: pointer;
  transition: var(--transition-base);
}

.card-add-btn:hover {
  color: var(--text-primary);
  border-color: var(--accent-purple);
  background-color: rgba(209, 0, 255, 0.08);
}

.card-add-btn:active {
  animation: hydraulicPress 0.2s ease;
}

/* ============================================================
   SECKILL — 霓虹紫秒杀区
   ============================================================ */

.seckill-section {
  margin: var(--space-xl) 0;
  padding: var(--space-xl);
  background-color: var(--bg-elevated);
  border: 1px solid var(--accent-purple-dim);
  position: relative;
}

.seckill-section::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--accent-purple), transparent);
}

.seckill-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
  gap: var(--space-md);
}

.seckill-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}

.seckill-icon {
  font-size: 28px;
  animation: neonPulse 2s ease-in-out infinite;
}

.seckill-title {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 32px;
  color: var(--text-primary);
  letter-spacing: 0.12em;
}

.seckill-sub {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--accent-purple);
  letter-spacing: 0.2em;
}

.countdown-block {
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: var(--bg-card);
  padding: 10px 20px;
  border: 1px solid var(--border-card);
}

.countdown-label {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.15em;
}

.countdown-digits {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 800;
  color: var(--accent-lime);
}

/* Element Plus countdown override */
.countdown-digits :deep(.el-countdown__content) {
  font-family: var(--font-display);
  font-size: 24px;
  font-weight: 800;
  color: var(--accent-lime);
}

.seckill-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
}

.seckill-card {
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  transition: var(--transition-base);
}

.seckill-card:hover {
  border-color: var(--accent-purple);
}

.seckill-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.coupon-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
}

.coupon-stock {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

.coupon-stock.low {
  color: var(--accent-red);
  animation: priceFlicker 2s infinite;
}

.seckill-card-price {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.price-original {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-tertiary);
  text-decoration: line-through;
}

.price-seckill {
  font-family: var(--font-display);
  font-size: 36px;
  font-weight: 800;
  color: var(--accent-red);
  animation: priceFlicker 2.5s infinite;
}

.seckill-card-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bar-track {
  flex: 1;
  height: 4px;
  background-color: var(--bg-surface);
}

.bar-fill {
  height: 100%;
  background-color: var(--accent-purple);
  transition: width var(--transition-base);
}

.bar-label {
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  color: var(--accent-lime);
  white-space: nowrap;
}

.seckill-btn {
  width: 100%;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 14px;
  letter-spacing: 0.08em;
  color: #fff;
  background-color: var(--accent-purple);
  border: none;
  padding: 14px;
  cursor: pointer;
  transition: var(--transition-base);
  position: relative;
  overflow: hidden;
}

.seckill-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.25), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.seckill-btn:hover::after {
  transform: translateX(100%);
}

.seckill-btn:hover {
  box-shadow: 0 0 30px var(--accent-purple-dim);
}

.seckill-btn:active {
  animation: hydraulicPress 0.2s ease;
}

.seckill-btn.soldout {
  background-color: var(--bg-surface);
  color: var(--text-tertiary);
  cursor: not-allowed;
}

/* ============================================================
   BRAND STORY
   ============================================================ */

.brand-section {
  margin: var(--space-xl) 0;
}

.brand-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}

.brand-visual {
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  position: relative;
  overflow: hidden;
}

.brand-img-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.brand-big-text {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 72px;
  color: var(--text-primary);
  letter-spacing: 0.2em;
  transform: skewX(-8deg);
}

.brand-big-sub {
  font-family: var(--font-display);
  font-size: 18px;
  color: var(--accent-purple);
  letter-spacing: 0.5em;
  margin-top: -8px;
  animation: neonPulse 2s ease-in-out infinite;
}

.brand-content {
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.brand-heading {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 28px;
  color: var(--text-primary);
  letter-spacing: 0.15em;
  margin-bottom: 24px;
}

.brand-heading .heading-slash {
  font-size: 28px;
  vertical-align: middle;
}

.brand-desc {
  font-family: var(--font-heading);
  font-weight: 500;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
  max-width: 44ch;
  margin-bottom: 16px;
}

.brand-cta {
  align-self: flex-start;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--accent-purple);
  background: none;
  border: 1px solid var(--accent-purple);
  padding: 10px 24px;
  cursor: pointer;
  margin-top: 8px;
  transition: var(--transition-base);
}

.brand-cta:hover {
  background-color: var(--accent-purple);
  color: #fff;
}

/* ============================================================
   DIALOG — 尺码选择
   ============================================================ */

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.dialog-product {
  display: flex;
  gap: 16px;
}

.dialog-img {
  width: 80px;
  height: 100px;
  object-fit: cover;
  background-color: var(--bg-surface);
}

.dialog-info h4 {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.dialog-price {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 800;
  color: var(--accent-lime);
}

.dialog-label {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  display: block;
}

.size-options {
  display: flex;
  gap: 8px;
}

.size-btn {
  width: 44px;
  height: 36px;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
  cursor: pointer;
  transition: var(--transition-fast);
}

.size-btn:hover {
  border-color: var(--accent-purple);
  color: var(--text-primary);
}

.size-btn.active {
  background-color: var(--accent-purple);
  color: #fff;
  border-color: var(--accent-purple);
}

.dialog-qty {
  margin-top: 4px;
}

/* Dialog footer buttons */
.dialog-cancel {
  font-family: var(--font-heading);
  font-weight: 600;
  font-size: 13px;
  color: var(--text-secondary);
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 10px 24px;
  cursor: pointer;
  transition: var(--transition-base);
}

.dialog-cancel:hover {
  border-color: var(--text-secondary);
}

.dialog-confirm {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 13px;
  letter-spacing: 0.05em;
  color: #fff;
  background-color: var(--accent-purple);
  border: none;
  padding: 10px 24px;
  cursor: pointer;
  transition: var(--transition-base);
}

.dialog-confirm:hover {
  box-shadow: 0 0 20px var(--accent-purple-dim);
}

.dialog-confirm:active {
  animation: hydraulicPress 0.2s ease;
}

/* ============================================================
   RESPONSIVE
   ============================================================ */

@media (max-width: 1024px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .hero-side {
    flex-direction: row;
  }

  .hero-side-top,
  .hero-side-bottom {
    flex: 1;
  }

  .product-grid-asym {
    grid-template-columns: repeat(2, 1fr);
  }

  .card-pos-1,
  .card-pos-2,
  .card-pos-3 {
    margin-top: 0;
  }

  .seckill-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .brand-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .home {
    padding: 0 var(--space-md);
  }

  .category-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .product-grid-asym {
    grid-template-columns: 1fr 1fr;
    gap: 2px;
  }

  .hero-title {
    font-size: 24px;
  }

  .seckill-grid {
    grid-template-columns: 1fr;
  }

  .seckill-title {
    font-size: 24px;
  }

  .seckill-section {
    padding: var(--space-md);
  }

  .brand-big-text {
    font-size: 48px;
  }

  .brand-visual {
    min-height: 250px;
  }

  .section-heading {
    margin-bottom: var(--space-md);
  }

  .heading-text {
    font-size: 22px;
  }
}

@media (max-width: 480px) {
  .product-grid-asym {
    grid-template-columns: 1fr;
  }

  .category-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .hero-side {
    flex-direction: column;
  }

  .hero-title {
    font-size: 20px;
  }

  .seckill-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>