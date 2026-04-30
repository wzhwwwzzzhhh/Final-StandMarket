<template>
  <div class="seckill-order-list">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">秒杀订单管理</h2>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-label">秒杀订单总数</span>
          <span class="stat-value">{{ total }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">待支付订单</span>
          <span class="stat-value pending-count">{{ pendingOrdersCount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">秒杀总销售额</span>
          <span class="stat-value total-amount">¥{{ totalAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">平均响应时间</span>
          <span class="stat-value avg-response">{{ avgResponseTime }}ms</span>
        </div>
      </div>
    </div>
    
    <!-- 搜索和筛选区域 -->
    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索订单号或用户ID" class="search-input">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
        </el-input>
        
        <el-select v-model="orderStatus" placeholder="选择订单状态" class="filter-select">
          <el-option label="全部" value="0"></el-option>
          <el-option label="处理中" value="0"></el-option>
          <el-option label="待支付" value="1"></el-option>
          <el-option label="已支付" value="2"></el-option>
          <el-option label="已取消" value="3"></el-option>
        </el-select>
        
        <el-select v-model="activityId" placeholder="选择秒杀活动" class="filter-select">
          <el-option label="全部活动" value="0"></el-option>
          <el-option 
            v-for="activity in activities" 
            :key="activity.id" 
            :label="activity.name" 
            :value="activity.id"
          ></el-option>
        </el-select>
        
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="date-picker"
        />
        
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button type="info" class="reset-button" @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button type="success" class="export-button" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出数据
          </el-button>
        </div>
      </div>
    </div>

    <!-- 秒杀订单列表 -->
    <div class="table-section">
      <el-table :data="orders" class="seckill-order-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="订单ID" width="100" align="center">
          <template #default="scope">
            <span class="id-tag">{{ scope.row.id }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="orderNumber" label="订单号" min-width="180">
          <template #default="scope">
            <div class="order-info">
              <span class="order-number">{{ scope.row.orderNumber }}</span>
              <el-tag v-if="scope.row.orderNumber.startsWith('SK')" size="small" type="danger">秒杀</el-tag>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="userId" label="用户ID" width="100" align="center">
          <template #default="scope">
            <span class="user-id">{{ scope.row.userId }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="couponName" label="秒杀券" min-width="150">
          <template #default="scope">
            <div class="coupon-info">
              <span class="coupon-name">{{ scope.row.couponName || '未知券' }}</span>
              <span class="coupon-price">¥{{ scope.row.seckillPrice }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="activityName" label="所属活动" width="150">
          <template #default="scope">
            <span class="activity-name">{{ scope.row.activityName || '未知活动' }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="订单状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)" class="status-tag">
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="scope">
            <span class="create-time">{{ formatTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="payTime" label="支付时间" width="180" align="center">
          <template #default="scope">
            <span class="pay-time">{{ scope.row.payTime ? formatTime(scope.row.payTime) : '--' }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button type="primary" size="small" class="view-button" @click="handleView(scope.row)">
                <el-icon><View /></el-icon>
                详情
              </el-button>
              
              <el-button 
                v-if="scope.row.status === 1" 
                type="success" 
                size="small" 
                class="confirm-button" 
                @click="handleConfirmPayment(scope.row)"
              >
                <el-icon><Check /></el-icon>
                确认支付
              </el-button>
              
              <el-button 
                v-if="scope.row.status === 0 || scope.row.status === 1" 
                type="warning" 
                size="small" 
                class="cancel-button" 
                @click="handleCancel(scope.row)"
              >
                <el-icon><Close /></el-icon>
                取消订单
              </el-button>
              
              <el-button 
                type="danger" 
                size="small" 
                class="delete-button" 
                @click="handleDelete(scope.row)"
                :disabled="scope.row.status !== 3"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-section">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
    
    <!-- 订单详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="秒杀订单详情" width="800px">
      <div v-if="currentOrder" class="order-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ currentOrder.orderNumber }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentOrder.userId }}</el-descriptions-item>
          <el-descriptions-item label="秒杀券">{{ currentOrder.couponName }}</el-descriptions-item>
          <el-descriptions-item label="秒杀价格">¥{{ currentOrder.seckillPrice }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <el-tag :type="getStatusType(currentOrder.status)">
              {{ getStatusText(currentOrder.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所属活动">{{ currentOrder.activityName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(currentOrder.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ currentOrder.payTime ? formatTime(currentOrder.payTime) : '--' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { Search, Refresh, Download, View, Check, Close, Delete } from '@element-plus/icons-vue'
import { seckillApi } from '@/api/seckillCoupon'

export default {
  name: 'SeckillOrderList',
  components: {
    Search, Refresh, Download, View, Check, Close, Delete
  },
  data() {
    return {
      // 搜索条件
      searchQuery: '',
      orderStatus: '0',
      activityId: '0',
      dateRange: [],
      
      // 分页
      currentPage: 1,
      pageSize: 10,
      total: 0,
      
      // 数据
      orders: [],
      activities: [],
      loading: false,
      
      // 统计信息
      pendingOrdersCount: 0,
      totalAmount: 0,
      avgResponseTime: 0,
      
      // 对话框
      detailDialogVisible: false,
      currentOrder: null
    }
  },
  created() {
    this.loadSeckillOrders()
    this.loadActivities()
    this.loadStatistics()
  },
  methods: {
    // 加载秒杀订单列表
    async loadSeckillOrders() {
      this.loading = true
      try {
        const params = {
          page: this.currentPage,
          pageSize: this.pageSize,
          search: this.searchQuery,
          status: this.orderStatus === '0' ? null : parseInt(this.orderStatus),
          activityId: this.activityId === '0' ? null : parseInt(this.activityId)
        }
        
        // 添加日期筛选
        if (this.dateRange && this.dateRange.length === 2) {
          params.startTime = this.dateRange[0].toISOString()
          params.endTime = this.dateRange[1].toISOString()
        }
        
        const response = await seckillApi.getSeckillOrderList(params)
        if (response.data.code === 0) {
          this.orders = response.data.data.list || []
          this.total = response.data.data.total || 0
        } else {
          this.$message.error('加载秒杀订单失败')
        }
      } catch (error) {
        console.error('加载秒杀订单失败:', error)
        this.$message.error('加载秒杀订单失败')
      } finally {
        this.loading = false
      }
    },
    
    // 加载秒杀活动列表
    async loadActivities() {
      try {
        const response = await seckillApi.getSeckillActivityList()
        if (response.data.code === 0) {
          this.activities = response.data.data || []
        }
      } catch (error) {
        console.error('加载秒杀活动失败:', error)
      }
    },
    
    // 加载统计信息
    async loadStatistics() {
      try {
        const response = await seckillApi.getSeckillOrderStatistics()
        if (response.data.code === 0) {
          const stats = response.data.data
          this.pendingOrdersCount = stats.pendingOrdersCount || 0
          this.totalAmount = stats.totalAmount || 0
          this.avgResponseTime = stats.avgResponseTime || 0
        }
      } catch (error) {
        console.error('加载统计信息失败:', error)
      }
    },
    
    // 搜索
    handleSearch() {
      this.currentPage = 1
      this.loadSeckillOrders()
    },
    
    // 重置
    handleReset() {
      this.searchQuery = ''
      this.orderStatus = '0'
      this.activityId = '0'
      this.dateRange = []
      this.currentPage = 1
      this.loadSeckillOrders()
    },
    
    // 分页大小改变
    handleSizeChange(size) {
      this.pageSize = size
      this.loadSeckillOrders()
    },
    
    // 当前页改变
    handleCurrentChange(page) {
      this.currentPage = page
      this.loadSeckillOrders()
    },
    
    // 查看订单详情
    handleView(order) {
      this.currentOrder = order
      this.detailDialogVisible = true
    },
    
    // 确认支付
    async handleConfirmPayment(order) {
      try {
        await this.$confirm('确认该订单已支付？', '提示', {
          confirmButtonText: '确认',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        const response = await seckillApi.confirmSeckillOrderPayment(order.orderNumber)
        if (response.data.code === 0) {
          this.$message.success('确认支付成功')
          this.loadSeckillOrders()
          this.loadStatistics()
        } else {
          this.$message.error(response.data.msg || '确认支付失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('确认支付失败')
        }
      }
    },
    
    // 取消订单
    async handleCancel(order) {
      try {
        await this.$confirm('确定要取消该订单吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        const response = await seckillApi.cancelSeckillOrder(order.orderNumber)
        if (response.data.code === 0) {
          this.$message.success('取消订单成功')
          this.loadSeckillOrders()
          this.loadStatistics()
        } else {
          this.$message.error(response.data.msg || '取消订单失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('取消订单失败')
        }
      }
    },
    
    // 删除订单
    async handleDelete(order) {
      try {
        await this.$confirm('确定要删除该订单吗？此操作不可恢复！', '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'error'
        })
        
        const response = await seckillApi.deleteSeckillOrder(order.id)
        if (response.data.code === 0) {
          this.$message.success('删除订单成功')
          this.loadSeckillOrders()
          this.loadStatistics()
        } else {
          this.$message.error(response.data.msg || '删除订单失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('删除订单失败')
        }
      }
    },
    
    // 导出数据
    handleExport() {
      this.$message.info('导出功能开发中...')
    },
    
    // 获取状态类型
    getStatusType(status) {
      const typeMap = {
        0: 'info',    // 处理中
        1: 'warning', // 待支付
        2: 'success', // 已支付
        3: 'danger'   // 已取消
      }
      return typeMap[status] || 'info'
    },
    
    // 获取状态文本
    getStatusText(status) {
      const textMap = {
        0: '处理中',
        1: '待支付',
        2: '已支付',
        3: '已取消'
      }
      return textMap[status] || '未知'
    },
    
    // 格式化时间
    formatTime(time) {
      if (!time) return '--'
      return new Date(time).toLocaleString('zh-CN')
    }
  }
}
</script>

<style scoped>
.seckill-order-list {
  padding: 20px;
  background-color: #f5f7fa;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.page-title {
  color: #303133;
  margin: 0;
  font-size: 24px;
}

.header-stats {
  display: flex;
  gap: 30px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 14px;
  color: #909399;
  margin-bottom: 5px;
}

.stat-value {
  display: block;
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.pending-count {
  color: #e6a23c;
}

.total-amount {
  color: #67c23a;
}

.avg-response {
  color: #409eff;
}

.search-section {
  margin-bottom: 20px;
}

.search-card {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  display: flex;
  gap: 15px;
  align-items: center;
}

.search-input {
  width: 200px;
}

.filter-select, .date-picker {
  width: 150px;
}

.search-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.table-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.order-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.coupon-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.coupon-name {
  font-weight: 500;
}

.coupon-price {
  color: #e6a23c;
  font-size: 12px;
}

.activity-name {
  color: #409eff;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.pagination-section {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.order-detail {
  padding: 10px 0;
}
</style>