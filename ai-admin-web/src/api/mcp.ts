import http, { type ApiResponse } from './http'

export interface AdminMcpServerView {
  serverCode: string
  displayName: string
  status: string
  baseUrl: string
  sseEndpoint?: string | null
  authType: string
  authTokenMasked?: string | null
  authTokenPreview?: string | null
  sortOrder?: number | null
}

export interface AdminMcpToolView {
  toolName: string
  description?: string | null
  enabled: boolean
  bound: boolean
}

export interface AdminMcpToolsResponse {
  tools: AdminMcpToolView[]
  source: string
}

export interface AdminMcpToolDetailView {
  toolName: string
  description?: string | null
  inputSchema?: unknown
  bound: boolean
  enabled: boolean
  serverCode: string
  serverVersion?: string | null
  toolHash?: string | null
  source: string
}

export async function listMcpServers() {
  const { data } = await http.get<ApiResponse<AdminMcpServerView[]>>('/api/admin/mcp/servers')
  return data.data
}

export async function getMcpServer(serverCode: string, revealToken = false) {
  const { data } = await http.get<ApiResponse<AdminMcpServerView>>(
    `/api/admin/mcp/servers/${encodeURIComponent(serverCode)}`,
    { params: { revealToken } },
  )
  return data.data
}

export async function createMcpServer(body: {
  serverCode: string
  displayName: string
  baseUrl: string
  sseEndpoint?: string
  authType: string
  authToken?: string
  sortOrder?: number
}) {
  const { data } = await http.post<ApiResponse<AdminMcpServerView>>('/api/admin/mcp/servers', body)
  return data.data
}

export async function updateMcpServer(
  serverCode: string,
  body: {
    displayName?: string
    baseUrl?: string
    sseEndpoint?: string
    authType?: string
    authToken?: string
    status?: string
    sortOrder?: number
  },
) {
  const { data } = await http.put<ApiResponse<AdminMcpServerView>>(
    `/api/admin/mcp/servers/${encodeURIComponent(serverCode)}`,
    body,
  )
  return data.data
}

export async function deleteMcpServer(serverCode: string) {
  await http.delete(`/api/admin/mcp/servers/${encodeURIComponent(serverCode)}`)
}

export async function listMcpTools(serverCode: string) {
  const { data } = await http.get<ApiResponse<AdminMcpToolsResponse>>(
    `/api/admin/mcp/servers/${encodeURIComponent(serverCode)}/tools`,
  )
  return data.data
}

export async function getMcpToolDetail(serverCode: string, toolName: string) {
  const { data } = await http.get<ApiResponse<AdminMcpToolDetailView>>(
    `/api/admin/mcp/servers/${encodeURIComponent(serverCode)}/tools/${encodeURIComponent(toolName)}`,
  )
  return data.data
}

export async function updateMcpTools(
  serverCode: string,
  tools: { toolName: string; bound: boolean; enabled: boolean }[],
) {
  const { data } = await http.put<ApiResponse<AdminMcpToolsResponse>>(
    `/api/admin/mcp/servers/${encodeURIComponent(serverCode)}/tools`,
    { tools },
  )
  return data.data
}
