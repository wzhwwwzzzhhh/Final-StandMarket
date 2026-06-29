<template>
  <div class="product-list">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">
        <span>商品列表</span>
        <el-divider direction="horizontal"></el-divider>
      </h2>
    </div>

    <!-- 筛选条件 -->
    <div class="filter-section">
      <div class="filter-card">
        <el-input v-model="keyword" placeholder="输入关键词搜索" class="search-input">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
          <template #append>
            <el-button type="primary" class="search-button" @click="searchProducts">搜索</el-button>
          </template>
        </el-input>
        <div class="filter-controls">
          <el-select v-model="categoryId" placeholder="选择分类" class="filter-select">
            <el-option label="全部" value="0"></el-option>
            <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id"></el-option>
          </el-select>
          <el-select v-model="tag" placeholder="选择标签" class="filter-select">
            <el-option label="全部" value=""></el-option>
            <el-option label="衣服" value="衣服"></el-option>
            <el-option label="裤子" value="裤子"></el-option>
            <el-option label="鞋子" value="鞋子"></el-option>
            <el-option label="配饰" value="配饰"></el-option>
          </el-select>
          <el-select v-model="sortBy" placeholder="排序方式" class="filter-select">
            <el-option label="默认" value="default"></el-option>
            <el-option label="价格从低到高" value="price_asc"></el-option>
            <el-option label="价格从高到低" value="price_desc"></el-option>
            <el-option label="销量优先" value="sales"></el-option>
          </el-select>
        </div>
      </div>
    </div>

    <!-- 商品列表 -->
    <div class="product-grid">
      <el-card v-for="product in products" :key="product.id" shadow="hover" class="product-card">
        <div class="product-image-container">
          <img :src="product.image" :alt="product.name" class="product-image" @click="goToDetail(product.id)" />
          <div v-if="product.isNew" class="product-badge new-badge">新品</div>
          <div v-if="product.isHot" class="product-badge hot-badge">热销</div>
          <div class="product-overlay">
            <el-button type="primary" size="small" class="add-cart-btn" @click.stop="addToCart(product)">
              <el-icon><ShoppingCart /></el-icon>
              加入购物车
            </el-button>
            <el-button type="text" size="small" class="view-detail-btn" @click.stop="goToDetail(product.id)">
              查看详情
            </el-button>
          </div>
        </div>
        <div class="product-info">
          <h4 class="product-name" @click="goToDetail(product.id)">{{ product.name }}</h4>
          <div class="product-rating">
            <el-rate v-model="product.rating" disabled :max="5" :colors="['#ff4d4f']" size="small" />
            <span class="rating-count">({{ product.ratingCount || 0 }})</span>
          </div>
          <div class="price-section">
            <span class="price">¥{{ product.price }}</span>
            <span class="sales">销量: {{ product.sales }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[10, 20, 30, 40]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        class="pagination-component"
      ></el-pagination>
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
import { Search, ShoppingCart } from '@element-plus/icons-vue'
// 导入API
import { productApi, cartApi } from '@/api/product'


export default {
  components: {
    Search,
    ShoppingCart
  },
  name: 'ProductList',
  data() {
    return {
      keyword: '',
      categoryId: '0',
      tag: '',
      sortBy: 'default',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      categories: [],
      products: [],
      // 尺码选择弹窗相关数据
      cartDialogVisible: false,
      selectedProduct: null,
      selectedSize: 'M',
      sizes: ['S', 'M', 'L', 'XL', 'XXL'],
      quantity: 1
    }
  },
  created() {
    // 获取分类列表
    this.getCategoryList()
    // 从路由参数中获取分类ID
    if (this.$route.query.categoryId) {
      this.categoryId = this.$route.query.categoryId
    }
    // 从路由参数中获取标签
    if (this.$route.query.tag) {
      this.tag = this.$route.query.tag
    }
    // 获取商品列表
    this.getProductList()
  },
  methods: {
    // 获取分类列表
    getCategoryList() {
      productApi.getCategoryList().then(response => {
        if (response.data.code === 1) {
          this.categories = response.data.data
        }
      }).catch(error => {
        console.error('获取分类列表失败:', error)
      })
    },
    goToDetail(id) {
      this.$router.push(`/product/detail/${id}`)
    },
    // 搜索商品
    searchProducts() {
      this.currentPage = 1
      this.getProductList()
    },
    // 获取商品列表
    getProductList() {
      const params = {
        page: this.currentPage,
        pageSize: this.pageSize,
        categoryId: this.categoryId === '0' ? null : this.categoryId,
        sortBy: this.sortBy,
        keyword: this.keyword,
        tag: this.tag,
        isSale: true
      }
      
      productApi.getProductList(params).then(response => {
        if (response.data.code === 1) {
          this.products = response.data.data.records
          this.total = response.data.data.total
        }
      }).catch(error => {
        console.error('获取商品列表失败:', error)
      })
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.getProductList()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.getProductList()
    },
    // 添加商品到购物车（显示尺码选择弹窗）
    addToCart(product) {
      this.selectedProduct = product
      this.selectedSize = 'M' // 重置为默认尺码
      this.quantity = 1 // 重置数量
      this.cartDialogVisible = true
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
          this.$message.success(`已添加 ${this.selectedProduct.name} (${this.selectedSize码}) × ${this.quantity}`)
          this.cartDialogVisible = false
        } else {
          this.$message.error(response.data.msg || '添加失败')
        }
      }).catch(error => {
        this.$message.error('网络错误，请稍后重试')
        console.error('添加到购物车失败:', error)
      })
    }
  },
  watch: {
    // 监听分类、标签和排序变化，重新获取商品列表
    categoryId() {
      this.currentPage = 1
      this.getProductList()
    },
    tag() {
      this.currentPage = 1
      this.getProductList()
    },
    sortBy() {
      this.currentPage = 1
      this.getProductList()
    }
  }
}
</script>

