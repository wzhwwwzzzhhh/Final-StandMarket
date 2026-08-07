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
          <el-button type="primary" size="small" @click="openAddAddressDialog">
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
              v-model="areaValue"
              :options="areaOptions"
              placeholder="请选择省市区"
              style="width: 100%"
              @change="handleAreaChange"
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
import addressApiFull from '../api/address'
import regionData from '../utils/regionData'

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
  id: null,
  consignee: '',
  phone: '',
  provinceCode: '',
  provinceName: '',
  cityCode: '',
  cityName: '',
  districtCode: '',
  districtName: '',
  detail: '',
  isDefault: false
})
const areaValue = ref([])

// 地区选项（真实省市区数据）
const areaOptions = regionData

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
      if (totalAmount.value <= 0) {
        return
      }

      const calculateData = {
        totalAmount: totalAmount.value,
        activityId: selectedActivity.value && selectedActivity.value > 0 ? selectedActivity.value : null,
        couponId: selectedCoupon.value && selectedCoupon.value > 0 ? selectedCoupon.value : null
      }

      const response = await seckillApi.calculateOrderAmount(calculateData)

      if (response.data.code === 1 && response.data.data) {
        calculatedAmount.value = response.data.data
        // 不显示提示，避免频繁打扰
      } else {
        ElMessage.warning(response.data.msg || '计算失败')
      }
    } catch (error) {
      console.error('计算金额失败:', error)
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
      // 获取商品信息列表
      if (orderData.selectedItems && orderData.selectedItems.length > 0) {
        selectedItems.value = orderData.selectedItems
        productIds = orderData.selectedItems.map(item => item.id)
      } else if (orderData.cartItemIds && orderData.cartItemIds.length > 0) {
        productIds = orderData.cartItemIds
      }
    }
    
    if (productIds.length === 0) {
      ElMessage.warning('请选择要结算的商品')
      router.back()
      return
    }

    // 并行调用多个接口查询数据
    const [activityRes, couponRes] = await Promise.all([
      seckillApi.getActivityList(),
      seckillApi.getUserCoupons(2) // 获取可用的秒杀券（status=2待使用）
    ])

    // 加载地址数据
    await loadAddressList()

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
const openAddAddressDialog = () => {
  addressForm.value = {
    id: null,
    consignee: '',
    phone: '',
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    districtCode: '',
    districtName: '',
    detail: '',
    isDefault: false
  }
  areaValue.value = []
  showAddressDialog.value = true
}

const editAddress = (address) => {
  addressForm.value = { ...address, isDefault: !!address.isDefault }
  areaValue.value = [address.provinceCode, address.cityCode, address.districtCode].filter(Boolean)
  showAddressDialog.value = true
}

const handleAreaChange = (value) => {
  if (value && value.length === 3) {
    const province = regionData.find(item => item.value === value[0])
    const city = province ? province.children.find(item => item.value === value[1]) : null
    const district = city ? city.children.find(item => item.value === value[2]) : null
    addressForm.value.provinceCode = value[0]
    addressForm.value.provinceName = province ? province.label : ''
    addressForm.value.cityCode = value[1]
    addressForm.value.cityName = city ? city.label : ''
    addressForm.value.districtCode = value[2]
    addressForm.value.districtName = district ? district.label : ''
  } else {
    addressForm.value.provinceCode = ''
    addressForm.value.provinceName = ''
    addressForm.value.cityCode = ''
    addressForm.value.cityName = ''
    addressForm.value.districtCode = ''
    addressForm.value.districtName = ''
  }
}

const loadAddressList = async () => {
  try {
    const res = await addressApi.getList()
    if (res.data.code === 1 && res.data.data) {
      addressList.value = res.data.data
      const defaultAddr = addressList.value.find(addr => addr.isDefault === 1)
      if (defaultAddr) {
        selectedAddress.value = defaultAddr.id
      } else if (addressList.value.length > 0) {
        selectedAddress.value = addressList.value[0].id
      }
    }
  } catch (e) {
    console.error('加载地址列表失败:', e)
  }
}

