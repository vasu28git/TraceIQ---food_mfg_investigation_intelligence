import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { isTokenExpired } from '../utils/jwt'

export function PlatformAdminRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const token = useAuthStore((s) => s.token)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const location = useLocation()

  if (!isAuthenticated || !token || isTokenExpired(token)) {
    if (token && isTokenExpired(token)) clearAuth()
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (!isPlatformAdmin) {
    return <Navigate to="/organisation/dashboard" replace />
  }

  return <Outlet />
}
