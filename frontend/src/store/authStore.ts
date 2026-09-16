import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { decodeJwt, isTokenExpired } from '../utils/jwt'
import type { AuthState } from '../types/auth'

interface AuthStore extends AuthState {
  setAuth: (token: string) => void
  clearAuth: () => void
  hasAuthority: (authority: string) => boolean
  hasAnyAuthority: (...authorities: string[]) => boolean
  hasAllAuthorities: (...authorities: string[]) => boolean
  isPlatformAdmin: () => boolean
  isTokenValid: () => boolean
}

const initialState: AuthState = {
  token: null,
  user: null,
  username: null,
  role: null,
  organisationId: null,
  permissions: [],
  authorities: [],
  mustChangePassword: false,
  isAuthenticated: false,
}

function normalizeAuthorities(list: string[]): Set<string> {
  return new Set(list.map((s) => String(s).toUpperCase()))
}

export const useAuthStore = create<AuthStore>()(
  persist(
    (set, get) => ({
      ...initialState,
      setAuth: (token: string) => {
        // reject expired / invalid token immediately
        if (!token || isTokenExpired(token)) {
          set({ ...initialState })
          return
        }
        const payload = decodeJwt(token)
        if (!payload) {
          set({ ...initialState })
          return
        }
        const username = payload.username || payload.sub || null
        const orgId = payload.organisationId ?? payload.orgId ?? null
        const role = payload.role || null
        const permissions = payload.permissions || []
        const authorities = payload.authorities || []
        const mustChangePassword = payload.mustChangePassword ?? false

        set({
          token,
          user: username,
          username,
          role,
          organisationId: orgId != null ? Number(orgId) : null,
          permissions,
          authorities,
          mustChangePassword,
          isAuthenticated: true,
        })
      },
      clearAuth: () => set({ ...initialState }),

      hasAuthority: (authority: string) => {
        if (!authority) return false
        const need = authority.toUpperCase()
        const { authorities, permissions } = get()
        const setAuth = normalizeAuthorities([...authorities, ...permissions])
        return setAuth.has(need)
      },

      hasAnyAuthority: (...authorities: string[]) => {
        if (!authorities.length) return false
        const { authorities: a, permissions: p } = get()
        const setAuth = normalizeAuthorities([...a, ...p])
        return authorities.some((auth) => setAuth.has(auth.toUpperCase()))
      },

      hasAllAuthorities: (...authorities: string[]) => {
        if (!authorities.length) return true
        const { authorities: a, permissions: p } = get()
        const setAuth = normalizeAuthorities([...a, ...p])
        return authorities.every((auth) => setAuth.has(auth.toUpperCase()))
      },

      isPlatformAdmin: () => {
        const { authorities } = get()
        return normalizeAuthorities(authorities).has('PLATFORM_ADMIN')
      },

      isTokenValid: () => {
        const token = get().token
        if (!token) return false
        return !isTokenExpired(token)
      },
    }),
    {
      name: 'taceiq-auth',
      partialize: (state) => ({ token: state.token }),
      onRehydrateStorage: () => (state) => {
        if (state?.token) {
          // validate on rehydrate – clears stale/expired token automatically
          if (isTokenExpired(state.token)) {
            // delay to allow store init
            setTimeout(() => state.clearAuth(), 0)
          } else {
            state.setAuth(state.token)
          }
        }
      },
    }
  )
)
