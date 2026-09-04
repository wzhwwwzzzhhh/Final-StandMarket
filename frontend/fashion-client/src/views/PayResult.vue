<template>
  <div class="pay-result">
    <div class="result-card">
      <div v-if="!checked" class="icon-wrapper pending">
        <el-icon :size="64"><Loading /></el-icon>
      </div>
      <div v-else-if="payPending" class="icon-wrapper pending">
        <el-icon :size="64"><Clock /></el-icon>
      </div>
      <div v-else :class="['icon-wrapper', paySuccess ? 'success' : 'fail']">
        <el-icon :size="64">
          <CircleCheck v-if="paySuccess" />
          <CircleClose v-else />
        </el-icon>
      </div>

      <h2 v-if="!checked" class="title">查询中...</h2>
      <h2 v-else-if="payPending" class="title">支付处理中</h2>
      <h2 v-else class="title">{{ resultTitle }}</h2>

      <p v-if="!checked" class="desc">正在查询支付结果...</p>
      <p v-else-if="payPending" class="desc">支付宝已付款成功，等待回调确认中，请稍后刷新查看订单状态</p>
      <p v-else class="desc">{{ resultDescription }}</p>

      <div class="order-info" v-if="orderId">
        <span class="label">订单编号：</span>
        <span class="value">{{ orderId }}</span>
      </div>

      <div class="actions">
        <el-button v-if="payPending" type="primary" size="large" @click="queryPayStatus">
          刷新支付状态
        </el-button>
        <el-button v-else type="primary" size="large" @click="viewOrder">
          查看订单
        </el-button>
        <el-button size="large" @click="goHome">
          返回首页
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { CircleCheck, CircleClose, Loading, Clock } from '@element-plus/icons-vue'
import { paymentApi } from '@/api/payment'
import { buildAlipayVerifyParams, interpretPayStatus } from './paymentStatus'

export default {
  name: 'PayResult',
  components: { CircleCheck, CircleClose, Loading, Clock },
  data() {
    return {
      orderId: null,
      checked: false,
      paymentState: 'loading',
      retryCount: 0,
      retryTimer: null
    }
  },
  computed: {
    paySuccess() {
      return this.paymentState === 'success'
    },
    payPending() {
      return this.paymentState === 'pending'
    },
    resultTitle() {
      const titles = {
        success: '支付成功',
        failed: '支付失败',
        incomplete: '支付未完成',
        invalid: '支付结果无效'
      }
      return titles[this.paymentState] || '支付失败'
    },
    resultDescription() {
      const descriptions = {
        success: '订单已支付成功，等待商家发货',
        failed: '支付失败，请重新尝试',
        incomplete: '未查询到支付记录，请确认后重新支付',
        invalid: '支付回跳参数无效，请返回订单页查询'
      }
      return descriptions[this.paymentState] || '支付结果暂不可用'
    }
  },
  beforeUnmount() {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
    }
  },
  created() {
    this.orderId = this.$route.query.orderId
    // 验签必须保留支付宝返回的完整参数集合，只排除本站追加的 orderId。
    try {
      this.alipayParams = buildAlipayVerifyParams(this.$route.query)
    } catch {
      this.alipayParams = {}
      this.paymentState = 'invalid'
      this.checked = true
      return
    }
    // 如果有支付宝回跳参数，走验签流程
    if (this.alipayParams.out_trade_no || this.alipayParams.trade_no) {
      this.verifyAlipayReturn()
    } else if (this.orderId) {
      this.queryPayStatus()
    } else {
      this.checked = true
      this.paymentState = 'invalid'
    }
  },
  methods: {
    // 支付宝同步回跳验签
    verifyAlipayReturn() {
      paymentApi.verifyReturn(this.alipayParams).then(response => {
        if (response.data.code === 1 && response.data.data) {
          this.applyPayStatus(response.data.data.payStatus)
        } else {
          this.paymentState = 'invalid'
        }
        this.checked = true
      }).catch(() => {
        // 验签失败，降级为直接查询
        if (this.orderId) {
          this.queryPayStatus()
        } else {
          this.checked = true
          this.paymentState = 'invalid'
        }
      })
    },
    queryPayStatus() {
      paymentApi.payStatus(this.orderId).then(response => {
        if (response.data.code === 1) {
          this.applyPayStatus(response.data.data.payStatus)
        } else {
          this.paymentState = 'invalid'
        }
        this.checked = true
      }).catch(() => {
        this.checked = true
        this.paymentState = 'invalid'
      })
    },
    applyPayStatus(status) {
      const mapped = interpretPayStatus(status)
      this.paymentState = mapped.state
      if (mapped.shouldPoll && this.orderId && this.retryCount < 3) {
        this.retryCount++
        this.retryTimer = setTimeout(() => this.queryPayStatus(), 3000)
      }
    },
    viewOrder() {
      this.$router.push('/order')
    },
    goHome() {
      this.$router.push('/')
    }
  }
}
</script>

<style scoped>
.pay-result {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 40px 20px;
}

.result-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 48px;
  text-align: center;
  max-width: 480px;
  width: 100%;
}

.icon-wrapper {
  margin-bottom: 24px;
}

.icon-wrapper.success {
  color: var(--accent-lime);
}

.icon-wrapper.fail {
  color: var(--accent-red);
}

.icon-wrapper.pending {
  color: var(--accent-orange);
  animation: rotate 1.5s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.title {
  font-family: var(--font-heading);
  font-size: 24px;
  font-weight: 900;
  color: var(--text-primary);
  margin: 0 0 12px;
  letter-spacing: 0.06em;
}

.desc {
  color: var(--text-muted);
  font-size: 14px;
  margin: 0 0 24px;
  font-family: var(--font-mono);
}

.order-info {
  margin-bottom: 32px;
  padding: 12px 16px;
  background: var(--bg-surface);
  font-size: 13px;
}

.order-info .label {
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.order-info .value {
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
