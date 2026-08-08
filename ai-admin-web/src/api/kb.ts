import http, { type ApiResponse } from './http'

export interface AdminKbDocumentPage {
  items: Record<string, unknown>[]
  total: number
  page: number
  size: number
}

export interface IngestResult {
  docId: string
  /** 雪花 BIGINT，后端以字符串返回，避免 JS Number 丢精度 */
  versionId: string
  version: string
  chunkCount: number
  embedded: boolean
}

export interface KbChunkView {
  id: string
  chunkKey: string
  /** 同版本内切分序号（1-based） */
  chunkSeq: number | null
  /** 入库时间 ISO-8601（毫秒） */
  ingestedAt: string | null
  content: string
  section: string
  summary: string
  status: string
  collection: string
  docId: string
  /** 雪花 BIGINT，后端以字符串返回 */
  versionId: string
  version: string
  currentRevision: number | null
  contentHash: string | null
}

export interface KbChunkWriteResult {
  view: KbChunkView
  embedded: boolean
}

export interface KbChunkRevisionView {
  chunkId: string
  revision: number
  contentHash: string
  status: string
  content: string
  createTime: string | null
}

export interface KbVersionEmbeddingView {
  versionId: string
  embeddingConfigId: string | null
  embeddingModelVersion: string | null
  embeddedChunkCount: number
}

export interface KbDocumentDetail {
  document: Record<string, unknown>
  versions: Record<string, unknown>[]
}

export async function listDocuments(params: {
  collection?: string
  status?: string
  page?: number
  size?: number
}) {
  const { data } = await http.get<ApiResponse<AdminKbDocumentPage>>('/api/admin/kb/documents', {
    params,
  })
  return data.data
}

export async function getDocument(docId: string) {
  const { data } = await http.get<ApiResponse<KbDocumentDetail>>(`/api/admin/kb/documents/${docId}`)
  return data.data
}

export async function ingestFile(form: FormData) {
  const { data } = await http.post<ApiResponse<IngestResult>>('/api/admin/kb/documents', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
  return data.data
}

export async function deprecateVersion(docId: string, versionId: string) {
  await http.post<ApiResponse<null>>(
    `/api/admin/kb/documents/${docId}/versions/${encodeURIComponent(versionId)}/deprecate`,
  )
}

export async function listChunks(docId: string, versionId: string, status?: string) {
  const { data } = await http.get<ApiResponse<KbChunkView[]>>(
    `/api/admin/kb/documents/${docId}/versions/${encodeURIComponent(versionId)}/chunks`,
    { params: { status } },
  )
  return data.data
}

export async function createChunk(
  docId: string,
  versionId: string,
  body: { content: string; section?: string },
) {
  const { data } = await http.post<ApiResponse<KbChunkWriteResult>>(
    `/api/admin/kb/documents/${docId}/versions/${encodeURIComponent(versionId)}/chunks`,
    body,
  )
  return data.data
}

export async function updateChunk(chunkId: string, body: { content: string; section?: string }) {
  const { data } = await http.put<ApiResponse<KbChunkWriteResult>>(
    `/api/admin/kb/chunks/${chunkId}`,
    body,
  )
  return data.data
}

export async function deleteChunk(chunkId: string) {
  await http.delete<ApiResponse<null>>(`/api/admin/kb/chunks/${chunkId}`)
}

export async function listRevisions(chunkId: string) {
  const { data } = await http.get<ApiResponse<KbChunkRevisionView[]>>(
    `/api/admin/kb/chunks/${chunkId}/revisions`,
  )
  return data.data
}

export async function rollbackRevision(chunkId: string, revision: number) {
  const { data } = await http.post<ApiResponse<KbChunkWriteResult>>(
    `/api/admin/kb/chunks/${chunkId}/revisions/${revision}/rollback`,
  )
  return data.data
}

export async function getVersionEmbedding(docId: string, versionId: string) {
  const { data } = await http.get<ApiResponse<KbVersionEmbeddingView>>(
    `/api/admin/kb/documents/${docId}/versions/${encodeURIComponent(versionId)}/embedding`,
  )
  return data.data
}

export async function rebuildEmbedding(docId: string, versionId: string) {
  const { data } = await http.post<ApiResponse<KbVersionEmbeddingView>>(
    `/api/admin/kb/documents/${docId}/versions/${encodeURIComponent(versionId)}/embedding/rebuild`,
    null,
    { timeout: 120000 },
  )
  return data.data
}
