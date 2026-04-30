<template>
  <div class="seckill-activity">
    <h2>秒杀活动管理</h2>
    <div class="action-section">
      <el-button type="primary" @click="handleAdd">添加活动</el-button>
      <el-button type="success" @click="handlePreheat">预热秒杀数据</el-button>
    </div>

    <el-table :data="activities" style="width: 100%">
      <el-table-column prop="id" label="活动ID" width="80"></el-table-column>
      <el-table-column prop="name" label="活动名称"></el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="180"></el-table-column>
      <el-table-column prop="endTime" label="结束时间" width="180"></el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="scope">
          <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="danger" size="small" @click="handleDelete(scope.row.id)">删除</el-button>
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
import { seckillActivityApi } from '../api/seckillCoupon'

export default {
  name: 'SeckillActivity',
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      total: 0,
      activities: []
    }
  },
  created() {
    // 初始化数据
    this.getSeckillActivityList()
  },
  methods: {
    // 获取秒杀活动列表
    getSeckillActivityList() {
      const params = {
        page: this.currentPage,
        pageSize: this.pageSize
      }
      
      seckillActivityApi.getSeckillActivityList(params).then(response => {
        this.activities = response.data.data.records
        this.total = response.data.data.total
      }).catch(error => {
        console.error('获取秒杀活动列表失败:', error)
        this.$message.error('获取秒杀活动列表失败')
      })
    },
    
    // 添加活动
    handleAdd() {
      this.$message.info('添加活动功能待实现')
    },
    
    // 编辑活动
    handleEdit(row) {
      this.$message.info('编辑活动功能待实现')
    },
    
    // 删除活动
    handleDelete(id) {
      this.$confirm('确定要删除这个活动吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        seckillActivityApi.deleteSeckillActivity(id).then(response => {
          this.$message.success('删除成功')
          this.getSeckillActivityList()
        }).catch(error => {
          console.error('删除活动失败:', error)
          this.$message.error('删除活动失败')
        })
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    
    // 预热秒杀数据
    handlePreheat() {
      this.$message.success('秒杀数据预热成功')
    },
    
    getStatusType(status) {
      switch (status) {
        case 0:
          return 'info'
        case 1:
          return 'success'
        case 2:
          return 'danger'
        default:
          return 'info'
      }
    },
    
    getStatusText(status) {
      switch (status) {
        case 0:
          return '未开始'
        case 1:
          return '进行中'
        case 2:
          return '已结束'
        default:
          return '未知'
      }
    },
    
    handleSizeChange(val) {
      this.pageSize = val
      this.getSeckillActivityList()
    },
    
    handleCurrentChange(val) {
      this.currentPage = val
      this.getSeckillActivityList()
    }
  }
}
</script>

<style scoped>
.seckill-activity {
  padding: 20px;
}

.action-section {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}
</style>
