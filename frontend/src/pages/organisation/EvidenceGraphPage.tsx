import { useEffect, useState, useCallback } from 'react'
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
  attributes?: { size?: number | null; contentType?: string | null; tags?: string[] | null } | null
}

type TraceNode = { stableId: string; label: string; title?: string | null; sourceType?: string | null; status?: string | null }
type TraceRel = { fromStableId: string; fromLabel: string; type: string; toStableId: string; toLabel: string }
type TraceResponse = { caseNode?: TraceNode; incidentNode?: TraceNode; nodes: TraceNode[]; relationships: TraceRel[]; depth: number }

function formatDate(d?: string | null) { if (!d) return '-'; try { return new Date(d).toLocaleString() } catch { return d } }

function RelationshipBadge({ type }: { type: string }) {
  const map: Record<string, { bg: string; fg: string }> = {
    BELONGS_TO: { bg: '#dbeafe', fg: '#1e40af' },
    CREATED_BY: { bg: '#fef3c7', fg: '#92400e' },
    DERIVED_FROM: { bg: '#dcfce7', fg: '#166534' },
    HAS_EVIDENCE: { bg: '#ede9fe', fg: '#5b21b6' },
  }
  const c = map[type] || { bg: '#e2e8f0', fg: '#475569' }
  return <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{type}</span>
}

