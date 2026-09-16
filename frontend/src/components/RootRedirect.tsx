import { Navigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { isTokenExpired } from '../utils/jwt'

export function RootRedirect() {
  const token = useAuthStore((s) => s.token)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword)

  if (!isAuthenticated || !token || isTokenExpired(token)) {
    return <Navigate to="/login" replace />
  }
  if (mustChangePassword) return <Navigate to="/change-password" replace />
  if (isPlatformAdmin) return <Navigate to="/platform/dashboard" replace />
  return <Navigate to="/organisation/dashboard" replace />
}
