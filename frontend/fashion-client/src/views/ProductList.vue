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

.product-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  min-height: 100vh;
  animation: fadeIn 0.8s ease-out;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 页面标题 */
.page-header {
  margin: 80px 0 40px;
}

.page-title {
  display: flex;
  align-items: center;
  font-size: 28px;
  font-weight: bold;
  color: #333;
  position: relative;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.page-title span {
  flex: 0 0 auto;
  margin-right: 25px;
  position: relative;
}

.page-title span::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 0;
  width: 70px;
  height: 4px;
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7a7a 100%);
  border-radius: 2px;
}

.page-title .el-divider {
  flex: 1;
  border-top: 1px solid #e0e0e0;
}

/* 筛选条件 */
.filter-section {
  margin: 40px 0;
}

.filter-card {
  background: #fff;
  padding: 24px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.filter-card:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.search-input {
  flex: 1;
  min-width: 300px;
  border-radius: 12px;
  border: 2px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.search-input:hover {
  border-color: #e0e0e0;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.search-input:focus {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 3px rgba(255, 77, 79, 0.1);
}

.search-button {
  border-radius: 12px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.3);
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a7a 100%);
  border: none;
}

.search-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 77, 79, 0.4);
  background: linear-gradient(135deg, #ff7a7a 0%, #ff4d4f 100%);
}

.filter-controls {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  align-items: center;
}

.filter-select {
  min-width: 150px;
  border-radius: 12px;
  border: 2px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

.filter-select:hover {
  border-color: #e0e0e0;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

/* 商品列表 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 35px;
  margin: 50px 0;
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
  display: flex;
  align-items: center;
  gap: 5px;
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
  background-color: #f8f9fa;
  padding: 6px 12px;
  border-radius: 20px;
  transition: all 0.3s ease;
}

.product-card:hover .sales {
  background-color: #e9ecef;
  color: #333;
}

/* 分页 */
.pagination {
  margin-top: 60px;
  display: flex;
  justify-content: center;
  margin-bottom: 100px;
}

.pagination-component {
  background: #fff;
  padding: 16px 24px;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.pagination-component:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .product-list {
    padding: 20px;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .filter-card {
    padding: 20px;
  }
  
  .filter-controls {
    flex-direction: column;
    align-items: stretch;
  }
  
  .filter-select {
    min-width: auto;
  }
  
  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 25px;
  }
  
  .product-image-container {
    height: 250px;
  }
  
  .product-name {
    font-size: 18px;
  }
  
  .price {
    font-size: 24px;
  }
}

@media (max-width: 480px) {
  .product-list {
    padding: 10px;
  }
  
  .page-title {
    font-size: 20px;
  }
  
  .filter-card {
    padding: 16px;
  }
  
  .search-input {
    min-width: auto;
  }
  
  .product-grid {
    grid-template-columns: 1fr;
  }
  
  .product-image-container {
    height: 220px;
  }
  
  .product-name {
    font-size: 16px;
  }
  
  .price {
    font-size: 20px;
  }
  
  .pagination-component {
    padding: 12px 16px;
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
