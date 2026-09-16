import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { apiClient, getApiErrorMessage } from '../../api/client'
import { useAuthStore } from '../../store/authStore'
import { getInvestigations } from '../../services/investigationService'
import { graphEvidence, checkOrgGraphReady } from '../../services/investigationService'
import type { Investigation } from '../../types/investigation'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const PRIMARY = '#6366F1'

function StatCard({ label, value, sub, loading, accent }: { label: string; value: number | string | null; sub?: string; loading?: boolean; accent?: string }) {
  return (
    <div style={{ flex: '1 1 160px', background: CARD_BG, border: `1px solid ${BORDER}`, borderLeft: accent ? `4px solid ${accent}` : `1px solid ${BORDER}`, borderRadius: 12, padding: 16, minWidth: 160 }}>
      <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.5, textTransform: 'uppercase', fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: 28, fontWeight: 800, color: TEXT_MAIN, marginTop: 8 }}>{loading ? '—' : value ?? '—'}</div>
      {sub && <div style={{ fontSize: 11, color: TEXT_SEC, marginTop: 4 }}>{sub}</div>}
    </div>
  )
}

function formatDate(d?: string | null) {
  if (!d) return '-'
  try { return new Date(d).toLocaleDateString() } catch { return d }
}

export function OrganisationDashboard() {
  const orgId = useAuthStore((s) => s.organisationId)
  const username = useAuthStore((s) => s.username)
  const navigate = useNavigate()

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [investigations, setInvestigations] = useState<Investigation[]>([])
  const [totalInvestigations, setTotalInvestigations] = useState<number | null>(null)
  const [graphStatus, setGraphStatus] = useState<{ ready: boolean | null; evidenceCount: number | null; message?: string; loading: boolean; error?: string | null }>({ ready: null, evidenceCount: null, loading: true })
  const [evidence, setEvidence] = useState<{ list: Array<{ stableId: string; title?: string; sourceType?: string; status?: string; caseId?: string; actorId?: string }>; total: number | null; loading: boolean }>({ list: [], total: null, loading: true })
  const [checksOpen, setChecksOpen] = useState<number | null>(null)

  useEffect(() => {
    if (!orgId) {
      setLoading(false)
      return
    }
    let mounted = true
    const fetchAll = async () => {
      setLoading(true)
      setError(null)
      try {
        // Investigations: fetch first page and count by status
        const invRes = await getInvestigations(0, 50).catch(() => ({ content: [], totalElements: 0 } as unknown as { content: Investigation[]; totalElements: number }))
        const invList = Array.isArray((invRes as unknown as { content: Investigation[] }).content) ? (invRes as unknown as { content: Investigation[] }).content : []
        const total = (invRes as unknown as { totalElements: number }).totalElements ?? invList.length
        if (!mounted) return
        setInvestigations(invList)
        setTotalInvestigations(total)

        // Evidence: graph evidence first page
        try {
          const evRes = await graphEvidence({ page: 0, size: 5 }) as unknown as { content?: Array<{ stableId: string; title?: string; sourceType?: string; status?: string; caseId?: string; actorId?: string }>; totalElements?: number } | Array<{ stableId: string }>
          const evList = Array.isArray(evRes) ? evRes as Array<{ stableId: string }> : (evRes as { content?: Array<{ stableId: string }> }).content ?? []
          const evTotal = Array.isArray(evRes) ? evList.length : (evRes as { totalElements?: number }).totalElements ?? evList.length
          if (mounted) setEvidence({ list: evList.slice(0, 5) as Array<{ stableId: string; title?: string; sourceType?: string; status?: string }>, total: evTotal, loading: false })
        } catch {
          if (mounted) setEvidence({ list: [], total: 0, loading: false })
        }

        // Graph readiness
        try {
          const gr = await checkOrgGraphReady() as unknown as { graphReady?: boolean; ready?: boolean; valid?: boolean; graphValid?: boolean; evidenceCount?: number; message?: string }
          const ready = gr.graphReady ?? gr.ready ?? gr.valid ?? gr.graphValid ?? false
          const count = gr.evidenceCount ?? 0
          if (mounted) setGraphStatus({ ready: !!ready, evidenceCount: count, message: gr.message, loading: false })
        } catch (err: unknown) {
          if (mounted) setGraphStatus({ ready: false, evidenceCount: 0, loading: false, error: getApiErrorMessage(err, 'Graph status unavailable') })
        }

        // Open checks: aggregate from investigations (best effort, existing API)
        try {
          // For each investigation, count open checks if available via /investigations/{id}/checks
          // To avoid N+1, just show placeholder if not available
          setChecksOpen(null)
        } catch {
          setChecksOpen(null)
        }
      } catch (err: unknown) {
        const ax = err as { response?: { status?: number } }
        if (ax.response?.status === 401) setError('Session expired. Please login again.')
        else if (ax.response?.status === 403) setError('Forbidden: no access to organisation data.')
        else setError(getApiErrorMessage(err, 'Failed to load dashboard'))
      } finally {
        if (mounted) setLoading(false)
      }
    }
    fetchAll()
    return () => { mounted = false }
  }, [orgId])

  if (!orgId) {
    return (
      <div style={{ padding: 24, background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, color: TEXT_MAIN }}>
        <h3 style={{ margin: 0 }}>No organisation context</h3>
        <p style={{ color: TEXT_SEC, fontSize: 14 }}>Your account is not linked to an organisation.</p>
      </div>
    )
  }

  const draftCount = investigations.filter(i => i.status === 'DRAFT').length
  const activeCount = investigations.filter(i => i.status === 'ACTIVE').length
  const completedCount = investigations.filter(i => i.status === 'COMPLETED').length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 22, fontWeight: 800 }}>Overview</h2>
        <p style={{ margin: '6px 0 0', color: TEXT_SEC, fontSize: 13 }}>
          Welcome, <span style={{ color: TEXT_MAIN, fontWeight: 600 }}>{username}</span> · Organisation #{orgId}
        </p>
      </div>

      {error && <div style={{ padding: 12, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, color: '#fecaca', fontSize: 14 }}>{error}</div>}

      {/* Summary cards */}
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <StatCard label="Active Investigations" value={activeCount} loading={loading} sub={`${totalInvestigations ?? 0} total`} accent="#22C55E" />
        <StatCard label="Draft Investigations" value={draftCount} loading={loading} sub="Ready to activate" accent="#6366F1" />
        <StatCard label="Completed Investigations" value={completedCount} loading={loading} sub="Awaiting archive" accent="#3B82F6" />
        <StatCard label="Total Evidence" value={evidence.total} loading={evidence.loading} sub="Canonical" accent="#8B5CF6" />
        <StatCard label="Open Checks" value={checksOpen} loading={loading} sub={checksOpen === null ? 'Open investigations' : `${checksOpen} open`} accent="#F59E0B" />
        <StatCard
          label="Graph Status"
          value={graphStatus.loading ? null : graphStatus.ready ? 'READY' : 'NOT READY'}
          loading={graphStatus.loading}
          sub={graphStatus.loading ? 'Checking...' : graphStatus.error ? graphStatus.error : graphStatus.evidenceCount !== null ? `${graphStatus.evidenceCount} evidence` : graphStatus.message || ''}
          accent={graphStatus.ready ? '#22C55E' : '#F59E0B'}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 12 }}>
        {/* Recent Investigations */}
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ margin: 0, color: TEXT_MAIN, fontSize: 14 }}>Recent Investigations</h3>
            <Link to="/organisation/investigations" style={{ fontSize: 12, color: PRIMARY, textDecoration: 'none' }}>View all →</Link>
          </div>
          {loading ? (
            <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 12 }}>Loading...</div>
          ) : investigations.length === 0 ? (
            <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 12 }}>No investigations yet. <Link to="/organisation/investigations" style={{ color: PRIMARY }}>Create one</Link></div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}>
              {investigations.slice(0, 5).map(inv => (
                <div
                  key={inv.id}
                  onClick={() => navigate(`/organisation/investigations/${inv.id}`)}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 12px', background: '#0F172A', border: `1px solid ${BORDER}`, borderRadius: 8, cursor: 'pointer' }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: 11, color: TEXT_SEC, fontFamily: 'ui-monospace, monospace' }}>{inv.investigationKey} · #{inv.id}</div>
                    <div style={{ fontSize: 13, color: TEXT_MAIN, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{inv.title}</div>
                    <div style={{ fontSize: 11, color: TEXT_SEC }}>{formatDate(inv.updatedAt || inv.createdAt)} · {inv.status}</div>
                  </div>
                  <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: inv.status === 'ACTIVE' ? '#dcfce7' : inv.status === 'DRAFT' ? '#e0e7ff' : '#e2e8f0', color: inv.status === 'ACTIVE' ? '#166534' : '#475569', fontWeight: 700 }}>{inv.status}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Recent Evidence */}
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ margin: 0, color: TEXT_MAIN, fontSize: 14 }}>Recent Evidence</h3>
            <Link to="/organisation/files" style={{ fontSize: 12, color: PRIMARY, textDecoration: 'none' }}>View files →</Link>
          </div>
          {evidence.loading ? (
            <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 12 }}>Loading evidence...</div>
          ) : evidence.list.length === 0 ? (
            <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 12 }}>No evidence yet. Upload via Files or sync an integration.</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 12 }}>
              {evidence.list.map(ev => (
                <div
                  key={ev.stableId}
                  onClick={() => navigate(`/organisation/evidence-graph?stableId=${encodeURIComponent(ev.stableId)}`)}
                  style={{ padding: '10px 12px', background: '#0F172A', border: `1px solid ${BORDER}`, borderRadius: 8, cursor: 'pointer' }}
                >
                  <div style={{ fontSize: 11, color: '#60A5FA', fontFamily: 'ui-monospace, monospace' }}>{ev.stableId}</div>
                  <div style={{ fontSize: 13, color: TEXT_MAIN, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{ev.title || '-'}</div>
                  <div style={{ fontSize: 11, color: TEXT_SEC }}>{ev.sourceType || '-'} · {ev.status || '-'} {ev.caseId ? `· Case ${ev.caseId}` : ''} {ev.actorId ? `· Actor ${ev.actorId}` : ''}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Graph Status detailed */}
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, color: TEXT_MAIN, fontSize: 14 }}>Graph Status</h3>
        {graphStatus.loading ? (
          <div style={{ color: TEXT_SEC, fontSize: 13, marginTop: 8 }}>Checking graph readiness...</div>
        ) : graphStatus.error ? (
          <div style={{ marginTop: 8, padding: 10, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 8, color: '#fecaca', fontSize: 13 }}>{graphStatus.error}</div>
        ) : (
          <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <span style={{ padding: '4px 10px', borderRadius: 999, background: graphStatus.ready ? '#dcfce7' : '#fef3c7', color: graphStatus.ready ? '#166534' : '#92400e', fontSize: 12, fontWeight: 700 }}>
              {graphStatus.ready ? '✓ READY' : '○ NOT READY'}
            </span>
            <span style={{ fontSize: 12, color: TEXT_SEC }}>{graphStatus.evidenceCount ?? 0} evidence · {graphStatus.message || (graphStatus.ready ? 'Graph validated' : 'Sync or upload evidence to make graph ready')}</span>
          </div>
        )}
        <div style={{ fontSize: 11, color: '#475569', marginTop: 8 }}>Source: <code style={{ background: '#0B1120', padding: '1px 4px', borderRadius: 4, border: `1px solid ${BORDER}` }}>GET /graph/validation</code> · tenant-isolated, no Neo4j direct.</div>
      </div>

      {/* Quick Actions */}
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, color: TEXT_MAIN, fontSize: 14 }}>Quick Actions</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10, marginTop: 12 }}>
          <Link to="/organisation/investigations" style={{ padding: '12px', background: PRIMARY, color: '#fff', borderRadius: 8, textDecoration: 'none', fontSize: 14, textAlign: 'center', fontWeight: 600 }}>Create Investigation</Link>
          <Link to="/organisation/complaints" style={{ padding: '12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, textDecoration: 'none', fontSize: 14, textAlign: 'center' }}>Create Complaint</Link>
          <Link to="/organisation/files" style={{ padding: '12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, textDecoration: 'none', fontSize: 14, textAlign: 'center' }}>Upload Evidence</Link>
          <Link to="/organisation/investigations" style={{ padding: '12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, textDecoration: 'none', fontSize: 14, textAlign: 'center' }}>Open Investigations</Link>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <span style={{ fontSize: 12, color: TEXT_SEC, padding: '6px 10px', border: `1px solid ${BORDER}`, borderRadius: 999, background: CARD_BG }}>Org #{orgId}</span>
        <span style={{ fontSize: 12, color: TEXT_SEC, padding: '6px 10px', border: `1px solid ${BORDER}`, borderRadius: 999, background: CARD_BG }}>{username}</span>
      </div>
    </div>
  )
}
