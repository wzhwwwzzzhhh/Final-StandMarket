<template>
  <div class="refund-manage">
    <div class="page-header">
      <h2>退款管理</h2>
      <div class="header-actions">
        <el-select v-model="statusFilter" placeholder="筛选状态" @change="loadRefunds" style="width: 140px">
          <el-option label="全部" :value="null"></el-option>
          <el-option label="待审核" :value="0"></el-option>
          <el-option label="已同意，等待退款处理" :value="1"></el-option>
          <el-option label="退款完成" :value="2"></el-option>
          <el-option label="已拒绝" :value="3"></el-option>
        </el-select>
        <el-button type="primary" @click="loadRefunds">刷新</el-button>
      </div>
    </div>

    <el-table :data="refunds" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="refundNo" label="退款单号" width="200" />
      <el-table-column prop="orderId" label="订单ID" width="100" />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column label="退款金额" width="120">
        <template #default="{ row }">
          <span style="color: #e6a23c; font-weight: bold">¥{{ row.amount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="退款原因" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column prop="auditOpinion" label="审核意见" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button type="success" size="small" @click="handleApprove(row)">同意</el-button>
            <el-button type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
          </template>
          <span v-else style="color: #999; font-size: 13px">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && refunds.length === 0" description="暂无退款记录" style="margin-top: 60px" />

    <!-- 拒绝审核对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="拒绝退款" width="450px" :close-on-click-modal="false">
      <el-form>
        <el-form-item label="审核意见" required>
          <el-input
            v-model="rejectOpinion"
            type="textarea"
            :rows="4"
            placeholder="请填写拒绝原因（必填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectSubmitting" @click="submitReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { refundApi } from '@/api/refund'

export default {
  name: 'RefundManage',
  data() {
    return {
      refunds: [],
      loading: false,
      statusFilter: null,
      currentRefund: null,
      rejectDialogVisible: false,
      rejectOpinion: '',
      rejectSubmitting: false
    }
  },
  mounted() {
    this.loadRefunds()
  },
  methods: {
    loadRefunds() {
      this.loading = true
      refundApi.list({ status: this.statusFilter }).then(response => {
        if (response.data.code === 1) {
          this.refunds = response.data.data || []
        } else {
          this.$message.error(response.data.msg || '获取退款列表失败')
        }
      }).catch(error => {
        console.error('获取退款列表失败:', error)
        this.$message.error('获取退款列表失败')
      }).finally(() => {
        this.loading = false
      })
    },
    handleApprove(row) {
      this.$confirm(`确认同意退款单 ${row.refundNo} ？`, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        refundApi.approve({ id: row.id, opinion: '同意退款' }).then(response => {
          if (response.data.code === 1) {
            this.$message.success('已同意，等待退款处理')
            this.loadRefunds()
          } else {
            this.$message.error(response.data.msg || '操作失败')
          }
        }).catch(error => {
          console.error('同意退款失败:', error)
          this.$message.error('操作失败')
        })
      }).catch(() => {})
    },
    handleReject(row) {
      this.currentRefund = row
      this.rejectOpinion = ''
      this.rejectDialogVisible = true
    },
    submitReject() {
      if (!this.rejectOpinion.trim()) {
        this.$message.warning('请填写审核意见')
        return
      }
      this.rejectSubmitting = true
      refundApi.reject({
        id: this.currentRefund.id,
        opinion: this.rejectOpinion.trim()
      }).then(response => {
        if (response.data.code === 1) {
          this.$message.success('已拒绝退款')
          this.rejectDialogVisible = false
          this.loadRefunds()
        } else {
          this.$message.error(response.data.msg || '操作失败')
        }
      }).catch(error => {
        console.error('拒绝退款失败:', error)
        this.$message.error('操作失败')
      }).finally(() => {
        this.rejectSubmitting = false
      })
    },
    statusText(status) {
      const map = { 0: '待审核', 1: '已同意，等待退款处理', 2: '退款完成', 3: '已拒绝' }
      return map[status] || '未知'
    },
    statusTagType(status) {
      const map = { 0: 'warning', 1: 'info', 2: 'success', 3: 'danger' }
      return map[status] || 'info'
    }
  }
}
</script>

<style scoped>
.refund-manage {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 22px;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
