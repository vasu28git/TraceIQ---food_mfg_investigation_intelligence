import { useState } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { getReport, getReportBlob } from '../../../services/investigationService'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

type Format = 'JSON' | 'CSV' | 'PDF'

export function InvestigationReports({ investigationId }: { investigationId: number }) {
  const [format, setFormat] = useState<Format>('PDF')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [jsonPreview, setJsonPreview] = useState<unknown | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const handleGenerate = async () => {
    setLoading(true)
    setError(null)
    setSuccessMsg(null)
    setJsonPreview(null)
    try {
      if (format === 'JSON') {
        const data = await getReport(investigationId, 'JSON')
        setJsonPreview(data)
        setSuccessMsg('JSON report generated — preview below. Use browser save if needed.')
      } else {
        const blob = await getReportBlob(investigationId, format as 'CSV' | 'PDF')
        // Derive filename safely from investigationId (investigationKey unavailable without GET /investigations/{id} gap)
        const ext = format.toLowerCase()
        const filename = `report-${investigationId}.${ext}`
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = filename
        document.body.appendChild(a)
        a.click()
        a.remove()
        URL.revokeObjectURL(url)
        setSuccessMsg(`${format} downloaded as ${filename}`)
      }
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 400) {
        const msg = getApiErrorMessage(err, '')
        if (msg.toLowerCase().includes('format')) setError(`Invalid format (400): ${msg}`)
        else if (msg.toLowerCase().includes('graph not ready')) setError('Graph not ready – cannot generate report (400). Ensure canonical sync and projection succeeded.')
        else setError(msg || 'Bad request (400).')
      } else if (st === 403) setError('You don’t have access to this report (403).')
      else if (st === 404) setError('Investigation not found (404).')
      else if (st === 409) setError(getApiErrorMessage(err, 'Report exceeds safe limits (409) – too many evidence/checks/decisions/timeline events.'))
      else setError(getApiErrorMessage(err, 'Failed to generate report'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 16 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: TEXT_MAIN }}>Generate report</div>
        <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>
          Read-only projection of existing investigation data: <code style={{ background: '#0B1120', padding: '2px 6px', borderRadius: 4, border: `1px solid ${BORDER}` }}>metadata → complaint → investigation → timeline → evidence → findings → checks → decisions → final result</code>.
          No report table, no AI, no new permissions. Backend default <code style={{ background: '#0B1120', padding: '2px 6px', borderRadius: 4 }}>REPORT_DEFAULT_FORMAT = PDF</code> (PDF|CSV|JSON). Findings show <code>title, status, conclusion, supportingEvidence, supportingChecks</code>.
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap', alignItems: 'center' }}>
          {(['JSON', 'CSV', 'PDF'] as Format[]).map((f) => (
            <button
              key={f}
              onClick={() => setFormat(f)}
              style={{
                padding: '6px 12px',
                borderRadius: 999,
                border: `1px solid ${format === f ? '#6366F1' : BORDER}`,
                background: format === f ? '#1E1B4B' : 'transparent',
                color: format === f ? '#A5B4FC' : TEXT_SEC,
                cursor: 'pointer',
                fontSize: 12,
                fontWeight: format === f ? 700 : 500,
              }}
            >
              {f} {f === 'PDF' && <span style={{ fontSize: 10, background: '#1E293B', color: '#64748B', padding: '2px 6px', borderRadius: 999, marginLeft: 6 }}>default</span>}
            </button>
          ))}
          <div style={{ flex: 1 }} />
          <button onClick={handleGenerate} disabled={loading} style={{ padding: '8px 16px', background: loading ? '#475569' : '#0F172A', color: '#fff', border: `1px solid ${BORDER}`, borderRadius: 8, cursor: loading ? 'not-allowed' : 'pointer', fontWeight: 700, fontSize: 13 }}>
            {loading ? 'Generating…' : format === 'JSON' ? 'Generate preview' : `Download ${format}`}
          </button>
        </div>
        <div style={{ fontSize: 11, color: MUTED, marginTop: 8 }}>
          Selected: <span style={{ color: TEXT_SEC, fontWeight: 600 }}>{format}</span> · Investigator remains source of truth; report is read-only, not persisted, generatedAt is ephemeral.
        </div>
      </div>

      {error && (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button onClick={handleGenerate} style={{ background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer', fontSize: 12 }}>Retry</button>
        </div>
      )}

      {successMsg && (
        <div style={{ background: '#052e1a', border: '1px solid #166534', borderRadius: 12, padding: 12, color: '#86efac', fontSize: 13 }}>{successMsg}</div>
      )}

      {jsonPreview != null && (
        <div style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ padding: 12, borderBottom: '1px solid #e5e7eb', background: '#f8fafc', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: '#0F172A' }}>JSON preview</span>
            <button
              onClick={() => {
                const blob = new Blob([JSON.stringify(jsonPreview, null, 2)], { type: 'application/json' })
                const url = URL.createObjectURL(blob)
                const a = document.createElement('a')
                a.href = url
                a.download = `report-${investigationId}.json`
                document.body.appendChild(a)
                a.click()
                a.remove()
                URL.revokeObjectURL(url)
              }}
              style={{ padding: '4px 8px', background: '#0F172A', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer', fontSize: 11 }}
            >
              Download .json
            </button>
          </div>
          <div style={{ padding: 12, maxHeight: 480, overflow: 'auto' }}>
            <div style={{ fontSize: 11, color: '#64748B', marginBottom: 8 }}>Safe fields only – no orgId, normalizedPayload, contentHash, storageRef, credentials, Neo4j IDs.</div>
            {(jsonPreview as unknown as { findings?: unknown[] })?.findings !== undefined && (
              <div style={{ marginBottom: 8, background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 8, padding: '8px 10px', fontSize: 11, color: '#166534' }}>
                Findings: {(jsonPreview as unknown as { findings: unknown[] }).findings.length} — Evidence → Finding → Supporting Evidence/Checks → Decision → Final Result
              </div>
            )}
            <pre style={{ margin: 0, background: '#0B1120', color: '#E2E8F0', padding: 12, borderRadius: 8, fontSize: 11, whiteSpace: 'pre-wrap', wordBreak: 'break-all', maxHeight: 400, overflow: 'auto' }}>
              {JSON.stringify(jsonPreview, null, 2)}
            </pre>
            <div style={{ marginTop: 8, fontSize: 11, color: MUTED }}>
              Sections: <code>reportMetadata</code> <code>complaint</code> <code>investigation</code> <code>timeline</code> <code>evidence</code> <code>findings</code> <code>checks</code> <code>decisions</code> <code>finalResult</code> – as returned by <code>InvestigationReportResponse</code>. Findings show <code>title, description, conclusion, status, supportingEvidence, supportingChecks</code>.
            </div>
          </div>
        </div>
      )}

      <div style={{ background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 12, padding: 12 }}>
        <div style={{ fontSize: 11, color: '#64748B', fontWeight: 700, letterSpacing: 0.6, textTransform: 'uppercase' }}>What this report is</div>
        <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 6 }}>
          Reports are <span style={{ color: TEXT_MAIN, fontWeight: 600 }}>read-only generated projections</span> of existing investigation data. They are <span style={{ color: TEXT_MAIN, fontWeight: 600 }}>not persisted as a second source of truth</span>, do not create `investigation → timeline → findings → checks → decisions → final result`, do not mutate `Neo4j`, do not call AI, and are <code>GRAPH_READY</code> gated. Findings appear as <code>findings: [title, status, conclusion, supportingEvidence, supportingChecks]</code>.
        </div>
        <div style={{ fontSize: 11, color: MUTED, marginTop: 8 }}>Security: no <code>orgId</code>, <code>normalizedPayload</code>, <code>contentHash</code>, <code>storageRef</code>, <code>syncId</code>, credentials, or Neo4j internal IDs are ever exposed. Tenant isolation via <code>AuthorizationService.getCurrentOrgId()</code>.</div>
      </div>
    </div>
  )
}
