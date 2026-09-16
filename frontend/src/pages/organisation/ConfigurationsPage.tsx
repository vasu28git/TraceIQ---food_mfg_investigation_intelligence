import { useEffect, useMemo, useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PERMISSIONS } from '../../utils/permissions'
import { getApiErrorMessage } from '../../api/client'
import { listConfigurations, getConfiguration, createConfiguration, updateConfiguration, deleteConfiguration, listDefinitions } from '../../services/configurationService'
import type { Configuration, ConfigurationDefinition } from '../../types/configuration'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

function formatDate(d?: string) {
  if (!d) return '-'
  try { return new Date(d).toLocaleString() } catch { return d }
}

type CatalogItem = {
  definition: ConfigurationDefinition
  config: Configuration | null
}

function TypeBadge({ type }: { type?: string }) {
  const t = (type || 'STRING').toUpperCase()
  const map: Record<string, { bg: string; fg: string }> = {
    INTEGER: { bg: '#dbeafe', fg: '#1e40af' },
    BOOLEAN: { bg: '#fef3c7', fg: '#92400e' },
    ENUM: { bg: '#e0e7ff', fg: '#3730a3' },
    STRING: { bg: '#e2e8f0', fg: '#475569' },
  }
  const c = map[t] || map.STRING
  return <span style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: c.bg, color: c.fg, fontWeight: 700 }}>{t}</span>
}

