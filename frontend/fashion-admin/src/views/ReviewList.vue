<template>
  <div class="review-list">
    <div class="page-header">
      <h2 class="page-title">评价管理</h2>
      <div class="header-stats">
        <div class="stat-item">
          <span class="stat-label">评价总数</span>
          <span class="stat-value">{{ total }}</span>
        </div>
      </div>
    </div>

    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索商品名称" class="search-input">
          <template #prefix>
            <el-icon class="el-input__icon"><Search /></el-icon>
          </template>
        </el-input>
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>
      </div>
    </div>

    <div class="table-section">
      <el-table :data="reviews" class="review-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="userName" label="用户" min-width="100" />
        <el-table-column prop="productName" label="商品" min-width="150" show-overflow-tooltip />
        <el-table-column label="评分" width="180" align="center">
          <template #default="scope">
            <el-rate :model-value="scope.row.rating" disabled :max="5" :colors="['#ff4d4f']" size="small" />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
              {{ scope.row.status === 1 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="评价时间" width="160" align="center" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <el-button
              :type="scope.row.status === 1 ? 'warning' : 'success'"
              size="small"
              @click="handleToggleStatus(scope.row)"
            >
              {{ scope.row.status === 1 ? '隐藏' : '显示' }}
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
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
  </div>
</template>

<script>
import { Search } from '@element-plus/icons-vue'
import { reviewApi } from '@/api/review'

export default {
  name: 'ReviewList',
  components: { Search },
  data() {
    return {
      reviews: [],
      total: 0,
      page: 1,
      pageSize: 10,
      searchQuery: '',
      loading: false
    }
  },
  created() {
    this.loadReviews()
  },
  methods: {
    loadReviews() {
      this.loading = true
      const params = { page: this.page, size: this.pageSize }
      if (this.searchQuery.trim()) {
        params.keyword = this.searchQuery.trim()
      }
      reviewApi.getList(params).then(response => {
        if (response.data.code === 1) {
          this.reviews = response.data.data.records || []
          this.total = response.data.data.total || 0
        }
      }).catch(() => {
        this.$message.error('获取评价列表失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handleSearch() {
      this.page = 1
      this.loadReviews()
    },
    handlePageChange(page) {
      this.page = page
      this.loadReviews()
    },
    handleToggleStatus(row) {
      const newStatus = row.status === 1 ? 0 : 1
      reviewApi.updateStatus({ id: row.id, status: newStatus }).then(response => {
        if (response.data.code === 1) {
          row.status = newStatus
          this.$message.success(response.data.msg || '操作成功')
        } else {
          this.$message.error(response.data.msg || '操作失败')
        }
      }).catch(() => {
        this.$message.error('操作失败')
      })
    },
    handleDelete(row) {
      this.$confirm(`确定删除该评价？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        reviewApi.deleteReview(row.id).then(response => {
          if (response.data.code === 1) {
            this.$message.success('删除成功')
            this.loadReviews()
          } else {
            this.$message.error(response.data.msg || '删除失败')
          }
        }).catch(() => {
          this.$message.error('删除失败')
        })
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.review-list {
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
</style>