export function EvidenceGraphPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const incidentIdParam = searchParams.get('incidentId')
  const batchParam = searchParams.get('batchReference')
  const incidentId = incidentIdParam ? Number(incidentIdParam) : null
  const hasIncidentContext = incidentId !== null && !Number.isNaN(incidentId)

  // incident context
  const [incident, setIncident] = useState<Investigation | null>(null)
  const [incidentLoading, setIncidentLoading] = useState(false)

  // graph status (non-blocking)
  const [graphAvailable, setGraphAvailable] = useState<boolean | null>(null)
  const [graphMessage, setGraphMessage] = useState<string | null>(null)
  const [graphChecking, setGraphChecking] = useState(false)

  // filters (no hard-coded defaults)
  const [caseId, setCaseId] = useState('')
  const [actorId, setActorId] = useState('')
  const [page, setPage] = useState(0)
  const size = 20

  // data
  const [items, setItems] = useState<GraphEvidence[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // detail
  const [selected, setSelected] = useState<string | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)

  // trace
  const [trace, setTrace] = useState<TraceResponse | null>(null)
  const [traceLoading, setTraceLoading] = useState(false)
  const [traceError, setTraceError] = useState<string | null>(null)
  const [traceGraphUnavailable, setTraceGraphUnavailable] = useState(false)

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

  const load = useCallback(async (p: number, cId?: string, aId?: string) => {
    setLoading(true); setError(null)
    try {
      // Incident-first: if incidentId present, use incident-scoped evidence (PostgreSQL, includes batch/correlation)
      if (hasIncidentContext && incidentId) {
        const res = await listInvestigationEvidence(incidentId, p, size, {
          search: undefined,
          sourceType: undefined,
          status: undefined,
          sort: undefined,
        }) as unknown as { content: GraphEvidence[]; totalElements: number; totalPages: number }
        // Filter by caseId/actorId client-side if provided (since incident evidence may not have those)
        let content = res.content ?? []
        // Apply caseId/actorId filter in-memory for incident mode (rare)
        const cTrim = (cId ?? caseId).trim()
        const aTrim = (aId ?? actorId).trim()
        if (cTrim || aTrim) {
          // For incident mode, caseId/actorId filtering is not primary – just show all and let user clear
          // We keep content as is; UI will indicate filter not applicable for incident mode
        }
        setItems(content)
        setTotalElements(res.totalElements ?? content.length)
        setTotalPages(res.totalPages ?? Math.ceil((res.totalElements ?? content.length)/size))
        return
      }
      // Org-wide: PostgreSQL-backed evidence (no Neo4j)
      const params: { caseId?: string; actorId?: string; page: number; size: number; incidentId?: number } = { page: p, size }
      const cTrim = (cId ?? caseId).trim()
      const aTrim = (aId ?? actorId).trim()
      if (cTrim) params.caseId = cTrim
      if (aTrim) params.actorId = aTrim
      const res = await graphEvidence(params) as unknown as { content: GraphEvidence[]; totalElements: number; totalPages: number; page: number; size: number } | GraphEvidence[]
      let content: GraphEvidence[]
      let te = 0, tp = 0
      if (Array.isArray(res)) {
        content = res as GraphEvidence[]
        te = content.length
        tp = 1
      } else {
        content = (res as { content: GraphEvidence[] }).content ?? []
        te = (res as { totalElements: number }).totalElements ?? content.length
        tp = (res as { totalPages: number }).totalPages ?? Math.ceil(te / size)
      }
      setItems(content)
      setTotalElements(te)
      setTotalPages(tp)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to load evidence')
      if (st === 403) setError('You don’t have access to the evidence graph (403).')
      else setError(msg)
    } finally { setLoading(false) }
  }, [caseId, actorId, hasIncidentContext, incidentId])

  useEffect(() => { load(page) }, [page, load])

  const handleSearch = () => {
    setPage(0)
    load(0, caseId, actorId)
  }

  const clearFilters = () => {
    setCaseId(''); setActorId(''); setPage(0)
    // For incident mode, also clear incident context
    if (hasIncidentContext) {
      setSearchParams(prev => {
        const p = new URLSearchParams(prev)
        p.delete('incidentId'); p.delete('batchReference'); p.delete('caseId'); p.delete('actorId')
        return p
      })
    } else {
      load(0, '', '')
    }
  }

  const openDetail = async (stableId: string) => {
    setSelected(stableId); setDetail(null); setDetailError(null); setTrace(null); setTraceError(null); setTraceGraphUnavailable(false)
    setDetailLoading(true)
    try {
      const d = await getGraphEvidenceDetail(stableId) as Detail
      setDetail(d)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 404) setDetailError('Evidence not found (404) or not in your organisation.')
      else if (st === 403) setDetailError('You don’t have access to this evidence (403).')
      else setDetailError(getApiErrorMessage(err, 'Failed to load evidence detail'))
    } finally { setDetailLoading(false) }
  }

  // Auto-open detail when navigated from Dashboard with ?stableId=
  useEffect(() => {
    const sid = searchParams.get('stableId')
    if (sid && !selected) {
      openDetail(sid)
    }
  }, [searchParams])

  const handleTrace = async () => {
    setTraceLoading(true); setTraceError(null); setTrace(null); setTraceGraphUnavailable(false)
    try {
      // Incident-first trace if incident context
      if (hasIncidentContext && incidentId) {
        const t = await traceIncident(incidentId) as TraceResponse
        setTrace(t)
      } else if (detail?.caseId) {
        const t = await traceCase(detail.caseId) as TraceResponse
        setTrace(t)
      } else {
        setTraceError('No traceable context – select evidence with a case or open from an incident.')
      }
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to load traceability')
      const lower = msg.toLowerCase()
      if (lower.includes('graph not ready') || lower.includes('temporarily unavailable') || st === 400) {
        setTraceGraphUnavailable(true)
        setTraceError('Evidence is available. Graph relationships are temporarily unavailable.')
      } else if (st === 404) setTraceError('Trace not found (404).')
      else if (st === 403) setTraceError('Forbidden (403).')
      else {
        setTraceGraphUnavailable(true)
        setTraceError('Evidence is available. Graph relationships are temporarily unavailable.')
      }
    } finally { setTraceLoading(false) }
  }

  const retryGraph = () => {
    checkGraphStatus()
    if (selected && detail) handleTrace()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Evidence Graph</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>
            {hasIncidentContext ? (
              <span>Incident <Link to={`/organisation/investigations/${incidentId}`} style={{ color: '#60A5FA' }}>#{incidentId}</Link> {incidentLoading ? 'loading…' : incident ? `· ${incident.title} · Batch ${incident.batchReference || batchParam || '-'}` : `· Batch ${batchParam || '-'}`} · </span>
            ) : null}
            {totalElements} evidence · tenant-isolated · PostgreSQL authoritative
            {graphChecking ? ' · checking graph…' : graphAvailable === false ? ' · Graph relationships temporarily unavailable' : graphAvailable ? ' · Graph relationships available' : ''}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => load(page)} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh evidence</button>
          <button onClick={checkGraphStatus} disabled={graphChecking} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: graphChecking ? 'not-allowed' : 'pointer', fontSize: 12 }}>{graphChecking ? 'Checking…' : 'Check graph'}</button>
        </div>
      </div>

      {graphAvailable === false && (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, padding: '10px 12px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          <div style={{ fontSize: 12, color: '#fecaca' }}>Graph relationships temporarily unavailable. <span style={{ color: '#fca5a5' }}>{graphMessage || 'Evidence is still available via PostgreSQL.'}</span></div>
          <button onClick={retryGraph} style={{ padding: '4px 8px', background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer', fontSize: 11 }}>Retry graph</button>
        </div>
      )}

      {hasIncidentContext && incident && (
        <div style={{ background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '10px 12px', display: 'flex', gap: 12, flexWrap: 'wrap', fontSize: 12 }}>
          <span style={{ color: MUTED }}>Incident:</span> <span style={{ color: TEXT_MAIN, fontWeight: 700 }}>{incident.investigationKey}</span>
          <span style={{ color: MUTED }}>Batch:</span> <span style={{ color: '#A5B4FC', fontFamily: 'ui-monospace, monospace' }}>{incident.batchReference || '-'}</span>
          <span style={{ color: MUTED }}>Status:</span> <span style={{ color: TEXT_SEC }}>{incident.status}</span>
          <Link to={`/organisation/investigations/${incidentId}?tab=evidence`} style={{ color: '#60A5FA', marginLeft: 'auto' }}>Open incident workspace →</Link>
        </div>
      )}

      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
        <div style={{ fontSize: 11, color: MUTED, fontWeight: 700, letterSpacing: 0.6, textTransform: 'uppercase' }}>
          {hasIncidentContext ? `Incident Evidence · ${totalElements} records` : `Search / Filter · ${totalElements} records`}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <label style={{ flex: 1, minWidth: 180, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>caseId (legacy)</span>
            <input value={caseId} onChange={(e) => setCaseId(e.target.value)} placeholder="Filter by caseId" style={{ padding: '8px 10px', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN }} />
          </label>
          <label style={{ flex: 1, minWidth: 180, display: 'flex', flexDirection: 'column', gap: 4 }}>
            <span style={{ fontSize: 11, color: TEXT_SEC, fontWeight: 600 }}>actorId (legacy)</span>
            <input value={actorId} onChange={(e) => setActorId(e.target.value)} placeholder="Filter by actorId" style={{ padding: '8px 10px', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN }} />
          </label>
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
            <button onClick={handleSearch} style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>Search</button>
            <button onClick={clearFilters} style={{ padding: '8px 14px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer' }}>Clear</button>
          </div>
        </div>
        <div style={{ fontSize: 11, color: MUTED }}>
          {hasIncidentContext ? <>Incident-scoped · <code>GET /api/incidents/{incidentId}/evidence</code> · PostgreSQL · batch {incident?.batchReference || batchParam || '-'}</> : <>Org-wide · <code>GET /api/graph/evidence?caseId&actorId&page&size</code> · PostgreSQL · no Neo4j required for list</>}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        {/* Evidence list */}
        <div style={{ flex: '1 1 520px', minWidth: 320, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {loading ? (
            <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading evidence...</div>
          ) : error ? (
            <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13 }}>{error} <button onClick={() => load(page)} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer' }}>Retry</button></div>
          ) : items.length === 0 ? (
            <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
              <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No evidence found.</div>
              <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>{hasIncidentContext ? `No evidence for incident ${incidentId}. Run Discover Evidence in incident workspace.` : 'No evidence matches filters. Try clearing filters.'}</div>
            </div>
          ) : (
            <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
              <div style={{ padding: 10, borderBottom: `1px solid ${BORDER}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: 11, color: MUTED, fontWeight: 700, letterSpacing: 0.5, textTransform: 'uppercase' }}>Evidence · click to select {hasIncidentContext ? `· Incident ${incidentId}` : ''}</span>
                <span style={{ fontSize: 11, color: TEXT_SEC }}>Evidence loaded · {totalElements} total · Page {page + 1} of {totalPages || 1}</span>
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                  <thead><tr style={{ textAlign: 'left', background: '#0F172A' }}>
                    <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Record / Evidence ID</th>
                    <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Source</th>
                    <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Batch</th>
                    <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Correlation</th>
                    <th style={{ padding: '8px 10px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Status</th>
                  </tr></thead>
                  <tbody>
                    {items.map((e) => (
                      <tr key={e.stableId} onClick={() => openDetail(e.stableId)} style={{ borderBottom: `1px solid ${BORDER}`, cursor: 'pointer', background: selected === e.stableId ? '#0B1120' : 'transparent' }}>
                        <td style={{ padding: '8px 10px', color: '#60A5FA', fontFamily: 'ui-monospace, monospace', fontWeight: 600, fontSize: 11 }}>{e.stableId}</td>
                        <td style={{ padding: '8px 10px' }}><span style={{ fontSize: 11, padding: '1px 6px', borderRadius: 999, background: '#0B1120', border: `1px solid ${BORDER}`, color: TEXT_SEC }}>{e.sourceType || '-'}</span></td>
                        <td style={{ padding: '8px 10px', color: TEXT_SEC, fontFamily: 'ui-monospace, monospace', fontSize: 11 }}>{(e as unknown as { batchReference?: string }).batchReference || '-'}</td>
                        <td style={{ padding: '8px 10px', color: e.correlationReason ? '#A5B4FC' : MUTED, fontWeight: e.correlationReason ? 700 : 400, fontSize: 11 }}>{e.correlationReason || '-'}</td>
                        <td style={{ padding: '8px 10px', color: TEXT_SEC }}>{e.status || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {totalPages > 1 && (
                <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 12, borderTop: `1px solid ${BORDER}` }}>
                  <button disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} style={{ padding: '6px 10px', background: page === 0 ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page === 0 ? 'not-allowed' : 'pointer' }}>Prev</button>
                  <span style={{ fontSize: 12, color: TEXT_SEC, alignSelf: 'center' }}>Page {page + 1} of {totalPages}</span>
                  <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} style={{ padding: '6px 10px', background: page + 1 >= totalPages ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer' }}>Next</button>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Detail / trace panel */}
        <div style={{ flex: '1 1 380px', minWidth: 340, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {!selected ? (
            <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
              <div style={{ fontWeight: 700, color: TEXT_MAIN }}>Select evidence</div>
              <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>Evidence details are available via PostgreSQL. Graph relationships load separately.</div>
              <div style={{ fontSize: 11, color: MUTED, marginTop: 8 }}><code>GET /api/graph/evidence/{'{stableId}'}</code> (PostgreSQL) + trace via Neo4j</div>
            </div>
          ) : (
            <div style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden' }}>
              <div style={{ padding: 12, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8fafc' }}>
                <div style={{ fontWeight: 800, color: '#0F172A', fontSize: 13 }}>Selected: <span style={{ fontFamily: 'ui-monospace, monospace' }}>{selected}</span></div>
                <button onClick={() => { setSelected(null); setDetail(null); setTrace(null) }} style={{ background: 'transparent', border: 0, cursor: 'pointer', color: '#64748B', fontSize: 18 }}>×</button>
              </div>
              <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
                {detailLoading ? (
                  <div style={{ color: '#64748B', fontSize: 13 }}>Loading detail...</div>
                ) : detailError ? (
                  <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#991B1B', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{detailError}</div>
                ) : detail ? (
                  <>
                    <div style={{ display: 'grid', gridTemplateColumns: '110px 1fr', gap: 6, fontSize: 13, color: '#334155' }}>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>stableId</span><span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 700 }}>{detail.stableId}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Title</span><span>{detail.title || '-'}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Source</span><span>{detail.sourceType || '-'}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Status</span><span>{detail.status || '-'}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Case</span><span>{detail.caseId ? <span style={{ fontFamily: 'ui-monospace, monospace' }}>{detail.caseId}</span> : <span style={{ color: MUTED }}>— no case</span>}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Actor</span><span>{detail.actorId || '-'}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Parent</span><span>{detail.parentId || '-'}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Created</span><span>{formatDate(detail.sourceCreatedAt)}</span>
                      <span style={{ color: '#64748B', fontWeight: 600 }}>Updated</span><span>{formatDate(detail.sourceUpdatedAt)}</span>
                    </div>
                    {detail.attributes && (
                      <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: 10 }}>
                        <div style={{ fontSize: 11, fontWeight: 700, color: '#475569', letterSpacing: 0.6, textTransform: 'uppercase' }}>Safe metadata</div>
                        <div style={{ fontSize: 12, color: '#334155', marginTop: 6 }}>size: {detail.attributes.size ?? '-'} · type: {detail.attributes.contentType ?? '-'} · tags: {(detail.attributes.tags || []).join(', ') || '-'}</div>
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <button onClick={() => openDetail(detail.stableId)} style={{ padding: '6px 10px', background: '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh detail</button>
                      <button onClick={handleTrace} disabled={traceLoading} style={{ padding: '6px 10px', background: traceLoading ? '#475569' : '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: traceLoading ? 'not-allowed' : 'pointer', fontSize: 12, fontWeight: 700 }}>{traceLoading ? 'Tracing…' : 'Load relationships'}</button>
                    </div>
                  </>
                ) : null}

                {/* Traceability */}
                <div style={{ borderTop: '1px solid #e5e7eb', paddingTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ fontSize: 12, fontWeight: 700, color: '#0F172A' }}>Relationships · Traceability</div>
                  {traceGraphUnavailable && <div style={{ background: '#fff7ed', border: '1px solid #fed7aa', color: '#9a3412', padding: '8px 10px', borderRadius: 8, fontSize: 12 }}>Evidence is available. Graph relationships are temporarily unavailable. <button onClick={handleTrace} style={{ marginLeft: 6, background: '#f59e0b', color: '#fff', border: 0, borderRadius: 6, padding: '2px 6px', cursor: 'pointer' }}>Retry relationships</button></div>}
                  {traceLoading ? <div style={{ color: MUTED, fontSize: 12 }}>Graph relationships loading...</div> : traceError && !traceGraphUnavailable ? <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#991B1B', padding: '8px 10px', borderRadius: 8, fontSize: 12 }}>{traceError} <button onClick={handleTrace} style={{ marginLeft: 6, background: '#dc2626', color: '#fff', border: 0, borderRadius: 6, padding: '2px 6px', cursor: 'pointer' }}>Retry</button></div> : trace ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      <div style={{ fontSize: 11, color: MUTED }}>Graph relationships loaded · depth {trace.depth} · nodes {trace.nodes.length} · relationships {trace.relationships.length}</div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 260, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 8 }}>
                        <div style={{ padding: '6px 8px', background: '#f1f5f9', fontSize: 11, fontWeight: 700, color: '#475569' }}>Nodes ({(trace.incidentNode ? 1 : 0) + trace.nodes.length + (trace.caseNode ? 1 : 0)})</div>
                        {trace.incidentNode && <div style={{ padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 12 }}><span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: '#ede9fe', color: '#5b21b6' }}>Incident</span> <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{trace.incidentNode.stableId}</span></div>}
                        {trace.caseNode && <div style={{ padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 12 }}><span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: '#dbeafe', color: '#1e40af' }}>Case</span> <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{trace.caseNode.stableId}</span></div>}
                        {trace.nodes.map((n) => (
                          <div key={n.label+':'+n.stableId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 12 }}>
                            <span><span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: n.label==='Case'?'#dbeafe':n.label==='Actor'?'#fef3c7':'#e0e7ff', color: n.label==='Case'?'#1e40af':n.label==='Actor'?'#92400e':'#3730a3' }}>{n.label}</span> <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600 }}>{n.stableId}</span></span>
                            <span style={{ color: MUTED, fontSize: 11 }}>{[n.title, n.sourceType, n.status].filter(Boolean).join(' · ') || '-'}</span>
                          </div>
                        ))}
                        <div style={{ padding: '6px 8px', background: '#f1f5f9', fontSize: 11, fontWeight: 700, color: '#475569' }}>Relationships ({trace.relationships.length})</div>
                        {trace.relationships.length === 0 ? <div style={{ padding: '8px', color: MUTED, fontSize: 12 }}>No relationships</div> : trace.relationships.slice(0, 30).map((r, i) => (
                          <div key={i} style={{ display: 'flex', gap: 6, alignItems: 'center', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 11, flexWrap: 'wrap' }}>
                            <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.fromLabel}:{r.fromStableId}</span>
                            <RelationshipBadge type={r.type} />
                            <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.toLabel}:{r.toStableId}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <div style={{ fontSize: 12, color: MUTED, background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: 8 }}>Graph relationships not yet loaded. Click <strong>Load relationships</strong> to fetch from Neo4j. If unavailable, evidence remains visible.</div>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      <div style={{ fontSize: 11, color: MUTED, background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px' }}>
        PostgreSQL authoritative: <code>GET /api/graph/evidence?caseId&actorId&incidentId&page&size</code> · <code>GET /api/graph/evidence/{'{stableId}'}</code> · Neo4j for relationships: <code>GET /api/graph/cases/{'{caseId}'}/traceability</code> &amp; <code>GET /api/graph/incidents/{'{id}'}/traceability</code> · Tenant via <code>AuthorizationService.getCurrentOrgId()</code>
      </div>
    </div>
  )
}
