import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { isTokenExpired } from '../utils/jwt'

export function ProtectedRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const token = useAuthStore((s) => s.token)
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const location = useLocation()

  // invalid / expired token → force re-login
  if (!isAuthenticated || !token || isTokenExpired(token)) {
    if (token && isTokenExpired(token)) clearAuth()
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  // platform admin must not use org workspace
  if (isPlatformAdmin) {
    return <Navigate to="/platform/dashboard" replace />
  }

  // password change enforcement – allow only change-password page
  if (mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }

  // if user is on change-password but no longer requires it, send to dashboard
  if (!mustChangePassword && location.pathname === '/change-password') {
    return <Navigate to="/organisation/dashboard" replace />
  }

  return <Outlet />
}

export function GuestRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const token = useAuthStore((s) => s.token)
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()

  if (isAuthenticated && token && !isTokenExpired(token)) {
    if (mustChangePassword) return <Navigate to="/change-password" replace />
    if (isPlatformAdmin) return <Navigate to="/platform/dashboard" replace />
    return <Navigate to="/organisation/dashboard" replace />
  }
  return <>{children}</>
}
