import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getOrganisations } from '../services/organisationService'
import type { Organisation } from '../types/organisation'

export function PlatformDashboard() {
  const [orgs, setOrgs] = useState<Organisation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true
    getOrganisations()
      .then((data) => {
        if (mounted) setOrgs(data)
      })
      .catch((err) => {
        const status = err.response?.status
        if (status === 401) setError('Session expired. Please login again.')
        else if (status === 403) setError('Forbidden: PLATFORM_ADMIN required.')
        else setError(err.response?.data?.message || 'Failed to load organisations')
      })
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [])

  if (loading) return <div style={{ padding: 24, color: '#94A3B8' }}>Loading dashboard...</div>
  if (error) return <div style={{ padding: 24, color: '#f87171' }}>{error}</div>

  const total = orgs.length
  const active = orgs.filter((o) => (o.status || '').toUpperCase() === 'ACTIVE').length
  const suspended = orgs.filter((o) => {
    const s = (o.status || '').toUpperCase()
    return s === 'SUSPENDED' || s === 'INACTIVE' || s === 'DISABLED'
  }).length
  const recent = [...orgs]
    .slice(-5)
    .reverse()

  const card: React.CSSProperties = {
    flex: 1,
    minWidth: 160,
    background: '#fff',
    border: '1px solid #e5e5e5',
    borderRadius: 8,
    padding: 16,
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0, color: '#F8FAFC' }}>Platform Dashboard</h2>
        <Link to="/platform/organisations/new" style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', borderRadius: 6, textDecoration: 'none', fontWeight: 600, fontSize: 14 }}>
          + Create Organisation
        </Link>
      </div>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div style={card}>
          <div style={{ fontSize: 13, color: '#64748B' }}>Total organisations</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#0F172A' }}>{total}</div>
        </div>
        <div style={card}>
          <div style={{ fontSize: 13, color: '#64748B' }}>Active</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#16a34a' }}>{active}</div>
        </div>
        <div style={card}>
          <div style={{ fontSize: 13, color: '#64748B' }}>Suspended / Inactive</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#dc2626' }}>{suspended}</div>
        </div>
      </div>

      <div style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
          <h3 style={{ margin: 0, color: '#0F172A' }}>Recently created</h3>
          <Link to="/platform/organisations" style={{ fontSize: 14, color: '#4F46E5', fontWeight: 600, textDecoration: 'none' }}>
            View all →
          </Link>
        </div>
        {recent.length === 0 ? (
          <div style={{ color: '#64748B', padding: 12 }}>No organisations yet.</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {recent.map((o) => (
              <Link key={o.orgId} to={`/platform/organisations/${o.orgId}`} style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 12px', border: '1px solid #eee', borderRadius: 6, textDecoration: 'none', color: '#0F172A' }}>
                <span>
                  <strong>{o.name}</strong> <span style={{ color: '#64748B' }}>· {o.domain || '-'}</span>
                </span>
                <span style={{ fontSize: 12, padding: '2px 8px', borderRadius: 999, background: (o.status || '').toUpperCase() === 'ACTIVE' ? '#e6f9ed' : '#fee', color: (o.status || '').toUpperCase() === 'ACTIVE' ? '#0a7' : '#d44' }}>
                  {o.status || 'UNKNOWN'}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>

      <div style={{ fontSize: 13 }}>
        <Link to="/platform/organisations" style={{ color: '#818CF8', fontWeight: 500, textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          Go to Organisations →
        </Link>
      </div>
    </div>
  )
}
