<template>
  <div class="employee-list">
    <div class="page-header">
      <h2 class="page-title">员工管理</h2>
      <el-button type="primary" class="add-button" @click="handleAdd">
        <el-icon><Plus /></el-icon> 新增员工
      </el-button>
    </div>

    <div class="search-section">
      <div class="search-card">
        <el-input v-model="searchQuery" placeholder="搜索员工姓名" class="search-input" clearable @clear="handleSearch">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <div class="search-actions">
          <el-button type="primary" class="search-button" @click="handleSearch">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
        </div>
      </div>
    </div>

    <div class="table-section">
      <el-table :data="employees" class="employee-table" style="width: 100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="sex" label="性别" width="80" align="center">
          <template #default="scope">
            <span>{{ scope.row.sex === '1' ? '男' : scope.row.sex === '0' ? '女' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
              {{ scope.row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button
              :type="scope.row.status === 1 ? 'warning' : 'success'"
              size="small"
              @click="handleToggleStatus(scope.row)"
            >
              {{ scope.row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination">
      <el-pagination
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[10, 20, 30, 40]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑员工' : '新增员工'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.sex">
            <el-radio value="1">男</el-radio>
            <el-radio value="0">女</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { employeeApi } from '../api/employee'
import { Plus, Search } from '@element-plus/icons-vue'

export default {
  name: 'EmployeeList',
  components: { Plus, Search },
  data() {
    return {
      searchQuery: '',
      currentPage: 1,
      pageSize: 10,
      total: 0,
      employees: [],
      loading: false,
      dialogVisible: false,
      isEdit: false,
      submitting: false,
      form: {
        username: '',
        name: '',
        phone: '',
        password: '',
        sex: '1'
      },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
        name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getEmployeeList()
  },
  methods: {
    getEmployeeList() {
      this.loading = true
      const params = {
        page: this.currentPage,
        pageSize: this.pageSize,
        name: this.searchQuery || undefined
      }
      employeeApi.getEmployeeList(params).then(res => {
        const data = res.data.data
        this.employees = data.records || []
        this.total = data.total || 0
      }).catch(() => {
        this.$message.error('获取员工列表失败')
      }).finally(() => {
        this.loading = false
      })
    },

    handleSearch() {
      this.currentPage = 1
      this.getEmployeeList()
    },

    handleAdd() {
      this.isEdit = false
      this.form = { username: '', name: '', phone: '', password: '', sex: '1' }
      this.dialogVisible = true
    },

    handleEdit(row) {
      this.isEdit = true
      this.form = {
        username: row.username,
        name: row.name,
        phone: row.phone,
        sex: row.sex || '1'
      }
      this.editId = row.id
      this.dialogVisible = true
    },

    handleSubmit() {
      this.$refs.formRef.validate(valid => {
        if (!valid) return
        this.submitting = true
        const data = { ...this.form }
        if (this.isEdit) {
          data.id = this.editId
          employeeApi.updateEmployee(data).then(() => {
            this.$message.success('更新成功')
            this.dialogVisible = false
            this.getEmployeeList()
          }).catch(() => {
            this.$message.error('更新失败')
          }).finally(() => {
            this.submitting = false
          })
        } else {
          employeeApi.addEmployee(data).then(() => {
            this.$message.success('新增成功')
            this.dialogVisible = false
            this.getEmployeeList()
          }).catch(() => {
            this.$message.error('新增失败')
          }).finally(() => {
            this.submitting = false
          })
        }
      })
    },

    handleToggleStatus(row) {
      const newStatus = row.status === 1 ? 0 : 1
      const text = newStatus === 1 ? '启用' : '禁用'
      this.$confirm(`确定${text}员工 "${row.name}" 吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        employeeApi.updateEmployee({ id: row.id, status: newStatus }).then(() => {
          this.$message.success(`${text}成功`)
          this.getEmployeeList()
        }).catch(() => {
          this.$message.error(`${text}失败`)
        })
      }).catch(() => {})
    },

    handleDelete(row) {
      this.$confirm(`确定删除员工 "${row.name}" 吗？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        employeeApi.deleteEmployee(row.id).then(() => {
          this.$message.success('删除成功')
          this.getEmployeeList()
        }).catch(() => {
          this.$message.error('删除失败')
        })
      }).catch(() => {})
    },

    handleSizeChange(val) {
      this.pageSize = val
      this.getEmployeeList()
    },

    handleCurrentChange(val) {
      this.currentPage = val
      this.getEmployeeList()
    }
  }
}
</script>

<style scoped>
.employee-list { padding: 30px; min-height: 100vh; background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; }
.page-title { font-size: 24px; font-weight: bold; color: #333; margin: 0; }
.add-button { border-radius: 8px; }

.search-section { margin-bottom: 30px; }
.search-card { background: #fff; padding: 24px; border-radius: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.1); display: flex; gap: 20px; align-items: center; }
.search-input { flex: 1; }
.search-actions { display: flex; gap: 12px; }
.search-button { border-radius: 8px; }

.table-section { margin-bottom: 30px; }
.employee-table { background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.1); }
.employee-table th { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: #fff; }

.pagination { margin-top: 30px; display: flex; justify-content: center; }
</style>
