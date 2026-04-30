<template>
  <div class="home">
    <!-- 轮播图 -->
    <el-carousel :interval="5000" height="600px" indicator-position="outside" trigger="click" class="main-carousel">
      <el-carousel-item v-for="(item, index) in carouselItems" :key="index" class="carousel-item">
        <div class="carousel-bg" :style="{ backgroundImage: `url(${item.image})` }"></div>
        <div class="carousel-content">
          <h3 class="carousel-title">{{ item.title }}</h3>
          <p class="carousel-subtitle">{{ item.subtitle }}</p>
          <el-button type="primary" size="large" class="carousel-btn" @click="item.action">立即购买</el-button>
        </div>
      </el-carousel-item>
    </el-carousel>

    <!-- 商品分类 -->
    <div class="category-section">
      <h2 class="section-title">
        <span>商品分类</span>
        <el-divider direction="horizontal"></el-divider>
      </h2>
      <div class="category-list">
        <div class="category-item" v-for="category in categories" :key="category.id" @click="goToCategory(category.id)">
          <div class="category-icon">
            <img :src="category.image" :alt="category.name" />
            <div class="category-overlay">
              <span>{{ category.name }}</span>
            </div>
          </div>
          <span class="category-name">{{ category.name }}</span>
        </div>
      </div>
    </div>

    <!-- 热门商品 -->
    <div class="hot-products">
      <h2 class="section-title">
        <span>热门商品</span>
        <el-divider direction="horizontal"></el-divider>
        <el-button type="text" class="view-all-btn" @click="viewAllProducts">查看全部 <el-icon><ArrowRight /></el-icon></el-button>
      </h2>
      <div class="product-list">
        <el-card v-for="product in hotProducts" :key="product.id" shadow="hover" class="product-card">
          <div class="product-image-container">
            <img :src="product.image" :alt="product.name" class="product-image" @click="viewProductDetail(product.id)" />
            <div v-if="product.isNew" class="product-badge new-badge">新品</div>
            <div v-if="product.isHot" class="product-badge hot-badge">热销</div>
            <div class="product-overlay">
              <el-button type="primary" size="small" class="add-cart-btn" @click="addProductToCart(product.id)">加入购物车</el-button>
              <el-button type="text" size="small" class="view-detail-btn" @click="viewProductDetail(product.id)">查看详情</el-button>
            </div>
          </div>
          <div class="product-info">
            <h4 class="product-name" @click="viewProductDetail(product.id)">{{ product.name }}</h4>
            <div class="product-rating">
              <el-rate v-model="product.rating" disabled :max="5" :colors="['#ff4d4f']" size="small" />
              <span class="rating-count">({{ product.ratingCount }})</span>
            </div>
            <div class="price-section">
              <span class="price">¥{{ product.price }}</span>
              <span class="sales">销量: {{ product.sales }}</span>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 秒杀活动 -->
    <div class="seckill-section">
      <div class="section-header">
        <h2 class="section-title">
          <span class="seckill-title">秒杀活动</span>
          <el-divider direction="horizontal"></el-divider>
        </h2>
        <div class="countdown">
          <span class="countdown-label">距离结束:</span>
          <div class="countdown-timer">
            <el-countdown
              :value="endTime"
              format="HH:mm:ss"
              @finish="handleCountdownFinish"
              class="countdown-clock"
            />
          </div>
        </div>
      </div>
      <div class="seckill-list">
        <el-card v-for="coupon in seckillCoupons" :key="coupon.id" shadow="hover" class="seckill-card">
          <div class="seckill-header">
            <span class="coupon-name">{{ coupon.name }}</span>
            <span class="stock" :class="{ 'stock-low': coupon.stock < 10 }">
              库存: {{ coupon.stock }}
              <el-progress v-if="coupon.stock > 0" :percentage="100 - (coupon.stock / coupon.totalStock * 100)" :stroke-width="6" :color="getProgressColor(coupon.stock, coupon.totalStock)" />
            </span>
          </div>
          <div class="seckill-info">
            <div class="price-section">
              <span class="original-price">¥{{ coupon.originalPrice }}</span>
              <span class="seckill-price">¥{{ coupon.seckillPrice }}</span>
              <span class="discount">{{ Math.round((1 - coupon.seckillPrice / coupon.originalPrice) * 100) }}% OFF</span>
            </div>
            <el-button type="danger" size="small" class="seckill-btn" @click="seckillCoupon(coupon.id)" :disabled="coupon.stock <= 0">
              {{ coupon.stock > 0 ? '立即抢购' : '已售罄' }}
            </el-button>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 品牌故事 -->
    <div class="brand-section">
      <div class="brand-content">
        <div class="brand-text">
          <h2 class="brand-title">关于我们的品牌</h2>
          <p class="brand-description">我们是一家专注于时尚服装的品牌，致力于为您提供高品质、时尚的服装产品。我们的设计理念是将时尚与舒适相结合，为您打造独特的个人风格。</p>
          <p class="brand-description">我们采用优质的面料和精湛的工艺，确保每一件产品都能达到最高的品质标准。无论您是追求时尚潮流还是注重舒适体验，我们都能满足您的需求。</p>
          <el-button type="primary" class="brand-btn" @click="viewAboutUs">了解更多</el-button>
        </div>
        <div class="brand-image">
          <img src="https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=fashion%20brand%20story%20image%20with%20modern%20style&image_size=landscape_4_3" alt="品牌故事" />
        </div>
      </div>
    </div>

    <!-- 尺码选择弹窗 -->
    <el-dialog
      v-model="cartDialogVisible"
      title="选择尺码"
      width="450px"
      :close-on-click-modal="false"
      class="size-dialog"
    >
      <div v-if="selectedProduct" class="dialog-content">
        <div class="product-preview">
          <img :src="selectedProduct.image" :alt="selectedProduct.name" class="preview-image" />
          <div class="preview-info">
            <h4 class="preview-name">{{ selectedProduct.name }}</h4>
            <div class="preview-price">¥{{ selectedProduct.price }}</div>
          </div>
        </div>

        <div class="size-section">
          <h5 class="section-label">选择尺码：</h5>
          <div class="size-options">
            <div
              v-for="size in sizes"
              :key="size"
              :class="['size-option', { 'active': selectedSize === size }]"
              @click="selectedSize = size"
            >
              {{ size }}
            </div>
          </div>
        </div>

        <div class="quantity-section">
          <h5 class="section-label">购买数量：</h5>
          <el-input-number v-model="quantity" :min="1" :max="99" size="small" />
        </div>
      </div>

      <template #footer>
        <el-button @click="cartDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddToCart">确定加入购物车</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ArrowRight } from '@element-plus/icons-vue'
