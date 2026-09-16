import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteOrganisation, getOrganisation, updateOrganisation } from '../../services/organisationService'
import type { Organisation } from '../../types/organisation'

export function OrganisationDetailsPage() {
  const { orgId } = useParams()
  const id = Number(orgId)
  const navigate = useNavigate()
  const [org, setOrg] = useState<Organisation | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<Organisation>({ name: '', domain: '', description: '', status: 'ACTIVE' })

  useEffect(() => {
    if (!orgId) return
    setLoading(true)
    getOrganisation(id)
      .then((data) => {
        setOrg(data)
        setForm(data)
      })
      .catch((err: unknown) => {
        const ax = err as { response?: { status?: number; data?: { message?: string } } }
        if (ax.response?.status === 401) setError('Session expired.')
        else if (ax.response?.status === 403) setError('Forbidden.')
        else if (ax.response?.status === 404) setError('Organisation not found.')
        else setError('Failed to load organisation')
      })
      .finally(() => setLoading(false))
  }, [orgId, id])

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) {
      setError('Name is required')
      return
    }
    try {
      const updated = await updateOrganisation(id, form)
      setOrg(updated)
      setForm(updated)
      setEditing(false)
      setSuccess('Organisation updated')
      setTimeout(() => setSuccess(null), 2000)
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string } } }
      setError(ax.response?.data?.message || 'Update failed')
    }
  }

  const handleDelete = async () => {
    if (!confirm('Delete this organisation? This cannot be undone.')) return
    try {
      await deleteOrganisation(id)
      navigate('/platform/organisations')
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string } } }
      setError(ax.response?.data?.message || 'Delete failed')
    }
  }

  const handleToggleStatus = async () => {
    if (!org) return
    const next = (org.status || '').toUpperCase() === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
    if (!confirm(`${next === 'ACTIVE' ? 'Activate' : 'Suspend'} "${org.name}"?`)) return
    try {
      const updated = await updateOrganisation(id, { ...org, status: next })
      setOrg(updated)
      setForm(updated)
      setSuccess(`Status changed to ${next}`)
      setTimeout(() => setSuccess(null), 2000)
    } catch (err: unknown) {
      const ax = err as { response?: { data?: { message?: string } } }
      setError(ax.response?.data?.message || 'Status update failed')
    }
  }

  if (loading) return <div style={{ padding: 24 }}>Loading organisation...</div>
  if (error && !org) return <div style={{ padding: 24, color: 'red' }}>{error}</div>
  if (!org) return null

  return (
    <div style={{ maxWidth: 640, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Link to="/platform/organisations" style={{ fontSize: 14, color: '#666', textDecoration: 'none' }}>
        ← Back to organisations
      </Link>

      {success && <div style={{ padding: 10, background: '#e6f9ed', border: '1px solid #b6e9c9', borderRadius: 6, color: '#0a7' }}>{success}</div>}
      {error && <div style={{ padding: 10, background: '#fee', border: '1px solid #fcc', borderRadius: 6, color: '#a00' }}>{error}</div>}

      {!editing ? (
        <div style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h2 style={{ margin: 0 }}>{org.name}</h2>
            <span style={{ fontSize: 12, padding: '4px 10px', borderRadius: 999, background: (org.status || '').toUpperCase() === 'ACTIVE' ? '#e6f9ed' : '#fee', color: (org.status || '').toUpperCase() === 'ACTIVE' ? '#0a7' : '#d44' }}>
              {org.status}
            </span>
          </div>
          <div style={{ fontSize: 14, display: 'flex', flexDirection: 'column', gap: 6, color: '#333' }}>
            <div>
              <strong>Domain:</strong> {org.domain || '-'}
            </div>
            <div>
              <strong>Description:</strong> {org.description || '-'}
            </div>
            <div>
              <strong>ID:</strong> {org.orgId}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
            <button onClick={() => setEditing(true)} style={{ padding: '8px 12px', background: '#111', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer' }}>
              Edit
            </button>
            <button onClick={handleToggleStatus} style={{ padding: '8px 12px', background: '#fff', border: '1px solid #ddd', borderRadius: 6, cursor: 'pointer' }}>
              {(org.status || '').toUpperCase() === 'ACTIVE' ? 'Suspend' : 'Activate'}
            </button>
            <button onClick={handleDelete} style={{ padding: '8px 12px', background: '#fff', border: '1px solid #fcc', color: '#d00', borderRadius: 6, cursor: 'pointer' }}>
              Delete
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleUpdate} style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 20, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h3 style={{ margin: 0 }}>Edit Organisation</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label>Name *</label>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required style={{ padding: 8, border: '1px solid #ccc', borderRadius: 4 }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label>Domain</label>
            <input value={form.domain || ''} onChange={(e) => setForm({ ...form, domain: e.target.value })} style={{ padding: 8, border: '1px solid #ccc', borderRadius: 4 }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label>Description</label>
            <textarea value={form.description || ''} onChange={(e) => setForm({ ...form, description: e.target.value })} rows={3} style={{ padding: 8, border: '1px solid #ccc', borderRadius: 4 }} />
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label>Status</label>
            <select value={form.status || 'ACTIVE'} onChange={(e) => setForm({ ...form, status: e.target.value })} style={{ padding: 8, border: '1px solid #ccc', borderRadius: 4 }}>
              <option value="ACTIVE">ACTIVE</option>
              <option value="SUSPENDED">SUSPENDED</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button type="submit" style={{ flex: 1, padding: 10, background: '#111', color: '#fff', border: 0, borderRadius: 4, cursor: 'pointer' }}>
              Save
            </button>
            <button type="button" onClick={() => setEditing(false)} style={{ padding: 10, border: '1px solid #ddd', background: '#fff', borderRadius: 4, cursor: 'pointer' }}>
              Cancel
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
