<template>
  <div class="browse-history">
    <div class="page-header">
      <el-button type="text" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </el-button>
      <div class="header-title">
        <span class="kicker">// BROWSE HISTORY</span>
        <h2 class="page-title">最近浏览</h2>
      </div>
      <div class="header-actions">
        <span class="count">{{ products.length }}</span>
        <el-button type="text" class="clear-button" :disabled="products.length === 0" @click="clearHistory">
          <el-icon><Delete /></el-icon>
          <span>清空</span>
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="products.length === 0" class="empty-state">
      <el-empty description="还没有浏览记录，去逛逛心仪的商品吧！">
        <el-button type="primary" class="shop-button" @click="$router.push('/')">去逛逛</el-button>
      </el-empty>
    </div>

    <div v-else class="product-grid">
      <div
        v-for="(product, index) in products"
        :key="product.id"
        class="product-card"
        role="button"
        tabindex="0"
        @click="$router.push(`/product/detail/${product.id}`)"
        @keydown.enter="$router.push(`/product/detail/${product.id}`)"
      >
        <div class="card-index">{{ String(index + 1).padStart(2, '0') }}</div>
        <div class="card-img-wrap">
          <img :src="product.image" :alt="product.name" class="card-img" />
        </div>
        <div class="card-info">
          <h3 class="card-name">{{ product.name }}</h3>
          <p class="card-sales">销量 {{ product.sales || 0 }}</p>
          <span class="card-price">¥{{ product.price }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Delete } from '@element-plus/icons-vue'
import browseApi from '@/api/browse'

export default {
  name: 'BrowseHistory',
  components: { ArrowLeft, Delete },
  data() {
    return {
      loading: true,
      products: []
    }
  },
  created() {
    this.loadHistory()
  },
  methods: {
    goBack() {
      this.$router.back()
    },
    loadHistory() {
      this.loading = true
      browseApi.list().then(response => {
        if (response.data.code === 1) {
          this.products = response.data.data || []
        } else {
          this.$message.error(response.data.msg || '获取浏览历史失败')
        }
        this.loading = false
      }).catch(() => {
        this.loading = false
        this.$message.error('网络错误')
      })
    },
    clearHistory() {
      this.$confirm('确定清空全部浏览历史吗？', '提示', {
        confirmButtonText: '清空',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        browseApi.clear().then(response => {
          if (response.data.code === 1) {
            this.products = []
            this.$message.success('已清空')
          } else {
            this.$message.error(response.data.msg || '清空失败')
          }
        }).catch(() => {
          this.$message.error('网络错误')
        })
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.browse-history {
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 var(--space-lg) var(--space-xl);
  min-height: 100vh;
  color: var(--text-primary);
  font-family: var(--font-body);
}

.page-header {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  padding: var(--space-lg) 0;
  border-bottom: 1px solid var(--border-card);
  margin-bottom: var(--space-lg);
}

.back-button {
  color: var(--text-secondary);
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  gap: 4px;
}

.back-button:hover {
  color: var(--accent-lime);
}

.header-title {
  flex: 1;
  display: grid;
  gap: 4px;
}

.kicker {
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.13em;
  color: var(--accent-lime);
}

.page-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 900;
  letter-spacing: 0.04em;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.count {
  background: var(--accent-purple);
  color: #fff;
  border-radius: var(--radius-none);
  padding: 2px 12px;
  font-family: var(--font-display);
  font-size: 14px;
  box-shadow: 0 0 14px rgba(209, 0, 255, 0.3);
}

.clear-button {
  color: var(--text-secondary);
  font-family: var(--font-heading);
  display: flex;
  align-items: center;
  gap: 4px;
}

.clear-button:hover:not(:disabled) {
  color: var(--accent-red);
}

.loading-container {
  padding: 40px 0;
}

.empty-state {
  padding: 80px 0;
}

.shop-button {
  border-radius: var(--radius-none);
  background: var(--accent-purple);
  border-color: var(--accent-purple);
  font-family: var(--font-heading);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--space-md);
}

.product-card {
  position: relative;
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  overflow: hidden;
  cursor: pointer;
  outline: none;
  transition: transform var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base);
}

.product-card:hover,
.product-card:focus-visible {
  border-color: rgba(204, 255, 0, 0.64);
  transform: translateY(-4px);
  box-shadow: 0 12px 26px rgba(0, 0, 0, 0.25);
}

.card-index {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 2;
  background: rgba(0, 0, 0, 0.65);
  color: var(--accent-lime);
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border: 1px solid var(--border-subtle);
}

.card-img-wrap {
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: var(--bg-elevated);
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--transition-base);
}

.product-card:hover .card-img {
  transform: scale(1.06);
}

.card-info {
  padding: var(--space-md);
  display: grid;
  gap: 6px;
}

.card-name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-sales {
  margin: 0;
  font-size: 12px;
  color: var(--text-tertiary);
}

.card-price {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent-lime);
  font-family: var(--font-display);
}

@media (max-width: 768px) {
  .browse-history {
    padding: 0 var(--space-md) var(--space-xl);
  }

  .page-header {
    gap: var(--space-md);
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-sm);
  }
}

@media (max-width: 480px) {
  .header-actions {
    gap: var(--space-sm);
  }

  .clear-button span {
    display: none;
  }
}
</style>
