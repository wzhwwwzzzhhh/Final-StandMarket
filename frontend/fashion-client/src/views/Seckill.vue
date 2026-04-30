<template>
  <div class="seckill">

    <!-- 秒杀券列表 -->
    <div class="seckill-coupons">
      <h3>限时优惠券</h3>
      <!-- 状态筛选 -->
      <div class="filter-section">
        <el-radio-group v-model="filterStatus" @change="handleFilterChange">
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
          <template #header>
            <div class="coupon-header">
              <span class="coupon-name">{{ coupon.name }}</span>
              <el-tag :type="getCouponStatusType(coupon)" size="small">
                {{ getCouponStatusText(coupon) }}
              </el-tag>
            </div>
          </template>
          <div class="coupon-info">
            <div class="coupon-left">
              <div class="seckill-price">¥{{ coupon.seckillPrice }}</div>
              <div class="original-price">¥{{ coupon.originalPrice }}</div>
            </div>
            <div class="coupon-right">
              <div class="coupon-time">
                <div class="time-item">
                  <span class="time-label">起售时间：</span>
                  <span class="time-value">{{ formatDateTime(coupon.startTime) }}</span>
                </div>
                <div class="time-item">
                  <span class="time-label">停售时间：</span>
                  <span class="time-value">{{ formatDateTime(coupon.endTime) }}</span>
                </div>
              </div>
              <div class="coupon-countdown">
                <template v-if="getCouponStatus(coupon) === 1">
                  <span class="countdown-label">剩余时间：</span>
                  <el-countdown :value="new Date(coupon.endTime).getTime()" format="HH:mm:ss" @finish="handleCouponCountdownFinish(coupon.id)" />
                </template>
                <template v-else-if="getCouponStatus(coupon) === 2">
                  <span class="countdown-label">距开始：</span>
                  <el-countdown :value="new Date(coupon.startTime).getTime()" format="HH:mm:ss" @finish="handleCouponCountdownFinish(coupon.id)" />
                </template>
              </div>
              <div class="coupon-stock">库存: {{ coupon.stock }}</div>
              <el-button 
                type="danger" 
                size="small" 
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

    <!-- 特价商品列表 -->
    <div class="special-offers">
      <h3>特价商品</h3>
      <div v-if="loading" class="loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="specialOffers.length === 0" class="empty">
        <el-empty description="暂无特价商品" />
      </div>
      <div v-else class="offer-list">
        <el-card v-for="offer in specialOffers" :key="offer.id" shadow="hover">
          <img :src="offer.image" :alt="offer.name" class="offer-image" />
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
        // 直接获取所有秒杀券
        const couponResponse = await seckillApi.getSeckillCouponList()
        if (couponResponse.data.code === 1) {
          this.seckillCoupons = couponResponse.data.data || []
        }
        
        // 获取特价商品
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
      
      // 处理时间字段为null的情况
      if (!coupon.startTime || !coupon.endTime) {
        // 如果时间字段为null，默认为已结束
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
          
          // 异步处理，开始轮询订单状态
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
    
    // 轮询订单状态
    async pollOrderStatus(orderNumber) {
      const maxAttempts = 20 // 增加轮询次数，因为异步处理需要时间
      const interval = 1000 // 1秒轮询一次
      
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        try {
          // 查询订单详情
          const response = await seckillApi.getSeckillOrderByNumber(orderNumber)
          if (response.data.code === 0) {
            const order = response.data.data
            
            // 订单状态说明：
            // 0: 处理中（异步处理尚未完成）
            // 1: 待支付（异步处理完成，订单已创建）
            // 2: 已支付
            // 3: 已取消
            
            if (order.status === 1 || order.status === 2) {
              // 订单创建成功
              this.$message.success('抢购成功！')
              this.$router.push({
                path: '/order',
                query: { orderNumber: order.orderNumber }
              })
              return
            } else if (order.status === 3) {
              // 订单被取消
              this.$message.error('订单处理失败，请重试')
              return
            }
            // status为0时继续轮询
          } else {
            // 订单不存在，可能是异步处理尚未完成
            console.log('订单尚未创建，继续轮询...')
          }
          
          await new Promise(resolve => setTimeout(resolve, interval))
        } catch (error) {
          // 订单查询失败，可能是订单尚未创建，继续轮询
          console.log('订单查询失败，继续轮询...', error)
          await new Promise(resolve => setTimeout(resolve, interval))
        }
      }
      
      // 轮询超时
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
.seckill {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  animation: fade-enter-active 0.5s ease-out;
  background: linear-gradient(135deg, #e9ecef 0%, #dee2e6 100%);
  min-height: 100vh;
}

.countdown-section {
  margin: 30px 0;
  text-align: center;
  padding: 30px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
}

.countdown-section:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-2px);
}

.countdown-section h2 {
  font-size: 28px;
  font-weight: bold;
  color: #333;
  margin-bottom: 20px;
  position: relative;
  display: inline-block;
}

.countdown-section h2::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  width: 80px;
  height: 3px;
  background-color: #ff4d4f;
  border-radius: 2px;
}

.countdown {
  font-size: 24px;
  font-weight: bold;
  margin-top: 20px;
  color: #ff4d4f;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.countdown span {
  color: #333;
  font-size: 18px;
}

