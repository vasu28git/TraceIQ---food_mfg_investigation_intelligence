import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { createInvestigation, getInvestigations } from '../../services/investigationService'
import { getApiErrorMessage } from '../../api/client'
import type { Investigation } from '../../types/investigation'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'

function StatusBadge({ status }: { status?: string }) {
  const s = (status || 'UNKNOWN').toUpperCase()
  const map: Record<string, { bg: string; fg: string }> = {
    DRAFT: { bg: '#e2e8f0', fg: '#475569' },
    ACTIVE: { bg: '#dcfce7', fg: '#166534' },
    COMPLETED: { bg: '#dbeafe', fg: '#1e40af' },
    ARCHIVED: { bg: '#1e293b', fg: '#94a3b8' },
  }
  const c = map[s] || { bg: '#e2e8f0', fg: '#475569' }
  return <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{s}</span>
}

export function InvestigationsPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<Investigation[]>([])
  const [page, setPage] = useState(0)
  const size = 20
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState({
    investigationKey: '', title: '', description: '',
    batchReference: '', productReference: '', orderReference: '',
    incidentStart: '', incidentEnd: '',
  })
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async (p: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await getInvestigations(p, size)
      const content = (res as unknown as { content: Investigation[] }).content ?? []
      setItems(Array.isArray(content) ? content : [])
      setTotalElements((res as unknown as { totalElements: number }).totalElements ?? content.length)
      setTotalPages((res as unknown as { totalPages: number }).totalPages ?? Math.ceil(content.length / size))
      setPage(p)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 403) setError('You don’t have access to investigations (403).')
      else       setError(getApiErrorMessage(err, 'Failed to load investigations'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(0) }, [load])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    if (!form.investigationKey.trim() || !form.title.trim()) {
      setFormError('Incident Key and Title are required')
      return
    }
    // Validate incident dates if both supplied
    if (form.incidentStart && form.incidentEnd) {
      const s = new Date(form.incidentStart)
      const en = new Date(form.incidentEnd)
      if (!isNaN(s.getTime()) && !isNaN(en.getTime()) && en < s) {
        setFormError('Incident End must not be before Incident Start')
        return
      }
    }
    setSubmitting(true)
    try {
      const created = await createInvestigation({
        investigationKey: form.investigationKey.trim(),
        title: form.title.trim(),
        description: form.description.trim() || null,
        batchReference: form.batchReference.trim() || null,
        productReference: form.productReference.trim() || null,
        orderReference: form.orderReference.trim() || null,
        incidentStart: form.incidentStart ? new Date(form.incidentStart).toISOString() : null,
        incidentEnd: form.incidentEnd ? new Date(form.incidentEnd).toISOString() : null,
      })
      setShowCreate(false)
      setForm({ investigationKey: '', title: '', description: '', batchReference: '', productReference: '', orderReference: '', incidentStart: '', incidentEnd: '' })
      // Prefer navigating directly to workspace using returned ID
      if (created?.id) navigate(`/organisation/investigations/${created.id}`)
      else await load(0)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 409) setFormError('Incident key already exists in this organisation (409).')
      else if (st === 400) setFormError(getApiErrorMessage(err, 'Invalid input (400).'))
      else if (st === 403) setFormError('You don’t have access to create investigations (403).')
      else setFormError(getApiErrorMessage(err, 'Failed to create incident'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Investigations</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>
            {totalElements} incident{totalElements !== 1 ? 's' : ''} · Page {page + 1} of {totalPages || 1}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => load(page)} style={{ padding: '8px 12px', background: '#111827', border: '1px solid #1E293B', color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
          <button onClick={() => setShowCreate(true)} style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>
            + Create Incident
          </button>
        </div>
      </div>

      {loading ? (
        <div role="status" aria-live="polite" style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading investigations…</div>
      ) : error ? (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button onClick={() => load(page)} style={{ background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer', fontSize: 12 }}>Retry</button>
        </div>
      ) : items.length === 0 ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 32, textAlign: 'center' }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No investigations yet</div>
          <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>Create your first incident to start the evidence → traceability → timeline → checks → decisions workflow.</div>
          <button onClick={() => setShowCreate(true)} style={{ marginTop: 12, padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>Create incident</button>
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 720 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#0F172A' }}>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC, fontWeight: 600 }}>Investigation Key</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Title</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Source Complaint</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Batch</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Status</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Created</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Updated</th>
                </tr>
              </thead>
              <tbody>
                {items.map((inv) => (
                  <tr key={inv.id} onClick={() => navigate(`/organisation/investigations/${inv.id}`)} style={{ borderBottom: `1px solid ${BORDER}`, cursor: 'pointer' }}>
                    <td style={{ padding: '10px 12px', color: '#60A5FA', fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{inv.investigationKey}</td>
                    <td style={{ padding: '10px 12px', color: TEXT_MAIN, maxWidth: 260, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{inv.title}</td>
                    <td style={{ padding: '10px 12px', color: inv.complaintKey ? '#A5B4FC' : TEXT_SEC, fontFamily: inv.complaintKey ? 'ui-monospace, monospace' : undefined, fontSize: 12 }}>{inv.complaintKey || 'Standalone investigation'}</td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC, fontSize: 12 }}>{(inv as unknown as { batchReference?: string }).batchReference || '-'}</td>
                    <td style={{ padding: '10px 12px' }}><StatusBadge status={inv.status} /></td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC, fontSize: 12 }}>{inv.createdAt ? new Date(inv.createdAt).toLocaleString() : '-'}</td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC, fontSize: 12 }}>{inv.updatedAt ? new Date(inv.updatedAt).toLocaleString() : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 12, borderTop: `1px solid ${BORDER}`, alignItems: 'center' }}>
            <button disabled={page === 0} onClick={() => load(page - 1)} style={{ padding: '6px 10px', background: page === 0 ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page === 0 ? 'not-allowed' : 'pointer' }}>Previous</button>
            <span style={{ fontSize: 12, color: TEXT_SEC }}>Page {page + 1} of {totalPages || 1} · {totalElements} total</span>
            <button disabled={page + 1 >= totalPages} onClick={() => load(page + 1)} style={{ padding: '6px 10px', background: page + 1 >= totalPages ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer' }}>Next</button>
          </div>
        </div>
      )}

      {showCreate && (
        <div onClick={() => !submitting && setShowCreate(false)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={(e) => e.stopPropagation()} style={{ width: 640, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'sticky', top: 0, background: '#fff' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Create Incident</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleCreate} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ fontSize: 11, color: '#64748B', background: '#f8fafc', padding: '8px 10px', borderRadius: 8, border: '1px solid #e2e8f0' }}>Incident is the domain term for Investigation persistence (`/api/investigations`). PostgreSQL is source of truth — no Neo4j required.</div>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Incident Key *</span>
                <input value={form.investigationKey} onChange={(e) => setForm({ ...form, investigationKey: e.target.value })} placeholder="e.g. INC-2025-001" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Title *</span>
                <input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="e.g. Batch discrepancy review" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Description</span>
                <textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional context" rows={3} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Batch Reference</span>
                  <input value={form.batchReference} onChange={(e) => setForm({ ...form, batchReference: e.target.value })} placeholder="BATCH-001" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Product Reference</span>
                  <input value={form.productReference} onChange={(e) => setForm({ ...form, productReference: e.target.value })} placeholder="PROD-X" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
              </div>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Order Reference</span>
                <input value={form.orderReference} onChange={(e) => setForm({ ...form, orderReference: e.target.value })} placeholder="ORD-99" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Incident Start</span>
                  <input type="datetime-local" value={form.incidentStart} onChange={(e) => setForm({ ...form, incidentStart: e.target.value })} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Incident End</span>
                  <input type="datetime-local" value={form.incidentEnd} onChange={(e) => setForm({ ...form, incidentEnd: e.target.value })} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
              </div>
              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
                <button type="button" onClick={() => setShowCreate(false)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Creating…' : 'Create Incident'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
