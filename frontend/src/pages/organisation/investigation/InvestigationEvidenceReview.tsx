import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { getInvestigationEvidenceDetail, getInvestigationEvidenceSummary, listInvestigationEvidence, saveInvestigationEvidenceAssessment } from '../../../services/investigationService'
import type { Investigation, InvestigationEvidence, InvestigationEvidenceAssessmentRequest } from '../../../types/investigation'

const CARD = '#111827'
const PANEL = '#0B1120'
const BORDER = '#1E293B'
const TEXT = '#F8FAFC'
const SECONDARY = '#94A3B8'
const MUTED = '#64748B'
const EMPTY: InvestigationEvidenceAssessmentRequest = { reviewStatus: 'PENDING_REVIEW', relevance: null, importance: null, assessment: null, investigatorNotes: '' }

function dateText(value?: string | null) { if (!value) return '-'; try { return new Date(value).toLocaleString() } catch { return value } }
function payloadFields(payload?: string | null) {
  if (!payload) return [] as [string, unknown][]
  try {
    const root = JSON.parse(payload) as Record<string, unknown>
    const source = typeof root.originalPayload === 'string' ? JSON.parse(root.originalPayload) as Record<string, unknown> : root
    return Object.entries(source)
  } catch { return [] as [string, unknown][] }
}
function Badge({ children, tone = '#334155' }: { children: ReactNode; tone?: string }) { return <span style={{ background: tone, color: '#E2E8F0', borderRadius: 999, padding: '3px 8px', fontSize: 11, fontWeight: 700 }}>{children}</span> }

type Summary = { totalEvidence: number; reviewed: number; pendingReview: number; sourceSystems: string[] }

