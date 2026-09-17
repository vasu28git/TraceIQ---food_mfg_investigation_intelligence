import { useEffect, useState, useCallback, useMemo } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/client'
import { graphEvidence, getGraphEvidenceDetail, traceCase, traceIncident, checkOrgGraphReady } from '../../services/investigationService'
import { getInvestigation, listInvestigationEvidence } from '../../services/investigationService'
import type { Investigation } from '../../types/investigation'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'
const PANEL = '#0B1120'

type GraphEvidence = {
  stableId: string
  title?: string | null
  sourceType?: string | null
  status?: string | null
  sourceCreatedAt?: string | null
  sourceUpdatedAt?: string | null
  correlationReason?: string | null
  batchReference?: string | null
  sourceRecordId?: string | null
  reviewStatus?: string | null
  relevance?: string | null
}

type Detail = {
  stableId: string
  title?: string | null
  sourceType?: string | null
  status?: string | null
  caseId?: string | null
  actorId?: string | null
  parentId?: string | null
  sourceCreatedAt?: string | null
  sourceUpdatedAt?: string | null
  firstSeenAt?: string | null
  lastSeenAt?: string | null
  batchReference?: string | null
  correlationReason?: string | null
  relevance?: string | null
  reviewStatus?: string | null
  discoveryPath?: string[] | null
  discoveryReason?: string | null
  semanticRoute?: string | null
  distance?: number | null
  attributes?: { size?: number | null; contentType?: string | null; tags?: string[] | null } | null
  normalizedPayload?: string | null
}

type TraceNode = { stableId: string; label: string; title?: string | null; sourceType?: string | null; status?: string | null }
type TraceRel = { fromStableId: string; fromLabel: string; type: string; toStableId: string; toLabel: string }
type TraceResponse = { caseNode?: TraceNode; incidentNode?: TraceNode; nodes: TraceNode[]; relationships: TraceRel[]; depth: number }

function formatDate(d?: string | null) { if (!d) return '-'; try { return new Date(d).toLocaleString() } catch { return d } }

function RelevanceBadge({ relevance }: { relevance?: string | null }) {
  const rel = (relevance || '').toUpperCase()
  const map: Record<string, { bg: string; fg: string }> = {
    DIRECT: { bg: '#1E3A8A', fg: '#93C5FD' },
    RELATED: { bg: '#78350F', fg: '#FCD34D' },
    SUPPORTING: { bg: '#334155', fg: '#CBD5E1' },
    RELEVANT: { bg: '#14532D', fg: '#86EFAC' },
    NOT_RELEVANT: { bg: '#7F1D1D', fg: '#FCA5A5' },
  }
  const c = map[rel] || { bg: '#334155', fg: '#CBD5E1' }
  return <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700, display: 'inline-flex', alignItems: 'center', gap: 4 }}>{rel || 'UNCLASSIFIED'}</span>
}

function ReviewStatusBadge({ status }: { status?: string | null }) {
  const st = (status || 'PENDING_REVIEW').toUpperCase()
  if (st === 'REVIEWED') return <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: '#14532D', color: '#86EFAC', fontWeight: 700 }}>Reviewed</span>
  if (st === 'REJECTED') return <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: '#7F1D1D', color: '#FCA5A5', fontWeight: 700 }}>Rejected</span>
  return <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: '#854D0E', color: '#FDE68A', fontWeight: 700 }}>Pending</span>
}

function RelationshipBadge({ type }: { type: string }) {
  const map: Record<string, { bg: string; fg: string }> = {
    BELONGS_TO: { bg: '#dbeafe', fg: '#1e40af' },
    CREATED_BY: { bg: '#fef3c7', fg: '#92400e' },
    DERIVED_FROM: { bg: '#dcfce7', fg: '#166534' },
    HAS_EVIDENCE: { bg: '#ede9fe', fg: '#5b21b6' },
    REFERENCES: { bg: '#e0e7ff', fg: '#3730a3' },
  }
  const c = map[type] || { bg: '#e2e8f0', fg: '#475569' }
  return <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{type}</span>
}

function cleanNodeName(node: string): string {
  if (!node) return ''
  return node.replace(/^SRC_[A-Z0-9]+_/, '')
}

