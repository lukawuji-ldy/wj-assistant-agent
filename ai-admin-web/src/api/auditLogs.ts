import http, { type ApiResponse } from './http'

export interface AdminAuditLogSummary {
  id: string
  adminId: string
  adminUsername: string | null
  action: string
  resourceType: string
  resourceId: string | null
  createTime: string | null
}

export interface AdminAuditChange {
  field: string
  from: unknown
  to: unknown
}

export interface AdminAuditDetailPayload {
  changes?: AdminAuditChange[]
  meta?: Record<string, unknown>
}

export interface AdminAuditLogDetail extends AdminAuditLogSummary {
  detail: AdminAuditDetailPayload | null
}

export interface AdminAuditLogPage {
  items: AdminAuditLogSummary[]
  total: number
  page: number
  size: number
}

export interface AdminAuditLogQueryParams {
  adminId?: string
  action?: string
  resourceType?: string
  resourceId?: string
  createTimeFrom?: string
  createTimeTo?: string
  page?: number
  size?: number
}

export async function listAuditLogs(params: AdminAuditLogQueryParams) {
  const { data } = await http.get<ApiResponse<AdminAuditLogPage>>('/api/admin/logs/audit', {
    params,
  })
  return data.data
}

export async function getAuditLog(id: string) {
  const { data } = await http.get<ApiResponse<AdminAuditLogDetail>>(`/api/admin/logs/audit/${id}`)
  return data.data
}
