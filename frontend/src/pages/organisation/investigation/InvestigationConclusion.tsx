import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { createInvestigationConclusion, getInvestigationConclusion, listInvestigationFindings, updateInvestigationConclusion } from '../../../services/investigationService'
import type { Investigation, InvestigationConclusion as Conclusion, InvestigationConclusionRequest, InvestigationFinding } from '../../../types/investigation'

const CARD = '#111827'
const PANEL = '#0F172A'
const BORDER = '#1E293B'
const TEXT = '#F8FAFC'
const MUTED = '#94A3B8'
const PRIMARY = '#6366F1'
const EMPTY: InvestigationConclusionRequest = { lifecycle: 'DRAFT', outcome: 'INCONCLUSIVE', confidence: 'MEDIUM', summary: '', investigatorReasoning: '', supportingFindingIds: [], contradictingFindingIds: [] }

export function InvestigationConclusion({ investigation }: { investigation: Investigation }) {
  const [conclusion, setConclusion] = useState<Conclusion | null>(null)
  const [findings, setFindings] = useState<InvestigationFinding[]>([])
  const [form, setForm] = useState<InvestigationConclusionRequest>(EMPTY)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [openFinding, setOpenFinding] = useState<InvestigationFinding | null>(null)

  const load = async () => {
    setLoading(true); setError(null)
    try {
      const [saved, existingFindings] = await Promise.all([getInvestigationConclusion(investigation.id), listInvestigationFindings(investigation.id)])
      setConclusion(saved); setFindings(existingFindings)
      if (saved) setForm({ lifecycle: saved.lifecycle, outcome: saved.outcome, confidence: saved.confidence, summary: saved.summary, investigatorReasoning: saved.investigatorReasoning || '', supportingFindingIds: saved.supportingFindings.map(item => item.id), contradictingFindingIds: saved.contradictingFindings.map(item => item.id) })
    } catch (err: unknown) { setError(getApiErrorMessage(err, 'Unable to load conclusion')) } finally { setLoading(false) }
  }
  useEffect(() => { load() }, [investigation.id])

  const beginCreate = () => { setForm(EMPTY); setEditing(true); setSuccess(null) }
  const beginEdit = () => { if (conclusion?.lifecycle !== 'FINAL') setEditing(true) }
  const toggle = (field: 'supportingFindingIds' | 'contradictingFindingIds', id: number) => setForm(current => {
    const selected = current[field].includes(id)
    const next = selected ? current[field].filter(value => value !== id) : [...current[field], id]
    const opposite = field === 'supportingFindingIds' ? 'contradictingFindingIds' : 'supportingFindingIds'
    return { ...current, [field]: next, [opposite]: selected ? current[opposite] : current[opposite].filter(value => value !== id) }
  })
  const save = async (lifecycle: 'DRAFT' | 'FINAL') => {
    if (lifecycle === 'FINAL' && !window.confirm('Are you sure you want to finalize this conclusion?')) return
    setSaving(true); setError(null); setSuccess(null)
    const payload = { ...form, lifecycle }
    try {
      const saved = conclusion ? await updateInvestigationConclusion(investigation.id, payload) : await createInvestigationConclusion(investigation.id, payload)
      setConclusion(saved); setForm({ lifecycle: saved.lifecycle, outcome: saved.outcome, confidence: saved.confidence, summary: saved.summary, investigatorReasoning: saved.investigatorReasoning || '', supportingFindingIds: saved.supportingFindings.map(item => item.id), contradictingFindingIds: saved.contradictingFindings.map(item => item.id) }); setEditing(false); setSuccess(lifecycle === 'FINAL' ? 'Investigation conclusion finalized.' : 'Conclusion saved')
    } catch (err: unknown) { setError(getApiErrorMessage(err, 'Unable to save conclusion')) } finally { setSaving(false) }
  }

  if (loading) return <div role="status" style={{ background: CARD, padding: 28, color: MUTED, textAlign: 'center' }}>Loading conclusion…</div>
  const linkedIds = new Set([...form.supportingFindingIds, ...form.contradictingFindingIds])
  return <div style={{ display: 'flex', flexDirection: 'column', gap: 14, color: TEXT }}>
    <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 18 }}>
      <div style={{ color: '#60A5FA', fontSize: 10, fontWeight: 800, textTransform: 'uppercase' }}>Investigator Conclusion / Disposition</div>
      <h2 style={{ margin: '6px 0' }}>Overall investigation determination</h2>
      <p style={{ color: MUTED, fontSize: 12, margin: 0 }}>{investigation.title} · Status: {investigation.status} · Findings: {findings.length}</p>
    </section>
    {success && <div role="status" style={{ background: '#14532D', color: '#DCFCE7', borderRadius: 8, padding: 12 }}>{success}</div>}
    {error && <div role="alert" style={{ background: '#451A1A', color: '#FECACA', borderRadius: 8, padding: 12 }}>{error}</div>}
    {!conclusion && !editing && <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 30, textAlign: 'center' }}><h3>No investigation conclusion has been recorded yet.</h3><p style={{ color: MUTED }}>Review the investigation findings before recording the overall determination.</p><button onClick={beginCreate} style={{ background: PRIMARY, color: '#fff', border: 0, borderRadius: 7, padding: '10px 16px', fontWeight: 700 }}>Create Conclusion</button></section>}
    {conclusion && !editing && <ConclusionView conclusion={conclusion} onEdit={beginEdit} />}
    {editing && <ConclusionEditor form={form} findings={findings} linkedIds={linkedIds} saving={saving} onChange={setForm} onToggle={toggle} onSaveDraft={() => save('DRAFT')} onFinalize={() => save('FINAL')} onCancel={() => setEditing(false)} onOpenFinding={setOpenFinding} />}
    {openFinding && <div role="dialog" onClick={() => setOpenFinding(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(2,6,23,.72)', zIndex: 50, display: 'flex', justifyContent: 'flex-end' }}><aside onClick={event => event.stopPropagation()} style={{ width: 'min(560px, 100%)', height: '100%', overflowY: 'auto', background: CARD, padding: 24 }}><button onClick={() => setOpenFinding(null)} aria-label="Close finding">×</button><h2>{openFinding.statement}</h2><p style={{ color: MUTED }}>{openFinding.category || 'OTHER'} · {openFinding.confidence || 'No confidence'} · {openFinding.status}</p><p>{openFinding.reasoning || 'No reasoning recorded.'}</p><h3>Linked Evidence</h3>{openFinding.evidence.map(item => <div key={item.stableId} style={{ background: PANEL, border: `1px solid ${BORDER}`, padding: 10, marginTop: 8 }}>{item.stableId} · {item.title || 'Untitled evidence'}</div>)}</aside></div>}
  </div>
}

function ConclusionView({ conclusion, onEdit }: { conclusion: Conclusion; onEdit: () => void }) {
  const renderFindings = (title: string, items: Conclusion['supportingFindings']) => <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 16 }}><h3>{title} ({items.length})</h3>{items.length === 0 ? <p style={{ color: MUTED }}>None recorded.</p> : items.map(item => <div key={item.id} style={{ borderTop: `1px solid ${BORDER}`, padding: '12px 0' }}><strong>{item.statement}</strong><div style={{ color: MUTED, fontSize: 12, marginTop: 4 }}>{item.category || 'OTHER'} · {item.confidence || 'No confidence'} · {item.status} · {item.evidence.length} linked evidence</div>{item.evidence.map(evidence => <div key={evidence.stableId} style={{ color: '#93C5FD', fontSize: 12, marginTop: 5 }}>→ {evidence.stableId}: {evidence.title || 'Untitled evidence'}</div>)}</div>)}</section>
  return <><section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 18 }}><div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}><div><h2 style={{ margin: 0 }}>{conclusion.outcome.replace(/_/g, ' ')}</h2><p style={{ color: MUTED, fontSize: 12 }}>Lifecycle: {conclusion.lifecycle} · Confidence: {conclusion.confidence || 'Not recorded'}</p></div>{conclusion.lifecycle === 'DRAFT' && <button onClick={onEdit}>Edit Draft</button>}</div><h3>Conclusion</h3><p>{conclusion.summary}</p><h3>Investigator Reasoning</h3><p style={{ whiteSpace: 'pre-wrap' }}>{conclusion.investigatorReasoning || 'No reasoning recorded.'}</p><p style={{ color: MUTED, fontSize: 12 }}>Updated: {conclusion.updatedAt ? new Date(conclusion.updatedAt).toLocaleString() : 'Unknown'} · Finalized: {conclusion.finalizedAt ? new Date(conclusion.finalizedAt).toLocaleString() : 'Not finalized'}</p></section>{renderFindings('Supporting Findings', conclusion.supportingFindings)}{renderFindings('Contradicting Findings', conclusion.contradictingFindings)}</>
}

