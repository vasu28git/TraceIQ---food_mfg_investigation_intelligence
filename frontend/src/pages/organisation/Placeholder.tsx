export function Placeholder({ title }: { title: string }) {
  return (
    <div style={{ padding: 24, background: '#111827', border: '1px solid #1E293B', borderRadius: 12 }}>
      <h3 style={{ margin: 0, color: '#F8FAFC' }}>{title}</h3>
      <p style={{ color: '#94A3B8', fontSize: 14, marginTop: 8 }}>This module is coming soon. The backend APIs for {title} are ready, UI will be built incrementally.</p>
    </div>
  )
}
