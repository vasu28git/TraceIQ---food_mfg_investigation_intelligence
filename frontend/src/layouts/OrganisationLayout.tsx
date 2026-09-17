import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { ORG_NAV_ITEMS, ORG_BOTTOM_NAV, PERMISSIONS } from '../utils/permissions'

const BG = '#0B1120'
const SIDEBAR_BG = '#0F172A'
const CARD_BG = '#111827'
const BORDER = '#1E293B'
const PRIMARY = '#6366F1'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'

function NavItem({ to, label, disabled }: { to: string; label: string; disabled?: boolean }) {
  if (disabled) {
    return (
      <div style={{ padding: '8px 12px', borderRadius: 8, color: '#475569', fontSize: 14, display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'not-allowed', opacity: 0.6 }}>
        <span>{label}</span>
        <span style={{ fontSize: 10, background: '#1E293B', color: '#64748B', padding: '2px 6px', borderRadius: 999 }}>Soon</span>
      </div>
    )
  }
  return (
    <NavLink
      to={to}
      style={({ isActive }) => ({
        display: 'block',
        padding: '8px 12px',
        borderRadius: 8,
        fontSize: 14,
        textDecoration: 'none',
        color: isActive ? TEXT_MAIN : TEXT_SEC,
        background: isActive ? '#1E293B' : 'transparent',
        border: isActive ? `1px solid ${BORDER}` : '1px solid transparent',
      })}
    >
      {label}
    </NavLink>
  )
}

