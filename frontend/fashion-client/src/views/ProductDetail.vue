<template>
  <div class="product-detail">
    <!-- 顶部导航 -->
    <div class="top-nav">
      <el-button type="text" @click="goBack" class="back-button">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <h1 class="page-title">商品详情</h1>
      <div class="nav-actions">
        <el-button type="text" class="action-button" @click="shareProduct">
          <el-icon><Share /></el-icon>
        </el-button>
        <el-button type="text" class="action-button" @click="toggleFavorite">
          <el-icon :size="20">{{ isFavorite ? 'StarFilled' : 'Star' }}</el-icon>
        </el-button>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated class="loading-skeleton" />
    </div>
    
    <!-- 错误提示 -->
    <div v-else-if="error" class="error-container">
      <el-empty description="" :image-size="120">
        <template #description>
          <div class="error-content">
            <p class="error-message">{{ error }}</p>
            <el-button type="primary" @click="getProductDetail" class="reload-button">
              <el-icon><Refresh /></el-icon>
              重新加载
            </el-button>
          </div>
        </template>
      </el-empty>
    </div>
    
    <!-- 商品信息 -->
    <template v-else>
      <!-- 商品图片轮播 -->
      <div class="product-image-section">
        <el-carousel :interval="5000" type="card" indicator-position="outside" height="500px" class="image-carousel">
          <el-carousel-item v-for="(img, index) in productImages" :key="index">
            <img :src="img" :alt="`${product.name} - ${index + 1}`" class="carousel-image" />
          </el-carousel-item>
        </el-carousel>
        <div class="image-thumbs">
          <div 
            v-for="(img, index) in productImages" 
            :key="index"
            class="thumb-item"
            :class="{ active: activeImageIndex === index }"
            @click="activeImageIndex = index"
          >
            <img :src="img" :alt="`${product.name} - ${index + 1}`" class="thumb-image" />
          </div>
        </div>
      </div>
      
      <!-- 商品基本信息 -->
      <div class="product-info">
        <div class="product-header">
          <h2 class="product-name">{{ product.name }}</h2>
          <div class="product-tags">
            <el-tag v-for="tag in product.tag.split(',')" :key="tag" size="small" effect="plain" class="product-tag">
              {{ tag }}
            </el-tag>
          </div>
        </div>
        
        <div class="product-price-section">
          <div class="price-container">
            <span class="price-label">价格</span>
            <span class="current-price">¥{{ product.price }}</span>
            <span v-if="product.originalPrice" class="original-price">¥{{ product.originalPrice }}</span>
            <span v-if="product.originalPrice" class="discount-tag">
              {{ Math.round((1 - product.price / product.originalPrice) * 100) }}% OFF
            </span>
          </div>
          <div class="sales-info">
            <span class="sales">销量 {{ product.sales }}</span>
            <span class="stock" :class="{ 'low-stock': product.stock < 10 }">
              库存 {{ product.stock }}
            </span>
          </div>
        </div>
        
        <div class="product-description">
          <h3 class="section-title">商品描述</h3>
          <p class="description-text">{{ product.description }}</p>
        </div>
        
        <!-- 商品规格 -->
        <div class="specs-section">
          <h3 class="section-title">商品规格</h3>
          <div class="spec-options">
            <div 
              v-for="spec in specs" 
              :key="spec" 
              class="spec-option"
              :class="{ active: selectedSpec === spec }"
              @click="selectedSpec = spec"
            >
              {{ spec }}
            </div>
          </div>
        </div>
        
        <!-- 数量选择 -->
        <div class="quantity-section">
          <h3 class="section-title">数量</h3>
          <div class="quantity-container">
            <el-input-number 
              v-model="quantity" 
              :min="1" 
              :max="product.stock" 
              class="quantity-input"
              :disabled="product.stock <= 0"
            />
            <span class="stock-info" v-if="product.stock > 0">
              库存 {{ product.stock }} 件
            </span>
            <span class="stock-info out-of-stock" v-else>
              库存不足
            </span>
          </div>
        </div>
        
        <!-- 操作按钮 -->
        <div class="action-buttons">
          <el-button type="default" class="buy-button" @click="buyNow" :disabled="product.stock <= 0">
            <el-icon><Shop /></el-icon>
            立即购买
          </el-button>
          <el-button type="primary" class="cart-button" @click="addToCart" :disabled="product.stock <= 0">
            <el-icon><ShoppingCart /></el-icon>
            加入购物车
          </el-button>
        </div>
      </div>
      
      <!-- 商品评价 -->
      <div class="product-reviews">
        <div class="reviews-header">
          <h3 class="section-title">商品评价</h3>
          <div class="review-stats">
            <div class="rating-stats">
              <span class="average-rating">{{ averageRating }}</span>
              <div class="rating-stars">
                <el-rate v-model="averageRating" disabled :max="5" :colors="['#ff4d4f']" />
              </div>
              <span class="review-count">{{ reviewTotal }} 条评价</span>
            </div>
            <el-select v-model="reviewFilter" class="review-filter">
              <el-option label="全部评价" value="all" />
              <el-option label="5星" value="5" />
              <el-option label="4星" value="4" />
              <el-option label="3星及以下" value="3" />
            </el-select>
          </div>
        </div>
        <div class="review-list" v-loading="reviewLoading">
          <div class="review-item" v-for="review in reviewList" :key="review.id">
            <div class="review-header">
              <div class="reviewer-info">
                <el-avatar class="reviewer-avatar">{{ (review.userName || '匿名用户').charAt(0) }}</el-avatar>
                <span class="reviewer-name">{{ review.userName || '匿名用户' }}</span>
              </div>
              <span class="review-time">{{ review.createTime }}</span>
              <el-rate :model-value="review.rating" disabled :max="5" :colors="['#ff4d4f']" size="small" />
            </div>
            <div class="review-content">{{ review.content }}</div>
            <div class="review-images" v-if="review.images && review.images !== ''">
              <img v-for="(img, idx) in parseReviewImages(review.images)" :key="idx" :src="img" :alt="`Review image ${idx + 1}`" class="review-image" />
            </div>
          </div>
          <div v-if="reviewList.length === 0" class="no-reviews">
            <el-empty description="暂无评价" :image-size="80" />
          </div>
          <div v-if="reviewTotal > reviewPageSize" class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="reviewTotal"
              :page-size="reviewPageSize"
              :current-page="reviewPage"
              @current-change="handleReviewPageChange"
            />
          </div>
        </div>
      </div>
      
      <!-- 相关推荐 -->
      <div class="related-products">
        <h3 class="section-title">相关推荐</h3>
        <div class="related-list">
          <div class="related-item" v-for="item in relatedProducts" :key="item.id" @click="navigateToProduct(item.id)">
            <img :src="item.image" :alt="item.name" class="related-image" />
            <h4 class="related-name">{{ item.name }}</h4>
            <div class="related-price">¥{{ item.price }}</div>
          </div>
        </div>
      </div>
      
      <!-- 底部固定操作栏 -->
      <div class="bottom-action-bar">
        <div class="bar-actions">
          <el-button type="text" class="bar-button" @click="goToCart">
            <el-icon><ShoppingCart /></el-icon>
            <span>购物车</span>
          </el-button>
          <el-button type="text" class="bar-button" @click="toggleFavorite">
            <el-icon :size="20">{{ isFavorite ? 'StarFilled' : 'Star' }}</el-icon>
            <span>收藏</span>
          </el-button>
        </div>
        <div class="bar-buttons">
          <el-button type="default" class="bar-buy-button" @click="buyNow" :disabled="product.stock <= 0">
            立即购买
          </el-button>
          <el-button type="primary" class="bar-cart-button" @click="addToCart" :disabled="product.stock <= 0">
            加入购物车
          </el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { ArrowLeft, Share, Star, StarFilled, Shop, ShoppingCart, Refresh } from '@element-plus/icons-vue'
