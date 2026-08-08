import http, { type ApiResponse } from './http'

export interface AdminProfileView {
  memoryId: string
  userId: string
  memoryType: string
  memoryKey: string
  memoryValue: string
  status: string
  confidence: number
  importance: number
  source: string
  version: number
  expireTime?: string | null
  lastUsedTime?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface AdminProfilePage {
  items: AdminProfileView[]
  total: number
  page: number
  size: number
}

export interface AdminSemanticView {
  id: string
  userId: string
  content: string
  memoryType: string
  status: string
  importance: number
  confidence: number
  tags: string[]
  source: string
  sourceMessageId?: string | null
  expireTime?: string | null
  lastUsedTime?: string | null
  createTime?: string | null
  updateTime?: string | null
  score?: number | null
}

export interface AdminSemanticPage {
  items: AdminSemanticView[]
  total: number
  page: number
  size: number
}

export async function listProfiles(params: {
  userId?: string
  memoryKey?: string
  memoryType?: string
  status?: string
  createTimeFrom?: string
  createTimeTo?: string
  page?: number
  size?: number
}) {
  const { data } = await http.get<ApiResponse<AdminProfilePage>>('/api/admin/memory/profiles', {
    params,
  })
  return data.data
}

export async function createProfile(body: {
  userId: string
  memoryType: string
  memoryKey: string
  memoryValue: string
  confidence?: number
  importance?: number
}) {
  const { data } = await http.post<ApiResponse<AdminProfileView>>('/api/admin/memory/profiles', body)
  return data.data
}

export async function updateProfile(
  memoryId: string,
  body: {
    memoryType?: string
    memoryKey?: string
    memoryValue?: string
    status?: string
    confidence?: number
    importance?: number
  },
) {
  const { data } = await http.put<ApiResponse<AdminProfileView>>(
    `/api/admin/memory/profiles/${encodeURIComponent(memoryId)}`,
    body,
  )
  return data.data
}

export async function deleteProfile(memoryId: string) {
  await http.delete<ApiResponse<null>>(`/api/admin/memory/profiles/${encodeURIComponent(memoryId)}`)
}

export async function listSemantics(params: {
  userId?: string
  status?: string
  keyword?: string
  similarQuery?: string
  createTimeFrom?: string
  createTimeTo?: string
  page?: number
  size?: number
}) {
  const { data } = await http.get<ApiResponse<AdminSemanticPage>>('/api/admin/memory/semantics', {
    params,
  })
  return data.data
}

export async function updateSemantic(
  id: string,
  body: {
    content?: string
    status?: string
    importance?: number
    confidence?: number
    tags?: string[]
  },
) {
  const { data } = await http.put<ApiResponse<AdminSemanticView>>(
    `/api/admin/memory/semantics/${encodeURIComponent(id)}`,
    body,
  )
  return data.data
}

export async function deleteSemantic(id: string) {
  await http.delete<ApiResponse<null>>(`/api/admin/memory/semantics/${encodeURIComponent(id)}`)
}
