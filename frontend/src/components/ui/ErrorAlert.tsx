export function ErrorAlert({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div style={{ padding: 16, background: '#451a1a', border: '1px solid #7f1d1d', borderRadius: 12, color: '#fecaca', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
      <span style={{ fontSize: 14 }}>{message}</span>
      {onRetry && (
        <button onClick={onRetry} style={{ padding: '6px 12px', background: '#7f1d1d', color: '#fff', border: 0, borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
          Retry
        </button>
      )}
    </div>
  )
}

export function EmptyState({ title, description, action }: { title: string; description?: string; action?: React.ReactNode }) {
  return (
    <div style={{ padding: 32, textAlign: 'center', background: '#111827', border: '1px solid #1E293B', borderRadius: 12, color: '#94A3B8' }}>
      <div style={{ fontWeight: 700, color: '#F8FAFC' }}>{title}</div>
      {description && <div style={{ fontSize: 13, marginTop: 8 }}>{description}</div>}
      {action && <div style={{ marginTop: 16 }}>{action}</div>}
    </div>
  )
}
