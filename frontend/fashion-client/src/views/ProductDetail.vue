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
              <span class="review-count">{{ reviews.length }} 条评价</span>
            </div>
            <el-select v-model="reviewFilter" class="review-filter">
              <el-option label="全部评价" value="all" />
              <el-option label="5星" value="5" />
              <el-option label="4星" value="4" />
              <el-option label="3星及以下" value="3" />
            </el-select>
          </div>
        </div>
        <div class="review-list">
          <div class="review-item" v-for="review in filteredReviews" :key="review.id">
            <div class="review-header">
              <div class="reviewer-info">
                <el-avatar class="reviewer-avatar">{{ review.reviewer.charAt(0) }}</el-avatar>
                <span class="reviewer-name">{{ review.reviewer }}</span>
              </div>
              <span class="review-time">{{ review.time }}</span>
              <el-rate v-model="review.rating" disabled :max="5" :colors="['#ff4d4f']" size="small" />
            </div>
            <div class="review-content">{{ review.content }}</div>
            <div class="review-images" v-if="review.images && review.images.length > 0">
              <img v-for="(img, index) in review.images" :key="index" :src="img" :alt="`Review image ${index + 1}`" class="review-image" />
            </div>
          </div>
          <div v-if="filteredReviews.length === 0" class="no-reviews">
            <el-empty description="暂无评价" :image-size="80" />
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
      reviews: [
        {
          id: 1,
          reviewer: '用户1',
          time: '2026-04-01 10:00:00',
          rating: 5,
          content: '商品质量很好，颜色和描述一致，穿着舒适，非常满意！',
          images: []
        },
        {
          id: 2,
          reviewer: '用户2',
          time: '2026-04-02 14:30:00',
          rating: 4,
          content: '商品整体不错，就是尺码稍微偏大，不过不影响穿着。',
          images: []
        },
        {
          id: 3,
          reviewer: '用户3',
          time: '2026-04-03 09:15:00',
          rating: 5,
          content: '物流速度很快，包装完好，商品质量超出预期，值得购买！',
          images: []
        }
      ],
      relatedProducts: [
        {
          id: 1,
          name: '时尚休闲衬衫',
          price: 129,
          image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20fashion%20casual%20shirt&image_size=landscape_4_3'
        },
        {
          id: 2,
          name: '舒适牛仔裤',
          price: 199,
          image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=comfortable%20jeans%20fashion&image_size=landscape_4_3'
        },
        {
          id: 3,
          name: '潮流运动鞋',
          price: 299,
          image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=trendy%20sports%20shoes&image_size=landscape_4_3'
        },
        {
          id: 4,
          name: '时尚休闲外套',
          price: 259,
          image: 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20casual%20jacket&image_size=landscape_4_3'
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
    // 获取商品详情
    this.getProductDetail()
  },
  computed: {
    // 平均评分
    averageRating() {
      if (this.reviews.length === 0) return 0
      const totalRating = this.reviews.reduce((sum, review) => sum + review.rating, 0)
      return Math.round(totalRating / this.reviews.length * 10) / 10
    },
    // 过滤后的评价
    filteredReviews() {
      if (this.reviewFilter === 'all') {
        return this.reviews
      } else if (this.reviewFilter === '3') {
        return this.reviews.filter(review => review.rating <= 3)
      } else {
        return this.reviews.filter(review => review.rating === parseInt(this.reviewFilter))
      }
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
          // 生成商品图片数组（模拟多张图片）
          this.productImages = [
            this.product.image,
            `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(this.product.name + ' detail view 1')}&image_size=landscape_4_3`,
            `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(this.product.name + ' detail view 2')}&image_size=landscape_4_3`,
            `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(this.product.name + ' detail view 3')}&image_size=landscape_4_3`
          ]
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
    // 立即购买
    buyNow() {
      if (this.product.stock <= 0) {
        this.$message.warning('库存不足')
        return
      }
      // 这里可以跳转到订单确认页面
      this.$router.push({
        path: '/order/confirm',
        query: {
          productId: this.product.id,
          quantity: this.quantity,
          spec: this.selectedSpec
        }
      })
    },
    // 切换收藏状态
    toggleFavorite() {
      this.isFavorite = !this.isFavorite
      this.$message.success(this.isFavorite ? '已收藏' : '已取消收藏')
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

/* 主容器 */
.product-detail {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0;
  min-height: 100vh;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  animation: fadeIn 0.8s ease-out;
}

/* 顶部导航 */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 30px;
  background: #fff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-button {
  font-size: 20px;
  color: #333;
  padding: 8px 12px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.back-button:hover {
  background: #f0f2f5;
  color: #667eea;
  transform: translateX(-5px);
}

.page-title {
  font-size: 20px;
  font-weight: bold;
  margin: 0;
  color: #333;
}

.nav-actions {
  display: flex;
  gap: 16px;
}

.action-button {
  font-size: 18px;
  color: #666;
  padding: 8px 12px;
  border-radius: 50%;
  transition: all 0.3s ease;
}

.action-button:hover {
  background: #f0f2f5;
  color: #667eea;
  transform: scale(1.1);
}

/* 加载和错误状态 */
.loading-container {
  padding: 60px 30px;
}

.loading-skeleton {
  background: #fff;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
}

.error-container {
  padding: 60px 30px;
  text-align: center;
}

.error-content {
  max-width: 400px;
  margin: 0 auto;
}

.error-message {
  font-size: 16px;
  color: #666;
  margin-bottom: 20px;
}

.reload-button {
  padding: 10px 30px;
  border-radius: 25px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

.reload-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

/* 商品图片部分 */
.product-image-section {
  padding: 30px;
  background: #fff;
  margin: 20px 30px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.product-image-section:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.image-carousel {
  margin-bottom: 20px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.carousel-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.5s ease;
}

.carousel-image:hover {
  transform: scale(1.05);
}

.image-thumbs {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 20px;
}

.thumb-item {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 3px solid transparent;
}

.thumb-item:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.thumb-item.active {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.2);
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 商品信息部分 */
.product-info {
  padding: 30px;
  background: #fff;
  margin: 20px 30px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.product-info:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.product-header {
  margin-bottom: 30px;
}

.product-name {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin: 0 0 15px 0;
  line-height: 1.3;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.product-tag {
  border-radius: 16px;
  padding: 4px 16px;
  font-size: 14px;
  font-weight: 500;
  background: #f8f9fa;
  color: #667eea;
  border: 1px solid #e3e6f0;
  transition: all 0.3s ease;
}

.product-tag:hover {
  background: #667eea;
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.product-price-section {
  margin-bottom: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid #f0f0f0;
}

.price-container {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 15px;
}

.price-label {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.current-price {
  font-size: 36px;
  font-weight: bold;
  color: #ff4d4f;
}

.original-price {
  font-size: 20px;
  color: #999;
  text-decoration: line-through;
}

.discount-tag {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a45 100%);
  color: #fff;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
}

.sales-info {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: #666;
}

.sales {
  color: #e6a23c;
  font-weight: 500;
}

.stock {
  color: #67c23a;
  font-weight: 500;
}

.stock.low-stock {
  color: #ff4d4f;
  animation: pulse 2s infinite;
}

.product-description {
  margin-bottom: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin: 0 0 15px 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.description-text {
  font-size: 16px;
  line-height: 1.6;
  color: #666;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 12px;
  border-left: 4px solid #667eea;
}

/* 商品规格 */
.specs-section {
  margin-bottom: 30px;
  padding-bottom: 30px;
  border-bottom: 1px solid #f0f0f0;
}

.spec-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.spec-option {
  padding: 12px 24px;
  border-radius: 25px;
  border: 2px solid #f0f0f0;
  background: #fff;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.spec-option:hover {
  border-color: #667eea;
  background: #f8f9fa;
  transform: translateY(-2px);
}

.spec-option.active {
  border-color: #667eea;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

/* 数量选择 */
.quantity-section {
  margin-bottom: 30px;
}

.quantity-container {
  display: flex;
  align-items: center;
  gap: 20px;
}

.quantity-input {
  width: 120px;
  border-radius: 12px;
  border: 2px solid #f0f0f0;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.quantity-input .el-input-number__decrease,
.quantity-input .el-input-number__increase {
  background: #f8f9fa;
  border: none;
  width: 40px;
  height: 40px;
  font-size: 18px;
  transition: all 0.3s ease;
}

.quantity-input .el-input-number__decrease:hover,
.quantity-input .el-input-number__increase:hover {
  background: #e3e6f0;
  color: #667eea;
}

.quantity-input .el-input__inner {
  border: none;
  text-align: center;
  font-size: 16px;
  font-weight: 500;
  height: 40px;
  line-height: 40px;
}

.stock-info {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.stock-info.out-of-stock {
  color: #ff4d4f;
  font-weight: 600;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 20px;
  margin-top: 30px;
}

.buy-button,
.cart-button {
  flex: 1;
  height: 56px;
  border-radius: 28px;
  font-size: 18px;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.buy-button {
  background: #fff;
  border: 2px solid #e0e0e0;
  color: #333;
}

.buy-button:hover:not(:disabled) {
  border-color: #667eea;
  color: #667eea;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.3);
}

.cart-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
}

.cart-button:hover:not(:disabled) {
  background: linear-gradient(135deg, #764ba2 0%, #667eea 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
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
