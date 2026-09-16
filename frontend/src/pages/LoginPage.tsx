import { useState } from 'react'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { loginApi } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import { decodeJwt } from '../utils/jwt'
import { getApiErrorMessage } from '../api/client'

export function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const setAuth = useAuthStore((s) => s.setAuth)

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Username and password are required')
      return
    }
    setError(null)
    setLoading(true)
    try {
      const res = await loginApi(username.trim(), password)
      const token = res.accessToken
      if (!token) throw new Error('No token returned')
      setAuth(token)

      const payload = decodeJwt(token)
      const mustChange = payload?.mustChangePassword ?? false
      const authorities = payload?.authorities || []

      if (mustChange) {
        navigate('/change-password', { replace: true })
        return
      }
      if (from && from !== '/login') {
        navigate(from, { replace: true })
        return
      }
      if (authorities.includes('PLATFORM_ADMIN')) {
        navigate('/platform/dashboard', { replace: true })
        return
      }
      navigate('/organisation/dashboard', { replace: true })
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Invalid username or password')
      // map common backend messages
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 401) setError(msg === 'Something went wrong' ? 'Invalid credentials' : msg)
      else if (status === 403) setError('Access denied')
      else setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ width: 400, maxWidth: '90vw', background: '#fff', borderRadius: 12, boxShadow: '0 8px 30px rgba(0,0,0,0.08)', border: '1px solid #E2E8F0', overflow: 'hidden' }}>
      <div style={{ padding: '28px 28px 20px', borderBottom: '1px solid #F1F5F9' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
          <div style={{ width: 36, height: 36, borderRadius: 8, background: '#0F172A', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 800, fontSize: 16 }}>T</div>
          <div>
            <div style={{ fontWeight: 800, fontSize: 16, color: '#0F172A', lineHeight: 1 }}>TaceIQ</div>
            <div style={{ fontSize: 12, color: '#64748B' }}>Sign in to your workspace</div>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 16 }} noValidate>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label htmlFor="username" style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
            Username or Email
          </label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="admin@taceiq.com or username"
            autoComplete="username"
            autoFocus
            required
            style={{ padding: '10px 12px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 14, outline: 'none' }}
          />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label htmlFor="password" style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>
            Password
          </label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
            required
            style={{ padding: '10px 12px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 14, outline: 'none' }}
          />
        </div>

        {error && (
          <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '10px 12px', borderRadius: 8, fontSize: 13 }}>
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={loading}
          style={{
            padding: '11px 16px',
            background: loading ? '#475569' : '#0F172A',
            color: '#fff',
            border: 0,
            borderRadius: 8,
            cursor: loading ? 'not-allowed' : 'pointer',
            fontWeight: 700,
            fontSize: 14,
            opacity: loading ? 0.7 : 1,
          }}
        >
          {loading ? 'Signing in…' : 'Sign in'}
        </button>

        <div style={{ fontSize: 12, color: '#94A3B8', textAlign: 'center' }}>
          Platform admin? Use your <code style={{ background: '#F1F5F9', padding: '1px 6px', borderRadius: 4 }}>admin email</code> to access platform workspace.
        </div>
      </form>
    </div>
  )
}
