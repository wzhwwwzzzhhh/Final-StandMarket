<template>
  <div class="coupon-user">
    <h2>用户持券管理</h2>
    <div class="filter-section">
      <el-input
        v-model="keyword"
        placeholder="按用户名/手机号搜索"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      ></el-input>
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="未使用" :value="0"></el-option>
        <el-option label="已使用" :value="1"></el-option>
        <el-option label="已过期" :value="2"></el-option>
        <el-option label="已锁定" :value="3"></el-option>
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
    </div>

    <el-table :data="userCoupons" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="持券ID" width="90"></el-table-column>
      <el-table-column prop="userName" label="用户" min-width="120">
        <template #default="scope">
          <span>{{ scope.row.userName || ('用户#' + scope.row.userId) }}</span>
          <span v-if="scope.row.userPhone" class="user-phone">（{{ scope.row.userPhone }}）</span>
        </template>
      </el-table-column>
      <el-table-column prop="templateName" label="券名称" min-width="140"></el-table-column>
      <el-table-column label="券类型" width="80">
        <template #default="scope">
          {{ getTypeText(scope.row.templateType) }}
        </template>
      </el-table-column>
      <el-table-column prop="expireTime" label="过期时间" width="160"></el-table-column>
      <el-table-column prop="useOrderId" label="核销订单" width="110">
        <template #default="scope">{{ scope.row.useOrderId || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[10, 20, 30, 40]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
      ></el-pagination>
    </div>
  </div>
</template>

<script>
import { couponApi } from '../api/coupon'

export default {
  name: 'CouponUserList',
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      total: 0,
      loading: false,
      keyword: '',
      statusFilter: null,
      userCoupons: []
    }
  },
  mounted() {
    this.loadList()
  },
  methods: {
    getTypeText(type) {
      return { 1: '满减', 2: '折扣', 3: '现金' }[type] || '未知'
    },
    getStatusText(status) {
      return { 0: '未使用', 1: '已使用', 2: '已过期', 3: '已锁定' }[status] || '未知'
    },
    getStatusType(status) {
      return { 0: 'success', 1: 'info', 2: 'warning', 3: 'danger' }[status] || 'info'
    },
    handleSearch() {
      this.currentPage = 1
      this.loadList()
    },
    async loadList() {
      this.loading = true
      try {
        const response = await couponApi.getUserCouponPage({
          page: this.currentPage,
          pageSize: this.pageSize,
          status: this.statusFilter === null || this.statusFilter === '' ? null : this.statusFilter,
          keyword: this.keyword || null
        })
        if (response.data.code === 1) {
          this.userCoupons = response.data.data.records
          this.total = response.data.data.total
        } else {
          this.$message.error(response.data.msg || '获取持券列表失败')
        }
      } catch (error) {
        console.error('获取持券列表失败:', error)
        this.$message.error('获取持券列表失败')
      } finally {
        this.loading = false
      }
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.loadList()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.loadList()
    }
  }
}
</script>

<style scoped>
.coupon-user {
  padding: 20px;
}

.filter-section {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.pagination {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}

.user-phone {
  color: #999;
  font-size: 12px;
}
</style>