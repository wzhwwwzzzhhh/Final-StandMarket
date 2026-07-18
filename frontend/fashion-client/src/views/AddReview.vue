<template>
  <div class="add-review">
    <div class="top-nav">
      <el-button type="text" @click="goBack" class="back-button">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <h1 class="page-title">商品评价</h1>
    </div>

    <div v-loading="loading" class="review-form">
      <!-- 商品信息 -->
      <div class="product-info" v-if="product">
        <img :src="product.image" :alt="product.name" class="product-image" />
        <div class="product-detail">
          <div class="product-name">{{ product.name }}</div>
          <div class="product-price">¥{{ product.price }}</div>
        </div>
      </div>

      <el-divider />

      <!-- 评分 -->
      <div class="form-section">
        <div class="section-label">商品评分</div>
        <el-rate v-model="form.rating" :max="5" :colors="['#ff4d4f', '#ff7a45', '#ffd93d', '#73d13d', '#52c41a']" show-text />
      </div>

      <!-- 评价内容 -->
      <div class="form-section">
        <div class="section-label">评价内容</div>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="4"
          placeholder="分享您的使用感受，帮助其他小伙伴参考~"
          maxlength="500"
          show-word-limit
        />
      </div>

      <!-- 图片上传 -->
      <div class="form-section">
        <div class="section-label">晒图片（选填）</div>
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          list-type="picture-card"
          :multiple="true"
          :limit="5"
          :on-success="handleUploadSuccess"
          :on-remove="handleUploadRemove"
          :before-upload="beforeUpload"
        >
          <el-icon><Plus /></el-icon>
        </el-upload>
      </div>

      <!-- 提交按钮 -->
      <div class="submit-section">
        <el-button type="primary" size="large" class="submit-btn" :loading="submitting" @click="handleSubmit">
          提交评价
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { productApi } from '@/api/product'
import reviewApi from '@/api/review'

export default {
  name: 'AddReview',
  components: { ArrowLeft, Plus },
  data() {
    return {
      loading: true,
      submitting: false,
      product: null,
      form: {
        orderId: this.$route.params.orderId ? parseInt(this.$route.params.orderId) : null,
        productId: this.$route.params.productId ? parseInt(this.$route.params.productId) : null,
        rating: 5,
        content: '',
        images: ''
      },
      imageList: []
    }
  },
  computed: {
    uploadUrl() {
      return '/api/common/upload'
    },
    uploadHeaders() {
      const token = localStorage.getItem('token')
      return token ? { 'Authorization': `Bearer ${token}` } : {}
    }
  },
  created() {
    this.loadProduct()
    this.checkReviewed()
  },
  methods: {
    goBack() {
      this.$router.back()
    },
    loadProduct() {
      productApi.getProductById(this.$route.params.productId).then(response => {
        if (response.data.code === 1) {
          this.product = response.data.data
        }
      }).finally(() => {
        this.loading = false
      })
    },
    checkReviewed() {
      reviewApi.check(this.$route.params.orderId).then(response => {
        if (response.data.code === 1 && response.data.data.reviewed) {
          this.$message.warning('该订单已评价')
          this.$router.back()
        }
      })
    },
    handleUploadSuccess(response) {
      if (response.code === 1 && response.data) {
        this.imageList.push(response.data)
      }
    },
    handleUploadRemove(file) {
      const url = file.response?.data || file.url
      this.imageList = this.imageList.filter(img => img !== url)
    },
    beforeUpload(file) {
      const isImage = file.type.startsWith('image/')
      const isLt5M = file.size / 1024 / 1024 < 5
      if (!isImage) {
        this.$message.error('只能上传图片文件')
        return false
      }
      if (!isLt5M) {
        this.$message.error('图片大小不能超过 5MB')
        return false
      }
      return true
    },
    handleSubmit() {
      if (!this.form.content.trim() && this.imageList.length === 0) {
        this.$message.warning('请填写评价内容或上传图片')
        return
      }
      this.submitting = true
      this.form.images = this.imageList.length > 0 ? JSON.stringify(this.imageList) : ''
      reviewApi.add(this.form).then(response => {
        if (response.data.code === 1) {
          this.$message.success('评价提交成功')
          this.$router.push('/order')
        } else {
          this.$message.error(response.data.msg || '提交失败')
        }
      }).catch(() => {
        this.$message.error('提交失败，请稍后重试')
      }).finally(() => {
        this.submitting = false
      })
    }
  }
}
</script>

<style scoped>
.add-review {
  min-height: 100vh;
  background: #f5f5f5;
}

.top-nav {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #eee;
  position: sticky;
  top: 0;
  z-index: 10;
}

.back-button {
  padding: 4px 8px;
  margin-right: 8px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.review-form {
  max-width: 600px;
  margin: 0 auto;
  padding: 16px;
}

.product-info {
  display: flex;
  align-items: center;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  gap: 12px;
}

.product-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
}

.product-detail {
  flex: 1;
}

.product-name {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
}

.product-price {
  color: #ff4d4f;
  font-size: 16px;
  font-weight: 600;
}

.form-section {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  margin-top: 12px;
}

.section-label {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 12px;
  color: #333;
}

.submit-section {
  margin-top: 24px;
  padding: 0 16px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}
</style>
