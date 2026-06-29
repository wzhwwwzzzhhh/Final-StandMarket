<template>
  <div class="seckill">
    <!-- 限时优惠券 -->
    <div class="seckill-coupons">
      <h3 class="section-title">
        <span class="title-icon">//</span>
        限时优惠券
      </h3>
      <div class="filter-section">
        <el-radio-group v-model="filterStatus" @change="handleFilterChange" class="status-filter">
          <el-radio-button :value="0">全部</el-radio-button>
          <el-radio-button :value="1">抢购中</el-radio-button>
          <el-radio-button :value="2">即将开始</el-radio-button>
          <el-radio-button :value="3">已结束</el-radio-button>
        </el-radio-group>
      </div>
      
      <div v-if="loading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="filteredCoupons.length === 0" class="empty">
        <el-empty :description="emptyDescription" />
      </div>
      <div v-else class="coupon-list">
        <el-card v-for="coupon in filteredCoupons" :key="coupon.id" shadow="hover" class="coupon-card">
          <div class="coupon-body">
            <div class="coupon-price-block">
              <div class="seckill-price">¥{{ coupon.seckillPrice }}</div>
              <div class="original-price">¥{{ coupon.originalPrice }}</div>
            </div>
            <div class="coupon-detail-block">
              <div class="coupon-name">{{ coupon.name }}</div>
              <div class="coupon-meta">
                <div class="coupon-time">
                  <span class="time-label">起售</span>
                  <span class="time-value">{{ formatDateTime(coupon.startTime) }}</span>
                </div>
                <div class="coupon-time">
                  <span class="time-label">停售</span>
                  <span class="time-value">{{ formatDateTime(coupon.endTime) }}</span>
                </div>
              </div>
              <div class="coupon-countdown">
                <template v-if="getCouponStatus(coupon) === 1">
                  <span class="countdown-label">剩余</span>
                  <el-countdown :value="new Date(coupon.endTime).getTime()" format="HH:mm:ss" @finish="handleCouponCountdownFinish(coupon.id)" />
                </template>
                <template v-else-if="getCouponStatus(coupon) === 2">
                  <span class="countdown-label">倒计时</span>
                  <el-countdown :value="new Date(coupon.startTime).getTime()" format="HH:mm:ss" @finish="handleCouponCountdownFinish(coupon.id)" />
                </template>
              </div>
              <div class="coupon-footer">
                <div class="coupon-stock">库存: {{ coupon.stock }}</div>
                <el-tag :type="getCouponStatusType(coupon)" size="small" class="coupon-status-tag">
                  {{ getCouponStatusText(coupon) }}
                </el-tag>
              </div>
              <el-button 
                type="danger" 
                size="small" 
                class="seckill-btn"
                @click="seckillCoupon(coupon.id)"
                :disabled="!canSeckill(coupon)"
              >
                {{ getSeckillButtonText(coupon) }}
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 特价商品 -->
    <div class="special-offers">
      <h3 class="section-title">
        <span class="title-icon">#</span>
        特价商品
      </h3>
      <div v-if="loading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="specialOffers.length === 0" class="empty">
        <el-empty description="暂无特价商品" />
      </div>
      <div v-else class="offer-list">
        <el-card v-for="offer in specialOffers" :key="offer.id" shadow="hover" class="offer-card">
          <div class="offer-image-wrap">
            <img :src="offer.image" :alt="offer.name" class="offer-image" />
            <div v-if="offer.stock <= 0" class="offer-soldout">SOLD OUT</div>
          </div>
          <div class="offer-info">
            <div class="offer-name">{{ offer.name }}</div>
            <div class="price-section">
              <span class="offer-price">¥{{ offer.offerPrice }}</span>
              <span class="original-price">¥{{ offer.originalPrice }}</span>
            </div>
            <div class="offer-stock">库存: {{ offer.stock }}</div>
            <el-button 
              type="danger" 
              size="small" 
              class="seckill-btn"
              @click="seckillSpecialOffer(offer.id)"
              :disabled="offer.stock <= 0"
            >
              {{ offer.stock <= 0 ? '已抢完' : '立即抢购' }}
            </el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { seckillApi } from '@/api/seckill'

