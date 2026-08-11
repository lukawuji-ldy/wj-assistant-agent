import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && typeof body.code === 'string' && body.code !== 'OK') {
      ElMessage.error({ message: body.message || '请求失败', zIndex: 4000 })
      return Promise.reject(new Error(body.message || body.code))
    }
    return response
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      localStorage.removeItem('admin_token')
      localStorage.removeItem('admin_profile')
      if (router.currentRoute.value.path !== '/login') {
        router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      ElMessage.error({ message: '登录已失效，请重新登录', zIndex: 4000 })
    } else {
      const msg = error?.response?.data?.message || error.message || '网络错误'
      ElMessage.error({ message: msg, zIndex: 4000 })
    }
    return Promise.reject(error)
  },
)

export default http
