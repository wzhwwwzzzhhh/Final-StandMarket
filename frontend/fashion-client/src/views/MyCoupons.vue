<template>
  <div class="my-coupons">
    <!-- 页面标题 -->
    <div class="page-header">
      <span class="kicker">// MY COUPON WALLET</span>
      <h2 class="title">我的优惠券</h2>
      <p class="subtitle">下单结算时可选择一张可用券抵扣</p>
    </div>

    <!-- 状态筛选 -->
    <div class="filter-section">
      <el-radio-group v-model="filter" @change="loadCoupons">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">未使用</el-radio-button>
        <el-radio-button label="1">已使用</el-radio-button>
        <el-radio-button label="2">已过期</el-radio-button>
      </el-radio-group>
      <el-button type="primary" plain size="small" class="go-center" @click="$router.push('/coupon-center')">
        去领券中心
      </el-button>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else class="coupons-content">
      <div v-if="coupons.length === 0" class="empty">
        <el-empty description="暂无优惠券">
          <el-button type="primary" plain @click="$router.push('/coupon-center')">去领券</el-button>
        </el-empty>
      </div>

      <div v-else class="coupon-grid">
        <div
          v-for="coupon in coupons"
          :key="coupon.id"
          class="coupon-card"
          :class="[`status-${coupon.status}`, `type-${coupon.templateType}`]"
        >
          <div class="coupon-left">
            <template v-if="coupon.templateType === 2">
              <span class="value-num">{{ coupon.discount }}</span>
              <span class="value-unit">折</span>
            </template>
            <template v-else>
              <span class="value-symbol">¥</span>
              <span class="value-num">{{ coupon.discount }}</span>
            </template>
            <span class="value-threshold">{{ thresholdText(coupon) }}</span>
          </div>

          <div class="coupon-right">
            <div class="coupon-name">{{ coupon.templateName }}</div>
            <div class="coupon-tags">
              <el-tag size="small" effect="plain" :type="typeTagType(coupon.templateType)">{{ typeText(coupon.templateType) }}</el-tag>
              <el-tag size="small" :type="statusTagType(coupon.status)" effect="plain">{{ statusText(coupon.status) }}</el-tag>
            </div>
            <div class="coupon-meta">
              <span>有效期至 {{ formatTime(coupon.expireTime) }}</span>
              <span v-if="coupon.useOrderId">核销单#{{ coupon.useOrderId }}</span>
            </div>
          </div>

          <div class="coupon-action">
            <el-button
              v-if="coupon.status === 0"
              type="primary"
              size="small"
              round
              @click="$router.push('/product/list')"
            >
              去使用
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { couponApi } from '../api/coupon'

const loading = ref(false)
const filter = ref('')
const coupons = ref([])

const typeText = (type) => ({ 1: '满减', 2: '折扣', 3: '现金' }[type] || '优惠券')
const typeTagType = (type) => ({ 1: 'danger', 2: 'warning', 3: 'success' }[type] || 'primary')

const statusText = (status) => ({ 0: '未使用', 1: '已使用', 2: '已过期', 3: '已锁定' }[status] || '未知')
const statusTagType = (status) => ({ 0: 'success', 1: 'info', 2: 'warning', 3: 'danger' }[status] || 'info')

const thresholdText = (coupon) => {
  if (!coupon.threshold || Number(coupon.threshold) === 0) return '无门槛'
  return `满${coupon.threshold}可用`
}

const formatTime = (time) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 16)
}

const loadCoupons = async () => {
  loading.value = true
  try {
    const res = await couponApi.getMyCoupons(filter.value)
    if (res.data.code === 1) {
      coupons.value = res.data.data || []
    } else {
      ElMessage.error(res.data.msg || '加载失败')
      coupons.value = []
    }
  } catch (error) {
    console.error('加载优惠券失败:', error)
    ElMessage.error('加载优惠券失败')
    coupons.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadCoupons)
</script>

<style scoped>
.my-coupons {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  padding-top: var(--space-2xl);
  padding-bottom: 120px;
  min-height: 100vh;
}

.page-header {
  text-align: center;
  margin-bottom: var(--space-xl);
}

.kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.3em;
  color: var(--accent-purple);
}

.title {
  font-family: var(--font-heading);
  font-size: 30px;
  font-weight: 900;
  color: var(--text-primary);
  letter-spacing: 0.06em;
  margin: 8px 0;
}

.subtitle {
  color: var(--text-muted);
  font-size: 13px;
  font-family: var(--font-mono);
}

.filter-section {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.filter-section :deep(.el-radio-button__inner) {
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.loading-container {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 40px;
}

.empty {
  padding: 80px 0;
}

.coupon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 20px;
}

.coupon-card {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.25s ease;
}

.coupon-card::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 118px;
  width: 1px;
  border-left: 1px dashed var(--border-subtle);
}

.coupon-card.status-0 { border-color: var(--accent-purple-dim); }
.coupon-card.status-0::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--accent-purple);
}

.coupon-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--accent-purple-dim);
}

.coupon-left {
  width: 118px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.coupon-left .value-symbol {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 16px;
  color: var(--accent-lime);
}

.coupon-left .value-num {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: 30px;
  color: var(--accent-lime);
  line-height: 1;
}

.coupon-left .value-unit {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 16px;
  color: var(--accent-lime);
}

.coupon-left .value-threshold {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.coupon-right {
  flex: 1;
  min-width: 0;
  padding-left: 24px;
  margin-right: 12px;
}

.coupon-name {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 14px;
  color: var(--text-primary);
  letter-spacing: 0.03em;
  margin-bottom: 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.coupon-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}

.coupon-tags :deep(.el-tag) {
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

.coupon-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.coupon-action {
  flex-shrink: 0;
}

.coupon-action :deep(.el-button) {
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.05em;
}

@media (max-width: 768px) {
  .my-coupons {
    padding: 0 var(--space-md);
    padding-top: var(--space-lg);
    padding-bottom: 100px;
  }
  .coupon-grid {
    grid-template-columns: 1fr;
  }
  .coupon-card {
    flex-wrap: wrap;
    gap: 12px;
  }
  .coupon-card::after {
    display: none;
  }
  .coupon-action {
    width: 100%;
  }
}
</style>