function ConclusionEditor({ form, findings, linkedIds, saving, onChange, onToggle, onSaveDraft, onFinalize, onCancel, onOpenFinding }: { form: InvestigationConclusionRequest; findings: InvestigationFinding[]; linkedIds: Set<number>; saving: boolean; onChange: (next: InvestigationConclusionRequest) => void; onToggle: (field: 'supportingFindingIds' | 'contradictingFindingIds', id: number) => void; onSaveDraft: () => void; onFinalize: () => void; onCancel: () => void; onOpenFinding: (finding: InvestigationFinding) => void }) {
  return <section style={{ background: CARD, border: `1px solid ${BORDER}`, borderRadius: 10, padding: 18 }}><h2>{form.lifecycle === 'FINAL' ? 'Finalized Conclusion' : 'Create / Edit Conclusion'}</h2><label style={label}>Final Outcome<select value={form.outcome} onChange={event => onChange({ ...form, outcome: event.target.value as InvestigationConclusionRequest['outcome'] })}>{['CONFIRMED', 'PARTIALLY_CONFIRMED', 'NOT_CONFIRMED', 'INCONCLUSIVE'].map(value => <option key={value} value={value}>{value.replace(/_/g, ' ')}</option>)}</select></label><label style={label}>Overall Confidence<select value={form.confidence || ''} onChange={event => onChange({ ...form, confidence: event.target.value as InvestigationConclusionRequest['confidence'] })}><option value="">Not recorded</option><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option></select></label><label style={label}>Conclusion Summary<textarea rows={4} value={form.summary} onChange={event => onChange({ ...form, summary: event.target.value })} /></label><label style={label}>Investigator Reasoning<textarea rows={6} value={form.investigatorReasoning || ''} onChange={event => onChange({ ...form, investigatorReasoning: event.target.value })} /></label><FindingPicker title="Supporting Findings" findings={findings} selected={form.supportingFindingIds} otherSelected={form.contradictingFindingIds} linkedIds={linkedIds} onToggle={id => onToggle('supportingFindingIds', id)} onOpenFinding={onOpenFinding} /><FindingPicker title="Contradicting Findings" findings={findings} selected={form.contradictingFindingIds} otherSelected={form.supportingFindingIds} linkedIds={linkedIds} onToggle={id => onToggle('contradictingFindingIds', id)} onOpenFinding={onOpenFinding} /><div style={{ display: 'flex', gap: 8, marginTop: 18 }}><button disabled={saving} onClick={onSaveDraft}>Save Draft</button><button disabled={saving} onClick={onFinalize} style={{ background: PRIMARY, color: '#fff', border: 0, borderRadius: 7, padding: '9px 13px', fontWeight: 700 }}>Finalize Conclusion</button><button disabled={saving} onClick={onCancel}>Cancel</button></div></section>
}

