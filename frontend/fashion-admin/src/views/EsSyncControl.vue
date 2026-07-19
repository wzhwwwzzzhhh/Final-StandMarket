<template>
  <div class="es-sync-page">
    <div class="page-header">
      <h2>ES 索引同步管理</h2>
    </div>

    <!-- 状态卡片 -->
    <el-row :gutter="20" class="status-cards">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">集群状态</div>
            <div class="stat-value" :class="healthClass">{{ healthStatus }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">索引文档数</div>
            <div class="stat-value">{{ docCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">集群名称</div>
            <div class="stat-value" style="font-size: 18px">{{ clusterName }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作区 -->
    <el-card class="action-card" shadow="hover">
      <div class="action-area">
        <div class="action-desc">
          <h3>全量重建索引</h3>
          <p>删除现有索引并重新创建，从数据库全量同步所有商品到 ES。操作耗时取决于商品数量。</p>
        </div>
        <el-button
          type="primary"
          size="large"
          :loading="syncing"
          @click="handleSync"
        >
          {{ syncing ? '同步中...' : '开始全量同步' }}
        </el-button>
      </div>
    </el-card>

    <!-- 同步日志 -->
    <el-card v-if="logs.length" class="log-card" shadow="hover">
      <template #header>
        <span>同步日志</span>
      </template>
      <div class="log-list">
        <div v-for="(log, i) in logs" :key="i" class="log-item" :class="log.type">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-msg">{{ log.msg }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

export default {
  name: 'EsSyncControl',
  setup() {
    const loading = ref(false)
    const syncing = ref(false)
    const docCount = ref(0)
    const healthStatus = ref('unknown')
    const clusterName = ref('')
    const logs = ref([])

    const addLog = (msg, type = 'info') => {
      const now = new Date()
      const time = now.toLocaleTimeString()
      logs.value.push({ time, msg, type })
    }

    const healthClass = computed(() => ({
      'status-green': healthStatus.value === 'green',
      'status-yellow': healthStatus.value === 'yellow',
      'status-red': healthStatus.value === 'red',
    }))

    const loadStatus = async () => {
      loading.value = true
      try {
        const resp = await api.get('/admin/es/status')
        if (resp.data.code === 1) {
          const data = resp.data.data
          healthStatus.value = data.status || 'unknown'
          clusterName.value = data.clusterName || ''
          docCount.value = data.docCount ?? '-'
        } else {
          healthStatus.value = 'error'
          addLog('获取状态失败: ' + (resp.data.msg || ''), 'error')
        }
      } catch (e) {
        healthStatus.value = 'error'
        addLog('ES 连接失败: ' + e.message, 'error')
      } finally {
        loading.value = false
      }
    }

    const handleSync = async () => {
      syncing.value = true
      addLog('开始全量重建索引...', 'info')
      try {
        const resp = await api.post('/admin/es/sync')
        if (resp.data.code === 1) {
          addLog('全量同步完成！', 'success')
        } else {
          addLog('同步失败: ' + (resp.data.msg || ''), 'error')
        }
      } catch (e) {
        addLog('同步请求失败: ' + e.message, 'error')
      } finally {
        syncing.value = false
        loadStatus()
      }
    }

    onMounted(() => {
      loadStatus()
    })

    return {
      loading, syncing, docCount, healthStatus, clusterName,
      healthClass, logs, handleSync, addLog
    }
  }
}
</script>

<style scoped>
.es-sync-page {
  padding: 20px;
}

.page-header h2 {
  margin: 0 0 20px;
  font-size: 22px;
  color: #333;
}

.status-cards {
  margin-bottom: 20px;
}

.stat-item {
  text-align: center;
  padding: 10px 0;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.status-green { color: #67c23a; }
.status-yellow { color: #e6a23c; }
.status-red { color: #f56c6c; }

.action-card {
  margin-bottom: 20px;
}

.action-area {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-desc h3 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #333;
}

.action-desc p {
  margin: 0;
  color: #999;
  font-size: 13px;
}

.log-card {
  margin-bottom: 20px;
}

.log-list {
  max-height: 300px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 13px;
}

.log-item {
  padding: 4px 0;
  display: flex;
  gap: 12px;
}

.log-time {
  color: #999;
  flex-shrink: 0;
}

.log-msg {
  color: #333;
}

.log-item.success .log-msg { color: #67c23a; }
.log-item.error .log-msg { color: #f56c6c; }
</style>
