<template>
  <div class="my-coupons">
    <!-- 页面标题 -->
    <div class="header">
      <h2>我的优惠券</h2>
      <p class="subtitle">查看您拥有的秒杀券</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 优惠券列表 -->
    <div v-else class="coupons-content">
      <!-- 状态筛选 -->
      <div class="filter-section">
        <el-radio-group v-model="filter.status" @change="handleFilterChange">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button label="1">可用</el-radio-button>
          <el-radio-button label="2">已使用</el-radio-button>
          <el-radio-button label="3">已过期</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 优惠券列表 -->
      <div class="coupons-list" v-loading="listLoading">
        <div v-if="coupons.length === 0" class="empty">
          <el-empty description="暂无优惠券" />
        </div>
        
        <div v-else class="coupons-grid">
          <div 
            v-for="coupon in filteredCoupons" 
            :key="coupon.id" 
            class="coupon-card"
            :class="getCouponStatusClass(coupon)"
          >
            <div class="coupon-header">
              <div class="coupon-name">{{ coupon.couponName || '秒杀券' }}</div>
              <div class="coupon-status" :class="'status-' + getCouponStatus(coupon)">
                {{ getCouponStatusText(coupon) }}
              </div>
            </div>

            <div class="coupon-body">
              <div class="price-section">
                <span class="seckill-price">¥{{ coupon.seckillPrice || 0 }}</span>
                <span class="original-price">原价：¥{{ coupon.originalPrice }}</span>
              </div>
              
              <div class="coupon-info">
                <div class="info-item">
                  <el-icon><Calendar /></el-icon>
                  <span>创建时间：{{ formatTime(coupon.createTime) }}</span>
                </div>
                <div v-if="coupon.payTime" class="info-item">
                  <el-icon><Clock /></el-icon>
                  <span>支付时间：{{ formatTime(coupon.payTime) }}</span>
                </div>
                <div v-if="coupon.orderNumber" class="info-item">
                  <el-icon><Document /></el-icon>
                  <span>订单号：{{ coupon.orderNumber }}</span>
                </div>
              </div>
            </div>

            <div class="coupon-footer">
              <div class="coupon-actions">
                <el-button 
                  v-if="getCouponStatus(coupon) === 1" 
                  type="primary" 
                  size="small"
                  @click="handleUse(coupon)"
                >
                  立即支付
                </el-button>
                <el-button 
                  v-else-if="getCouponStatus(coupon) === 2" 
                  type="success" 
                  size="small"
                  @click="handleUseCoupon(coupon)"
                >
                  立即使用
                </el-button>
                <el-button 
                  v-else-if="getCouponStatus(coupon) === 3" 
                  type="info" 
                  size="small"
                  disabled
                >
                  已取消
                </el-button>
                <el-button 
                  v-else-if="getCouponStatus(coupon) === 4" 
                  type="warning" 
                  size="small"
                  disabled
                >
                  已使用
                </el-button>
                <el-button 
                  v-else-if="getCouponStatus(coupon) === 5" 
                  type="info" 
                  size="small"
                  disabled
                >
                  已过期
                </el-button>
                <el-button type="default" size="small" @click="handleDetail(coupon)">
                  查看详情
                </el-button>
              </div>
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

    <!-- 优惠券详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="优惠券详情" width="500px">
      <div v-if="selectedCoupon" class="coupon-detail">
        <div class="detail-item">
          <label>券名称：</label>
          <span>{{ selectedCoupon.couponName || '秒杀券' }}</span>
        </div>
        <div class="detail-item">
          <label>原价：</label>
          <span>¥{{ selectedCoupon.originalPrice }}</span>
        </div>
        <div class="detail-item">
          <label>秒杀价：</label>
          <span>¥{{ selectedCoupon.seckillPrice || 0 }}</span>
        </div>
        <div class="detail-item">
          <label>有效期：</label>
          <span>{{ formatTime(selectedCoupon.startTime) }} - {{ formatTime(selectedCoupon.endTime) }}</span>
        </div>
        <div class="detail-item">
          <label>购买时间：</label>
          <span>{{ formatTime(selectedCoupon.createTime) }}</span>
        </div>
        <div class="detail-item">
          <label>订单号：</label>
          <span>{{ selectedCoupon.orderNumber }}</span>
        </div>
        <div class="detail-item">
          <label>状态：</label>
          <span :class="'status-' + getCouponStatus(selectedCoupon)">
            {{ getCouponStatusText(selectedCoupon) }}
          </span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Calendar, Document } from '@element-plus/icons-vue'
import { seckillApi } from '../api/seckill'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const listLoading = ref(false)
const coupons = ref([])
const selectedCoupon = ref(null)
const showDetailDialog = ref(false)

// 筛选条件
const filter = ref({
  status: '' // 1:可用, 2:已使用, 3:已过期
})

// 分页
const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