function deriveRoute(item: Detail): string {
  if (item.semanticRoute) return item.semanticRoute
  if (item.distance === 1) return 'DIRECT_BATCH'
  if (item.discoveryPath && item.discoveryPath.length > 2) {
    const mid = item.discoveryPath[1].toUpperCase()
    if (mid.startsWith('M-') || mid.includes('MACHINE')) return 'MACHINE_ROUTE'
    if (mid.startsWith('SUP-') || mid.includes('SUPPLIER')) return 'SUPPLIER_ROUTE'
    if (mid.startsWith('WZ-') || mid.startsWith('WH-') || mid.startsWith('LOG-') || mid.includes('ZONE') || mid.includes('WAREHOUSE')) return 'WAREHOUSE_ROUTE'
    if (mid.startsWith('PRD-') || mid.includes('PRODUCT')) return 'PRODUCT_ROUTE'
    if (mid.startsWith('CUST-') || mid.includes('CUSTOMER')) return 'CUSTOMER_ROUTE'
  }
  return 'GRAPH_ROUTE'
}

function payloadFields(payload?: string | null): [string, unknown][] {
  if (!payload) return []
  try {
    const root = JSON.parse(payload) as Record<string, unknown>
    const source = typeof root.originalPayload === 'string'
      ? (JSON.parse(root.originalPayload) as Record<string, unknown>)
      : root
    return Object.entries(source)
  } catch {
    return []
  }
}

function formattedJson(payload?: string | null): string {
  if (!payload) return 'No payload available.'
  try {
    const obj = JSON.parse(payload)
    if (typeof obj.originalPayload === 'string') {
      try { obj.originalPayload = JSON.parse(obj.originalPayload) } catch {}
    }
    return JSON.stringify(obj, null, 2)
  } catch {
    return payload
  }
}