export function ConfigurationsPage() {
  const orgId = useAuthStore((s) => s.organisationId)
  const hasAny = useAuthStore((s) => s.hasAnyAuthority)
  const canRead = hasAny(...PERMISSIONS.CONFIG_READ)
  const canCreate = hasAny(...PERMISSIONS.CONFIG_CREATE)
  const canUpdate = hasAny(...PERMISSIONS.CONFIG_UPDATE)
  const canDelete = hasAny(...PERMISSIONS.CONFIG_DELETE)

  const [definitions, setDefinitions] = useState<ConfigurationDefinition[]>([])
  const [configs, setConfigs] = useState<Configuration[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')

  // edit/set value
  const [editing, setEditing] = useState<CatalogItem | null>(null)
  const [formValue, setFormValue] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // view
  const [viewTarget, setViewTarget] = useState<Configuration | null>(null)
  const [viewLoading, setViewLoading] = useState(false)
  const [viewError, setViewError] = useState<string | null>(null)

  // delete (org value)
  const [deleteTarget, setDeleteTarget] = useState<Configuration | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const load = async () => {
    setLoading(true); setError(null)
    try {
      const [defs, confs] = await Promise.all([listDefinitions(), listConfigurations()])
      setDefinitions(Array.isArray(defs) ? defs : [])
      setConfigs(Array.isArray(confs) ? confs : [])
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 403) setError('Forbidden – missing CONFIG_READ permission.')
      else setError(getApiErrorMessage(err, 'Failed to load configurations'))
    } finally { setLoading(false) }
  }

  useEffect(() => { if (canRead) load() }, [orgId])

  const catalog: CatalogItem[] = useMemo(() => {
    const map = new Map<string, Configuration>()
    for (const c of configs) {
      if (c.definition?.key) map.set(c.definition.key, c)
    }
    return definitions.map(def => ({
      definition: def,
      config: map.get(def.key) || null,
    }))
  }, [definitions, configs])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return catalog
    return catalog.filter(item =>
      item.definition.key.toLowerCase().includes(q) ||
      (item.definition.description || '').toLowerCase().includes(q) ||
      (item.config?.value || '').toLowerCase().includes(q)
    )
  }, [catalog, query])

  const openEdit = (item: CatalogItem) => {
    if (!canUpdate && !canCreate) return
    setEditing(item)
    setFormValue(item.config?.value ?? '')
    setFormError(null)
  }

  const openView = async (item: CatalogItem) => {
    if (!item.config) {
      // No org value yet – show definition with default
      setViewTarget(null)
      // Create a synthetic view for definition without org value
      setViewTarget({
        id: -1,
        definition: item.definition,
        value: item.definition.defaultValue || null,
        createdAt: undefined,
        updatedAt: undefined,
      } as unknown as Configuration)
      return
    }
    setViewTarget(item.config); setViewError(null); setViewLoading(true)
    try {
      const fresh = await getConfiguration(item.config.id)
      setViewTarget(fresh)
    } catch (err: unknown) {
      setViewError(getApiErrorMessage(err, 'Failed to load details'))
    } finally { setViewLoading(false) }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editing) return
    setFormError(null)
    const def = editing.definition
    const trimmed = formValue.trim()
    const normalized = trimmed === '' ? null : trimmed

    // Frontend type validation (backend also validates)
    if (normalized != null) {
      const type = (def.type || 'STRING').toUpperCase()
      if (type === 'INTEGER') {
        if (!/^-?\d+$/.test(normalized)) {
          setFormError('Invalid integer value')
          return
        }
      } else if (type === 'BOOLEAN') {
        if (!['true', 'false'].includes(normalized.toLowerCase())) {
          setFormError('Invalid boolean value – must be true or false')
          return
        }
      } else if (type === 'ENUM' && def.allowedValues && def.allowedValues.length > 0) {
        if (!def.allowedValues.includes(normalized)) {
          setFormError(`Invalid value. Allowed: ${def.allowedValues.join(', ')}`)
          return
        }
      }
    }

    setSubmitting(true)
    try {
      if (editing.config) {
        // Update existing org value
        await updateConfiguration(editing.config.id, { value: normalized })
      } else {
        // Create org value for this definition
        await createConfiguration({ definition: { key: def.key }, value: normalized })
      }
      setEditing(null)
      setFormValue('')
      await load()
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Save failed')
      if (status === 409) msg = 'Configuration for this key already exists (409) – try refreshing'
      if (status === 403) msg = 'Forbidden – missing permission (403)'
      if (status === 400 && msg.toLowerCase().includes('allowed')) msg = msg
      setFormError(msg)
    } finally { setSubmitting(false) }
  }

  const handleDelete = async () => {
    if (!deleteTarget || !canDelete) return
    setDeleting(true); setDeleteError(null)
    try {
      await deleteConfiguration(deleteTarget.id)
      setConfigs(prev => prev.filter(x => x.id !== deleteTarget.id))
      setDeleteTarget(null)
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      let msg = getApiErrorMessage(err, 'Delete failed')
      if (status === 403) msg = 'Forbidden – missing CONFIG_DELETE (403)'
      if (status === 404) msg = 'Configuration not found (404)'
      setDeleteError(msg)
    } finally { setDeleting(false) }
  }

  if (!canRead) {
    return <div style={{ padding: 24, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca' }}><h3 style={{ margin: 0 }}>Forbidden</h3><p style={{ fontSize: 14, marginTop: 8 }}>You do not have CONFIG_READ permission.</p></div>
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h2 style={{ margin: 0, color: TEXT_MAIN, fontSize: 20, fontWeight: 800 }}>Configurations</h2>
          <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 4 }}>{definitions.length} predefined definitions · {configs.length} configured · Org #{orgId}</div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={load} style={{ padding: '8px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 8, cursor: 'pointer' }}>Refresh</button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <input placeholder="Search by key, description or value…" value={query} onChange={e => setQuery(e.target.value)} style={{ flex: 1, minWidth: 220, padding: '9px 12px', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 8, color: TEXT_MAIN }} />
        <span style={{ fontSize: 12, color: MUTED, alignSelf: 'center' }}>{filtered.length} filtered / {catalog.length} total</span>
      </div>

      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading ? <LoadingSpinner label="Loading configurations…" /> : filtered.length === 0 ? (
        <div style={{ padding: 32, textAlign: 'center', background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, color: TEXT_SEC }}>
          <div style={{ fontWeight: 700, color: TEXT_MAIN }}>No matching configurations</div>
          <div style={{ fontSize: 13, marginTop: 6 }}>Try a different search term.</div>
        </div>
      ) : (
        <div style={{ background: CARD_BG, border: `1px solid ${BORDER}`, borderRadius: 12, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, minWidth: 860 }}>
              <thead>
                <tr style={{ textAlign: 'left', background: '#0F172A' }}>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC, fontWeight: 600 }}>Key</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Description</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Type</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Current Value</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Default</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Allowed</th>
                  <th style={{ padding: '10px 12px', borderBottom: `1px solid ${BORDER}`, color: TEXT_SEC }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(item => {
                  const def = item.definition
                  const cfg = item.config
                  const current = cfg?.value ?? null
                  const isConfigured = cfg != null
                  return (
                    <tr key={def.key} style={{ borderBottom: `1px solid ${BORDER}` }}>
                      <td style={{ padding: '10px 12px', color: TEXT_MAIN, fontWeight: 700, fontFamily: 'ui-monospace, monospace', fontSize: 12 }}>{def.key}</td>
                      <td style={{ padding: '10px 12px', color: TEXT_SEC, maxWidth: 260, fontSize: 12 }}>{def.description || '-'}</td>
                      <td style={{ padding: '10px 12px' }}><TypeBadge type={def.type} /></td>
                      <td style={{ padding: '10px 12px', color: isConfigured ? TEXT_MAIN : MUTED, fontWeight: isConfigured ? 600 : 400, fontSize: 12 }}>
                        {isConfigured ? (current ?? <span style={{ fontStyle: 'italic' }}>(null)</span>) : <span style={{ fontStyle: 'italic', color: MUTED }}>(not set → {def.defaultValue || '–'})</span>}
                      </td>
                      <td style={{ padding: '10px 12px', color: MUTED, fontFamily: 'ui-monospace, monospace', fontSize: 11 }}>{def.defaultValue || '–'}</td>
                      <td style={{ padding: '10px 12px', color: TEXT_SEC, maxWidth: 200 }}>
                        {def.allowedValues && def.allowedValues.length > 0 ? (
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                            {def.allowedValues.map(v => <span key={v} style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: v === current ? '#dcfce7' : '#0B1120', color: v === current ? '#166534' : MUTED, border: `1px solid ${v === current ? '#86efac' : BORDER}` }}>{v}</span>)}
                          </div>
                        ) : <span style={{ fontSize: 11, color: MUTED }}>{def.type === 'BOOLEAN' ? 'true, false' : def.type === 'INTEGER' ? 'any integer' : 'any'}</span>}
                      </td>
                      <td style={{ padding: '10px 12px' }}>
                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                          <button onClick={() => openView(item)} style={{ padding: '4px 8px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>View</button>
                          {(canUpdate || canCreate) && <button onClick={() => openEdit(item)} style={{ padding: '4px 8px', background: 'transparent', border: `1px solid ${BORDER}`, color: TEXT_MAIN, borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>{isConfigured ? 'Edit' : 'Set value'}</button>}
                          {canDelete && isConfigured && <button onClick={() => { setDeleteTarget(cfg!); setDeleteError(null) }} style={{ padding: '4px 8px', background: 'transparent', border: `1px solid #7f1d1d`, color: '#F87171', borderRadius: 6, cursor: 'pointer', fontSize: 12 }}>Clear</button>}
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

      {/* Edit/Set value modal */}
      {editing && (
        <div onClick={() => !submitting && setEditing(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 460, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>{editing.config ? `Edit · ${editing.definition.key}` : `Set value · ${editing.definition.key}`}</h3>
              <button onClick={() => setEditing(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <form onSubmit={handleSubmit} style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div style={{ padding: '8px 10px', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8 }}>
                <div style={{ fontSize: 11, color: MUTED, fontWeight: 600 }}>KEY (immutable)</div>
                <div style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 700, color: '#0F172A' }}>{editing.definition.key}</div>
                <div style={{ fontSize: 12, color: MUTED, marginTop: 4 }}>{editing.definition.description}</div>
                <div style={{ fontSize: 11, color: MUTED, marginTop: 6 }}>Type: <TypeBadge type={editing.definition.type} /> {editing.definition.defaultValue && <span style={{ marginLeft: 6 }}>Default: <code>{editing.definition.defaultValue}</code></span>}</div>
                {editing.definition.allowedValues && editing.definition.allowedValues.length > 0 && (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 6 }}>
                    {editing.definition.allowedValues.map(v => <span key={v} style={{ fontSize: 10, padding: '2px 6px', borderRadius: 999, background: '#e0e7ff', color: '#3730a3' }}>{v}</span>)}
                  </div>
                )}
              </div>

              {(() => {
                const type = (editing.definition.type || 'STRING').toUpperCase()
                if (type === 'ENUM' && editing.definition.allowedValues && editing.definition.allowedValues.length > 0) {
                  return (
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Value</span>
                      <select value={formValue} onChange={e => setFormValue(e.target.value)} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }}>
                        <option value="">-- (use default / null) --</option>
                        {editing.definition.allowedValues.map(v => <option key={v} value={v}>{v}</option>)}
                      </select>
                    </label>
                  )
                }
                if (type === 'BOOLEAN') {
                  return (
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Value (BOOLEAN)</span>
                      <select value={formValue} onChange={e => setFormValue(e.target.value)} style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }}>
                        <option value="">-- (use default / null) --</option>
                        <option value="true">true</option>
                        <option value="false">false</option>
                      </select>
                    </label>
                  )
                }
                if (type === 'INTEGER') {
                  return (
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Value (INTEGER)</span>
                      <input type="number" step="1" value={formValue} onChange={e => setFormValue(e.target.value)} placeholder="e.g. 30" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                    </label>
                  )
                }
                return (
                  <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span style={{ fontSize: 12, fontWeight: 600, color: '#334155' }}>Value</span>
                    <input value={formValue} onChange={e => setFormValue(e.target.value)} placeholder="Leave blank for default" style={{ padding: '9px 12px', border: '1px solid #CBD5E1', borderRadius: 8 }} />
                  </label>
                )
              })()}

              <div style={{ fontSize: 11, color: MUTED }}>Only value is mutable – definition/key and organisation cannot be changed (backend returns 400). Organisation is forced from your session.</div>
              {formError && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{formError}</div>}
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
                <button type="button" onClick={() => setEditing(null)} disabled={submitting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
                <button type="submit" disabled={submitting} style={{ padding: '8px 16px', background: submitting ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: submitting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{submitting ? 'Saving…' : 'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* View modal */}
      {viewTarget && (
        <div onClick={() => setViewTarget(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 480, maxWidth: '100%', background: '#fff', borderRadius: 12, overflow: 'hidden', boxShadow: '0 20px 60px rgba(0,0,0,0.3)' }}>
            <div style={{ padding: 16, borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800, color: '#0F172A' }}>Configuration · {viewTarget.definition.key}</h3>
              <button onClick={() => setViewTarget(null)} style={{ background: 'transparent', border: 0, cursor: 'pointer', fontSize: 18, color: '#64748B' }}>×</button>
            </div>
            <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
              {viewLoading ? <LoadingSpinner label="Loading details…" /> : viewError ? <ErrorAlert message={viewError} onRetry={() => viewTarget && openView({ definition: viewTarget.definition, config: viewTarget } as CatalogItem)} /> : (
                <>
                  <div style={{ display: 'grid', gridTemplateColumns: '120px 1fr', gap: 8, fontSize: 13 }}>
                    <span style={{ color: MUTED, fontWeight: 600 }}>ID</span><span style={{ fontFamily: 'ui-monospace, monospace' }}>{viewTarget.id > 0 ? viewTarget.id : '– (not yet configured)'}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Key</span><span style={{ fontFamily: 'ui-monospace, monospace', fontWeight: 700 }}>{viewTarget.definition.key}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Type</span><span><TypeBadge type={viewTarget.definition.type} /></span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Value</span><span style={{ fontWeight: 600, color: viewTarget.value ? '#0F172A' : MUTED }}>{viewTarget.value ?? '(null / default)'}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Description</span><span>{viewTarget.definition.description || '-'}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Allowed</span><span>{viewTarget.definition.allowedValues && viewTarget.definition.allowedValues.length ? viewTarget.definition.allowedValues.join(', ') : 'Any'}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Default</span><span>{viewTarget.definition.defaultValue || '-'}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Updated</span><span>{formatDate(viewTarget.updatedAt)}</span>
                    <span style={{ color: MUTED, fontWeight: 600 }}>Created</span><span>{formatDate(viewTarget.createdAt)}</span>
                  </div>
                  <div style={{ fontSize: 11, color: MUTED, background: '#f8fafc', padding: '8px 10px', borderRadius: 8, border: '1px solid #e2e8f0' }}>Backend returns exactly what is stored – no extra fields. Organisation scoping is enforced server-side. Definitions are global.</div>
                </>
              )}
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <button onClick={() => setViewTarget(null)} style={{ padding: '8px 14px', background: '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: 'pointer', fontWeight: 600 }}>Close</button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Delete confirm (clear org value) */}
      {deleteTarget && (
        <div onClick={() => !deleting && setDeleteTarget(null)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, padding: 16 }}>
          <div onClick={e => e.stopPropagation()} style={{ width: 400, maxWidth: '100%', background: '#fff', borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, color: '#7f1d1d' }}>Clear configuration value?</h3>
            <p style={{ fontSize: 13, color: '#475569', marginTop: 8 }}>Clear value for <strong style={{ color: '#0F172A', fontFamily: 'ui-monospace, monospace' }}>{deleteTarget.definition.key}</strong> (value: {deleteTarget.value ?? 'null'})? This will remove the organization-specific override and fall back to default.</p>
            {deleteError && <div style={{ marginTop: 8, background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '8px 10px', borderRadius: 8, fontSize: 13 }}>{deleteError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
              <button onClick={() => setDeleteTarget(null)} disabled={deleting} style={{ padding: '8px 14px', background: '#fff', border: '1px solid #CBD5E1', borderRadius: 8, cursor: 'pointer' }}>Cancel</button>
              <button onClick={handleDelete} disabled={deleting} style={{ padding: '8px 16px', background: deleting ? '#7f1d1d' : '#DC2626', color: '#fff', border: 0, borderRadius: 8, cursor: deleting ? 'not-allowed' : 'pointer', fontWeight: 700 }}>{deleting ? 'Clearing…' : 'Clear'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
