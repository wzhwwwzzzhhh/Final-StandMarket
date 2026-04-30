<template>
  <div class="seckill-order">
    <div class="header">
      <h2>我的秒杀订单</h2>
      <p class="subtitle">查看您购买的秒杀券记录</p>
    </div>

    <!-- 筛选条件 -->
    <div class="filter-section">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="订单状态：">
          <el-select v-model="filter.status" placeholder="全部状态" clearable>
            <el-option label="全部" value=""></el-option>
            <el-option label="待支付" value="1"></el-option>
            <el-option label="已支付" value="2"></el-option>
            <el-option label="已取消" value="3"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="购买时间：">
          <el-date-picker
            v-model="filter.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 订单列表 -->
    <div class="order-list" v-loading="loading">
      <div v-if="seckillOrders.length === 0" class="empty">
        <el-empty description="暂无秒杀订单记录" />
      </div>
      
      <div v-else>
        <div v-for="order in filteredOrders" :key="order.id" class="order-card">
          <div class="order-header">
            <div class="order-info">
              <span class="order-number">订单号：{{ order.orderNumber }}</span>
              <span class="order-time">{{ formatTime(order.createTime) }}</span>
            </div>
            <div class="order-status" :class="'status-' + order.status">
              {{ getStatusText(order.status) }}
            </div>
          </div>

          <div class="order-body">
            <div class="coupon-info">
              <div class="coupon-name">{{ order.couponName || '秒杀券' }}</div>
              <div class="price-section">
                <span class="seckill-price">¥{{ order.seckillPrice || order.amount }}</span>
                <span v-if="order.originalPrice" class="original-price">¥{{ order.originalPrice }}</span>
              </div>
              <div class="coupon-details">
                <span class="detail-item">
                  <el-icon><Clock /></el-icon>
                  购买时间：{{ formatDateTime(order.createTime) }}
                </span>
                <span v-if="order.payTime" class="detail-item">
                  <el-icon><Money /></el-icon>
                  支付时间：{{ formatDateTime(order.payTime) }}
                </span>
              </div>
            </div>
          </div>

          <div class="order-footer">
            <div class="order-actions">
              <el-button 
                v-if="order.status === 1" 
                type="primary" 
                size="small"
                @click="handlePay(order)"
              >
                立即支付
              </el-button>
              <el-button 
                v-if="order.status === 1" 
                type="danger" 
                size="small"
                @click="handleCancel(order)"
              >
                取消订单
              </el-button>
              <el-button 
                v-if="order.status === 2" 
                type="success" 
                size="small"
                @click="handleUse(order)"
              >
                使用优惠券
              </el-button>
              <el-button type="default" size="small" @click="handleDetail(order)">
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Money } from '@element-plus/icons-vue'
import { seckillApi } from '../api/seckill'

// 响应式数据
const loading = ref(false)
const seckillOrders = ref([])

// 筛选条件
const filter = ref({
  status: '',
  dateRange: []
})

// 分页
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

// 计算属性：过滤后的订单
const filteredOrders = computed(() => {
  let filtered = seckillOrders.value
  
  // 状态筛选
  if (filter.value.status) {
    filtered = filtered.filter(order => order.status.toString() === filter.value.status)
  }
  
  // 时间筛选
  if (filter.value.dateRange && filter.value.dateRange.length === 2) {
    const [start, end] = filter.value.dateRange
    filtered = filtered.filter(order => {
      const orderDate = new Date(order.createTime).toISOString().split('T')[0]
      return orderDate >= start && orderDate <= end
    })
  }
  
  return filtered
})

// 加载秒杀订单列表
const loadSeckillOrders = async () => {
  loading.value = true
  try {
    const response = await seckillApi.getSeckillOrderList()
    if (response.data.code === 1) {
      seckillOrders.value = response.data.data || []
      pagination.value.total = seckillOrders.value.length
    } else {
      ElMessage.error(response.data.msg || '加载失败')
    }
  } catch (error) {
    console.error('加载秒杀订单失败:', error)
    ElMessage.error('加载秒杀订单失败')
  } finally {
    loading.value = false
  }
}

// 状态文本映射
const getStatusText = (status) => {
  const statusMap = {
    1: '待支付',
    2: '已支付',
    3: '已取消'
  }
  return statusMap[status] || '未知状态'
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleDateString()
}

const formatDateTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 查询
const handleSearch = () => {
  pagination.value.current = 1
}

// 重置
const handleReset = () => {
  filter.value = {
    status: '',
    dateRange: []
  }
  pagination.value.current = 1
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
}

const handleCurrentChange = (current) => {
  pagination.value.current = current
}

// 订单操作
const handlePay = async (order) => {
  try {
    await ElMessageBox.confirm('确定要支付该订单吗？', '支付确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 这里调用支付接口
    ElMessage.success('支付成功')
    await loadSeckillOrders() // 重新加载列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('支付失败')
    }
  }
}

const handleCancel = async (order) => {
  try {
    await ElMessageBox.confirm('确定要取消该订单吗？', '取消确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 这里调用取消订单接口
    ElMessage.success('取消成功')
    await loadSeckillOrders() // 重新加载列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

const handleUse = (order) => {
  ElMessage.info('使用优惠券功能待实现')
}

const handleDetail = (order) => {
  ElMessage.info(`查看订单详情：${order.orderNumber}`)
}

// 生命周期
onMounted(() => {
  loadSeckillOrders()
})
</script>

<style scoped>
.seckill-order {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h2 {
  color: #333;
  margin-bottom: 10px;
}

.subtitle {
  color: #666;
  font-size: 14px;
}

.filter-section {
  background: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.order-card {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 20px;
  overflow: hidden;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #e4e7ed;
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.order-number {
  font-weight: bold;
  color: #333;
}

.order-time {
  color: #666;
  font-size: 12px;
}

.order-status {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
}

.status-1 { background: #fff0f0; color: #f56c6c; }
.status-2 { background: #f0f9ff; color: #409eff; }
.status-3 { background: #f5f5f5; color: #909399; }

.order-body {
  padding: 20px;
}

.coupon-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coupon-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.price-section {
  text-align: right;
}

.seckill-price {
  font-size: 18px;
  font-weight: bold;
  color: #f56c6c;
}

.original-price {
  font-size: 14px;
  color: #999;
  text-decoration: line-through;
  margin-left: 10px;
}

.coupon-details {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #666;
  font-size: 12px;
}

.order-footer {
  padding: 15px 20px;
  border-top: 1px solid #e4e7ed;
  text-align: right;
}

.order-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.pagination {
  margin-top: 20px;
  text-align: center;
}

.empty {
  text-align: center;
  padding: 40px 0;
}

@media (max-width: 768px) {
  .seckill-order {
    padding: 10px;
  }
  
  .coupon-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .price-section {
    text-align: left;
  }
  
  .order-actions {
    flex-wrap: wrap;
  }
}
</style>