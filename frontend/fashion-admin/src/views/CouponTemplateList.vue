<template>
  <div class="coupon-template">
    <h2>优惠券模板管理</h2>
    <div class="action-section">
      <el-button type="primary" @click="handleAdd">创建模板</el-button>
    </div>

    <el-table :data="templates" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70"></el-table-column>
      <el-table-column prop="name" label="券名称" min-width="140"></el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="scope">
          <el-tag>{{ getTypeText(scope.row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优惠" width="110">
        <template #default="scope">
          {{ formatDiscount(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column prop="threshold" label="使用门槛" width="100">
        <template #default="scope">{{ formatThreshold(scope.row.threshold) }}</template>
      </el-table-column>
      <el-table-column prop="totalCount" label="发行总量" width="90">
        <template #default="scope">
          {{ scope.row.totalCount === 0 ? '不限量' : scope.row.totalCount }}
        </template>
      </el-table-column>
      <el-table-column label="有效期" min-width="170">
        <template #default="scope">
          <span v-if="scope.row.validType === 1">
            {{ formatDate(scope.row.startTime) }} ~ {{ formatDate(scope.row.endTime) }}
          </span>
          <span v-else>领取后 {{ scope.row.validDays || 7 }} 天内有效</span>
        </template>
      </el-table-column>
      <el-table-column label="适用范围" min-width="130">
        <template #default="scope">
          {{ formatScope(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="scope">
          <el-button type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
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

    <!-- 添加/编辑模板对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="640px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="券名称">
          <el-input v-model="form.name" placeholder="如：满100减20"></el-input>
        </el-form-item>
        <el-form-item label="券类型">
          <el-radio-group v-model="form.type">
            <el-radio :label="1">满减券</el-radio>
            <el-radio :label="2">折扣券</el-radio>
            <el-radio :label="3">现金券</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.type === 2 ? '折扣值' : '抵扣金额'">
          <el-input
            v-model="form.discount"
            type="number"
            step="0.01"
            :placeholder="form.type === 2 ? '如：8.5 表示85折' : '抵扣金额（元）'"
          ></el-input>
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input v-model="form.threshold" type="number" step="0.01" placeholder="满X元可用，0=无门槛"></el-input>
        </el-form-item>
        <el-form-item label="发行总量">
          <el-input v-model="form.totalCount" type="number" placeholder="0=不限量"></el-input>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input v-model="form.perUserLimit" type="number" placeholder="默认1"></el-input>
        </el-form-item>
        <el-form-item label="有效期类型">
          <el-radio-group v-model="form.validType">
            <el-radio :label="1">固定时间</el-radio>
            <el-radio :label="2">领取后N天</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.validType === 1">
          <el-form-item label="开始时间">
            <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择开始时间" style="width: 100%"></el-date-picker>
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择结束时间" style="width: 100%"></el-date-picker>
          </el-form-item>
        </template>
        <el-form-item v-else label="有效天数">
          <el-input v-model="form.validDays" type="number" placeholder="领取后X天内有效，默认7"></el-input>
        </el-form-item>
        <el-form-item label="适用范围">
          <el-radio-group v-model="form.scopeType">
            <el-radio :label="0">全店</el-radio>
            <el-radio :label="1">指定分类</el-radio>
            <el-radio :label="2">指定商品</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 1" label="指定分类">
          <el-select v-model="form.applyCategoryId" placeholder="选择商品分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 2" label="指定商品ID">
          <el-input v-model="form.applyProductIds" placeholder="商品id，多个用英文逗号分隔，如：1,2,3"></el-input>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">停用</el-radio>
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
import { couponApi } from '../api/coupon'
import { categoryApi } from '../api/product'

export default {
  name: 'CouponTemplateList',
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      total: 0,
      loading: false,
      templates: [],
      categories: [],
      dialogVisible: false,
      dialogTitle: '添加模板',
      form: this.emptyForm()
    }
  },
  mounted() {
    this.loadTemplates()
    this.loadCategories()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        name: '',
        type: 1,
        discount: null,
        threshold: 0,
        totalCount: 0,
        perUserLimit: 1,
        validType: 1,
        validDays: 7,
        startTime: null,
        endTime: null,
        scopeType: 0,
        applyCategoryId: null,
        applyProductIds: '',
        status: 1
      }
    },
    formatDate(dateTime) {
      if (!dateTime) return '-'
      return dateTime
    },
    getTypeText(type) {
      return { 1: '满减', 2: '折扣', 3: '现金' }[type] || '未知'
    },
    formatDiscount(row) {
      if (row.type === 2) {
        return row.discount ? `${row.discount}折` : '-'
      }
      return row.discount ? `省￥${row.discount}` : '-'
    },
    formatThreshold(threshold) {
      return threshold === 0 || threshold === null ? '无门槛' : `满￥${threshold}`
    },
    formatScope(row) {
      if (row.scopeType === 1) return `分类#${row.applyCategoryId}`
      if (row.scopeType === 2) return `商品：${row.applyProductIds}`
      return '全店'
    },
    async loadTemplates() {
      this.loading = true
      try {
        const response = await couponApi.getTemplatePage({
          page: this.currentPage,
          pageSize: this.pageSize
        })
        if (response.data.code === 1) {
          this.templates = response.data.data.records
          this.total = response.data.data.total
        } else {
          this.$message.error(response.data.msg || '获取模板列表失败')
        }
      } catch (error) {
        console.error('获取模板列表失败:', error)
        this.$message.error('获取模板列表失败')
      } finally {
        this.loading = false
      }
    },
    async loadCategories() {
      try {
        const response = await categoryApi.getCategoryList(1)
        if (response.data.code === 1) {
          this.categories = response.data.data || []
        }
      } catch (error) {
        console.error('加载分类失败:', error)
      }
    },
    handleAdd() {
      this.dialogTitle = '添加模板'
      this.form = this.emptyForm()
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.dialogTitle = '编辑模板'
      this.form = {
        ...this.emptyForm(),
        ...row,
        applyProductIds: row.applyProductIds || ''
      }
      this.dialogVisible = true
    },
    handleDelete(row) {
      this.$confirm(`确定删除模板"${row.name}"吗？删除后不可领取。`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        try {
          const response = await couponApi.deleteTemplate(row.id)
          if (response.data.code === 1) {
            this.$message.success('删除成功')
            this.loadTemplates()
          } else {
            this.$message.error(response.data.msg || '删除失败')
          }
        } catch (error) {
          this.$message.error('删除失败')
        }
      }).catch(() => {})
    },
    validate() {
      if (!this.form.name) return '请输入券名称'
      if (this.form.discount === null || this.form.discount === '') return '请输入抵扣金额/折扣值'
      if (this.form.type === 2 && Number(this.form.discount) > 10) return '折扣值不能大于10'
      if (this.form.scopeType === 1 && !this.form.applyCategoryId) return '请选择指定分类'
      if (this.form.scopeType === 2 && !this.form.applyProductIds) return '请输入指定商品ID'
      if (this.form.validType === 1 && !this.form.startTime) return '请选择开始时间'
      if (this.form.validType === 1 && !this.form.endTime) return '请选择结束时间'
      return null
    },
    async handleSubmit() {
      const errMsg = this.validate()
      if (errMsg) {
        this.$message.warning(errMsg)
        return
      }
      try {
        const payload = {
          ...this.form,
          threshold: this.form.threshold === null || this.form.threshold === '' ? 0 : this.form.threshold,
          totalCount: this.form.totalCount === null || this.form.totalCount === '' ? 0 : this.form.totalCount,
          perUserLimit: this.form.perUserLimit === null || this.form.perUserLimit === '' ? 1 : this.form.perUserLimit,
          validDays: this.form.validDays === null || this.form.validDays === '' ? 7 : this.form.validDays,
          applyProductIds: this.form.scopeType === 2 ? this.form.applyProductIds : null,
          applyCategoryId: this.form.scopeType === 1 ? this.form.applyCategoryId : null
        }
        const response = this.form.id
          ? await couponApi.updateTemplate(payload)
          : await couponApi.addTemplate(payload)
        if (response.data.code === 1) {
          this.$message.success(this.form.id ? '修改成功' : '添加成功')
          this.dialogVisible = false
          this.loadTemplates()
        } else {
          this.$message.error(response.data.msg || '操作失败')
        }
      } catch (error) {
        console.error('保存模板失败:', error)
        this.$message.error('保存模板失败')
      }
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.loadTemplates()
    },
    handleCurrentChange(val) {
      this.currentPage = val
      this.loadTemplates()
    }
  }
}
</script>

<style scoped>
.coupon-template {
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