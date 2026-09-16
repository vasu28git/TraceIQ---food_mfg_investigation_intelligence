export interface JwtPayload {
  sub: string
  username?: string
  userId?: number
  organisationId?: number
  orgId?: number
  role?: string
  roleId?: number
  authorities?: string[]
  permissions?: string[]
  mustChangePassword?: boolean
  iat?: number
  exp?: number
}

export interface AuthState {
  token: string | null
  user: string | null
  username: string | null
  role: string | null
  organisationId: number | null
  permissions: string[]
  authorities: string[]
  mustChangePassword: boolean
  isAuthenticated: boolean
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  email: string
}