// 导入API
import { productApi, cartApi } from '@/api/product'
// 导入图片
import banner1 from '@/assets/images/promotions/新对话.png'
import banner2 from '@/assets/images/promotions/新对话 (1).png'
import banner3 from '@/assets/images/promotions/新对话 (2).png'
import category1 from '@/assets/images/clothes/新对话 (3).png'
import category2 from '@/assets/images/clothes/新对话 (5).png'
import category3 from '@/assets/images/shoes/新对话 (10).png'
import category4 from '@/assets/images/accessories/新对话 (13).png'

export default {
  name: 'Home',
  components: {
    ArrowRight
  },
  data() {
    return {
      carouselItems: [
        {
          image: banner1,
          title: '夏季新品上市',
          subtitle: '全场满300减50',
          action: () => this.viewAllProducts()
        },
        {
          image: banner2,
          title: '限时秒杀',
          subtitle: '低至5折',
          action: () => this.$router.push('/seckill')
        },
        {
          image: banner3,
          title: '会员专享',
          subtitle: '会员享额外9折优惠',
          action: () => this.viewAllProducts()
        }
      ],
      endTime: new Date().getTime() + 60 * 60 * 1000, // 1小时后结束
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
      // 尺码选择弹窗相关数据
      cartDialogVisible: false,
      selectedProduct: null,
      selectedSize: 'M',
      sizes: ['S', 'M', 'L', 'XL', 'XXL'],
      quantity: 1
    }
  },
  created() {
    // 获取热门商品
    this.getHotProducts()
  },
  methods: {
    // 获取热门商品
    getHotProducts() {
      const params = {
        page: 1,
        pageSize: 4,
        sortBy: 'sales' // 按销量排序获取热门商品
      }
      
      productApi.getProductList(params).then(response => {
        if (response.data.code === 1) {
          this.hotProducts = response.data.data.records
        }
      }).catch(error => {
        console.error('获取热门商品失败:', error)
      })
    },
    goToCategory(categoryId) {
      // 跳转到商品列表页面，只传递标签
      let tag = ''
      switch(categoryId) {
        case 1:
          tag = '衣服'
          break
        case 2:
          tag = '裤子'
          break
        case 3:
          tag = '鞋子'
          break
        case 4:
          tag = '配饰'
          break
      }
      this.$router.push({ path: '/product/list', query: { tag: tag } })
    },
    viewAllProducts() {
      this.$router.push('/product/list')
    },
    viewProductDetail(productId) {
      this.$router.push(`/product/detail/${productId}`)
    },
    addProductToCart(productId) {
      // 检查是否登录
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }

      // 查找商品信息
      const product = this.hotProducts.find(p => p.id === productId)
      if (product) {
        this.selectedProduct = product
        this.selectedSize = 'M' // 重置为默认尺码
        this.quantity = 1 // 重置数量
        this.cartDialogVisible = true
      } else {
        this.$message.error('商品信息不存在')
      }
    },
    // 确认添加到购物车
    confirmAddToCart() {
      if (!this.selectedProduct) return

      const cartData = {
        name: this.selectedProduct.name,
        image: this.selectedProduct.image,
        productId: this.selectedProduct.id,
        skuInfo: this.selectedSize,
        number: this.quantity,
        amount: this.selectedProduct.price * this.quantity
      }

      cartApi.addToCart(cartData).then(response => {
        if (response.data.code === 1) {
          this.$message.success(`已添加 ${this.selectedProduct.name} (${this.selectedSize}码) × ${this.quantity}`)
          this.cartDialogVisible = false
        } else {
          this.$message.error(response.data.msg || '添加失败')
        }
      }).catch(error => {
        this.$message.error('网络错误，请稍后重试')
        console.error('添加到购物车失败:', error)
      })
    },
    seckillCoupon(couponId) {
      // 检查是否登录
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      
      // 模拟秒杀
      this.$message.success('抢购成功！')
    },
    handleCountdownFinish() {
      console.log('倒计时结束')
      // 刷新秒杀活动
    },
    viewAboutUs() {
      console.log('查看关于我们')
    },
    getProgressColor(stock, totalStock) {
      const percentage = (stock / totalStock) * 100
      if (percentage > 50) return '#67c23a'
      if (percentage > 20) return '#e6a23c'
      return '#f56c6c'
    }
  }
}
</script>

