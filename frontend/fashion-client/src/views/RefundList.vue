<template>
  <div class="refund-list-page">
    <div class="header">
      <h2>退款记录</h2>
    </div>

    <div class="refund-list" v-loading="loading">
      <div v-if="refunds.length > 0">
        <div v-for="refund in refunds" :key="refund.id" class="refund-item">
          <div class="refund-header">
            <div class="refund-info">
              <span class="refund-no">退款单号：{{ refund.refundNo }}</span>
              <span class="order-no">订单号：{{ refund.orderId }}</span>
              <span class="refund-time">{{ formatTime(refund.createTime) }}</span>
            </div>
            <div class="refund-status" :class="'status-' + refund.status">
              {{ getStatusText(refund.status) }}
            </div>
          </div>

          <div class="refund-body">
            <div class="refund-row">
              <span class="label">退款金额：</span>
              <span class="amount">¥{{ refund.amount }}</span>
            </div>
            <div class="refund-row" v-if="refund.reason">
              <span class="label">退款原因：</span>
              <span class="reason">{{ refund.reason }}</span>
            </div>
            <div class="refund-row" v-if="refund.auditOpinion">
              <span class="label">审核意见：</span>
              <span class="opinion">{{ refund.auditOpinion }}</span>
            </div>
            <div class="refund-row" v-if="refund.auditTime && refund.status !== 0">
              <span class="label">审核时间：</span>
              <span>{{ formatTime(refund.auditTime) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无退款记录"></el-empty>
      </div>
    </div>
  </div>
</template>

<script>
import { refundApi } from '@/api/refund'

export default {
  name: 'RefundList',
  data() {
    return {
      refunds: [],
      loading: false
    }
  },
  mounted() {
    this.loadRefunds()
  },
  methods: {
    loadRefunds() {
      this.loading = true
      refundApi.list().then(response => {
        if (response.data.code === 1) {
          this.refunds = response.data.data || []
        } else {
          this.$message.error(response.data.msg || '获取退款记录失败')
        }
      }).catch(error => {
        console.error('获取退款记录失败:', error)
        this.$message.error('获取退款记录失败')
      }).finally(() => {
        this.loading = false
      })
    },
    getStatusText(status) {
      const map = { 0: '待审核', 2: '已退款', 3: '已拒绝' }
      return map[status] || '未知'
    },
    formatTime(time) {
      if (!time) return ''
      const d = new Date(time)
      const pad = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    }
  }
}
</script>

<style scoped>
.refund-list-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-xl);
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--border-subtle);
}

.header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 22px;
  color: var(--text-primary);
  letter-spacing: 0.06em;
}

.refund-list {
  min-height: 400px;
}

.refund-item {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  margin-bottom: 4px;
  transition: var(--transition-base);
}

.refund-item:hover {
  border-color: var(--accent-purple);
}

.refund-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-card);
  background: var(--bg-surface);
}

.refund-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.refund-no {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.order-no {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.refund-time {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.refund-status {
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  padding: 6px 14px;
  letter-spacing: 0.04em;
}

.status-0 {
  color: var(--accent-orange);
  border: 1px solid var(--accent-orange);
}

.status-2 {
  color: var(--accent-lime);
  border: 1px solid var(--accent-lime);
}

.status-3 {
  color: #ff4d4f;
  border: 1px solid #ff4d4f;
}

.refund-body {
  padding: 16px 20px;
}

.refund-row {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
  font-family: var(--font-display);
  line-height: 1.6;
}

.refund-row:last-child {
  margin-bottom: 0;
}

.refund-row .label {
  color: var(--text-tertiary);
  min-width: 80px;
  flex-shrink: 0;
}

.refund-row .amount {
  color: var(--accent-lime);
  font-weight: 800;
  font-size: 16px;
}

.refund-row .reason,
.refund-row .opinion {
  color: var(--text-primary);
}

.empty-state {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 80px 0;
}

@media (max-width: 768px) {
  .refund-list-page {
    padding: 0 var(--space-md);
  }
}
</style>
