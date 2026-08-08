import http, { type ApiResponse } from './http'

export interface AdminLlmConfigView {
  configId: string
  name: string
  provider: string
  modelKind: string
  baseUrl: string
  model: string
  temperature: number | null
  maxTokens: number | null
  extraJson: string | null
  status: string
  apiKeyMasked: string
  apiKeyPreview: string | null
}

export interface AdminLlmConfigPage {
  items: AdminLlmConfigView[]
  total: number
  page: number
  size: number
}

export async function listLlmConfigs(params: {
  page?: number
  size?: number
  modelKind?: string
  status?: string
}) {
  const { data } = await http.get<ApiResponse<AdminLlmConfigPage>>('/api/admin/llm-configs', {
    params,
  })
  return data.data
}

export async function getLlmConfig(configId: string, revealKey = false) {
  const { data } = await http.get<ApiResponse<AdminLlmConfigView>>(
    `/api/admin/llm-configs/${encodeURIComponent(configId)}`,
    { params: { revealKey } },
  )
  return data.data
}

export async function createLlmConfig(body: {
  configId: string
  name: string
  provider?: string
  modelKind: string
  baseUrl: string
  apiKey: string
  model: string
  temperature?: number | null
  maxTokens?: number | null
  extraJson?: string
  status?: string
}) {
  const { data } = await http.post<ApiResponse<AdminLlmConfigView>>('/api/admin/llm-configs', body)
  return data.data
}

export async function updateLlmConfig(
  configId: string,
  body: {
    name?: string
    provider?: string
    modelKind?: string
    baseUrl?: string
    apiKey?: string
    model?: string
    temperature?: number | null
    maxTokens?: number | null
    extraJson?: string
    status?: string
  },
) {
  const { data } = await http.put<ApiResponse<AdminLlmConfigView>>(
    `/api/admin/llm-configs/${encodeURIComponent(configId)}`,
    body,
  )
  return data.data
}

export async function deleteLlmConfig(configId: string) {
  await http.delete<ApiResponse<null>>(`/api/admin/llm-configs/${encodeURIComponent(configId)}`)
}
