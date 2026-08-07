<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

<script>
export default {
  name: 'NotFound'
}
</script>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  gap: 16px;
  background: #f8f9fa;
}

.not-found h1 {
  font-size: 72px;
  margin: 0;
  color: #2c3e50;
}

.not-found p {
  color: #999;
  font-size: 18px;
}
</style>
