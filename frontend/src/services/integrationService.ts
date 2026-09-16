import { apiClient } from '../api/client'
import type { Integration, IntegrationCreateRequest, IntegrationUpdateRequest } from '../types/integration'

export async function listIntegrations(): Promise<Integration[]> {
  const res = await apiClient.get<Integration[]>('/integrations')
  return res.data
}

export async function getIntegration(id: number): Promise<Integration> {
  const res = await apiClient.get<Integration>(`/integrations/${id}`)
  return res.data
}

export async function createIntegration(payload: IntegrationCreateRequest): Promise<Integration> {
  // Never send organisationId – backend derives from JWT
  const body: Record<string, unknown> = { name: payload.name.trim() }
  if (payload.type != null) body.type = payload.type.trim() || null
  if (payload.status != null) body.status = payload.status.trim() || null
  if (payload.configuration != null) body.configuration = payload.configuration
  const res = await apiClient.post<Integration>('/integrations', body)
  return res.data
}

export async function updateIntegration(id: number, payload: IntegrationUpdateRequest): Promise<Integration> {
  const body: Record<string, unknown> = {}
  if (payload.name !== undefined) body.name = payload.name?.trim()
  if (payload.type !== undefined) body.type = payload.type?.trim() || null
  if (payload.status !== undefined) body.status = payload.status?.trim() || null
  if (payload.configuration !== undefined) body.configuration = payload.configuration
  const res = await apiClient.put<Integration>(`/integrations/${id}`, body)
  return res.data
}

export async function updateIntegrationStatus(id: number, status: string): Promise<Integration> {
  const res = await apiClient.patch<Integration>(`/integrations/${id}/status`, { status })
  return res.data
}

export async function testIntegration(id: number): Promise<string> {
  const res = await apiClient.post<{ message: string }>(`/integrations/${id}/test`)
  return res.data.message || 'Success'
}

export async function syncIntegration(id: number, incidentId?: number | null): Promise<{ syncId: number; status: string }> {
  const params: Record<string, unknown> = {}
  if (incidentId != null) params.incidentId = incidentId
  const res = await apiClient.post<{ syncId: number; status: string }>(`/integrations/${id}/sync`, null, { params })
  return res.data
}

export async function checkGraphReady(integrationId: number): Promise<{ graphReady: boolean; graphProjected: boolean; graphValid: boolean; evidenceCount: number; message?: string }> {
  const res = await apiClient.post(`/integrations/${integrationId}/graph/ready`)
  return res.data
}

export async function checkOrgGraphReady(): Promise<{ graphReady: boolean; graphProjected: boolean; graphValid: boolean; evidenceCount: number; message?: string }> {
  const res = await apiClient.get('/graph/ready')
  return res.data
}

export async function deleteIntegration(id: number): Promise<void> {
  await apiClient.delete(`/integrations/${id}`)
}
