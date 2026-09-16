import { useEffect, useState, useCallback } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { getTimeline } from '../../../services/investigationService'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'

/**
 * Evidence Chronology – deterministic reconstruction of evidence occurrences.
 * Current evidence ingestion does not provide structured domain event/action history.
 * Therefore the timeline currently represents deterministic evidence creation/update chronology.
 * Richer actions such as LOGIN, RECORD_ACCESSED, or RECORD_EXPORTED require the upstream
 * evidence source to provide structured event/action data or history.
 */
type TimelineEvent = {
  eventType: string
  eventTime: string
  title: string
  description?: string | null
  sourceType?: string | null
  sourceId?: string | null
  stableId?: string | null
  metadata?: unknown
  caseId?: string | null
  actorId?: string | null
  parentId?: string | null
  status?: string | null
  size?: number | null
  contentType?: string | null
  tags?: string[] | null
}

const TYPE_STYLE: Record<string, { bg: string; fg: string; dot: string }> = {
  EVIDENCE_CREATED: { bg: '#e0e7ff', fg: '#3730a3', dot: '#6366f1' },
  EVIDENCE_UPDATED: { bg: '#fef3c7', fg: '#92400e', dot: '#f59e0b' },
}

function formatTime(t?: string | null) {
  if (!t) return '-'
  try { return new Date(t).toLocaleString() } catch { return t }
}

