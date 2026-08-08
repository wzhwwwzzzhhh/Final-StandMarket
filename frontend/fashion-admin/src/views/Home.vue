<template>
  <div class="home">
    <div class="dashboard-header">
      <h2 class="dashboard-title">服装店铺管理系统</h2>
      <div class="dashboard-actions">
        <el-button type="primary" @click="refreshData" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新数据
        </el-button>
      </div>
    </div>

    <div class="dashboard">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">商品总数</span>
                <el-icon class="card-icon"><Goods /></el-icon>
              </div>
            </template>
            <div class="card-content">
              <el-statistic :value="productCount" title="商品数量" :precision="0" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">订单总数</span>
                <el-icon class="card-icon"><ShoppingCart /></el-icon>
              </div>
            </template>
            <div class="card-content">
              <el-statistic :value="orderCount" title="订单数量" :precision="0" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">用户总数</span>
                <el-icon class="card-icon"><User /></el-icon>
              </div>
            </template>
            <div class="card-content">
              <el-statistic :value="userCount" title="用户数量" :precision="0" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">总销售额</span>
                <el-icon class="card-icon"><Money /></el-icon>
              </div>
            </template>
            <div class="card-content">
              <el-statistic :value="totalSales" title="销售额" prefix="¥" :precision="2" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div class="charts-section">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card shadow="hover" class="chart-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">销售趋势</span>
                <el-select v-model="timeRange" size="small" class="time-select" @change="loadTrend">
                  <el-option label="近7天" value="7" />
                  <el-option label="近30天" value="30" />
                  <el-option label="近90天" value="90" />
                </el-select>
              </div>
            </template>
            <div class="chart-container">
              <div ref="salesChart" class="chart"></div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="hover" class="chart-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">商品分类占比</span>
              </div>
            </template>
            <div class="chart-container">
              <div ref="categoryChart" class="chart"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <div class="recent-orders">
      <el-card shadow="hover" class="orders-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">最近订单</span>
            <el-button type="text" size="small" @click="viewAllOrders">
              查看全部
            </el-button>
          </div>
        </template>
        <div class="orders-table">
          <el-table :data="recentOrders" stripe style="width: 100%" v-loading="loading">
            <el-table-column prop="number" label="订单号" width="200" />
            <el-table-column prop="userName" label="客户" />
            <el-table-column prop="amount" label="金额" width="100">
              <template #default="scope">
                ¥{{ scope.row.amount ? scope.row.amount.toFixed(2) : '0.00' }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="orderTime" label="下单时间" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="scope">
                <el-button type="primary" size="small" @click="viewOrderDetail(scope.row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script>
import { Refresh, Goods, ShoppingCart, User, Money } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { dashboardApi } from '../api/dashboard'

export default {
  name: 'Home',
  components: {
    Refresh,
    Goods,
    ShoppingCart,
    User,
    Money
  },
  data() {
    return {
      productCount: 0,
      orderCount: 0,
      userCount: 0,
      totalSales: 0,
      timeRange: '7',
      loading: false,
      salesChart: null,
      categoryChart: null,
      recentOrders: []
    }
  },
  mounted() {
    this.loadData()
  },
  beforeUnmount() {
    if (this.salesChart) {
      this.salesChart.dispose()
    }
    if (this.categoryChart) {
      this.categoryChart.dispose()
    }
  },
  methods: {
    loadData() {
      this.loading = true
      Promise.all([
        dashboardApi.getSales(),
        dashboardApi.getRecentOrders({ limit: 5 }),
        dashboardApi.getCategory()
      ]).then(([salesRes, ordersRes, categoryRes]) => {
        const sales = salesRes.data.data
        this.productCount = sales.totalProducts || 0
        this.orderCount = sales.totalOrders || 0
        this.userCount = sales.totalUsers || 0
        this.totalSales = sales.totalSales || 0

        this.recentOrders = ordersRes.data.data || []

        this.loadTrend()

        this.$nextTick(() => {
          this.initSalesChart([])
          this.initCategoryChart(categoryRes.data.data || [])
        })
      }).catch(err => {
        console.error('加载数据失败:', err)
        this.$message.error('加载数据失败')
      }).finally(() => {
        this.loading = false
      })
    },

    loadTrend() {
      dashboardApi.getTrend(this.timeRange).then(res => {
        const data = res.data.data || []
        this.initSalesChart(data)
      }).catch(err => {
        console.error('加载趋势数据失败:', err)
      })
    },

    initSalesChart(trendData) {
      if (!this.$refs.salesChart) return
      if (!this.salesChart) {
        this.salesChart = echarts.init(this.$refs.salesChart)
        window.addEventListener('resize', () => {
          this.salesChart && this.salesChart.resize()
        })
      }

      const dates = trendData.map(d => d.date)
      const amounts = trendData.map(d => d.amount)
      const counts = trendData.map(d => d.count)

      const option = {
        tooltip: { trigger: 'axis' },
        legend: { data: ['销售额', '订单数'], top: 0 },
        grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
        xAxis: [{ type: 'category', boundaryGap: false, data: dates.length ? dates : ['-'] }],
        yAxis: [
          { type: 'value', name: '销售额', position: 'left', axisLabel: { formatter: '¥{value}' } },
          { type: 'value', name: '订单数', position: 'right', axisLabel: { formatter: '{value}单' } }
        ],
        series: [
          {
            name: '销售额', type: 'line', smooth: true,
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(52, 152, 219, 0.5)' },
                { offset: 1, color: 'rgba(52, 152, 219, 0.1)' }
              ])
            },
            data: amounts.length ? amounts : [0]
          },
          {
            name: '订单数', type: 'line', smooth: true, yAxisIndex: 1,
            data: counts.length ? counts : [0]
          }
        ]
      }
      this.salesChart.setOption(option, true)
    },

    initCategoryChart(categoryData) {
      if (!this.$refs.categoryChart) return
      if (!this.categoryChart) {
        this.categoryChart = echarts.init(this.$refs.categoryChart)
        window.addEventListener('resize', () => {
          this.categoryChart && this.categoryChart.resize()
        })
      }

      const chartData = categoryData.length ? categoryData.map(d => ({
        value: d.total_sales || d.productCount || 0,
        name: d.name || '未分类'
      })) : [{ value: 1, name: '暂无数据' }]

      const option = {
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'left', top: 'center' },
        series: [{
          name: '商品分类', type: 'pie', radius: '60%', center: ['60%', '50%'],
          data: chartData,
          emphasis: {
            itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
          },
          itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 }
        }]
      }
      this.categoryChart.setOption(option, true)
    },

    refreshData() {
      this.loadData()
      this.$message.success('数据已刷新')
    },

    viewAllOrders() {
      this.$router.push('/order/list')
    },

    viewOrderDetail(orderId) {
      this.$router.push(`/order/list`)
    },

    getStatusType(status) {
      switch (status) {
        case 1: return 'info'
        case 2: return 'warning'
        case 3: return 'primary'
        case 4: return 'success'
        case 5: return 'danger'
        default: return 'default'
      }
    },

    getStatusText(status) {
      switch (status) {
        case 1: return '待付款'
        case 2: return '待发货'
        case 3: return '已发货'
        case 4: return '已完成'
        case 5: return '已取消'
        default: return '未知'
      }
    }
  }
}
</script>

<style scoped>
.home {
  padding: 20px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.dashboard-title {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stats-card { border-radius: 12px; margin-bottom: 20px; }
.stats-card .card-header { display: flex; justify-content: space-between; align-items: center; }
.stats-card .card-title { font-size: 16px; font-weight: bold; color: #333; }
.stats-card .card-icon { font-size: 24px; color: #409eff; }
.card-content { text-align: center; padding: 10px 0; }

.charts-section { margin-bottom: 30px; }
.chart-card { border-radius: 12px; }
.chart-card .card-header { display: flex; justify-content: space-between; align-items: center; }
.chart-card .card-title { font-size: 16px; font-weight: bold; color: #333; }
.chart-container { padding: 10px; }
.chart { width: 100%; height: 350px; }

.orders-card { border-radius: 12px; }
.orders-card .card-header { display: flex; justify-content: space-between; align-items: center; }
.orders-card .card-title { font-size: 16px; font-weight: bold; color: #333; }

.time-select { width: 120px; }
</style>
