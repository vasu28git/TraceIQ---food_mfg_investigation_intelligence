import { Link } from 'react-router-dom'

export function ForbiddenPage() {
  return (
    <div style={{ padding: 32, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca', maxWidth: 520, margin: '40px auto' }}>
      <h2 style={{ margin: 0 }}>403 · Forbidden</h2>
      <p style={{ marginTop: 8, fontSize: 14 }}>You do not have permission to access this resource. Contact your administrator if you believe this is an error.</p>
      <Link to="/" style={{ display: 'inline-block', marginTop: 12, color: '#fecaca', fontWeight: 600 }}>
        ← Back to dashboard
      </Link>
    </div>
  )
}
