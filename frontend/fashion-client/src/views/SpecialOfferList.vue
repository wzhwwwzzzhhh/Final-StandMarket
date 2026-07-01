<template>
  <div class="special-offer-page">
    <div class="page-hero">
      <h1 class="hero-title">特价商品</h1>
      <p class="hero-subtitle">限时特惠 · 先到先得</p>
    </div>

    <div class="offer-container">
      <div v-if="loading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="offers.length === 0" class="empty-state">
        <el-empty description="暂无特价活动" />
      </div>

      <div v-else class="offer-grid">
        <div v-for="offer in offers" :key="offer.id" class="offer-card" @click="goDetail(offer)">
          <div class="offer-image-wrap">
            <img :src="offer.image || '/placeholder.png'" :alt="offer.name" class="offer-image" />
            <div v-if="offer.stock <= 0" class="offer-soldout">SOLD OUT</div>
            <div class="offer-discount-badge" v-if="offer.originalPrice && offer.offerPrice">
              -{{ calcDiscount(offer.originalPrice, offer.offerPrice) }}%
            </div>
          </div>
          <div class="offer-info">
            <div class="offer-name">{{ offer.name }}</div>
            <div class="price-section">
              <span class="offer-price">¥{{ offer.offerPrice }}</span>
              <span class="original-price" v-if="offer.originalPrice">¥{{ offer.originalPrice }}</span>
            </div>
            <div class="offer-meta">
              <span class="offer-stock">库存 {{ offer.stock }}</span>
              <span class="offer-time" v-if="offer.endTime">距结束 {{ timeRemaining(offer.endTime) }}</span>
            </div>
            <el-button
              type="danger"
              class="buy-btn"
              :disabled="offer.stock <= 0"
              @click.stop="handleBuy(offer)"
            >
              {{ offer.stock <= 0 ? '已抢完' : '立即抢购' }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Loading } from '@element-plus/icons-vue'
import { seckillApi } from '@/api/seckill'

export default {
  name: 'SpecialOfferList',
  components: { Loading },
  data() {
    return {
      offers: [],
      loading: false,
      timer: null
    }
  },
  mounted() {
    this.loadOffers()
    this.timer = setInterval(() => {}, 1000)
  },
  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    loadOffers() {
      this.loading = true
      seckillApi.getSpecialOfferList().then(res => {
        this.offers = (res.data.data || []).map(o => ({
          ...o,
          image: o.image || (o.product && o.product.image)
        }))
      }).catch(() => {
        this.$message.error('获取特价商品失败')
      }).finally(() => {
        this.loading = false
      })
    },

    calcDiscount(original, offer) {
      if (!original || original <= 0) return 0
      return Math.round((1 - offer / original) * 100)
    },

    timeRemaining(endTime) {
      if (!endTime) return ''
      const diff = new Date(endTime) - Date.now()
      if (diff <= 0) return '已结束'
      const h = Math.floor(diff / 3600000)
      const m = Math.floor((diff % 3600000) / 60000)
      const s = Math.floor((diff % 60000) / 1000)
      return `${h}时${m}分${s}秒`
    },

    goDetail(offer) {
      // 如果有关联商品ID，可跳转到商品详情
    },

    handleBuy(offer) {
      if (!localStorage.getItem('token')) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      seckillApi.seckillSpecialOffer(offer.id).then(() => {
        this.$message.success('抢购成功！')
        this.loadOffers()
      }).catch(err => {
        const msg = err.response?.data?.message || '抢购失败'
        this.$message.error(msg)
      })
    }
  }
}
</script>

<style scoped>
.special-offer-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-hero {
  text-align: center;
  padding: 60px 20px 40px;
}

.hero-title {
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 900;
  color: var(--text-primary);
  margin: 0 0 8px;
  letter-spacing: 0.04em;
}

.hero-subtitle {
  font-family: var(--font-display);
  font-size: 14px;
  color: var(--text-tertiary);
  letter-spacing: 0.15em;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  gap: 16px;
  color: var(--text-tertiary);
}

.offer-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}

.offer-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  overflow: hidden;
  cursor: pointer;
  transition: var(--transition-base);
}

.offer-card:hover {
  transform: translateY(-4px);
  border-color: var(--accent-purple);
  box-shadow: 0 8px 32px rgba(209, 0, 255, 0.15);
}

.offer-image-wrap {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  background: var(--bg-secondary);
}

.offer-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.offer-card:hover .offer-image {
  transform: scale(1.05);
}

.offer-soldout {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 24px;
  letter-spacing: 0.1em;
}

.offer-discount-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  background: var(--accent-red);
  color: #fff;
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 13px;
  padding: 4px 10px;
}

.offer-info {
  padding: 16px;
}

.offer-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  margin-bottom: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.price-section {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}

.offer-price {
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 20px;
  color: var(--accent-red);
}

.original-price {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--text-tertiary);
  text-decoration: line-through;
}

.offer-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-family: var(--font-display);
  font-size: 12px;
  color: var(--text-tertiary);
}

.offer-time {
  color: var(--accent-lime);
}

.buy-btn {
  width: 100%;
  border-radius: 0;
  font-family: var(--font-heading);
  font-weight: 700;
  letter-spacing: 0.05em;
  height: 40px;
}

@media (max-width: 768px) {
  .hero-title { font-size: 28px; }
  .page-hero { padding: 40px 20px 30px; }
  .offer-grid { grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
}

@media (max-width: 480px) {
  .offer-grid { grid-template-columns: 1fr; }
}
</style>
