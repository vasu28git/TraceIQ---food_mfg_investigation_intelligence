import { useEffect, useState, useCallback } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { traceIncident } from '../../../services/investigationService'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

type TraceNode = { stableId: string; label: string; title?: string | null; sourceType?: string | null; status?: string | null }
type TraceRel = { fromStableId: string; fromLabel: string; type: string; toStableId: string; toLabel: string }
type TraceResponse = { incidentNode: TraceNode; nodes: TraceNode[]; relationships: TraceRel[]; depth: number }

function RelationshipBadge({ type }: { type: string }) {
  const map: Record<string, { bg: string; fg: string }> = {
    HAS_EVIDENCE: { bg: '#dcfce7', fg: '#166534' },
    BELONGS_TO: { bg: '#dbeafe', fg: '#1e40af' },
    CREATED_BY: { bg: '#fef3c7', fg: '#92400e' },
    DERIVED_FROM: { bg: '#e0e7ff', fg: '#3730a3' },
  }
  const c = map[type] || { bg: '#e2e8f0', fg: '#475569' }
  return <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{type}</span>
}

export function IncidentTraceability({ incidentId, onNavigate }: { incidentId: number; onNavigate?: (tab: string, opts?: { evidenceId?: string }) => void }) {
  const [data, setData] = useState<TraceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await traceIncident(incidentId) as TraceResponse
      setData(res)
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to load traceability')
      const lower = msg.toLowerCase()
      if (st === 404) setError('Incident not found (404) — it may not exist, not belong to your organisation, or has no graph data yet.')
      else if (st === 403) setError('Forbidden (403) — missing TRACEABILITY_ACCESS.')
      else if (st === 400 || lower.includes('graph not ready')) setError(msg || 'Graph not ready — traceability is temporarily unavailable. Evidence data is still preserved in PostgreSQL.')
      else setError(msg)
    } finally {
      setLoading(false)
    }
  }, [incidentId])

  useEffect(() => { load() }, [load])

  if (loading) {
    return <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading traceability…</div>
  }

  if (error) {
    const isGraphUnavailable = error.toLowerCase().includes('graph not ready') || error.toLowerCase().includes('temporarily unavailable')
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ background: isGraphUnavailable ? '#1f2937' : '#451a1a', border: `1px solid ${isGraphUnavailable ? BORDER : '#7f1d1d'}`, borderRadius: 12, padding: 16 }}>
          <div style={{ fontWeight: 800, color: isGraphUnavailable ? TEXT_MAIN : '#fecaca' }}>{isGraphUnavailable ? 'Traceability temporarily unavailable' : 'Traceability error'}</div>
          <div style={{ fontSize: 13, color: isGraphUnavailable ? TEXT_SEC : '#fca5a5', marginTop: 6 }}>{error}</div>
          <button onClick={load} style={{ marginTop: 10, padding: '6px 12px', background: isGraphUnavailable ? '#374151' : '#7f1d1d', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer' }}>Retry</button>
        </div>
        {isGraphUnavailable && <div style={{ fontSize: 11, color: MUTED, background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px' }}>Evidence uploaded to this incident is preserved in PostgreSQL. Traceability will be available once Neo4j projection completes. Endpoint: <code>GET /api/graph/incidents/{incidentId}/traceability</code></div>}
      </div>
    )
  }

  if (!data) {
    return <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>No traceability data.</div>
  }

  const allNodes = [data.incidentNode, ...data.nodes]
  const hasData = data.nodes.length > 0 || data.relationships.length > 0

  if (!hasData) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No evidence yet</div>
          <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>This incident has no incident-centric evidence. Use Evidence Collection to upload files or sync an integration with <code style={{ background: '#0B1120', padding: '2px 6px', borderRadius: 4, border: `1px solid ${BORDER}` }}>?incidentId={incidentId}</code>. Legacy case-based evidence still appears in the Evidence tab.</div>
          <div style={{ fontSize: 11, color: MUTED, marginTop: 8 }}>Depth: {data.depth} · incident: {data.incidentNode.stableId} ({data.incidentNode.label})</div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ fontSize: 12, color: TEXT_SEC }}>Incident: <span style={{ color: '#60A5FA', fontFamily: 'ui-monospace, monospace', fontWeight: 700 }}>{data.incidentNode.stableId}</span> · Depth {data.depth} · {allNodes.length} nodes · {data.relationships.length} relationships</div>
        <button onClick={load} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
      </div>

      <div style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 12, padding: 12, overflowX: 'auto' }}>
        <svg width="100%" height="240" viewBox="0 0 600 240" style={{ minWidth: 500, display: 'block' }}>
          {data.relationships.slice(0, 20).map((r, idx) => {
            const allIds = allNodes.map(n => n.stableId)
            const idxFrom = allIds.indexOf(r.fromStableId)
            const idxTo = allIds.indexOf(r.toStableId)
            const angle = (i: number) => (i / Math.max(allIds.length, 1)) * 2 * Math.PI - Math.PI / 2
            const pos = (i: number) => {
              if (i === 0) return { x: 300, y: 120 }
              const a = angle(i); const rad = 100
              return { x: 300 + Math.cos(a) * rad, y: 120 + Math.sin(a) * rad }
            }
            const p1 = pos(idxFrom >= 0 ? idxFrom : 0)
            const p2 = pos(idxTo >= 0 ? idxTo : 0)
            const color = r.type === 'HAS_EVIDENCE' ? '#16a34a' : r.type === 'BELONGS_TO' ? '#3b82f6' : r.type === 'CREATED_BY' ? '#f59e0b' : '#6366f1'
            return <g key={idx}><line x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y} stroke={color} strokeWidth={1.4} opacity={0.7} /><text x={(p1.x + p2.x) / 2} y={(p1.y + p2.y) / 2 - 4} fontSize={8} fill={color} textAnchor="middle">{r.type}</text></g>
          })}
          {allNodes.slice(0, 12).map((n, i) => {
            const angle = (i / Math.max(allNodes.length, 1)) * 2 * Math.PI - Math.PI / 2
            const isIncident = i === 0
            const pos = isIncident ? { x: 300, y: 120 } : { x: 300 + Math.cos(angle) * 100, y: 120 + Math.sin(angle) * 100 }
            const fill = n.label === 'Incident' ? '#065f46' : n.label === 'Case' ? '#1e40af' : n.label === 'Actor' ? '#92400e' : '#3730a3'
            return <g key={n.label + ':' + n.stableId}>
              <circle cx={pos.x} cy={pos.y} r={isIncident ? 20 : 14} fill={fill} stroke="#fff" strokeWidth={2} />
              <text x={pos.x} y={pos.y + 4} fontSize={7} fill="#fff" textAnchor="middle" fontWeight={700}>{n.label.slice(0, 4)}</text>
              <text x={pos.x} y={pos.y + (isIncident ? 32 : 26)} fontSize={7} fill="#334155" textAnchor="middle">{n.stableId.slice(0, 14)}</text>
            </g>
          })}
        </svg>
        <div style={{ fontSize: 10, color: MUTED, textAlign: 'center', marginTop: 4 }}>Incident-centric traceability — Incident → Evidence via HAS_EVIDENCE plus legacy Case/Actor edges. Limited to first 20 relationships.</div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 320, overflowY: 'auto', border: '1px solid #e2e8f0', borderRadius: 8, background: '#fff' }}>
        <div style={{ padding: '6px 8px', background: '#f1f5f9', fontSize: 11, fontWeight: 700, color: '#475569' }}>Nodes ({allNodes.length}) — Incident + Evidence + Actors</div>
        {allNodes.map((n) => (
          <div key={n.label + ':' + n.stableId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 12 }}>
            <button onClick={() => n.label !== 'Incident' && onNavigate?.('evidence', { evidenceId: n.stableId })} disabled={n.label === 'Incident'} style={{ background: 'transparent', border: 0, cursor: n.label === 'Incident' ? 'default' : 'pointer', textAlign: 'left', padding: 0 }}><span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 999, background: n.label === 'Incident' ? '#dcfce7' : n.label === 'Case' ? '#dbeafe' : n.label === 'Actor' ? '#fef3c7' : '#e0e7ff', color: n.label === 'Incident' ? '#065f46' : n.label === 'Case' ? '#1e40af' : n.label === 'Actor' ? '#92400e' : '#3730a3' }}>{n.label}</span> <span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 600, color: n.label === 'Incident' ? '#334155' : '#2563EB' }}>{n.stableId}</span> {n.label !== 'Incident' && <span style={{ fontSize: 10, color: '#64748B' }}>→ Evidence</span>}</button>
            <span style={{ color: MUTED, fontSize: 11 }}>{[n.title, n.sourceType, n.status].filter(Boolean).join(' · ') || '-'}</span>
          </div>
        ))}
        <div style={{ padding: '6px 8px', background: '#f1f5f9', fontSize: 11, fontWeight: 700, color: '#475569' }}>Relationships ({data.relationships.length}) — HAS_EVIDENCE (incident-centric) + legacy</div>
        {data.relationships.length === 0 ? <div style={{ padding: '8px', color: MUTED, fontSize: 12 }}>No relationships</div> : data.relationships.map((r, i) => (
          <div key={i} style={{ display: 'flex', gap: 6, alignItems: 'center', padding: '6px 8px', borderBottom: '1px solid #f1f5f9', fontSize: 11, flexWrap: 'wrap' }}>
            <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.fromLabel}:{r.fromStableId}</span>
            <RelationshipBadge type={r.type} />
            <span style={{ fontFamily: 'ui-monospace, monospace' }}>{r.toLabel}:{r.toStableId}</span>
          </div>
        ))}
      </div>

      <div style={{ fontSize: 11, color: MUTED, background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px' }}>
        Endpoint: <code>GET /api/graph/incidents/{incidentId}/traceability</code> · tenant-scoped via current organisation · Legacy <code>GET /api/graph/cases/{"{caseId}"}/traceability</code> remains.
      </div>
    </div>
  )
}
