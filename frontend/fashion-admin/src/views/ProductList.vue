<template>
  <div class="product-list">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">商品管理</h2>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-label">商品总数</span>
          <span class="stat-value">{{ total }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">上架商品</span>
          <span class="stat-value">{{ activeProductsCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">总销量</span>
          <span class="stat-value">{{ totalSales }}</span>
        </div>
      </div>
    </div>
    
    <!-- 搜索和筛选区域 -->
    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索商品名称" class="search-input">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
        </el-input>
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
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button type="success" class="add-button" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            添加商品
          </el-button>
        </div>
      </div>
    </div>

    <!-- 商品列表 -->
    <div class="table-section">
      <el-table :data="products" class="product-table" style="width: 100%">
        <el-table-column prop="id" label="商品ID" width="80" align="center">
          <template #default="scope">
            <span class="id-tag">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="200">
          <template #default="scope">
            <div class="product-name-cell">
              <img :src="scope.row.image || 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=product%20placeholder&image_size=square'" :alt="scope.row.name" class="product-thumbnail" />
              <span class="product-name">{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="categoryId" label="分类" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" effect="plain" class="category-tag">{{ getCategoryName(scope.row.categoryId) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tag" label="标签" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" class="tag-item">{{ scope.row.tag }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="100" align="center">
          <template #default="scope">
            <span class="price-value">¥{{ scope.row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" align="center">
          <template #default="scope">
            <span class="stock-value" :class="{ 'low-stock': scope.row.stock < 10 }">
              {{ scope.row.stock }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sales" label="销量" width="100" align="center">
          <template #default="scope">
            <span class="sales-value">{{ scope.row.sales }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="scope">
            <el-switch 
              v-model="scope.row.status" 
              :active-value="1" 
              :inactive-value="0" 
              @change="handleStatusChange(scope.row)"
              active-text="上架"
              inactive-text="下架"
              class="status-switch"
            ></el-switch>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-button type="primary" size="small" class="edit-button" @click="handleEdit(scope.row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" class="delete-button" @click="handleDelete(scope.row.id)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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
  </div>
</template>

<script>
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { productApi, categoryApi } from '../api/product'

export default {
  name: 'ProductList',
  components: {
    Search,
    Plus,
    Edit,
    Delete
  },
  data() {
    return {
      searchQuery: '',
      categoryId: '0',
      tag: '',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      categories: [],
      products: [],
      updatingStatus: false // 防止重复更新状态
    }
  },
  created() {
    // 初始化数据
    this.getCategoryList()
    this.getProductList()
  },
  computed: {
    // 上架商品数量
    activeProductsCount() {
      return this.products.filter(product => product.status === 1).length
    },
    // 总销量
    totalSales() {
      return this.products.reduce((sum, product) => sum + product.sales, 0)
    }
  },
  methods: {
    // 获取分类列表
    getCategoryList() {
      categoryApi.getCategoryList(1).then(response => {
        this.categories = response.data.data
      }).catch(error => {
        console.error('获取分类列表失败:', error)
        this.$message.error('获取分类列表失败')
      })
    },
    
    // 获取商品列表
    getProductList() {
      const params = {
        page: this.currentPage,
        pageSize: this.pageSize,
        keyword: this.searchQuery,
        tag: this.tag
      }

      productApi.getProductList(params).then(response => {
        this.products = response.data.data.records
        this.total = response.data.data.total
      }).catch(error => {
        console.error('获取商品列表失败:', error)
        this.$message.error('获取商品列表失败')
      })
    },
    
    // 搜索
    handleSearch() {
      this.currentPage = 1
      this.getProductList()
    },
    
    // 添加商品
    handleAdd() {
      this.$router.push('/product/add')
    },
    
    // 编辑商品
    handleEdit(row) {
      this.$router.push(`/product/edit/${row.id}`)
    },
    
    // 删除商品
    handleDelete(id) {
      this.$confirm('确定要删除这个商品吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        productApi.deleteProduct(id).then(response => {
          this.$message.success('删除成功')
          this.getProductList()
        }).catch(error => {
          console.error('删除商品失败:', error)
          this.$message.error('删除商品失败')
        })
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    
    // 修改商品状态
    handleStatusChange(row) {
      // 防止重复触发状态更新
      if (this.updatingStatus) {
        return
      }
      
      this.updatingStatus = true
      
      productApi.updateStatus(row.id, row.status).then(response => {
        if (response.data.code === 1) {
          // 只显示一次成功提示
          this.$message.success('状态更新成功')
        } else {
          this.$message.error('状态更新失败')
          // 恢复原状态
          row.status = !row.status
        }
      }).catch(error => {
        console.error('更新状态失败:', error)
        this.$message.error('更新状态失败')
        // 恢复原状态
        row.status = !row.status
      }).finally(() => {
        // 无论成功失败，都恢复更新状态
        this.updatingStatus = false
      })
    },
    
    // 分页大小变化
    handleSizeChange(val) {
      this.pageSize = val
      this.getProductList()
    },
    
    // 当前页变化
    handleCurrentChange(val) {
      this.currentPage = val
      this.getProductList()
    },
    // 根据分类ID获取分类名称
    getCategoryName(categoryId) {
      const category = this.categories.find(cat => cat.id === categoryId)
      return category ? category.name : '未知分类'
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
.product-list {
  padding: 30px;
  min-height: 100vh;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  animation: fadeIn 0.8s ease-out;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 页面标题和统计信息 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e9ecef;
}

.page-title {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-stats {
  display: flex;
  gap: 30px;
}

.stat-item {
  background: #fff;
  padding: 16px 24px;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  min-width: 120px;
  text-align: center;
}

.stat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.stat-label {
  display: block;
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-value {
  display: block;
  font-size: 20px;
  font-weight: bold;
  color: #333;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 搜索和筛选区域 */
.search-section {
  margin-bottom: 30px;
}

.search-card {
  background: #fff;
  padding: 24px;
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  display: flex;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
  transition: all 0.3s ease;
}

.search-card:hover {
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
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
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

.search-actions {
  display: flex;
  gap: 12px;
}

.search-button,
.add-button {
  border-radius: 12px;
  padding: 10px 24px;
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.search-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
}

.search-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.add-button {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  border: none;
  color: #fff;
}

.add-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(103, 194, 58, 0.4);
}

/* 商品列表 */
.table-section {
  margin-bottom: 30px;
}

.product-table {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.product-table:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.product-table th {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  padding: 16px;
  text-align: center;
}

.product-table td {
  padding: 16px;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
}

.product-table tr:hover td {
  background: #f8f9fa;
}

/* 商品ID */
.id-tag {
  display: inline-block;
  padding: 4px 12px;
  background: #f0f2f5;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #667eea;
  border: 1px solid #e3e6f0;
}

/* 商品名称 */
.product-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.product-thumbnail {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.product-thumbnail:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.product-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  line-height: 1.4;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分类标签 */
.category-tag {
  border-radius: 12px;
  padding: 2px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #667eea;
  border-color: #e3e6f0;
  background: #f8f9fa;
  transition: all 0.3s ease;
}

.category-tag:hover {
  background: #667eea;
  color: #fff;
  border-color: #667eea;
}

/* 标签 */
.tag-item {
  border-radius: 12px;
  padding: 2px 12px;
  font-size: 12px;
  font-weight: 500;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  transition: all 0.3s ease;
}

.tag-item:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

/* 价格 */
.price-value {
  font-size: 14px;
  font-weight: bold;
  color: #ff4d4f;
}

/* 库存 */
.stock-value {
  font-size: 14px;
  font-weight: 600;
  color: #67c23a;
  transition: all 0.3s ease;
}

.stock-value.low-stock {
  color: #ff4d4f;
  animation: pulse 2s infinite;
}

/* 销量 */
.sales-value {
  font-size: 14px;
  font-weight: 600;
  color: #e6a23c;
}

/* 状态开关 */
.status-switch {
  --el-switch-on-color: #67c23a;
  --el-switch-off-color: #909399;
  --el-switch-on-text-color: #fff;
  --el-switch-off-text-color: #fff;
  --el-switch-width: 80px;
  --el-switch-height: 32px;
  transition: all 0.3s ease;
}

.status-switch:hover {
  transform: scale(1.05);
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.edit-button,
.delete-button {
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.edit-button {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border: none;
  color: #fff;
}

.edit-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.delete-button {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a45 100%);
  border: none;
  color: #fff;
}

.delete-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.4);
}

/* 分页 */
.pagination {
  margin-top: 30px;
  display: flex;
  justify-content: center;
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
@media (max-width: 1200px) {
  .product-list {
    padding: 20px;
  }
  
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
  }
  
  .header-stats {
    width: 100%;
    justify-content: space-between;
  }
  
  .stat-item {
    flex: 1;
    min-width: auto;
  }
  
  .search-card {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-input {
    min-width: auto;
  }
  
  .filter-select {
    min-width: auto;
  }
  
  .search-actions {
    justify-content: center;
  }
}

@media (max-width: 768px) {
  .product-list {
    padding: 15px;
  }
  
  .page-title {
    font-size: 20px;
  }
  
  .header-stats {
    flex-direction: column;
    gap: 12px;
  }
  
  .stat-item {
    padding: 12px 16px;
  }
  
  .search-card {
    padding: 20px;
  }
  
  .product-table th,
  .product-table td {
    padding: 12px;
    font-size: 12px;
  }
  
  .product-thumbnail {
    width: 36px;
    height: 36px;
  }
  
  .product-name {
    max-width: 120px;
    font-size: 12px;
  }
  
  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }
  
  .edit-button,
  .delete-button {
    justify-content: center;
  }
  
  .pagination-component {
    padding: 12px 16px;
  }
}

@media (max-width: 480px) {
  .product-list {
    padding: 10px;
  }
  
  .page-title {
    font-size: 18px;
  }
  
  .search-card {
    padding: 16px;
  }
  
  .search-actions {
    flex-direction: column;
  }
  
  .search-button,
  .add-button {
    justify-content: center;
  }
  
  .product-table th,
  .product-table td {
    padding: 8px;
    font-size: 11px;
  }
  
  .product-thumbnail {
    width: 28px;
    height: 28px;
  }
  
  .product-name {
    max-width: 80px;
    font-size: 11px;
  }
  
  .status-switch {
    --el-switch-width: 60px;
    --el-switch-height: 24px;
  }
}
</style>