export default {
  name: 'Seckill',
  components: {
    ArrowLeft,
    Loading
  },
  data() {
    return {
      seckillCoupons: [],
      specialOffers: [],
      loading: false,
      filterStatus: 0,
      now: new Date()
    }
  },
  computed: {
    filteredCoupons() {
      if (this.filterStatus === 0) {
        return this.seckillCoupons
      }
      return this.seckillCoupons.filter(coupon => {
        const status = this.getCouponStatus(coupon)
        return status === this.filterStatus
      })
    },
    emptyDescription() {
      const descriptions = {
        0: '暂无秒杀券',
        1: '暂无抢购中的秒杀券',
        2: '暂无即将开始的秒杀券',
        3: '暂无已结束的秒杀券'
      }
      return descriptions[this.filterStatus] || '暂无秒杀券'
    }
  },
  created() {
    this.loadSeckillData()
    this.startNowTimer()
  },
  beforeUnmount() {
    this.stopNowTimer()
  },
  methods: {
    startNowTimer() {
      this.nowTimer = setInterval(() => {
        this.now = new Date()
      }, 1000)
    },
    stopNowTimer() {
      if (this.nowTimer) {
        clearInterval(this.nowTimer)
      }
    },
    async loadSeckillData() {
      this.loading = true
      try {
        const couponResponse = await seckillApi.getSeckillCouponList()
        if (couponResponse.data.code === 1) {
          this.seckillCoupons = couponResponse.data.data || []
        }
        
        try {
          const offerResponse = await seckillApi.getSpecialOfferList()
          if (offerResponse.data.code === 1) {
            this.specialOffers = offerResponse.data.data || []
          }
        } catch (error) {
          console.log('获取特价商品列表失败（接口可能未实现）:', error)
          this.specialOffers = []
        }
      } catch (error) {
        console.error('加载秒杀数据失败:', error)
        this.$message.error('加载秒杀数据失败，请重试')
      } finally {
        this.loading = false
      }
    },
    getCouponStatus(coupon) {
      const now = this.now.getTime()
      
      if (!coupon.startTime || !coupon.endTime) {
        return 3
      }
      
      const startTime = new Date(coupon.startTime).getTime()
      const endTime = new Date(coupon.endTime).getTime()
      
      if (now < startTime) {
        return 2
      } else if (now >= startTime && now <= endTime) {
        return 1
      } else {
        return 3
      }
    },
    getCouponStatusType(coupon) {
      const status = this.getCouponStatus(coupon)
      const typeMap = {
        1: 'danger',
        2: 'warning',
        3: 'info'
      }
      return typeMap[status]
    },
    getCouponStatusText(coupon) {
      const status = this.getCouponStatus(coupon)
      const textMap = {
        1: '抢购中',
        2: '即将开始',
        3: '已结束'
      }
      return textMap[status]
    },
    canSeckill(coupon) {
      const status = this.getCouponStatus(coupon)
      return status === 1 && coupon.stock > 0
    },
    getSeckillButtonText(coupon) {
      const status = this.getCouponStatus(coupon)
      if (coupon.stock <= 0) {
        return '已抢完'
      }
      if (status === 2) {
        return '即将开始'
      }
      if (status === 3) {
        return '已结束'
      }
      return '立即抢购'
    },
    handleFilterChange() {
    },
    handleCountdownFinish() {
      this.loadSeckillData()
    },
    handleCouponCountdownFinish(couponId) {
    },
    formatDateTime(dateTime) {
      if (!dateTime) return ''
      const date = new Date(dateTime)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      const seconds = String(date.getSeconds()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    },
    async seckillCoupon(couponId) {
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      
      try {
        const response = await seckillApi.seckillCoupon(couponId)
        if (response.data.code === 1) {
          const order = response.data.data
          this.$message.success(`抢购请求已提交！订单号：${order.orderNumber}`)
          this.pollOrderStatus(order.orderNumber)
        } else {
          this.$message.error(response.data.msg || '抢购失败')
        }
      } catch (error) {
        console.error('抢购秒杀券失败:', error)
        if (error.response && error.response.data) {
          this.$message.error(error.response.data.msg || '抢购失败，请重试')
        } else {
          this.$message.error('抢购失败，请重试')
        }
      }
    },
    
    async pollOrderStatus(orderNumber) {
      const maxAttempts = 20
      const interval = 1000
      
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        try {
          const response = await seckillApi.getSeckillOrderByNumber(orderNumber)
          if (response.data.code === 0) {
            const order = response.data.data
            
            if (order.status === 1 || order.status === 2) {
              this.$message.success('抢购成功！')
              this.$router.push({
                path: '/order',
                query: { orderNumber: order.orderNumber }
              })
              return
            } else if (order.status === 3) {
              this.$message.error('订单处理失败，请重试')
              return
            }
          } else {
            console.log('订单尚未创建，继续轮询...')
          }
          
          await new Promise(resolve => setTimeout(resolve, interval))
        } catch (error) {
          console.log('订单查询失败，继续轮询...', error)
          await new Promise(resolve => setTimeout(resolve, interval))
        }
      }
      
      this.$message.warning('订单处理超时，请稍后查看订单状态')
    },
    async seckillSpecialOffer(offerId) {
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      
      try {
        const response = await seckillApi.seckillSpecialOffer(offerId)
        if (response.data.success) {
          this.$message.success('抢购成功！')
          this.loadSeckillData()
        } else {
          this.$message.error(response.data.msg || '抢购失败')
        }
      } catch (error) {
        console.error('抢购特价商品失败:', error)
        this.$message.error('抢购失败，请重试')
      }
    }
  }
}
</script>

