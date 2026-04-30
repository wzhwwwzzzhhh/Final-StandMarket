<template>
  <div class="order-list">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">订单管理</h2>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-label">订单总数</span>
          <span class="stat-value">{{ total }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">待处理订单</span>
          <span class="stat-value pending-count">{{ pendingOrdersCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">总销售额</span>
          <span class="stat-value total-amount">¥{{ totalAmount }}</span>
        </div>
      </div>
    </div>
    
    <!-- 搜索和筛选区域 -->
    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索订单号" class="search-input">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="orderStatus" placeholder="选择订单状态" class="filter-select">
          <el-option label="全部" value="0"></el-option>
          <el-option label="待付款" value="1"></el-option>
          <el-option label="待发货" value="2"></el-option>
          <el-option label="已发货" value="3"></el-option>
          <el-option label="已完成" value="4"></el-option>
          <el-option label="已取消" value="5"></el-option>
        </el-select>
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button type="info" class="export-button" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出订单
          </el-button>
        </div>
      </div>
    </div>

    <!-- 订单列表 -->
    <div class="table-section">
      <el-table :data="orders" class="order-table" style="width: 100%">
        <el-table-column prop="id" label="订单ID" width="100" align="center">
          <template #default="scope">
            <span class="id-tag">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="number" label="订单号" min-width="200">
          <template #default="scope">
            <span class="order-number">{{ scope.row.number }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="100" align="center">
          <template #default="scope">
            <span class="user-id">{{ scope.row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="订单金额" width="120" align="center">
          <template #default="scope">
            <span class="amount-value">¥{{ scope.row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="订单状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" class="status-tag">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderTime" label="下单时间" width="180" align="center">
          <template #default="scope">
            <span class="order-time">{{ scope.row.orderTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="scope">
            <div class="action-buttons">
              <el-button type="primary" size="small" class="view-button" @click="handleView(scope.row)">
                <el-icon><View /></el-icon>
                查看
              </el-button>
              <el-button 
                :type="getProcessButtonType(scope.row.status)" 
                size="small" 
                class="process-button" 
                @click="handleProcess(scope.row)"
                :disabled="scope.row.status >= 4"
              >
                <el-icon><Check /></el-icon>
                {{ getProcessButtonText(scope.row.status) }}
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
    
    <!-- 订单详情对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="订单详情"
      width="800px"
      class="order-dialog"
    >
      <div v-if="currentOrder" class="order-detail">
        <div class="order-detail-header">
          <div class="detail-item">
            <span class="detail-label">订单号：</span>
            <span class="detail-value">{{ currentOrder.number }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">订单状态：</span>
            <el-tag :type="getStatusType(currentOrder.status)">{{ getStatusText(currentOrder.status) }}</el-tag>
          </div>
          <div class="detail-item">
            <span class="detail-label">下单时间：</span>
            <span class="detail-value">{{ currentOrder.orderTime }}</span>
          </div>
        </div>
        <div class="order-detail-body">
          <h4 class="detail-title">商品信息</h4>
          <div class="product-list">
            <div class="product-item" v-for="(item, index) in currentOrder.orderItems" :key="index">
              <img :src="item.image" :alt="item.name" class="product-image" />
              <div class="product-info">
                <div class="product-name">{{ item.name }}</div>
                <div class="product-sku">{{ item.skuInfo }}</div>
                <div class="product-price">¥{{ item.price }} x {{ item.quantity }}</div>
              </div>
              <div class="product-subtotal">¥{{ item.price * item.quantity }}</div>
            </div>
          </div>
        </div>
        <div class="order-detail-footer">
          <div class="total-amount">
            总金额：<span class="amount">¥{{ currentOrder.amount }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { orderApi } from '../api/order'
import { Search, Download, View, Check } from '@element-plus/icons-vue'

export default {
  name: 'OrderList',
  components: {
    Search,
    Download,
    View,
    Check
  },
  data() {
    return {
      searchQuery: '',
      orderStatus: '0',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      orders: [],
      dialogVisible: false,
      currentOrder: null
    }
  },
  created() {
    // 初始化数据
    this.getOrderList()
  },
  computed: {
    // 待处理订单数量
    pendingOrdersCount() {
      return this.orders.filter(order => order.status >= 1 && order.status <= 3).length
    },
    // 总销售额
    totalAmount() {
      return this.orders.reduce((sum, order) => sum + order.amount, 0).toFixed(2)
    }
  },
  methods: {
    // 获取订单列表
    getOrderList() {
      const params = {
        page: this.currentPage,
        pageSize: this.pageSize,
        number: this.searchQuery,
        status: this.orderStatus !== '0' ? this.orderStatus : null
      }
      
      orderApi.getOrderList(params).then(response => {
        this.orders = response.data.data.records
        this.total = response.data.data.total
      }).catch(error => {
        console.error('获取订单列表失败:', error)
        this.$message.error('获取订单列表失败')
      })
    },
    
    // 搜索
    handleSearch() {
      this.currentPage = 1
      this.getOrderList()
    },
    
    // 导出订单
    handleExport() {
      this.$message.info('导出订单功能开发中')
    },
    
    // 查看订单
    handleView(row) {
      orderApi.getOrderById(row.id).then(response => {
        this.currentOrder = response.data.data
        this.dialogVisible = true
      }).catch(error => {
        console.error('获取订单详情失败:', error)
        this.$message.error('获取订单详情失败')
      })
    },
    
    // 处理订单
    handleProcess(row) {
      const statusMap = {
        1: '待发货',
        2: '已发货',
        3: '已完成'
      }
      
      const nextStatus = row.status + 1
      if (nextStatus > 3) {
        this.$message.warning('订单已完成，无需处理')
        return
      }
      
      this.$confirm(`确定要将订单状态改为${statusMap[nextStatus]}吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        orderApi.updateOrderStatus(row.id, { status: nextStatus }).then(response => {
          this.$message.success('订单状态更新成功')
          this.getOrderList()
        }).catch(error => {
          console.error('更新订单状态失败:', error)
          this.$message.error('更新订单状态失败')
        })
      }).catch(() => {
        this.$message.info('已取消操作')
      })
    },
    
    // 获取状态类型
    getStatusType(status) {
      switch (status) {
        case 1:
          return 'info'
        case 2:
          return 'warning'
        case 3:
          return 'primary'
        case 4:
          return 'success'
        case 5:
          return 'danger'
        default:
          return 'info'
      }
    },
    
    // 获取状态文本
    getStatusText(status) {
      switch (status) {
        case 1:
          return '待付款'
        case 2:
          return '待发货'
        case 3:
          return '已发货'
        case 4:
          return '已完成'
        case 5:
          return '已取消'
        default:
          return '未知'
      }
    },
    
    // 获取处理按钮类型
    getProcessButtonType(status) {
      switch (status) {
        case 1:
          return 'warning'
        case 2:
          return 'primary'
        case 3:
          return 'success'
        default:
          return 'default'
      }
    },
    
    // 获取处理按钮文本
    getProcessButtonText(status) {
      switch (status) {
        case 1:
          return '发货'
        case 2:
          return '完成'
        case 3:
          return '完成'
        default:
          return '处理'
      }
    },
    
    // 页面大小变化
    handleSizeChange(val) {
      this.pageSize = val
      this.getOrderList()
    },
    
    // 当前页变化
    handleCurrentChange(val) {
      this.currentPage = val
      this.getOrderList()
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
.order-list {
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

.pending-count {
  color: #e6a23c;
  animation: pulse 2s infinite;
}

.total-amount {
  color: #67c23a;
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
.export-button {
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

.export-button {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border: none;
  color: #fff;
}

.export-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.4);
}

/* 订单列表 */
.table-section {
  margin-bottom: 30px;
}

.order-table {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.order-table:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.order-table th {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  padding: 16px;
  text-align: center;
}

.order-table td {
  padding: 16px;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
}

.order-table tr:hover td {
  background: #f8f9fa;
}

/* 订单ID */
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

/* 订单号 */
.order-number {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  line-height: 1.4;
}

/* 用户ID */
.user-id {
  font-size: 14px;
  font-weight: 600;
  color: #666;
}

/* 订单金额 */
.amount-value {
  font-size: 14px;
  font-weight: bold;
  color: #ff4d4f;
}

/* 订单状态 */
.status-tag {
  font-size: 12px;
  font-weight: 500;
  border-radius: 12px;
  padding: 4px 12px;
  transition: all 0.3s ease;
}

.status-tag:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 下单时间 */
.order-time {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.view-button,
.process-button {
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

.view-button {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border: none;
  color: #fff;
}

.view-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.4);
}

.process-button {
  border: none;
  color: #fff;
  transition: all 0.3s ease;
}

.process-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
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

/* 订单详情对话框 */
.order-dialog {
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.order-detail {
  padding: 20px;
  animation: fadeIn 0.5s ease-out;
}

.order-detail-header {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-label {
  font-size: 14px;
  font-weight: 600;
  color: #666;
  min-width: 80px;
}

.detail-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.detail-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.product-list {
  margin-bottom: 20px;
}

.product-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 12px;
  margin-bottom: 12px;
  transition: all 0.3s ease;
}

.product-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.product-image {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-sku {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 12px;
  color: #999;
}

.product-subtotal {
  font-size: 14px;
  font-weight: bold;
  color: #ff4d4f;
  min-width: 80px;
  text-align: right;
}

.order-detail-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.total-amount {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.total-amount .amount {
  color: #ff4d4f;
  font-size: 18px;
  margin-left: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .order-list {
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
  
  .order-detail-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .product-item {
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
  }
  
  .product-subtotal {
    text-align: left;
    min-width: auto;
  }
}

@media (max-width: 768px) {
  .order-list {
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
  
  .order-table th,
  .order-table td {
    padding: 12px;
    font-size: 12px;
  }
  
  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }
  
  .view-button,
  .process-button {
    justify-content: center;
  }
  
  .pagination-component {
    padding: 12px 16px;
  }
  
  .order-dialog {
    width: 95% !important;
  }
  
  .order-detail {
    padding: 16px;
  }
  
  .product-image {
    width: 48px;
    height: 48px;
  }
}

@media (max-width: 480px) {
  .order-list {
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
  .export-button {
    justify-content: center;
  }
  
  .order-table th,
  .order-table td {
    padding: 8px;
    font-size: 11px;
  }
  
  .order-dialog {
    width: 98% !important;
  }
  
  .order-detail {
    padding: 12px;
  }
  
  .product-image {
    width: 36px;
    height: 36px;
  }
  
  .product-name {
    font-size: 12px;
  }
  
  .product-sku {
    font-size: 11px;
  }
  
  .product-price {
    font-size: 11px;
  }
  
  .product-subtotal {
    font-size: 12px;
  }
}
</style>
