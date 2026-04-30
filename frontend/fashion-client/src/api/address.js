import axios from 'axios';
import router from '../router';

// 创建axios实例
const api = axios.create({
  baseURL: '/api', // 后端API基础路径
  timeout: 10000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json'
  }
});

// 请求拦截器，添加token
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, error => {
  return Promise.reject(error);
});

// 响应拦截器，处理错误
api.interceptors.response.use(response => {
  return response;
}, error => {
  if (error.response && error.response.status === 401) {
    // 未授权，跳转到登录页
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    router.push('/login');
  }
  return Promise.reject(error);
});

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