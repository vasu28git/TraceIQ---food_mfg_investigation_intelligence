import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/client'
import { apiClient } from '../../api/client'
import type { Complaint, CreateComplaintRequest } from '../../types/investigation'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#0C1222'
const BORDER = '#1A2744'
const ACCENT = '#06B6D4'
const ACCENT_DIM = '#164E63'
const TEXT_MAIN = '#F1F5F9'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

function ComplaintStatusBadge({ complaint }: { complaint: Complaint }) {
  const hasInvestigation = !!complaint.investigation
  return (
    <span style={{
      fontSize: 11, padding: '3px 10px', borderRadius: 999, fontWeight: 700,
      background: hasInvestigation ? '#164E63' : '#3B1C1C',
      color: hasInvestigation ? '#22D3EE' : '#FCA5A5',
      border: `1px solid ${hasInvestigation ? '#0E7490' : '#7F1D1D'}`,
    }}>
      {hasInvestigation ? `Linked → ${complaint.investigation!.investigationKey}` : 'Needs Investigation'}
    </span>
  )
}

function formatDate(d?: string | null) {
  if (!d) return '-'
  try { return new Date(d).toLocaleString() } catch { return d }
}

export function ComplaintsPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<Complaint[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const size = 20

  const linkedCount = items.filter(c => c.investigation).length
  const openCount = items.length - linkedCount

  const [showCreate, setShowCreate] = useState(false)
  const [form, setForm] = useState<CreateComplaintRequest>({ complaintKey: '', title: '', description: '', batchReference: '', externalReference: '', raisedAt: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const load = useCallback(async (p: number) => {
    setLoading(true); setError(null)
    try {
      const res = await apiClient.get<{ content: Complaint[]; totalElements: number; totalPages: number }>(`/complaints`, { params: { page: p, size } })
      const data = res.data as unknown as { content?: Complaint[]; totalElements?: number; totalPages?: number } | Complaint[]
      let content: Complaint[] = []
      let total = 0
      let pages = 0
      if (Array.isArray(data)) {
        content = data
        total = content.length
        pages = 1
      } else {
        content = (data as { content?: Complaint[] }).content ?? []
        total = (data as { totalElements?: number }).totalElements ?? content.length
        pages = (data as { totalPages?: number }).totalPages ?? Math.ceil(total / size)
      }
      setItems(content)
      setTotalElements(total)
      setTotalPages(pages)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 403) setError('Forbidden – missing access to complaints.')
      else setError(getApiErrorMessage(err, 'Failed to load complaints'))
    } finally { setLoading(false) }
  }, [])

  useEffect(() => { load(page) }, [page, load])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault(); setFormError(null)
    if (!form.complaintKey.trim()) { setFormError('Complaint key is required'); return }
    if (!form.title.trim()) { setFormError('Title is required'); return }
    setSubmitting(true)
    try {
      const raisedRaw = form.raisedAt?.trim()
      const payload: Record<string, unknown> = {
        complaintKey: form.complaintKey.trim(),
        title: form.title.trim(),
        description: form.description?.trim() || null,
        batchReference: form.batchReference?.trim() || null,
        externalReference: form.externalReference?.trim() || null,
        raisedAt: raisedRaw ? new Date(raisedRaw).toISOString() : null,
      }
      const res = await apiClient.post<Complaint>(`/complaints`, payload)
      const created = res.data
      setShowCreate(false)
      setForm({ complaintKey: '', title: '', description: '', batchReference: '', externalReference: '', raisedAt: '' })
      navigate(`/organisation/complaints/${created.id}`)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Failed to create complaint')
      if (st === 409) msg = 'Complaint key already exists (409)'
      if (st === 400) msg = msg.includes('Graph not ready') ? 'Graph not ready – ensure canonical sync succeeded and projection is validated (400).' : msg
      setFormError(msg)
    } finally { setSubmitting(false) }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 6, height: 28, borderRadius: 3, background: `linear-gradient(180deg, ${ACCENT}, #0891B2)` }} />
            <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 22, fontWeight: 800 }}>Complaints</h2>
          </div>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginLeft: 16 }}>{totalElements} complaint{totalElements !== 1 ? 's' : ''} — Intake → Investigation flow</div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: 12, marginRight: 12, fontSize: 12 }}>
            {items.length > 0 && (
              <>
                <span style={{ color: '#22D3EE' }}>{linkedCount} linked</span>
                <span style={{ color: TEXT_SEC }}>·</span>
                <span style={{ color: '#FCA5A5' }}>{openCount} open</span>
              </>
            )}
          </div>
          <button onClick={() => load(page)} style={{ padding: '8px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
          <button onClick={() => { setShowCreate(true); setFormError(null) }} style={{ padding: '8px 14px', background: ACCENT, color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>+ Create Complaint</button>
        </div>
      </div>

      {error && <ErrorAlert message={error} onRetry={() => load(page)} />}
      {loading ? <LoadingSpinner label="Loading complaints…" /> : items.length === 0 ? (
        <div style={{ padding: 32, textAlign: 'center', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, color: TEXT_SEC }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No complaints yet</div>
          <div style={{ fontSize: 13, marginTop: 6 }}>Create a complaint to start the intake → investigation flow.</div>
          <button onClick={() => setShowCreate(true)} style={{ marginTop: 12, padding: '8px 14px', background: ACCENT, color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer' }}>Create Complaint</button>
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 820 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#080E1A' }}>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC, fontWeight: 600 }}>Key</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Title</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Batch</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Status</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Created</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map(c => (
                  <tr key={c.id} style={{ borderBottom: `1px solid ${BORDER}` }}>
                    <td onClick={() => navigate(`/organisation/complaints/${c.id}`)} style={{ padding: '10px 12px', color: ACCENT, fontFamily: 'ui-monospace, monospace', fontWeight: 600, cursor: 'pointer' }}>{c.complaintKey}</td>
                    <td onClick={() => navigate(`/organisation/complaints/${c.id}`)} style={{ padding: '10px 12px', color: TEXT_MAIN, maxWidth: 280, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', cursor: 'pointer' }}>{c.title}</td>
                    <td style={{ padding: '10px 12px', fontSize: 12 }}>
                      {c.batchReference ? (
                        <span style={{ padding: '2px 8px', borderRadius: 6, background: ACCENT_DIM, color: '#67E8F9', border: `1px solid #155E75`, fontFamily: 'ui-monospace, monospace', fontSize: 11 }}>{c.batchReference}</span>
                      ) : (
                        <span style={{ color: MUTED }}>—</span>
                      )}
                    </td>
                    <td style={{ padding: '10px 12px' }}><ComplaintStatusBadge complaint={c} /></td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC, fontSize: 12 }}>{formatDate(c.createdAt)}</td>
                    <td style={{ padding: '10px 12px' }}>
                      {c.investigation ? (
                        <button
                          onClick={(e) => { e.stopPropagation(); navigate(`/organisation/investigations/${c.investigation!.id}`) }}
                          style={{ padding: '5px 12px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer', fontSize: 12, fontWeight: 600, whiteSpace: 'nowrap' }}
                        >
                          Open Investigation
                        </button>
                      ) : (
                        <button
                          onClick={(e) => { e.stopPropagation(); navigate(`/organisation/complaints/${c.id}`) }}
                          style={{ padding: '5px 12px', background: 'transparent', border: `1px solid #7F1D1D`, color: '#FCA5A5', borderRadius: 6, cursor: 'pointer', fontSize: 12, whiteSpace: 'nowrap' }}
                        >
                          Link Investigation
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 12, borderTop: `1px solid ${BORDER}` }}>
              <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))} style={{ padding: '6px 10px', background: page === 0 ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page === 0 ? 'not-allowed' : 'pointer' }}>Previous</button>
              <span style={{ fontSize: 12, color: TEXT_SEC, alignSelf: 'center' }}>Page {page + 1} of {totalPages} · {totalElements} total</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)} style={{ padding: '6px 10px', background: page + 1 >= totalPages ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer' }}>Next</button>
            </div>
          )}
        </div>
      )}

      {showCreate && (
        <div onClick={() => !submitting && setShowCreate(false)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 480, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)', maxHeight: '90vh', overflowY: 'auto' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center', position: 'sticky', top: 0, background: '#fff' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Create Complaint</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleCreate} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Complaint Key *</span>
                <input value={form.complaintKey} onChange={e => setForm({ ...form, complaintKey: e.target.value })} placeholder="CMP-2025-001" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Title *</span>
                <input value={form.title} onChange={e => setForm({ ...form, title: e.target.value })} placeholder="Customer complaint title" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Description</span>
                <textarea value={form.description ?? ''} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="Details..." rows={3} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Batch Reference</span>
                  <input value={form.batchReference ?? ''} onChange={e => setForm({ ...form, batchReference: e.target.value })} placeholder="BATCH-001" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>External Reference</span>
                  <input value={form.externalReference ?? ''} onChange={e => setForm({ ...form, externalReference: e.target.value })} placeholder="EXT-001" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
              </div>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Raised At</span>
                <input type="datetime-local" value={form.raisedAt ?? ''} onChange={e => setForm({ ...form, raisedAt: e.target.value })} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <div style={{ fontSize: 11, color: MUTED, background: '#f8fafc', padding: '8px 10px', borderRadius: 8, border: '1px solid #e2e8f0' }}>Organisation forced from session. Source type defaults to MANUAL.</div>
              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
                <button type="button" onClick={() => setShowCreate(false)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : ACCENT, color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Creating…' : 'Create Complaint'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