// 计算属性：筛选后的优惠券
const filteredCoupons = computed(() => {
  if (!filter.value.status) {
    return coupons.value
  }
  
  return coupons.value.filter(coupon => {
    const status = getCouponStatus(coupon)
    return status.toString() === filter.value.status
  })
})

// 方法
const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 获取优惠券状态（秒杀订单状态：1-待支付 2-已支付待使用 3-已取消 4-已使用 5-已过期）
const getCouponStatus = (coupon) => {
  return coupon.status || 1
}

// 获取优惠券状态文本
const getCouponStatusText = (coupon) => {
  const status = getCouponStatus(coupon)
  switch (status) {
    case 1: return '待支付'
    case 2: return '待使用'
    case 3: return '已取消'
    case 4: return '已使用'
    case 5: return '已过期'
    default: return '未知'
  }
}

// 获取优惠券状态类名
const getCouponStatusClass = (coupon) => {
  const status = getCouponStatus(coupon)
  return `status-${status}`
}

// 筛选变化
const handleFilterChange = () => {
  pagination.value.current = 1
  loadCoupons()
}

// 分页大小变化
const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  loadCoupons()
}

// 当前页变化
const handleCurrentChange = (current) => {
  pagination.value.current = current
  loadCoupons()
}

// 立即支付
const handleUse = async (coupon) => {
  try {
    await ElMessageBox.confirm('确定要支付该订单吗？', '支付确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const response = await seckillApi.paySeckillOrder(coupon.orderNumber)
    if (response.data.code === 1) {
      ElMessage.success('支付成功')
    } else {
      ElMessage.error('支付失败: ' + response.data.msg)
    }
    
    // 刷新列表
    loadCoupons()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('支付失败')
    }
  }
}

// 立即使用优惠券（跳转到商品列表）
const handleUseCoupon = async (coupon) => {
  try {
    await ElMessageBox.confirm('该优惠券可前往商品列表使用，是否前往？', '使用优惠券', {
      confirmButtonText: '前往购物',
      cancelButtonText: '取消',
      type: 'success'
    })
    router.push('/product/list')
  } catch (error) {
  }
}

// 查看详情
const handleDetail = (coupon) => {
  selectedCoupon.value = coupon
  showDetailDialog.value = true
}

// 加载优惠券数据（秒杀券订单）
const loadCoupons = async () => {
  listLoading.value = true
  try {
    // 调用后端接口获取秒杀券订单数据
    const response = await seckillApi.getSeckillOrderList()
    if (response.data.code === 1) {
      coupons.value = response.data.data
      pagination.value.total = coupons.value.length
    } else {
      ElMessage.error('加载优惠券失败: ' + response.data.msg)
      coupons.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    console.error('加载优惠券失败:', error)
    ElMessage.error('加载优惠券失败')
    coupons.value = []
    pagination.value.total = 0
  } finally {
    listLoading.value = false
    loading.value = false
  }
}

// 初始化加载
onMounted(() => {
  loading.value = true
  loadCoupons()
})
</script>

<style scoped>
.my-coupons {
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
  margin-bottom: 20px;
  text-align: center;
}

.coupons-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.coupon-card {
  background: white;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  transition: all 0.3s;
  position: relative;
  overflow: hidden;
}

.coupon-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.coupon-card.status-1 {
  border-color: #67c23a;
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
}

.coupon-card.status-2 {
  border-color: #409eff;
  background: linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%);
}

.coupon-card.status-3 {
  border-color: #f56c6c;
  background: #fef0f0;
}

.coupon-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.coupon-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.coupon-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: bold;
}

.coupon-status.status-1 {
  background: #67c23a;
  color: white;
}

.coupon-status.status-2 {
  background: #909399;
  color: white;
}

.coupon-status.status-3 {
  background: #f56c6c;
  color: white;
}

.price-section {
  margin-bottom: 15px;
}

.seckill-price {
  font-size: 24px;
  font-weight: bold;
  color: #f56c6c;
  margin-right: 10px;
}

.original-price {
  color: #999;
  text-decoration: line-through;
  font-size: 14px;
}

.coupon-info {
  margin-bottom: 15px;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  color: #666;
  font-size: 14px;
}

.info-item .el-icon {
  margin-right: 8px;
  color: #999;
}

.coupon-footer {
  border-top: 1px solid #f0f0f0;
  padding-top: 15px;
}

.coupon-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

.empty {
  padding: 60px 0;
  text-align: center;
}

.coupon-detail {
  padding: 0 20px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-item label {
  font-weight: bold;
  color: #333;
}

.detail-item span {
  color: #666;
}

.loading-container {
  padding: 40px 0;
}

@media (max-width: 768px) {
  .my-coupons {
    padding: 10px;
  }
  
  .coupons-grid {
    grid-template-columns: 1fr;
  }
  
  .coupon-card {
    padding: 15px;
  }
  
  .coupon-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .coupon-actions {
    flex-direction: column;
  }
}
</style>