<style scoped>
.home {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  min-height: 100vh;
}

/* 轮播图样式 */
.main-carousel {
  margin-bottom: 60px;
  border-radius: 15px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
}

.carousel-item {
  position: relative;
  overflow: hidden;
}

.carousel-bg {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  transition: transform 0.5s ease;
}

.carousel-item:hover .carousel-bg {
  transform: scale(1.05);
}

.carousel-content {
  position: absolute;
  top: 50%;
  left: 10%;
  transform: translateY(-50%);
  color: #fff;
  text-shadow: 0 2px 15px rgba(0, 0, 0, 0.7);
  z-index: 10;
  animation: slideInLeft 1s ease-out;
  max-width: 500px;
}

@keyframes slideInLeft {
  from {
    opacity: 0;
    transform: translateY(-50%) translateX(-50px);
  }
  to {
    opacity: 1;
    transform: translateY(-50%) translateX(0);
  }
}

.carousel-title {
  font-size: 52px;
  font-weight: bold;
  margin-bottom: 20px;
  line-height: 1.2;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.carousel-subtitle {
  font-size: 26px;
  margin-bottom: 35px;
  opacity: 0.95;
  font-weight: 300;
}

.carousel-btn {
  padding: 15px 35px;
  font-size: 18px;
  border-radius: 50px;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(255, 77, 79, 0.3);
}

.carousel-btn:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(255, 77, 79, 0.5);
  background: linear-gradient(135deg, #ff7a7a 0%, #ff4d4f 100%);
}

/* 通用样式 */
.section-title {
  display: flex;
  align-items: center;
  margin: 80px 0 40px;
  font-size: 28px;
  font-weight: bold;
  color: #333;
  position: relative;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.section-title span {
  flex: 0 0 auto;
  margin-right: 25px;
  position: relative;
}

.section-title span::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 0;
  width: 70px;
  height: 4px;
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7a7a 100%);
  border-radius: 2px;
}

.section-title .el-divider {
  flex: 1;
  border-top: 1px solid #e0e0e0;
}

.view-all-btn {
  color: #ff4d4f;
  font-size: 16px;
  transition: all 0.3s ease;
  font-weight: 500;
}

.view-all-btn:hover {
  transform: translateX(8px);
  color: #ff7a7a;
}

/* 分类样式 */
.category-section {
  margin-top: 60px;
}

.category-list {
  display: flex;
  justify-content: space-around;
  flex-wrap: wrap;
  margin-top: 50px;
}

.category-item {
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  margin: 0 25px 35px;
  width: 160px;
}

.category-item:hover {
  transform: translateY(-12px);
}

.category-icon {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  overflow: hidden;
  margin: 0 auto 20px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  position: relative;
  transition: all 0.3s ease;
  background: #fff;
  padding: 10px;
}

