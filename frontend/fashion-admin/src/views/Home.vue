<template>
  <div class="home">
    <div class="dashboard-header">
      <h2 class="dashboard-title">服装店铺管理系统</h2>
      <div class="dashboard-actions">
        <el-button type="primary" @click="refreshData">
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
              <div class="stats-trend positive">
                <el-icon><ArrowUp /></el-icon>
                <span>12.5%</span>
                <span class="trend-desc">较上月</span>
              </div>
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
              <div class="stats-trend positive">
                <el-icon><ArrowUp /></el-icon>
                <span>8.3%</span>
                <span class="trend-desc">较上月</span>
              </div>
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
              <div class="stats-trend positive">
                <el-icon><ArrowUp /></el-icon>
                <span>15.2%</span>
                <span class="trend-desc">较上月</span>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card">
            <template #header>
              <div class="card-header">
                <span class="card-title">今日销售额</span>
                <el-icon class="card-icon"><Money /></el-icon>
              </div>
            </template>
            <div class="card-content">
              <el-statistic :value="todaySales" title="销售额" prefix="¥" :precision="2" />
              <div class="stats-trend negative">
                <el-icon><ArrowDown /></el-icon>
                <span>3.1%</span>
                <span class="trend-desc">较昨日</span>
              </div>
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
                <el-select v-model="timeRange" size="small" class="time-select">
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
                <el-button type="text" size="small" @click="exportChart">
                  <el-icon><Download /></el-icon> 导出
                </el-button>
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
          <el-table :data="recentOrders" stripe style="width: 100%">
            <el-table-column prop="orderId" label="订单号" width="180" />
            <el-table-column prop="customer" label="客户" />
            <el-table-column prop="amount" label="金额" width="100">
              <template #default="scope">
                ¥{{ scope.row.amount.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.status)">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="time" label="下单时间" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="scope">
                <el-button type="primary" size="small" @click="viewOrderDetail(scope.row.orderId)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script>
import { Refresh, Goods, ShoppingCart, User, Money, ArrowUp, ArrowDown, Download } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

export default {
  name: 'Home',
  components: {
    Refresh,
    Goods,
    ShoppingCart,
    User,
    Money,
    ArrowUp,
    ArrowDown,
    Download
  },
  data() {
    return {
      productCount: 120,
      orderCount: 345,
      userCount: 567,
      todaySales: 12345,
      timeRange: '7',
      salesChart: null,
      categoryChart: null,
      recentOrders: [
        { orderId: 'ORD20260413001', customer: '张三', amount: 299.00, status: '已完成', time: '2026-04-13 14:30' },
        { orderId: 'ORD20260413002', customer: '李四', amount: 599.00, status: '处理中', time: '2026-04-13 13:15' },
        { orderId: 'ORD20260413003', customer: '王五', amount: 199.00, status: '已完成', time: '2026-04-13 11:45' },
        { orderId: 'ORD20260413004', customer: '赵六', amount: 899.00, status: '待支付', time: '2026-04-13 10:30' },
        { orderId: 'ORD20260413005', customer: '孙七', amount: 399.00, status: '已完成', time: '2026-04-13 09:15' }
      ]
    }
  },
  mounted() {
    this.initCharts()
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
    initCharts() {
      this.initSalesChart()
      this.initCategoryChart()
    },
    initSalesChart() {
      this.salesChart = echarts.init(this.$refs.salesChart)
      const option = {
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'cross',
            label: {
              backgroundColor: '#6a7985'
            }
          }
        },
        legend: {
          data: ['销售额', '订单数'],
          top: 0
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          containLabel: true
        },
        xAxis: [
          {
            type: 'category',
            boundaryGap: false,
            data: ['4-7', '4-8', '4-9', '4-10', '4-11', '4-12', '4-13']
          }
        ],
        yAxis: [
          {
            type: 'value',
            name: '销售额',
            position: 'left',
            axisLabel: {
              formatter: '¥{value}'
            }
          },
          {
            type: 'value',
            name: '订单数',
            position: 'right',
            axisLabel: {
              formatter: '{value}单'
            }
          }
        ],
        series: [
          {
            name: '销售额',
            type: 'line',
            stack: 'Total',
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                {
                  offset: 0,
                  color: 'rgba(52, 152, 219, 0.5)'
                },
                {
                  offset: 1,
                  color: 'rgba(52, 152, 219, 0.1)'
                }
              ])
            },
            emphasis: {
              focus: 'series'
            },
            data: [12000, 19000, 15000, 18000, 16000, 22000, 12345]
          },
          {
            name: '订单数',
            type: 'line',
            yAxisIndex: 1,
            emphasis: {
              focus: 'series'
            },
            data: [45, 62, 53, 59, 51, 72, 38]
          }
        ]
      }
      this.salesChart.setOption(option)
      
      window.addEventListener('resize', () => {
        this.salesChart.resize()
      })
    },
    initCategoryChart() {
      this.categoryChart = echarts.init(this.$refs.categoryChart)
      const option = {
        tooltip: {
          trigger: 'item'
        },
        legend: {
          orient: 'vertical',
          left: 'left',
          top: 'center'
        },
        series: [
          {
            name: '商品分类',
            type: 'pie',
            radius: '60%',
            center: ['60%', '50%'],
            data: [
              { value: 45, name: '男装' },
              { value: 30, name: '女装' },
              { value: 15, name: '鞋子' },
              { value: 10, name: '配饰' }
            ],
            emphasis: {
              itemStyle: {
                shadowBlur: 10,
                shadowOffsetX: 0,
                shadowColor: 'rgba(0, 0, 0, 0.5)'
              }
            },
            itemStyle: {
              borderRadius: 10,
              borderColor: '#fff',
              borderWidth: 2
            }
          }
        ]
      }
      this.categoryChart.setOption(option)
      
      window.addEventListener('resize', () => {
        this.categoryChart.resize()
      })
    },
    refreshData() {
      this.$message.success('数据已刷新')
      // 模拟数据刷新
      this.productCount = Math.floor(Math.random() * 50) + 100
      this.orderCount = Math.floor(Math.random() * 100) + 300
      this.userCount = Math.floor(Math.random() * 100) + 500
      this.todaySales = Math.floor(Math.random() * 5000) + 10000
      
      // 重新初始化图表
      this.initCharts()
    },
    exportChart() {
      this.$message.success('图表已导出')
    },
    viewAllOrders() {
      this.$router.push('/order/list')
    },
    viewOrderDetail(orderId) {
      console.log('查看订单详情:', orderId)
    },
    getStatusType(status) {
      switch(status) {
        case '已完成':
          return 'success'
        case '处理中':
          return 'warning'
        case '待支付':
          return 'info'
        default:
          return 'default'
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
  margin: 0;
}

.dashboard-actions {
  display: flex;
  gap: 10px;
}

.dashboard {
  margin-bottom: 30px;
}

.stats-card {
  transition: all 0.3s ease;
  border-radius: 10px;
  overflow: hidden;
}

.stats-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 20px;
  background-color: #f8f9fa;
  border-bottom: 1px solid #e9ecef;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.card-icon {
  font-size: 18px;
  color: #3498db;
}

.card-content {
  padding: 20px;
  position: relative;
}

.stats-trend {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.stats-trend.positive {
  color: #67c23a;
}

.stats-trend.negative {
  color: #f56c6c;
}

.trend-desc {
  color: #999;
}

.charts-section {
  margin-bottom: 30px;
}

.chart-card {
  transition: all 0.3s ease;
  border-radius: 10px;
  overflow: hidden;
}

.chart-card:hover {
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.time-select {
  width: 120px;
}

.chart-container {
  padding: 20px;
}

.chart {
  width: 100%;
  height: 350px;
}

.recent-orders {
  margin-top: 30px;
}

.orders-card {
  transition: all 0.3s ease;
  border-radius: 10px;
  overflow: hidden;
}

.orders-card:hover {
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.1);
}

.orders-table {
  margin-top: 10px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .el-row {
    flex-direction: column;
  }
  
  .el-col {
    width: 100% !important;
    margin-bottom: 20px;
  }
  
  .chart {
    height: 300px;
  }
}

@media (max-width: 480px) {
  .home {
    padding: 10px;
  }
  
  .card-content {
    padding: 15px;
  }
  
  .chart-container {
    padding: 10px;
  }
  
  .chart {
    height: 250px;
  }
}
</style>
