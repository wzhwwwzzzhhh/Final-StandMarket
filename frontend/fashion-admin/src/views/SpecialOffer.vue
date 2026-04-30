<template>
  <div class="special-offer">
    <h2>特价商品管理</h2>
    <div class="action-section">
      <el-button type="primary" @click="handleAdd">添加特价商品</el-button>
    </div>

    <el-table :data="offers" style="width: 100%">
      <el-table-column prop="id" label="ID" width="80"></el-table-column>
      <el-table-column prop="productId" label="商品ID" width="100"></el-table-column>
      <el-table-column prop="originalPrice" label="原价" width="100"></el-table-column>
      <el-table-column prop="offerPrice" label="特价" width="100"></el-table-column>
      <el-table-column prop="stock" label="库存" width="100"></el-table-column>
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

    <!-- 添加/编辑特价商品对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="500px"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="商品ID">
          <el-input v-model="form.productId" type="number" placeholder="请输入商品ID"></el-input>
        </el-form-item>
        <el-form-item label="原价">
          <el-input v-model="form.originalPrice" type="number" step="0.01" placeholder="请输入原价"></el-input>
        </el-form-item>
        <el-form-item label="特价">
          <el-input v-model="form.offerPrice" type="number" step="0.01" placeholder="请输入特价"></el-input>
        </el-form-item>
        <el-form-item label="库存">
          <el-input v-model="form.stock" type="number" placeholder="请输入库存"></el-input>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            style="width: 100%"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="0">未开始</el-radio>
            <el-radio label="1">进行中</el-radio>
            <el-radio label="2">已结束</el-radio>
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
import { specialOfferApi } from '../api/specialOffer'

export default {
  name: 'SpecialOffer',
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      total: 0,
      offers: [],
      dialogVisible: false,
      dialogTitle: '添加特价商品',
      form: {
        id: null,
        productId: null,
        originalPrice: null,
        offerPrice: null,
        stock: null,
        startTime: null,
        endTime: null,
        status: 0
      }
    }
  },
  mounted() {
    this.loadOffers()
  },
  methods: {
    async loadOffers() {
      try {
        const response = await specialOfferApi.getOfferList({
          page: this.currentPage,
          pageSize: this.pageSize
        })
        if (response.data.code === 1) {
          this.offers = response.data.data.records
          this.total = response.data.data.total
        } else {
          this.$message.error('获取特价商品列表失败')
        }
      } catch (error) {
        console.error('获取特价商品列表失败:', error)
        this.$message.error('获取特价商品列表失败')
      }
    },
    handleAdd() {
      this.dialogTitle = '添加特价商品'
      this.form = {
        id: null,
        productId: null,
        originalPrice: null,
        offerPrice: null,
        stock: null,
        startTime: null,
        endTime: null,
        status: 0
      }
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.dialogTitle = '编辑特价商品'
      this.form = { ...row }
      this.dialogVisible = true
    },
    async handleSubmit() {
      try {
        if (this.form.id) {
          // 编辑
          const response = await specialOfferApi.updateOffer(this.form)
          if (response.data.code === 1) {
            this.$message.success('修改成功')
            this.dialogVisible = false
            this.loadOffers()
          } else {
            this.$message.error('修改失败')
          }
        } else {
          // 添加
          const response = await specialOfferApi.addOffer(this.form)
          if (response.data.code === 1) {
            this.$message.success('添加成功')
            this.dialogVisible = false
            this.loadOffers()
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
      this.$confirm('确定要删除这个特价商品吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          const response = await specialOfferApi.deleteOffer(id)
          if (response.data.code === 1) {
            this.$message.success('删除成功')
            this.loadOffers()
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
      this.loadOffers()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.loadOffers()
    }
  }
}
</script>

<style scoped>
.special-offer {
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