<style scoped>
.product-list {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

/* === 页面标题 === */
.page-header {
  margin: var(--space-lg) 0 var(--space-xl);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 16px;
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 28px;
  color: var(--text-primary);
  letter-spacing: 0.08em;
}

.page-title span {
  position: relative;
}

.page-title span::after {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 0;
  width: 60px;
  height: 2px;
  background-color: var(--accent-purple);
}

.page-title .el-divider {
  flex: 1;
  border-top: 1px solid var(--border-subtle);
}

/* === 筛选区域 === */
.filter-section {
  margin: var(--space-lg) 0;
}

.filter-card {
  background-color: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-controls {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.filter-select {
  min-width: 140px;
}

/* === 商品网格 === */
.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 2px;
  margin: var(--space-lg) 0;
}

.product-card {
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
  transition: var(--transition-base);
  overflow: hidden;
  animation: floatIn 0.4s ease backwards;
}

.product-card:hover {
  border-color: var(--accent-purple);
  z-index: 3;
}

.product-image-container {
  position: relative;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  cursor: pointer;
  background-color: var(--bg-surface);
}

.product-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: var(--transition-slow);
}

.product-card:hover .product-image {
  transform: scale(1.1);
  filter: grayscale(100%);
}

.product-badge {
  position: absolute;
  top: 12px;
  padding: 4px 12px;
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.08em;
  border-radius: var(--radius-none);
}

.new-badge {
  left: 12px;
  background-color: var(--accent-purple);
}

.hot-badge {
  right: 12px;
  background-color: var(--accent-red);
}

.product-overlay {
  position: absolute;
  bottom: -100%;
  left: 0;
  right: 0;
  background-color: rgba(10, 10, 10, 0.9);
  padding: 16px;
  transition: bottom var(--transition-base);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.product-card:hover .product-overlay {
  bottom: 0;
}

.product-overlay :deep(.add-cart-btn) {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.06em;
  background: var(--accent-purple);
  border: none;
  border-radius: var(--radius-none);
}

.product-overlay :deep(.view-detail-btn) {
  color: var(--text-primary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-none);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 600;
}

.product-overlay :deep(.view-detail-btn:hover) {
  background-color: rgba(255, 255, 255, 0.1);
}

.product-info {
  padding: 16px;
}

.product-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  cursor: pointer;
  margin-bottom: 10px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-name:hover {
  color: var(--accent-purple);
}

.product-rating {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.rating-count {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: 8px;
}

.price-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 800;
  color: var(--accent-lime);
  animation: priceFlicker 3s infinite;
}

.sales {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  background-color: var(--bg-surface);
  padding: 4px 10px;
}

/* === 分页 === */
.pagination {
  margin: var(--space-xl) 0;
  display: flex;
  justify-content: center;
}

.pagination-component {
  padding: var(--space-md) 0;
}

/* === 尺码选择弹窗 === */
.dialog-content {
  padding: 8px 0;
}

.product-preview {
  display: flex;
  gap: 16px;
  padding: 16px;
  background-color: var(--bg-card);
  border: 1px solid var(--border-card);
  margin-bottom: 20px;
}

.preview-image {
  width: 90px;
  height: 90px;
  object-fit: cover;
  flex-shrink: 0;
  border: 1px solid var(--border-card);
}

.preview-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.preview-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  margin: 0;
}

.preview-price {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 800;
  color: var(--accent-lime);
}

.size-section,
.quantity-section {
  margin-bottom: 18px;
}

.section-label {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  margin: 0 0 10px 0;
}

.size-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.size-option {
  min-width: 56px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  border: 1px solid var(--border-subtle);
  cursor: pointer;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  transition: var(--transition-fast);
  background-color: var(--bg-card);
}

.size-option:hover {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.size-option.active {
  background-color: var(--accent-purple);
  color: #fff;
  border-color: var(--accent-purple);
}

.quantity-section :deep(.el-input-number) {
  width: 140px;
}

/* === 响应式 === */
@media (max-width: 1024px) {
  .product-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .product-list {
    padding: 0 var(--space-md);
  }
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .page-title {
    font-size: 22px;
  }
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 480px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