<style scoped>
/* ============================================================
   SECKILL — 霓虹秒杀页
   ============================================================ */

.seckill {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

/* === 分区标题 === */
.section-title {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 22px;
  color: var(--text-primary);
  letter-spacing: 0.06em;
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-icon {
  font-family: var(--font-display);
  color: var(--accent-purple);
  font-size: 18px;
}

/* === 筛选器 === */
.filter-section {
  margin-bottom: var(--space-xl);
}

.status-filter :deep(.el-radio-button__inner) {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 8px 20px;
  transition: var(--transition-fast);
}

.status-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--accent-purple);
  border-color: var(--accent-purple);
  color: #fff;
  box-shadow: none;
}

/* === 加载和空状态 === */
.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 16px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
}

.loading .el-icon {
  font-size: 32px;
  color: var(--accent-purple);
}

.loading span {
  font-family: var(--font-display);
  font-size: 14px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.empty {
  padding: 80px 0;
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
}

/* ============================================================
   COUPON CARDS — 霓虹优惠券
   ============================================================ */

.coupon-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 4px;
}

.coupon-card {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  transition: var(--transition-base);
}

.coupon-card:hover {
  border-color: var(--accent-purple);
  z-index: 2;
}

.coupon-card :deep(.el-card__body) {
  padding: 0;
}

.coupon-body {
  display: flex;
  padding: 24px;
  gap: 20px;
}

.coupon-price-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px 20px;
  background: var(--bg-surface);
  min-width: 100px;
  transition: var(--transition-fast);
}

.coupon-card:hover .coupon-price-block {
  background: var(--accent-purple-dim);
}

.seckill-price {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 800;
  color: var(--accent-lime);
  animation: priceFlicker 3s infinite;
}

.original-price {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--text-tertiary);
  text-decoration: line-through;
  margin-top: 4px;
}

.coupon-detail-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.coupon-detail-block .coupon-name {
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coupon-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.coupon-time {
  display: flex;
  gap: 8px;
}

.time-label {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
  min-width: 32px;
}

.time-value {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-secondary);
}

.coupon-countdown {
  display: flex;
  align-items: center;
  gap: 8px;
}

.countdown-label {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--accent-orange);
  letter-spacing: 0.04em;
}

.coupon-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.coupon-stock {
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.coupon-status-tag {
  font-family: var(--font-display);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

/* === 秒杀按钮 === */
.seckill-btn {
  width: 100%;
  margin-top: 4px;
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  font-family: var(--font-heading) !important;
  font-weight: 800 !important;
  font-size: 13px !important;
  letter-spacing: 0.06em !important;
  padding: 10px 0 !important;
  transition: var(--transition-base);
  position: relative;
  overflow: hidden;
}

.seckill-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.seckill-btn:hover:not(:disabled)::after {
  transform: translateX(100%);
}

.seckill-btn:hover:not(:disabled) {
  box-shadow: 0 0 20px var(--accent-purple-dim);
}

.seckill-btn:disabled {
  background: var(--bg-surface) !important;
  color: var(--text-tertiary) !important;
  cursor: not-allowed;
}

/* ============================================================
   OFFER CARDS — 特价商品
   ============================================================ */

.seckill-coupons,
.special-offers {
  margin-top: var(--space-xxl);
}

.offer-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}

.offer-card {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  transition: var(--transition-base);
}

.offer-card:hover {
  border-color: var(--accent-purple);
  z-index: 2;
}

.offer-card :deep(.el-card__body) {
  padding: 0;
}

.offer-image-wrap {
  position: relative;
  overflow: hidden;
}

.offer-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
  transition: var(--transition-slow);
}

.offer-card:hover .offer-image {
  transform: scale(1.08);
  filter: grayscale(60%);
}

.offer-soldout {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,0.7);
  color: var(--accent-red);
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 20px;
  letter-spacing: 0.12em;
}

.offer-info {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.offer-name {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-section {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.offer-price {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 800;
  color: var(--accent-lime);
}

.offer-stock {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

/* === 响应式 === */
@media (max-width: 768px) {
  .seckill {
    padding: 0 var(--space-md);
  }

  .coupon-list {
    grid-template-columns: 1fr;
  }

  .coupon-body {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .offer-list {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>