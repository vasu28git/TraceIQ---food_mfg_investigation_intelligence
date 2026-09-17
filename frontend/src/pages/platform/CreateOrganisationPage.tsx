import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createOrganisation } from '../../services/organisationService'
import type { OrganisationProvisioningResult } from '../../types/organisation'

export function CreateOrganisationPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [domain, setDomain] = useState('')
  const [description, setDescription] = useState('')
  const [status, setStatus] = useState('ACTIVE')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<OrganisationProvisioningResult | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    setLoading(true)
    try {
      const res = await createOrganisation({
        name: name.trim(),
        domain: domain.trim() || undefined,
        description: description.trim() || undefined,
        status,
      })
      setResult(res)
    } catch (err: unknown) {
      const ax = err as { response?: { status?: number; data?: { message?: string; error?: string } } }
      const status = ax.response?.status
      const backendMsg = ax.response?.data?.message || ax.response?.data?.error
      if (status === 401) setError('Session expired. Please login again.')
      else if (status === 403) setError(backendMsg || 'Forbidden: PLATFORM_ADMIN required.')
      else if (status === 400) setError(backendMsg || 'Validation failed')
      else if (status === 409) setError(backendMsg || 'Conflict: organisation or user already exists (409)')
      else if (status === 500) setError(backendMsg ? `${backendMsg} (500)` : 'Internal server error (500) – check backend logs')
      else setError(backendMsg ? `${backendMsg} (${status || 'error'})` : `Failed to create organisation${status ? ` (${status})` : ''}`)
    } finally {
      setLoading(false)
    }
  }

  if (result) {
    return (
      <div style={{ maxWidth: 640, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div style={{ padding: 16, background: '#e6f9ed', border: '1px solid #b6e9c9', borderRadius: 8 }}>
          <h3 style={{ margin: 0, color: '#0a7' }}>Organisation created</h3>
          <p style={{ margin: '8px 0 0', color: '#333', fontSize: 14 }}>The organisation has been provisioned with its initial ADMIN role and user.</p>
        </div>

        <div style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 16, display: 'flex', flexDirection: 'column', gap: 12, color: '#0F172A' }}>
          <h4 style={{ margin: 0, color: '#0F172A' }}>Organisation</h4>
          <div style={{ fontSize: 14, display: 'flex', flexDirection: 'column', gap: 4, color: '#334155' }}>
            <div>
              <strong>Name:</strong> {result.organisation.name}
            </div>
            <div>
              <strong>Domain:</strong> {result.organisation.domain || '-'}
            </div>
            <div>
              <strong>Status:</strong> {result.organisation.status}
            </div>
            <div>
              <strong>ID:</strong> {result.organisation.orgId}
            </div>
          </div>
        </div>

        <div style={{ background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 16, display: 'flex', flexDirection: 'column', gap: 12, color: '#0F172A' }}>
          <h4 style={{ margin: 0, color: '#0F172A' }}>Initial Admin</h4>
          <div style={{ fontSize: 14, display: 'flex', flexDirection: 'column', gap: 4, color: '#334155' }}>
            <div>
              <strong>Username:</strong> {result.ogUser.username}
            </div>
            <div>
              <strong>Role:</strong> {result.adminRole.name}
            </div>
            <div>
              <strong>Status:</strong> {result.ogUser.status}
            </div>
            <div style={{ padding: '8px 10px', background: '#fff8e1', border: '1px solid #ffecb3', borderRadius: 6, fontSize: 13, color: '#92400e' }}>
              Initial password is the configured provisioning password (default <code>ChangeMe123!</code>). The user must change it on first login (<code>mustChangePassword=true</code>).
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <Link to={`/platform/organisations/${result.organisation.orgId}`} style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', borderRadius: 6, textDecoration: 'none', fontWeight: 600 }}>
            View Organisation
          </Link>
          <Link to="/platform/organisations" style={{ padding: '8px 14px', border: '1px solid #CBD5E1', borderRadius: 6, textDecoration: 'none', color: '#334155', background: '#fff', fontWeight: 500 }}>
            Back to list
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 520, margin: '0 auto', background: '#fff', border: '1px solid #e5e5e5', borderRadius: 8, padding: 24, color: '#0F172A' }}>
      <h2 style={{ margin: 0, marginBottom: 8, color: '#0F172A' }}>Create Organisation</h2>
      <p style={{ margin: 0, marginBottom: 16, fontSize: 14, color: '#475569' }}>Provision a new organisation with its initial ADMIN role and user.</p>

      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label htmlFor="name" style={{ color: '#334155', fontWeight: 600, fontSize: 13 }}>Name *</label>
          <input id="name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="Acme Corp" style={{ padding: 8, border: '1px solid #CBD5E1', borderRadius: 4, color: '#0F172A', background: '#fff', fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label htmlFor="domain" style={{ color: '#334155', fontWeight: 600, fontSize: 13 }}>Domain</label>
          <input id="domain" value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="acme.com" style={{ padding: 8, border: '1px solid #CBD5E1', borderRadius: 4, color: '#0F172A', background: '#fff', fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label htmlFor="description" style={{ color: '#334155', fontWeight: 600, fontSize: 13 }}>Description</label>
          <textarea id="description" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Organisation description" rows={3} style={{ padding: 8, border: '1px solid #CBD5E1', borderRadius: 4, color: '#0F172A', background: '#fff', fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label htmlFor="status" style={{ color: '#334155', fontWeight: 600, fontSize: 13 }}>Status</label>
          <select id="status" value={status} onChange={(e) => setStatus(e.target.value)} style={{ padding: 8, border: '1px solid #CBD5E1', borderRadius: 4, color: '#0F172A', background: '#fff', fontSize: 14 }}>
            <option value="ACTIVE">ACTIVE</option>
            <option value="SUSPENDED">SUSPENDED</option>
            <option value="INACTIVE">INACTIVE</option>
          </select>
        </div>

        {error && <div style={{ padding: 10, background: '#fee', border: '1px solid #fcc', borderRadius: 6, color: '#a00', fontSize: 14 }}>{error}</div>}

        <div style={{ display: 'flex', gap: 12, marginTop: 4 }}>
          <button type="submit" disabled={loading} style={{ flex: 1, padding: 10, background: '#6366F1', color: '#fff', border: 0, borderRadius: 4, cursor: 'pointer', fontWeight: 600, fontSize: 14 }}>
            {loading ? 'Creating...' : 'Create Organisation'}
          </button>
          <button type="button" onClick={() => navigate('/platform/organisations')} style={{ padding: 10, border: '1px solid #CBD5E1', background: '#fff', color: '#334155', borderRadius: 4, cursor: 'pointer', fontWeight: 500, fontSize: 14 }}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