.category-item:hover .category-icon {
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);
}

.category-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
  border-radius: 50%;
}

.category-item:hover .category-icon img {
  transform: scale(1.15);
}

.category-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
  border-radius: 50%;
}

.category-item:hover .category-overlay {
  opacity: 1;
}

.category-overlay span {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  text-shadow: 0 2px 5px rgba(0, 0, 0, 0.3);
}

.category-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  transition: color 0.3s ease;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.category-item:hover .category-name {
  color: #ff4d4f;
}

/* 商品样式 */
.hot-products {
  margin-top: 100px;
}

.product-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 35px;
  margin-top: 50px;
}

.product-card {
  transition: all 0.4s ease;
  overflow: hidden;
  border-radius: 15px;
  position: relative;
  background: #fff;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
}

.product-card:hover {
  transform: translateY(-20px);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.15);
}

.product-image-container {
  position: relative;
  overflow: hidden;
  height: 300px;
  border-radius: 15px 15px 0 0;
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s ease;
  cursor: pointer;
}

.product-card:hover .product-image {
  transform: scale(1.15);
}

.product-badge {
  position: absolute;
  top: 20px;
  padding: 8px 16px;
  border-radius: 25px;
  font-size: 14px;
  font-weight: bold;
  color: #fff;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.2);
}

