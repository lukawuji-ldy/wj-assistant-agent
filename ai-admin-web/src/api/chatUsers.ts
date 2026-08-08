import http, { type ApiResponse } from './http'

export interface AdminChatUserView {
  userId: string
  username: string
  nickname: string
  status: string
}

export interface AdminChatUserPage {
  items: AdminChatUserView[]
  total: number
  page: number
  size: number
}

export async function listChatUsers(keyword = '', page = 1, size = 50) {
  const { data } = await http.get<ApiResponse<AdminChatUserPage>>('/api/admin/chat-users', {
    params: { keyword: keyword || undefined, page, size },
  })
  return data.data
}
