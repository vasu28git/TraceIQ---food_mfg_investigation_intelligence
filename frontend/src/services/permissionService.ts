import { apiClient } from '../api/client'
import type { Permission } from '../types/role'

export async function getAllPermissions(): Promise<Permission[]> {
  const res = await apiClient.get<Permission[]>('/permissions')
  return res.data
}

export async function getPermissionById(id: number): Promise<Permission> {
  const res = await apiClient.get<Permission>(`/permissions/${id}`)
  return res.data
}
