import { apiClient } from '../api/client'
import type { Role, RoleCreateRequest, RoleUpdateRequest, Permission } from '../types/role'
import type { RoleSummary } from '../types/user'

// Keep backward-compatible simple summary fetcher for UsersPage
export async function listRolesByOrg(orgId: number): Promise<RoleSummary[]> {
  const res = await apiClient.get<RoleSummary[]>(`/roles/org/${orgId}`)
  return res.data
}

// Full role list – uses same endpoint, returns richer Role objects
export async function getRolesByOrg(orgId: number): Promise<Role[]> {
  const res = await apiClient.get<Role[]>(`/roles/org/${orgId}`)
  return res.data
}

export async function createRole(payload: RoleCreateRequest): Promise<Role> {
  // Backend forces organisation from auth – never send orgId
  const body = { name: payload.name.trim(), description: payload.description?.trim() || undefined }
  const res = await apiClient.post<Role>('/roles', body)
  return res.data
}

export async function updateRole(id: number, payload: RoleUpdateRequest): Promise<Role> {
  const body: Record<string, unknown> = {}
  if (payload.name != null) body.name = payload.name.trim()
  if (payload.description !== undefined) body.description = payload.description
  const res = await apiClient.put<Role>(`/roles/${id}`, body)
  return res.data
}

export async function deleteRole(id: number): Promise<void> {
  await apiClient.delete(`/roles/${id}`)
}

export async function getPermissionsForRole(roleId: number): Promise<Permission[]> {
  const res = await apiClient.get<Permission[] | Set<Permission>>(`/roles/${roleId}/permissions`)
  const data = res.data as unknown
  // Backend returns Set<Permission> serialized as array
  return Array.isArray(data) ? (data as Permission[]) : Array.from(data as Set<Permission>)
}

export async function assignPermissionsToRole(roleId: number, permissionIds: number[]): Promise<Role> {
  const res = await apiClient.post<Role>(`/roles/${roleId}/permissions`, permissionIds)
  return res.data
}

export async function removePermissionsFromRole(roleId: number, permissionIds: number[]): Promise<Role> {
  // axios DELETE with body requires data in config
  const res = await apiClient.delete<Role>(`/roles/${roleId}/permissions`, { data: permissionIds })
  return res.data
}
