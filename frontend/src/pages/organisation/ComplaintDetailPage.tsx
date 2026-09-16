import { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../api/client'
import type { Complaint } from '../../types/investigation'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

function formatDate(d?: string | null) {
  if (!d) return '-'
  try { return new Date(d).toLocaleString() } catch { return d }
}

export function ComplaintDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const complaintId = id ? Number(id) : NaN

  const [complaint, setComplaint] = useState<Complaint | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // create investigation from complaint
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({ investigationKey: '', title: '', description: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const load = async () => {
    if (Number.isNaN(complaintId)) { setError('Invalid complaint ID'); setLoading(false); return }
    setLoading(true); setError(null)
    try {
      const res = await apiClient.get<Complaint>(`/complaints/${complaintId}`)
      setComplaint(res.data)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 404) setError('Complaint not found (404).')
      else if (st === 403) setError('Forbidden – no access to this complaint.')
      else setError(getApiErrorMessage(err, 'Failed to load complaint'))
    } finally { setLoading(false) }
  }

  useEffect(() => { load() }, [complaintId])

  const handleCreateInvestigation = async (e: React.FormEvent) => {
    e.preventDefault(); setFormError(null)
    if (!form.investigationKey.trim()) { setFormError('Investigation key is required'); return }
    if (!form.title.trim()) { setFormError('Title is required'); return }
    setSubmitting(true)
    try {
      const res = await apiClient.post(`/complaints/${complaintId}/investigation`, {
        investigationKey: form.investigationKey.trim(),
        title: form.title.trim(),
        description: form.description?.trim() || null,
      }, { timeout: 60000 })
      const inv = res.data as { id: number; investigationKey: string }
      navigate(`/organisation/investigations/${inv.id}`)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Failed to create investigation')
      if (st === 409) msg = 'Investigation key already exists (409)'
      if (st === 400 && msg.toLowerCase().includes('graph not ready')) msg = 'Graph not ready – ensure canonical sync succeeded and projection is validated (400).'
      setFormError(msg)
    } finally { setSubmitting(false) }
  }

  if (loading) return <LoadingSpinner label="Loading complaint…" />
  if (error) return <ErrorAlert message={error} onRetry={load} />
  if (!complaint) return <div style={{ padding: 24, color: TEXT_SEC }}>Complaint not found.</div>

  const hasInvestigation = !!complaint.investigation

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12, color: '#64748B' }}>
        <Link to="/organisation/complaints" style={{ color: '#60A5FA', textDecoration: 'none' }}>← Complaints</Link>
        <span>·</span>
        <span style={{ color: TEXT_SEC }}>Complaint #{complaint.id}</span>
      </div>

      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 18, fontWeight: 800 }}>{complaint.title}</h2>
            <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4, fontFamily: 'ui-monospace, monospace' }}>{complaint.complaintKey} · {complaint.sourceType}</div>
          </div>
          <div style={{ fontSize: 11, padding: '4px 8px', borderRadius: 999, background: hasInvestigation ? '#dcfce7' : '#e0e7ff', color: hasInvestigation ? '#166534' : '#3730a3', fontWeight: 700 }}>
            {hasInvestigation ? 'Linked to investigation' : 'No investigation yet'}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 8, fontSize: 13, marginTop: 16 }}>
          <span style={{ color: MUTED, fontWeight: 600 }}>ID</span><span style={{ fontFamily: 'ui-monospace, monospace' }}>{complaint.id}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Description</span><span>{complaint.description || '-'}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Batch Ref</span><span>{complaint.batchReference || '-'}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>External Ref</span><span>{complaint.externalReference || '-'}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Raised At</span><span>{formatDate(complaint.raisedAt)}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Received At</span><span>{formatDate(complaint.receivedAt)}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Created</span><span>{formatDate(complaint.createdAt)}</span>
          <span style={{ color: MUTED, fontWeight: 600 }}>Updated</span><span>{formatDate(complaint.updatedAt)}</span>
        </div>

        <div style={{ marginTop: 16, padding: 12, background: '#0F172A', border: `1px solid ${BORDER}`, borderRadius: 8 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: TEXT_MAIN }}>Investigation</div>
          {hasInvestigation ? (
            <div style={{ marginTop: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
              <div>
                <div style={{ fontSize: 13, color: TEXT_MAIN, fontWeight: 600 }}>{complaint.investigation!.title} <span style={{ fontSize: 11, color: TEXT_SEC, fontFamily: 'ui-monospace, monospace' }}>({complaint.investigation!.investigationKey})</span></div>
                <div style={{ fontSize: 12, color: TEXT_SEC }}>{complaint.investigation!.status} · #{complaint.investigation!.id}</div>
              </div>
              <Link to={`/organisation/investigations/${complaint.investigation!.id}`} style={{ padding: '8px 12px', background: '#6366F1', color: '#fff', borderRadius: 8, textDecoration: 'none', fontSize: 13, fontWeight: 600 }}>Open Investigation</Link>
            </div>
          ) : (
            <div style={{ marginTop: 8 }}>
              <p style={{ fontSize: 13, color: TEXT_SEC, margin: 0 }}>No investigation linked to this complaint yet.</p>
              <button onClick={() => { setShowCreate(true); setForm({ investigationKey: '', title: complaint.title, description: complaint.description || '' }); setFormError(null) }} style={{ marginTop: 10, padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>Create Investigation</button>
            </div>
          )}
        </div>
      </div>

      {showCreate && !hasInvestigation && (
        <div onClick={() => !submitting && setShowCreate(false)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 480, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Create Investigation from Complaint</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleCreateInvestigation} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Investigation Key *</span>
                <input value={form.investigationKey} onChange={e => setForm({ ...form, investigationKey: e.target.value })} placeholder="INV-2025-001" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Title *</span>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="Investigation title" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Description</span>
                <textarea value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="Details..." rows={3} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" onClick={() => setShowCreate(false)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Creating…' : 'Create'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