function FindingPicker({ title, findings, selected, otherSelected, linkedIds, onToggle, onOpenFinding }: { title: string; findings: InvestigationFinding[]; selected: number[]; otherSelected: number[]; linkedIds: Set<number>; onToggle: (id: number) => void; onOpenFinding: (finding: InvestigationFinding) => void }) {
  return <section><h3>{title}</h3>{findings.length === 0 ? <p style={{ color: MUTED }}>No findings have been recorded.</p> : findings.map(finding => { const isSelected = selected.includes(finding.id); const isOtherSelected = otherSelected.includes(finding.id); return <div key={finding.id} style={{ display: 'flex', alignItems: 'center', gap: 8, background: isSelected ? '#172554' : PANEL, border: `1px solid ${isSelected ? '#3B82F6' : BORDER}`, padding: 10, marginTop: 6 }}><input type="checkbox" checked={isSelected} disabled={isOtherSelected} aria-label={`${title}: ${finding.statement}`} onChange={() => onToggle(finding.id)} /><button type="button" onClick={() => onOpenFinding(finding)} style={{ background: 'transparent', color: TEXT, border: 0, textAlign: 'left', cursor: 'pointer', flex: 1 }}>{finding.statement}</button><span style={{ color: isSelected ? '#BFDBFE' : MUTED, fontSize: 11 }}>{isSelected ? 'Selected' : isOtherSelected ? 'Selected in other group' : `${finding.evidence.length} evidence`}</span></div> })}</section>
}

const label = { display: 'flex', flexDirection: 'column', gap: 6, marginTop: 14, color: MUTED, fontSize: 12, fontWeight: 600 } as const
