import { apiClient } from '../api/client'
import type { LoginResponse } from '../types/auth'

export async function loginApi(username: string, password: string): Promise<LoginResponse> {
  const res = await apiClient.post<LoginResponse>('/auth/login', {
    username,
    password,
    email: username, // backend accepts either username or email
  })
  return res.data
}

export async function changePasswordApi(currentPassword: string, newPassword: string, confirmPassword: string) {
  const res = await apiClient.post('/auth/change-password', {
    currentPassword,
    newPassword,
    confirmPassword,
  })
  return res.data
}
