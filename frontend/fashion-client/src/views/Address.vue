<template>
  <div class="address-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">
        <span>地址管理</span>
        <el-divider direction="horizontal"></el-divider>
      </h2>
    </div>

    <!-- 地址列表卡片 -->
    <div class="address-card">
      <!-- 卡片头部 -->
      <div class="card-header">
        <h3 class="card-title">我的收货地址</h3>
        <el-button type="primary" @click="openAddDialog" class="add-button">
          <el-icon><Plus /></el-icon>
          <span>添加地址</span>
        </el-button>
      </div>

      <!-- 空地址状态 -->
      <div v-if="addressList.length === 0" class="empty-address">
        <el-empty description="暂无地址，快去添加吧！">
          <el-button type="primary" @click="openAddDialog" class="add-first-button">
            <el-icon><Plus /></el-icon>
            <span>添加首个地址</span>
          </el-button>
        </el-empty>
      </div>

      <!-- 地址列表 -->
      <div v-else class="address-list">
        <div 
          v-for="(address, index) in addressList" 
          :key="address.id" 
          class="address-item"
          :class="{ 'default-address': address.isDefault === 1 }"
          :style="{ animationDelay: `${index * 0.1}s` }"
        >
          <!-- 地址信息 -->
          <div class="address-info">
            <div class="address-header">
              <div class="contact-info">
                <span class="consignee">{{ address.consignee }}</span>
                <span class="phone">{{ address.phone }}</span>
              </div>
              <span v-if="address.isDefault === 1" class="default-tag">
                <el-icon><Star /></el-icon>
                <span>默认</span>
              </span>
            </div>
            <div class="address-detail">
              {{ address.provinceName }}{{ address.cityName }}{{ address.districtName }}{{ address.detail }}
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="address-actions">
            <el-button 
              type="primary" 
              size="small" 
              @click="openEditDialog(address)"
              class="edit-button"
            >
              <el-icon><Edit /></el-icon>
              <span>编辑</span>
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="deleteAddress(address.id)"
              class="delete-button"
            >
              <el-icon><Delete /></el-icon>
              <span>删除</span>
            </el-button>
            <el-button 
              v-if="address.isDefault !== 1" 
              type="info" 
              size="small" 
              @click="setDefault(address.id)"
              class="default-button"
            >
              <el-icon><Top /></el-icon>
              <span>设为默认</span>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加/编辑地址对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="500px"
      class="address-dialog"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" class="address-form">
        <el-form-item label="收货人" prop="consignee">
          <el-input v-model="form.consignee" placeholder="请输入收货人姓名" class="custom-input" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" class="custom-input" />
        </el-form-item>
        <el-form-item label="地区" prop="districtName">
          <el-cascader
            v-model="regionValue"
            :options="regionData"
            :props="{
              value: 'value',
              label: 'label',
              children: 'children'
            }"
            placeholder="请选择省市区"
            @change="handleRegionChange"
            clearable
            class="custom-cascader"
          />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input v-model="form.detail" placeholder="请输入详细地址" class="custom-input" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.isDefault" class="default-checkbox">
            <span>设为默认地址</span>
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" class="cancel-button">取消</el-button>
          <el-button type="primary" @click="saveAddress" class="save-button">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, reactive, computed, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Edit, Delete, Top, Star } from '@element-plus/icons-vue';
import { useRouter, useRoute } from 'vue-router';
import addressApi from '../api/address';
import regionData from '../utils/regionData';

