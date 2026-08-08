<template>
  <div class="coupon-center">
    <!-- 页面标题 -->
    <div class="page-header">
      <span class="kicker">// COUPON CENTER</span>
      <h2 class="title">领券中心</h2>
      <p class="subtitle">领取满减券、折扣券、现金券，结算时自动抵扣</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else>
      <!-- 可领券列表 -->
      <div v-if="templates.length === 0" class="empty">
        <el-empty description="暂无可以领取的优惠券" />
      </div>
      <div v-else class="coupon-grid">
        <div
          v-for="tpl in templates"
          :key="tpl.id"
          class="coupon-card"
          :class="`type-${tpl.type}`"
        >
          <div class="coupon-main">
            <div class="coupon-value">
              <template v-if="tpl.type === 2">
                <span class="value-num">{{ tpl.discount }}</span>
                <span class="value-unit">折</span>
              </template>
              <template v-else>
                <span class="value-symbol">¥</span>
                <span class="value-num">{{ tpl.discount }}</span>
              </template>
            </div>
            <div class="coupon-info">
              <div class="coupon-name">{{ tpl.name }}</div>
              <div class="coupon-tags">
                <el-tag size="small" :type="typeTagType(tpl.type)" effect="plain">{{ typeText(tpl.type) }}</el-tag>
                <el-tag size="small" type="info" effect="plain">{{ thresholdText(tpl.threshold) }}</el-tag>
              </div>
            </div>
          </div>

          <div class="coupon-desc">
            <span>{{ rangeText(tpl) }}</span>
            <span v-if="tpl.totalCount > 0">限量{{ tpl.totalCount }}张</span>
            <span>每人限领{{ tpl.perUserLimit || 1 }}张</span>
          </div>

          <div class="coupon-footer">
            <span class="coupon-validity">{{ validityText(tpl) }}</span>
            <el-button
              type="primary"
              size="small"
              round
              :disabled="claimedIds.includes(tpl.id)"
              :loading="claimingId === tpl.id"
              @click="handleClaim(tpl)"
            >
              {{ claimedIds.includes(tpl.id) ? '已领取' : '立即领取' }}
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
const templates = ref([])
const claimedIds = ref([])
const claimingId = ref(null)

const typeText = (type) => ({ 1: '满减券', 2: '折扣券', 3: '现金券' }[type] || '优惠券')
const typeTagType = (type) => ({ 1: 'danger', 2: 'warning', 3: 'success' }[type] || 'primary')

const thresholdText = (threshold) => {
  if (!threshold || Number(threshold) === 0) return '无门槛'
  return `满${threshold}可用`
}

const rangeText = (tpl) => {
  if (tpl.scopeType === 1) return '限指定分类'
  if (tpl.scopeType === 2) return `限指定商品`
  return '全店通用'
}

const validityText = (tpl) => {
  if (tpl.validType === 1) {
    return `${formatTime(tpl.startTime)} ~ ${formatTime(tpl.endTime)}有效`
  }
  return `领取后 ${tpl.validDays || 7} 天内有效`
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).slice(0, 10)
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await couponApi.getClaimableTemplates()
    if (res.data.code === 1) {
      templates.value = res.data.data || []
      // 判断哪些已领取（拉取卡包，取未使用+已使用中的模板id视为已领）
      const myRes = await couponApi.getMyCoupons()
      if (myRes.data.code === 1 && myRes.data.data) {
        claimedIds.value = [...new Set(myRes.data.data.map(c => c.templateId))]
      }
    } else {
      ElMessage.error(res.data.msg || '加载失败')
    }
  } catch (error) {
    console.error('加载优惠券失败:', error)
    ElMessage.error('加载优惠券失败')
  } finally {
    loading.value = false
  }
}

const handleClaim = async (tpl) => {
  claimingId.value = tpl.id
  try {
    const res = await couponApi.claimCoupon(tpl.id)
    if (res.data.code === 1) {
      ElMessage.success('领取成功，可在"我的优惠券"中查看')
      claimedIds.value.push(tpl.id)
    } else {
      ElMessage.warning(res.data.msg || '领取失败')
    }
  } catch (error) {
    ElMessage.error('领取失败')
  } finally {
    claimingId.value = null
  }
}

onMounted(loadTemplates)
</script>

<style scoped>
.coupon-center {
  max-width: 1100px;
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
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.coupon-card {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  position: relative;
  overflow: hidden;
  transition: all 0.25s ease;
}

.coupon-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: var(--accent-purple);
}

.coupon-card.type-1::before { background: var(--accent-red); }
.coupon-card.type-2::before { background: var(--accent-orange); }
.coupon-card.type-3::before { background: var(--accent-lime); }

.coupon-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px var(--accent-purple-dim);
}

.coupon-main {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 22px 22px 12px;
}

.value-symbol {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 18px;
  color: var(--accent-lime);
}

.value-num {
  font-family: var(--font-display);
  font-weight: 900;
  font-size: 34px;
  color: var(--accent-lime);
  letter-spacing: -0.02em;
}

.value-unit {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 16px;
  color: var(--accent-lime);
}

.coupon-info {
  flex: 1;
  min-width: 0;
}

.coupon-name {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 15px;
  color: var(--text-primary);
  margin-bottom: 8px;
  letter-spacing: 0.03em;
}

.coupon-tags {
  display: flex;
  gap: 6px;
}

.coupon-tags :deep(.el-tag) {
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
}

.coupon-desc {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  padding: 0 22px 14px;
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  border-bottom: 1px dashed var(--border-subtle);
}

.coupon-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 22px;
}

.coupon-validity {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.coupon-footer :deep(.el-button) {
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.05em;
}

@media (max-width: 768px) {
  .coupon-center {
    padding: 0 var(--space-md);
    padding-top: var(--space-lg);
    padding-bottom: 100px;
  }
  .coupon-grid {
    grid-template-columns: 1fr;
  }
}
</style>