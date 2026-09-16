export interface UserResponse {
  id: number
  username: string
  status?: string
  mustChangePassword?: boolean
  organisation?: { orgId: number; name?: string }
  role?: { id: number; name: string; description?: string }
  createdAt?: string // Instant ISO
  updatedAt?: string
}

export interface UserCreateRequest {
  username: string
  password: string
  roleId?: number | null
  status?: string // ACTIVE | INACTIVE | DISABLED | SUSPENDED
}

export interface UserUpdateRequest {
  username?: string
  roleId?: number | null
  status?: string
}

export interface RoleSummary {
  id: number
  name: string
  description?: string
}
