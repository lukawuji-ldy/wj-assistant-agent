<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">无忌运营台</div>
      <el-menu :default-active="active" router background-color="#111827" text-color="#e5e7eb" active-text-color="#93c5fd">
        <el-menu-item index="/users">后台用户</el-menu-item>
        <el-menu-item index="/llm-configs">LLM 配置</el-menu-item>
        <el-menu-item index="/prompts">提示词</el-menu-item>
        <el-menu-item index="/logs/llm-calls">调用日志</el-menu-item>
        <el-menu-item index="/logs/checkpoints">Checkpoint</el-menu-item>
        <el-menu-item index="/logs/audit">操作日志</el-menu-item>
        <el-menu-item index="/kb">知识库</el-menu-item>
        <el-sub-menu index="/memory">
          <template #title>用户记忆</template>
          <el-menu-item index="/memory/profile">Profile</el-menu-item>
          <el-menu-item index="/memory/semantic">Semantic</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/mcp">MCP 绑定</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="title">{{ title }}</div>
        <div class="header-right">
          <span class="user">{{ auth.displayLabel }}（{{ auth.profile?.role }}）</span>
          <el-button type="primary" link @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => {
  const p = route.path
  if (p.startsWith('/kb')) return '/kb'
  if (p.startsWith('/memory/profile')) return '/memory/profile'
  if (p.startsWith('/memory/semantic')) return '/memory/semantic'
  if (p.startsWith('/logs/llm-calls')) return '/logs/llm-calls'
  if (p.startsWith('/logs/checkpoints')) return '/logs/checkpoints'
  if (p.startsWith('/logs/audit')) return '/logs/audit'
  return p
})
const title = computed(() => (route.meta.title as string) || '运营后台')

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #111827;
  color: #fff;
}
.brand {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  font-weight: 700;
  letter-spacing: 0.02em;
  border-bottom: 1px solid #1f2937;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.user {
  color: #6b7280;
  font-size: 13px;
}
.main {
  background: #f3f4f6;
}
</style>
