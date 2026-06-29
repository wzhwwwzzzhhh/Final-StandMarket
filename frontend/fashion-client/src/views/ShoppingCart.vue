<template>
  <div class="shopping-cart">
    <div class="header">
      <el-button type="primary" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2 class="page-title">购物车</h2>
      <div class="cart-count">
        <span class="count">{{ cartItems.length }}</span>
      </div>
    </div>
    
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    
    <div v-else-if="cartItems.length === 0" class="empty-cart">
      <el-empty description="购物车还是空的，快去挑选心仪的商品吧！">
        <el-button type="primary" class="shop-button" @click="$router.push('/')">
            <el-icon><Top /></el-icon> 去购物
          </el-button>
      </el-empty>
    </div>
    
    <div v-else class="cart-list">
      <el-table ref="tableRef" :data="cartItems" style="width: 100%" @selection-change="handleSelectionChange" row-key="id" class="cart-table">
        <el-table-column type="selection" width="60" :reserve-selection="true" :selectable="row => true"></el-table-column>
        <el-table-column label="商品信息" min-width="350">
          <template #default="scope">
            <div class="product-info">
              <img :src="scope.row.image" :alt="scope.row.name" class="product-image" />
              <div class="product-details">
                <div class="product-name">{{ scope.row.name }}</div>
                <div class="product-sku">{{ scope.row.skuInfo }}</div>
                <div class="product-stock" v-if="scope.row.stock <= 10">
                  <el-tag size="small" type="warning" effect="light">仅剩 {{ scope.row.stock }} 件</el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="scope">
            <span class="unit-price">¥{{ (scope.row.amount / scope.row.number).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="180">
          <template #default="scope">
            <div class="quantity-control">
              <el-button size="small" class="quantity-btn minus" @click="decreaseQuantity(scope.row)" :disabled="scope.row.number <= 1">-</el-button>
              <span class="quantity">{{ scope.row.number }}</span>
              <el-button size="small" class="quantity-btn plus" @click="increaseQuantity(scope.row)">+</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="小计" width="140">
          <template #default="scope">
            <span class="subtotal">¥{{ scope.row.amount.toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button type="danger" size="small" class="remove-button" @click="removeItem(scope.row.id)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="cartItems.length > 0" class="cart-footer">
      <div class="select-all-section">
        <div class="select-all">
          <el-checkbox v-model="selectAll" @change="handleSelectAll" class="select-all-checkbox">
            <span class="select-all-text">全选</span>
          </el-checkbox>
        </div>
        <div class="batch-actions">
          <el-button type="danger" size="small" class="batch-delete-button" @click="batchDelete" :disabled="selectedItems.length === 0">
            <el-icon><Delete /></el-icon> 批量删除
          </el-button>
          <el-button type="info" size="small" class="clear-button" @click="clearCart" :disabled="cartItems.length === 0">
            <el-icon><Close /></el-icon> 清空购物车
          </el-button>
        </div>
      </div>
      <div class="total-section">
        <div class="discount-section" v-if="discount > 0">
          <el-tag type="success" effect="dark" class="discount-tag">
            <el-icon><Present /></el-icon> 优惠：-¥{{ discount }}
          </el-tag>
        </div>
        <div class="total-price">
          <span class="total-label">合计：</span>
          <span class="price">¥{{ (totalPrice - discount).toFixed(2) }}</span>
        </div>
        <el-button type="primary" size="large" class="checkout-button" @click="checkout" :disabled="totalPrice === 0">
          <el-icon><Top /></el-icon> 去结算 ({{ selectedItems.length }})
        </el-button>
      </div>
    </div>
    
    <div v-if="cartItems.length > 0" class="recommended-products">
      <h3 class="recommended-title">
        <el-icon><Star /></el-icon> 为您推荐
      </h3>
      <div class="recommended-list">
        <div class="recommended-item" v-for="(item, index) in recommendedProducts" :key="index">
          <img :src="item.image" :alt="item.name" class="recommended-image" />
          <div class="recommended-info">
            <div class="recommended-name">{{ item.name }}</div>
            <div class="recommended-price">¥{{ item.price }}</div>
          </div>
          <el-button type="primary" size="small" class="add-button" @click="addRecommendedProduct(item)">
            <el-icon><Plus /></el-icon> 加入购物车
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Delete, Close, Present, Top, Star, Plus } from '@element-plus/icons-vue'
import { cartApi } from '@/api/product'

export default {
  name: 'ShoppingCart',
  components: {
    ArrowLeft,
    Delete,
    Close,
    Present,
    Top,
    Star,
    Plus
  },
  data() {
    return {
      selectAll: false,
      cartItems: [],
      loading: false,
      selectedItems: [],
      tableRef: null,
      recommendedProducts: [
        {
          id: 1,
          name: '时尚休闲外套',
          price: 199.99,
          image: 'https://img.alicdn.com/imgextra/i4/2206686535940/O1CN01J9Q9fR1T9kG7I8J3h_!!2206686535940-0-picasso.jpg'
        },
        {
          id: 2,
          name: '潮流牛仔裤',
          price: 129.99,
          image: 'https://img.alicdn.com/imgextra/i3/2206686535940/O1CN01qX4bX81T9kG5v7KvB_!!2206686535940-0-picasso.jpg'
        },
        {
          id: 3,
          name: '舒适运动鞋',
          price: 299.99,
          image: 'https://img.alicdn.com/imgextra/i2/2206686535940/O1CN01L8X1xI1T9kG7uW7jT_!!2206686535940-0-picasso.jpg'
        },
        {
          id: 4,
          name: '时尚背包',
          price: 159.99,
          image: 'https://img.alicdn.com/imgextra/i1/2206686535940/O1CN01vJ7X7e1T9kG6z5w4Q_!!2206686535940-0-picasso.jpg'
        }
      ]
    }
  },
  watch: {
    cartItems: {
      handler(newItems) {
        newItems.forEach(item => {
          if (item.checked === undefined) {
            item.checked = false
          }
        })
        this.updateSelectAllStatus()
      },
      deep: true,
      immediate: true
    }
  },
  computed: {
    totalPrice() {
      return this.cartItems.reduce((total, item) => {
        if (item.checked) {
          return total + Number(item.amount)
        }
        return total
      }, 0)
    },
    discount() {
      if (this.totalPrice >= 100) {
        return Math.floor(this.totalPrice / 100) * 10
      }
      return 0
    }
  },
  mounted() {
    this.getCartList()
  },
  methods: {
    updateSelectAllStatus() {
      if (this.cartItems.length === 0) {
        this.selectAll = false
        return
      }
      const allSelected = this.cartItems.every(item => item.checked)
      this.selectAll = allSelected
    },
    getCartList() {
      this.loading = true
      cartApi.getCartList().then(response => {
        this.loading = false
        if (response.data.code === 1) {
          this.cartItems = response.data.data
        } else {
          this.$message.error(response.data.msg || '获取购物车失败')
        }
      }).catch(error => {
        this.loading = false
        this.$message.error('网络错误，请稍后重试')
        console.error('获取购物车失败:', error)
      })
    },
    goBack() {
      this.$router.back()
    },
    increaseQuantity(item) {
      const updatedItem = {
        id: item.id,
        number: item.number + 1
      }
      cartApi.updateCartItem(updatedItem).then(response => {
        if (response.data.code === 1) {
          const unitPrice = Number(item.amount) / item.number
          item.number++
          item.amount = (item.number * unitPrice).toFixed(2)
        } else {
          this.$message.error(response.data.msg || '更新数量失败')
        }
      }).catch(error => {
        this.$message.error('网络错误，请稍后重试')
        console.error('更新数量失败:', error)
      })
    },
    decreaseQuantity(item) {
      if (item.number > 1) {
        const updatedItem = {
          id: item.id,
          number: item.number - 1
        }
        cartApi.updateCartItem(updatedItem).then(response => {
          if (response.data.code === 1) {
            const unitPrice = Number(item.amount) / item.number
            item.number--
            item.amount = (item.number * unitPrice).toFixed(2)
          } else {
            this.$message.error(response.data.msg || '更新数量失败')
          }
        }).catch(error => {
          this.$message.error('网络错误，请稍后重试')
          console.error('更新数量失败:', error)
        })
      }
    },
    removeItem(id) {
      this.$confirm('确定要从购物车中删除这个商品吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        cartApi.deleteCartItem(id).then(response => {
          if (response.data.code === 1) {
            this.cartItems = this.cartItems.filter(item => item.id !== id)
            this.$message.success('删除成功')
          } else {
            this.$message.error(response.data.msg || '删除失败')
          }
        }).catch(error => {
          this.$message.error('网络错误，请稍后重试')
          console.error('删除失败:', error)
        })
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    handleSelectAll(val) {
      if (this.$refs.tableRef) {
        this.$refs.tableRef.clearSelection()
        if (val) {
          this.cartItems.forEach(item => {
            this.$refs.tableRef.toggleRowSelection(item, true)
          })
        }
      }
    },
    handleSelectionChange(selection) {
      this.selectedItems = selection
      this.cartItems.forEach(item => {
        item.checked = false
      })
      selection.forEach(item => {
        const cartItem = this.cartItems.find(cartItem => cartItem.id === item.id)
        if (cartItem) {
          cartItem.checked = true
        }
      })
      this.updateSelectAllStatus()
    },
    batchDelete() {
      if (this.selectedItems.length === 0) {
        this.$message.warning('请选择要删除的商品')
        return
      }
      
      this.$confirm('确定要批量删除选中的商品吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const selectedIds = this.selectedItems.map(item => item.id)
        
        cartApi.batchDeleteCartItems(selectedIds).then(response => {
          if (response.data.code === 1) {
            this.cartItems = this.cartItems.filter(item => !selectedIds.includes(item.id))
            this.selectedItems = []
            this.selectAll = false
            this.$message.success('批量删除成功')
          } else {
            this.$message.error(response.data.msg || '批量删除失败，请稍后重试')
          }
        }).catch(error => {
          this.$message.error('网络错误，请稍后重试')
          console.error('批量删除失败:', error)
        })
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    clearCart() {
      if (this.cartItems.length === 0) {
        this.$message.info('购物车已经为空')
        return
      }
      
      this.$confirm('确定要清空购物车吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        const allIds = this.cartItems.map(item => item.id)
        
        cartApi.batchDeleteCartItems(allIds).then(response => {
          if (response.data.code === 1) {
            this.cartItems = []
            this.selectedItems = []
            this.selectAll = false
            this.$message.success('购物车已清空')
          } else {
            this.$message.error(response.data.msg || '清空购物车失败，请稍后重试')
          }
        }).catch(error => {
          this.$message.error('网络错误，请稍后重试')
          console.error('清空购物车失败:', error)
        })
      }).catch(() => {
        this.$message.info('已取消操作')
      })
    },
    addRecommendedProduct(product) {
      this.$message.success(`已将 ${product.name} 添加到购物车`)
    },
    checkout() {
      const selectedIds = this.selectedItems.map(item => item.id)
      
      if (selectedIds.length === 0) {
        this.$message.warning('请选择要结算的商品')
        return
      }
      
      const orderData = {
        selectedItems: this.selectedItems,
        cartItemIds: selectedIds
      }
      
      try {
        sessionStorage.setItem('orderData', JSON.stringify(orderData))
        this.$router.push('/create-order')
      } catch (error) {
        console.error('保存订单数据失败:', error)
        this.$message.error('结算失败，请重试')
      }
    }
  }
}
</script>

<style scoped>
/* ============================================================
   SHOPPING CART — 霓虹机能风购物车
   ============================================================ */

.shopping-cart {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

/* === 页面标题 === */
.header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: var(--space-xl);
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--border-subtle);
}

.back-button {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  color: var(--text-secondary);
  padding: 8px 18px;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  transition: var(--transition-base);
}

.back-button:hover {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.page-title {
  margin: 0;
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 24px;
  color: var(--text-primary);
  letter-spacing: 0.06em;
  flex: 1;
}

.cart-count {
  background: var(--accent-purple);
  color: #fff;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
}

/* === 加载和空状态 === */
.loading-container {
  background: var(--bg-elevated);
  padding: 40px;
  border: 1px solid var(--border-card);
}

.empty-cart {
  background: var(--bg-elevated);
  padding: 80px 40px;
  border: 1px solid var(--border-card);
  text-align: center;
  margin: 40px 0;
  animation: floatIn 0.5s ease;
}

.shop-button {
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  padding: 14px 36px !important;
  font-family: var(--font-heading) !important;
  font-weight: 800 !important;
  font-size: 14px !important;
  letter-spacing: 0.06em !important;
  margin-top: 24px;
  transition: var(--transition-base);
}

.shop-button:hover {
  box-shadow: 0 0 24px var(--accent-purple-dim);
}

/* ============================================================
   CART TABLE — 工业风数据表
   ============================================================ */

.cart-list {
  margin-bottom: var(--space-xl);
  animation: floatIn 0.5s ease 0.1s backwards;
}

.cart-table {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
}

.cart-table :deep(th) {
  background: var(--bg-surface) !important;
  color: var(--text-secondary) !important;
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle);
  text-align: center;
}

.cart-table :deep(td) {
  padding: 16px;
  vertical-align: middle;
  border-bottom: 1px solid var(--border-card);
  color: var(--text-primary);
}

.cart-table :deep(tr:hover td) {
  background: rgba(209, 0, 255, 0.04);
}

/* === 商品信息 === */
.product-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  transition: var(--transition-slow);
}

.product-image:hover {
  transform: scale(1.08);
  filter: grayscale(60%);
}

.product-details {
  flex: 1;
  min-width: 0;
}

.product-details .product-name {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: var(--transition-fast);
}

.product-details .product-name:hover {
  color: var(--accent-purple);
}

.product-details .product-sku {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-details .product-stock {
  margin-top: 4px;
}

.unit-price {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-secondary);
}

/* === 数量控制 === */
.quantity-control {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}

.quantity-btn {
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 16px;
  font-weight: 800;
  font-family: var(--font-display);
  transition: var(--transition-fast);
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  color: var(--text-secondary);
  cursor: pointer;
}

.quantity-btn.plus {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.quantity-btn:hover:not(:disabled) {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.quantity-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.quantity {
  width: 44px;
  text-align: center;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  padding: 6px 0;
  background: var(--bg-card);
}

/* === 小计 === */
.subtotal {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 800;
  color: var(--accent-lime);
}

/* === 删除按钮 === */
.remove-button {
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 6px 12px;
  border: 1px solid var(--border-subtle);
  background: transparent !important;
  color: var(--text-tertiary) !important;
  transition: var(--transition-fast);
}

.remove-button:hover {
  border-color: var(--accent-red);
  color: var(--accent-red) !important;
}

/* ============================================================
   BOTTOM BAR — 工业风结算栏
   ============================================================ */

.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
  animation: floatIn 0.5s ease 0.2s backwards;
}

.select-all-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.select-all {
  display: flex;
  align-items: center;
  gap: 8px;
}

.select-all-checkbox {
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.select-all-text {
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
}

.batch-actions {
  display: flex;
  gap: 8px;
}

.batch-delete-button,
.clear-button {
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 6px 14px;
  border: 1px solid var(--border-subtle);
  background: transparent !important;
  color: var(--text-tertiary) !important;
  transition: var(--transition-fast);
}

.batch-delete-button:hover {
  border-color: var(--accent-red);
  color: var(--accent-red) !important;
}

.clear-button:hover {
  border-color: var(--accent-orange);
  color: var(--accent-orange) !important;
}

.total-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.discount-section {
  margin-right: 8px;
}

.discount-tag {
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: var(--accent-lime);
  color: var(--bg-primary);
  border: none;
}

.total-price {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-family: var(--font-display);
}

.total-label {
  font-size: 13px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.price {
  color: var(--accent-lime);
  font-size: 28px;
  font-weight: 800;
  font-family: var(--font-display);
  animation: priceFlicker 3s infinite;
}

.checkout-button {
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  padding: 14px 32px !important;
  font-family: var(--font-heading) !important;
  font-weight: 800 !important;
  font-size: 14px !important;
  letter-spacing: 0.06em !important;
  transition: var(--transition-base);
  position: relative;
  overflow: hidden;
}

.checkout-button::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.checkout-button:hover::after {
  transform: translateX(100%);
}

.checkout-button:hover:not(:disabled) {
  box-shadow: 0 0 24px var(--accent-purple-dim);
}

.checkout-button:disabled {
  background: var(--bg-surface) !important;
  color: var(--text-tertiary) !important;
  cursor: not-allowed;
}

/* ============================================================
   RECOMMENDED — 推荐商品
   ============================================================ */

.recommended-products {
  margin-top: var(--space-xxl);
  animation: floatIn 0.5s ease 0.3s backwards;
}

.recommended-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-heading);
  font-weight: 900;
  font-size: 20px;
  color: var(--text-primary);
  letter-spacing: 0.06em;
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--border-subtle);
}

.recommended-title .el-icon {
  color: var(--accent-purple);
}

.recommended-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}

.recommended-item {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 20px;
  transition: var(--transition-base);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.recommended-item:hover {
  border-color: var(--accent-purple);
  z-index: 2;
}

.recommended-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  transition: var(--transition-slow);
}

.recommended-item:hover .recommended-image {
  transform: scale(1.1);
  filter: grayscale(100%);
}

.recommended-info {
  flex: 1;
  width: 100%;
}

.recommended-name {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommended-price {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 800;
  color: var(--accent-lime);
  margin-bottom: 10px;
}

.add-button {
  background: transparent !important;
  border: 1px solid var(--border-subtle) !important;
  color: var(--text-secondary) !important;
  font-family: var(--font-display) !important;
  font-size: 11px !important;
  font-weight: 700 !important;
  padding: 6px 14px !important;
  letter-spacing: 0.04em !important;
  transition: var(--transition-fast);
}

.add-button:hover {
  border-color: var(--accent-purple) !important;
  color: var(--accent-purple) !important;
}

/* === 响应式设计 === */
@media (max-width: 768px) {
  .shopping-cart {
    padding: 0 var(--space-md);
  }

  .header {
    flex-wrap: wrap;
    gap: 12px;
  }

  .page-title {
    font-size: 20px;
  }

  .cart-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
    padding: 20px;
  }

  .select-all-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    width: 100%;
  }

  .batch-actions {
    width: 100%;
  }

  .total-section {
    width: 100%;
    flex-direction: column;
    align-items: flex-end;
    gap: 12px;
  }

  .recommended-list {
    grid-template-columns: repeat(2, 1fr);
  }

  .recommended-item {
    padding: 16px;
  }
}
</style>