<template>
  <div class="operation-log-list">
    <div class="page-header">
      <h2 class="page-title">操作日志</h2>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-label">日志总数</span>
          <span class="stat-value">{{ total }}</span>
        </div>
      </div>
    </div>

    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索操作人/操作描述" class="search-input" clearable @clear="handleSearch">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="module" placeholder="全部模块" clearable class="module-select" @change="handleSearch">
          <el-option v-for="item in moduleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>
      </div>
    </div>

    <div class="table-section">
      <el-table :data="logs" class="log-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="employeeName" label="操作人" min-width="90" />
        <el-table-column prop="module" label="模块" width="100" align="center">
          <template #default="scope">
            <el-tag size="small" type="info">{{ scope.row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="操作" min-width="130" />
        <el-table-column prop="method" label="接口" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column prop="createTime" label="操作时间" width="160" align="center" />
        <el-table-column label="参数" width="160" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" @click="handleViewParams(scope.row)">查看参数</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog v-model="paramsVisible" title="请求参数（JSON）" width="600px">
      <pre class="params-json">{{ currentParams }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { Search } from '@element-plus/icons-vue'
import { operationLogApi } from '@/api/operationLog'

export default {
  name: 'OperationLogList',
  components: { Search },
  data() {
    return {
      logs: [],
      total: 0,
      page: 1,
      pageSize: 10,
      searchQuery: '',
      module: '',
      moduleOptions: [
        { label: '商品管理', value: '商品管理' },
        { label: '分类管理', value: '分类管理' },
        { label: '订单管理', value: '订单管理' },
        { label: '退款管理', value: '退款管理' },
        { label: '员工管理', value: '员工管理' },
        { label: '用户管理', value: '用户管理' },
        { label: '评价管理', value: '评价管理' },
        { label: '特价商品', value: '特价商品' },
        { label: '秒杀活动', value: '秒杀活动' },
        { label: '秒杀券', value: '秒杀券' },
        { label: '秒杀订单', value: '秒杀订单' },
        { label: 'ES同步', value: 'ES同步' }
      ],
      loading: false,
      paramsVisible: false,
      currentParams: ''
    }
  },
  created() {
    this.loadLogs()
  },
  methods: {
    loadLogs() {
      this.loading = true
      const params = { page: this.page, size: this.pageSize }
      if (this.searchQuery.trim()) {
        params.keyword = this.searchQuery.trim()
      }
      if (this.module) {
        params.module = this.module
      }
      operationLogApi.getPage(params).then(response => {
        if (response.data.code === 1) {
          this.logs = response.data.data.records || []
          this.total = response.data.data.total || 0
        }
      }).catch(() => {
        this.$message.error('获取操作日志失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handleSearch() {
      this.page = 1
      this.loadLogs()
    },
    handlePageChange(page) {
      this.page = page
      this.loadLogs()
    },
    handleViewParams(row) {
      this.currentParams = row.params || '无参数'
      this.paramsVisible = true
    }
  }
}
</script>

<style scoped>
.operation-log-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.header-stats {
  display: flex;
  gap: 20px;
}

.stat-item {
  background: #f5f7fa;
  padding: 12px 24px;
  border-radius: 8px;
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.search-section {
  margin-bottom: 20px;
}

.search-card {
  display: flex;
  gap: 16px;
  align-items: center;
  background: #fff;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.search-input {
  width: 300px;
}

.module-select {
  width: 160px;
}

.search-actions {
  display: flex;
  gap: 12px;
}

.table-section {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 16px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.params-json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  max-height: 400px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>