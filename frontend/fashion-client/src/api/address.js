import api from '@/utils/request';

const addressApi = {
  // 添加地址
  addAddress: (address, config) => {
    return api.post('/user/address', address, config);
  },
  
  // 删除地址
  deleteAddress: (id, config) => {
    return api.delete(`/user/address/${id}`, config);
  },
  
  // 更新地址
  updateAddress: (address, config) => {
    return api.put('/user/address', address, config);
  },
  
  // 根据ID查询地址
  getAddressById: (id, config) => {
    return api.get(`/user/address/${id}`, config);
  },
  
  // 获取地址列表
  getAddressList: (config) => {
    return api.get('/user/address/list', config);
  },
  
  // 获取默认地址
  getDefaultAddress: (config) => {
    return api.get('/user/address/default', config);
  },
  
  // 设置默认地址
  setDefaultAddress: (id, config) => {
    return api.put(`/user/address/default/${id}`, null, config);
  }
};

export default addressApi;
