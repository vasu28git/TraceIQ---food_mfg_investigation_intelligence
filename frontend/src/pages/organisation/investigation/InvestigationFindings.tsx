import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../../../api/client'
import {
  createInvestigationFinding,
  getFindingEvidenceTraceability,
  getInvestigationEvidenceDetail,
  listInvestigationEvidence,
  listInvestigationFindings,
  updateInvestigationFinding,
} from '../../../services/investigationService'
import type {
  FindingEvidenceTraceability,
  Investigation,
  InvestigationEvidence,
  InvestigationFinding,
  InvestigationFindingRequest,
} from '../../../types/investigation'
import { EvidenceDetailDrawer } from './EvidenceDetailDrawer'

const CARD = '#111827'
const PANEL = '#0F172A'
const BORDER = '#1E293B'
const TEXT = '#F8FAFC'
const MUTED = '#94A3B8'
const PRIMARY = '#6366F1'
const EMPTY_FORM: InvestigationFindingRequest = { statement: '', category: 'QUALITY', confidence: 'MEDIUM', status: 'OPEN', reasoning: '', evidence: [] }

function StatCard({ label, value, sub, accent }: { label: string; value: string | number; sub?: string; accent: string }) {
  return (
    <div style={{ flex: '1 1 150px', minWidth: 145, background: CARD, border: `1px solid ${BORDER}`, borderLeft: `4px solid ${accent}`, borderRadius: 10, padding: 14 }}>
      <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700, letterSpacing: 0.4 }}>{label}</div>
      <div style={{ color: TEXT, fontSize: 25, fontWeight: 800, marginTop: 6 }}>{value}</div>
      {sub && <div style={{ color: MUTED, fontSize: 11, marginTop: 3 }}>{sub}</div>}
    </div>
  )
}

function StatusBadge({ value }: { value: string }) {
  const colors: Record<string, [string, string]> = { OPEN: ['#1D4ED8', '#DBEAFE'], CONFIRMED: ['#166534', '#DCFCE7'], DISMISSED: ['#7F1D1D', '#FECACA'] }
  const [bg, fg] = colors[value] || ['#334155', '#E2E8F0']
  return <span style={{ background: bg, color: fg, borderRadius: 999, padding: '3px 8px', fontSize: 10, fontWeight: 800 }}>{value}</span>
}

