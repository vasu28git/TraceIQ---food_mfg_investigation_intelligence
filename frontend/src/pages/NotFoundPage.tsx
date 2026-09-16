import { Link } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'

export function NotFoundPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const isPlatformAdmin = useAuthStore((s) => s.isPlatformAdmin)()

  const home = !isAuthenticated ? '/login' : isPlatformAdmin ? '/platform/dashboard' : '/organisation/dashboard'

  return (
    <div style={{ minHeight: '60vh', display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column', gap: 16, padding: 24 }}>
      <h1 style={{ margin: 0, fontSize: 48, fontWeight: 800, color: '#111827' }}>404</h1>
      <p style={{ color: '#6B7280', fontSize: 14, margin: 0 }}>Page not found.</p>
      <Link to={home} style={{ padding: '8px 16px', background: '#111827', color: '#fff', borderRadius: 8, textDecoration: 'none', fontSize: 14 }}>
        Go home
      </Link>
    </div>
  )
}