const selectStyle: React.CSSProperties = { background: PANEL, color: TEXT_MAIN, border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px', fontSize: 12 }

export function EvidenceGraphPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const incidentIdParam = searchParams.get('incidentId')
  const batchParam = searchParams.get('batchReference')
  const incidentId = incidentIdParam ? Number(incidentIdParam) : null
  const hasIncidentContext = incidentId !== null && !Number.isNaN(incidentId)

  const [incident, setIncident] = useState<Investigation | null>(null)
  const [incidentLoading, setIncidentLoading] = useState(false)

  const [graphAvailable, setGraphAvailable] = useState<boolean | null>(null)
  const [graphMessage, setGraphMessage] = useState<string | null>(null)
  const [graphChecking, setGraphChecking] = useState(false)

  const [search, setSearch] = useState('')
  const [sourceType, setSourceType] = useState('')
  const [batch, setBatch] = useState('')
  const [evidenceType, setEvidenceType] = useState('')
  const [reviewStatus, setReviewStatus] = useState('')
  const [page, setPage] = useState(0)
  const size = 20

  const [items, setItems] = useState<GraphEvidence[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedStableId, setSelectedStableId] = useState<string | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)

  const [trace, setTrace] = useState<TraceResponse | null>(null)
  const [traceLoading, setTraceLoading] = useState(false)
  const [traceError, setTraceError] = useState<string | null>(null)
  const [traceGraphUnavailable, setTraceGraphUnavailable] = useState(false)

  const sourceTypeOptions = useMemo(() => Array.from(new Set(items.map(i => i.sourceType).filter(Boolean))), [items])
  const batchOptions = useMemo(() => Array.from(new Set(items.map(i => i.batchReference).filter(Boolean))), [items])

  const checkGraphStatus = useCallback(async () => {
    setGraphChecking(true)
    try {
      const res = await checkOrgGraphReady() as { valid?: boolean; graphValid?: boolean; graphReady?: boolean; evidenceCount?: number; errors?: string[]; message?: string }
      const available = !!(res.graphReady ?? res.graphValid ?? res.valid)
      setGraphAvailable(available)
      setGraphMessage(res.message || (res.errors || []).join('; ') || null)
    } catch (err: unknown) {
      setGraphAvailable(false)
      setGraphMessage(getApiErrorMessage(err, 'Graph relationships temporarily unavailable.'))
    } finally { setGraphChecking(false) }
  }, [])

  useEffect(() => { checkGraphStatus() }, [checkGraphStatus])

  useEffect(() => {
    if (hasIncidentContext && incidentId) {
      setIncidentLoading(true)
      getInvestigation(incidentId).then(setIncident).catch(() => setIncident(null)).finally(() => setIncidentLoading(false))
    } else {
      setIncident(null)
    }
  }, [incidentId, hasIncidentContext])

  const load = useCallback(async (p: number) => {
    setLoading(true); setError(null)
    try {
      if (hasIncidentContext && incidentId) {
        const res = await listInvestigationEvidence(incidentId, p, size, {
          search: search || undefined,
          sourceType: sourceType || undefined,
          status: undefined,
          sort: undefined,
        }) as unknown as { content: GraphEvidence[]; totalElements: number; totalPages: number }
        let content = res.content ?? []
        if (batch.trim()) {
          content = content.filter(i => (i.batchReference || '').toLowerCase().includes(batch.trim().toLowerCase()))
        }
        if (evidenceType.trim()) {
          content = content.filter(i => (i.sourceType || '').toLowerCase().includes(evidenceType.trim().toLowerCase()))
        }
        if (reviewStatus) {
          content = content.filter(i => (i.reviewStatus || 'PENDING_REVIEW') === reviewStatus)
        }
        setItems(content)
        setTotalElements(res.totalElements ?? content.length)
        setTotalPages(res.totalPages ?? Math.ceil((res.totalElements ?? content.length) / size))
        return
      }
      const params: { page: number; size: number; incidentId?: number } = { page: p, size }
      const res = await graphEvidence(params) as unknown as { content: GraphEvidence[]; totalElements: number; totalPages: number } | GraphEvidence[]
      let content: GraphEvidence[]
      let te = 0, tp = 0
      if (Array.isArray(res)) { content = res as GraphEvidence[]; te = content.length; tp = 1 }
      else { content = (res as { content: GraphEvidence[] }).content ?? []; te = (res as { totalElements: number }).totalElements ?? content.length; tp = (res as { totalPages: number }).totalPages ?? Math.ceil(te / size) }
      setItems(content)
      setTotalElements(te)
      setTotalPages(tp)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to load evidence')
      if (st === 403) setError('You don\'t have access to the evidence data (403).')
      else setError(msg)
    } finally { setLoading(false) }
  }, [hasIncidentContext, incidentId, search, sourceType, batch, evidenceType, reviewStatus])

  useEffect(() => { load(page) }, [page, load])

  const filteredItems = useMemo(() => {
    let result = items
    if (search.trim()) {
      const s = search.trim().toLowerCase()
      result = result.filter(i =>
        (i.stableId && i.stableId.toLowerCase().includes(s)) ||
        (i.title && i.title.toLowerCase().includes(s)) ||
        (i.sourceRecordId && i.sourceRecordId.toLowerCase().includes(s))
      )
    }
    if (sourceType) result = result.filter(i => i.sourceType === sourceType)
    if (batch) result = result.filter(i => i.batchReference === batch)
    if (evidenceType.trim()) {
      const et = evidenceType.trim().toLowerCase()
      result = result.filter(i => (i.sourceType || '').toLowerCase().includes(et))
    }
    if (reviewStatus) result = result.filter(i => (i.reviewStatus || 'PENDING_REVIEW') === reviewStatus)
    return result
  }, [items, search, sourceType, batch, evidenceType, reviewStatus])

  const handleSearch = () => { setPage(0) }

  const clearFilters = () => {
    setSearch(''); setSourceType(''); setBatch(''); setEvidenceType(''); setReviewStatus(''); setPage(0)
    if (hasIncidentContext) {
      setSearchParams(prev => {
        const p = new URLSearchParams(prev)
        p.delete('incidentId'); p.delete('batchReference')
        return p
      })
    }
  }

  const openDetail = async (stableId: string) => {
    setSelectedStableId(stableId); setDetail(null); setDetailError(null); setTrace(null); setTraceError(null); setTraceGraphUnavailable(false)
    setDetailLoading(true)
    try {
      const d = await getGraphEvidenceDetail(stableId) as Detail
      setDetail(d)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 404) setDetailError('Evidence not found (404) or not in your organisation.')
      else if (st === 403) setDetailError('You don\'t have access to this evidence (403).')
      else setDetailError(getApiErrorMessage(err, 'Failed to load evidence detail'))
    } finally { setDetailLoading(false) }
  }

  useEffect(() => {
    const sid = searchParams.get('stableId')
    if (sid && !selectedStableId) openDetail(sid)
  }, [searchParams])

  const handleTrace = async () => {
    if (!detail) return
    setTraceLoading(true); setTraceError(null); setTrace(null); setTraceGraphUnavailable(false)
    try {
      if (hasIncidentContext && incidentId) {
        const t = await traceIncident(incidentId) as TraceResponse
        setTrace(t)
      } else if (detail.caseId) {
        const t = await traceCase(detail.caseId) as TraceResponse
        setTrace(t)
      } else {
        setTraceError('No traceable context available for this evidence record.')
      }
    } catch (err: unknown) {
      const msg = getApiErrorMessage(err, 'Failed to load traceability')
      const lower = msg.toLowerCase()
      if (lower.includes('graph not ready') || lower.includes('temporarily unavailable')) {
        setTraceGraphUnavailable(true)
        setTraceError('Evidence is available. Graph relationships are temporarily unavailable.')
      } else {
        setTraceGraphUnavailable(true)
        setTraceError('Evidence is available. Graph relationships are temporarily unavailable.')
      }
    } finally { setTraceLoading(false) }
  }

  const closeDetail = () => {
    setSelectedStableId(null); setDetail(null); setTrace(null); setTraceError(null); setTraceGraphUnavailable(false)
  }

  const totalFiltered = filteredItems.length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Evidence Data</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>
            {hasIncidentContext && (
              <span>Incident <Link to={`/organisation/investigations/${incidentId}`} style={{ color: '#60A5FA' }}>#{incidentId}</Link> {incidentLoading ? 'loading...' : incident ? `· ${incident.title} · Batch ${incident.batchReference || batchParam || '-'}` : `· Batch ${batchParam || '-'}`} · </span>
            )}
            {totalElements} records · tenant-isolated
            {graphChecking ? ' · checking graph...' : graphAvailable === false ? ' · Graph unavailable' : graphAvailable ? ' · Graph available' : ''}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => load(page)} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
        </div>
      </div>

      {graphAvailable === false && (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          <div style={{ fontSize: 12, color: '#fecaca' }}>Graph relationships temporarily unavailable. <span style={{ color: '#fca5a5' }}>{graphMessage || 'Evidence data is still available via PostgreSQL.'}</span></div>
          <button onClick={() => { checkGraphStatus() }} style={{ padding: '4px 8px', background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer', fontSize: 11 }}>Retry</button>
        </div>
      )}

      {hasIncidentContext && incident && (
        <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: '10px 12px', display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 12 }}>
          <span style={{ color: MUTED }}>Incident:</span> <span style={{ color: TEXT_MAIN, fontWeight: 700 }}>{incident.investigationKey}</span>
          <span style={{ color: MUTED }}>Batch:</span> <span style={{ color: '#A5B4FC', fontFamily: 'ui-monospace, monospace' }}>{incident.batchReference || '-'}</span>
          <span style={{ color: MUTED }}>Status:</span> <span style={{ color: TEXT_SEC }}>{incident.status}</span>
          <Link to={`/organisation/investigations/${incidentId}?tab=evidence`} style={{ color: '#60A5FA', marginLeft: 'auto' }}>Open incident workspace →</Link>
        </div>
      )}

      {/* Filters */}
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ fontSize: 11, color: MUTED, fontWeight: 700, letterSpacing: 0.6, textTransform: 'uppercase' }}>
          Filters · {totalFiltered} records
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
          <label style={{ flex: '1 1 200px', minWidth: 180, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>Search Evidence ID / Title</span>
            <input
              value={search}
              onChange={e => { setSearch(e.target.value); setPage(0) }}
              placeholder="Search by ID or title..."
              style={{ padding: '8px 10px', background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN, fontSize: 12 }}
            />
          </label>
          <label style={{ flex: '0 1 160px', minWidth: 140, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>Source System</span>
            <select value={sourceType} onChange={e => { setSourceType(e.target.value); setPage(0) }} style={selectStyle}>
              <option value="">All sources</option>
              {sourceTypeOptions.map(s => <option key={s} value={s || ''}>{s}</option>)}
            </select>
          </label>
          <label style={{ flex: '0 1 160px', minWidth: 140, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>Batch</span>
            <select value={batch} onChange={e => { setBatch(e.target.value); setPage(0) }} style={selectStyle}>
              <option value="">All batches</option>
              {batchOptions.map(b => <option key={b} value={b || ''}>{b}</option>)}
            </select>
          </label>
          <label style={{ flex: '0 1 160px', minWidth: 140, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>Evidence Type</span>
            <select value={evidenceType} onChange={e => { setEvidenceType(e.target.value); setPage(0) }} style={selectStyle}>
              <option value="">All types</option>
              <option value="CMMS">CMMS</option>
              <option value="MES">MES</option>
              <option value="LIMS">LIMS</option>
              <option value="ERP">ERP</option>
              <option value="WAREHOUSE">WAREHOUSE</option>
              <option value="MANUAL">MANUAL</option>
            </select>
          </label>
          <label style={{ flex: '0 1 160px', minWidth: 140, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>Review Status</span>
            <select value={reviewStatus} onChange={e => { setReviewStatus(e.target.value); setPage(0) }} style={selectStyle}>
              <option value="">All statuses</option>
              <option value="PENDING_REVIEW">Pending Review</option>
              <option value="REVIEWED">Reviewed</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          <button onClick={clearFilters} style={{ padding: '8px 14px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12, whiteSpace: 'nowrap' }}>Clear</button>
        </div>
      </div>

      {/* Evidence Table */}
      {loading ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading evidence...</div>
      ) : error ? (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13 }}>{error} <button onClick={() => load(page)} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer' }}>Retry</button></div>
      ) : filteredItems.length === 0 ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No evidence found.</div>
          <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>{hasIncidentContext ? `No evidence for incident ${incidentId}.` : 'Try clearing filters.'}</div>
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ padding: 10, borderBottom: `1px solid ${BORDER}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 11, color: MUTED, fontWeight: 700, letterSpacing: 0.5, textTransform: 'uppercase' }}>Evidence Records</span>
            <span style={{ fontSize: 11, color: TEXT_SEC }}>{totalFiltered} shown · {totalElements} total · Page {page + 1} of {totalPages || 1}</span>
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#0F172A' }}>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Evidence ID</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Source</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Type</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Batch</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Title</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Relevance</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Review</th>
                  <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Created</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((e) => (
                  <tr
                    key={e.stableId}
                    onClick={() => openDetail(e.stableId)}
                    style={{ borderBottom: `1px solid ${BORDER}`, cursor: 'pointer', background: selectedStableId === e.stableId ? '#0F172A' : 'transparent', transition: 'background 0.1s' }}
                    onMouseEnter={ev => { if (selectedStableId !== e.stableId) (ev.currentTarget as HTMLElement).style.background = '#0F172A' }}
                    onMouseLeave={ev => { if (selectedStableId !== e.stableId) (ev.currentTarget as HTMLElement).style.background = 'transparent' }}
                  >
                    <td style={{ padding: '8px 10px', color: '#60A5FA', fontFamily: 'ui-monospace, monospace', fontWeight: 600, fontSize: 11, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.stableId}</td>
                    <td style={{ padding: '8px 10px' }}>
                      <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: '#0B1120', border: `1px solid ${BORDER}`, color: TEXT_SEC }}>{e.sourceType || '-'}</span>
                    </td>
                    <td style={{ padding: '8px 10px', color: TEXT_SEC, fontSize: 11 }}>{e.sourceType || '-'}</td>
                    <td style={{ padding: '8px 10px', color: '#A5B4FC', fontFamily: 'ui-monospace, monospace', fontSize: 11 }}>{e.batchReference || '-'}</td>
                    <td style={{ padding: '8px 10px', color: TEXT_SEC, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 11 }}>{e.title || '-'}</td>
                    <td style={{ padding: '8px 10px' }}><RelevanceBadge relevance={e.relevance} /></td>
                    <td style={{ padding: '8px 10px' }}><ReviewStatusBadge status={e.reviewStatus} /></td>
                    <td style={{ padding: '8px 10px', color: MUTED, fontSize: 11, whiteSpace: 'nowrap' }}>{formatDate(e.sourceCreatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 12, borderTop: `1px solid ${BORDER}` }}>
              <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))} style={{ padding: '6px 10px', background: page === 0 ? PANEL : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page === 0 ? 'not-allowed' : 'pointer', fontSize: 12 }}>Prev</button>
              <span style={{ fontSize: 12, color: TEXT_SEC, alignSelf: 'center' }}>Page {page + 1} of {totalPages}</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)} style={{ padding: '6px 10px', background: page + 1 >= totalPages ? PANEL : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer', fontSize: 12 }}>Next</button>
            </div>
          )}
        </div>
      )}

      {/* Detail Drawer */}
      {selectedStableId && (
        <div
          role="dialog"
          aria-label="Evidence details"
          style={{ position: 'fixed', inset: 0, background: 'rgba(2,6,23,0.72)', zIndex: 60, display: 'flex', justifyContent: 'flex-end' }}
          onClick={closeDetail}
        >
          <aside
            onClick={e => e.stopPropagation()}
            style={{ width: 'min(660px, 100vw)', height: '100%', overflowY: 'auto', background: CARD_BG, padding: 24, boxSizing: 'border-box', color: TEXT_MAIN, borderLeft: `1px solid ${BORDER}`, display: 'flex', flexDirection: 'column', gap: 20 }}
          >
            {/* Drawer Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: `1px solid ${BORDER}`, paddingBottom: 16 }}>
              <div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 6, flexWrap: 'wrap' }}>
                  {detail && <RelevanceBadge relevance={detail.relevance} />}
                  {detail && <ReviewStatusBadge status={detail.reviewStatus} />}
                  {detail?.distance != null && (
                    <span style={{ fontSize: 10, padding: '2px 8px', borderRadius: 999, background: '#0F766E', color: '#5EEAD4', fontWeight: 700 }}>
                      {detail.distance === 1 ? '1 Hop' : `${detail.distance} Hops`}
                    </span>
                  )}
                  {detailLoading && <span style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700 }}>Loading...</span>}
                </div>
                <h2 style={{ margin: '4px 0 2px', fontSize: 18, fontWeight: 800 }}>
                  {detail?.title || detail?.stableId || selectedStableId}
                </h2>
                <div style={{ color: MUTED, fontSize: 12, fontFamily: 'monospace', marginTop: 2 }}>{selectedStableId}</div>
                {detail && (
                  <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 4 }}>
                    {detail.sourceType || 'Unknown source'} · {detail.status || '-'}
                  </div>
                )}
              </div>
              <button
                onClick={closeDetail}
                aria-label="Close evidence details"
                style={{ width: 32, height: 32, borderRadius: 7, border: `1px solid ${BORDER}`, background: PANEL, color: TEXT_SEC, fontSize: 20, cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
              >
                ×
              </button>
            </div>

            {detailError && (
              <div role="alert" style={{ background: '#451A1A', color: '#FECACA', borderRadius: 8, padding: 12, fontSize: 12 }}>{detailError}</div>
            )}

            {detailLoading ? (
              <div style={{ color: TEXT_SEC, fontSize: 13, padding: 20 }}>Loading evidence details...</div>
            ) : detail ? (
              <>
                {/* Source Context */}
                <section>
                  <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT_MAIN, fontWeight: 700, letterSpacing: 0.04 }}>Source Context</h3>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, color: TEXT_SEC, fontSize: 12 }}>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Evidence ID</div>
                      <div style={{ color: '#60A5FA', fontWeight: 700, marginTop: 2, fontFamily: 'monospace', fontSize: 11, wordBreak: 'break-all' }}>{detail.stableId}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Source System</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2 }}>{detail.sourceType || '-'}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Batch</div>
                      <div style={{ color: '#A5B4FC', fontWeight: 700, marginTop: 2, fontFamily: 'monospace' }}>{detail.batchReference || '-'}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Record Status</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2 }}>{detail.status || '-'}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Case</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2, fontFamily: 'monospace', fontSize: 11 }}>{detail.caseId || '— none'}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Actor</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2, fontSize: 11 }}>{detail.actorId || '-'}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Created</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2 }}>{formatDate(detail.sourceCreatedAt)}</div>
                    </div>
                    <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Updated</div>
                      <div style={{ color: TEXT_MAIN, marginTop: 2 }}>{formatDate(detail.sourceUpdatedAt)}</div>
                    </div>
                  </div>
                </section>

                {/* Traceability Discovery Path */}
                <section>
                  <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT_MAIN, fontWeight: 700, letterSpacing: 0.04 }}>Discovery Route</h3>
                  {detail.discoveryPath && detail.discoveryPath.length > 0 ? (
                    <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 12 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <div style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700, textTransform: 'uppercase' }}>
                          Traceability Path ({detail.distance != null ? `${detail.distance} Hop${detail.distance === 1 ? '' : 's'}` : 'Direct'})
                        </div>
                        <span style={{ background: '#1E293B', border: '1px solid #334155', borderRadius: 4, padding: '2px 8px', color: '#93C5FD', fontSize: 11, fontWeight: 700, fontFamily: 'monospace' }}>
                          route = {deriveRoute(detail)}
                        </span>
                      </div>
                      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6, margin: '8px 0' }}>
                        {detail.discoveryPath.map((node, idx) => (
                          <span key={`${node}-${idx}`} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                            <span style={{ background: '#1E293B', border: '1px solid #334155', borderRadius: 6, padding: '4px 8px', fontFamily: 'monospace', fontSize: 12, color: '#F1F5F9' }}>
                              {cleanNodeName(node)}
                            </span>
                            {idx < detail.discoveryPath!.length - 1 && <span style={{ color: '#60A5FA', fontWeight: 800 }}>→</span>}
                          </span>
                        ))}
                      </div>
                      {detail.discoveryReason && (
                        <div style={{ color: TEXT_SEC, fontSize: 12, marginTop: 8, borderTop: `1px solid ${BORDER}`, paddingTop: 6 }}>
                          <strong style={{ color: TEXT_MAIN }}>Route Explanation:</strong> {detail.discoveryReason}
                        </div>
                      )}
                    </div>
                  ) : (
                    <div style={{ background: PANEL, padding: 12, color: MUTED, fontSize: 12, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                      Direct batch reference or legacy association.
                    </div>
                  )}
                </section>

                {/* Complete Source Record Payload */}
                <section>
                  <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT_MAIN, fontWeight: 700, letterSpacing: 0.04 }}>Source Record Payload</h3>
                  <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 6, padding: 10 }}>
                    {payloadFields(detail.normalizedPayload).length > 0 && (
                      <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 10 }}>
                        {payloadFields(detail.normalizedPayload).map(([key, value]) => (
                          <div key={key} style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 8, padding: '5px 4px', borderBottom: `1px solid ${BORDER}`, fontSize: 12 }}>
                            <span style={{ color: MUTED, fontFamily: 'monospace' }}>{key}</span>
                            <span style={{ color: TEXT_MAIN, wordBreak: 'break-word' }}>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    <details>
                      <summary style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>View Raw JSON Payload</summary>
                      <pre style={{ margin: '8px 0 0', padding: 10, background: '#020617', borderRadius: 6, color: '#CBD5E1', fontSize: 11, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: 220, overflowY: 'auto' }}>
                        {formattedJson(detail.normalizedPayload)}
                      </pre>
                    </details>
                  </div>
                </section>

                {/* Action Buttons */}
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <button
                    onClick={() => openDetail(detail.stableId)}
                    style={{ padding: '8px 14px', background: PANEL, color: TEXT_SEC, border: `1px solid ${BORDER}`, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}
                  >
                    Refresh Details
                  </button>
                  <button
                    onClick={handleTrace}
                    disabled={traceLoading}
                    style={{ padding: '8px 14px', background: traceLoading ? '#475569' : '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: traceLoading ? 'not-allowed' : 'pointer', fontSize: 12, fontWeight: 700 }}
                  >
                    {traceLoading ? 'Loading Graph...' : 'View Traceability'}
                  </button>
                </div>

                {/* Traceability Results */}
                {(trace || traceError || traceGraphUnavailable) && (
                  <section style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 12 }}>
                    <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT_MAIN, fontWeight: 700, letterSpacing: 0.04 }}>Graph Relationships</h3>
                    {traceGraphUnavailable && (
                      <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, padding: 10, fontSize: 12, color: '#fecaca', marginBottom: 8 }}>
                        {traceError || 'Graph relationships temporarily unavailable.'}
                        <button onClick={handleTrace} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '3px 8px', cursor: 'pointer', fontSize: 11 }}>Retry</button>
                      </div>
                    )}
                    {traceLoading && <div style={{ color: MUTED, fontSize: 12 }}>Loading graph relationships...</div>}
                    {!traceLoading && traceError && !traceGraphUnavailable && (
                      <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, padding: 10, fontSize: 12, color: '#fecaca' }}>
                        {traceError}
                        <button onClick={handleTrace} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '3px 8px', cursor: 'pointer', fontSize: 11 }}>Retry</button>
                      </div>
                    )}
                    {trace && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <div style={{ fontSize: 11, color: MUTED }}>Graph loaded · depth {trace.depth} · {trace.nodes.length} nodes · {trace.relationships.length} relationships</div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 300, overflowY: 'auto', border: `1px solid ${BORDER}`, borderRadius: 8 }}>
                          <div style={{ padding: '6px 8px', background: '#0F172A', fontSize: 11, fontWeight: 700, color: MUTED, textTransform: 'uppercase' }}>Nodes ({(trace.incidentNode ? 1 : 0) + trace.nodes.length + (trace.caseNode ? 1 : 0)})</div>
                          {trace.incidentNode && (
                            <div style={{ padding: '6px 8px', borderBottom: `1px solid ${BORDER}`, fontSize: 12 }}>
                              <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: '#5b21b6', color: '#ede9fe', marginRight: 6 }}>Incident</span>
                              <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{trace.incidentNode.stableId}</span>
                            </div>
                          )}
                          {trace.caseNode && (
                            <div style={{ padding: '6px 8px', borderBottom: `1px solid ${BORDER}`, fontSize: 12 }}>
                              <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: '#1e40af', color: '#dbeafe', marginRight: 6 }}>Case</span>
                              <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{trace.caseNode.stableId}</span>
                            </div>
                          )}
                          {trace.nodes.map((n) => (
                            <div key={`${n.label}:${n.stableId}`} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 8px', borderBottom: `1px solid ${BORDER}`, fontSize: 12 }}>
                              <span>
                                <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: n.label === 'Case' ? '#1e40af' : n.label === 'Actor' ? '#92400e' : '#3730a3', color: n.label === 'Case' ? '#dbeafe' : n.label === 'Actor' ? '#fef3c7' : '#e0e7ff', marginRight: 6 }}>{n.label}</span>
                                <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{n.stableId}</span>
                              </span>
                              <span style={{ color: MUTED, fontSize: 11 }}>{[n.title, n.sourceType, n.status].filter(Boolean).join(' · ') || '-'}</span>
                            </div>
                          ))}
                          <div style={{ padding: '6px 8px', background: '#0F172A', fontSize: 11, fontWeight: 700, color: MUTED, textTransform: 'uppercase' }}>Relationships ({trace.relationships.length})</div>
                          {trace.relationships.length === 0 ? (
                            <div style={{ padding: 8, color: MUTED, fontSize: 12 }}>No relationships</div>
                          ) : trace.relationships.slice(0, 30).map((r, i) => (
                            <div key={i} style={{ display: 'flex', gap: 6, alignItems: 'center', padding: '6px 8px', borderBottom: `1px solid ${BORDER}`, fontSize: 11, flexWrap: 'wrap' }}>
                              <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.fromLabel}:{r.fromStableId}</span>
                              <RelationshipBadge type={r.type} />
                              <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.toLabel}:{r.toStableId}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                    {!trace && !traceLoading && !traceError && !traceGraphUnavailable && (
                      <div style={{ fontSize: 12, color: MUTED, background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 8 }}>
                        Click <strong>View Traceability</strong> to load graph relationships from Neo4j. Evidence data remains available regardless of graph status.
                      </div>
                    )}
                  </section>
                )}
              </>
            ) : null}

            {/* Close bar */}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10, borderTop: `1px solid ${BORDER}`, paddingTop: 14 }}>
              <button
                type="button"
                onClick={closeDetail}
                style={{ background: PANEL, color: TEXT_MAIN, border: `1px solid ${BORDER}`, borderRadius: 6, padding: '8px 18px', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}
              >
                Close Details
              </button>
            </div>
          </aside>
        </div>
      )}
    </div>
  )
}
