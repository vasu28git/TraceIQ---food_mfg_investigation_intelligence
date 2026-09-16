import { apiClient } from '../api/client'
import type { UserResponse, UserCreateRequest, UserUpdateRequest } from '../types/user'

export async function listUsers(): Promise<UserResponse[]> {
  const res = await apiClient.get<UserResponse[]>('/users')
  return res.data
}

export async function getUserById(id: number): Promise<UserResponse> {
  const res = await apiClient.get<UserResponse>(`/users/${id}`)
  return res.data
}

export async function createUser(payload: UserCreateRequest): Promise<UserResponse> {
  // Backend contract: POST /api/users never accepts organisationId
  const body: Record<string, unknown> = {
    username: payload.username?.trim(),
    password: payload.password,
    status: payload.status,
  }
  if (payload.roleId != null) body.roleId = payload.roleId
  const res = await apiClient.post<UserResponse>('/users', body)
  return res.data
}

export async function updateUser(id: number, payload: UserUpdateRequest): Promise<UserResponse> {
  const body: Record<string, unknown> = {}
  if (payload.username != null) body.username = payload.username.trim()
  if (payload.status != null) body.status = payload.status
  if (payload.roleId !== undefined) body.roleId = payload.roleId
  const res = await apiClient.put<UserResponse>(`/users/${id}`, body)
  return res.data
}

export async function updateUserStatus(id: number, status: string): Promise<UserResponse> {
  const res = await apiClient.patch<UserResponse>(`/users/${id}/status`, { status })
  return res.data
}

export async function deleteUser(id: number): Promise<void> {
  await apiClient.delete(`/users/${id}`)
}
