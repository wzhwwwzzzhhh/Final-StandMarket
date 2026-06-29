<template>
  <div class="order">
    <div class="header">
      <h2>我的订单</h2>
      <div class="header-actions">
        <el-button type="success" @click="goToCoupons">优惠券订单</el-button>
      </div>
    </div>

    <div class="order-tabs">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部订单" name="all"></el-tab-pane>
        <el-tab-pane label="待付款" name="1"></el-tab-pane>
        <el-tab-pane label="待发货" name="2"></el-tab-pane>
        <el-tab-pane label="待收货" name="3"></el-tab-pane>
        <el-tab-pane label="已完成" name="4"></el-tab-pane>
        <el-tab-pane label="已取消" name="5"></el-tab-pane>
      </el-tabs>
    </div>

    <div class="order-list" v-loading="loading">
      <div v-if="orders.length > 0">
        <div v-for="order in orders" :key="order.id" class="order-item">
          <div class="order-header">
            <div class="order-info">
              <span class="order-number">订单号：{{ order.number }}</span>
              <span class="order-time">{{ formatTime(order.orderTime) }}</span>
              <span class="delivery-status" v-if="order.deliveryStatus">
                配送：{{ order.deliveryStatus === 1 ? '立即送出' : '指定时间' }}
              </span>
            </div>
            <div class="order-status" :class="'status-' + order.status">
              {{ getStatusText(order.status) }}
            </div>
          </div>

          <div class="order-body">
            <div v-if="order.items && order.items.length > 0" class="product-list">
              <div v-for="item in order.items" :key="item.id" class="product-item">
                <img v-if="item.image" :src="item.image" :alt="item.name" class="product-image" />
                <div class="product-info">
                  <div class="product-name">{{ item.name }}</div>
                  <div class="product-sku" v-if="item.skuInfo">规格：{{ item.skuInfo }}</div>
                  <div class="product-price">¥{{ (item.amount / item.number).toFixed(2) }} x {{ item.number }}</div>
                </div>
              </div>
            </div>
            <div v-else class="empty-products">
              暂无商品信息
            </div>
          </div>

          <div class="order-footer">
            <div class="order-amount">
              <span class="label">实付金额：</span>
              <span class="amount">¥{{ order.amount }}</span>
            </div>
            <div class="order-actions">
              <el-button v-if="order.status === 1" type="primary" size="small" @click="handlePay(order.id)">
                立即支付
              </el-button>
              <el-button v-if="order.status === 1" size="small" @click="handleCancel(order.id)">
                取消订单
              </el-button>
              <el-button v-if="order.status === 3" type="primary" size="small" @click="handleConfirm(order.id)">
                确认收货
              </el-button>
              <el-button size="small" @click="handleViewDetail(order.id)">
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty-state">
        <el-empty description="暂无订单"></el-empty>
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="订单详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="orderDetail" class="order-detail">
        <div class="detail-section">
          <h3>订单信息</h3>
          <div class="detail-row">
            <span class="label">订单号：</span>
            <span>{{ orderDetail.number }}</span>
          </div>
          <div class="detail-row">
            <span class="label">订单状态：</span>
            <span :class="'status-' + orderDetail.status">{{ getStatusText(orderDetail.status) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">下单时间：</span>
            <span>{{ formatTime(orderDetail.orderTime) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">支付方式：</span>
            <span>{{ orderDetail.payMethod === 1 ? '微信支付' : '支付宝' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">支付状态：</span>
            <span>{{ orderDetail.payStatus === 1 ? '已支付' : '未支付' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">订单金额：</span>
            <span class="amount">¥{{ orderDetail.amount }}</span>
          </div>
          <div class="detail-row">
            <span class="label">配送状态：</span>
            <span>{{ orderDetail.deliveryStatus === 1 ? '立即送出' : '指定时间配送' }}</span>
          </div>
          <div v-if="orderDetail.estimatedDeliveryTime" class="detail-row">
            <span class="label">预计配送时间：</span>
            <span>{{ formatTime(orderDetail.estimatedDeliveryTime) }}</span>
          </div>
        </div>

        <div class="detail-section">
          <h3>收货信息</h3>
          <div class="detail-row">
            <span class="label">收货人：</span>
            <span>{{ orderDetail.consignee }}</span>
          </div>
          <div class="detail-row">
            <span class="label">联系电话：</span>
            <span>{{ orderDetail.phone }}</span>
          </div>
          <div class="detail-row">
            <span class="label">收货地址：</span>
            <span>{{ orderDetail.address }}</span>
          </div>
        </div>

        <div class="detail-section">
          <h3>商品信息</h3>
          <div v-if="orderDetail.items && orderDetail.items.length > 0" class="product-list">
            <div v-for="item in orderDetail.items" :key="item.id" class="product-item">
              <img v-if="item.image" :src="item.image" :alt="item.name" class="product-image" />
              <div class="product-info">
                <div class="product-name">{{ item.name }}</div>
                <div class="product-sku" v-if="item.skuInfo">规格：{{ item.skuInfo }}</div>
                <div class="product-price">¥{{ (item.amount / item.number).toFixed(2) }} x {{ item.number }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-products">
            暂无商品信息
          </div>
        </div>
      </div>
      <div v-else class="loading">
        加载中...
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { orderApi, cartApi } from '@/api/product'

export default {
  name: 'Order',
  data() {
    return {
      activeTab: 'all',
      orders: [],
      orderDetail: null,
      detailVisible: false,
      loading: false
    }
  },
  mounted() {
    // 检查localStorage中是否有订单数据
    this.checkOrderData()
    this.loadOrders()
  },
  methods: {
    goToCoupons() {
      this.$router.push('/my-coupons')
    },
    checkOrderData() {
      try {
        const orderData = localStorage.getItem('orderData')
        if (orderData) {
          console.log('从localStorage获取订单数据:', orderData)
          const parsedData = JSON.parse(orderData)
          this.createOrder(parsedData)
          // 清除localStorage中的订单数据
          localStorage.removeItem('orderData')
        }
      } catch (error) {
        console.error('解析订单数据失败:', error)
      }
    },
    createOrder(orderData) {
      this.loading = true
      // 直接传递商品ID列表
      const productIds = orderData.productIds
      
      console.log('创建订单数据:', productIds)
      
      orderApi.createOrder(productIds).then(response => {
        if (response.data.code === 1) {
          this.$message.success('订单创建成功')
          // 清空购物车中已结算的商品
          if (orderData.cartItemIds && orderData.cartItemIds.length > 0) {
            cartApi.batchDeleteCartItems(orderData.cartItemIds).then(() => {
              console.log('购物车已清空')
            })
          }
        } else {
          this.$message.error(response.data.msg || '订单创建失败')
        }
      }).catch(error => {
        console.error('创建订单失败:', error)
        this.$message.error('创建订单失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handleTabChange(tabName) {
      this.loadOrders()
    },
    loadOrders() {
      this.loading = true
      const status = this.activeTab === 'all' ? null : parseInt(this.activeTab)
      
      console.log('加载订单列表，状态:', status)
      
      orderApi.getOrderList(status).then(response => {
        console.log('获取订单列表响应:', response.data)
        if (response.data.code === 1) {
          this.orders = response.data.data || []
          console.log('订单列表数据:', this.orders)
        } else {
          this.$message.error(response.data.msg || '获取订单列表失败')
        }
      }).catch(error => {
        console.error('获取订单列表失败:', error)
        this.$message.error('获取订单列表失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handlePay(orderId) {
      this.$confirm('确认支付该订单？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        orderApi.payOrder(orderId).then(response => {
          if (response.data.code === 1) {
            this.$message.success('支付成功')
            this.loadOrders()
          } else {
            this.$message.error(response.data.msg || '支付失败')
          }
        }).catch(error => {
          console.error('支付失败:', error)
          this.$message.error('支付失败')
        }).finally(() => {
          this.loading = false
        })
      }).catch(() => {})
    },
    handleCancel(orderId) {
      this.$confirm('确认取消该订单？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        orderApi.cancelOrder(orderId).then(response => {
          if (response.data.code === 1) {
            this.$message.success('订单取消成功')
            this.loadOrders()
          } else {
            this.$message.error(response.data.msg || '取消订单失败')
          }
        }).catch(error => {
          console.error('取消订单失败:', error)
          this.$message.error('取消订单失败')
        }).finally(() => {
          this.loading = false
        })
      }).catch(() => {})
    },
    handleConfirm(orderId) {
      this.$confirm('确认收到商品？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        orderApi.confirmOrder(orderId).then(response => {
          if (response.data.code === 1) {
            this.$message.success('确认收货成功')
            this.loadOrders()
          } else {
            this.$message.error(response.data.msg || '确认收货失败')
          }
        }).catch(error => {
          console.error('确认收货失败:', error)
          this.$message.error('确认收货失败')
        }).finally(() => {
          this.loading = false
        })
      }).catch(() => {})
    },
    handleViewDetail(orderId) {
      this.detailVisible = true
      this.orderDetail = null
      
      orderApi.getOrderDetail(orderId).then(response => {
        if (response.data.code === 1) {
          this.orderDetail = response.data.data
        } else {
          this.$message.error(response.data.msg || '获取订单详情失败')
          this.detailVisible = false
        }
      }).catch(error => {
        console.error('获取订单详情失败:', error)
        this.$message.error('获取订单详情失败')
        this.detailVisible = false
      })
    },
    getStatusText(status) {
      const statusMap = {
        1: '待付款',
        2: '待发货',
        3: '待收货',
        4: '已完成',
        5: '已取消'
      }
      return statusMap[status] || '未知状态'
    },
    formatTime(time) {
      if (!time) return ''
      const date = new Date(time)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      const seconds = String(date.getSeconds()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    }
  }
}
</script>

<style scoped>
/* ============================================================
   ORDER — 霓虹订单列表
   ============================================================ */

.order {
  max-width: 1200px;
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

.header-actions .el-button {
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  font-family: var(--font-display) !important;
  font-size: 12px !important;
  font-weight: 700 !important;
  letter-spacing: 0.04em !important;
}

/* === 订单标签页 === */
.order-tabs {
  margin-bottom: var(--space-xl);
}

.order-tabs :deep(.el-tabs__item) {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
  height: auto;
  line-height: 1;
  padding: 0 20px 12px;
  transition: var(--transition-fast);
}

.order-tabs :deep(.el-tabs__item:hover) {
  color: var(--text-secondary);
}

.order-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent-purple);
}

.order-tabs :deep(.el-tabs__active-bar) {
  background: var(--accent-purple);
  height: 2px;
}

.order-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--border-subtle);
  height: 1px;
}

/* === 订单列表 === */
.order-list {
  min-height: 400px;
}

.order-item {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  margin-bottom: 4px;
  transition: var(--transition-base);
}

.order-item:hover {
  border-color: var(--accent-purple);
}

/* === 订单头部 === */
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-card);
  background: var(--bg-surface);
}

.order-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.order-number {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 700;
  letter-spacing: 0.04em;
}

.order-time {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.delivery-status {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.order-status {
  font-family: var(--font-display);
  font-size: 12px;
  font-weight: 700;
  padding: 6px 14px;
  letter-spacing: 0.04em;
}

.status-1 {
  color: var(--accent-orange);
  border: 1px solid var(--accent-orange);
}

.status-2 {
  color: var(--accent-purple);
  border: 1px solid var(--accent-purple);
}

.status-3 {
  color: var(--accent-lime);
  border: 1px solid var(--accent-lime);
}

.status-4 {
  color: #1e90ff;
  border: 1px solid #1e90ff;
}

.status-5 {
  color: var(--text-tertiary);
  border: 1px solid var(--border-subtle);
}

/* === 订单商品 === */
.order-body {
  padding: 20px;
  border-bottom: 1px solid var(--border-card);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 12px;
  background: var(--bg-card);
  gap: 12px;
}

.product-image {
  width: 72px;
  height: 72px;
  object-fit: cover;
  flex-shrink: 0;
  transition: var(--transition-slow);
}

.product-image:hover {
  transform: scale(1.08);
  filter: grayscale(60%);
}

.product-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.product-name {
  font-family: var(--font-heading);
  font-size: 14px;
  color: var(--text-primary);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}

.product-sku {
  font-family: var(--font-display);
  font-size: 11px;
  color: var(--text-tertiary);
}

.product-price {
  font-family: var(--font-display);
  font-size: 13px;
  color: var(--accent-lime);
  font-weight: 700;
}

.empty-products {
  text-align: center;
  padding: 40px 0;
  color: var(--text-tertiary);
  font-family: var(--font-display);
  font-size: 13px;
}

/* === 订单底部 === */
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--bg-surface);
}

.order-amount {
  font-size: 14px;
  color: var(--text-primary);
  font-family: var(--font-display);
}

.order-amount .label {
  color: var(--text-tertiary);
  margin-right: 8px;
}

.order-amount .amount {
  font-size: 22px;
  color: var(--accent-lime);
  font-weight: 800;
  font-family: var(--font-display);
}

.order-actions {
  display: flex;
  gap: 8px;
}

.order-actions .el-button {
  font-family: var(--font-display) !important;
  font-size: 11px !important;
  font-weight: 700 !important;
  letter-spacing: 0.04em !important;
  padding: 6px 14px !important;
  border: 1px solid var(--border-subtle) !important;
  background: transparent !important;
  color: var(--text-secondary) !important;
  transition: var(--transition-fast);
}

.order-actions .el-button:hover {
  border-color: var(--accent-purple) !important;
  color: var(--accent-purple) !important;
}

.order-actions .el-button.el-button--primary {
  background: var(--accent-purple) !important;
  border-color: var(--accent-purple) !important;
  color: #fff !important;
}

.empty-state {
  background: var(--bg-elevated);
  border: 1px solid var(--border-card);
  padding: 80px 0;
}

.order-detail {
  padding: 20px 0;
}

.detail-section {
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border-card);
}

.detail-section:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.detail-section h3 {
  margin: 0 0 14px 0;
  font-family: var(--font-heading);
  font-size: 15px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: 0.04em;
}

.detail-row {
  display: flex;
  margin-bottom: 8px;
  font-size: 13px;
  font-family: var(--font-display);
}

.detail-row .label {
  color: var(--text-tertiary);
  min-width: 80px;
}

.detail-row .amount {
  color: var(--accent-lime);
  font-weight: 700;
}

/* === 响应式 === */
@media (max-width: 768px) {
  .order {
    padding: 0 var(--space-md);
  }

  .order-footer {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .order-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>