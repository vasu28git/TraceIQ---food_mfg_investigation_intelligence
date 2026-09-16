import { apiClient } from '../api/client'
import type { Configuration, ConfigurationCreateRequest, ConfigurationUpdateRequest } from '../types/configuration'

export async function listConfigurations(): Promise<Configuration[]> {
  const res = await apiClient.get<Configuration[]>('/configurations')
  return res.data
}

export async function listDefinitions(): Promise<import('../types/configuration').ConfigurationDefinition[]> {
  const res = await apiClient.get<import('../types/configuration').ConfigurationDefinition[]>('/configurations/definitions')
  return res.data
}

export async function getConfiguration(id: number): Promise<Configuration> {
  const res = await apiClient.get<Configuration>(`/configurations/${id}`)
  return res.data
}

export async function createConfiguration(payload: ConfigurationCreateRequest): Promise<Configuration> {
  // Backend forces organisation from auth – never send orgId
  const body = {
    definition: { key: payload.definition.key.trim() },
    value: payload.value ?? null,
  }
  const res = await apiClient.post<Configuration>('/configurations', body)
  return res.data
}

export async function updateConfiguration(id: number, payload: ConfigurationUpdateRequest): Promise<Configuration> {
  // Only value is mutable; definition/key and organisation are immutable per ConfigurationService.java:123
  const body: Record<string, unknown> = {
    value: payload.value ?? null,
  }
  // Backend expects Configuration object with value field; we send minimal shape that maps
  const res = await apiClient.put<Configuration>(`/configurations/${id}`, body)
  return res.data
}

export async function deleteConfiguration(id: number): Promise<void> {
  await apiClient.delete(`/configurations/${id}`)
}