export default {
  name: 'Address',
  setup() {
    const router = useRouter();
    const route = useRoute();
    const addressList = ref([]);
    const dialogVisible = ref(false);
    const form = reactive({
      id: null,
      consignee: '',
      sex: '',
      phone: '',
      provinceCode: '',
      provinceName: '',
      cityCode: '',
      cityName: '',
      districtCode: '',
      districtName: '',
      detail: '',
      label: '',
      isDefault: 0
    });
    const regionValue = ref([]);
    const rules = {
      consignee: [
        { required: true, message: '请输入收货人姓名', trigger: 'blur' }
      ],
      phone: [
        { required: true, message: '请输入手机号', trigger: 'blur' }
      ],
      districtName: [
        { required: true, message: '请选择省市区', trigger: 'change' }
      ],
      detail: [
        { required: true, message: '请输入详细地址', trigger: 'blur' }
      ]
    };
    const formRef = ref(null);

    const dialogTitle = computed(() => {
      return form.id ? '编辑地址' : '添加地址';
    });

    // 加载地址列表
    const loadAddressList = async () => {
      try {
        const response = await addressApi.getAddressList();
        console.log('加载地址列表的响应:', response);
        if (response && response.data && response.data.code === 1) {
          addressList.value = response.data.data || [];
        } else if (response && response.data && response.data.message) {
          ElMessage.error(response.data.message);
        }
      } catch (error) {
        console.error('Failed to load address list:', error);
      }
    };

    // 打开添加地址对话框
    const openAddDialog = () => {
      form.id = null;
      form.consignee = '';
      form.sex = '';
      form.phone = '';
      form.provinceCode = '';
      form.provinceName = '';
      form.cityCode = '';
      form.cityName = '';
      form.districtCode = '';
      form.districtName = '';
      form.detail = '';
      form.label = '';
      form.isDefault = 0;
      regionValue.value = [];
      dialogVisible.value = true;
    };

    // 处理地区选择变化
    const handleRegionChange = (value) => {
      if (value && value.length === 3) {
        // 查找对应的省市区名称
        const province = regionData.find(item => item.value === value[0]);
        const city = province ? province.children.find(item => item.value === value[1]) : null;
        const district = city ? city.children.find(item => item.value === value[2]) : null;
        
        form.provinceCode = value[0];
        form.provinceName = province ? province.label : '';
        form.cityCode = value[1];
        form.cityName = city ? city.label : '';
        form.districtCode = value[2];
        form.districtName = district ? district.label : '';
      } else {
        form.provinceCode = '';
        form.provinceName = '';
        form.cityCode = '';
        form.cityName = '';
        form.districtCode = '';
        form.districtName = '';
      }
    };

    // 打开编辑地址对话框
    const openEditDialog = (address) => {
      Object.assign(form, address);
      form.isDefault = form.isDefault || 0;
      
      // 设置地区选择器的值
      if (address.provinceCode && address.cityCode && address.districtCode) {
        regionValue.value = [address.provinceCode, address.cityCode, address.districtCode];
      } else {
        regionValue.value = [];
      }
      
      dialogVisible.value = true;
    };

    // 保存地址
    const saveAddress = async () => {
      try {
        await formRef.value.validate();
        // 将isDefault从布尔值转换为整数
        form.isDefault = form.isDefault ? 1 : 0;
        console.log('保存地址前的表单数据:', form);
        let response;
        if (form.id) {
          console.log('更新地址');
          response = await addressApi.updateAddress(form);
        } else {
          console.log('添加地址');
          response = await addressApi.addAddress(form);
        }
        console.log('保存地址的响应:', response);
        if (response && response.data && response.data.code === 1) {
          ElMessage.success(form.id ? '地址更新成功' : '地址添加成功');
          dialogVisible.value = false;
          loadAddressList();
          
          // 检查是否有返回地址
          const returnTo = route.query.returnTo;
          if (returnTo) {
            // 延迟跳转，确保用户看到成功提示
            setTimeout(() => {
              router.push(returnTo);
            }, 1000);
          }
        } else {
          ElMessage.error(response?.data?.message || '保存地址失败');
        }
      } catch (error) {
        ElMessage.error('保存地址失败');
        console.error('Failed to save address:', error);
      }
    };

    // 删除地址
    const deleteAddress = async (id) => {
      try {
        await ElMessageBox.confirm('确定要删除这个地址吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        });
        const response = await addressApi.deleteAddress(id);
        console.log('删除地址的响应:', response);
        if (response && response.data && response.data.code === 1) {
          ElMessage.success('地址删除成功');
          loadAddressList();
        } else {
          ElMessage.error(response?.data?.message || '删除地址失败');
        }
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除地址失败');
          console.error('Failed to delete address:', error);
        }
      }
    };

    // 设置默认地址
    const setDefault = async (id) => {
      try {
        const response = await addressApi.setDefaultAddress(id);
        console.log('设置默认地址的响应:', response);
        if (response && response.data && response.data.code === 1) {
          ElMessage.success('默认地址设置成功');
          loadAddressList();
        } else {
          ElMessage.error(response?.data?.message || '设置默认地址失败');
        }
      } catch (error) {
        ElMessage.error('设置默认地址失败');
        console.error('Failed to set default address:', error);
      }
    };

    onMounted(() => {
      loadAddressList();
    });

    return {
      addressList,
      dialogVisible,
      form,
      rules,
      formRef,
      dialogTitle,
      regionValue,
      regionData,
      openAddDialog,
      openEditDialog,
      handleRegionChange,
      saveAddress,
      deleteAddress,
      setDefault
    };
  }
};
</script>

<style scoped>
/* 全局样式 */
.address-container {
  min-height: 100vh;
  padding: 30px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  font-family: 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
}

/* 页面标题 */
.page-header {
  max-width: 800px;
  margin: 0 auto 30px;
  text-align: center;
}

.page-title {
  font-size: 28px;
  font-weight: bold;
  color: white;
  margin: 0;
  position: relative;
  display: inline-block;
}

.page-title span {
  position: relative;
  z-index: 2;
}

