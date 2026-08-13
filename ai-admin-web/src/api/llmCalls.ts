import http, { type ApiResponse } from './http'

export interface AdminLlmCallSummary {
  callId: string
  traceId: string | null
  userId: string | null
  conversationId: string | null
  messageId: string | null
  modelId: string
  provider: string | null
  bizSource: string | null
  bizRefId: string | null
  attempt: number
  isFallback: boolean
  status: string
  errorCode: string | null
  latencyMs: number | null
  promptTokens: number | null
  completionTokens: number | null
  createTime: string | null
}

export interface AdminLlmCallDetail extends AdminLlmCallSummary {
  requestJson: unknown
  responseJson: unknown
}

export interface AdminLlmCallPage {
  items: AdminLlmCallSummary[]
  total: number
  page: number
  size: number
}

export interface AdminLlmCallQueryParams {
  userId?: string
  conversationId?: string
  messageId?: string
  callId?: string
  traceId?: string
  modelId?: string
  provider?: string
  biz_source?: string
  biz_ref_id?: string
  status?: string
  isFallback?: boolean
  createTimeFrom?: string
  createTimeTo?: string
  latencyMsMin?: number
  latencyMsMax?: number
  promptTokensMin?: number
  promptTokensMax?: number
  page?: number
  size?: number
}

export async function listLlmCalls(params: AdminLlmCallQueryParams) {
  const { data } = await http.get<ApiResponse<AdminLlmCallPage>>('/api/admin/logs/llm-calls', {
    params,
  })
  return data.data
}

export async function getLlmCall(callId: string) {
  const { data } = await http.get<ApiResponse<AdminLlmCallDetail>>(`/api/admin/logs/llm-calls/${callId}`)
  return data.data
}