const deleteAddress = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个收货地址吗？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await addressApiFull.deleteAddress(id)
    if (res.data.code === 1) {
      ElMessage.success('删除成功')
      await loadAddressList()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const saveAddress = async () => {
  const payload = {
    ...addressForm.value,
    isDefault: addressForm.value.isDefault ? 1 : 0
  }
  try {
    const res = addressForm.value.id
      ? await addressApiFull.updateAddress(payload)
      : await addressApiFull.addAddress(payload)
    if (res.data.code === 1) {
      ElMessage.success('保存成功')
      showAddressDialog.value = false
      await loadAddressList()
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存地址失败')
  }
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

    // 构建订单数据（金额由服务端重算，前端不再传递，防止篡改）
    const orderData = {
      productIds: productIds,
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
          cartApi.batchDeleteCartItems(orderData.cartItemIds).catch(error => {
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
/* ============================================================
   CREATE ORDER — 机能风订单创建页
   ============================================================ */

.create-order {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 var(--space-lg);
  padding-top: var(--space-2xl);
  padding-bottom: 120px;
  min-height: 100vh;
  animation: floatIn 0.5s ease;
}

.order-content {
  background: transparent;
}

/* ---- 页面标题 ---- */
.header {
  display: flex;
  align-items: center;
  margin-bottom: var(--space-xl);
  padding-bottom: var(--space-lg);
  border-bottom: 2px solid var(--border-subtle);
  gap: var(--space-md);
}

.header :deep(.el-button) {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.04em;
  transition: var(--transition-base);
}

.header :deep(.el-button:hover) {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.page-title {
  font-family: var(--font-heading);
  font-size: 26px;
  font-weight: 900;
  color: var(--text-primary);
  letter-spacing: 0.06em;
  margin: 0;
}

.loading-container {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  padding: 60px 40px;
}

/* ---- 区块 ---- */
.section {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  margin-bottom: var(--space-lg);
  overflow: hidden;
}

.section h3 {
  font-family: var(--font-heading);
  font-size: 14px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: 0.05em;
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-elevated);
}

.scroll-box {
  max-height: 250px;
  overflow-y: auto;
  padding: 16px 20px;
}

.scroll-box::-webkit-scrollbar {
  width: 4px;
}

.scroll-box::-webkit-scrollbar-thumb {
  background: var(--accent-purple-dim);
}

.scroll-box::-webkit-scrollbar-track {
  background: var(--bg-surface);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-subtle);
}

.section-header h3 {
  padding: 0;
  border-bottom: none;
  background: none;
}

.section-header :deep(.el-button) {
  background: var(--bg-card);
  border: 1px solid var(--border-card);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.04em;
  transition: var(--transition-base);
}

.section-header :deep(.el-button:hover) {
  border-color: var(--accent-purple);
  color: var(--accent-purple);
}

.section-tip {
  color: var(--text-muted);
  font-size: 12px;
  font-family: var(--font-mono);
  letter-spacing: 0.02em;
}

/* ---- 商品列表 ---- */
.product-list {
  padding: 0;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  transition: background 0.2s;
}

.product-item:last-child {
  border-bottom: none;
}

.product-item:hover {
  background: rgba(209, 0, 255, 0.03);
}

.product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  margin-right: 16px;
  flex-shrink: 0;
  filter: grayscale(30%);
  transition: all 0.3s ease;
}

.product-image:hover {
  filter: grayscale(0%);
  transform: scale(1.06);
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0.03em;
}

.product-sku {
  color: var(--text-muted);
  font-size: 11px;
  font-family: var(--font-mono);
  margin-bottom: 6px;
}

.product-price {
  display: flex;
  align-items: center;
  gap: 12px;
}

.unit-price {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 800;
  color: var(--accent-lime);
}

.quantity {
  color: var(--text-muted);
  font-size: 12px;
  font-family: var(--font-mono);
}

.product-subtotal {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 18px;
  color: var(--accent-lime);
  margin-left: 24px;
  flex-shrink: 0;
  animation: priceFlicker 3s infinite;
}

/* ---- 卡片通用（活动/秒杀券） ---- */
.activity-grid,
.coupon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  padding: 16px 20px;
}

.activity-card,
.coupon-card {
  border: 1px solid var(--border-card);
  padding: 16px;
  cursor: pointer;
  transition: all 0.25s ease;
  background: var(--bg-surface);
  position: relative;
  overflow: hidden;
}

.activity-card::before,
.coupon-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 3px;
  height: 100%;
  background: transparent;
  transition: background 0.25s ease;
}

.activity-card:hover,
.coupon-card:hover {
  border-color: var(--accent-purple-dim);
  background: var(--bg-elevated);
}

.activity-card:hover::before,
.coupon-card:hover::before {
  background: var(--accent-purple);
}

.activity-card.selected,
.coupon-card.selected {
  border-color: var(--accent-purple);
  background: rgba(209, 0, 255, 0.06);
  box-shadow: 0 0 20px var(--accent-purple-dim);
}

.activity-card.selected::before,
.coupon-card.selected::before {
  background: var(--accent-purple);
}

.activity-card.disabled {
  opacity: 0.35;
  cursor: not-allowed;
  filter: grayscale(60%);
}

.activity-card.disabled:hover {
  border-color: var(--border-card);
  background: var(--bg-surface);
}

.activity-card.disabled:hover::before {
  background: transparent;
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
}

.card-header :deep(.el-tag) {
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.card-header :deep(.el-tag--success) {
  background: transparent;
  border: 1px solid var(--accent-lime);
  color: var(--accent-lime);
}

.card-header :deep(.el-tag--warning) {
  background: transparent;
  border: 1px solid var(--accent-orange);
  color: var(--accent-orange);
}

.card-name {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
  letter-spacing: 0.03em;
}

.card-desc {
  color: var(--text-muted);
  font-size: 12px;
  font-family: var(--font-mono);
}

.card-discount {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 18px;
  color: var(--accent-lime);
  animation: priceFlicker 3s infinite;
}

.card-time {
  color: var(--text-muted);
  font-size: 11px;
  font-family: var(--font-mono);
  line-height: 1.4;
}

.card-price-info {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 4px 0;
}

.card-seckill-price {
  font-family: var(--font-display);
  font-weight: 800;
  font-size: 22px;
  color: var(--accent-lime);
  animation: priceFlicker 3s infinite;
}

.card-original-price {
  color: var(--text-muted);
  text-decoration: line-through;
  font-size: 13px;
  font-family: var(--font-mono);
}

/* ---- 收货地址 ---- */
.address-list {
  padding: 0;
}

.address-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  transition: background 0.2s;
}

.address-item:last-child {
  border-bottom: none;
}

.address-item:hover {
  background: rgba(209, 0, 255, 0.03);
}

.address-radio-group {
  flex: 1;
}

.address-item :deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  color: var(--text-primary);
}

.address-item :deep(.el-radio__inner) {
  border-color: var(--border-card);
  background: var(--bg-surface);
}

.address-item :deep(.el-radio__input.is-checked .el-radio__inner) {
  background: var(--accent-purple);
  border-color: var(--accent-purple);
}

.address-info {
  margin-left: 8px;
}

.address-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.consignee {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
  letter-spacing: 0.03em;
}

.phone {
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: 13px;
}

.address-header :deep(.el-tag) {
  border-radius: 0;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: transparent;
  border: 1px solid var(--accent-lime);
  color: var(--accent-lime);
}

.address-detail {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.address-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 4px;
}

.address-actions :deep(.el-button) {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  transition: color 0.2s;
}

.address-actions :deep(.el-button:hover) {
  color: var(--accent-purple);
}

.address-actions :deep(.el-button--danger:hover) {
  color: var(--accent-red);
}

/* ---- 配送状态 / 支付方式 ---- */
.delivery-options,
.payment-methods {
  display: flex;
  gap: 24px;
  padding: 16px 20px;
}

.delivery-options :deep(.el-radio),
.payment-methods :deep(.el-radio) {
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.delivery-options :deep(.el-radio__inner),
.payment-methods :deep(.el-radio__inner) {
  border-color: var(--border-card);
  background: var(--bg-surface);
}

.delivery-options :deep(.el-radio__input.is-checked .el-radio__inner),
.payment-methods :deep(.el-radio__input.is-checked .el-radio__inner) {
  background: var(--accent-purple);
  border-color: var(--accent-purple);
}

.delivery-time {
  padding: 0 20px 16px;
}

.delivery-time :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
}

.delivery-time :deep(.el-date-editor) {
  --el-input-bg-color: var(--bg-surface);
  --el-input-border-color: var(--border-card);
  --el-input-text-color: var(--text-primary);
}

/* ---- 订单金额汇总 ---- */
.order-summary {
  padding: 20px;
  background: var(--bg-surface);
  border-top: 1px solid var(--border-subtle);
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 13px;
}

.summary-item .label {
  color: var(--text-secondary);
  font-family: var(--font-mono);
  letter-spacing: 0.02em;
}

.summary-item .value {
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--text-primary);
}

