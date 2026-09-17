import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { PLATFORM_NAV_ITEMS } from '../utils/permissions'

export function AuthLayout() {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F8FAFC', padding: 16 }}>
      <Outlet />
    </div>
  )
}

// ---------------------------------------------------------------------------
// Platform Shell – separate from organisation shell (platform admin only)
// ---------------------------------------------------------------------------
const BG = '#0B1120'
const SIDEBAR_BG = '#0F172A'
const CARD_BG = '#111827'
const BORDER = '#1E293B'
const PRIMARY = '#6366F1'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'

function PlatformNavItem({ to, label }: { to: string; label: string }) {
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

export function AppLayout() {
  const username = useAuthStore((s) => s.username)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const navigate = useNavigate()

  const handleLogout = () => {
    clearAuth()
    navigate('/login', { replace: true })
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', background: BG, color: TEXT_MAIN, fontFamily: 'Inter, system-ui, sans-serif' }}>
      {/* Sidebar – Platform */}
      <aside
        style={{
          width: 260,
          background: SIDEBAR_BG,
          borderRight: `1px solid ${BORDER}`,
          display: 'flex',
          flexDirection: 'column',
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflow: 'hidden',
        }}
      >
        <div style={{ padding: 20, borderBottom: `1px solid ${BORDER}` }}>
          <div style={{ fontWeight: 800, letterSpacing: 0.5, fontSize: 18, color: TEXT_MAIN }}>TraceIQ</div>
          <div style={{ fontSize: 11, color: '#E879F9', marginTop: 2, fontWeight: 700, letterSpacing: 0.6, textTransform: 'uppercase' }}>Platform Admin</div>
        </div>

        <div style={{ padding: '12px 16px', borderBottom: `1px solid ${BORDER}` }}>
          <div style={{ fontSize: 12, color: TEXT_SEC, textTransform: 'uppercase', letterSpacing: 0.6 }}>Signed in as</div>
          <div style={{ fontSize: 13, fontWeight: 600, color: TEXT_MAIN, marginTop: 4, wordBreak: 'break-all' }}>{username || '-'}</div>
          <div style={{ fontSize: 11, color: TEXT_SEC, marginTop: 2 }}>Platform Administrator</div>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div>
            <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>Platform</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {PLATFORM_NAV_ITEMS.map((i) => (
                <PlatformNavItem key={i.to} to={i.to} label={i.label} />
              ))}
            </div>
          </div>

          <div>
            <div style={{ fontSize: 11, color: TEXT_SEC, letterSpacing: 0.6, textTransform: 'uppercase', marginBottom: 8, paddingLeft: 8 }}>System</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <button onClick={handleLogout} style={{ textAlign: 'left', padding: '8px 12px', borderRadius: 8, fontSize: 14, background: 'transparent', border: '1px solid transparent', color: '#F87171', cursor: 'pointer' }}>
                Logout
              </button>
            </div>
          </div>
        </div>

        <div style={{ padding: 12, borderTop: `1px solid ${BORDER}`, fontSize: 11, color: '#475569' }}>© TraceIQ Platform · Admin</div>
      </aside>

      {/* Main */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <header style={{ height: 56, background: SIDEBAR_BG, borderBottom: `1px solid ${BORDER}`, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 20px', position: 'sticky', top: 0, zIndex: 10 }}>
          <div style={{ fontSize: 14, color: TEXT_SEC, display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ background: '#4C1D95', color: '#E9D5FF', padding: '2px 8px', borderRadius: 999, fontSize: 11, fontWeight: 700 }}>PLATFORM</span>
            <span>Admin Workspace</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <div style={{ width: 32, height: 32, borderRadius: 999, background: '#9333EA', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: 13, fontWeight: 700 }}>
              {(username || 'A').slice(0, 1).toUpperCase()}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.1 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: TEXT_MAIN }}>{username}</span>
              <span style={{ fontSize: 11, color: TEXT_SEC }}>Platform Admin</span>
            </div>
          </div>
        </header>

        <main style={{ flex: 1, padding: 24, background: BG }}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
