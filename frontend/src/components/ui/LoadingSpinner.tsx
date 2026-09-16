export function LoadingSpinner({ label = 'Loading...' }: { label?: string }) {
  return (
    <div style={{ padding: 32, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12, color: '#94A3B8' }}>
      <span
        aria-hidden
        style={{
          width: 20,
          height: 20,
          border: '2px solid #334155',
          borderTopColor: '#6366F1',
          borderRadius: '50%',
          display: 'inline-block',
          animation: 'spin 0.8s linear infinite',
        }}
      />
      <span style={{ fontSize: 14 }}>{label}</span>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
    </div>
  )
}

export function InlineLoader() {
  return <span style={{ fontSize: 13, color: '#94A3B8' }}>Loading…</span>
}
