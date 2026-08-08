import http, { type ApiResponse } from './http'

export interface AdminUserView {
  adminId: string
  username: string
  displayName: string
  role: string
  status: string
  builtin: boolean
}

export interface AdminUserPage {
  items: AdminUserView[]
  total: number
  page: number
  size: number
}

export async function listUsers(page = 1, size = 20) {
  const { data } = await http.get<ApiResponse<AdminUserPage>>('/api/admin/users', {
    params: { page, size },
  })
  return data.data
}

export async function createUser(body: {
  username: string
  password: string
  displayName: string
  role: string
}) {
  const { data } = await http.post<ApiResponse<AdminUserView>>('/api/admin/users', body)
  return data.data
}

export async function updateUser(
  adminId: string,
  body: { displayName?: string; role?: string; status?: string },
) {
  const { data } = await http.put<ApiResponse<AdminUserView>>(`/api/admin/users/${adminId}`, body)
  return data.data
}

export async function changePassword(adminId: string, newPassword: string) {
  await http.put<ApiResponse<null>>(`/api/admin/users/${adminId}/password`, { newPassword })
}

export async function deleteUser(adminId: string) {
  await http.delete<ApiResponse<null>>(`/api/admin/users/${adminId}`)
}
