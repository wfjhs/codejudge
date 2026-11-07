import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/problems' },
  { path: '/login', component: () => import('@/views/Login.vue') },
  { path: '/register', component: () => import('@/views/Register.vue') },
  {
    path: '/',
    component: () => import('@/components/AppLayout.vue'),
    children: [
      { path: 'problems', component: () => import('@/views/ProblemList.vue') },
      { path: 'problems/:id', component: () => import('@/views/ProblemDetail.vue') },
      { path: 'submissions', component: () => import('@/views/Submissions.vue') },
      { path: 'leaderboard', component: () => import('@/views/Leaderboard.vue') },
      { path: 'admin/problems', component: () => import('@/views/AdminProblems.vue'), meta: { admin: true } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/problems' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫：未登录跳登录；admin 页面校验角色
router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!auth.token) {
    if (to.path === '/login' || to.path === '/register') return true;
    return '/login';
  }
  if (to.meta.admin && auth.user?.role !== 1) {
    return '/problems';
  }
  return true;
});

export default router;
