import http, { type ApiResponse } from './http'

export interface AdminPromptSummary {
  code: string
  name: string
  role: string
  publishedVersion: number | null
  draftVersion: number | null
  hasDraft: boolean
  status: string | null  // ACTIVE|DISABLED of main row
  latestVersion: number
}

export interface AdminPromptVersionView {
  id: number
  code: string
  name: string
  role: string
  content: string
  version: number
  status: string  // DRAFT|PUBLISHED|SUPERSEDED
  changeNote: string | null
  createdBy: string
  createTime: string | null  // ISO instant
  publishTime: string | null
}

export interface AdminPromptDiffView {
  code: string
  from: AdminPromptVersionView
  to: AdminPromptVersionView
}

export async function listPromptSummaries() {
  const { data } = await http.get<ApiResponse<AdminPromptSummary[]>>('/api/admin/prompts')
  return data.data
}

export async function listPromptVersions(code: string) {
  const { data } = await http.get<ApiResponse<AdminPromptVersionView[]>>(
    `/api/admin/prompts/${encodeURIComponent(code)}`,
  )
  return data.data
}

export async function diffPromptVersions(code: string, from: number, to: number) {
  const { data } = await http.get<ApiResponse<AdminPromptDiffView>>(
    `/api/admin/prompts/${encodeURIComponent(code)}/versions/${from}/diff/${to}`,
  )
  return data.data
}

export async function createPromptVersion(
  code: string,
  body: { name: string; role: string; content: string; changeNote?: string; publish?: boolean },
) {
  const { data } = await http.post<ApiResponse<AdminPromptVersionView>>(
    `/api/admin/prompts/${encodeURIComponent(code)}/versions`,
    body,
  )
  return data.data
}

export async function publishPromptVersion(code: string, version: number) {
  const { data } = await http.put<ApiResponse<AdminPromptVersionView>>(
    `/api/admin/prompts/${encodeURIComponent(code)}/versions/${version}/publish`,
  )
  return data.data
}

export async function rollbackPromptVersion(code: string, version: number) {
  const { data } = await http.put<ApiResponse<AdminPromptVersionView>>(
    `/api/admin/prompts/${encodeURIComponent(code)}/versions/${version}/rollback`,
  )
  return data.data
}
