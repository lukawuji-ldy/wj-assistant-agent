import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('admin_token'))
  const profile = ref<authApi.AdminAuthUser | null>(readProfile())

  const isLoggedIn = computed(() => Boolean(token.value))
  const displayLabel = computed(() => profile.value?.username || '管理员')

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    token.value = res.accessToken
    localStorage.setItem('admin_token', res.accessToken)
    profile.value = {
      adminId: res.adminId,
      username: res.username,
      role: res.role,
      tokenType: 'ADMIN',
    }
    localStorage.setItem('admin_profile', JSON.stringify(profile.value))
  }

  async function loadMe() {
    if (!token.value) {
      return
    }
    const me = await authApi.fetchMe()
    profile.value = me
    localStorage.setItem('admin_profile', JSON.stringify(me))
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // ignore
    }
    token.value = null
    profile.value = null
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_profile')
  }

  return { token, profile, isLoggedIn, displayLabel, login, loadMe, logout }
})

function readProfile(): authApi.AdminAuthUser | null {
  const raw = localStorage.getItem('admin_profile')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as authApi.AdminAuthUser
  } catch {
    return null
  }
}
