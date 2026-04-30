<template>
  <div class="create-order">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>

    <!-- 订单内容 -->
    <div v-else class="order-content">
      <!-- 页面标题 -->
      <div class="header">
        <el-button type="primary" size="small" @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回购物车
        </el-button>
        <h2 class="page-title">生成订单</h2>
      </div>

      <!-- 商品信息 -->
      <div class="section">
        <div class="section-header">
          <h3>商品信息</h3>
        </div>
        <div class="product-list">
          <div v-for="item in selectedItems" :key="item.id" class="product-item">
            <img :src="item.image" :alt="item.name" class="product-image" />
            <div class="product-info">
              <div class="product-name">{{ item.name }}</div>
              <div class="product-sku">{{ item.skuInfo }}</div>
              <div class="product-price">
                <span class="unit-price">¥{{ (item.amount / item.number).toFixed(2) }}</span>
                <span class="quantity">× {{ item.number }}</span>
              </div>
            </div>
            <div class="product-subtotal">¥{{ item.amount.toFixed(2) }}</div>
          </div>
        </div>
      </div>

      <!-- 秒杀活动选择 -->
      <div class="section">
        <div class="section-header">
          <h3>秒杀活动</h3>
          <span class="section-tip">选择参与秒杀活动享受优惠</span>
        </div>
        <div class="scroll-box activity-grid">
          <div 
            :class="['activity-card', { 'selected': selectedActivity === 0, 'disabled': false }]"
            @click="selectedActivity = 0; calculateAmount()"
          >
            <div class="card-content">
              <span class="card-name">不参与秒杀活动</span>
              <span class="card-desc">原价购买</span>
            </div>
          </div>
          <div 
            v-for="activity in availableActivities" 
            :key="activity.id"
            :class="['activity-card', { 
              'selected': selectedActivity === activity.id, 
              'disabled': !isActivityValid(activity) 
            }]"
            @click="!isActivityValid(activity) || (selectedActivity = activity.id, calculateAmount())"
          >
            <div class="card-content">
              <div class="card-header">
                <span class="card-name">{{ activity.name }}</span>
                <el-tag v-if="!isActivityValid(activity)" type="warning" size="small">未开始</el-tag>
                <el-tag v-else type="success" size="small">进行中</el-tag>
              </div>
              <span class="card-discount">{{ activity.discount ? activity.discount + '折优惠' : '折扣优惠' }}</span>
              <span class="card-time">{{ formatTime(activity.startTime) }} - {{ formatTime(activity.endTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 秒杀券选择 -->
      <div class="section">
        <div class="section-header">
          <h3>秒杀券</h3>
          <span class="section-tip">选择可用的秒杀券</span>
        </div>
        <div class="scroll-box coupon-grid">
          <div v-if="availableCoupons.length === 0" class="no-coupon">
            <el-empty description="暂无可用秒杀券" :image-size="60" />
          </div>
          <template v-else>
            <div 
              :class="['coupon-card', { 'selected': selectedCoupon === 0 }]"
              @click="selectedCoupon = 0; calculateAmount()"
            >
              <div class="card-content">
                <span class="card-name">不使用秒杀券</span>
                <span class="card-desc">无额外优惠</span>
              </div>
            </div>
            <div 
              v-for="coupon in availableCoupons" 
              :key="coupon.id"
              :class="['coupon-card', { 'selected': selectedCoupon === coupon.couponId }]"
              @click="selectedCoupon = coupon.couponId; calculateAmount()"
            >
              <div class="card-content">
                <div class="card-header">
                  <span class="card-name">{{ coupon.name }}</span>
                </div>
                <div class="card-price-info">
                  <span class="card-seckill-price">¥{{ coupon.seckillPrice }}</span>
                  <span class="card-original-price">¥{{ coupon.originalPrice }}</span>
                </div>
                <span class="card-time">有效期：{{ formatTime(coupon.startTime) }} - {{ formatTime(coupon.endTime) }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 收货地址选择 -->
      <div class="section">
        <div class="section-header">
          <h3>收货地址</h3>
          <el-button type="primary" size="small" @click="showAddressDialog = true">
            <el-icon><Plus /></el-icon> 添加地址
          </el-button>
        </div>
        <div class="address-list">
          <div v-if="addressList.length === 0" class="no-address">
            <el-empty description="暂无收货地址" />
          </div>
          <div v-for="address in addressList" :key="address.id" class="address-item">
            <el-radio-group v-model="selectedAddress" class="address-radio-group">
              <el-radio :value="address.id" class="address-radio">
                <div class="address-info">
                  <div class="address-header">
                    <span class="consignee">{{ address.consignee }}</span>
                    <span class="phone">{{ address.phone }}</span>
                    <el-tag v-if="address.isDefault" type="success" size="small">默认</el-tag>
                  </div>
                  <div class="address-detail">{{ address.provinceName }} {{ address.cityName }} {{ address.districtName }} {{ address.detail }}</div>
                </div>
              </el-radio>
            </el-radio-group>
            <div class="address-actions">
              <el-button link @click="editAddress(address)">编辑</el-button>
              <el-button link type="danger" @click="deleteAddress(address.id)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 配送状态选择 -->
      <div class="section">
        <h3>配送状态</h3>
        <div class="delivery-status">
          <el-radio-group v-model="deliveryStatus" class="delivery-options">
            <el-radio value="1">立即送出</el-radio>
            <el-radio value="0">选择具体时间</el-radio>
          </el-radio-group>
          <div v-if="deliveryStatus === '0'" class="delivery-time">
            <el-form-item label="配送时间">
              <el-date-picker
                v-model="estimatedDeliveryTime"
                type="datetime"
                placeholder="请选择配送时间"
                style="width: 100%"
                :disabled="deliveryStatus !== '0'"
              />
            </el-form-item>
          </div>
        </div>
      </div>

      <!-- 支付方式选择 -->
      <div class="section">
        <h3>支付方式</h3>
        <el-radio-group v-model="selectedPaymentMethod" class="payment-methods">
          <el-radio value="1">微信支付</el-radio>
          <el-radio value="2">支付宝</el-radio>
        </el-radio-group>
      </div>

      <!-- 订单金额 -->
      <div class="section">
        <div class="order-summary">
          <div class="summary-item">
            <span class="label">商品金额：</span>
            <span class="value">¥{{ totalAmount.toFixed(2) }}</span>
          </div>
          <div v-if="activityDiscount > 0" class="summary-item">
            <span class="label">活动优惠：</span>
            <span class="value discount">-¥{{ activityDiscount.toFixed(2) }}</span>
          </div>
          <div v-if="couponDiscount > 0" class="summary-item">
            <span class="label">券优惠：</span>
            <span class="value discount">-¥{{ couponDiscount.toFixed(2) }}</span>
          </div>
          <div class="summary-item total">
            <span class="label">实付金额：</span>
            <span class="value">¥{{ finalAmount.toFixed(2) }}</span>
          </div>
        </div>
      </div>

      <!-- 提交订单 -->
      <div class="submit-section">
        <el-button 
          type="primary" 
          size="large" 
          class="submit-button"
          @click="handleSubmit"
          :disabled="!isFormValid"
        >
          提交订单
        </el-button>
      </div>
    </div>

    <!-- 地址管理对话框 -->
    <el-dialog v-model="showAddressDialog" title="管理收货地址" width="600px">
      <div class="address-dialog">
        <el-form :model="addressForm" label-width="80px">
          <el-form-item label="收货人">
            <el-input v-model="addressForm.consignee" placeholder="请输入收货人姓名" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="addressForm.phone" placeholder="请输入手机号码" />
          </el-form-item>
          <el-form-item label="地区">
            <el-cascader 
              v-model="addressForm.area" 
              :options="areaOptions" 
              placeholder="请选择省市区" 
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="详细地址">
            <el-input v-model="addressForm.detail" placeholder="请输入详细地址" />
          </el-form-item>
          <el-form-item label="设为默认">
            <el-switch v-model="addressForm.isDefault" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="showAddressDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAddress">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { orderApi, addressApi, seckillApi, cartApi } from '../api/product'

const router = useRouter()
const route = useRoute()

// 响应式数据
const loading = ref(false)
const selectedItems = ref([])

// 秒杀活动相关
const availableActivities = ref([])
const selectedActivity = ref(0)

// 秒杀券相关
const availableCoupons = ref([])
const selectedCoupon = ref(0)

// 金额计算结果（从后端获取）
const calculatedAmount = ref({
  totalAmount: 0,
  activityDiscount: 0,
  couponDiscount: 0,
  finalAmount: 0,
  activityName: '',
  activityDiscountText: '',
  couponName: '',
  couponDiscountText: ''
})

// 收货地址相关
const addressList = ref([])
const selectedAddress = ref(null)
const selectedPaymentMethod = ref(1) // 1微信支付
const deliveryStatus = ref('1') // 1立即送出，0选择具体时间
const estimatedDeliveryTime = ref(null) // 预计配送时间
const showAddressDialog = ref(false)
const addressForm = ref({
  consignee: '',
  phone: '',
  area: [],
  detail: '',
  isDefault: false
})

// 地区选项（模拟数据）
const areaOptions = ref([
  {
    value: 'beijing',
    label: '北京市',
    children: [
      { value: 'chaoyang', label: '朝阳区' },
      { value: 'haidian', label: '海淀区' }
    ]
  }
])

// 计算属性
const totalAmount = computed(() => {
  return selectedItems.value.reduce((sum, item) => sum + item.amount, 0)
})

// 使用后端计算的结果
const activityDiscount = computed(() => calculatedAmount.value.activityDiscount)
const couponDiscount = computed(() => calculatedAmount.value.couponDiscount)
const finalAmount = computed(() => calculatedAmount.value.finalAmount || totalAmount.value)

const isFormValid = computed(() => {
  return selectedItems.value.length > 0 && selectedAddress.value
})

// 方法
const goBack = () => {
  router.back()
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

const isActivityValid = (activity) => {
  const now = new Date()
  const startTime = new Date(activity.startTime)
  const endTime = new Date(activity.endTime)
  return now >= startTime && now <= endTime
}

// 防抖计时器
let calculateTimer = null

// 调用后端计算订单金额
const calculateAmount = () => {
  // 清除之前的定时器
  if (calculateTimer) {
    clearTimeout(calculateTimer)
  }
  
  // 设置防抖，300ms后执行
  calculateTimer = setTimeout(async () => {
    try {
      console.log('开始计算金额...', {
        totalAmount: totalAmount.value,
        activityId: selectedActivity.value,
        couponId: selectedCoupon.value
      })
      
      if (totalAmount.value <= 0) {
        console.warn('商品总金额无效:', totalAmount.value)
        return
      }
      
      const calculateData = {
        totalAmount: totalAmount.value,
        activityId: selectedActivity.value && selectedActivity.value > 0 ? selectedActivity.value : null,
        couponId: selectedCoupon.value && selectedCoupon.value > 0 ? selectedCoupon.value : null
      }
      
      console.log('发送计算请求:', calculateData)
      
      const response = await seckillApi.calculateOrderAmount(calculateData)
      
      console.log('收到响应:', response.data)
      
      if (response.data.code === 1 && response.data.data) {
        calculatedAmount.value = response.data.data
        console.log('✅ 金额计算成功:', calculatedAmount.value)
        // 不显示提示，避免频繁打扰
      } else {
        console.warn('计算返回异常:', response.data.msg)
        ElMessage.warning(response.data.msg || '计算失败')
      }
    } catch (error) {
      console.error('❌ 计算金额失败:', error)
      ElMessage.error('计算金额失败，请重试')
      
      // 如果接口失败，使用默认值（无优惠）
      calculatedAmount.value = {
        totalAmount: totalAmount.value,
        activityDiscount: 0,
        couponDiscount: 0,
        finalAmount: totalAmount.value,
        activityName: '',
        activityDiscountText: '',
        couponName: '',
        couponDiscountText: ''
      }
    }
  }, 300) // 300ms防抖
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    // 从sessionStorage获取订单数据
    const orderDataStr = sessionStorage.getItem('orderData')
    let productIds = []
    
    if (orderDataStr) {
      const orderData = JSON.parse(orderDataStr)
      console.log('sessionStorage订单数据:', orderData)
      // 获取商品信息列表
      if (orderData.selectedItems && orderData.selectedItems.length > 0) {
        selectedItems.value = orderData.selectedItems
        productIds = orderData.selectedItems.map(item => item.id)
        console.log('商品信息:', selectedItems.value)
      } else if (orderData.cartItemIds && orderData.cartItemIds.length > 0) {
        productIds = orderData.cartItemIds
        console.log('购物车商品ID:', productIds)
      }
    }
    
    if (productIds.length === 0) {
      ElMessage.warning('请选择要结算的商品')
      router.back()
      return
    }

    // 并行调用多个接口查询数据
    const [addressRes, activityRes, couponRes] = await Promise.all([
      addressApi.getList(),
      seckillApi.getActivityList(),
      seckillApi.getUserCoupons(2) // 获取可用的秒杀券（status=2待使用）
    ])
    
    // 处理地址数据
    if (addressRes.data.code === 1 && addressRes.data.data) {
      addressList.value = addressRes.data.data
      // 设置默认地址
      const defaultAddr = addressList.value.find(addr => addr.isDefault === 1)
      if (defaultAddr) {
        selectedAddress.value = defaultAddr.id
      } else if (addressList.value.length > 0) {
        selectedAddress.value = addressList.value[0].id
      }
    }
    
    // 处理秒杀活动数据
    if (activityRes.data.code === 1 && activityRes.data.data) {
      availableActivities.value = activityRes.data.data
    }
    
    // 处理秒杀券数据
    if (couponRes.data.code === 1 && couponRes.data.data) {
      availableCoupons.value = couponRes.data.data
    }
    
    // 初始计算金额（不等待防抖）
    calculateAmount()

  } catch (error) {
    console.error('加载数据失败:', error)
    console.error('地址响应:', error.response?.data)
    ElMessage.error('加载数据失败: ' + (error.response?.data?.msg || error.message))
  } finally {
    loading.value = false
  }
}

// 地址管理
const editAddress = (address) => {
  addressForm.value = { ...address }
  showAddressDialog.value = true
}

const deleteAddress = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个收货地址吗？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    // 这里调用删除地址接口
    addressList.value = addressList.value.filter(addr => addr.id !== id)
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const saveAddress = async () => {
  // 这里调用保存地址接口
  ElMessage.success('保存成功')
  showAddressDialog.value = false
}

// 提交订单
const handleSubmit = async () => {
  if (!selectedAddress.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  
  try {
    await ElMessageBox.confirm('确定要提交订单吗？', '订单确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    // 获取商品ID列表
    const orderDataStr = sessionStorage.getItem('orderData')
    let productIds = []
    if (orderDataStr) {
      const orderData = JSON.parse(orderDataStr)
      if (orderData.selectedItems && orderData.selectedItems.length > 0) {
        productIds = orderData.selectedItems.map(item => item.id)
      } else if (orderData.cartItemIds && orderData.cartItemIds.length > 0) {
        productIds = orderData.cartItemIds
      }
    }

    // 构建订单数据
    const orderData = {
      productIds: productIds,
      amount: finalAmount.value, // 传递前端计算好的总金额（包含优惠后的金额）
      addressId: selectedAddress.value,
      payMethod: selectedPaymentMethod.value,
      activityId: selectedActivity.value || null,
      couponId: selectedCoupon.value || null,
      deliveryStatus: deliveryStatus.value,
      estimatedDeliveryTime: estimatedDeliveryTime.value
    }

    // 调用创建订单接口
    const response = await orderApi.createOrder(orderData)
    
    if (response.data.code === 1) {
      const createdOrder = response.data.data
      ElMessage.success('订单创建成功')
      
      // 清空购物车中已结算的商品
      const orderDataStr = sessionStorage.getItem('orderData')
      if (orderDataStr) {
        const orderData = JSON.parse(orderDataStr)
        if (orderData.cartItemIds && orderData.cartItemIds.length > 0) {
          cartApi.batchDeleteCartItems(orderData.cartItemIds).then(() => {
            console.log('购物车已清空')
          }).catch(error => {
            console.error('清空购物车失败:', error)
          })
        }
      }
      
      // 清除sessionStorage中的订单数据
      sessionStorage.removeItem('orderData')
      
      // 跳转到订单详情页面
      router.push('/order')
    } else {
      ElMessage.error('创建订单失败: ' + response.data.msg)
    }
    
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交订单失败')
    }
  }
}

// 生命周期
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.create-order {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  background: #f5f5f5;
  min-height: 100vh;
}

.order-content {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.header {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
  gap: 15px;
}

.page-title {
  color: #333;
  margin: 0;
}

.loading-container {
  background: #fff;
  padding: 40px;
  border-radius: 8px;
}

.section {
  background: white;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 20px;
  overflow: hidden;
}

.scroll-box {
  max-height: 250px;
  overflow-y: auto;
  padding: 15px 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background: #f8f9fa;
  border-bottom: 1px solid #e4e7ed;
}

.section-header h3 {
  margin: 0;
  color: #333;
}

.section-tip {
  color: #909399;
  font-size: 13px;
}

.product-list {
  padding: 15px 20px;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.product-item:last-child {
  border-bottom: none;
}

.product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  margin-right: 15px;
  flex-shrink: 0;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-sku {
  color: #909399;
  font-size: 12px;
  margin-bottom: 4px;
}

.product-price {
  display: flex;
  align-items: center;
  gap: 10px;
}

.unit-price {
  color: #f56c6c;
  font-weight: 500;
}

.quantity {
  color: #909399;
  font-size: 13px;
}

.product-subtotal {
  font-weight: 600;
  color: #f56c6c;
  font-size: 16px;
  margin-left: 20px;
  flex-shrink: 0;
}

.activity-list,
.coupon-list {
  width: 100%;
}

.activity-list :deep(.el-radio),
.coupon-list :deep(.el-radio) {
  display: flex;
  align-items: stretch;
  margin-bottom: 10px;
}

.activity-list :deep(.el-radio__label),
.coupon-list :deep(.el-radio__label) {
  flex: 1;
}

.activity-option,
.coupon-option {
  flex: 1;
  padding: 15px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  min-height: 60px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.activity-option:hover,
.coupon-option:hover {
  border-color: #409eff;
}

.activity-info,
.coupon-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.activity-name,
.coupon-name {
  font-weight: 500;
  font-size: 14px;
}

.activity-time,
.coupon-time {
  color: #999;
  font-size: 12px;
  display: block;
}

.activity-discount,
.coupon-price {
  color: #f56c6c;
  font-weight: 600;
}

.original-price {
  color: #999;
  text-decoration: line-through;
  font-size: 12px;
  margin-left: 5px;
}

.activity-time,
.coupon-time {
  color: #999;
  font-size: 12px;
}

.no-coupon,
.no-address {
  padding: 30px 0;
}

.address-list {
  padding: 15px 20px;
}

.address-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.address-item:last-child {
  border-bottom: none;
}

.address-item :deep(.el-radio__label) {
  flex: 1;
}

.address-info {
  margin-left: 8px;
}

.address-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}

.consignee {
  font-weight: 500;
  color: #303133;
}

.phone {
  color: #606266;
}

.address-detail {
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}

.address-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.payment-methods {
  padding: 15px 20px;
  display: flex;
  gap: 20px;
}

.payment-methods .el-radio {
  margin-right: 0;
}

.order-summary {
  padding: 15px 20px;
  background: #f8f9fa;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 14px;
}

.summary-item .label {
  color: #606266;
}

.summary-item .value {
  color: #303133;
  font-weight: 500;
}

.summary-item .discount {
  color: #f56c6c;
}

.summary-item.total {
  padding-top: 12px;
  margin-top: 8px;
  border-top: 1px solid #e4e7ed;
  font-size: 16px;
  font-weight: 600;
}

.summary-item.total .value {
  color: #f56c6c;
  font-size: 20px;
}

.summary-item.total {
  border-top: 1px solid #e4e7ed;
  margin-top: 10px;
  padding-top: 15px;
  font-size: 18px;
  font-weight: bold;
}

.label {
  color: #666;
}

.value {
  color: #f56c6c;
  font-weight: bold;
}

.discount {
  color: #67c23a;
}

.submit-section {
  text-align: center;
  margin-top: 30px;
}

.submit-button {
  width: 200px;
  height: 50px;
  font-size: 16px;
}

.no-coupon,
.no-address {
  padding: 40px 0;
  text-align: center;
}

.address-dialog {
  padding: 0 20px;
}

@media (max-width: 768px) {
  .create-order {
    padding: 10px;
  }
  
  .product-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .address-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .address-actions {
    align-self: flex-end;
  }

  .activity-grid,
  .coupon-grid {
    grid-template-columns: 1fr !important;
  }
}

/* 秒杀活动网格布局 */
.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 15px;
  padding: 20px;
}

.activity-card,
.coupon-card {
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 15px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: #fff;
}

.activity-card:hover,
.coupon-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.activity-card.selected,
.coupon-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.2);
}

.activity-card.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.activity-card.disabled:hover {
  border-color: #e4e7ed;
  box-shadow: none;
  transform: none;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.card-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.card-desc {
  color: #909399;
  font-size: 13px;
}

.card-discount {
  color: #f56c6c;
  font-weight: 600;
  font-size: 16px;
}

.card-time {
  color: #999;
  font-size: 12px;
  line-height: 1.4;
}

/* 秒杀券网格布局 */
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 15px;
  padding: 20px;
}

.card-price-info {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 5px 0;
}

.card-seckill-price {
  color: #f56c6c;
  font-weight: 700;
  font-size: 20px;
}

.card-original-price {
  color: #999;
  text-decoration: line-through;
  font-size: 14px;
}
</style>