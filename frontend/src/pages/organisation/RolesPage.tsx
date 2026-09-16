import { useEffect, useMemo, useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PERMISSIONS } from '../../utils/permissions'
import { getApiErrorMessage } from '../../api/client'
import { getRolesByOrg, createRole, updateRole, deleteRole, getPermissionsForRole, assignPermissionsToRole, removePermissionsFromRole } from '../../services/roleService'
import { getAllPermissions } from '../../services/permissionService'
import type { Role, Permission } from '../../types/role'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

const BUSINESS_GROUPS: Record<string, string> = {
  INVESTIGATION_ACCESS: 'Investigation',
  INVESTIGATION_ADMIN: 'Investigation',
  EVIDENCE_ACCESS: 'Evidence',
  EVIDENCE_SOURCE_ACCESS: 'Evidence Sources',
  EVIDENCE_GRAPH_ACCESS: 'Evidence Graph',
  TRACEABILITY_ACCESS: 'Traceability',
  TIMELINE_ACCESS: 'Timeline',
  AI_INVESTIGATION_ACCESS: 'AI Investigation',
  WORKSPACE_ACCESS: 'Workspace',
  RECOMMENDATION_ACCESS: 'Recommendations',
  DECISION_ACCESS: 'Decisions',
  REPORT_ACCESS: 'Reports',
}

function groupPermissions(perms: Permission[]): Record<string, Permission[]> {
  if (!Array.isArray(perms)) return {}
  const groups: Record<string, Permission[]> = {}
  for (const p of perms) {
    const name = (p.name || 'UNKNOWN').toUpperCase()
    let key: string
    if (BUSINESS_GROUPS[name]) {
      key = BUSINESS_GROUPS[name]
    } else {
      const prefix = name.includes('_') ? name.split('_')[0] : 'OTHER'
      key = prefix.toUpperCase()
    }
    if (!groups[key]) groups[key] = []
    groups[key].push(p)
  }
  for (const k of Object.keys(groups)) groups[k].sort((a, b) => a.name.localeCompare(b.name))
  return groups
}

