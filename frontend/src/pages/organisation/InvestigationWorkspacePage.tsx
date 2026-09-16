import { useEffect, useState, useCallback } from 'react'
import { useParams, Link, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/client'
import type { Investigation } from '../../types/investigation'
import { getInvestigation } from '../../services/investigationService'
import { InvestigationOverview } from './InvestigationOverview'
import { InvestigationNotes } from './investigation/InvestigationNotes'
import { InvestigationTimeline } from './investigation/InvestigationTimeline'
import { InvestigationEvidenceGraph } from './investigation/InvestigationEvidenceGraph'
import { InvestigationEvidenceReview } from './investigation/InvestigationEvidenceReview'
import { InvestigationFindings } from './investigation/InvestigationFindings'
import { InvestigationConclusion } from './investigation/InvestigationConclusion'
import { InvestigationActions } from './investigation/InvestigationActions'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

type Tab = 'overview' | 'evidence' | 'findings' | 'conclusion' | 'actions' | 'notes' | 'timeline' | 'evidence-graph'

const TABS: { key: Tab; label: string }[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'evidence', label: 'Evidence' },
  { key: 'findings', label: 'Findings' },
  { key: 'conclusion', label: 'Conclusion' },
  { key: 'actions', label: 'Actions' },
  { key: 'notes', label: 'Notes' },
  { key: 'timeline', label: 'Timeline' },
  { key: 'evidence-graph', label: 'Evidence Graph' },
]

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