.summary-item .discount {
  color: var(--accent-lime);
  font-family: var(--font-display);
  font-weight: 800;
}

.summary-item.total {
  padding-top: 16px;
  margin-top: 12px;
  border-top: 2px solid var(--border-subtle);
  font-size: 16px;
}

.summary-item.total .label {
  font-family: var(--font-heading);
  font-weight: 800;
  font-size: 15px;
  color: var(--text-primary);
  letter-spacing: 0.04em;
}

.summary-item.total .value {
  color: var(--accent-lime);
  font-size: 26px;
  font-weight: 900;
  letter-spacing: 0.02em;
  animation: priceFlicker 3s infinite;
}

/* ---- 提交按钮 ---- */
.submit-section {
  text-align: center;
  margin-top: var(--space-xl);
}

.submit-button {
  width: 260px;
  height: 52px;
  background: var(--accent-purple) !important;
  border: none !important;
  color: #fff !important;
  font-family: var(--font-heading) !important;
  font-weight: 900 !important;
  font-size: 16px !important;
  letter-spacing: 0.08em !important;
  position: relative;
  overflow: hidden;
  transition: all 0.25s ease;
}

.submit-button::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
  transform: translateX(-100%);
  transition: transform 0.5s;
}

.submit-button:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 0 24px var(--accent-purple-dim);
}

.submit-button:not(:disabled):hover::after {
  transform: translateX(100%);
}

.submit-button:not(:disabled):active {
  transform: translateY(0);
}

.submit-button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
  filter: grayscale(50%);
}

/* ---- 空状态 ---- */
.no-coupon,
.no-address {
  padding: 40px 0;
  text-align: center;
}

.no-coupon :deep(.el-empty__description),
.no-address :deep(.el-empty__description) {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

/* ---- 对话框 ---- */
.address-dialog {
  padding: 0;
}

.address-dialog :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.03em;
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .create-order {
    padding: 0 var(--space-md);
    padding-top: var(--space-lg);
    padding-bottom: 100px;
  }

  .page-title {
    font-size: 20px;
  }

  .product-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }

  .product-subtotal {
    margin-left: 0;
    align-self: flex-end;
  }

  .address-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
  }

  .address-actions {
    align-self: flex-end;
    width: 100%;
    justify-content: flex-end;
  }

  .activity-grid,
  .coupon-grid {
    grid-template-columns: 1fr;
  }

  .submit-button {
    width: 100%;
  }

  .delivery-options,
  .payment-methods {
    flex-direction: column;
    gap: 12px;
  }
}
</style>