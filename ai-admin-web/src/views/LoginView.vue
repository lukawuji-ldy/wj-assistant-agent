<template>
  <div class="login-page">
    <el-card class="login-card" shadow="hover">
      <h1>无忌助手 · 运营后台</h1>
      <p class="hint">使用 admin_user 账号登录（与聊天用户隔离）</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" native-type="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: '',
})

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/users'
    await router.replace(redirect)
  } catch {
    // http interceptor already toasts
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background:
    radial-gradient(circle at 20% 20%, #dbeafe 0, transparent 40%),
    radial-gradient(circle at 80% 0%, #e0e7ff 0, transparent 35%),
    linear-gradient(160deg, #f8fafc, #eef2ff);
}
.login-card {
  width: 380px;
  padding: 8px 4px 4px;
}
h1 {
  margin: 0 0 8px;
  font-size: 22px;
}
.hint {
  margin: 0 0 20px;
  color: #6b7280;
  font-size: 13px;
}
.submit {
  width: 100%;
}
</style>
