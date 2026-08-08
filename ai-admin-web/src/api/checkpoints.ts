import http, { type ApiResponse } from './http'

export interface AdminCheckpointThreadSummary {
  threadId: string
  threadName: string
  userId: string
  conversationId: string
  isReleased: boolean
  checkpointCount: number
  lastSavedAt: string
}

export interface AdminCheckpointStepSummary {
  checkpointId: string
  parentCheckpointId: string | null
  nodeId: string | null
  nextNodeId: string | null
  savedAt: string | null
  stateContentType: string | null
  deltaMs: number | null
  stepIndex: number
}

export interface AdminCheckpointStateEntry {
  key: string
  type: string
  summary: string
  value: unknown
}

export interface AdminCheckpointMessageView {
  index: number
  role: string
  content: string | null
  toolCallsJson: string | null
  toolResponsesJson: string | null
}

export interface AdminCheckpointDetail {
  checkpointId: string
  parentCheckpointId: string | null
  threadId: string
  nodeId: string | null
  nextNodeId: string | null
  savedAt: string | null
  stateContentType: string | null
  stateData: unknown
  decodedState: unknown
  stateEntries: AdminCheckpointStateEntry[]
  messages: AdminCheckpointMessageView[]
  decodeError: string | null
}

export interface AdminCheckpointThreadDetail {
  thread: AdminCheckpointThreadSummary
  steps: AdminCheckpointStepSummary[]
}

export interface AdminCheckpointThreadPage {
  items: AdminCheckpointThreadSummary[]
  total: number
  page: number
  size: number
}

export interface AdminCheckpointThreadQueryParams {
  threadName?: string
  userId?: string
  conversationId?: string
  isReleased?: boolean
  savedFrom?: string
  savedTo?: string
  page?: number
  size?: number
}

export async function listCheckpointThreads(params: AdminCheckpointThreadQueryParams) {
  const { data } = await http.get<ApiResponse<AdminCheckpointThreadPage>>('/api/admin/logs/checkpoints/threads', {
    params,
  })
  return data.data
}

export async function getCheckpointThread(threadId: string) {
  const { data } = await http.get<ApiResponse<AdminCheckpointThreadDetail>>(
    `/api/admin/logs/checkpoints/threads/${threadId}`,
  )
  return data.data
}

export async function getCheckpoint(checkpointId: string) {
  const { data } = await http.get<ApiResponse<AdminCheckpointDetail>>(
    `/api/admin/logs/checkpoints/${checkpointId}`,
  )
  return data.data
}
