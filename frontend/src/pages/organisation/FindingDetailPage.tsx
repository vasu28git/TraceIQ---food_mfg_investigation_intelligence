import { useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { getApiErrorMessage } from '../../api/client'
import { getFindingEvidenceTraceability, getInvestigation, listInvestigationFindings } from '../../services/investigationService'
import type { FindingEvidenceTraceability, Investigation, InvestigationFinding } from '../../types/investigation'

const BG = '#0B1120'; const CARD = '#111827'; const PANEL = '#0F172A'; const BORDER = '#1E293B'; const TEXT = '#F8FAFC'; const MUTED = '#94A3B8'; const BLUE = '#60A5FA'

type Tab = 'overview' | 'evidence' | 'traceability'

export function FindingDetailPage() {
  const { investigationId, findingId } = useParams<{ investigationId: string; findingId: string }>()
  const [params, setParams] = useSearchParams()
  const id = Number(investigationId); const fid = Number(findingId)
  const tab = (params.get('tab') as Tab) || 'traceability'
  const [investigation, setInvestigation] = useState<Investigation | null>(null)
  const [finding, setFinding] = useState<InvestigationFinding | null>(null)
  const [trace, setTrace] = useState<FindingEvidenceTraceability | null>(null)
  const [loading, setLoading] = useState(true); const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!Number.isFinite(id) || !Number.isFinite(fid)) { setError('Invalid finding reference'); setLoading(false); return }
    Promise.all([getInvestigation(id), listInvestigationFindings(id), getFindingEvidenceTraceability(id, fid)])
      .then(([loadedInvestigation, findings, loadedTrace]) => { setInvestigation(loadedInvestigation); setFinding(findings.find(item => item.id === fid) || null); setTrace(loadedTrace) })
      .catch(err => setError(getApiErrorMessage(err, 'Unable to load finding traceability')))
      .finally(() => setLoading(false))
  }, [id, fid])

  const changeTab = (next: Tab) => setParams(previous => { const nextParams = new URLSearchParams(previous); nextParams.set('tab', next); return nextParams })
  if (loading) return <div role="status" style={{ background: CARD, color: MUTED, padding: 30, borderRadius: 10 }}>Loading finding…</div>
  if (error || !finding || !investigation) return <div role="alert" style={{ background: '#451A1A', color: '#FECACA', padding: 14, borderRadius: 8 }}>{error || 'Finding not found'}</div>
  const evidence = finding.evidence
  return <div style={{ minHeight: '100%', background: BG, color: TEXT, padding: 2 }}>
    <div style={{ display: 'flex', gap: 8, color: MUTED, fontSize: 12, marginBottom: 14 }}><Link to={`/organisation/investigations/${id}?tab=findings`} style={{ color: BLUE }}>Investigations</Link><span>›</span><span>{investigation.investigationKey || `INV-${id}`}</span><span>›</span><span>Findings</span><span>›</span><strong style={{ color: TEXT }}>Finding #{finding.id}</strong></div>
    <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 18, marginBottom: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'start' }}><div><h1 style={{ margin: 0, fontSize: 24 }}>Finding #{finding.id}</h1><div style={{ color: MUTED, fontSize: 12, marginTop: 8 }}>{investigation.investigationKey} · Batch: {investigation.batchReference || '—'} · Updated: {finding.updatedAt ? new Date(finding.updatedAt).toLocaleString() : '—'}</div></div><span style={{ background: '#14532D', color: '#BBF7D0', borderRadius: 999, padding: '5px 10px', fontSize: 11, fontWeight: 800 }}>{finding.status}</span></div><blockquote style={{ margin: '18px 0 0', padding: '14px 18px', borderLeft: '3px solid #60A5FA', background: PANEL, color: TEXT, fontWeight: 700 }}>{finding.statement}</blockquote></section>
    <div role="tablist" style={{ display: 'flex', gap: 8, borderBottom: `1px solid ${BORDER}`, paddingBottom: 8, marginBottom: 12 }}>{(['overview', 'evidence', 'traceability'] as Tab[]).map(item => <button key={item} role="tab" aria-selected={tab === item} onClick={() => changeTab(item)} style={{ border: `1px solid ${tab === item ? '#6366F1' : BORDER}`, background: tab === item ? '#1E1B4B' : PANEL, color: tab === item ? '#C7D2FE' : MUTED, borderRadius: 7, padding: '8px 14px', cursor: 'pointer', fontWeight: 700 }}>{item === 'traceability' ? 'Evidence Traceability' : item === 'evidence' ? 'Linked Evidence' : 'Overview'}</button>)}</div>
    {tab === 'overview' && <section style={panel}><h2>Finding Overview</h2><p style={{ color: MUTED }}>{finding.reasoning || 'No investigator reasoning recorded.'}</p><div style={grid}><Metric label="Category" value={finding.category || '—'} /><Metric label="Confidence" value={finding.confidence || '—'} /><Metric label="Linked Evidence" value={String(evidence.length)} /></div></section>}
    {tab === 'evidence' && <section style={panel}><h2>Linked Evidence ({evidence.length})</h2>{evidence.length === 0 ? <p style={{ color: MUTED }}>No evidence is linked to this finding.</p> : evidence.map(item => <div key={item.stableId} style={itemCard}><strong>{item.stableId}</strong><span style={{ color: MUTED }}>{item.sourceType || 'Unknown source'} · {item.title || 'Untitled evidence'} · {item.relationshipType}</span></div>)}</section>}
    {tab === 'traceability' && <TraceabilityPanel trace={trace} />}
  </div>
}

