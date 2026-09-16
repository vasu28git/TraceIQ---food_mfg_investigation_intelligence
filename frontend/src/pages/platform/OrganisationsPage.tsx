import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteOrganisation, getOrganisations, updateOrganisation } from '../../services/organisationService'
import type { Organisation } from '../../types/organisation'

export function OrganisationsPage() {
  const [orgs, setOrgs] = useState<Organisation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getOrganisations()
      setOrgs(data)
    } catch (err: unknown) {
      const ax = err as { response?: { status?: number; data?: { message?: string } } }
      if (ax.response?.status === 401) setError('Session expired. Please login again.')
      else if (ax.response?.status === 403) setError('Forbidden: PLATFORM_ADMIN required.')
      else setError(ax.response?.data?.message || 'Failed to load organisations')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return orgs.filter((o) => {
      const matchesQuery = !q || o.name.toLowerCase().includes(q) || (o.domain || '').toLowerCase().includes(q) || (o.status || '').toLowerCase().includes(q)
      const matchesStatus = statusFilter === 'ALL' || (o.status || '').toUpperCase() === statusFilter
      return matchesQuery && matchesStatus
    })
  }, [orgs, query, statusFilter])

  const handleDelete = async (orgId: number) => {
    if (!confirm('Delete this organisation? This cannot be undone.')) return
    try {
      await deleteOrganisation(orgId)
      setSuccess('Organisation deleted')
      setOrgs((prev) => prev.filter((o) => o.orgId !== orgId))
      setTimeout(() => setSuccess(null), 2000)
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string } } }
      setError(ax.response?.data?.message || 'Delete failed')
    }
  }

  const handleToggleStatus = async (org: Organisation) => {
    const isActive = (org.status || '').toUpperCase() === 'ACTIVE'
    const nextStatus = isActive ? 'SUSPENDED' : 'ACTIVE'
    if (!confirm(`${isActive ? 'Suspend' : 'Activate'} "${org.name}"?`)) return
    try {
      const updated = await updateOrganisation(org.orgId!, { ...org, status: nextStatus })
      setOrgs((prev) => prev.map((o) => (o.orgId === org.orgId ? updated : o)))
      setSuccess(`Organisation ${nextStatus.toLowerCase()}`)
      setTimeout(() => setSuccess(null), 2000)
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string } } }
      setError(ax.response?.data?.message || 'Status update failed')
    }
  }

  if (loading) return <div style={{ padding: 24 }}>Loading organisations...</div>

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <h2 style={{ margin: 0 }}>Organisations</h2>
        <Link to="/platform/organisations/new" style={{ padding: '8px 14px', background: '#111', color: '#fff', borderRadius: 6, textDecoration: 'none' }}>
          + Create Organisation
        </Link>
      </div>

      {error && <div style={{ padding: 10, background: '#fee', border: '1px solid #fcc', borderRadius: 6, color: '#a00' }}>{error}</div>}
      {success && <div style={{ padding: 10, background: '#e6f9ed', border: '1px solid #b6e9c9', borderRadius: 6, color: '#0a7' }}>{success}</div>}

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <input
          placeholder="Search by name, domain, status..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          style={{ flex: 1, minWidth: 220, padding: '8px 10px', border: '1px solid #ddd', borderRadius: 6 }}
        />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={{ padding: '8px 10px', border: '1px solid #ddd', borderRadius: 6 }}>
          <option value="ALL">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </div>

      {filtered.length === 0 ? (
        <div style={{ padding: 24, textAlign: 'center', color: '#888', background: '#fff', border: '1px solid #eee', borderRadius: 8 }}>
          {orgs.length === 0 ? 'No organisations yet. Create one.' : 'No matches for your search.'}
        </div>
      ) : (
        <div style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
              <thead>
                <tr style={{ background: '#fafafa', textAlign: 'left' }}>
                  <th style={{ padding: '10px 12px', borderBottom: '1px solid #eee' }}>Name</th>
                  <th style={{ padding: '10px 12px', borderBottom: '1px solid #eee' }}>Domain</th>
                  <th style={{ padding: '10px 12px', borderBottom: '1px solid #eee' }}>Status</th>
                  <th style={{ padding: '10px 12px', borderBottom: '1px solid #eee' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((o) => (
                  <tr key={o.orgId} style={{ borderBottom: '1px solid #f0f0f0' }}>
                    <td style={{ padding: '10px 12px' }}>
                      <Link to={`/platform/organisations/${o.orgId}`} style={{ color: '#111', fontWeight: 600, textDecoration: 'none' }}>
                        {o.name}
                      </Link>
                    </td>
                    <td style={{ padding: '10px 12px', color: '#666' }}>{o.domain || '-'}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{ fontSize: 12, padding: '2px 8px', borderRadius: 999, background: (o.status || '').toUpperCase() === 'ACTIVE' ? '#e6f9ed' : '#fee', color: (o.status || '').toUpperCase() === 'ACTIVE' ? '#0a7' : '#d44' }}>
                        {o.status || 'UNKNOWN'}
                      </span>
                    </td>
                    <td style={{ padding: '10px 12px', display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Link to={`/platform/organisations/${o.orgId}`} style={{ padding: '4px 8px', border: '1px solid #ddd', borderRadius: 6, textDecoration: 'none', color: '#111', fontSize: 13 }}>
                        View
                      </Link>
                      <button onClick={() => handleToggleStatus(o)} style={{ padding: '4px 8px', border: '1px solid #ddd', borderRadius: 6, background: '#fff', cursor: 'pointer', fontSize: 13 }}>
                        {(o.status || '').toUpperCase() === 'ACTIVE' ? 'Suspend' : 'Activate'}
                      </button>
                      <button onClick={() => handleDelete(o.orgId!)} style={{ padding: '4px 8px', border: '1px solid #fcc', borderRadius: 6, background: '#fff', color: '#d00', cursor: 'pointer', fontSize: 13 }}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
