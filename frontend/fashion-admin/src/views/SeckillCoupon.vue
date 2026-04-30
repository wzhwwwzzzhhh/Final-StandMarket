<template>
  <div class="seckill-coupon">
    <h2>秒杀券管理</h2>
    <div class="action-section">
          <el-button type="primary" @click="handleAdd">添加秒杀券</el-button>
          <el-button type="success" @click="handlePreheatBatch">批量预热</el-button>
        </div>

    <el-table 
        ref="multipleTable"
        :data="coupons" 
        style="width: 100%"
        @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55"></el-table-column>
      <el-table-column prop="id" label="券ID" width="80"></el-table-column>
      <el-table-column prop="name" label="券名称"></el-table-column>
      <el-table-column prop="originalPrice" label="原价" width="100"></el-table-column>
      <el-table-column prop="seckillPrice" label="秒杀价" width="100"></el-table-column>
      <el-table-column prop="stock" label="库存" width="80"></el-table-column>
      <el-table-column prop="limitPerUser" label="每人限购" width="100"></el-table-column>
      <el-table-column label="起售时间" width="160">
        <template #default="scope">
          {{ formatDate(scope.row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column label="停售时间" width="160">
        <template #default="scope">
          {{ formatDate(scope.row.endTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? '有效' : '无效' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="scope">
          <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="success" size="small" @click="handlePreheat(scope.row)">预热</el-button>
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

    <!-- 添加/编辑秒杀券对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="600px"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="券名称">
          <el-input v-model="form.name" placeholder="请输入券名称"></el-input>
        </el-form-item>
        <el-form-item label="原价">
          <el-input v-model="form.originalPrice" type="number" step="0.01" placeholder="请输入原价"></el-input>
        </el-form-item>
        <el-form-item label="秒杀价">
          <el-input v-model="form.seckillPrice" type="number" step="0.01" placeholder="请输入秒杀价"></el-input>
        </el-form-item>
        <el-form-item label="库存">
          <el-input v-model="form.stock" type="number" placeholder="请输入库存"></el-input>
        </el-form-item>
        <el-form-item label="每人限购">
          <el-input v-model="form.limitPerUser" type="number" placeholder="请输入每人限购数量"></el-input>
        </el-form-item>
        <el-form-item label="起售时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择起售时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="停售时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择停售时间"
            value-format="YYYY-MM-DD HH:mm:ss"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">有效</el-radio>
            <el-radio :label="0">无效</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { seckillCouponApi } from '../api/seckillCoupon'

export default {
  name: 'SeckillCoupon',
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      total: 0,
      coupons: [],
      dialogVisible: false,
      dialogTitle: '添加秒杀券',
      form: {
        id: null,
        name: '',
        originalPrice: null,
        seckillPrice: null,
        stock: null,
        limitPerUser: null,
        startTime: null,
        endTime: null,
        status: 1
      },
      multipleSelection: []
    }
  },
  mounted() {
    this.loadCoupons()
  },
  methods: {
    formatDate(dateTime) {
      if (!dateTime) return '-'
      const date = new Date(dateTime)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      const seconds = String(date.getSeconds()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    },

    async loadCoupons() {
      try {
        const response = await seckillCouponApi.getCouponList({
          page: this.currentPage,
          pageSize: this.pageSize
        })
        if (response.data.code === 1) {
          this.coupons = response.data.data.records
          this.total = response.data.data.total
        } else {
          this.$message.error('获取秒杀券列表失败')
        }
      } catch (error) {
        console.error('获取秒杀券列表失败:', error)
        this.$message.error('获取秒杀券列表失败')
      }
    },
    handleAdd() {
      this.dialogTitle = '添加秒杀券'
      this.form = {
        id: null,
        name: '',
        originalPrice: null,
        seckillPrice: null,
        stock: null,
        limitPerUser: null,
        startTime: null,
        endTime: null,
        status: 1
      }
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.dialogTitle = '编辑秒杀券'
      this.form = { 
        id: row.id,
        name: row.name,
        originalPrice: row.originalPrice,
        seckillPrice: row.seckillPrice,
        stock: row.stock,
        limitPerUser: row.limitPerUser,
        startTime: row.startTime,
        endTime: row.endTime,
        status: row.status
      }
      this.dialogVisible = true
    },
    async handleSubmit() {
      try {
        if (!this.form.name) {
          this.$message.warning('请输入券名称')
          return
        }
        if (!this.form.originalPrice) {
          this.$message.warning('请输入原价')
          return
        }
        if (!this.form.seckillPrice) {
          this.$message.warning('请输入秒杀价')
          return
        }
        if (!this.form.stock && this.form.stock !== 0) {
          this.$message.warning('请输入库存')
          return
        }
        if (!this.form.startTime) {
          this.$message.warning('请选择起售时间')
          return
        }
        if (!this.form.endTime) {
          this.$message.warning('请选择停售时间')
          return
        }
        
        if (this.form.id) {
          // 编辑
          const response = await seckillCouponApi.updateCoupon(this.form)
          if (response.data.code === 1) {
            this.$message.success('修改成功')
            this.dialogVisible = false
            this.loadCoupons()
          } else {
            this.$message.error('修改失败')
          }
        } else {
          // 添加
          const response = await seckillCouponApi.addCoupon(this.form)
          if (response.data.code === 1) {
            this.$message.success('添加成功')
            this.dialogVisible = false
            this.loadCoupons()
          } else {
            this.$message.error('添加失败')
          }
        }
      } catch (error) {
        console.error('操作失败:', error)
        this.$message.error('操作失败')
      }
    },
    async handleDelete(id) {
      this.$confirm('确定要删除这个秒杀券吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          const response = await seckillCouponApi.deleteCoupon(id)
          if (response.data.code === 1) {
            this.$message.success('删除成功')
            this.loadCoupons()
          } else {
            this.$message.error('删除失败')
          }
        } catch (error) {
          console.error('删除失败:', error)
          this.$message.error('删除失败')
        }
      }).catch(() => {
        this.$message.info('已取消删除')
      })
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.loadCoupons()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.loadCoupons()
    },
    
    // 处理表格选择变化
    handleSelectionChange(val) {
      this.multipleSelection = val
    },
    
    // 预热单个秒杀券
    async handlePreheat(row) {
      try {
        await this.$confirm(`确定要预热秒杀券"${row.name}"吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        const response = await seckillCouponApi.preheatCoupon(row.id)
        if (response.data.code === 1) {
          this.$message.success('预热成功')
        } else {
          this.$message.error(response.data.msg || '预热失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('预热失败')
        }
      }
    },
    
    // 批量预热
    async handlePreheatBatch() {
      // 获取选中的秒杀券ID列表
      const selectedIds = this.multipleSelection.map(item => item.id)
      
      if (selectedIds.length === 0) {
        this.$message.warning('请先选择要预热的秒杀券')
        return
      }
      
      try {
        await this.$confirm(`确定要预热选中的 ${selectedIds.length} 个秒杀券吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        
        const response = await seckillCouponApi.preheatBatchCoupons(selectedIds)
        if (response.data.code === 1) {
          this.$message.success(`批量预热成功，共预热 ${selectedIds.length} 个秒杀券`)
        } else {
          this.$message.error(response.data.msg || '批量预热失败')
        }
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('批量预热失败')
        }
      }
    }
  }
}
</script>

<style scoped>
.seckill-coupon {
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