import { productApi, cartApi } from '@/api/product'
import favoriteApi from '@/api/favorite'
import reviewApi from '@/api/review'
import clothesImg1 from '@/assets/images/clothes/新对话 (6).png'
import clothesImg2 from '@/assets/images/clothes/新对话 (3).png'
import clothesImg3 from '@/assets/images/clothes/新对话 (5).png'
import shoesImg from '@/assets/images/shoes/新对话 (10).png'

export default {
  name: 'ProductDetail',
  components: {
    ArrowLeft,
    Share,
    Star,
    StarFilled,
    Shop,
    ShoppingCart,
    Refresh
  },
  data() {
    return {
      product: {
        id: this.$route.params.id,
        name: '',
        price: 0,
        originalPrice: 0,
        description: '',
        stock: 0,
        sales: 0,
        image: '',
        tag: ''
      },
      specs: ['S', 'M', 'L', 'XL', 'XXL'],
      selectedSpec: 'M',
      quantity: 1,
      reviewList: [],
      reviewStats: null,
      reviewTotal: 0,
      reviewPage: 1,
      reviewPageSize: 10,
      reviewLoading: false,
      relatedProducts: [
        {
          id: 1,
          name: '时尚休闲衬衫',
          price: 129,
          image: clothesImg1
        },
        {
          id: 2,
          name: '舒适牛仔裤',
          price: 199,
          image: clothesImg2
        },
        {
          id: 3,
          name: '潮流运动鞋',
          price: 299,
          image: shoesImg
        },
        {
          id: 4,
          name: '时尚休闲外套',
          price: 259,
          image: clothesImg3
        }
      ],
      loading: true,
      error: '',
      isFavorite: false,
      productImages: [],
      activeImageIndex: 0,
      reviewFilter: 'all'
    }
  },
  created() {
    this.getProductDetail()
    this.checkFavorite()
  },
  watch: {
    reviewFilter() {
      this.reviewPage = 1
      this.loadReviews()
    }
  },
  computed: {
    // 平均评分
    averageRating() {
      return this.reviewStats ? parseFloat(this.reviewStats.avg_rating) || 0 : 0
    }
  },
  methods: {
    goBack() {
      this.$router.back()
    },
    // 获取商品详情
    getProductDetail() {
      this.loading = true
      this.error = ''
      productApi.getProductById(this.$route.params.id).then(response => {
        this.loading = false
        if (response.data.code === 1) {
          this.product = response.data.data
          // 生成商品图片数组：主图为商品图，其余用本地占位图（模拟多图）
          this.productImages = [
            this.product.image,
            clothesImg1,
            clothesImg2,
            shoesImg
          ]
          this.loadReviews()
          this.loadReviewStats()
        } else {
          this.error = response.data.msg || '获取商品详情失败'
        }
      }).catch(error => {
        this.loading = false
        this.error = '网络错误，请稍后重试'
        console.error('获取商品详情失败:', error)
      })
    },
    // 加入购物车
    addToCart() {
      // 构建购物车数据
      const cartData = {
        name: this.product.name,
        image: this.product.image,
        productId: this.product.id,
        skuInfo: this.selectedSpec,
        number: this.quantity,
        amount: this.product.price * this.quantity
      }
      
      // 调用后端API添加到购物车
      cartApi.addToCart(cartData).then(response => {
        if (response.data.code === 1) {
          this.$message.success('已加入购物车')
        } else {
          this.$message.error(response.data.msg || '添加失败')
        }
      }).catch(error => {
        this.$message.error('网络错误，请稍后重试')
        console.error('添加购物车失败:', error)
      })
    },
    // 立即购买：先加入购物车，再跳转结算页（结算页依赖购物车项，后端按购物车项落单）
    buyNow() {
      if (this.product.stock <= 0) {
        this.$message.warning('库存不足')
        return
      }
      const cartData = {
        productId: this.product.id,
        number: this.quantity,
        skuInfo: this.selectedSpec
      }
      cartApi.addToCart(cartData).then(response => {
        if (response.data.code !== 1) {
          this.$message.error(response.data.msg || '添加失败')
          return
        }
        this.$router.push({
          path: '/create-order',
          query: {
            productId: this.product.id,
            quantity: this.quantity,
            spec: this.selectedSpec
          }
        })
      }).catch(() => {
        this.$message.error('网络错误，请稍后重试')
      })
    },
    // 检查收藏状态
    checkFavorite() {
      const productId = this.$route.params.id
      if (!productId) return
      favoriteApi.check(productId).then(response => {
        if (response.data.code === 1) {
          this.isFavorite = response.data.data.favorited
        }
      }).catch(() => {})
    },
    // 切换收藏状态
    toggleFavorite() {
      const productId = this.$route.params.id
      if (this.isFavorite) {
        favoriteApi.remove(productId).then(response => {
          if (response.data.code === 1) {
            this.isFavorite = false
            this.$message.success('已取消收藏')
          }
        }).catch(() => {})
      } else {
        favoriteApi.add(productId).then(response => {
          if (response.data.code === 1) {
            this.isFavorite = true
            this.$message.success('已收藏')
          }
        }).catch(() => {})
      }
    },
    // 分享商品
    shareProduct() {
      this.$message.info('分享功能开发中')
    },
    // 前往购物车
    goToCart() {
      this.$router.push('/cart')
    },
    // 导航到其他商品
    navigateToProduct(productId) {
      this.$router.push(`/product/detail/${productId}`)
    },
    // 加载商品评价
    loadReviews() {
      const productId = this.$route.params.id
      if (!productId) return
      this.reviewLoading = true
      const params = { page: this.reviewPage, size: this.reviewPageSize }
      if (this.reviewFilter !== 'all') {
        params.rating = this.reviewFilter === '3' ? 3 : parseInt(this.reviewFilter)
      }
      reviewApi.list(productId, params).then(response => {
        if (response.data.code === 1) {
          this.reviewList = response.data.data.records || []
          this.reviewTotal = response.data.data.total || 0
        }
      }).catch(() => {}).finally(() => {
        this.reviewLoading = false
      })
    },
    // 加载评分统计
    loadReviewStats() {
      const productId = this.$route.params.id
      if (!productId) return
      reviewApi.stats(productId).then(response => {
        if (response.data.code === 1) {
          this.reviewStats = response.data.data
        }
      }).catch(() => {})
    },
    // 评价分页切换
    handleReviewPageChange(page) {
      this.reviewPage = page
      this.loadReviews()
    },
    // 安全解析 JSON 图片数组
    parseReviewImages(images) {
      if (!images) return []
      try { return JSON.parse(images) } catch { return [] }
    }
  }
}
</script>

