import { apiClient } from '../api/client'
import type { FileRecord, FileCreateRequest, FileUpdateRequest } from '../types/file'

export async function listFilesByOrg(orgId: number): Promise<FileRecord[]> {
  const res = await apiClient.get<FileRecord[]>(`/files/organisation/${orgId}`)
  return res.data
}

// Fallback generic list – backend has no GET /api/files, use org-scoped
export async function listFiles(): Promise<FileRecord[]> {
  // keep for typings but delegate to org-scoped via caller; throw if not used correctly
  const res = await apiClient.get<FileRecord[]>(`/files/organisation/0`).catch(() => ({ data: [] as FileRecord[] })) // placeholder never called
  return res.data
}

export async function getFile(id: number): Promise<FileRecord> {
  const res = await apiClient.get<FileRecord>(`/files/${id}`)
  return res.data
}

export async function createFile(payload: FileCreateRequest): Promise<FileRecord> {
  // Never send organisationId – backend derives from JWT; integration optional but must be org-scoped
  const body: Record<string, unknown> = {
    sourceType: payload.sourceType.trim(),
    originalName: payload.originalName.trim(),
  }
  if (payload.storageKey != null) body.storageKey = payload.storageKey
  if (payload.contentType != null) body.contentType = payload.contentType
  if (payload.size != null) body.size = payload.size
  if (payload.status != null) body.status = payload.status
  if (payload.integration) body.integration = payload.integration
  const res = await apiClient.post<FileRecord>('/files', body)
  return res.data
}

export async function updateFile(id: number, payload: FileUpdateRequest): Promise<FileRecord> {
  // Only mutable fields per FileService.java:147 – organisation never, integration only if org-scoped
  const body: Record<string, unknown> = {}
  if (payload.originalName !== undefined) body.originalName = payload.originalName
  if (payload.storageKey !== undefined) body.storageKey = payload.storageKey
  if (payload.contentType !== undefined) body.contentType = payload.contentType
  if (payload.size !== undefined) body.size = payload.size
  if (payload.status !== undefined) body.status = payload.status
  if (payload.integration !== undefined) body.integration = payload.integration
  const res = await apiClient.put<FileRecord>(`/files/${id}`, body)
  return res.data
}

export async function updateFileStatus(id: number, status: string): Promise<FileRecord> {
  const res = await apiClient.patch<FileRecord>(`/files/${id}/status`, { status })
  return res.data
}

export async function deleteFile(id: number): Promise<void> {
  await apiClient.delete(`/files/${id}`)
}

export async function uploadFile(file: File, incidentId?: number | null): Promise<FileRecord> {
  const formData = new FormData()
  formData.append('file', file)
  const params: Record<string, unknown> = {}
  if (incidentId != null) params.incidentId = incidentId
  const res = await apiClient.post<FileRecord>('/files/upload', formData, { params, timeout: 60000 })
  return res.data
}

export async function getFilesByIntegration(integrationId: number): Promise<FileRecord[]> {
  const res = await apiClient.get<FileRecord[]>(`/files/integration/${integrationId}`)
  return res.data
}
