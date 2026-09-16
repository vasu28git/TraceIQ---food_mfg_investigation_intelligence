import { useEffect, useMemo, useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PERMISSIONS } from '../../utils/permissions'
import { getApiErrorMessage } from '../../api/client'
import { listUsers, createUser, updateUser, updateUserStatus, deleteUser } from '../../services/userService'
import { listRolesByOrg } from '../../services/roleService'
import type { UserResponse } from '../../types/user'
import type { RoleSummary } from '../../types/user'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

const STATUS_OPTS = ['ACTIVE', 'INACTIVE', 'DISABLED', 'SUSPENDED'] as const

function formatDate(d?: string) {
  if (!d) return '-'
  try {
    return new Date(d).toLocaleString()
  } catch { return d }
}

function StatusBadge({ status }: { status?: string }) {
  const s = (status || 'UNKNOWN').toUpperCase()
  const map: Record<string, { bg: string; fg: string }> = {
    ACTIVE: { bg: '#dcfce7', fg: '#166534' },
    INACTIVE: { bg: '#fee2e2', fg: '#991b1b' },
    DISABLED: { bg: '#fee2e2', fg: '#7f1d1d' },
    SUSPENDED: { bg: '#fef3c7', fg: '#92400e' },
  }
  const c = map[s] || { bg: '#e2e8f0', fg: '#475569' }
  return <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{s}</span>
}

