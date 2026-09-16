import type { ReactNode } from 'react'
import { useAuthStore } from '../store/authStore'

type Props = {
  /** single authority required */
  authority?: string
  /** any of these authorities required */
  anyOf?: string[]
  /** all of these authorities required */
  allOf?: string[]
  /** what to render when denied – default hides */
  fallback?: ReactNode
  children: ReactNode
}

/**
 * PermissionGuard – hides children if user lacks required authority.
 * Frontend only; backend is authoritative (see AuthorizationService.java).
 */
export function PermissionGuard({ authority, anyOf, allOf, fallback = null, children }: Props) {
  const hasAuthority = useAuthStore((s) => s.hasAuthority)
  const hasAnyAuthority = useAuthStore((s) => s.hasAnyAuthority)
  const hasAllAuthorities = useAuthStore((s) => s.hasAllAuthorities)

  let allowed = true
  if (authority) allowed = hasAuthority(authority)
  else if (anyOf) allowed = hasAnyAuthority(...anyOf)
  else if (allOf) allowed = hasAllAuthorities(...allOf)

  return allowed ? <>{children}</> : <>{fallback}</>
}

export function RequirePermission({ anyOf, children }: { anyOf: string[]; children: ReactNode }) {
  const hasAnyAuthority = useAuthStore((s) => s.hasAnyAuthority)
  if (!hasAnyAuthority(...anyOf)) {
    return (
      <div style={{ padding: 24, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca' }}>
        <h3 style={{ margin: 0 }}>Forbidden</h3>
        <p style={{ fontSize: 14, marginTop: 8 }}>You do not have permission to view this page.</p>
      </div>
    )
  }
  return <>{children}</>
}
