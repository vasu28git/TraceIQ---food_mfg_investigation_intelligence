import type { Investigation } from '../../types/investigation'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

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

export function InvestigationOverview({
  investigation,
}: {
  investigation: Investigation
  onUpdated?: (inv: Investigation) => void
  onNavigate?: (tab: string, opts?: { evidenceId?: string }) => void
}) {
  const status = (investigation.status || 'DRAFT').toUpperCase()
  const invExtra = investigation as unknown as {
    complaintKey?: string
    batchReference?: string
    productReference?: string
    orderReference?: string
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 20 }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 16 }}>
          Incident & Investigation Metadata
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16, fontSize: 13 }}>
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Investigation Key</span>
            <span style={{ color: TEXT_MAIN, fontFamily: 'monospace', fontWeight: 700, fontSize: 14 }}>{investigation.investigationKey}</span>
          </div>
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Status</span>
            <StatusBadge status={status} />
          </div>
          {invExtra.complaintKey && (
            <div>
              <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Associated Complaint</span>
              <span style={{ color: '#60A5FA', fontWeight: 600 }}>{invExtra.complaintKey}</span>
            </div>
          )}
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Batch Reference</span>
            <span style={{ color: '#F59E0B', fontFamily: 'monospace', fontWeight: 700 }}>{invExtra.batchReference || 'None'}</span>
          </div>
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Product Reference</span>
            <span style={{ color: TEXT_MAIN }}>{invExtra.productReference || '-'}</span>
          </div>
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Order Reference</span>
            <span style={{ color: TEXT_SEC }}>{invExtra.orderReference || '-'}</span>
          </div>
          <div>
            <span style={{ color: MUTED, display: 'block', fontSize: 11, marginBottom: 4 }}>Created Date</span>
            <span style={{ color: TEXT_SEC }}>{investigation.createdAt ? new Date(investigation.createdAt).toLocaleString() : '-'}</span>
          </div>
        </div>
      </div>
    </div>
  )
}

