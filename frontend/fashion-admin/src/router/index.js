import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
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
    path: '/coupon/template',
    name: 'CouponTemplateList',
    component: () => import('../views/CouponTemplateList.vue')
  },
  {
    path: '/coupon/user',
    name: 'CouponUserList',
    component: () => import('../views/CouponUserList.vue')
  },
  {
    path: '/user/list',
    name: 'UserList',
    component: () => import('../views/UserList.vue')
  },
  {
    path: '/employee/list',
    name: 'EmployeeList',
    component: () => import('../views/EmployeeList.vue')
  },
  {
    path: '/review/list',
    name: 'ReviewList',
    component: () => import('../views/ReviewList.vue')
  },
  {
    path: '/refund/list',
    name: 'RefundList',
    component: () => import('../views/RefundList.vue')
  },
  {
    path: '/es/sync',
    name: 'EsSyncControl',
    component: () => import('../views/EsSyncControl.vue')
  },
  {
    path: '/operationLog/list',
    name: 'OperationLogList',
    component: () => import('../views/OperationLogList.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../views/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() {
    // 每次路由跳转时滚动到页面顶部
    return { top: 0 }
  }
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('admin_token')
  if (!to.meta.public && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