export function InvestigationFindings({ investigation }: { investigation: Investigation }) {
  const navigate = useNavigate()
  const [findings, setFindings] = useState<InvestigationFinding[]>([])
  const [eligible, setEligible] = useState<InvestigationEvidence[]>([])
  const [eligibleLoading, setEligibleLoading] = useState(true)
  const [eligibleError, setEligibleError] = useState<string | null>(null)
  const [form, setForm] = useState<InvestigationFindingRequest>(EMPTY_FORM)
  const [editing, setEditing] = useState<InvestigationFinding | null>(null)
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [confidenceFilter, setConfidenceFilter] = useState('')
  const [sort, setSort] = useState('updated')
  const [traceability, setTraceability] = useState<FindingEvidenceTraceability | null>(null)
  const [traceabilityLoading, setTraceabilityLoading] = useState(false)
  const [traceabilityError, setTraceabilityError] = useState<string | null>(null)

  // Evidence Details Drawer state
  const [detailEvidence, setDetailEvidence] = useState<InvestigationEvidence | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)

  const load = async () => {
    setLoading(true)
    setError(null)
    try {
      const findingData = await listInvestigationFindings(investigation.id)
      setFindings(findingData)
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, 'Unable to load findings'))
    } finally {
      setLoading(false)
    }
    setEligibleLoading(true)
    setEligibleError(null)
    try {
      const evidencePage = await listInvestigationEvidence(investigation.id, 0, 100)
      setEligible(
        (evidencePage.content || []).filter(
          item =>
            item.reviewStatus === 'REVIEWED' &&
            item.relevance === 'RELEVANT' &&
            (item.assessment === 'SUPPORTS_INVESTIGATION' || item.assessment === 'CONTRADICTS_INVESTIGATION')
        )
      )
    } catch (err: unknown) {
      setEligibleError(getApiErrorMessage(err, 'Unable to load reviewed evidence'))
    } finally {
      setEligibleLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [investigation.id])

  const visibleFindings = useMemo(() => {
    const filtered = findings.filter(finding => {
      const query = search.trim().toLowerCase()
      return (
        (!query ||
          finding.statement.toLowerCase().includes(query) ||
          (finding.category || '').toLowerCase().includes(query) ||
          (finding.reasoning || '').toLowerCase().includes(query)) &&
        (!statusFilter || finding.status === statusFilter) &&
        (!categoryFilter || finding.category === categoryFilter) &&
        (!confidenceFilter || finding.confidence === confidenceFilter)
      )
    })
    return [...filtered].sort((left, right) =>
      sort === 'statement'
        ? left.statement.localeCompare(right.statement)
        : sort === 'confidence'
        ? (right.confidence || '').localeCompare(left.confidence || '')
        : new Date(right.updatedAt || 0).getTime() - new Date(left.updatedAt || 0).getTime()
    )
  }, [findings, search, statusFilter, categoryFilter, confidenceFilter, sort])

  const linkedCount = findings.reduce((total, finding) => total + finding.evidence.length, 0)
  const openCount = findings.filter(finding => finding.status === 'OPEN').length
  const closedCount = findings.filter(finding => finding.status === 'CONFIRMED' || finding.status === 'DISMISSED').length
  const maxConfidence = findings.some(finding => finding.confidence === 'HIGH')
    ? 'HIGH'
    : findings.some(finding => finding.confidence === 'MEDIUM')
    ? 'MEDIUM'
    : findings.length
    ? 'LOW'
    : '—'

  const openCreate = () => {
    setEditing(null)
    setForm({ ...EMPTY_FORM, evidence: [] })
    setOpen(true)
  }

  const openEdit = (finding: InvestigationFinding) => {
    setEditing(finding)
    setForm({
      statement: finding.statement,
      category: finding.category,
      confidence: finding.confidence,
      status: finding.status,
      reasoning: finding.reasoning,
      evidence: finding.evidence.map(item => ({ stableId: item.stableId, relationshipType: item.relationshipType })),
    })
    setTraceability(null)
    setTraceabilityError(null)
    setOpen(true)
    setTraceabilityLoading(true)
    getFindingEvidenceTraceability(investigation.id, finding.id)
      .then(setTraceability)
      .catch(err => setTraceabilityError(getApiErrorMessage(err, 'Unable to load evidence traceability')))
      .finally(() => setTraceabilityLoading(false))
  }

  const toggleEvidence = (item: InvestigationEvidence) => {
    const relationshipType = item.assessment === 'CONTRADICTS_INVESTIGATION' ? 'CONTRADICTING' : 'SUPPORTING'
    setForm(current => ({
      ...current,
      evidence: current.evidence.some(value => value.stableId === item.stableId)
        ? current.evidence.filter(value => value.stableId !== item.stableId)
        : [...current.evidence, { stableId: item.stableId, relationshipType }],
    }))
  }

  const openEvidenceDetail = async (item: InvestigationEvidence) => {
    setDetailEvidence(item)
    setDetailLoading(true)
    setDetailError(null)
    try {
      const loaded = await getInvestigationEvidenceDetail(investigation.id, item.stableId)
      setDetailEvidence(loaded)
    } catch (err: unknown) {
      setDetailError(getApiErrorMessage(err, 'Unable to load evidence details'))
    } finally {
      setDetailLoading(false)
    }
  }

  const openEvidenceDetailByStableId = async (stableId: string) => {
    setDetailEvidence({ stableId, title: stableId } as InvestigationEvidence)
    setDetailLoading(true)
    setDetailError(null)
    try {
      const loaded = await getInvestigationEvidenceDetail(investigation.id, stableId)
      setDetailEvidence(loaded)
    } catch (err: unknown) {
      setDetailError(getApiErrorMessage(err, 'Unable to load evidence details'))
    } finally {
      setDetailLoading(false)
    }
  }

  const closeEvidenceDetail = () => {
    setDetailEvidence(null)
    setDetailError(null)
  }

  const findingRel = useMemo(() => {
    if (!detailEvidence) return undefined
    const link = form.evidence.find(val => val.stableId === detailEvidence.stableId)
    return {
      isLinked: !!link,
      relationshipType: (link?.relationshipType ||
        (detailEvidence.assessment === 'CONTRADICTS_INVESTIGATION' ? 'CONTRADICTING' : 'SUPPORTING')) as 'SUPPORTING' | 'CONTRADICTING',
      onToggleLink: () => toggleEvidence(detailEvidence),
      onSetRelationship: (type: 'SUPPORTING' | 'CONTRADICTING') => {
        setForm(curr => ({
          ...curr,
          evidence: curr.evidence.some(val => val.stableId === detailEvidence.stableId)
            ? curr.evidence.map(val => (val.stableId === detailEvidence.stableId ? { ...val, relationshipType: type } : val))
            : [...curr.evidence, { stableId: detailEvidence.stableId, relationshipType: type }],
        }))
      },
    }
  }, [detailEvidence, form.evidence])

  const save = async () => {
    setSaving(true)
    setError(null)
    try {
      editing ? await updateInvestigationFinding(investigation.id, editing.id, form) : await createInvestigationFinding(investigation.id, form)
      await load()
      setOpen(false)
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, 'Unable to save finding'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div data-findings style={{ display: 'flex', flexDirection: 'column', gap: 14, color: TEXT }}>
      {editing && (
        <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 16 }}>
          <h3 style={{ marginTop: 0 }}>Evidence Traceability</h3>
          {traceabilityLoading ? (
            <p style={{ color: MUTED }}>Loading persisted discovery paths…</p>
          ) : traceabilityError ? (
            <div role="alert" style={{ color: '#FECACA' }}>{traceabilityError}</div>
          ) : traceability ? (
            <TraceabilitySection traceability={traceability} onOpenEvidence={openEvidenceDetailByStableId} />
          ) : (
            <p style={{ color: MUTED }}>No persisted traceability is available.</p>
          )}
        </section>
      )}

      <style>{`
        [data-findings] aside label {
          display: flex;
          flex-direction: column;
          gap: 6px;
          margin-top: 14px;
          color: ${MUTED};
          font-size: 12px;
          font-weight: 600;
        }
        [data-findings] aside textarea,
        [data-findings] aside select {
          box-sizing: border-box;
          width: 100%;
          background: ${PANEL};
          color: ${TEXT};
          border: 1px solid ${BORDER};
          border-radius: 7px;
          padding: 9px;
          font: inherit;
        }
        [data-findings] aside textarea { resize: vertical; }
        [data-findings] aside section { margin-top: 20px; }
      `}</style>

      <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 18 }}>
        <div style={{ color: '#60A5FA', fontSize: 10, fontWeight: 800, textTransform: 'uppercase', letterSpacing: 0.5 }}>
          Investigator Findings
        </div>
        <h2 style={{ margin: '5px 0', fontSize: 20 }}>Structured conclusions from reviewed evidence</h2>
        <p style={{ color: MUTED, fontSize: 12, margin: 0 }}>Document and manage findings based on reviewed evidence.</p>
        <button
          onClick={openCreate}
          style={{ marginTop: 14, background: PRIMARY, color: '#fff', border: 0, borderRadius: 7, padding: '9px 14px', fontWeight: 700, cursor: 'pointer' }}
        >
          + Add Finding
        </button>
      </section>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <StatCard label="Findings" value={findings.length} sub="This investigation" accent="#6366F1" />
        <StatCard label="Evidence Linked" value={linkedCount} sub="Traceable records" accent="#22C55E" />
        <StatCard label="Open" value={openCount} sub="Needs follow-up" accent="#F59E0B" />
        <StatCard label="Closed" value={closedCount} sub="Confirmed or dismissed" accent="#3B82F6" />
        <StatCard label="Max Confidence" value={maxConfidence} sub="Across findings" accent="#EF4444" />
      </div>

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <input
          value={search}
          onChange={event => setSearch(event.target.value)}
          placeholder="Search findings..."
          style={{ flex: '1 1 220px', background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: 9 }}
        />
        <select value={statusFilter} onChange={event => setStatusFilter(event.target.value)} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: 9 }}>
          <option value="">Status: All</option>
          <option value="OPEN">Open</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="DISMISSED">Dismissed</option>
        </select>
        <select value={categoryFilter} onChange={event => setCategoryFilter(event.target.value)} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: 9 }}>
          <option value="">Category: All</option>
          {['QUALITY', 'PROCESS', 'EQUIPMENT', 'MATERIAL', 'SUPPLIER', 'OTHER'].map(value => (
            <option key={value} value={value}>{value}</option>
          ))}
        </select>
        <select value={confidenceFilter} onChange={event => setConfidenceFilter(event.target.value)} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: 9 }}>
          <option value="">Confidence: All</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
        <select value={sort} onChange={event => setSort(event.target.value)} style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: 9 }}>
          <option value="updated">Updated newest</option>
          <option value="statement">Statement A-Z</option>
          <option value="confidence">Confidence</option>
        </select>
      </div>

      {error && <div role="alert" style={{ background: '#451A1A', color: '#FECACA', padding: 12, borderRadius: 7 }}>{error}</div>}

      {loading ? (
        <div role="status" style={{ background: CARD, padding: 28, textAlign: 'center', color: MUTED }}>
          Loading findings…
        </div>
      ) : visibleFindings.length === 0 ? (
        <div style={{ background: CARD, padding: 32, textAlign: 'center', color: MUTED }}>
          No findings have been created for this investigation yet.
        </div>
      ) : (
        <section style={{ overflowX: 'auto', background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10 }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 820 }}>
            <thead>
              <tr style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', textAlign: 'left' }}>
                {['#', 'Finding Statement', 'Category', 'Evidence', 'Confidence', 'Status', 'Updated'].map(label => (
                  <th key={label} style={{ padding: '11px 12px', borderBottom: `1px solid ${BORDER}` }}>{label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {visibleFindings.map((finding, index) => (
                <tr key={finding.id} onClick={() => openEdit(finding)} style={{ cursor: 'pointer', borderBottom: `1px solid ${BORDER}` }}>
                  <td style={{ padding: 12, color: MUTED }}>{index + 1}</td>
                  <td style={{ padding: 12, color: TEXT, fontWeight: 650, maxWidth: 330 }}>{finding.statement}</td>
                  <td style={{ padding: 12 }}><Badge value={finding.category || 'OTHER'} /></td>
                  <td style={{ padding: 12, color: '#93C5FD' }}>{finding.evidence.length}</td>
                  <td style={{ padding: 12 }}><Badge value={finding.confidence || '—'} /></td>
                  <td style={{ padding: 12 }}><StatusBadge value={finding.status} /></td>
                  <td style={{ padding: 12, color: MUTED, fontSize: 11 }}>{finding.updatedAt ? new Date(finding.updatedAt).toLocaleDateString() : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ padding: 11, color: MUTED, fontSize: 11 }}>
            Showing {visibleFindings.length} of {findings.length} findings
          </div>
        </section>
      )}

      {/* Finding Editor Drawer */}
      {open && (
        <div
          role="dialog"
          aria-label="Finding editor"
          onClick={() => !saving && setOpen(false)}
          style={{ position: 'fixed', inset: 0, background: 'rgba(2,6,23,.72)', display: 'flex', justifyContent: 'flex-end', zIndex: 50 }}
        >
          <aside
            onClick={event => event.stopPropagation()}
            style={{ width: 'min(620px, 100vw)', height: '100%', overflowY: 'auto', background: CARD, padding: 26, boxSizing: 'border-box' }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${BORDER}`, paddingBottom: 14 }}>
              <div>
                <div style={{ color: '#60A5FA', fontSize: 10, fontWeight: 800, textTransform: 'uppercase' }}>Finding Details</div>
                <h2 style={{ margin: '5px 0 0' }}>{editing ? 'Edit Finding' : 'Create New Finding'}</h2>
              </div>
              <button onClick={() => setOpen(false)} aria-label="Close">×</button>
            </div>

            <section>
              <h3>1. Finding Details</h3>
              <label>
                Finding statement
                <textarea value={form.statement} onChange={event => setForm({ ...form, statement: event.target.value })} rows={4} maxLength={2000} />
              </label>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <label>
                  Category
                  <select value={form.category || ''} onChange={event => setForm({ ...form, category: event.target.value })}>
                    <option value="QUALITY">Quality</option>
                    <option value="PROCESS">Process</option>
                    <option value="EQUIPMENT">Equipment</option>
                    <option value="MATERIAL">Material</option>
                    <option value="SUPPLIER">Supplier</option>
                    <option value="OTHER">Other</option>
                  </select>
                </label>
                <label>
                  Confidence
                  <select value={form.confidence || ''} onChange={event => setForm({ ...form, confidence: event.target.value })}>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </select>
                </label>
              </div>
              <label>
                Status
                <select value={form.status || 'OPEN'} onChange={event => setForm({ ...form, status: event.target.value as InvestigationFindingRequest['status'] })}>
                  <option value="OPEN">Open</option>
                  <option value="CONFIRMED">Confirmed</option>
                  <option value="DISMISSED">Dismissed</option>
                </select>
              </label>
            </section>

            <section>
              <h3>2. Link Evidence</h3>
              <p style={{ color: MUTED, fontSize: 12 }}>
                Select reviewed evidence that supports or contradicts this finding. Click an evidence item to inspect its complete details.
              </p>
              {eligibleLoading ? (
                <p style={{ color: MUTED, fontSize: 12 }}>Loading reviewed evidence…</p>
              ) : eligibleError ? (
                <div style={{ color: '#FCA5A5', fontSize: 12 }}>Unable to load reviewed evidence. Close and reopen to retry.</div>
              ) : eligible.length === 0 ? (
                <p style={{ color: MUTED, fontSize: 12 }}>No eligible reviewed evidence. Complete Evidence Review first.</p>
              ) : (
                eligible.map(item => {
                  const link = form.evidence.find(value => value.stableId === item.stableId)
                  return (
                    <div
                      key={item.stableId}
                      onClick={() => openEvidenceDetail(item)}
                      style={{
                        display: 'flex',
                        gap: 10,
                        alignItems: 'center',
                        background: PANEL,
                        border: `1px solid ${link ? (link.relationshipType === 'CONTRADICTING' ? '#7F1D1D' : '#14532D') : BORDER}`,
                        borderRadius: 7,
                        padding: '10px 12px',
                        marginBottom: 7,
                        cursor: 'pointer',
                        transition: 'border-color 0.15s ease',
                      }}
                      onMouseEnter={e => {
                        if (!link) e.currentTarget.style.borderColor = '#3B82F6'
                      }}
                      onMouseLeave={e => {
                        if (!link) e.currentTarget.style.borderColor = BORDER
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={!!link}
                        onClick={event => event.stopPropagation()}
                        onChange={() => toggleEvidence(item)}
                        style={{ cursor: 'pointer', width: 16, height: 16 }}
                      />
                      <span style={{ flex: 1, fontSize: 12 }}>
                        <strong style={{ color: TEXT }}>{item.sourceRecordId || item.stableId}</strong>
                        <small style={{ display: 'block', color: MUTED, marginTop: 2 }}>
                          {item.sourceType} · {item.title}
                        </small>
                      </span>
                      {link ? (
                        <span
                          style={{
                            background: link.relationshipType === 'CONTRADICTING' ? '#7F1D1D' : '#14532D',
                            color: link.relationshipType === 'CONTRADICTING' ? '#FEE2E2' : '#DCFCE7',
                            borderRadius: 999,
                            padding: '3px 8px',
                            fontSize: 10,
                            fontWeight: 800,
                          }}
                        >
                          {link.relationshipType === 'CONTRADICTING' ? 'Contradicts' : 'Supports'}
                        </span>
                      ) : (
                        <span style={{ color: '#60A5FA', fontSize: 11, fontWeight: 600 }}>
                          Details →
                        </span>
                      )}
                    </div>
                  )
                })
              )}
            </section>

            <section>
              <h3>3. Investigator Reasoning</h3>
              <label>
                Reasoning / Notes
                <textarea value={form.reasoning || ''} onChange={event => setForm({ ...form, reasoning: event.target.value })} rows={6} maxLength={5000} />
              </label>
            </section>

            <div style={{ display: 'flex', gap: 8, marginTop: 22 }}>
              <button
                onClick={save}
                disabled={saving || !form.statement.trim()}
                style={{ background: PRIMARY, color: '#fff', border: 0, borderRadius: 7, padding: '10px 16px', fontWeight: 700, cursor: saving || !form.statement.trim() ? 'not-allowed' : 'pointer' }}
              >
                {saving ? 'Saving…' : 'Save Finding'}
              </button>
              <button
                onClick={() => setOpen(false)}
                disabled={saving}
                style={{ background: PANEL, color: TEXT, border: `1px solid ${BORDER}`, borderRadius: 7, padding: '10px 16px', cursor: 'pointer' }}
              >
                Cancel
              </button>
            </div>
          </aside>
        </div>
      )}

      {/* Reusable Evidence Details Drawer */}
      <EvidenceDetailDrawer
        investigation={investigation}
        evidence={detailEvidence}
        loading={detailLoading}
        error={detailError}
        onClose={closeEvidenceDetail}
        findingRelationship={findingRel}
      />
    </div>
  )
}

function TraceabilitySection({
  traceability,
  onOpenEvidence,
}: {
  traceability: FindingEvidenceTraceability
  onOpenEvidence?: (stableId: string) => void
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {traceability.evidence.map(item => (
        <details key={item.evidenceId} open style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 10 }}>
          <summary style={{ cursor: 'pointer', fontWeight: 700 }}>
            {item.evidenceId}{' '}
            <span style={{ color: MUTED, fontWeight: 400 }}>
              · {item.sourceSystem || 'Unknown source'} · {item.sourceRecordId || 'No source record'}
            </span>
            {onOpenEvidence && (
              <span
                onClick={event => {
                  event.preventDefault()
                  event.stopPropagation()
                  onOpenEvidence(item.evidenceId)
                }}
                style={{ color: '#93C5FD', textDecoration: 'underline', cursor: 'pointer', marginLeft: 8, fontSize: 11 }}
              >
                View Evidence Details →
              </span>
            )}
          </summary>
          <div style={{ marginTop: 10, color: MUTED, fontSize: 12 }}>
            {item.discoveryPaths.length === 0 ? (
              <p>No persisted discovery path is available for this evidence.</p>
            ) : (
              item.discoveryPaths.map((path, index) => (
                <div key={`${item.evidenceId}-${index}`} style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 8, marginTop: 8 }}>
                  <div style={{ color: path.classification === 'PRIMARY' ? '#86EFAC' : '#FCD34D', fontWeight: 700 }}>
                    {path.classification === 'PRIMARY' ? 'Primary evidence path' : 'Cross-batch context'}
                  </div>
                  <div style={{ marginTop: 5, color: TEXT }}>{path.path.length ? path.path.join(' → ') : 'Persisted path is empty'}</div>
                  <div style={{ marginTop: 4 }}>Reason: {path.reason || 'Not recorded'}</div>
                </div>
              ))
            )}
            {item.sourceRecord && (
              <details style={{ marginTop: 10 }}>
                <summary style={{ cursor: 'pointer', color: '#93C5FD' }}>View original source record</summary>
                <pre style={{ whiteSpace: 'pre-wrap', overflowX: 'auto', background: '#020617', padding: 10, borderRadius: 6, color: '#CBD5E1' }}>
                  {item.sourceRecord.payload || 'No source payload available.'}
                </pre>
                {item.sourceRecord.sourceFile && <div>Source file: {item.sourceRecord.sourceFile.originalName || 'Unnamed file'}</div>}
              </details>
            )}
          </div>
        </details>
      ))}
    </div>
  )
}

function Badge({ value }: { value: string }) {
  return <span style={{ background: '#1E3A5F', color: '#BFDBFE', borderRadius: 5, padding: '3px 7px', fontSize: 10, fontWeight: 750 }}>{value}</span>
}