.new-badge {
  left: 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.hot-badge {
  right: 20px;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.product-overlay {
  position: absolute;
  bottom: -120px;
  left: 0;
  right: 0;
  background: rgba(0, 0, 0, 0.85);
  padding: 25px;
  transition: bottom 0.4s ease;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.product-card:hover .product-overlay {
  bottom: 0;
}

.add-cart-btn {
  border-radius: 25px;
  padding: 8px 25px;
  font-weight: 500;
  background: #ff4d4f;
  border: none;
  transition: all 0.3s ease;
}

.add-cart-btn:hover {
  background: #ff7a7a;
  transform: translateY(-2px);
}

.view-detail-btn {
  color: #fff;
  border: 1px solid #fff;
  border-radius: 25px;
  padding: 8px 25px;
  transition: all 0.3s ease;
  font-weight: 500;
}

.view-detail-btn:hover {
  background: #fff;
  color: #333;
  transform: translateY(-2px);
}

.product-info {
  padding: 25px;
}

.product-name {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 15px;
  cursor: pointer;
  height: 56px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  transition: color 0.3s ease;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.product-name:hover {
  color: #ff4d4f;
}

.product-rating {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.rating-count {
  font-size: 14px;
  color: #999;
  margin-left: 10px;
}

.price-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price {
  font-size: 28px;
  font-weight: bold;
  color: #ff4d4f;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.sales {
  font-size: 16px;
  color: #999;
}

/* 秒杀样式 */
.seckill-section {
  margin-top: 100px;
  background: linear-gradient(135deg, #fff0f0 0%, #ffe6e6 100%);
  padding: 50px;
  border-radius: 20px;
  box-shadow: 0 6px 25px rgba(255, 77, 79, 0.15);
}

.seckill-title {
  color: #ff4d4f;
  font-size: 32px;
  font-weight: bold;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 50px;
  flex-wrap: wrap;
  gap: 25px;
}

.countdown {
  display: flex;
  align-items: center;
  gap: 20px;
  background: #fff;
  padding: 15px 25px;
  border-radius: 50px;
  box-shadow: 0 3px 15px rgba(0, 0, 0, 0.15);
}

.countdown-label {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.countdown-timer {
  display: flex;
  align-items: center;
}

.countdown-clock {
  font-size: 22px;
  font-weight: bold;
  color: #ff4d4f;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.seckill-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 30px;
}

.seckill-card {
  background: #fff;
  border-radius: 15px;
  overflow: hidden;
  transition: all 0.4s ease;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
}

.seckill-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 35px rgba(0, 0, 0, 0.18);
}

.seckill-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 25px;
  border-bottom: 1px solid #f0f0f0;
}

.coupon-name {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  flex: 1;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.stock {
  font-size: 16px;
  color: #999;
  text-align: right;
  min-width: 140px;
}

.stock-low {
  color: #ff4d4f;
  font-weight: bold;
}

.stock .el-progress {
  margin-top: 10px;
  height: 8px;
  border-radius: 4px;
}

.seckill-info {
  padding: 30px;
}

.seckill-info .price-section {
  display: flex;
  align-items: baseline;
  gap: 20px;
  margin-bottom: 25px;
  flex-wrap: wrap;
}

.original-price {
  text-decoration: line-through;
  color: #999;
  font-size: 18px;
}

.seckill-price {
  font-size: 32px;
  font-weight: bold;
  color: #ff4d4f;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.discount {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  padding: 6px 16px;
  border-radius: 25px;
  animation: pulse 2s infinite;
  box-shadow: 0 3px 10px rgba(255, 77, 79, 0.3);
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

.seckill-btn {
  width: 100%;
  padding: 12px;
  font-size: 18px;
  font-weight: bold;
  border-radius: 10px;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  border: none;
  box-shadow: 0 3px 10px rgba(255, 77, 79, 0.3);
}

.seckill-btn:hover:not(:disabled) {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(255, 77, 79, 0.5);
  background: linear-gradient(135deg, #ff7a7a 0%, #ff4d4f 100%);
}

/* 品牌故事样式 */
.brand-section {
  margin-top: 120px;
  margin-bottom: 100px;
  background: #f9f9f9;
  padding: 100px;
  border-radius: 20px;
  box-shadow: 0 6px 25px rgba(0, 0, 0, 0.12);
}

.brand-content {
  display: flex;
  gap: 100px;
  align-items: center;
  animation: fadeIn 1s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(40px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.brand-text {
  flex: 1;
}

.brand-title {
  font-size: 36px;
  font-weight: bold;
  margin-bottom: 35px;
  color: #333;
  position: relative;
  padding-bottom: 25px;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.brand-title::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100px;
  height: 4px;
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7a7a 100%);
  border-radius: 2px;
}

.brand-description {
  font-size: 20px;
  line-height: 2;
  margin-bottom: 35px;
  color: #666;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.brand-btn {
  padding: 15px 35px;
  font-size: 18px;
  border-radius: 50px;
  transition: all 0.3s ease;
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(255, 77, 79, 0.3);
  font-weight: 500;
}

.brand-btn:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(255, 77, 79, 0.5);
  background: linear-gradient(135deg, #ff7a7a 0%, #ff4d4f 100%);
}

.brand-image {
  flex: 0 0 500px;
  border-radius: 15px;
  overflow: hidden;
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.25);
  transition: all 0.4s ease;
}

.brand-image:hover {
  transform: scale(1.03);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.3);
}

.brand-image img {
  width: 100%;
  height: 400px;
  object-fit: cover;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .carousel-content {
    left: 5%;
    max-width: 90%;
  }
  
  .carousel-title {
    font-size: 36px;
  }
  
  .carousel-subtitle {
    font-size: 20px;
  }
  
  .category-list {
    justify-content: center;
  }
  
  .category-item {
    margin: 0 20px 25px;
  }
  
  .product-list {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 25px;
  }
  
  .seckill-list {
    grid-template-columns: 1fr;
  }
  
  .seckill-section {
    padding: 30px;
  }
  
  .brand-section {
    padding: 60px 30px;
  }
  
  .brand-content {
    flex-direction: column;
    text-align: center;
    gap: 50px;
  }
  
  .brand-image {
    flex: 0 0 100%;
    max-width: 400px;
  }
  
  .brand-title::after {
    left: 50%;
    transform: translateX(-50%);
  }
  
  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .countdown {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .carousel-title {
    font-size: 28px;
  }
  
  .carousel-subtitle {
    font-size: 16px;
  }
  
  .carousel-btn {
    padding: 12px 25px;
    font-size: 16px;
  }
  
  .product-list {
    grid-template-columns: 1fr;
  }
  
  .brand-image {
    max-width: 320px;
  }
  
  .brand-image img {
    height: 280px;
  }
  
  .brand-title {
    font-size: 28px;
  }
  
  .brand-description {
    font-size: 18px;
  }
  
  .section-title {
    font-size: 24px;
  }
  
  .seckill-title {
    font-size: 28px;
  }
}

/* 尺码选择弹窗样式 */
.dialog-content {
  padding: 10px 0;
}

.product-preview {
  display: flex;
  gap: 20px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 10px;
  margin-bottom: 25px;
}

.preview-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}

.preview-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
}

.preview-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin: 0;
  line-height: 1.4;
}

.preview-price {
  font-size: 22px;
  font-weight: bold;
  color: #ff4d4f;
}

.size-section,
.quantity-section {
  margin-bottom: 20px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.size-options {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.size-option {
  min-width: 60px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 15px;
  font-weight: 500;
  color: #666;
  transition: all 0.3s ease;
  background: #fff;
}

.size-option:hover {
  border-color: #667eea;
  color: #667eea;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

.size-option.active {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.quantity-section :deep(.el-input-number) {
  width: 150px;
}
</style>
