import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/product/list',
    name: 'ProductList',
    component: () => import('../views/ProductList.vue')
  },
  {
    path: '/product/detail/:id',
    name: 'ProductDetail',
    component: () => import('../views/ProductDetail.vue')
  },
  {
    path: '/seckill',
    name: 'Seckill',
    component: () => import('../views/Seckill.vue')
  },
  {
    path: '/cart',
    name: 'ShoppingCart',
    component: () => import('../views/ShoppingCart.vue')
  },
  {
    path: '/order',
    name: 'Order',
    component: () => import('../views/Order.vue')
  },
  {
    path: '/seckill-order',
    name: 'SeckillOrder',
    component: () => import('../views/SeckillOrder.vue')
  },
  {
    path: '/create-order',
    name: 'CreateOrder',
    component: () => import('../views/CreateOrder.vue')
  },
  {
    path: '/my-coupons',
    name: 'MyCoupons',
    component: () => import('../views/MyCoupons.vue')
  },
  {
    path: '/address',
    name: 'Address',
    component: () => import('../views/Address.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('../views/Profile.vue')
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue')
  },
  {
    path: '/profile/edit',
    name: 'ProfileEdit',
    component: () => import('../views/ProfileEdit.vue')
  },
  {
    path: '/upload',
    name: 'UploadTest',
    component: () => import('../views/UploadTest.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    // 每次路由跳转时滚动到页面顶部
    return { top: 0 }
  }
})

export default router
