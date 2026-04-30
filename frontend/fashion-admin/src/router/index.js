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
    path: '/product/add',
    name: 'AddProduct',
    component: () => import('../views/AddProduct.vue')
  },
  {
    path: '/product/edit/:id',
    name: 'EditProduct',
    component: () => import('../views/AddProduct.vue')
  },
  {
    path: '/category',
    name: 'Category',
    component: () => import('../views/Category.vue')
  },
  {
    path: '/order/list',
    name: 'OrderList',
    component: () => import('../views/OrderList.vue')
  },
  {
    path: '/seckill/activity',
    name: 'SeckillActivity',
    component: () => import('../views/SeckillActivity.vue')
  },
  {
    path: '/seckill/coupon',
    name: 'SeckillCoupon',
    component: () => import('../views/SeckillCoupon.vue')
  },
  {
    path: '/seckill/orders',
    name: 'SeckillOrderList',
    component: () => import('../views/SeckillOrderList.vue')
  },
  {
    path: '/seckill/offer',
    name: 'SpecialOffer',
    component: () => import('../views/SpecialOffer.vue')
  },
  {
    path: '/user/list',
    name: 'UserList',
    component: () => import('../views/UserList.vue')
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
