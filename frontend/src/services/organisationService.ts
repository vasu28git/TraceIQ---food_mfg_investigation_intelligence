import { apiClient } from '../api/client'
import type { Organisation, OrganisationProvisioningResult } from '../types/organisation'

export async function createOrganisation(data: Organisation): Promise<OrganisationProvisioningResult> {
  const res = await apiClient.post<OrganisationProvisioningResult>('/organisations', data)
  return res.data
}

export async function getOrganisations(): Promise<Organisation[]> {
  const res = await apiClient.get<Organisation[]>('/organisations')
  return res.data
}

export async function getOrganisation(orgId: number): Promise<Organisation> {
  const res = await apiClient.get<Organisation>(`/organisations/${orgId}`)
  return res.data
}

export async function updateOrganisation(orgId: number, data: Partial<Organisation>): Promise<Organisation> {
  const res = await apiClient.put<Organisation>(`/organisations/${orgId}`, data)
  return res.data
}

export async function deleteOrganisation(orgId: number): Promise<void> {
  await apiClient.delete(`/organisations/${orgId}`)
}