export function InvestigationEvidenceReview({ investigation }: { investigation: Investigation }) {
  const [items, setItems] = useState<InvestigationEvidence[]>([])
  const [summary, setSummary] = useState<Summary>({ totalEvidence: 0, reviewed: 0, pendingReview: 0, sourceSystems: [] })
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal] = useState(0)
  const [search, setSearch] = useState('')
  const [source, setSource] = useState('')
  const [review, setReview] = useState('')
  const [selected, setSelected] = useState<InvestigationEvidence | null>(null)
  const [form, setForm] = useState<InvestigationEvidenceAssessmentRequest>(EMPTY)
  const [savedForm, setSavedForm] = useState<InvestigationEvidenceAssessmentRequest>(EMPTY)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const size = 10

  const refreshSummary = useCallback(async () => {
    try { setSummary(await getInvestigationEvidenceSummary(investigation.id)) } catch { /* list errors remain visible */ }
  }, [investigation.id])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await listInvestigationEvidence(investigation.id, page, size, { search: search || undefined, sourceType: source || undefined })
      setItems(response.content || [])
      setTotal(response.totalElements || 0)
      setTotalPages(response.totalPages || 0)
    } catch (err: unknown) { setError(getApiErrorMessage(err, 'Unable to load investigation evidence')) } finally { setLoading(false) }
  }, [investigation.id, page, search, source])

  useEffect(() => { load().catch(() => undefined); refreshSummary() }, [load, refreshSummary])

  const openEvidence = async (item: InvestigationEvidence) => {
    setSelected(item); setDetailError(null); setSuccess(null)
    try {
      const loaded = await getInvestigationEvidenceDetail(investigation.id, item.stableId)
      const next = { reviewStatus: loaded.reviewStatus || 'PENDING_REVIEW', relevance: loaded.relevance || null, importance: loaded.importance || null, assessment: loaded.assessment || null, investigatorNotes: loaded.investigatorNotes || '' } as InvestigationEvidenceAssessmentRequest
      setSelected(loaded); setForm(next); setSavedForm(next)
    } catch (err: unknown) { setDetailError(getApiErrorMessage(err, 'Unable to load evidence details')) }
  }

  const dirty = JSON.stringify(form) !== JSON.stringify(savedForm)
  const close = () => {
    if (dirty && !window.confirm('Unsaved assessment\nYou have unsaved changes. Leave without saving?')) return
    setSelected(null); setSuccess(null)
  }

  const save = async () => {
    if (!selected) return
    setSaving(true); setDetailError(null); setSuccess(null)
    try {
      const saved = await saveInvestigationEvidenceAssessment(investigation.id, selected.stableId, form)
      const next = { reviewStatus: saved.reviewStatus || 'PENDING_REVIEW', relevance: saved.relevance || null, importance: saved.importance || null, assessment: saved.assessment || null, investigatorNotes: saved.investigatorNotes || '' } as InvestigationEvidenceAssessmentRequest
      setSelected(saved); setItems(current => current.map(item => item.stableId === saved.stableId ? saved : item)); setForm(next); setSavedForm(next); setSuccess('Assessment saved'); await refreshSummary()
    } catch (err: unknown) { setDetailError(getApiErrorMessage(err, 'Unable to save assessment. Please try again.')) } finally { setSaving(false) }
  }

  const nextPending = () => {
    const next = items.find(item => (item.reviewStatus || 'PENDING_REVIEW') === 'PENDING_REVIEW' && item.stableId !== selected?.stableId)
    if (next) openEvidence(next)
  }

  const visibleItems = review ? items.filter(item => (item.reviewStatus || 'PENDING_REVIEW') === review) : items
  const sourceOptions = useMemo(() => Array.from(new Set(items.map(item => item.sourceType).filter(Boolean))), [items])
  const importanceRank: Record<string, number> = { HIGH: 0, CRITICAL: 0, MEDIUM: 1, IMPORTANT: 1, LOW: 2, CONTEXT: 2 }
  const relevanceGroup = (item: InvestigationEvidence) => {
    const relevance = String(item.relevance || '')
    if (relevance === 'RELEVANT' || relevance === 'HIGH') return 'Relevant'
    if (relevance === 'NOT_RELEVANT' || relevance === 'LOW') return 'Not relevant'
    return 'Pending relevance review'
  }
  const groupedItems = useMemo(() => {
    const groups = new Map<string, InvestigationEvidence[]>()
    for (const item of visibleItems) {
      const group = relevanceGroup(item)
      const entries = groups.get(group) || []
      entries.push(item)
      groups.set(group, entries)
    }
    for (const entries of groups.values()) {
      entries.sort((left, right) => (importanceRank[left.importance || ''] ?? 3) - (importanceRank[right.importance || ''] ?? 3) || (left.stableId || '').localeCompare(right.stableId || ''))
    }
    return ['Relevant', 'Pending relevance review', 'Not relevant']
      .filter(group => groups.has(group))
      .map(group => ({ group, items: groups.get(group) || [] }))
  }, [visibleItems])

  return <div data-evidence-review style={{ display: 'flex', flexDirection: 'column', gap: 14, color: TEXT }}>
    <style>{`
      [data-evidence-review] aside {
        box-sizing: border-box;
        width: min(620px, 100vw) !important;
        padding: 28px !important;
        color: ${TEXT};
      }
      [data-evidence-review] aside > div:first-child {
        align-items: flex-start;
        border-bottom: 1px solid ${BORDER};
        padding-bottom: 18px;
        margin-bottom: 20px;
      }
      [data-evidence-review] aside > div:first-child h2 {
        margin: 7px 0 4px;
        font-size: 20px;
        line-height: 1.2;
      }
      [data-evidence-review] aside > div:first-child button {
        width: 32px;
        height: 32px;
        border: 1px solid ${BORDER};
        border-radius: 7px;
        background: ${PANEL};
        color: ${SECONDARY};
        font-size: 20px;
        line-height: 1;
        cursor: pointer;
      }
      [data-evidence-review] aside section { margin-top: 22px; }
      [data-evidence-review] aside section h3 {
        margin: 0 0 10px;
        color: ${TEXT};
        font-size: 14px;
      }
      [data-evidence-review] aside label {
        display: flex;
        flex-direction: column;
        gap: 6px;
        margin-top: 12px;
        color: ${SECONDARY};
        font-size: 12px;
        font-weight: 600;
      }
      [data-evidence-review] aside select,
      [data-evidence-review] aside textarea {
        box-sizing: border-box;
        width: 100%;
        border: 1px solid ${BORDER};
        border-radius: 7px;
        background: ${PANEL};
        color: ${TEXT};
        padding: 9px 10px;
        font: inherit;
        outline: none;
      }
      [data-evidence-review] aside select:focus,
      [data-evidence-review] aside textarea:focus {
        border-color: #60A5FA;
        box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.18);
      }
      [data-evidence-review] aside textarea {
        min-height: 112px;
        resize: vertical;
        line-height: 1.45;
      }
      [data-evidence-review] aside section:last-child > div:last-child {
        display: flex;
        flex-wrap: wrap;
        gap: 9px;
        margin-top: 16px;
      }
      [data-evidence-review] aside section:last-child button {
        border: 1px solid ${BORDER};
        border-radius: 7px;
        padding: 10px 14px;
        font: inherit;
        font-size: 12px;
        font-weight: 700;
        cursor: pointer;
      }
      [data-evidence-review] aside section:last-child button:first-of-type {
        background: #2563EB;
        color: white;
        border-color: #2563EB;
      }
      [data-evidence-review] aside section:last-child button:last-of-type {
        background: ${PANEL};
        color: ${TEXT};
      }
      [data-evidence-review] aside section:last-child button:disabled {
        opacity: 0.55;
        cursor: not-allowed;
      }
      @media (max-width: 640px) {
        [data-evidence-review] aside { padding: 20px !important; }
      }
    `}</style>
    <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 18 }}>
      <div style={{ color: '#60A5FA', fontSize: 11, fontWeight: 800, textTransform: 'uppercase' }}>Evidence Review</div>
      <h2 style={{ margin: '5px 0', fontSize: 21 }}>Review and assess discovered evidence</h2>
      <div style={{ color: SECONDARY, fontSize: 13 }}>{investigation.investigationKey} · {investigation.title}</div>
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>{investigation.batchReference && <Badge tone="#78350F">Batch: {investigation.batchReference}</Badge>}<Badge tone="#1E3A5F">Status: {investigation.status}</Badge></div>
    </section>

    <section style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 10 }}>
      {[['Evidence records', summary.totalEvidence || total], ['Source systems', summary.sourceSystems.length], ['Reviewed', summary.reviewed], ['Pending review', summary.pendingReview]].map(([label, value]) => <div key={String(label)} style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 14 }}><div style={{ color: MUTED, fontSize: 11, textTransform: 'uppercase', fontWeight: 700 }}>{label}</div><div style={{ fontSize: 24, fontWeight: 800 }}>{value}</div></div>)}
    </section>

    {summary.totalEvidence > 0 && summary.reviewed === summary.totalEvidence && <div role="status" style={{ background: '#14532D', color: '#DCFCE7', borderRadius: 8, padding: 12 }}><strong>Evidence review complete</strong><div>All discovered evidence has been reviewed. You can now move to investigation findings.</div></div>}
    {success && <div role="status" style={{ background: '#14532D', color: '#DCFCE7', borderRadius: 8, padding: 12 }}>{success}</div>}
    {error && <div role="alert" style={{ background: '#451A1A', color: '#FECACA', borderRadius: 8, padding: 12 }}>{error}</div>}
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}><input value={search} onChange={event => { setSearch(event.target.value); setPage(0) }} placeholder="Search evidence" style={{ flex: 1, minWidth: 220, background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }} /><select value={source} onChange={event => { setSource(event.target.value); setPage(0) }} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }}><option value="">All sources</option>{sourceOptions.map(value => <option key={value} value={value || ''}>{value}</option>)}</select><select value={review} onChange={event => setReview(event.target.value)} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }}><option value="">All review states</option><option value="PENDING_REVIEW">Pending review</option><option value="REVIEWED">Reviewed</option></select></div>

    {loading ? <div role="status" style={{ background: CARD, padding: 28, textAlign: 'center', color: SECONDARY }}>Loading evidence…</div> : visibleItems.length === 0 ? <div style={{ background: CARD, padding: 32, textAlign: 'center', color: SECONDARY }}>No evidence has been discovered for this investigation yet.</div> : <section style={{ overflowX: 'auto', background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10 }}><table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 760 }}><thead><tr style={{ color: MUTED, fontSize: 11, textTransform: 'uppercase', textAlign: 'left' }}>{['Evidence', 'Source', 'Title', 'Status', 'Discovery', 'Review'].map(label => <th key={label} style={{ padding: 12 }}>{label}</th>)}</tr></thead><tbody>{groupedItems.map(({ group, items: groupItems }) => <><tr key={`${group}-header`}><th colSpan={6} style={{ padding: '12px 14px 8px', color: '#93C5FD', background: PANEL, textAlign: 'left', fontSize: 12 }}>{group} · {groupItems.length}</th></tr>{groupItems.map(item => <tr key={item.stableId} onClick={() => openEvidence(item)} style={{ cursor: 'pointer', borderTop: `1px solid ${BORDER}` }}><td style={{ padding: 12, color: '#93C5FD', fontFamily: 'monospace' }}>{item.sourceRecordId || item.stableId}</td><td style={{ padding: 12 }}>{item.sourceType || '-'}</td><td style={{ padding: 12 }}>{item.title || '-'}</td><td style={{ padding: 12 }}>{item.status || '-'}</td><td style={{ padding: 12, color: SECONDARY }}>{item.correlationReason || item.associationType || '-'}</td><td style={{ padding: 12 }}><Badge tone={item.reviewStatus === 'REVIEWED' ? '#14532D' : '#78350F'}>{item.reviewStatus === 'REVIEWED' ? 'Reviewed' : 'Pending review'}</Badge></td></tr>)}</>)}</tbody></table><div style={{ padding: 12, color: SECONDARY, fontSize: 12, display: 'flex', justifyContent: 'space-between' }}><span>Showing {visibleItems.length} of {total}</span><span><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button> Page {page + 1} of {Math.max(1, totalPages)} <button disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Next</button></span></div></section>}

    {selected && <div role="dialog" aria-label="Evidence details" style={{ position: 'fixed', inset: 0, background: 'rgba(2,6,23,0.72)', zIndex: 40, display: 'flex', justifyContent: 'flex-end' }} onClick={close}><aside onClick={event => event.stopPropagation()} style={{ width: 'min(620px, 100%)', height: '100%', overflowY: 'auto', background: CARD, padding: 22 }}><div style={{ display: 'flex', justifyContent: 'space-between' }}><div><div style={{ color: '#60A5FA', fontSize: 11, textTransform: 'uppercase' }}>Evidence details</div><h2>{selected.sourceRecordId || selected.stableId}</h2><div style={{ color: SECONDARY }}>{selected.sourceType || 'Unknown source'} · {selected.title || 'Untitled record'}</div></div><button onClick={close} aria-label="Close evidence details">×</button></div><section><h3>Source information</h3><div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, color: SECONDARY, fontSize: 12 }}>{[['Status', selected.status], ['Batch', selected.batchReference], ['Product', selected.productReference], ['Machine', selected.machineReference], ['Supplier', selected.supplierReference], ['Linked', dateText(selected.linkedAt)]].map(([label, value]) => <div key={String(label)} style={{ background: PANEL, padding: 10 }}><div style={{ color: MUTED }}>{label}</div><div>{value || '-'}</div></div>)}</div></section><section><h3>Discovery path</h3>{selected.matchExplanations?.map((match, index) => <div key={`${match.reason}-${index}`} style={{ background: PANEL, padding: 10, marginBottom: 7, color: SECONDARY }}>{match.connectionPath?.join(' → ') || match.reason || 'Deterministic discovery match'}</div>)}</section><section><h3>Original source record</h3><div style={{ background: PANEL, padding: 10, maxHeight: 260, overflow: 'auto' }}>{payloadFields(selected.normalizedPayload).map(([key, value]) => <div key={key} style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 8, padding: 6, borderBottom: `1px solid ${BORDER}`, fontSize: 12 }}><span style={{ color: MUTED }}>{key}</span><span>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span></div>)}</div></section><section><h3>Investigator assessment</h3><label>Review status<select value={form.reviewStatus} onChange={event => setForm({ ...form, reviewStatus: event.target.value as 'PENDING_REVIEW' | 'REVIEWED' })}><option value="PENDING_REVIEW">Pending review</option><option value="REVIEWED">Reviewed</option></select></label><label>Relevance<select value={form.relevance || ''} onChange={event => setForm({ ...form, relevance: (event.target.value || null) as 'RELEVANT' | 'NOT_RELEVANT' | null })}><option value="">Not set</option><option value="RELEVANT">Relevant</option><option value="NOT_RELEVANT">Not relevant</option></select></label><label>Importance<select value={form.importance || ''} onChange={event => setForm({ ...form, importance: (event.target.value || null) as 'HIGH' | 'MEDIUM' | 'LOW' | null })}><option value="">Not set</option><option value="HIGH">High</option><option value="MEDIUM">Medium</option><option value="LOW">Low</option></select></label><label>Assessment<select value={form.assessment || ''} onChange={event => setForm({ ...form, assessment: (event.target.value || null) as InvestigationEvidenceAssessmentRequest['assessment'] })}><option value="">Not assessed</option><option value="SUPPORTS_INVESTIGATION">Supports investigation</option><option value="CONTRADICTS_INVESTIGATION">Contradicts investigation</option><option value="CONTEXT_ONLY">Context only</option><option value="INCONCLUSIVE">Inconclusive</option><option value="NOT_ASSESSED">Not assessed</option></select></label><label>Investigator notes<textarea value={form.investigatorNotes || ''} onChange={event => setForm({ ...form, investigatorNotes: event.target.value })} maxLength={5000} rows={5} /></label>{detailError && <div role="alert">{detailError}</div>}{success && <div role="status">{success}</div>}<button onClick={save} disabled={saving}>{saving ? 'Saving…' : 'Save Assessment'}</button><button onClick={nextPending} disabled={saving || !items.some(item => (item.reviewStatus || 'PENDING_REVIEW') === 'PENDING_REVIEW' && item.stableId !== selected.stableId)}>Next Pending</button></section></aside></div>}
  </div>
}
