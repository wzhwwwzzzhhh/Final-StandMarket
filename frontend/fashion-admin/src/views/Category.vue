<template>
  <div class="category">
    <h2>分类管理</h2>
    <div class="action-section">
      <el-button type="primary" @click="handleAdd">添加分类</el-button>
    </div>

    <el-table :data="categories" style="width: 100%">
      <el-table-column prop="id" label="分类ID" width="80"></el-table-column>
      <el-table-column prop="name" label="分类名称"></el-table-column>
      <el-table-column prop="sort" label="排序" width="100"></el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-switch v-model="scope.row.status" active-value="1" inactive-value="0" @change="handleStatusChange(scope.row)"></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="danger" size="small" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import { categoryApi } from '../api/product'

export default {
  name: 'Category',
  data() {
    return {
      categories: [],
      updatingStatus: false // 防止重复更新状态
    }
  },
  created() {
    // 初始化数据
    this.getCategoryList()
  },
  methods: {
    // 获取分类列表
    getCategoryList() {
      categoryApi.getCategoryList().then(response => {
        this.categories = response.data.data
      }).catch(error => {
        console.error('获取分类列表失败:', error)
        this.$message.error('获取分类列表失败')
      })
    },
    
    // 添加分类
    handleAdd() {
      this.$prompt('请输入分类名称', '添加分类', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '分类名称'
      }).then(({ value }) => {
        categoryApi.addCategory({ name: value, sort: this.categories.length + 1, status: 1 }).then(response => {
          this.$message.success('添加成功')
          this.getCategoryList()
        }).catch(error => {
          console.error('添加分类失败:', error)
          this.$message.error('添加分类失败')
        })
      }).catch(() => {
        this.$message.info('已取消添加')
      })
    },
    
    // 编辑分类
    handleEdit(row) {
      this.$prompt('请输入分类名称', '编辑分类', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: row.name,
        inputPlaceholder: '分类名称'
      }).then(({ value }) => {
        categoryApi.updateCategory(row.id, { name: value, sort: row.sort, status: row.status }).then(response => {
          this.$message.success('编辑成功')
          this.getCategoryList()
        }).catch(error => {
          console.error('编辑分类失败:', error)
          this.$message.error('编辑分类失败')
        })
      }).catch(() => {
        this.$message.info('已取消编辑')
      })
    },
    
    // 删除分类
    handleDelete(id) {
      this.$confirm('确定要删除这个分类吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        categoryApi.deleteCategory(id).then(response => {
          this.$message.success('删除成功')
          this.getCategoryList()
        }).catch(error => {
          console.error('删除分类失败:', error)
          this.$message.error('删除分类失败')
        })
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    
    // 修改分类状态
    handleStatusChange(row) {
      // 防止重复触发状态更新
      if (this.updatingStatus) {
        return
      }
      
      this.updatingStatus = true
      
      categoryApi.updateStatus(row.id, row.status).then(response => {
        if (response.data.code === 1) {
          this.$message.success('状态更新成功')
        } else {
          this.$message.error('状态更新失败')
          // 恢复原状态
          row.status = !row.status
        }
      }).catch(error => {
        console.error('更新状态失败:', error)
        this.$message.error('更新状态失败')
        // 恢复原状态
        row.status = !row.status
      }).finally(() => {
        // 无论成功失败，都恢复更新状态
        this.updatingStatus = false
      })
    }
  }
}
</script>

<style scoped>
.category {
  padding: 20px;
}

.action-section {
  margin-bottom: 20px;
}
</style>