export function InvestigationWorkspacePage() {
  const { investigationId } = useParams<{ investigationId: string }>()
  const [searchParams, setSearchParams] = useSearchParams()
  
  const allTabKeys = TABS.map(t => t.key)
  const initialTab = (searchParams.get('tab') as Tab) || 'overview'
  const validTab = allTabKeys.includes(initialTab) ? initialTab : 'overview'

  const [tab, setTab] = useState<Tab>(validTab)
  const [investigation, setInvestigation] = useState<Investigation | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const handleTabChange = useCallback((next: string) => {
    const typed = next as Tab
    if (!allTabKeys.includes(typed)) return
    setTab(typed)
    setSearchParams(prev => {
      const p = new URLSearchParams(prev)
      p.set('tab', typed)
      return p
    }, { replace: true })
  }, [allTabKeys, setSearchParams])

  useEffect(() => {
    const urlTab = searchParams.get('tab') as Tab | null
    if (urlTab && allTabKeys.includes(urlTab) && urlTab !== tab) {
      setTab(urlTab)
    }
  }, [searchParams])

  const idNum = investigationId ? Number(investigationId) : NaN

  const loadData = useCallback(async () => {
    if (!investigationId || Number.isNaN(idNum)) {
      setError('Invalid investigation ID')
      setLoading(false)
      return
    }
    setLoading(true)
    setError(null)
    try {
      const invRes = await getInvestigation(idNum)
      setInvestigation(invRes)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to load investigation')
      if (status === 404) {
        setError(`Investigation not found (404).`)
      } else if (status === 403) {
        setError(`You don't have access to this investigation (403).`)
      } else {
        setError(msg)
      }
    } finally {
      setLoading(false)
    }
  }, [investigationId, idNum])

  useEffect(() => {
    loadData()
  }, [loadData])

  if (loading) {
    return (
      <div role="status" aria-live="polite" style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 32, color: TEXT_SEC, textAlign: 'center' }}>
        Loading incident details…
      </div>
    )
  }

  const invExtra = investigation as unknown as {
    complaintKey?: string
    batchReference?: string
    productReference?: string
    orderReference?: string
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Top Breadcrumbs */}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 12, color: '#64748B' }}>
        <Link to="/organisation/investigations" style={{ color: '#60A5FA', textDecoration: 'none' }}>← Investigations</Link>
        <span>·</span>
        <span>Incident Container</span>
        <span>·</span>
        <span style={{ color: TEXT_SEC, fontFamily: 'monospace' }}>{investigation?.investigationKey || `INC-${investigationId}`}</span>
      </div>

      {/* Incident Header Card */}
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 18, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 12 }}>
          <div style={{ flex: 1, minWidth: 280 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <span style={{ background: '#0B1120', border: `1px solid ${BORDER}`, color: '#A5B4FC', borderRadius: 6, padding: '3px 8px', fontFamily: 'ui-monospace, monospace', fontSize: 12, fontWeight: 700 }}>
                {investigation?.investigationKey || `INC-${investigationId}`}
              </span>
              <StatusBadge status={investigation?.status} />
              {invExtra?.complaintKey && (
                <span style={{ fontSize: 12, background: '#1E293B', color: '#60A5FA', padding: '2px 8px', borderRadius: 6 }}>
                  Complaint: {invExtra.complaintKey}
                </span>
              )}
            </div>

            <h1 style={{ fontSize: 20, fontWeight: 800, color: TEXT_MAIN, margin: '8px 0 4px 0', wordBreak: 'break-word' }}>
              {investigation?.title || `Incident #${investigationId}`}
            </h1>

            {investigation?.description && (
              <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 4 }}>{investigation.description}</div>
            )}
          </div>

          {/* Context Badges Pill Grid */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            {invExtra?.batchReference && (
              <div style={{ background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '6px 12px', fontSize: 12 }}>
                <span style={{ color: MUTED, display: 'block', fontSize: 10, fontWeight: 700, textTransform: 'uppercase' }}>Batch</span>
                <span style={{ color: '#F59E0B', fontWeight: 700, fontFamily: 'monospace' }}>{invExtra.batchReference}</span>
              </div>
            )}
            {invExtra?.productReference && (
              <div style={{ background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '6px 12px', fontSize: 12 }}>
                <span style={{ color: MUTED, display: 'block', fontSize: 10, fontWeight: 700, textTransform: 'uppercase' }}>Product</span>
                <span style={{ color: TEXT_MAIN, fontWeight: 600 }}>{invExtra.productReference}</span>
              </div>
            )}
            {invExtra?.orderReference && (
              <div style={{ background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '6px 12px', fontSize: 12 }}>
                <span style={{ color: MUTED, display: 'block', fontSize: 10, fontWeight: 700, textTransform: 'uppercase' }}>Order</span>
                <span style={{ color: TEXT_SEC, fontWeight: 600 }}>{invExtra.orderReference}</span>
              </div>
            )}
          </div>
        </div>
      </div>

      {error && (
        <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#991B1B', padding: '10px 12px', borderRadius: 8, fontSize: 13, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
          <span>{error}</span>
          <button onClick={loadData} style={{ border: '1px solid #FCA5A5', borderRadius: 6, background: '#FFF7ED', color: '#9A3412', padding: '6px 10px', cursor: 'pointer', fontWeight: 700 }}>Retry</button>
        </div>
      )}

      {/* Tabs */}
      <div role="tablist" aria-label="Investigation tabs" style={{ display: 'flex', gap: 6, flexWrap: 'wrap', borderBottom: `1px solid ${BORDER}`, paddingBottom: 8 }}>
        {TABS.map((t) => {
          const isSelected = tab === t.key
          return (
            <button
              key={t.key}
              role="tab"
              aria-selected={isSelected}
              onClick={() => handleTabChange(t.key)}
              style={{
                padding: '8px 16px',
                borderRadius: 8,
                border: `1px solid ${isSelected ? '#6366F1' : BORDER}`,
                background: isSelected ? '#1E1B4B' : 'transparent',
                color: isSelected ? '#A5B4FC' : TEXT_SEC,
                cursor: 'pointer',
                fontSize: 13,
                fontWeight: isSelected ? 700 : 500,
                whiteSpace: 'nowrap',
                transition: 'all 0.15s ease',
              }}
            >
              {t.label}
            </button>
          )
        })}
      </div>

      {/* View Container */}
      <div>
        {tab === 'overview' && investigation && <InvestigationOverview investigation={investigation} />}
        {tab === 'evidence' && investigation && <InvestigationEvidenceReview investigation={investigation} />}
        {tab === 'findings' && investigation && <InvestigationFindings investigation={investigation} />}
        {tab === 'conclusion' && investigation && <InvestigationConclusion investigation={investigation} />}
        {tab === 'actions' && investigation && <InvestigationActions investigation={investigation} />}
        {tab === 'notes' && investigation && <InvestigationNotes investigationId={investigation.id} investigationStatus={investigation.status} />}
        {tab === 'timeline' && investigation && <InvestigationTimeline investigationId={investigation.id} />}
        {tab === 'evidence-graph' && investigation && (
          <InvestigationEvidenceGraph
            investigationId={investigation.id}
            batchReference={invExtra?.batchReference}
          />
        )}
      </div>
    </div>
  )
}

