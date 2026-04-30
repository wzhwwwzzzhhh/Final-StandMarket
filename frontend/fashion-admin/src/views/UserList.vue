<template>
  <div class="user-list">
    <div class="header">
      <h2>用户管理</h2>
      <div class="stats-card">
        <div class="stat-item">
          <div class="stat-number">{{ total }}</div>
          <div class="stat-label">总用户数</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">{{ activeUsers }}</div>
          <div class="stat-label">活跃用户</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">{{ newUsersToday }}</div>
          <div class="stat-label">今日新增</div>
        </div>
      </div>
    </div>

    <div class="search-section">
      <el-input
        v-model="searchQuery"
        placeholder="搜索用户名或手机号"
        style="width: 300px; margin-right: 10px;"
        prefix-icon="el-icon-search"
      ></el-input>
      <el-button type="primary" @click="handleSearch">
        <el-icon><Search /></el-icon>
        搜索
      </el-button>
    </div>

    <el-table
      :data="users"
      style="width: 100%"
      stripe
      border
      :loading="loading"
    >
      <el-table-column prop="id" label="用户ID" width="80" />
      <el-table-column prop="name" label="用户名">
        <template #default="scope">
          <div class="user-info">
            <div class="user-avatar">
              {{ scope.row.name.charAt(0).toUpperCase() }}
            </div>
            <span>{{ scope.row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="150" />
      <el-table-column prop="sex" label="性别" width="80">
        <template #default="scope">
          <span :class="['sex-tag', scope.row.sex === '男' ? 'male' : 'female']">
            {{ scope.row.sex || '未知' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="180">
        <template #default="scope">
          {{ formatTime(scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180">
        <template #default="scope">
          <el-button type="primary" size="small" @click="handleView(scope.row)" round>
            <el-icon><View /></el-icon>
            查看
          </el-button>
          <el-button type="danger" size="small" @click="handleDelete(scope.row.id)" round>
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
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
        background
      ></el-pagination>
    </div>

    <!-- 用户详情对话框 -->
    <el-dialog
      title="用户详情"
      v-model="dialogVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <div v-if="userDetail" class="user-detail">
        <div class="user-avatar-large">
          {{ userDetail.name ? userDetail.name.charAt(0).toUpperCase() : '?' }}
        </div>
        <el-form :model="userDetail" label-width="100px" class="detail-form">
          <el-form-item label="用户ID">
            <el-input v-model="userDetail.id" disabled />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="userDetail.name" disabled />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="userDetail.phone" disabled />
          </el-form-item>
          <el-form-item label="性别">
            <el-input v-model="userDetail.sex" disabled />
          </el-form-item>
          <el-form-item label="身份证号">
            <el-input v-model="userDetail.idNumber" disabled />
          </el-form-item>
          <el-form-item label="注册时间">
              <el-input :value="formatTime(userDetail.createTime)" disabled />
            </el-form-item>
        </el-form>
      </div>
      <div v-else class="loading">
        加载中...
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { userApi } from '../api/user'
import { Search, View, Delete } from '@element-plus/icons-vue'

export default {
  name: 'UserList',
  components: {
    Search,
    View,
    Delete
  },
  data() {
    return {
      searchQuery: '',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      users: [],
      dialogVisible: false,
      userDetail: null,
      loading: false,
      activeUsers: 0,
      newUsersToday: 0
    }
  },
  mounted() {
    this.loadUsers()
    this.loadUserStats()
  },
  methods: {
    async loadUsers() {
      this.loading = true
      try {
        const response = await userApi.getUserList({
          page: this.currentPage,
          pageSize: this.pageSize,
          name: this.searchQuery,
          phone: this.searchQuery
        })
        if (response.data.code === 1) {
          this.users = response.data.data.records
          this.total = response.data.data.total
        } else {
          this.$message.error('获取用户列表失败')
        }
      } catch (error) {
        console.error('获取用户列表失败:', error)
        this.$message.error('获取用户列表失败')
      } finally {
        this.loading = false
      }
    },
    loadUserStats() {
      // 模拟统计数据
      this.activeUsers = Math.floor(this.total * 0.7)
      this.newUsersToday = Math.floor(Math.random() * 10) + 1
    },
    handleSearch() {
      this.currentPage = 1
      this.loadUsers()
    },
    async handleView(row) {
      this.userDetail = null
      this.dialogVisible = true
      try {
        const response = await userApi.getUserById(row.id)
        if (response.data.code === 1) {
          this.userDetail = response.data.data
        } else {
          this.$message.error('获取用户详情失败')
          this.dialogVisible = false
        }
      } catch (error) {
        console.error('获取用户详情失败:', error)
        this.$message.error('获取用户详情失败')
        this.dialogVisible = false
      }
    },
    async handleDelete(id) {
      this.$confirm('确定要删除这个用户吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        this.loading = true
        try {
          const response = await userApi.deleteUser(id)
          if (response.data.code === 1) {
            this.$message.success('删除成功')
            this.loadUsers()
            this.loadUserStats()
          } else {
            this.$message.error('删除失败')
          }
        } catch (error) {
          console.error('删除失败:', error)
          this.$message.error('删除失败')
        } finally {
          this.loading = false
        }
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.loadUsers()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.loadUsers()
    },
    formatTime(time) {
      if (!time) return ''
      const date = new Date(time)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}`
    }
  }
}
</script>

<style scoped>
.user-list {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  min-height: calc(100vh - 80px);
}

.header {
  margin-bottom: 30px;
}

.header h2 {
  margin: 0 0 20px 0;
  font-size: 24px;
  color: #333;
}

.stats-card {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  text-align: center;
  transition: all 0.3s ease;
}

.stat-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #1890ff;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.search-section {
  background: white;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}

.el-table {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

.sex-tag {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.sex-tag.male {
  background: #e6f7ff;
  color: #1890ff;
}

.sex-tag.female {
  background: #fff0f5;
  color: #ff4d4f;
}

.pagination {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}

.user-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.user-avatar-large {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1890ff 0%, #36cfc9 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 32px;
  margin-bottom: 20px;
}

.detail-form {
  width: 100%;
}

.loading {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .user-list {
    padding: 10px;
  }

  .stats-card {
    flex-direction: column;
  }

  .search-section {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }

  .el-input {
    width: 100% !important;
  }

  .el-table {
    font-size: 12px;
  }

  .el-table-column {
    width: auto !important;
  }
}
</style>
