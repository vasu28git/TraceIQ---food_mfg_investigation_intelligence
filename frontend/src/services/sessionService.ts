/**
 * Session service – thin wrapper around Zustand auth state + JWT.
 * Backend does not expose a dedicated /api/me endpoint; session is derived from the JWT
 * issued at /api/auth/login (see JwtService.java:42). This keeps the frontend stateless
 * and consistent with the backend's authoritative permission checks.
 */
import { useAuthStore } from '../store/authStore'
import { decodeJwt, isTokenExpired } from '../utils/jwt'
import type { JwtPayload } from '../types/auth'

export type Session = {
  isAuthenticated: boolean
  username: string | null
  role: string | null
  organisationId: number | null
  permissions: string[]
  authorities: string[]
  mustChangePassword: boolean
  isPlatformAdmin: boolean
  token: string | null
  payload: JwtPayload | null
  isExpired: boolean
}

/** Read current session synchronously from store + token */
export function getSession(): Session {
  const s = useAuthStore.getState()
  const payload = s.token ? decodeJwt(s.token) : null
  return {
    isAuthenticated: s.isAuthenticated && !!s.token && !isTokenExpired(s.token!),
    username: s.username,
    role: s.role,
    organisationId: s.organisationId,
    permissions: s.permissions,
    authorities: s.authorities,
    mustChangePassword: s.mustChangePassword,
    isPlatformAdmin: s.isPlatformAdmin(),
    token: s.token,
    payload,
    isExpired: s.token ? isTokenExpired(s.token) : true,
  }
}

/** Subscribe to session changes (hook) */
export function useSession(): Session {
  const token = useAuthStore((s) => s.token)
  const username = useAuthStore((s) => s.username)
  const role = useAuthStore((s) => s.role)
  const organisationId = useAuthStore((s) => s.organisationId)
  const permissions = useAuthStore((s) => s.permissions)
  const authorities = useAuthStore((s) => s.authorities)
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()

  const payload = token ? decodeJwt(token) : null
  return {
    isAuthenticated: isAuthenticated && !!token && !isTokenExpired(token!),
    username,
    role,
    organisationId,
    permissions,
    authorities,
    mustChangePassword,
    isPlatformAdmin,
    token,
    payload,
    isExpired: token ? isTokenExpired(token) : true,
  }
}

/** Logout helper – clears store and optionally navigates */
export function logout(navigate?: (path: string) => void) {
  useAuthStore.getState().clearAuth()
  if (navigate) navigate('/login')
  else if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}