.page-title::after {
  content: '';
  position: absolute;
  bottom: -10px;
  left: 50%;
  transform: translateX(-50%);
  width: 120px;
  height: 4px;
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border-radius: 2px;
  z-index: 1;
}

/* 地址卡片 */
.address-card {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  animation: fadeIn 0.5s ease-out;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 25px 30px;
  background: linear-gradient(90deg, #f5f7fa 0%, #c3cfe2 100%);
  border-bottom: 1px solid #e8e8e8;
}

.card-title {
  font-size: 18px;
  font-weight: bold;
  color: #333;
  margin: 0;
}

.add-button {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  border-radius: 25px;
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.add-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(79, 172, 254, 0.4);
}

/* 空地址状态 */
.empty-address {
  padding: 80px 0;
  text-align: center;
}

.add-first-button {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  border-radius: 25px;
  padding: 10px 30px;
  font-size: 16px;
  font-weight: 500;
  margin-top: 20px;
  transition: all 0.3s ease;
}

.add-first-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 5px 15px rgba(79, 172, 254, 0.4);
}

/* 地址列表 */
.address-list {
  padding: 20px 30px 30px;
}

/* 地址项 */
.address-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  margin-bottom: 15px;
  transition: all 0.3s ease;
  background: white;
  animation: slideInUp 0.5s ease-out;
  animation-fill-mode: both;
  opacity: 0;
  transform: translateY(20px);
}

.address-item:hover {
  box-shadow: 0 5px 20px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.default-address {
  border-color: #409eff;
  background: linear-gradient(135deg, #f0f9ff 0%, #ecf5ff 100%);
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.2);
}

/* 地址信息 */
.address-info {
  flex: 1;
  margin-right: 20px;
}

.address-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  background: none;
  border-bottom: none;
  padding: 0;
}

.contact-info {
  display: flex;
  align-items: center;
}

.consignee {
  font-weight: bold;
  font-size: 16px;
  color: #333;
  margin-right: 20px;
}

.phone {
  font-size: 14px;
  color: #666;
}

.default-tag {
  display: flex;
  align-items: center;
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  color: white;
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 15px;
  font-weight: 500;
}

.default-tag el-icon {
  margin-right: 4px;
  font-size: 12px;
}

.address-detail {
  color: #666;
  line-height: 1.6;
  font-size: 14px;
  white-space: pre-line;
  word-break: break-all;
}

/* 操作按钮 */
.address-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.edit-button, .delete-button, .default-button {
  border-radius: 20px;
  padding: 6px 16px;
  font-size: 12px;
  font-weight: 500;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 80px;
}

.edit-button {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
}

.edit-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(79, 172, 254, 0.4);
}

.delete-button {
  background: linear-gradient(90deg, #ff6b6b 0%, #ee5a6f 100%);
  border: none;
}

.delete-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(255, 107, 107, 0.4);
}

.default-button {
  background: linear-gradient(90deg, #43e97b 0%, #38f9d7 100%);
  border: none;
  color: white;
}

.default-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(67, 233, 123, 0.4);
}

.edit-button el-icon, .delete-button el-icon, .default-button el-icon {
  margin-right: 4px;
  font-size: 12px;
}

/* 对话框 */
.address-dialog {
  border-radius: 16px;
  overflow: hidden;
}

.address-dialog .el-dialog__header {
  background: linear-gradient(90deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 20px 24px;
  border-bottom: 1px solid #e8e8e8;
}

.address-dialog .el-dialog__title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.address-form {
  padding: 20px 0;
}

.custom-input, .custom-cascader {
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  transition: all 0.3s ease;
}

.custom-input:focus, .custom-cascader:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.default-checkbox {
  font-size: 14px;
  color: #666;
}

.dialog-footer {
  text-align: right;
  padding: 10px 24px 20px;
  background: #f9f9f9;
  border-top: 1px solid #e8e8e8;
}

.cancel-button, .save-button {
  border-radius: 20px;
  padding: 6px 20px;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.save-button {
  background: linear-gradient(90deg, #4facfe 0%, #00f2fe 100%);
  border: none;
  margin-left: 10px;
}

.save-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 10px rgba(79, 172, 254, 0.4);
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .address-container {
    padding: 20px 15px;
  }

  .page-title {
    font-size: 24px;
  }

  .card-header {
    padding: 20px 20px;
  }

  .address-list {
    padding: 15px 20px 20px;
  }

  .address-item {
    flex-direction: column;
    align-items: flex-start;
    padding: 15px;
  }

  .address-info {
    margin-right: 0;
    margin-bottom: 15px;
    width: 100%;
  }

  .address-actions {
    flex-direction: row;
    width: 100%;
    justify-content: flex-end;
  }

  .edit-button, .delete-button, .default-button {
    min-width: 70px;
    padding: 4px 12px;
  }

  .address-dialog {
    width: 90% !important;
  }
}
</style>