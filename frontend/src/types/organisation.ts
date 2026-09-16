export interface Organisation {
  orgId?: number
  name: string
  domain?: string
  description?: string
  status?: string
}

export interface Role {
  id: number
  name: string
  description?: string
  organisation?: Organisation
}

export interface User {
  id: number
  username: string
  status?: string
  mustChangePassword?: boolean
  organisation?: Organisation
  role?: Role
}

export interface OrganisationProvisioningResult {
  organisation: Organisation & { orgId: number }
  adminRole: Role
  ogUser: User
}
