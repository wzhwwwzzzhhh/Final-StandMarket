<template>
  <div class="shopping-cart">
    <!-- 页面标题 -->
    <div class="header">
      <el-button type="primary" class="back-button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <h2 class="page-title">购物车</h2>
      <div class="cart-count">
        <span class="count">{{ cartItems.length }}</span>
      </div>
    </div>
    
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    
    <!-- 购物车为空 -->
    <div v-else-if="cartItems.length === 0" class="empty-cart">
      <el-empty description="购物车还是空的，快去挑选心仪的商品吧！">
        <el-button type="primary" class="shop-button" @click="$router.push('/')">
            <el-icon><Top /></el-icon> 去购物
          </el-button>
      </el-empty>
    </div>
    
    <!-- 购物车列表 -->
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

    <!-- 底部结算区域 -->
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
    
    <!-- 推荐商品 -->
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
        // 为每个商品添加checked属性
        newItems.forEach(item => {
          if (item.checked === undefined) {
            item.checked = false
          }
        })
        // 检查是否需要更新全选状态
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
      // 简单的优惠计算逻辑：满100减10
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
      // 检查是否所有商品都被选中
      const allSelected = this.cartItems.every(item => item.checked)
      this.selectAll = allSelected
    },
    // 从后端获取购物车数据
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
      // 确保tableRef已初始化
      if (this.$refs.tableRef) {
        // 先清除所有选择
        this.$refs.tableRef.clearSelection()
        // 根据val值决定是否全选
        if (val) {
          // 全选所有商品
          this.cartItems.forEach(item => {
            this.$refs.tableRef.toggleRowSelection(item, true)
          })
        }
      }
    },
    handleSelectionChange(selection) {
      // 保存选中的商品
      this.selectedItems = selection
      // 清空所有商品的checked属性
      this.cartItems.forEach(item => {
        item.checked = false
      })
      // 为选中的商品设置checked属性为true
      selection.forEach(item => {
        const cartItem = this.cartItems.find(cartItem => cartItem.id === item.id)
        if (cartItem) {
          cartItem.checked = true
        }
      })
      // 更新全选状态
      this.updateSelectAllStatus()
    },
    // 批量删除
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
        // 获取选中的商品ID列表
        const selectedIds = this.selectedItems.map(item => item.id)
        
        // 调用后端批量删除API
        cartApi.batchDeleteCartItems(selectedIds).then(response => {
          if (response.data.code === 1) {
            // 从购物车列表中移除删除的商品
            this.cartItems = this.cartItems.filter(item => !selectedIds.includes(item.id))
            // 清空选中状态
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
    // 清空购物车
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
        // 获取所有商品ID
        const allIds = this.cartItems.map(item => item.id)
        
        // 调用后端批量删除API
        cartApi.batchDeleteCartItems(allIds).then(response => {
          if (response.data.code === 1) {
            // 清空购物车
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
    // 添加推荐商品到购物车
    addRecommendedProduct(product) {
      // 这里可以根据实际情况调用添加到购物车的API
      this.$message.success(`已将 ${product.name} 添加到购物车`)
    },
    checkout() {
      // 获取选中的商品ID列表
      const selectedIds = this.selectedItems.map(item => item.id)
      
      if (selectedIds.length === 0) {
        this.$message.warning('请选择要结算的商品')
        return
      }
      
      // 构建订单数据
      const orderData = {
        selectedItems: this.selectedItems,
        cartItemIds: selectedIds
      }
      
      try {
        // 保存订单数据到sessionStorage，以便订单生成页面使用
        sessionStorage.setItem('orderData', JSON.stringify(orderData))
        
        // 跳转到订单生成页面
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
/* 全局动画 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes slideIn {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

/* 主容器 */
.shopping-cart {
  padding: 30px;
  max-width: 1200px;
  margin: 0 auto;
  animation: fadeIn 0.8s ease-out;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  min-height: 100vh;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

/* 页面标题 */
.header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e9ecef;
  animation: slideIn 0.5s ease-out;
}

.back-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  padding: 10px 20px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.back-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: bold;
  color: #333;
  flex: 1;
}

.cart-count {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a45 100%);
  color: #fff;
  border-radius: 50%;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.3);
  animation: pulse 2s infinite;
}

/* 加载状态 */
.loading-container {
  background: #fff;
  padding: 40px;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  margin-bottom: 30px;
}

/* 购物车为空 */
.empty-cart {
  background: #fff;
  padding: 60px 40px;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  text-align: center;
  margin: 40px 0;
  animation: fadeIn 0.8s ease-out;
}

.shop-button {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  border: none;
  border-radius: 12px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
  margin-top: 20px;
}

.shop-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(103, 194, 58, 0.4);
}

/* 购物车列表 */
.cart-list {
  margin-bottom: 30px;
  animation: fadeIn 0.8s ease-out 0.2s both;
}

.cart-table {
  background: #fff;
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
}

.cart-table:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
}

.cart-table th {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  padding: 16px;
  text-align: center;
}

.cart-table td {
  padding: 20px;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s ease;
}

.cart-table tr:hover td {
  background: #f8f9fa;
}

/* 商品信息 */
.product-info {
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s ease;
}

.product-info:hover {
  transform: translateX(10px);
}

.product-image {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.product-image:hover {
  transform: scale(1.1);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.product-details {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  transition: color 0.3s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-name:hover {
  color: #ff4d4f;
}

.product-sku {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-stock {
  margin-top: 8px;
}

/* 单价 */
.unit-price {
  font-size: 14px;
  font-weight: 600;
  color: #666;
}

/* 数量控制 */
.quantity-control {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: center;
}

.quantity-btn {
  width: 36px;
  height: 36px;
  padding: 0;
  border-radius: 8px;
  font-size: 18px;
  font-weight: bold;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.quantity-btn.minus {
  background: #f0f2f5;
  border: 2px solid #e3e6f0;
  color: #666;
}

.quantity-btn.plus {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  color: #fff;
}

.quantity-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.quantity {
  width: 50px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  padding: 6px 0;
  border-radius: 8px;
  background: #f8f9fa;
  border: 2px solid #e3e6f0;
}

/* 小计 */
.subtotal {
  font-size: 16px;
  font-weight: bold;
  color: #ff4d4f;
}

/* 删除按钮 */
.remove-button {
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a45 100%);
  border: none;
  color: #fff;
}

.remove-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 77, 79, 0.4);
}

/* 底部结算区域 */
.cart-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 30px;
  padding: 24px;
  border-top: 1px solid #e9ecef;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  animation: fadeIn 0.8s ease-out 0.4s both;
}

.cart-footer:hover {
  box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
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
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.select-all-text {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.batch-actions {
  display: flex;
  gap: 12px;
}

.batch-delete-button,
.clear-button {
  border-radius: 8px;
  padding: 6px 16px;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 4px;
}

.batch-delete-button {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7a45 100%);
  border: none;
  color: #fff;
}

.clear-button {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  border: none;
  color: #fff;
}

.batch-delete-button:hover,
.clear-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.total-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.discount-section {
  margin-right: 12px;
}

.discount-tag {
  border-radius: 12px;
  padding: 4px 16px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 4px;
  animation: pulse 2s infinite;
}

.total-price {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.total-label {
  font-size: 16px;
  color: #666;
}

.price {
  color: #ff4d4f;
  font-size: 28px;
  font-weight: bold;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
}

.checkout-button {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  border: none;
  border-radius: 12px;
  padding: 12px 32px;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
  display: flex;
  align-items: center;
  gap: 8px;
}

.checkout-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(103, 194, 58, 0.4);
}

.checkout-button:disabled {
  background: #c0c4cc;
  box-shadow: none;
  cursor: not-allowed;
}

.checkout-button:disabled:hover {
  transform: none;
  box-shadow: none;
}

/* 推荐商品 */
.recommended-products {
  margin-top: 50px;
  animation: fadeIn 0.8s ease-out 0.6s both;
}

.recommended-title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 2px solid #667eea;
}

.recommended-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 24px;
}

.recommended-item {
  background: #fff;
  padding: 20px;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}

.recommended-item:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.recommended-image {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: 12px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.recommended-image:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.15);
}

.recommended-info {
  flex: 1;
  width: 100%;
}

.recommended-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommended-price {
  font-size: 16px;
  font-weight: bold;
  color: #ff4d4f;
  margin-bottom: 12px;
}

.add-button {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 8px;
  padding: 6px 16px;
  font-size: 12px;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  display: flex;
  align-items: center;
  gap: 4px;
}

.add-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .shopping-cart {
    padding: 20px;
  }
  
  .recommended-list {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 20px;
  }
}

@media (max-width: 768px) {
  .shopping-cart {
    padding: 15px;
  }
  
  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .page-title {
    font-size: 24px;
  }
  
  .product-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  
  .product-image {
    width: 80px;
    height: 80px;
  }
  
  .cart-footer {
    flex-direction: column;
    align-items: flex-start;
    gap: 20px;
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
    justify-content: space-between;
  }
  
  .total-section {
    width: 100%;
    flex-direction: column;
    align-items: flex-end;
    gap: 12px;
  }
  
  .recommended-list {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 16px;
  }
  
  .recommended-item {
    padding: 16px;
  }
  
  .recommended-image {
    width: 100px;
    height: 100px;
  }
}

@media (max-width: 480px) {
  .shopping-cart {
    padding: 10px;
  }
  
  .page-title {
    font-size: 20px;
  }
  
  .product-image {
    width: 60px;
    height: 60px;
  }
  
  .product-name {
    font-size: 14px;
  }
  
  .quantity-control {
    gap: 8px;
  }
  
  .quantity-btn {
    width: 32px;
    height: 32px;
  }
  
  .quantity {
    width: 40px;
    font-size: 14px;
  }
  
  .total-price {
    font-size: 16px;
  }
  
  .price {
    font-size: 24px;
  }
  
  .checkout-button {
    padding: 10px 24px;
    font-size: 14px;
  }
  
  .recommended-list {
    grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
    gap: 12px;
  }
  
  .recommended-item {
    padding: 12px;
  }
  
  .recommended-image {
    width: 80px;
    height: 80px;
  }
  
  .recommended-name {
    font-size: 12px;
  }
  
  .recommended-price {
    font-size: 14px;
  }
}
</style>