export function OrganisationLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [isMobile, setIsMobile] = useState(false)
  const username = useAuthStore((s) => s.username)
  const role = useAuthStore((s) => s.role)
  const orgId = useAuthStore((s) => s.organisationId)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const hasAnyAuthority = useAuthStore((s) => s.hasAnyAuthority)
  const navigate = useNavigate()

  useEffect(() => {
    const onResize = () => setIsMobile(window.innerWidth < 768)
    onResize()
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  useEffect(() => {
    if (isMobile) setCollapsed(true)
  }, [isMobile])

  const handleLogout = () => {
    clearAuth()
    navigate('/login', { replace: true })
  }

  // Permission-aware filtering – hide items user cannot read (backend still authoritative)
  const visibleNav = ORG_NAV_ITEMS.filter((item) => {
    if (item.alwaysVisible) return true
    if (!item.group) return true
    return hasAnyAuthority(...PERMISSIONS[item.group])
  })

  // If user has no read permissions at all, still show dashboard; at least one item visible
  const showEmptyHint = visibleNav.length === 1 // only dashboard

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: BG, color: TEXT_MAIN, fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* Sidebar */}
      <aside
        style={{
          width: collapsed ? 72 : 260,
          background: SIDEBAR_BG,
          borderRight: `1px solid ${BORDER}`,
          display: 'flex',
          flexDirection: 'column',
          transition: 'width 0.2s',
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflow: 'hidden',
          flexShrink: 0,
        }}
      >
        <div style={{ padding: 20, borderBottom: `1px solid ${BORDER}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {!collapsed && (
            <div>
              <div style={{ fontWeight: 800, letterSpacing: 0.5, fontSize: 18, color: TEXT_MAIN }}>TraceIQ</div>
              <div style={{ fontSize: 11, color: TEXT_SEC, marginTop: 2 }}>Organisation Workspace</div>
            </div>
          )}
          <button onClick={() => setCollapsed((v) => !v)} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} style={{ background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, padding: '6px 8px', cursor: 'pointer' }}>
            {collapsed ? '→' : '←'}
          </button>
        </div>

        {!collapsed && (
          <div style={{ padding: '12px 16px', borderBottom: `1px solid ${BORDER}` }}>
            <div style={{ fontSize: 12, color: TEXT_SEC, textTransform: 'uppercase', letterSpacing: 0.6 }}>Organisation</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: TEXT_MAIN, marginTop: 4, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {orgId ? `Org #${orgId}` : 'Unknown Org'}
            </div>
            <div style={{ fontSize: 12, color: TEXT_SEC, marginTop: 2, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {username || '-'} {role ? `· ${role}` : ''}
            </div>
          </div>
        )}

        <div style={{ flex: 1, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            {!collapsed && <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>Workspace</div>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {visibleNav.filter(i => ['Dashboard','Investigations'].includes(i.label)).map((item) => (
                <NavItem key={item.to} to={item.to} label={collapsed ? (item.icon || '•') : item.label} />
              ))}
            </div>
          </div>

          <div>
            {!collapsed && <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>Evidence</div>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {visibleNav.filter(i => ['Complaints','Evidence Data'].includes(i.label)).map((item) => (
                <NavItem key={item.to} to={item.to} label={collapsed ? (item.icon || '•') : item.label} />
              ))}
              {visibleNav.filter(i => ['Complaints','Evidence Data'].includes(i.label)).length===0 && !collapsed && (
                <div style={{ fontSize: 11, color: '#64748B', padding: '8px', fontStyle: 'italic' }}>No evidence modules permitted</div>
              )}
            </div>
          </div>

          <div>
            {!collapsed && <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>Administration</div>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {visibleNav.filter(i => !['Dashboard','Investigations','Complaints','Evidence Data'].includes(i.label)).map((item) => (
                <NavItem key={item.to} to={item.to} label={collapsed ? (item.icon || '•') : item.label} />
              ))}
              {visibleNav.filter(i => !['Dashboard','Investigations','Complaints','Evidence Data'].includes(i.label)).length===0 && !collapsed && (
                <div style={{ fontSize: 11, color: '#64748B', padding: '8px', fontStyle: 'italic' }}>No additional modules permitted</div>
              )}
              {showEmptyHint && !collapsed && (
                <div style={{ fontSize: 11, color: '#64748B', padding: '8px', fontStyle: 'italic' }}>No additional modules permitted</div>
              )}
            </div>
          </div>

          <div>
            {!collapsed && <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>System</div>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {ORG_BOTTOM_NAV.map((item) => (
                <NavItem key={item.to} to={item.to} label={collapsed ? (item.icon || '•') : item.label} />
              ))}
              <button onClick={handleLogout} style={{ textAlign: 'left', padding: '8px 12px', borderRadius: 8, fontSize: 14, background: 'transparent', border: '1px solid transparent', color: '#F87171', cursor: 'pointer' }}>
                {collapsed ? '↪' : 'Logout'}
              </button>
            </div>
          </div>
        </div>

        {!collapsed && <div style={{ padding: 12, borderTop: `1px solid ${BORDER}`, fontSize: 11, color: '#475569' }}>© TraceIQ Platform</div>}
      </aside>

      {/* Mobile overlay when sidebar is open on small screens */}
      {isMobile && !collapsed && (
        <div onClick={() => setCollapsed(true)} style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', zIndex: 5 }} aria-hidden />
      )}

      {/* Main */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <header style={{ height: 56, background: SIDEBAR_BG, borderBottom: `1px solid ${BORDER}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 20px', position: 'sticky', top: 0, zIndex: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {isMobile && (
              <button onClick={() => setCollapsed((v) => !v)} style={{ background: CARD_BG, border: `1px solid ${BORDER}`, color: TEXT_SEC, borderRadius: 8, padding: '6px 8px', cursor: 'pointer' }}>
                ☰
              </button>
            )}
            <div style={{ fontSize: 14, color: TEXT_SEC }}>Organisation Workspace</div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 32, height: 32, borderRadius: 999, background: PRIMARY, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 13, fontWeight: 700 }}>
              {(username || 'U').slice(0, 1).toUpperCase()}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: TEXT_MAIN }}>{username}</span>
              <span style={{ fontSize: 11, color: TEXT_SEC }}>{role || 'Member'}</span>
            </div>
          </div>
        </header>

        <main style={{ flex: 1, padding: isMobile ? 16 : 24, background: BG }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