<style scoped>
.product-detail {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

/* === 顶部导航 === */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  margin-bottom: var(--space-lg);
  border-bottom: 1px solid var(--border-subtle);
}

.back-button {
  font-size: 18px;
  color: var(--text-secondary);
  padding: 6px 10px;
  transition: var(--transition-fast);
}

.back-button:hover {
  color: var(--accent-purple);
}

.page-title {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 20px;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: 0.06em;
}

.nav-actions {
  display: flex;
  gap: 12px;
}

.action-button {
  font-size: 16px;
  color: var(--text-secondary);
  padding: 6px 10px;
  transition: var(--transition-fast);
}

.action-button:hover {
  color: var(--accent-purple);
}

/* === 加载和错误状态 === */
.loading-container {
  padding: 40px 0;
}

.error-container {
  padding: 60px 0;
  text-align: center;
}

.error-content {
  max-width: 400px;
  margin: 0 auto;
}

.error-message {
  font-family: var(--font-heading);
  font-size: 15px;
  color: var(--text-secondary);
  margin-bottom: 20px;
}

/* === 商品图片 === */
.product-image-section {
  margin-bottom: var(--space-lg);
}

.image-carousel {
  margin-bottom: 16px;
  border: 1px solid var(--border-card);
}

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-thumbs {
  display: flex;
  gap: 4px;
  justify-content: center;
}

