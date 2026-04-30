<template>
  <div class="add-product">
    <h2>{{ isEdit ? '编辑商品' : '添加商品' }}</h2>
    <el-form :model="productForm" label-width="100px">
      <el-form-item label="商品名称">
        <el-input v-model="productForm.name" placeholder="请输入商品名称"></el-input>
      </el-form-item>
      <el-form-item label="商品分类">
        <el-select v-model="productForm.categoryId" placeholder="请选择分类">
          <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="商品价格">
        <el-input v-model="productForm.price" placeholder="请输入商品价格"></el-input>
      </el-form-item>
      <el-form-item label="商品库存">
        <el-input v-model="productForm.stock" placeholder="请输入商品库存"></el-input>
      </el-form-item>
      <el-form-item label="商品描述">
        <el-input type="textarea" v-model="productForm.description" placeholder="请输入商品描述"></el-input>
      </el-form-item>
      <el-form-item label="商品图片">
        <file-upload v-model="productForm.image" @upload-success="handleUploadSuccess" @upload-error="handleUploadError" />
      </el-form-item>
      <el-form-item label="商品标签">
        <el-select v-model="productForm.tag" placeholder="请选择标签">
          <el-option label="衣服" value="衣服"></el-option>
          <el-option label="裤子" value="裤子"></el-option>
          <el-option label="鞋子" value="鞋子"></el-option>
          <el-option label="配饰" value="配饰"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitForm">{{ isEdit ? '更新' : '提交' }}</el-button>
        <el-button @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import { productApi, categoryApi } from '../api/product'
import FileUpload from '../components/FileUpload.vue'

export default {
  name: 'AddProduct',
  components: {
    FileUpload
  },
  data() {
    return {
      isEdit: false,
      productId: '',
      categories: [],
      productForm: {
        name: '',
        categoryId: '',
        price: '',
        stock: '',
        description: '',
        image: '',
        tag: ''
      }
    }
  },
  created() {
    // 获取分类列表
    this.getCategoryList()
    
    // 检查是否是编辑模式
    const id = this.$route.params.id
    if (id) {
      this.isEdit = true
      this.productId = id
      this.getProductDetail()
    }
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
    
    // 获取商品详情
    getProductDetail() {
      productApi.getProductById(this.productId).then(response => {
        this.productForm = response.data.data
      }).catch(error => {
        console.error('获取商品详情失败:', error)
        this.$message.error('获取商品详情失败')
      })
    },
    
    // 处理上传成功
    handleUploadSuccess(url) {
      console.log('图片上传成功:', url)
      this.productForm.image = url
    },
    
    // 处理上传失败
    handleUploadError(error) {
      console.error('图片上传失败:', error)
      this.$message.error('图片上传失败')
    },
    
    // 提交表单
    submitForm() {
      if (this.isEdit) {
        // 更新商品
        productApi.updateProduct(this.productId, this.productForm).then(response => {
          this.$message.success('商品更新成功')
          this.$router.push('/product/list')
        }).catch(error => {
          console.error('更新商品失败:', error)
          this.$message.error('更新商品失败')
        })
      } else {
        // 添加商品
        productApi.addProduct(this.productForm).then(response => {
          this.$message.success('商品添加成功')
          this.$router.push('/product/list')
        }).catch(error => {
          console.error('添加商品失败:', error)
          this.$message.error('添加商品失败')
        })
      }
    },
    
    // 重置表单
    resetForm() {
      if (this.isEdit) {
        this.getProductDetail()
      } else {
        this.productForm = {
          name: '',
          categoryId: '',
          price: '',
          stock: '',
          description: '',
          image: '',
          tag: ''
        }
      }
    }
  }
}
</script>

<style scoped>
.add-product {
  padding: 20px;
  max-width: 800px;
}
</style>
