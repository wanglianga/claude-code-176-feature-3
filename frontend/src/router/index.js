import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: '监管看板', component: () => import('../views/Dashboard.vue'),
        meta: { icon: 'DataLine', roles: ['ADMIN', 'GOV_EDU', 'GOV_HEALTH', 'SCHOOL_DOCTOR', 'TEACHER'] } },
      { path: 'students', name: '学生名单', component: () => import('../views/Students.vue'),
        meta: { icon: 'User', roles: ['ADMIN', 'GOV_EDU', 'GOV_HEALTH', 'SCHOOL_DOCTOR', 'TEACHER', 'SCREEN_ORG', 'HOSPITAL', 'CLINIC'] } },
      { path: 'students/:id', name: '学生健康档案', component: () => import('../views/StudentDetail.vue'),
        meta: { hidden: true } },
      { path: 'screening', name: '现场筛查', component: () => import('../views/Screening.vue'),
        meta: { icon: 'View', roles: ['ADMIN', 'SCREEN_ORG', 'SCHOOL_DOCTOR'] } },
      { path: 'review', name: '复核与干预', component: () => import('../views/Review.vue'),
        meta: { icon: 'CircleCheck', roles: ['ADMIN', 'SCHOOL_DOCTOR', 'TEACHER', 'GOV_EDU', 'GOV_HEALTH'] } },
      { path: 'parent', name: '家长告知确认', component: () => import('../views/Parent.vue'),
        meta: { icon: 'ChatDotRound', roles: ['ADMIN', 'SCHOOL_DOCTOR', 'TEACHER'] } },
      { path: 'visit', name: '复诊处方', component: () => import('../views/Visit.vue'),
        meta: { icon: 'FirstAidKit', roles: ['ADMIN', 'HOSPITAL', 'CLINIC', 'GOV_HEALTH'] } },
      { path: 'subsidy', name: '配镜与补助', component: () => import('../views/Subsidy.vue'),
        meta: { icon: 'Wallet', roles: ['ADMIN', 'GOV_HEALTH', 'GOV_EDU', 'HOSPITAL', 'CLINIC', 'SCHOOL_DOCTOR', 'TEACHER'] } },
      { path: 'verification', name: '门店补助核验', component: () => import('../views/Verification.vue'),
        meta: { icon: 'Stamp', roles: ['ADMIN', 'GOV_HEALTH', 'GOV_EDU', 'HOSPITAL', 'CLINIC'] } },
      { path: 'followup', name: '复查随访', component: () => import('../views/Followup.vue'),
        meta: { icon: 'Calendar', roles: ['ADMIN', 'HOSPITAL', 'CLINIC', 'SCHOOL_DOCTOR', 'TEACHER', 'GOV_HEALTH'] } },
      { path: 'issues', name: '异常事项台账', component: () => import('../views/Issues.vue'),
        meta: { icon: 'Warning', roles: ['ADMIN', 'GOV_EDU', 'GOV_HEALTH', 'SCHOOL_DOCTOR', 'TEACHER', 'SCREEN_ORG', 'HOSPITAL', 'CLINIC'] } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const raw = localStorage.getItem('vision_user')
  const user = raw ? JSON.parse(raw) : null
  if (to.meta.public) return true
  if (!user) return '/login'
  if (to.meta.roles && !to.meta.roles.includes(user.role)) return '/dashboard'
  return true
})

export default router