.activity-time {
  margin-top: 15px;
  font-size: 14px;
  color: #666;
}

.filter-section {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.seckill-coupons,
.special-offers {
  margin-top: 50px;
}

.seckill-coupons h3,
.special-offers h3 {
  font-size: 24px;
  font-weight: bold;
  color: #333;
  margin-bottom: 20px;
  position: relative;
  padding-left: 20px;
}

.seckill-coupons h3::before,
.special-offers h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 24px;
  background-color: #ff4d4f;
  border-radius: 2px;
}

.loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  gap: 15px;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.loading .el-icon {
  font-size: 32px;
  color: #ff4d4f;
}

.loading span {
  font-size: 16px;
  color: #666;
  margin-top: 10px;
}

.empty {
  padding: 60px 0;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.coupon-list,
.offer-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 25px;
  margin-top: 30px;
}

.el-card {
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.el-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-5px);
}

.coupon-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
}

.coupon-left {
  text-align: center;
  padding: 20px;
  background-color: #f8f9fa;
  border-radius: 8px;
  min-width: 120px;
  transition: all 0.3s ease;
}

.el-card:hover .coupon-left {
  background-color: rgba(255, 77, 79, 0.1);
  transform: scale(1.05);
}

.seckill-price {
  font-size: 28px;
  font-weight: bold;
  color: #ff4d4f;
  margin-bottom: 5px;
}

.original-price {
  text-decoration: line-through;
  color: #999;
  font-size: 14px;
}

.coupon-right {
  flex: 1;
  margin-left: 20px;
}

.coupon-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.coupon-time {
  margin-bottom: 10px;
}

.time-item {
  font-size: 13px;
  color: #666;
  margin-bottom: 5px;
}

.time-label {
  color: #999;
}

.time-value {
  color: #666;
}

.coupon-countdown {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.countdown-label {
  font-size: 14px;
  color: #ff4d4f;
  font-weight: bold;
  margin-right: 5px;
}

.coupon-name {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 15px;
  color: #333;
  transition: color 0.3s ease;
}

.el-card:hover .coupon-name {
  color: #ff4d4f;
}

.coupon-stock {
  font-size: 14px;
  color: #666;
  margin-bottom: 15px;
  background-color: #f8f9fa;
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
  transition: all 0.3s ease;
}

.el-card:hover .coupon-stock {
  background-color: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.offer-image {
  width: 100%;
  height: 220px;
  object-fit: cover;
  transition: transform 0.3s ease;
  border-radius: 12px 12px 0 0;
}

.el-card:hover .offer-image {
  transform: scale(1.05);
}

.offer-info {
  margin-top: 15px;
  padding: 0 20px 20px;
}

.offer-name {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 15px;
  color: #333;
  transition: color 0.3s ease;
}

.el-card:hover .offer-name {
  color: #ff4d4f;
}

.price-section {
  display: flex;
  align-items: center;
  margin-bottom: 15px;
}

.offer-price {
  font-size: 24px;
  font-weight: bold;
  color: #ff4d4f;
  margin-right: 15px;
  transition: color 0.3s ease;
}

.el-card:hover .offer-price {
  color: #ff7875;
}

.offer-stock {
  font-size: 14px;
  color: #666;
  margin-bottom: 15px;
  background-color: #f8f9fa;
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
  transition: all 0.3s ease;
}

.el-card:hover .offer-stock {
  background-color: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.el-button {
  transition: all 0.3s ease;
  border-radius: 8px;
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
}

.el-button:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.3);
}

.el-button:disabled {
  background-color: #f5f5f5;
  border-color: #d9d9d9;
  color: #bfbfbf;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .seckill {
    padding: 15px;
  }
  
  .countdown-section {
    padding: 20px;
  }
  
  .countdown-section h2 {
    font-size: 24px;
  }
  
  .countdown {
    font-size: 20px;
  }
  
  .seckill-coupons h3,
  .special-offers h3 {
    font-size: 20px;
  }
  
  .loading,
  .empty {
    padding: 40px 0;
  }
  
  .coupon-list,
  .offer-list {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 20px;
  }
  
  .coupon-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 15px;
  }
  
  .coupon-left {
    min-width: 100%;
    text-align: center;
  }
  
  .coupon-right {
    margin-left: 0;
    width: 100%;
  }
  
  .offer-image {
    height: 180px;
  }
}

@media (max-width: 480px) {
  .seckill {
    padding: 10px;
  }
  
  .countdown-section {
    padding: 15px;
  }
  
  .countdown-section h2 {
    font-size: 20px;
  }
  
  .countdown {
    font-size: 18px;
    flex-direction: column;
    gap: 10px;
  }
  
  .seckill-coupons h3,
  .special-offers h3 {
    font-size: 18px;
    padding-left: 15px;
  }
  
  .loading,
  .empty {
    padding: 30px 0;
  }
  
  .coupon-list,
  .offer-list {
    grid-template-columns: 1fr;
    gap: 15px;
  }
  
  .coupon-info {
    padding: 15px;
  }
  
  .seckill-price {
    font-size: 24px;
  }
  
  .offer-image {
    height: 150px;
  }
  
  .offer-info {
    padding: 0 15px 15px;
  }
  
  .offer-price {
    font-size: 20px;
  }
  
  .el-button {
    width: 100%;
    text-align: center;
  }
}
</style>
