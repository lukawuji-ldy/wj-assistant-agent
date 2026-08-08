import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/users',
      children: [
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '后台用户' },
        },
        {
          path: 'kb',
          name: 'kb',
          component: () => import('@/views/KbDocumentsView.vue'),
          meta: { title: '知识库' },
        },
        {
          path: 'kb/:docId',
          name: 'kb-detail',
          component: () => import('@/views/KbDocumentDetailView.vue'),
          meta: { title: '知识库详情' },
        },
        {
          path: 'mcp',
          name: 'mcp',
          component: () => import('@/views/McpView.vue'),
          meta: { title: 'MCP 绑定' },
        },
        {
          path: 'memory',
          redirect: '/memory/profile',
        },
        {
          path: 'memory/profile',
          name: 'memory-profile',
          component: () => import('@/views/MemoryProfileView.vue'),
          meta: { title: 'Profile 记忆' },
        },
        {
          path: 'memory/semantic',
          name: 'memory-semantic',
          component: () => import('@/views/MemorySemanticView.vue'),
          meta: { title: 'Semantic 记忆' },
        },
        {
          path: 'llm-configs',
          name: 'llm-configs',
          component: () => import('@/views/LlmConfigsView.vue'),
          meta: { title: 'LLM 配置' },
        },
        {
          path: 'prompts',
          name: 'prompts',
          component: () => import('@/views/PromptsView.vue'),
          meta: { title: '提示词' },
        },
        {
          path: 'logs/llm-calls',
          name: 'llm-calls',
          component: () => import('@/views/LlmCallsView.vue'),
          meta: { title: '调用日志' },
        },
        {
          path: 'logs/checkpoints',
          name: 'checkpoints',
          component: () => import('@/views/CheckpointsView.vue'),
          meta: { title: 'Checkpoint 回放' },
        },
        {
          path: 'logs/audit',
          name: 'audit-logs',
          component: () => import('@/views/AuditLogsView.vue'),
          meta: { title: '操作日志' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLoggedIn && to.path === '/login') {
      return { path: '/users' }
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