export function UsersPage() {
  const orgId = useAuthStore((s) => s.organisationId)
  const hasAnyAuthority = useAuthStore((s) => s.hasAnyAuthority)
  const canCreate = hasAnyAuthority(...PERMISSIONS.USER_CREATE)
  const canUpdate = hasAnyAuthority(...PERMISSIONS.USER_UPDATE)
  const canDelete = hasAnyAuthority(...PERMISSIONS.USER_DELETE)
  const canRead = hasAnyAuthority(...PERMISSIONS.USER_READ)

  const [users, setUsers] = useState<UserResponse[]>([])
  const [roles, setRoles] = useState<RoleSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingRoles, setLoadingRoles] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  // form state
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [editingUser, setEditingUser] = useState<UserResponse | null>(null)
  const [form, setForm] = useState<{ username: string; password: string; roleId: string; status: string }>({ username: '', password: '', roleId: '', status: 'ACTIVE' })
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // delete confirm
  const [deleteTarget, setDeleteTarget] = useState<UserResponse | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  // status change inline
  const [statusUpdatingId, setStatusUpdatingId] = useState<number | null>(null)

  const loadUsers = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await listUsers()
      setUsers(data)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 403) setError('Forbidden – missing USER_READ permission.')
      else setError(getApiErrorMessage(err, 'Failed to load users'))
    } finally { setLoading(false) }
  }

  const loadRoles = async () => {
    if (!orgId) { setLoadingRoles(false); return }
    setLoadingRoles(true)
    try {
      const data = await listRolesByOrg(orgId)
      setRoles(Array.isArray(data) ? data : [])
    } catch (err: unknown) {
      // roles load failure should not block users list; show subtle error
      console.warn('Failed to load roles', err)
      setRoles([])
    } finally { setLoadingRoles(false) }
  }

  useEffect(() => { loadUsers(); loadRoles() }, [orgId])
  // keep roles fresh when not cached
  useEffect(() => {
    if (!orgId) return
    // also listen to focus to reload lightly (optional)
  }, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return users
    return users.filter(u => u.username.toLowerCase().includes(q) || (u.role?.name || '').toLowerCase().includes(q) || (u.status||'').toLowerCase().includes(q))
  }, [users, query])

  const openCreate = () => {
    if (!canCreate) return
    setEditingUser(null)
    setForm({ username: '', password: '', roleId: '', status: 'ACTIVE' })
    setFormError(null)
    setModalMode('create')
  }
  const openEdit = (u: UserResponse) => {
    if (!canUpdate) return
    setEditingUser(u)
    setForm({ username: u.username, password: '', roleId: u.role ? String(u.role.id) : '', status: (u.status || 'ACTIVE').toUpperCase() })
    setFormError(null)
    setModalMode('edit')
  }
  const closeModal = () => { if (!submitting) { setModalMode(null); setEditingUser(null); setFormError(null) } }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setFormError(null)
    // validation
    if (!form.username.trim()) { setFormError('Username is required'); return }
    if (modalMode === 'create') {
      if (!form.password || form.password.length < 8) { setFormError('Password is required (min 8 characters)'); return }
    }
    if (form.roleId && isNaN(Number(form.roleId))) { setFormError('Invalid role'); return }
    if (!STATUS_OPTS.includes(form.status as typeof STATUS_OPTS[number])) { setFormError('Invalid status'); return }

    setSubmitting(true)
    try {
      if (modalMode === 'create') {
        await createUser({ username: form.username.trim(), password: form.password, roleId: form.roleId ? Number(form.roleId) : null, status: form.status })
      } else if (modalMode === 'edit' && editingUser) {
        await updateUser(editingUser.id, { username: form.username.trim(), roleId: form.roleId ? Number(form.roleId) : null, status: form.status })
      }
      setModalMode(null)
      setEditingUser(null)
      await loadUsers()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Save failed')
      if (status === 409) msg = msg.includes('already exists') ? msg : 'Username already exists (409)'
      if (status === 403) msg = 'Forbidden – you lack permission for this action (403)'
      if (status === 400) msg = msg
      setFormError(msg)
    } finally { setSubmitting(false) }
  }

  const handleStatusChange = async (u: UserResponse, newStatus: string) => {
    if (!canUpdate) return
    if (newStatus === u.status) return
    setStatusUpdatingId(u.id)
    try {
      const updated = await updateUserStatus(u.id, newStatus)
      setUsers(prev => prev.map(x => x.id === u.id ? updated : x))
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Status update failed')
      if (status === 403) msg = 'Forbidden (403)'
      alert(msg)
    } finally { setStatusUpdatingId(null) }
  }

  const handleDelete = async () => {
    if (!deleteTarget || !canDelete) return
    setDeleting(true); setDeleteError(null)
    try {
      await deleteUser(deleteTarget.id)
      setUsers(prev => prev.filter(x => x.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Delete failed')
      if (status === 403) msg = 'Forbidden – missing USER_DELETE (403)'
      if (status === 409) msg = msg
      if (status === 400) msg = msg // e.g. last admin, self-delete
      setDeleteError(msg)
    } finally { setDeleting(false) }
  }

  if (!canRead) {
    return <div style={{ padding: 24, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca' }}><h3 style={{ margin: 0 }}>Forbidden</h3><p style={{ fontSize: 14, marginTop: 8 }}>You do not have USER_READ permission. Backend remains authoritative.</p></div>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Users</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>{users.length} user(s) · Org #{orgId}</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => { loadUsers(); loadRoles() }} style={{ padding: '8px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, cursor: 'pointer' }}>Refresh</button>
          {canCreate && <button onClick={openCreate} style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>+ Create user</button>}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <input placeholder="Search username, role, status…" value={query} onChange={e => setQuery(e.target.value)} style={{ flex: 1, minWidth: 220, padding: '9px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN }} />
        <span style={{ fontSize: 12, color: MUTED, alignSelf: 'center' }}>{filtered.length} filtered</span>
      </div>

      {error && <ErrorAlert message={error} onRetry={loadUsers} />}
      {loading ? <LoadingSpinner label="Loading users…" /> : filtered.length === 0 ? (
        <div style={{ padding: 32, textAlign: 'center', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, color: TEXT_SEC }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>{users.length === 0 ? 'No users yet' : 'No matches'}</div>
          <div style={{ fontSize: 13, marginTop: 6 }}>{users.length === 0 ? 'Create your first user to get started.' : 'Try a different search term.'}</div>
          {canCreate && users.length === 0 && <button onClick={openCreate} style={{ marginTop: 12, padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer' }}>Create user</button>}
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 640 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#0F172A' }}>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC, fontWeight: 600 }}>Username</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Status</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Role</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Must change Pwd</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Created</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(u => (
                  <tr key={u.id} style={{ borderBottom: `1px solid ${BORDER}` }}>
                    <td style={{ padding: '10px 12px', color: TEXT_MAIN, fontWeight: 600 }}>{u.username}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <StatusBadge status={u.status} />
                    </td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC }}>{u.role?.name || <span style={{ color: MUTED }}>-</span>}</td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC }}>{u.mustChangePassword ? <span style={{ background: '#fef3c7', color: '#92400e', padding: '2px 6px', borderRadius: 999, fontSize: 11, fontWeight: 700 }}>YES</span> : <span style={{ color: MUTED, fontSize: 12 }}>No</span>}</td>
                    <td style={{ padding: '10px 12px', color: TEXT_SEC, fontSize: 12 }}>{formatDate(u.createdAt)}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                        {canUpdate && (
                          <>
                            <button onClick={() => openEdit(u)} style={{ padding: '4px 8px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>Edit</button>
                            <select
                              value={(u.status || 'ACTIVE').toUpperCase()}
                              onChange={e => handleStatusChange(u, e.target.value)}
                              disabled={statusUpdatingId === u.id}
                              style={{ padding: '4px 6px', background: '#0B1120', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 6, fontSize: 12 }}
                            >
                              {STATUS_OPTS.map(s => <option key={s} value={s}>{s}</option>)}
                            </select>
                          </>
                        )}
                        {canDelete && <button onClick={() => { setDeleteTarget(u); setDeleteError(null) }} style={{ padding: '4px 8px', background: 'transparent', border: '1px solid #7f1d1d', color: '#F87171', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>Delete</button>}
                        {!canUpdate && !canDelete && <span style={{ color: MUTED, fontSize: 11 }}>No actions</span>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create / Edit modal */}
      {modalMode && (
        <div onClick={closeModal} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 420, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>{modalMode === 'create' ? 'Create user' : `Edit user · ${editingUser?.username}`}</h3>
              <button onClick={closeModal} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleSubmit} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Username *</span>
                <input value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} placeholder="jdoe" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              {modalMode === 'create' && (
                <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Password * (min 8)</span>
                  <input type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="••••••••" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                </label>
              )}
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Role</span>
                <select value={form.roleId} onChange={e => setForm({ ...form, roleId: e.target.value })} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }}>
                  <option value="">-- No role --</option>
                  {loadingRoles ? <option>Loading roles…</option> : (Array.isArray(roles) ? roles : []).map(r => <option key={r.id} value={String(r.id)}>{r.name}{r.description ? ` – ${r.description}` : ''}</option>)}
                </select>
                {!loadingRoles && roles.length === 0 && <span style={{ fontSize: 11, color: '#DC2626' }}>No roles available in this organisation</span>}
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Status</span>
                <select value={form.status} onChange={e => setForm({ ...form, status: e.target.value })} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }}>
                  {STATUS_OPTS.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </label>
              <div style={{ fontSize: 11, color: MUTED }}>Organisation is derived from your session and never sent (backend forces current org).</div>
              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
                <button type="button" onClick={closeModal} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Saving…' : modalMode === 'create' ? 'Create' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirm */}
      {deleteTarget && (
        <div onClick={() => !deleting && setDeleteTarget(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 400, maxWidth: '100%', background: '#fff', borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, color: '#7f1d1d' }}>Delete user?</h3>
            <p style={{ fontSize: 13, color: '#475569', marginTop: 8 }}>Delete <strong style={{ color: '#0F172A' }}>{deleteTarget.username}</strong>? This cannot be undone. Backend may block if it is last active ADMIN or your own account (400).</p>
            {deleteError && <div style={{ marginTop: 8, background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{deleteError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setDeleteTarget(null)} disabled={deleting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
              <button onClick={handleDelete} disabled={deleting} style={{ padding: '8px 16px', background: deleting ? '#7f1d1d' : '#DC2626', color: '#fff', border: 0, borderRadius: 8, cursor: deleting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{deleting ? 'Deleting…' : 'Delete'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
