export interface Permission {
  id: number
  name: string
  description?: string
}

export interface Role {
  id: number
  name: string
  description?: string
  organisation?: { orgId: number; name?: string }
  permissions?: Permission[]
  createdAt?: string
  updatedAt?: string
}

export interface RoleCreateRequest {
  name: string
  description?: string
}

export interface RoleUpdateRequest {
  name?: string
  description?: string
}
