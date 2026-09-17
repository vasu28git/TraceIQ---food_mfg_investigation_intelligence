import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import {
  getInvestigationEvidenceDetail,
  getInvestigationEvidenceSummary,
  listInvestigationEvidence,
  saveInvestigationEvidenceAssessment,
  reviewInvestigationEvidence,
} from '../../../services/investigationService'
import type {
  Investigation,
  InvestigationEvidence,
  InvestigationEvidenceAssessmentRequest,
} from '../../../types/investigation'

const CARD = '#111827'
const PANEL = '#0B1120'
const BORDER = '#1E293B'
const TEXT = '#F8FAFC'
const SECONDARY = '#94A3B8'
const MUTED = '#64748B'

const EMPTY: InvestigationEvidenceAssessmentRequest = {
  reviewStatus: 'PENDING_REVIEW',
  relevance: null,
  importance: null,
  assessment: null,
  investigatorNotes: '',
}

function dateText(value?: string | null) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function payloadFields(payload?: string | null) {
  if (!payload) return [] as [string, unknown][]
  try {
    const root = JSON.parse(payload) as Record<string, unknown>
    const source = typeof root.originalPayload === 'string'
      ? (JSON.parse(root.originalPayload) as Record<string, unknown>)
      : root
    return Object.entries(source)
  } catch {
    return [] as [string, unknown][]
  }
}

function Badge({ children, tone = '#334155' }: { children: ReactNode; tone?: string }) {
  return (
    <span
      style={{
        background: tone,
        color: '#E2E8F0',
        borderRadius: 999,
        padding: '3px 8px',
        fontSize: 11,
        fontWeight: 700,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
      }}
    >
      {children}
    </span>
  )
}

function RelevanceBadge({ relevance }: { relevance?: string | null }) {
  const rel = (relevance || '').toUpperCase()
  if (rel === 'DIRECT') return <Badge tone="#1E3A8A">DIRECT</Badge>
  if (rel === 'RELATED') return <Badge tone="#78350F">RELATED</Badge>
  if (rel === 'SUPPORTING') return <Badge tone="#334155">SUPPORTING</Badge>
  if (rel === 'RELEVANT') return <Badge tone="#14532D">RELEVANT</Badge>
  if (rel === 'NOT_RELEVANT') return <Badge tone="#7F1D1D">NOT RELEVANT</Badge>
  return <Badge tone="#334155">{rel || 'UNCLASSIFIED'}</Badge>
}

function ReviewStatusBadge({ status }: { status?: string | null }) {
  const st = (status || 'PENDING_REVIEW').toUpperCase()
  if (st === 'REVIEWED') return <Badge tone="#14532D">Reviewed</Badge>
  if (st === 'REJECTED') return <Badge tone="#7F1D1D">Rejected</Badge>
  return <Badge tone="#854D0E">Pending review</Badge>
}

function deriveRoute(item: InvestigationEvidence): string {
  if (item.distance === 1) return 'DIRECT_BATCH'
  if (item.discoveryPath && item.discoveryPath.length > 2) {
    const mid = item.discoveryPath[1].toUpperCase()
    if (mid.startsWith('M-') || mid.includes('MACHINE')) return 'MACHINE_ROUTE'
    if (mid.startsWith('SUP-') || mid.includes('SUPPLIER')) return 'SUPPLIER_ROUTE'
    if (mid.startsWith('LOG-') || mid.includes('ZONE') || mid.includes('WAREHOUSE')) return 'WAREHOUSE_ROUTE'
    if (mid.startsWith('PRD-') || mid.includes('PRODUCT')) return 'PRODUCT_ROUTE'
  }
  if (item.machineReference) return 'MACHINE_ROUTE'
  if (item.supplierReference) return 'SUPPLIER_ROUTE'
  if (item.productReference) return 'PRODUCT_ROUTE'
  if (item.discoveryReason && item.discoveryReason.toLowerCase().includes('machine')) return 'MACHINE_ROUTE'
  if (item.discoveryReason && item.discoveryReason.toLowerCase().includes('supplier')) return 'SUPPLIER_ROUTE'
  if (item.discoveryReason && item.discoveryReason.toLowerCase().includes('warehouse')) return 'WAREHOUSE_ROUTE'
  return item.distance === 2 ? 'MACHINE_ROUTE' : 'GRAPH_ROUTE'
}

