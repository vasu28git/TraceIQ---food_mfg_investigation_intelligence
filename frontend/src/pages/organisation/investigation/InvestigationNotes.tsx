import { useEffect, useState, useCallback } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { createNote, listNotes, updateNote, deleteNote } from '../../../services/investigationService'
import type { InvestigationNote } from '../../../types/investigation'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

function formatDate(d?: string | null) {
  if (!d) return '-'
  try { return new Date(d).toLocaleString() } catch { return d }
}

export function InvestigationNotes({ investigationId, investigationStatus }: { investigationId: number; investigationStatus?: string }) {
  const [notes, setNotes] = useState<InvestigationNote[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0, page: 0, size: 20 })

  // create
  const [showCreate, setShowCreate] = useState(false)
  const [createContent, setCreateContent] = useState('')
  const [createError, setCreateError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // edit
  const [editing, setEditing] = useState<InvestigationNote | null>(null)
  const [editContent, setEditContent] = useState('')
  const [editError, setEditError] = useState<string | null>(null)

  // delete confirm
  const [deletingId, setDeletingId] = useState<number | null>(null)

  const [actionFeedback, setActionFeedback] = useState<string | null>(null)

  const isMutable = (() => {
    const s = (investigationStatus || '').toUpperCase()
    return s === 'DRAFT' || s === 'ACTIVE'
  })()

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await listNotes(investigationId, 0, 50)
      // backend returns Page<InvestigationNoteResponse> with content/totalElements/totalPages/size/number
      const content = (res as unknown as { content: InvestigationNote[] }).content ?? (res as unknown as InvestigationNote[])
      const totalElements = (res as unknown as { totalElements: number }).totalElements ?? (Array.isArray(content) ? content.length : 0)
      const totalPages = (res as unknown as { totalPages: number }).totalPages ?? 0
      const size = (res as unknown as { size: number }).size ?? 50
      const number = (res as unknown as { number: number }).number ?? 0
      const list = Array.isArray(content) ? content : Array.isArray(res) ? (res as unknown as InvestigationNote[]) : []
      setNotes(list)
      setPageInfo({ totalElements, totalPages, page: number, size })
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      if (st === 403) setError('You don’t have access to notes (403).')
      else if (st === 404) setError('Investigation not found (404).')
      else if ((getApiErrorMessage(err, '').toLowerCase().includes('graph not ready'))) setError('Graph not ready – notes require canonical sync and projection (400).')
      else setError(getApiErrorMessage(err, 'Failed to load notes'))
    } finally {
      setLoading(false)
    }
  }, [investigationId])

  useEffect(() => { load() }, [load])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreateError(null)
    const trimmed = createContent.trim()
    if (!trimmed) { setCreateError('Content is required'); return }
    if (trimmed.length > 5000) { setCreateError('Content max 5000 characters'); return }
    setSubmitting(true)
    try {
      await createNote(investigationId, { content: trimmed })
      setShowCreate(false)
      setCreateContent('')
      setActionFeedback('Note added')
      setTimeout(() => setActionFeedback(null), 3000)
      await load()
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to create note')
      const lower = msg.toLowerCase()
      if (st === 409 || lower.includes('completed') || lower.includes('archived') || lower.includes('cannot be modified')) setCreateError('Investigation is completed/archived and cannot be modified (409).')
      else if (lower.includes('graph not ready')) setCreateError('Graph not ready – notes require canonical sync and projection (400).')
      else setCreateError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editing) return
    setEditError(null)
    const trimmed = editContent.trim()
    if (!trimmed) { setEditError('Content is required'); return }
    if (trimmed.length > 5000) { setEditError('Content max 5000 characters'); return }
    setSubmitting(true)
    try {
      await updateNote(investigationId, editing.id, { content: trimmed })
      setEditing(null)
      setEditContent('')
      setActionFeedback('Note updated')
      setTimeout(() => setActionFeedback(null), 3000)
      await load()
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to update note')
      const lower = msg.toLowerCase()
      if (st === 403 || lower.includes('only the original author')) setEditError('You can only edit your own notes (403).')
      else if (st === 404) setEditError('Note not found (404).')
      else if (st === 409 || lower.includes('completed') || lower.includes('archived')) setEditError('Investigation is completed/archived and cannot be modified (409).')
      else if (lower.includes('graph not ready')) setEditError('Graph not ready (400).')
      else setEditError(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (note: InvestigationNote) => {
    if (!confirm(`Delete this note?\n\n"${note.content.slice(0,120)}${note.content.length>120?'…':''}"`)) return
    setDeletingId(note.id)
    setError(null)
    try {
      await deleteNote(investigationId, note.id)
      setActionFeedback('Note deleted')
      setTimeout(() => setActionFeedback(null), 3000)
      await load()
    } catch (err: unknown) {
      const st = (err as { response?: { status?: number } })?.response?.status
      const msg = getApiErrorMessage(err, 'Failed to delete note')
      const lower = msg.toLowerCase()
      if (st === 403 || lower.includes('only the original author')) setError('You can only delete your own notes (403).')
      else if (st === 404) setError('Note not found (404).')
      else if (st === 409 || lower.includes('completed') || lower.includes('archived')) setError('Investigation is completed/archived and cannot be modified (409).')
      else if (lower.includes('graph not ready')) setError('Graph not ready (400).')
      else setError(msg)
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 700, color: TEXT_MAIN }}>Notes</div>
          <div style={{ fontSize: 11, color: TEXT_SEC, marginTop: 2 }}>
            Investigator working notes · {pageInfo.totalElements} notes · DRAFT/ACTIVE mutable only · not part of evidence timeline
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => load()} style={{ padding: '6px 10px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, cursor: 'pointer', fontSize: 12 }}>Refresh</button>
          {isMutable ? (
            <button onClick={() => { setCreateError(null); setShowCreate(true) }} style={{ padding: '6px 12px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontSize: 12, fontWeight: 700 }}>+ Add Note</button>
          ) : (
            <span style={{ fontSize: 11, color: MUTED, alignSelf:'center', padding:'4px 8px', border:`1px solid ${BORDER}`, borderRadius:999 }}>Add disabled — {investigationStatus || 'unknown'} cannot be modified</span>
          )}
        </div>
      </div>

      {actionFeedback && <div role="status" style={{ background:'#DCFCE7', border:'1px solid #BBF7D0', color:'#166534', padding:'8px 10px', borderRadius:8, fontSize:12 }}>{actionFeedback}</div>}

      {loading ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, color: TEXT_SEC, textAlign: 'center' }}>Loading notes…</div>
      ) : error ? (
        <div style={{ background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, padding: 12, color: '#fecaca', fontSize: 13 }}>{error} <button onClick={() => load()} style={{ marginLeft: 8, background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, padding: '4px 8px', cursor: 'pointer' }}>Retry</button></div>
      ) : notes.length === 0 ? (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 24, textAlign: 'center' }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No notes yet</div>
          <div style={{ fontSize: 13, color: TEXT_SEC, marginTop: 6 }}>Add investigator notes for this investigation. Notes are workspace material, not evidence, and do not create Timeline events. DRAFT/ACTIVE only, GRAPH_READY gated, max 5000 characters.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {notes.map((n) => (
            <div key={n.id} style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontSize: 13, color: TEXT_MAIN, whiteSpace: 'pre-wrap', wordBreak: 'break-word', lineHeight: 1.5 }}>{n.content}</div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap', borderTop: `1px solid ${BORDER}`, paddingTop: 8 }}>
                <div style={{ fontSize: 11, color: MUTED, display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                  <span>Author: <span style={{ color: TEXT_SEC, fontWeight: 600 }}>#{n.authorUserId ?? '-'}</span></span>
                  <span>·</span>
                  <span>Created {formatDate(n.createdAt)}</span>
                  {n.updatedAt && n.updatedAt !== n.createdAt && <><span>·</span><span>Updated {formatDate(n.updatedAt)}</span></>}
                </div>
                {isMutable && (
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button
                      onClick={() => { setEditing(n); setEditContent(n.content); setEditError(null) }}
                      style={{ padding: '4px 8px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 6, cursor: 'pointer', fontSize: 11 }}
                    >Edit</button>
                    <button
                      onClick={() => handleDelete(n)}
                      disabled={deletingId === n.id}
                      style={{ padding: '4px 8px', background: 'transparent', border: '1px solid #7f1d1d', color: '#F87171', borderRadius: 6, cursor: deletingId === n.id ? 'not-allowed' : 'pointer', fontSize: 11, opacity: deletingId === n.id ? 0.6 : 1 }}
                    >{deletingId === n.id ? 'Deleting…' : 'Delete'}</button>
                  </div>
                )}
                {!isMutable && <span style={{ fontSize: 11, color: MUTED, background:'#0B1120', border:`1px solid ${BORDER}`, borderRadius:999, padding:'2px 8px' }}>Read-only</span>}
              </div>
            </div>
          ))}
        </div>
      )}

      <div style={{ fontSize: 11, color: '#475569', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, padding: '8px 10px' }}>
        Backend: <code>GET/POST /api/investigations/{'{id}'}/notes</code> · <code>PATCH/DELETE /api/investigations/{'{id}'}/notes/{'{noteId}'}</code> · author-owned edit/delete (403 if not owner), investigation DRAFT/ACTIVE only (409), GRAPH_READY gated (400). Notes never affect evidence timeline.
      </div>

      {showCreate && (
        <div onClick={() => !submitting && setShowCreate(false)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={(e) => e.stopPropagation()} style={{ width: 560, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Add note</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleCreate} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Content *</span>
                <textarea
                  value={createContent}
                  onChange={(e) => setCreateContent(e.target.value)}
                  maxLength={5000}
                  rows={6}
                  placeholder="Write investigator note…"
                  style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8, resize: 'vertical', fontSize: 13 }}
                  required
                />
                <span style={{ fontSize: 11, color: createContent.length > 5000 ? '#DC2626' : MUTED, alignSelf: 'flex-end' }}>{createContent.length} / 5000</span>
              </label>
              {createError && <div role="alert" style={{ background:'#FEF2F2', border:'1px solid #FECACA', color:'#991B1B', padding:'8px 10px', borderRadius:8, fontSize:12 }}>{createError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" onClick={() => setShowCreate(false)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Saving…' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editing && (
        <div onClick={() => !submitting && setEditing(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={(e) => e.stopPropagation()} style={{ width: 560, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Edit note</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleEdit} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Content *</span>
                <textarea
                  value={editContent}
                  onChange={(e) => setEditContent(e.target.value)}
                  maxLength={5000}
                  rows={6}
                  style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8, resize: 'vertical', fontSize: 13 }}
                  required
                />
                <span style={{ fontSize: 11, color: editContent.length > 5000 ? '#DC2626' : MUTED, alignSelf: 'flex-end' }}>{editContent.length} / 5000</span>
              </label>
              {editError && <div role="alert" style={{ background:'#FEF2F2', border:'1px solid #FECACA', color:'#991B1B', padding:'8px 10px', borderRadius:8, fontSize:12 }}>{editError}</div>}
              <div style={{ fontSize: 11, color: MUTED, background:'#f8fafc', border:'1px solid #e2e8f0', borderRadius:8, padding:'6px 8px' }}>Only the original author may edit. Backend enforces ownership (403 if not owner).</div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button type="button" onClick={() => setEditing(null)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Saving…' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