.thumb-item {
  width: 76px;
  height: 76px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  transition: var(--transition-base);
  opacity: 0.5;
}

.thumb-item:hover {
  opacity: 0.8;
}

.thumb-item.active {
  border-color: var(--accent-purple);
  opacity: 1;
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* === 商品信息 === */
.product-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px;
  margin-bottom: var(--space-xl);
}

.product-header {
  grid-column: 1 / -1;
  margin-bottom: 16px;
}

.product-name {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 32px;
  color: var(--text-primary);
  margin: 0 0 12px 0;
  line-height: 1.1;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.product-price-section {
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 24px;
}

.price-container {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.price-label {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.current-price {
  font-family: var(--font-display);
  font-size: 42px;
  font-weight: 800;
  color: var(--accent-lime);
  animation: priceFlicker 3s infinite;
}

.original-price {
  font-family: var(--font-heading);
  font-size: 18px;
  color: var(--text-tertiary);
  text-decoration: line-through;
}

.discount-tag {
  background-color: var(--accent-red);
  color: #fff;
  padding: 4px 12px;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
}

.sales-info {
  display: flex;
  gap: 20px;
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.stock.low-stock {
  color: var(--accent-red);
}

.product-description {
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 24px;
}

.section-title {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 16px;
  color: var(--text-primary);
  margin: 0 0 14px 0;
  letter-spacing: 0.04em;
}

.description-text {
  font-family: var(--font-heading);
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
  max-width: 50ch;
}

/* === 商品规格 === */
.specs-section {
  grid-column: 1 / -1;
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 24px;
  margin-top: 2px;
}

.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.spec-option {
  min-width: 56px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
  transition: var(--transition-fast);
  background-color: var(--bg-card);
}

.spec-option:hover {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.spec-option.active {
  background-color: var(--accent-purple);
  color: #fff;
  border-color: var(--accent-purple);
}

/* === 数量选择 === */
.quantity-section {
  grid-column: 1 / -1;
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 24px;
  margin-top: 2px;
}

.quantity-container {
  display: flex;
  align-items: center;
  gap: 16px;
}

.quantity-input {
  width: 130px;
}

.stock-info {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.stock-info.out-of-stock {
  color: var(--accent-red);
}

/* === 操作按钮 === */
.action-buttons {
  grid-column: 1 / -1;
  display: flex;
  gap: 2px;
  margin-top: 2px;
}

.buy-button,
.cart-button {
  flex: 1;
  height: 56px;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 16px;
  letter-spacing: 0.06em;
  transition: var(--transition-base);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-none);
}

.buy-button {
  background-color: var(--bg-card);
  border: 1px solid var(--border-subtle);
  color: var(--text-primary);
}

.buy-button:hover:not(:disabled) {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.cart-button {
  background-color: var(--accent-purple);
  border: none;
  color: #fff;
}

.cart-button:hover:not(:disabled) {
  box-shadow: 0 0 30px var(--accent-purple-dim);
}

.cart-button:active:not(:disabled) {
  animation: hydraulicPress 0.2s ease;
}

/* 商品评价 */
.product-reviews {
  padding: 30px;
  background: #fff;
  margin: 20px 30px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.product-reviews:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.reviews-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.review-stats {
  display: flex;
  align-items: center;
  gap: 20px;
}

.rating-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.average-rating {
  font-size: 24px;
  font-weight: bold;
  color: #ff4d4f;
  min-width: 40px;
}

.rating-stars {
  display: flex;
  align-items: center;
}

.review-count {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.review-filter {
  width: 120px;
  border-radius: 12px;
  border: 2px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.review-item {
  padding: 24px;
  background: #f8f9fa;
  border-radius: 16px;
  transition: all 0.3s ease;
  border-left: 4px solid #667eea;
}

.review-item:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.reviewer-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.reviewer-avatar {
  width: 40px;
  height: 40px;
  font-size: 16px;
  font-weight: bold;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.reviewer-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.review-time {
  font-size: 12px;
  color: #999;
}

.review-content {
  font-size: 14px;
  line-height: 1.6;
  color: #666;
  margin-bottom: 16px;
}

.review-images {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.review-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  cursor: pointer;
}

.review-image:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.no-reviews {
  padding: 60px 0;
  text-align: center;
}

/* 相关推荐 */
.related-products {
  padding: 30px;
  background: #fff;
  margin: 20px 30px 100px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.related-products:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.related-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px;
  margin-top: 24px;
}

.related-item {
  background: #f8f9fa;
  border-radius: 16px;
  overflow: hidden;
  transition: all 0.3s ease;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.related-item:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
}

.related-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.related-item:hover .related-image {
  transform: scale(1.1);
}

.related-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 12px 16px 8px;
  line-height: 1.4;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.related-price {
  font-size: 18px;
  font-weight: bold;
  color: #ff4d4f;
  margin: 0 16px 16px;
}

/* 底部固定操作栏 */
.bottom-action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.1);
  padding: 16px 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  z-index: 99;
}

.bar-actions {
  display: flex;
  gap: 24px;
}

.bar-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #666;
  font-weight: 500;
  transition: all 0.3s ease;
  padding: 8px 12px;
  border-radius: 8px;
}

.bar-button:hover {
  background: #f0f2f5;
  color: #667eea;
  transform: translateY(-2px);
}

.bar-buttons {
  display: flex;
  gap: 12px;
}

.bar-buy-button,
.bar-cart-button {
  padding: 12px 24px;
  border-radius: 24px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  min-width: 120px;
}

.bar-buy-button {
  background: #fff;
  border: 2px solid #e0e0e0;
  color: #333;
}

.bar-buy-button:hover:not(:disabled) {
  border-color: #667eea;
  color: #667eea;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.bar-cart-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
}

.bar-cart-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .top-nav {
    padding: 15px 20px;
  }
  
  .page-title {
    font-size: 18px;
  }
  
  .product-image-section,
  .product-info,
  .product-reviews,
  .related-products {
    margin: 15px 20px;
    padding: 20px;
  }
  
  .image-carousel {
    height: 300px !important;
  }
  
  .thumb-item {
    width: 60px;
    height: 60px;
  }
  
  .product-name {
    font-size: 24px;
  }
  
  .current-price {
    font-size: 28px;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .buy-button,
  .cart-button {
    width: 100%;
  }
  
  .related-list {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 16px;
  }
  
  .related-image {
    height: 150px;
  }
  
  .bottom-action-bar {
    padding: 12px 20px;
  }
  
  .bar-actions {
    gap: 16px;
  }
  
  .bar-buttons {
    gap: 8px;
  }
  
  .bar-buy-button,
  .bar-cart-button {
    padding: 10px 16px;
    min-width: 100px;
    font-size: 12px;
  }
}

@media (max-width: 480px) {
  .top-nav {
    padding: 12px 15px;
  }
  
  .back-button,
  .action-button {
    font-size: 16px;
    padding: 6px 10px;
  }
  
  .page-title {
    font-size: 16px;
  }
  
  .product-image-section,
  .product-info,
  .product-reviews,
  .related-products {
    margin: 10px 15px;
    padding: 16px;
  }
  
  .image-carousel {
    height: 250px !important;
  }
  
  .image-thumbs {
    gap: 8px;
  }
  
  .thumb-item {
    width: 50px;
    height: 50px;
  }
  
  .product-name {
    font-size: 20px;
  }
  
  .current-price {
    font-size: 24px;
  }
  
  .spec-options {
    gap: 10px;
  }
  
  .spec-option {
    padding: 8px 16px;
    font-size: 14px;
  }
  
  .related-list {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
  
  .related-image {
    height: 120px;
  }
  
  .bottom-action-bar {
    padding: 10px 15px;
  }
  
  .bar-actions {
    gap: 12px;
  }
  
  .bar-button {
    font-size: 10px;
  }
  
  .bar-buy-button,
  .bar-cart-button {
    padding: 8px 12px;
    min-width: 80px;
    font-size: 11px;
  }
}
</style>
