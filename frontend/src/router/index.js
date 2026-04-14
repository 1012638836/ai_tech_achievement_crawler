import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue') },
  { path: '/sources', name: 'Sources', component: () => import('../views/SourceList.vue') },
  { path: '/achievements', name: 'Achievements', component: () => import('../views/AchievementList.vue') },
  { path: '/achievements/:id', name: 'AchievementDetail', component: () => import('../views/AchievementDetail.vue') },
  { path: '/tasks', name: 'Tasks', component: () => import('../views/TaskList.vue') },
  { path: '/links', name: 'Links', component: () => import('../views/LinkList.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