function TraceabilityPanel({ trace }: { trace: FindingEvidenceTraceability | null }) {
  if (!trace || trace.evidence.length === 0) return <section style={panel}><h2>Evidence Traceability</h2><p style={{ color: MUTED }}>No linked evidence traceability is available.</p></section>
  return <section style={panel}><div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><div><h2 style={{ margin: 0 }}>Evidence Traceability</h2><p style={{ color: MUTED, fontSize: 12 }}>Persisted discovery paths from the affected identifier to each original source record.</p></div><span style={{ color: BLUE, fontSize: 12 }}>{trace.evidence.length} evidence record{trace.evidence.length === 1 ? '' : 's'}</span></div>{trace.evidence.map((item, index) => <article key={item.evidenceId} style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 9, padding: 14, marginTop: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}><div><strong style={{ fontSize: 16 }}>{index + 1}. {item.evidenceId}</strong><div style={{ color: MUTED, fontSize: 12, marginTop: 4 }}>{item.sourceSystem || 'Unknown source'} · Source record: {item.sourceRecordId || 'Unavailable'}</div></div><span style={{ color: '#86EFAC', fontSize: 11, fontWeight: 800 }}>{item.findingRelationship || 'LINKED'}</span></div>{item.discoveryPaths.length === 0 ? <div style={{ color: '#FCD34D', marginTop: 14, fontSize: 12 }}>No persisted discovery path is available. No relationship was inferred.</div> : item.discoveryPaths.map((path, pathIndex) => <div key={`${item.evidenceId}-${pathIndex}`} style={{ borderTop: `1px solid ${BORDER}`, marginTop: 12, paddingTop: 12 }}><div style={{ display: 'flex', justifyContent: 'space-between', color: path.classification === 'PRIMARY' ? '#86EFAC' : '#FCD34D', fontSize: 11, fontWeight: 800 }}><span>{path.classification === 'PRIMARY' ? 'PRIMARY EVIDENCE' : 'CROSS-BATCH CONTEXT'}</span><span>{path.reason || 'Persisted provenance'}</span></div><div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8, marginTop: 10 }}>{path.path.map((step, stepIndex) => <span key={`${step}-${stepIndex}`} style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>{stepIndex > 0 && <span style={{ color: MUTED, fontSize: 18 }}>→</span>}<span style={{ background: '#172554', border: '1px solid #2563EB', borderRadius: 7, padding: '9px 12px', color: '#DBEAFE', fontSize: 12 }}>{step.replace(' Evidence:', ': ')}</span></span>)}</div></div>)}{item.sourceRecord && <details style={{ marginTop: 14 }}><summary style={{ cursor: 'pointer', color: BLUE, fontWeight: 700 }}>View Original Source Record</summary><div style={{ color: MUTED, fontSize: 12, marginTop: 10 }}>Record ID: {item.sourceRecord.sourceRecordId} · Ingested: {item.sourceRecord.ingestedAt ? new Date(item.sourceRecord.ingestedAt).toLocaleString() : '—'}{item.sourceRecord.sourceFile && ` · File: ${item.sourceRecord.sourceFile.originalName || 'Unnamed file'}`}</div><pre style={{ whiteSpace: 'pre-wrap', background: '#020617', padding: 12, borderRadius: 7, overflowX: 'auto', color: '#CBD5E1', fontSize: 11 }}>{item.sourceRecord.payload || 'No payload available.'}</pre></details>}</article>)}</section>
}

const panel = { background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 18 }
const grid = { display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 10 }
const itemCard = { display: 'flex', flexDirection: 'column', gap: 5, background: PANEL, border: `1px solid ${BORDER}`, padding: 12, borderRadius: 7, marginTop: 8 } as const
function Metric({ label, value }: { label: string; value: string }) { return <div style={{ background: PANEL, border: `1px solid ${BORDER}`, padding: 12, borderRadius: 7 }}><div style={{ color: MUTED, fontSize: 11 }}>{label}</div><strong>{value}</strong></div> }