function cleanNodeName(node: string): string {
  if (!node) return ''
  return node.replace(/^SRC_[A-Z0-9]+_/, '')
}

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
  const [relevanceFilter, setRelevanceFilter] = useState('')
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
    try {
      setSummary(await getInvestigationEvidenceSummary(investigation.id))
    } catch {
      /* list errors remain visible */
    }
  }, [investigation.id])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await listInvestigationEvidence(investigation.id, page, size, {
        search: search || undefined,
        sourceType: source || undefined,
        relevance: relevanceFilter || undefined,
        reviewStatus: review || undefined,
      })
      setItems(response.content || [])
      setTotal(response.totalElements || 0)
      setTotalPages(response.totalPages || 0)
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, 'Unable to load investigation evidence'))
    } finally {
      setLoading(false)
    }
  }, [investigation.id, page, search, source, relevanceFilter, review])

  useEffect(() => {
    load().catch(() => undefined)
    refreshSummary()
  }, [load, refreshSummary])

  const openEvidence = async (item: InvestigationEvidence) => {
    setSelected(item)
    setDetailError(null)
    setSuccess(null)
    try {
      const loaded = await getInvestigationEvidenceDetail(investigation.id, item.stableId)
      const next = {
        reviewStatus: (loaded.reviewStatus === 'REJECTED' ? 'PENDING_REVIEW' : loaded.reviewStatus) || 'PENDING_REVIEW',
        relevance: loaded.assessmentRelevance || null,
        importance: loaded.importance || null,
        assessment: loaded.assessment || null,
        investigatorNotes: loaded.investigatorNotes || '',
      } as InvestigationEvidenceAssessmentRequest
      setSelected(loaded)
      setForm(next)
      setSavedForm(next)
    } catch (err: unknown) {
      setDetailError(getApiErrorMessage(err, 'Unable to load evidence details'))
    }
  }

  const dirty = JSON.stringify(form) !== JSON.stringify(savedForm)
  const close = () => {
    if (dirty && !window.confirm('Unsaved assessment\nYou have unsaved changes. Leave without saving?')) return
    setSelected(null)
    setSuccess(null)
  }

  const save = async () => {
    if (!selected) return
    setSaving(true)
    setDetailError(null)
    setSuccess(null)
    try {
      const saved = await saveInvestigationEvidenceAssessment(investigation.id, selected.stableId, form)
      const next = {
        reviewStatus: (saved.reviewStatus === 'REJECTED' ? 'PENDING_REVIEW' : saved.reviewStatus) || 'PENDING_REVIEW',
        relevance: saved.assessmentRelevance || null,
        importance: saved.importance || null,
        assessment: saved.assessment || null,
        investigatorNotes: saved.investigatorNotes || '',
      } as InvestigationEvidenceAssessmentRequest
      setSelected(saved)
      setItems(current => current.map(item => (item.stableId === saved.stableId ? saved : item)))
      setForm(next)
      setSavedForm(next)
      setSuccess('Assessment saved')
      await refreshSummary()
    } catch (err: unknown) {
      setDetailError(getApiErrorMessage(err, 'Unable to save assessment. Please try again.'))
    } finally {
      setSaving(false)
    }
  }

  const quickReview = async (status: 'REVIEWED' | 'REJECTED') => {
    if (!selected) return
    setSaving(true)
    setDetailError(null)
    setSuccess(null)
    try {
      const updated = await reviewInvestigationEvidence(investigation.id, selected.stableId, {
        reviewStatus: status,
        investigatorNotes: form.investigatorNotes || null,
      })
      setSelected(updated)
      setItems(current => current.map(item => (item.stableId === updated.stableId ? updated : item)))
      const next = {
        reviewStatus: status === 'REJECTED' ? 'PENDING_REVIEW' : status,
        relevance: updated.assessmentRelevance || null,
        importance: updated.importance || null,
        assessment: updated.assessment || null,
        investigatorNotes: updated.investigatorNotes || '',
      } as InvestigationEvidenceAssessmentRequest
      setForm(next)
      setSavedForm(next)
      setSuccess(`Evidence marked as ${status === 'REVIEWED' ? 'Reviewed' : 'Rejected'}`)
      await refreshSummary()
    } catch (err: unknown) {
      setDetailError(getApiErrorMessage(err, `Unable to mark evidence as ${status}`))
    } finally {
      setSaving(false)
    }
  }

  const nextPending = () => {
    const next = items.find(
      item => (item.reviewStatus || 'PENDING_REVIEW') === 'PENDING_REVIEW' && item.stableId !== selected?.stableId
    )
    if (next) openEvidence(next)
  }

  const sourceOptions = useMemo(() => Array.from(new Set(items.map(item => item.sourceType).filter(Boolean))), [items])
  const importanceRank: Record<string, number> = { HIGH: 0, CRITICAL: 0, MEDIUM: 1, IMPORTANT: 1, LOW: 2, CONTEXT: 2 }

  const relevanceGroup = (item: InvestigationEvidence) => {
    const relevance = String(item.relevance || '').toUpperCase()
    if (relevance === 'DIRECT') return 'Direct Evidence (Batch Anchor)'
    if (relevance === 'RELATED') return 'Related Evidence (Multi-Hop Graph Discovery)'
    if (relevance === 'SUPPORTING') return 'Supporting Traceability Records'
    if (relevance === 'RELEVANT') return 'Relevant'
    if (relevance === 'NOT_RELEVANT') return 'Not relevant'
    return 'Pending Classification'
  }

  const groupedItems = useMemo(() => {
    const groups = new Map<string, InvestigationEvidence[]>()
    for (const item of items) {
      const group = relevanceGroup(item)
      const entries = groups.get(group) || []
      entries.push(item)
      groups.set(group, entries)
    }
    for (const entries of groups.values()) {
      entries.sort(
        (left, right) =>
          (importanceRank[left.importance || ''] ?? 3) - (importanceRank[right.importance || ''] ?? 3) ||
          (left.distance ?? 99) - (right.distance ?? 99) ||
          (left.stableId || '').localeCompare(right.stableId || '')
      )
    }
    return [
      'Direct Evidence (Batch Anchor)',
      'Related Evidence (Multi-Hop Graph Discovery)',
      'Supporting Traceability Records',
      'Relevant',
      'Pending Classification',
      'Not relevant',
    ]
      .filter(group => groups.has(group))
      .map(group => ({ group, items: groups.get(group) || [] }))
  }, [items])

  return (
    <div data-evidence-review style={{ display: 'flex', flexDirection: 'column', gap: 14, color: TEXT }}>
      <style>{`
        [data-evidence-review] aside {
          box-sizing: border-box;
          width: min(640px, 100vw) !important;
          padding: 24px !important;
          color: ${TEXT};
        }
        [data-evidence-review] aside > div:first-child {
          align-items: flex-start;
          border-bottom: 1px solid ${BORDER};
          padding-bottom: 16px;
          margin-bottom: 18px;
        }
        [data-evidence-review] aside > div:first-child h2 {
          margin: 6px 0 4px;
          font-size: 19px;
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
        [data-evidence-review] aside section { margin-top: 20px; }
        [data-evidence-review] aside section h3 {
          margin: 0 0 10px;
          color: ${TEXT};
          font-size: 13px;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 0.04em;
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
          min-height: 96px;
          resize: vertical;
          line-height: 1.45;
        }
        [data-evidence-review] .action-btn {
          border: 1px solid ${BORDER};
          border-radius: 7px;
          padding: 9px 14px;
          font: inherit;
          font-size: 12px;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.15s ease;
        }
        [data-evidence-review] .action-btn:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        @media (max-width: 640px) {
          [data-evidence-review] aside { padding: 18px !important; }
        }
      `}</style>

      {/* Header section */}
      <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 18 }}>
        <div style={{ color: '#60A5FA', fontSize: 11, fontWeight: 800, textTransform: 'uppercase' }}>
          Evidence Review & Traceability Discovery
        </div>
        <h2 style={{ margin: '5px 0', fontSize: 21 }}>Review Discovered Evidence</h2>
        <div style={{ color: SECONDARY, fontSize: 13 }}>
          {investigation.investigationKey} · {investigation.title}
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          {investigation.batchReference && <Badge tone="#78350F">Anchor Batch: {investigation.batchReference}</Badge>}
          <Badge tone="#1E3A5F">Status: {investigation.status}</Badge>
        </div>
      </section>

      {/* Summary metric tiles */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 10 }}>
        {[
          ['Evidence records', summary.totalEvidence || total],
          ['Source systems', summary.sourceSystems.length],
          ['Reviewed', summary.reviewed],
          ['Pending review', summary.pendingReview],
        ].map(([label, value]) => (
          <div key={String(label)} style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 14 }}>
            <div style={{ color: MUTED, fontSize: 11, textTransform: 'uppercase', fontWeight: 700 }}>{label}</div>
            <div style={{ fontSize: 24, fontWeight: 800 }}>{value}</div>
          </div>
        ))}
      </section>

      {summary.totalEvidence > 0 && summary.reviewed === summary.totalEvidence && (
        <div role="status" style={{ background: '#14532D', color: '#DCFCE7', borderRadius: 8, padding: 12 }}>
          <strong>Evidence review complete</strong>
          <div>All discovered evidence has been reviewed. You can now move to investigation findings.</div>
        </div>
      )}
      {success && <div role="status" style={{ background: '#14532D', color: '#DCFCE7', borderRadius: 8, padding: 12 }}>{success}</div>}
      {error && <div role="alert" style={{ background: '#451A1A', color: '#FECACA', borderRadius: 8, padding: 12 }}>{error}</div>}

      {/* Relevance Filter Pills */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 10 }}>
        <span style={{ color: MUTED, fontSize: 11, fontWeight: 700, textTransform: 'uppercase', marginRight: 4 }}>
          Relevance:
        </span>
        {[
          { id: '', label: 'All Relevance' },
          { id: 'DIRECT', label: 'Direct Anchor' },
          { id: 'RELATED', label: 'Related (Graph)' },
          { id: 'SUPPORTING', label: 'Supporting' },
        ].map(tab => (
          <button
            key={tab.id}
            type="button"
            onClick={() => {
              setRelevanceFilter(tab.id)
              setPage(0)
            }}
            style={{
              background: relevanceFilter === tab.id ? '#2563EB' : PANEL,
              color: relevanceFilter === tab.id ? '#FFFFFF' : SECONDARY,
              border: `1px solid ${relevanceFilter === tab.id ? '#3B82F6' : BORDER}`,
              borderRadius: 6,
              padding: '5px 12px',
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Search & Select Filters */}
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <input
          value={search}
          onChange={event => {
            setSearch(event.target.value)
            setPage(0)
          }}
          placeholder="Search evidence..."
          style={{ flex: 1, minWidth: 220, background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }}
        />
        <select
          value={source}
          onChange={event => {
            setSource(event.target.value)
            setPage(0)
          }}
          style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }}
        >
          <option value="">All sources</option>
          {sourceOptions.map(val => (
            <option key={val} value={val || ''}>
              {val}
            </option>
          ))}
        </select>
        <select
          value={review}
          onChange={event => {
            setReview(event.target.value)
            setPage(0)
          }}
          style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 9 }}
        >
          <option value="">All review states</option>
          <option value="PENDING_REVIEW">Pending review</option>
          <option value="REVIEWED">Reviewed</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>

      {/* Evidence Table */}
      {loading ? (
        <div role="status" style={{ background: CARD, padding: 28, textAlign: 'center', color: SECONDARY }}>
          Loading evidence…
        </div>
      ) : items.length === 0 ? (
        <div style={{ background: CARD, padding: 32, textAlign: 'center', color: SECONDARY }}>
          No evidence records found matching the current filters.
        </div>
      ) : (
        <section style={{ overflowX: 'auto', background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 840 }}>
            <thead>
              <tr style={{ color: MUTED, fontSize: 11, textTransform: 'uppercase', textAlign: 'left' }}>
                {['Evidence ID', 'Source', 'Title', 'Relevance', 'Distance', 'Discovery Route', 'Review'].map(label => (
                  <th key={label} style={{ padding: 12 }}>{label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groupedItems.map(({ group, items: groupItems }) => (
                <div key={group} style={{ display: 'contents' }}>
                  <tr>
                    <th
                      colSpan={7}
                      style={{
                        padding: '10px 14px',
                        color: '#93C5FD',
                        background: PANEL,
                        textAlign: 'left',
                        fontSize: 12,
                        borderTop: `1px solid ${BORDER}`,
                      }}
                    >
                      {group} · {groupItems.length} records
                    </th>
                  </tr>
                  {groupItems.map(item => (
                    <tr
                      key={item.stableId}
                      onClick={() => openEvidence(item)}
                      style={{ cursor: 'pointer', borderTop: `1px solid ${BORDER}` }}
                    >
                      <td style={{ padding: 12, color: '#93C5FD', fontFamily: 'monospace', fontSize: 12 }}>
                        {item.sourceRecordId || item.stableId}
                      </td>
                      <td style={{ padding: 12 }}>{item.sourceType || '-'}</td>
                      <td style={{ padding: 12, maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {item.title || '-'}
                      </td>
                      <td style={{ padding: 12 }}>
                        <RelevanceBadge relevance={item.relevance} />
                      </td>
                      <td style={{ padding: 12 }}>
                        {item.distance != null ? (
                          <Badge tone="#0F766E">{item.distance === 1 ? '1 Hop' : `${item.distance} Hops`}</Badge>
                        ) : (
                          <span style={{ color: MUTED }}>-</span>
                        )}
                      </td>
                      <td style={{ padding: 12, color: SECONDARY, fontSize: 12, maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        <span style={{ background: '#1E293B', border: '1px solid #334155', borderRadius: 4, padding: '2px 6px', color: '#93C5FD', fontSize: 10, fontWeight: 700, fontFamily: 'monospace', marginRight: 6 }}>
                          {deriveRoute(item)}
                        </span>
                        <span>{item.discoveryReason || item.correlationReason || item.associationType || '-'}</span>
                      </td>
                      <td style={{ padding: 12 }}>
                        <ReviewStatusBadge status={item.reviewStatus} />
                      </td>
                    </tr>
                  ))}
                </div>
              ))}
            </tbody>
          </table>
          <div style={{ padding: 12, color: SECONDARY, fontSize: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>Showing {items.length} of {total} records</span>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <button
                disabled={page === 0}
                onClick={() => setPage(v => v - 1)}
                style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 5, padding: '4px 8px', cursor: 'pointer' }}
              >
                Previous
              </button>
              <span>Page {page + 1} of {Math.max(1, totalPages)}</span>
              <button
                disabled={page + 1 >= totalPages}
                onClick={() => setPage(v => v + 1)}
                style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 5, padding: '4px 8px', cursor: 'pointer' }}
              >
                Next
              </button>
            </div>
          </div>
        </section>
      )}

      {/* Detail & Review Drawer */}
      {selected && (
        <div
          role="dialog"
          aria-label="Evidence details"
          style={{ position: 'fixed', inset: 0, background: 'rgba(2,6,23,0.72)', zIndex: 40, display: 'flex', justifyContent: 'flex-end' }}
          onClick={close}
        >
          <aside
            onClick={event => event.stopPropagation()}
            style={{ width: 'min(640px, 100%)', height: '100%', overflowY: 'auto', background: CARD, padding: 22 }}
          >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 4 }}>
                  <RelevanceBadge relevance={selected.relevance} />
                  <ReviewStatusBadge status={selected.reviewStatus} />
                  {selected.distance != null && <Badge tone="#0F766E">{selected.distance} Hop{selected.distance === 1 ? '' : 's'}</Badge>}
                </div>
                <h2>{selected.sourceRecordId || selected.stableId}</h2>
                <div style={{ color: SECONDARY, fontSize: 13 }}>
                  {selected.sourceType || 'Unknown source'} · {selected.title || 'Untitled record'}
                </div>
              </div>
              <button onClick={close} aria-label="Close evidence details">×</button>
            </div>

            {/* Quick Action Decision Bar */}
            <section style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 12 }}>
              <div style={{ color: SECONDARY, fontSize: 11, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8 }}>
                Quick Review Actions
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  className="action-btn"
                  onClick={() => quickReview('REVIEWED')}
                  disabled={saving || selected.reviewStatus === 'REVIEWED'}
                  style={{ background: '#166534', color: '#DCFCE7', borderColor: '#15803D', flex: 1 }}
                >
                  ✓ Accept & Mark Reviewed
                </button>
                <button
                  className="action-btn"
                  onClick={() => quickReview('REJECTED')}
                  disabled={saving || selected.reviewStatus === 'REJECTED'}
                  style={{ background: '#7F1D1D', color: '#FEE2E2', borderColor: '#991B1B', flex: 1 }}
                >
                  ✕ Reject Evidence
                </button>
              </div>
            </section>

            {/* Traceability Discovery Path */}
            <section>
              <h3>Traceability Discovery Path</h3>
              {selected.discoveryPath && selected.discoveryPath.length > 0 ? (
                <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                    <div style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700, textTransform: 'uppercase' }}>
                      Neo4j Traceability Route ({selected.distance != null ? `${selected.distance} Hop${selected.distance === 1 ? '' : 's'}` : 'Direct'})
                    </div>
                    <span style={{ background: '#1E293B', border: '1px solid #334155', borderRadius: 4, padding: '2px 8px', color: '#93C5FD', fontSize: 11, fontWeight: 700, fontFamily: 'monospace' }}>
                      route = {deriveRoute(selected)}
                    </span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6, margin: '8px 0' }}>
                    {selected.discoveryPath.map((node, idx) => (
                      <span key={`${node}-${idx}`} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <span
                          style={{
                            background: '#1E293B',
                            border: '1px solid #334155',
                            borderRadius: 6,
                            padding: '4px 8px',
                            fontFamily: 'monospace',
                            fontSize: 12,
                            color: '#F1F5F9',
                          }}
                        >
                          {cleanNodeName(node)}
                        </span>
                        {idx < selected.discoveryPath!.length - 1 && (
                          <span style={{ color: '#60A5FA', fontWeight: 800 }}>→</span>
                        )}
                      </span>
                    ))}
                  </div>
                  <div style={{ display: 'flex', gap: 14, marginTop: 8, borderTop: `1px solid ${BORDER}`, paddingTop: 6, fontSize: 11, color: MUTED }}>
                    <span><strong>distance:</strong> {selected.distance ?? 1}</span>
                    <span><strong>route:</strong> {deriveRoute(selected)}</span>
                    <span><strong>relevance:</strong> {selected.relevance || 'RELATED'}</span>
                  </div>
                  {selected.discoveryReason && (
                    <div style={{ color: SECONDARY, fontSize: 12, marginTop: 6 }}>
                      <strong style={{ color: TEXT }}>Route Explanation:</strong> {selected.discoveryReason}
                    </div>
                  )}
                  {selected.discoveryMethod && (
                    <div style={{ color: MUTED, fontSize: 11, marginTop: 4 }}>
                      Discovery Method: {selected.discoveryMethod}
                    </div>
                  )}
                </div>
              ) : selected.matchExplanations && selected.matchExplanations.length > 0 ? (
                selected.matchExplanations.map((match, index) => (
                  <div key={`${match.reason}-${index}`} style={{ background: PANEL, padding: 10, marginBottom: 7, color: SECONDARY, borderRadius: 6 }}>
                    {match.connectionPath?.join(' → ') || match.reason || 'Deterministic discovery match'}
                  </div>
                ))
              ) : (
                <div style={{ background: PANEL, padding: 10, color: MUTED, fontSize: 12, borderRadius: 6 }}>
                  Direct batch reference or legacy association.
                </div>
              )}
            </section>

            {/* Source Information */}
            <section>
              <h3>Source Context</h3>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, color: SECONDARY, fontSize: 12 }}>
                {[
                  ['Status', selected.status],
                  ['Anchor Batch', selected.batchReference],
                  ['Product', selected.productReference],
                  ['Machine', selected.machineReference],
                  ['Supplier', selected.supplierReference],
                  ['Linked At', dateText(selected.linkedAt)],
                ].map(([label, value]) => (
                  <div key={String(label)} style={{ background: PANEL, padding: 10, borderRadius: 6 }}>
                    <div style={{ color: MUTED }}>{label}</div>
                    <div style={{ color: TEXT, marginTop: 2 }}>{value || '-'}</div>
                  </div>
                ))}
              </div>
            </section>

            {/* Original Payload */}
            <section>
              <h3>Original Record Payload</h3>
              <div style={{ background: PANEL, padding: 10, maxHeight: 240, overflow: 'auto', borderRadius: 6 }}>
                {payloadFields(selected.normalizedPayload).map(([key, value]) => (
                  <div
                    key={key}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 2fr',
                      gap: 8,
                      padding: 6,
                      borderBottom: `1px solid ${BORDER}`,
                      fontSize: 12,
                    }}
                  >
                    <span style={{ color: MUTED }}>{key}</span>
                    <span>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span>
                  </div>
                ))}
              </div>
            </section>

            {/* Detailed Assessment */}
            <section>
              <h3>Investigator Assessment</h3>
              <label>
                Review Status
                <select
                  value={form.reviewStatus}
                  onChange={event =>
                    setForm({ ...form, reviewStatus: event.target.value as 'PENDING_REVIEW' | 'REVIEWED' })
                  }
                >
                  <option value="PENDING_REVIEW">Pending review</option>
                  <option value="REVIEWED">Reviewed</option>
                </select>
              </label>

              <label>
                Relevance Assessment
                <select
                  value={form.relevance || ''}
                  onChange={event =>
                    setForm({ ...form, relevance: (event.target.value || null) as 'RELEVANT' | 'NOT_RELEVANT' | null })
                  }
                >
                  <option value="">Not set</option>
                  <option value="RELEVANT">Relevant</option>
                  <option value="NOT_RELEVANT">Not relevant</option>
                </select>
              </label>

              <label>
                Importance
                <select
                  value={form.importance || ''}
                  onChange={event =>
                    setForm({ ...form, importance: (event.target.value || null) as 'HIGH' | 'MEDIUM' | 'LOW' | null })
                  }
                >
                  <option value="">Not set</option>
                  <option value="HIGH">High</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="LOW">Low</option>
                </select>
              </label>

              <label>
                Investigation Impact Assessment
                <select
                  value={form.assessment || ''}
                  onChange={event =>
                    setForm({ ...form, assessment: (event.target.value || null) as InvestigationEvidenceAssessmentRequest['assessment'] })
                  }
                >
                  <option value="">Not assessed</option>
                  <option value="SUPPORTS_INVESTIGATION">Supports investigation</option>
                  <option value="CONTRADICTS_INVESTIGATION">Contradicts investigation</option>
                  <option value="CONTEXT_ONLY">Context only</option>
                  <option value="INCONCLUSIVE">Inconclusive</option>
                  <option value="NOT_ASSESSED">Not assessed</option>
                </select>
              </label>

              <label>
                Investigator Notes
                <textarea
                  value={form.investigatorNotes || ''}
                  onChange={event => setForm({ ...form, investigatorNotes: event.target.value })}
                  maxLength={5000}
                  rows={4}
                  placeholder="Record observations, justification for review decision..."
                />
              </label>

              {detailError && <div role="alert" style={{ color: '#F87171', fontSize: 12, marginTop: 8 }}>{detailError}</div>}
              {success && <div role="status" style={{ color: '#4ADE80', fontSize: 12, marginTop: 8 }}>{success}</div>}

              <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                <button
                  className="action-btn"
                  onClick={save}
                  disabled={saving}
                  style={{ background: '#2563EB', color: 'white', borderColor: '#2563EB', flex: 1 }}
                >
                  {saving ? 'Saving…' : 'Save Assessment'}
                </button>
                <button
                  className="action-btn"
                  onClick={nextPending}
                  disabled={
                    saving ||
                    !items.some(
                      item => (item.reviewStatus || 'PENDING_REVIEW') === 'PENDING_REVIEW' && item.stableId !== selected.stableId
                    )
                  }
                  style={{ background: PANEL, color: TEXT, flex: 1 }}
                >
                  Next Pending
                </button>
              </div>
            </section>
          </aside>
        </div>
      )}
    </div>
  )
}
