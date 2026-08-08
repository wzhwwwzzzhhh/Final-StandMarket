<template>
  <div class="favorite-list">
    <div class="header">
      <el-button type="primary" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2 class="page-title">我的收藏</h2>
      <span class="count">{{ favorites.length }}</span>
    </div>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="favorites.length === 0" class="empty-state">
      <el-empty description="还没有收藏商品，快去逛逛吧！">
        <el-button type="primary" @click="$router.push('/')">去购物</el-button>
      </el-empty>
    </div>

    <div v-else class="product-grid">
      <div v-for="item in productList" :key="item.id" class="product-card" @click="$router.push(`/product/detail/${item.id}`)">
        <div class="card-img-wrap">
          <img :src="item.image" :alt="item.name" class="card-img" />
          <div class="card-actions">
            <el-button type="danger" size="small" circle @click.stop="removeFavorite(item.id)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="card-info">
          <h3 class="card-name">{{ item.name }}</h3>
          <p class="card-desc">{{ item.description }}</p>
          <span class="card-price">¥{{ item.price }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Delete } from '@element-plus/icons-vue'
import favoriteApi from '@/api/favorite'
import { productApi } from '@/api/product'

export default {
  name: 'FavoriteList',
  components: { ArrowLeft, Delete },
  data() {
    return {
      loading: true,
      favorites: [],
      productList: []
    }
  },
  created() {
    this.loadFavorites()
  },
  methods: {
    goBack() {
      this.$router.back()
    },
    loadFavorites() {
      this.loading = true
      favoriteApi.list().then(response => {
        if (response.data.code === 1) {
          this.favorites = response.data.data || []
          return this.loadProductDetails()
        } else {
          this.$message.error(response.data.msg || '获取收藏列表失败')
          this.loading = false
        }
      }).catch(() => {
        this.loading = false
        this.$message.error('网络错误')
      })
    },
    async loadProductDetails() {
      // 同时并发请求所有商品详情，用 allSettled 避免单个失败拖垮全部
      const promises = this.favorites.map(fav =>
        productApi.getProductById(fav.productId)
          .then(res => res.data.data)
          .catch(() => null)
      )
      const results = await Promise.allSettled(promises)
      this.productList = results.map(r => r.status === 'fulfilled' ? r.value : null).filter(Boolean)
      this.loading = false
    },
    removeFavorite(productId) {
      favoriteApi.remove(productId).then(response => {
        if (response.data.code === 1) {
          this.$message.success('已取消收藏')
          this.loadFavorites()
        } else {
          this.$message.error(response.data.msg || '操作失败')
        }
      }).catch(() => {
        this.$message.error('网络错误')
      })
    }
  }
}
</script>

<style scoped>
.favorite-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.page-title {
  flex: 1;
  font-size: 22px;
  font-weight: 600;
}

.count {
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 12px;
  padding: 2px 12px;
  font-size: 14px;
}

.loading-container {
  padding: 40px 0;
}

.empty-state {
  padding: 80px 0;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px;
}

.product-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.product-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}

.card-img-wrap {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
}

.card-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.product-card:hover .card-actions {
  opacity: 1;
}

.card-info {
  padding: 12px;
}

.card-name {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #999;
  margin: 0 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-danger);
}
</style>
