import http, { type ApiResponse } from './http'

export interface AdminLoginRequest {
  username: string
  password: string
}

export interface AdminLoginResponse {
  accessToken: string
  tokenType: string
  adminId: string
  username: string
  displayName: string
  role: string
}

export interface AdminAuthUser {
  adminId: string
  username: string
  role: string
  tokenType: string
}

export async function login(payload: AdminLoginRequest) {
  const { data } = await http.post<ApiResponse<AdminLoginResponse>>('/api/admin/auth/login', payload)
  return data.data
}

export async function fetchMe() {
  const { data } = await http.get<ApiResponse<AdminAuthUser>>('/api/admin/auth/me')
  return data.data
}

export async function logout() {
  await http.post<ApiResponse<null>>('/api/admin/auth/logout')
}