export function InvestigationTimeline({ investigationId, onNavigate }: { investigationId: number; onNavigate?: (tab: string, opts?: { evidenceId?: string }) => void }) {
  const [page, setPage] = useState(0)
  const size = 50
  const [data, setData] = useState<{ events: TimelineEvent[]; totalElements: number; totalPages: number; page: number; size: number } | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (p: number) => {
    setLoading(true)
    setError(null)
    try {
      const res = await getTimeline(investigationId, p, size)
      setData({
        events: res.events ?? [],
        totalElements: res.totalElements ?? 0,
        totalPages: res.totalPages ?? 0,
        page: res.page ?? p,
        size: res.size ?? size,
      })
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 403) setError('You don’t have access to this investigation’s timeline (403).')
      else if (st === 404) setError('Investigation not found (404).')
      else if ((getApiErrorMessage(err, '').toLowerCase().includes('graph not ready'))) setError('Graph not ready – timeline requires canonical sync and projection (400).')
      else setError(getApiErrorMessage(err, 'Failed to load timeline'))
    } finally {
      setLoading(false)
    }
  }, [investigationId])

  useEffect(() => { load(page) }, [load, page])

  const events = data?.events ?? []
  const totalElements = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? (totalElements ? Math.ceil(totalElements / size) : 0)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 700, color: TEXT_MAIN }}>Evidence Chronology</div>
          <div style={{ fontSize: 11, color: TEXT_SEC, marginTop: 2 }}>Deterministic evidence creation/update chronology · {totalElements} events {totalElements ? `· Page ${page + 1} of ${totalPages || 1}` : ''} · eventTime ASC, eventType ASC, stableId ASC</div>
        </div>
        <button onClick={() => load(page)} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
      </div>

      <div style={{ fontSize: 11, color: '#94A3B8', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px', lineHeight: 1.5 }}>
        Evidence Chronology shows <strong style={{ color: TEXT_MAIN }}>canonical evidence creation/update</strong> only. No audit log. Current ingestion provides <code>sourceCreatedAt/sourceUpdatedAt</code> only – no structured LOGIN/RECORD_ACCESSED/RECORD_EXPORTED. Richer actions require upstream structured event data.
      </div>

      {loading ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading evidence chronology…</div>
      ) : error ? (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13 }}>{error} <button onClick={() => load(page)} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer' }}>Retry</button></div>
      ) : events.length === 0 ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No evidence chronology yet</div>
          <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>Collect evidence for this incident (manual upload or integration sync with <code style={{ background: '#0B1120', padding: '2px 6px', borderRadius: 4, border: `1px solid ${BORDER}` }}>?incidentId</code>) to see its incident-scoped creation/update chronology. Timeline merges direct incident evidence and linked evidence, deduped.</div>
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ padding: 12, borderBottom: `1px solid ${BORDER}`, fontSize: 11, color: '#475569', fontWeight: 700, letterSpacing: 0.6, textTransform: 'uppercase' }}>Evidence Chronology · eventTime ASC, eventType ASC, stableId ASC · linked evidence only</div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {events.map((ev, idx) => {
              const style = TYPE_STYLE[ev.eventType] || { bg: '#e2e8f0', fg: '#475569', dot: '#94a3b8' }
              return (
                <div key={`${ev.eventType}-${ev.stableId || ev.sourceId || idx}-${idx}`} style={{ display: 'flex', gap: 12, padding: '12px 16px', borderBottom: idx < events.length - 1 ? `1px solid ${BORDER}` : 'none', alignItems: 'flex-start' }}>
                  <div style={{ width: 10, height: 10, borderRadius: 999, background: style.dot, marginTop: 6, flexShrink: 0 }} />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 999, background: style.bg, color: style.fg, fontWeight: 700 }}>{ev.eventType}</span>
                      <span style={{ fontSize: 12, color: TEXT_SEC }}>{formatTime(ev.eventTime)}</span>
                      {ev.stableId && <span style={{ fontSize: 11, fontFamily: 'ui-monospace, monospace', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 6, padding: '2px 6px', color: TEXT_SEC }}>{ev.stableId}</span>}
                    </div>
                    <div style={{ fontSize: 13, fontWeight: 600, color: TEXT_MAIN, marginTop: 6, wordBreak: 'break-word' }}>{ev.title || 'Untitled evidence'}</div>
                    <div style={{ fontSize: 11, color: '#475569', marginTop: 4, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                      {ev.stableId && <span>Evidence: <span style={{ fontFamily: 'ui-monospace, monospace', color: TEXT_SEC }}>{ev.stableId}</span></span>}
                      {ev.sourceType && <span>· Source: {ev.sourceType}</span>}
                      {ev.caseId && <span>· Case: {ev.caseId}</span>}
                      {ev.actorId && <span>· Actor: {ev.actorId}</span>}
                      {ev.parentId && <span>· Parent: {ev.parentId}</span>}
                    </div>
                    <div style={{ fontSize: 11, color: '#475569', marginTop: 2, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      {ev.status && <span>Status: {ev.status}</span>}
                      {ev.contentType && <span>· {ev.contentType}</span>}
                      {ev.size != null && <span>· {ev.size} bytes</span>}
                    </div>
                    {ev.tags && ev.tags.length > 0 && (
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
                        {ev.tags.map((t) => (
                          <span key={t} style={{ fontSize: 11, background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 6, padding: '2px 6px', color: TEXT_SEC }}>{t}</span>
                        ))}
                      </div>
                    )}
                    <div style={{ marginTop: 6 }}>
                      <span style={{ fontSize: 11, color: '#64748B' }}>Canonical evidence · </span>
                      {ev.stableId ? <button onClick={() => onNavigate?.('evidence', { evidenceId: ev.stableId! })} style={{ fontSize: 11, color: '#60A5FA', fontFamily: 'ui-monospace, monospace', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 6, padding: '2px 6px', cursor: 'pointer' }}>Open Evidence: {ev.stableId} →</button> : <span style={{ fontSize: 11, color: '#94A3B8', fontFamily: 'ui-monospace, monospace' }}>[No stableId]</span>}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center', padding: 12, borderTop: `1px solid ${BORDER}` }}>
              <button disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} style={{ padding: '6px 10px', background: page === 0 ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page === 0 ? 'not-allowed' : 'pointer' }}>Prev</button>
              <span style={{ fontSize: 12, color: TEXT_SEC, alignSelf: 'center' }}>Page {page + 1} of {totalPages} · {totalElements} events</span>
              <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} style={{ padding: '6px 10px', background: page + 1 >= totalPages ? '#0B1120' : CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: page + 1 >= totalPages ? 'not-allowed' : 'pointer' }}>Next</button>
            </div>
          )}
        </div>
      )}
      <div style={{ fontSize: 11, color: '#475569', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px' }}>
        Backend: <code>GET /api/incidents/{'{id}'}/timeline</code> alias <code>GET /api/investigations/{'{id}'}/timeline</code> – Evidence Chronology (EVIDENCE_CREATED/EVIDENCE_UPDATED only), incident-scoped union of <code>canonical.incident_id</code> + linked evidence, deterministic <code>eventTime ASC, eventType ASC, stableId ASC</code>, bounded <code>default 50 max 100</code>.
      </div>
    </div>
  )
}