export function RolesPage() {
  const orgId = useAuthStore((s) => s.organisationId)
  const hasAny = useAuthStore((s) => s.hasAnyAuthority)
  const canRead = hasAny(...PERMISSIONS.ROLE_READ)
  const canCreate = hasAny(...PERMISSIONS.ROLE_CREATE)
  const canUpdate = hasAny(...PERMISSIONS.ROLE_UPDATE)
  const canDelete = hasAny(...PERMISSIONS.ROLE_DELETE)
  const canAssign = hasAny(...PERMISSIONS.PERMISSION_ASSIGN)
  const canReadPerms = hasAny(...PERMISSIONS.PERMISSION_READ)

  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  // create/edit
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [editing, setEditing] = useState<Role | null>(null)
  const [form, setForm] = useState({ name: '', description: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [createSelected, setCreateSelected] = useState<Set<number>>(new Set())
  const [createPermFilter, setCreatePermFilter] = useState('')

  // delete
  const [deleteTarget, setDeleteTarget] = useState<Role | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  // permissions assignment (manage)
  const [permRole, setPermRole] = useState<Role | null>(null)
  const [catalog, setCatalog] = useState<Permission[]>([])
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [assigned, setAssigned] = useState<Set<number>>(new Set())
  const [assignedInitial, setAssignedInitial] = useState<Set<number>>(new Set())
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [permSaving, setPermSaving] = useState(false)
  const [permError, setPermError] = useState<string | null>(null)
  const [permSuccess, setPermSuccess] = useState<string | null>(null)
  const [permFilter, setPermFilter] = useState('')

  const loadRoles = async () => {
    if (!orgId) return
    setLoading(true); setError(null)
    try {
      const data = await getRolesByOrg(orgId)
      setRoles(Array.isArray(data) ? data : [])
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 403) setError('Forbidden – missing ROLE_READ permission.')
      else setError(getApiErrorMessage(err, 'Failed to load roles'))
      setRoles([])
    } finally { setLoading(false) }
  }

  const loadCatalog = async () => {
    if (!canReadPerms) { setCatalog([]); return }
    setCatalogLoading(true); setCatalogError(null)
    try {
      const data = await getAllPermissions()
      setCatalog(Array.isArray(data) ? data : [])
    } catch (err: unknown) {
      setCatalogError(getApiErrorMessage(err, 'Failed to load permission catalog'))
      setCatalog([])
    } finally { setCatalogLoading(false) }
  }

  const openPerms = async (role: Role) => {
    setPermRole(role)
    setPermError(null); setPermSuccess(null); setPermFilter('')
    setCatalogLoading(true)
    try {
      const [all, assignedPerms] = await Promise.all([
        canReadPerms ? getAllPermissions().catch(() => [] as Permission[]) : Promise.resolve([] as Permission[]),
        getPermissionsForRole(role.id).catch(() => [] as Permission[]),
      ])
      setCatalog(Array.isArray(all) ? all : [])
      const safeAssigned = Array.isArray(assignedPerms) ? assignedPerms : []
      const ids = new Set(safeAssigned.map(p => p.id))
      setAssigned(ids); setAssignedInitial(new Set(ids)); setSelected(new Set(ids))
    } catch (err: unknown) {
      setPermError(getApiErrorMessage(err, 'Failed to load permissions'))
    } finally { setCatalogLoading(false) }
  }

  useEffect(() => { loadRoles() }, [orgId])
  useEffect(() => { loadCatalog() }, [])

  const filteredRoles = useMemo(() => {
    const safeRoles = Array.isArray(roles) ? roles : []
    const q = query.trim().toLowerCase()
    if (!q) return safeRoles
    return safeRoles.filter(r => r.name.toLowerCase().includes(q) || (r.description || '').toLowerCase().includes(q))
  }, [roles, query])

  const filteredCatalog = useMemo(() => {
    const safeCatalog = Array.isArray(catalog) ? catalog : []
    const q = permFilter.trim().toLowerCase()
    if (!q) return safeCatalog
    return safeCatalog.filter(p => p.name.toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q))
  }, [catalog, permFilter])

  const grouped = useMemo(() => groupPermissions(filteredCatalog), [filteredCatalog])

  const createFilteredCatalog = useMemo(() => {
    const safeCatalog = Array.isArray(catalog) ? catalog : []
    const q = createPermFilter.trim().toLowerCase()
    if (!q) return safeCatalog
    return safeCatalog.filter(p => p.name.toLowerCase().includes(q) || (p.description || '').toLowerCase().includes(q))
  }, [catalog, createPermFilter])

  const createGrouped = useMemo(() => groupPermissions(createFilteredCatalog), [createFilteredCatalog])

  // create/edit handlers
  const openCreate = () => {
    if (!canCreate) return
    setForm({ name: '', description: '' })
    setFormError(null)
    setEditing(null)
    setModalMode('create')
    setCreateSelected(new Set())
    setCreatePermFilter('')
    if (catalog.length === 0 && canReadPerms) loadCatalog()
  }
  const openEdit = (r: Role) => { if (!canUpdate) return; setEditing(r); setForm({ name: r.name, description: r.description || '' }); setFormError(null); setModalMode('edit') }
  const closeModal = () => { if (!submitting) { setModalMode(null); setEditing(null); setFormError(null) } }

  const handleRoleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!form.name.trim()) { setFormError('Role name is required'); return }
    setSubmitting(true); setFormError(null)
    try {
      if (modalMode === 'create') {
        const created = await createRole({ name: form.name.trim(), description: form.description.trim() || undefined })
        // Assign selected permissions if any (using existing POST /api/roles/{id}/permissions)
        if (createSelected.size > 0) {
          try {
            await assignPermissionsToRole(created.id, [...createSelected])
          } catch (assignErr: unknown) {
            const status = (assignErr as { response?: { status?: number } })?.response?.status
            let msg = getApiErrorMessage(assignErr, 'Role created but failed to assign permissions')
            if (status === 403) msg = 'Role created but failed to assign permissions: Forbidden – privilege escalation or missing PERMISSION_ASSIGN (403)'
            setFormError(msg)
            // Refresh to show role with whatever was persisted, do not pretend success
            await loadRoles()
            // Keep modal open so user sees error, allow retry via Manage perms
            return
          }
        }
        setCreateSelected(new Set())
        setCreatePermFilter('')
        setModalMode(null); setEditing(null)
        await loadRoles()
      } else if (modalMode === 'edit' && editing) {
        await updateRole(editing.id, { name: form.name.trim(), description: form.description.trim() || undefined })
        setModalMode(null); setEditing(null)
        await loadRoles()
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Save failed')
      if (status === 409) msg = msg.includes('already exists') ? msg : 'Role already exists (409)'
      if (status === 403) msg = 'Forbidden – missing permission (403)'
      setFormError(msg)
    } finally { setSubmitting(false) }
  }

  const handleDelete = async () => {
    if (!deleteTarget || !canDelete) return
    setDeleting(true); setDeleteError(null)
    try {
      await deleteRole(deleteTarget.id)
      setRoles(prev => prev.filter(r => r.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Delete failed')
      if (status === 403) msg = 'Cannot delete protected ADMIN role (403) – backend rejected.'
      if (status === 409) msg = msg // includes count: Cannot delete role assigned to users
      setDeleteError(msg)
    } finally { setDeleting(false) }
  }

  const handlePermSave = async () => {
    if (!permRole || !canAssign) return
    const added = [...selected].filter(id => !assignedInitial.has(id))
    const removed = [...assignedInitial].filter(id => !selected.has(id))
    if (added.length === 0 && removed.length === 0) { setPermSuccess('No changes'); return }
    setPermSaving(true); setPermError(null); setPermSuccess(null)
    try {
      if (added.length) await assignPermissionsToRole(permRole.id, added)
      if (removed.length) await removePermissionsFromRole(permRole.id, removed)
      const updated = await getPermissionsForRole(permRole.id)
      const newIds = new Set(updated.map(p => p.id))
      setAssigned(newIds); setAssignedInitial(new Set(newIds)); setSelected(new Set(newIds))
      // reflect in roles list (count)
      setRoles(prev => prev.map(r => r.id === permRole.id ? { ...r, permissions: updated as unknown as Permission[] } : r))
      setPermSuccess(`Updated: +${added.length} added, -${removed.length} removed`)
      setTimeout(() => setPermSuccess(null), 2000)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Permission update failed')
      if (status === 403) msg = 'Forbidden – you lack PERMISSION_ASSIGN (403) or privilege escalation.'
      setPermError(msg)
    } finally { setPermSaving(false) }
  }

  if (!canRead) {
    return <div style={{ padding: 24, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca' }}><h3 style={{ margin: 0 }}>Forbidden</h3><p style={{ fontSize: 14, marginTop: 8 }}>You do not have ROLE_READ permission.</p></div>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Roles & Permissions</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>{roles.length} role(s) · Org #{orgId}</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => { loadRoles(); loadCatalog() }} style={{ padding: '8px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, cursor: 'pointer' }}>Refresh</button>
          {canCreate && <button onClick={openCreate} style={{ padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>+ Create role</button>}
        </div>
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <input placeholder="Search role name or description…" value={query} onChange={e => setQuery(e.target.value)} style={{ flex: 1, minWidth: 220, padding: '9px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN }} />
        <span style={{ fontSize: 12, color: MUTED, alignSelf: 'center' }}>{filteredRoles.length} filtered</span>
      </div>

      {error && <ErrorAlert message={error} onRetry={loadRoles} />}
      {loading ? <LoadingSpinner label="Loading roles…" /> : filteredRoles.length === 0 ? (
        <div style={{ padding: 32, textAlign: 'center', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, color: TEXT_SEC }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>{roles.length === 0 ? 'No roles yet' : 'No matches'}</div>
          <div style={{ fontSize: 13, marginTop: 6 }}>{roles.length === 0 ? 'Create your first role. ADMIN is protected.' : 'Try a different search.'}</div>
          {canCreate && roles.length === 0 && <button onClick={openCreate} style={{ marginTop: 12, padding: '8px 14px', background: '#6366F1', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer' }}>Create role</button>}
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 720 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#0F172A' }}>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC, fontWeight: 600 }}>Role</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Description</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Permissions</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredRoles.map(r => {
                  const permCount = r.permissions?.length ?? 0
                  return (
                    <tr key={r.id} style={{ borderBottom: `1px solid ${BORDER}` }}>
                      <td style={{ padding: '10px 12px', color: TEXT_MAIN, fontWeight: 700 }}>
                        {r.name} {r.name.toUpperCase() === 'ADMIN' && <span style={{ fontSize: 10, background: '#fef3c7', color: '#92400e', padding: '1px 6px', borderRadius: 999, marginLeft: 6, fontWeight: 700 }}>PROTECTED</span>}
                      </td>
                      <td style={{ padding: '10px 12px', color: TEXT_SEC, maxWidth: 260, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.description || <span style={{ color: MUTED }}>-</span>}</td>
                      <td style={{ padding: '10px 12px', color: TEXT_SEC }}>
                        <span style={{ background: '#0B1120', border: `1px solid ${BORDER}`, padding: '2px 8px', borderRadius: 999, fontSize: 11 }}>{permCount} {permCount === 1 ? 'permission' : 'permissions'}</span>
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          {canUpdate && <button onClick={() => openEdit(r)} style={{ padding: '4px 8px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>Edit</button>}
                          {canReadPerms && <button onClick={() => openPerms(r)} style={{ padding: '4px 8px', background: canAssign ? '#1e293b' : 'transparent', border: `1px solid ${BORDER}`, color: canAssign ? '#38bdf8' : TEXT_SEC, borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>{canAssign ? 'Manage perms' : 'View perms'}</button>}
                          {canDelete && <button onClick={() => { setDeleteTarget(r); setDeleteError(null) }} style={{ padding: '4px 8px', background: 'transparent', border: '1px solid #7f1d1d', color: '#F87171', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>Delete</button>}
                          {!canUpdate && !canDelete && !canReadPerms && <span style={{ color: MUTED, fontSize: 11 }}>No actions</span>}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create / Edit */}
      {modalMode && (
        <div onClick={() => !submitting && setModalMode(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 560, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)', maxHeight: '90vh', display: 'flex', flexDirection: 'column' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>{modalMode === 'create' ? 'Create role' : `Edit role · ${editing?.name}`}</h3>
              <button onClick={() => setModalMode(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleRoleSubmit} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, overflowY: 'auto', flex: 1 }}>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Name *</span>
                <input value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="EDITOR" required style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              </label>
              <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Description</span>
                <textarea value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} placeholder="Can manage users and view configs" rows={2} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8, resize: 'vertical' }} />
              </label>
              <div style={{ fontSize: 11, color: MUTED }}>Organisation is forced from your session – never sent.</div>

              {modalMode === 'create' && (
                <div style={{ borderTop: '1px solid #e5e7eb', paddingTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>Permissions</span>
                    <span style={{ fontSize: 11, color: MUTED }}>{createSelected.size} selected · {catalog.length} total</span>
                  </div>
                  <input placeholder="Filter permissions…" value={createPermFilter} onChange={e => setCreatePermFilter(e.target.value)} style={{ padding: '8px 10px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 13 }} />
                  <div style={{ maxHeight: 280, overflowY: 'auto', border: '1px solid #e5e7eb', borderRadius: 8, background: '#f8fafc' }}>
                    {catalogLoading ? <div style={{ padding: 16 }}><LoadingSpinner label="Loading catalog…" /></div> : catalogError ? <div style={{ padding: 12 }}><ErrorAlert message={catalogError} onRetry={loadCatalog} /></div> : Object.keys(createGrouped).length === 0 ? <div style={{ padding: 16, textAlign: 'center', color: MUTED, fontSize: 13 }}>No permissions</div> : (
                      Object.entries(createGrouped).sort(([a],[b])=>a.localeCompare(b)).map(([group, perms]) => (
                        <div key={group} style={{ background: '#fff', borderBottom: '1px solid #e5e7eb' }}>
                          <div style={{ padding: '6px 10px', background: '#0F172A', color: '#fff', fontSize: 11, fontWeight: 700 }}>{group} · {perms.length}</div>
                          <div style={{ display: 'flex', flexDirection: 'column' }}>
                            {perms.map(p => {
                              const checked = createSelected.has(p.id)
                              return (
                                <label key={p.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, padding: '8px 10px', cursor: 'pointer', background: checked ? '#eff6ff' : '#fff', borderBottom: '1px solid #f1f5f9' }}>
                                  <input type="checkbox" checked={checked} onChange={e => {
                                    const next = new Set(createSelected)
                                    if (e.target.checked) next.add(p.id); else next.delete(p.id)
                                    setCreateSelected(next)
                                  }} style={{ marginTop: 2 }} />
                                  <div style={{ flex: 1 }}>
                                    <div style={{ fontSize: 12, fontWeight: 600, color: '#0F172A' }}>{p.name}</div>
                                    {p.description && <div style={{ fontSize: 11, color: MUTED }}>{p.description}</div>}
                                  </div>
                                </label>
                              )
                            })}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                  {!canReadPerms && <div style={{ fontSize: 11, color: '#92400e', background: '#fef3c7', padding: '6px 8px', borderRadius: 6 }}>You lack PERMISSION_READ – catalog may be empty.</div>}
                </div>
              )}

              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4, flexShrink: 0 }}>
                <button type="button" onClick={() => setModalMode(null)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Saving…' : modalMode === 'create' ? 'Create' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirm */}
      {deleteTarget && (
        <div onClick={() => !deleting && setDeleteTarget(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 420, maxWidth: '100%', background: '#fff', borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, color: '#7f1d1d' }}>Delete role?</h3>
            <p style={{ fontSize: 13, color: '#475569', marginTop: 8 }}>Delete <strong style={{ color: '#0F172A' }}>{deleteTarget.name}</strong>? {deleteTarget.name.toUpperCase() === 'ADMIN' && <span style={{ color: '#DC2626', fontWeight: 700 }}>ADMIN is protected – backend will return 403.</span>} If assigned to users backend returns 409.</p>
            {deleteError && <div style={{ marginTop: 8, background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{deleteError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setDeleteTarget(null)} disabled={deleting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
              <button onClick={handleDelete} disabled={deleting} style={{ padding: '8px 16px', background: deleting ? '#7f1d1d' : '#DC2626', color: '#fff', border: 0, borderRadius: 8, cursor: deleting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{deleting ? 'Deleting…' : 'Delete'}</button>
            </div>
          </div>
        </div>
      )}

      {/* Permissions assignment drawer */}
      {permRole && (
        <div onClick={() => !permSaving && setPermRole(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', justifyContent: 'flex-end', zIndex: 60 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 520, maxWidth: '100%', height: '100%', background: '#fff', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Permissions · {permRole.name}</h3>
                <div style={{ fontSize: 12, color: MUTED, marginTop: 2 }}>{assigned.size} assigned · {catalog.length} total · {canAssign ? 'editable' : 'read-only'}</div>
              </div>
              <button onClick={() => setPermRole(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 20, color: '#64748B' }}>×</button>
            </div>

            <div style={{ padding: 12, borderBottom: '1px solid #e5e7eb', display: 'flex', gap: 8 }}>
              <input placeholder="Filter permissions…" value={permFilter} onChange={e => setPermFilter(e.target.value)} style={{ flex: 1, padding: '8px 10px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
              <span style={{ fontSize: 11, color: MUTED, alignSelf: 'center', whiteSpace: 'nowrap' }}>{selected.size} selected</span>
            </div>

            <div style={{ flex: 1, overflowY: 'auto', padding: 12, background: '#f8fafc' }}>
              {catalogLoading ? <LoadingSpinner label="Loading catalog…" /> : catalogError ? <ErrorAlert message={catalogError} onRetry={() => openPerms(permRole)} /> : Object.keys(grouped).length === 0 ? <div style={{ padding: 24, textAlign: 'center', color: MUTED }}>No permissions</div> : (
                Object.entries(grouped).sort(([a],[b])=>a.localeCompare(b)).map(([group, perms]) => (
                  <div key={group} style={{ marginBottom: 16, background: '#fff', border: '1px solid #e5e7eb', borderRadius: 10, overflow: 'hidden' }}>
                    <div style={{ padding: '8px 12px', background: '#0F172A', color: '#fff', fontSize: 11, fontWeight: 700, letterSpacing: 0.6 }}>{group} · {perms.length}</div>
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                      {perms.map(p => {
                        const checked = selected.has(p.id)
                        const wasAssigned = assigned.has(p.id)
                        return (
                          <label key={p.id} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '9px 12px', borderBottom: '1px solid #f1f5f9', cursor: canAssign ? 'pointer' : 'default', opacity: canAssign ? 1 : 0.9, background: checked ? '#eff6ff' : '#fff' }}>
                            <input type="checkbox" checked={checked} disabled={!canAssign} onChange={e => {
                              const next = new Set(selected)
                              if (e.target.checked) next.add(p.id); else next.delete(p.id)
                              setSelected(next); setPermError(null)
                            }} style={{ marginTop: 2 }} />
                            <div style={{ flex: 1 }}>
                              <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', display: 'flex', gap: 6, alignItems: 'center' }}>
                                {p.name}
                                {wasAssigned && <span style={{ fontSize: 10, background: '#dcfce7', color: '#166534', padding: '1px 6px', borderRadius: 999 }}>assigned</span>}
                              </div>
                              {p.description && <div style={{ fontSize: 12, color: MUTED, marginTop: 2 }}>{p.description}</div>}
                            </div>
                          </label>
                        )
                      })}
                    </div>
                  </div>
                ))
              )}
              {!canReadPerms && <div style={{ padding: 12, background: '#fef3c7', border: '1px solid #fde68a', borderRadius: 8, color: '#92400e', fontSize: 13 }}>You lack PERMISSION_READ – catalog may be empty.</div>}
            </div>

            <div style={{ padding: 12, borderTop: '1px solid #e5e7eb', background: '#fff', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {permError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{permError}</div>}
              {permSuccess && <div role="status" style={{ background: '#dcfce7', border: '1px solid #86efac', color: '#166534', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{permSuccess}</div>}
              {!canAssign && <div style={{ fontSize: 11, color: MUTED }}>Read-only – you lack PERMISSION_ASSIGN.</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <button onClick={() => setPermRole(null)} disabled={permSaving} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Close</button>
                {canAssign && <button onClick={handlePermSave} disabled={permSaving || catalogLoading} style={{ padding: '8px 16px', background: permSaving ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: permSaving ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{permSaving ? 'Saving…' : 'Save permissions'}</button>}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
