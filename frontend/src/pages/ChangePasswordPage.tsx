import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { changePasswordApi } from '../services/authService'
import { useAuthStore } from '../store/authStore'
import { getApiErrorMessage } from '../api/client'

export function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('New password and confirm password do not match')
      return
    }
    setLoading(true)
    try {
      await changePasswordApi(currentPassword, newPassword, confirmPassword)
      setSuccess(true)
      setTimeout(() => {
        clearAuth()
        navigate('/login', { replace: true })
      }, 1200)
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, 'Failed to change password'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ width: 420, maxWidth: '90vw', background: '#fff', borderRadius: 12, boxShadow: '0 8px 30px rgba(0,0,0,0.08)', border: '1px solid #E2E8F0', overflow: 'hidden' }}>
      <div style={{ padding: 24, borderBottom: '1px solid #F1F5F9' }}>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: '#0F172A' }}>Change Password</h2>
        <p style={{ margin: '6px 0 0', fontSize: 13, color: '#64748B' }}>You must change your password before continuing. This is required for new or reset accounts.</p>
      </div>

      <form onSubmit={handleSubmit} style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Current password</label>
          <input type="password" placeholder="••••••••" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required autoComplete="current-password" style={{ padding: '10px 12px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>New password</label>
          <input type="password" placeholder="At least 8 characters" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required autoComplete="new-password" style={{ padding: '10px 12px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 14 }} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <label style={{ fontSize: 13, fontWeight: 600, color: '#334155' }}>Confirm new password</label>
          <input type="password" placeholder="Repeat new password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required autoComplete="new-password" style={{ padding: '10px 12px', border: '1px solid #CBD5E1', borderRadius: 8, fontSize: 14 }} />
        </div>

        {error && <div role="alert" style={{ background: '#FEF2F2', border: '1px solid #FECACA', color: '#DC2626', padding: '10px 12px', borderRadius: 8, fontSize: 13 }}>{error}</div>}
        {success && <div role="status" style={{ background: '#ECFDF5', border: '1px solid #A7F3D0', color: '#065F46', padding: '10px 12px', borderRadius: 8, fontSize: 13 }}>Password changed — redirecting to login…</div>}

        <button type="submit" disabled={loading || success} style={{ padding: '11px 16px', background: loading || success ? '#475569' : '#0F172A', color: '#fff', border: 0, borderRadius: 8, cursor: loading ? 'not-allowed' : 'pointer', fontWeight: 700, fontSize: 14 }}>
          {loading ? 'Updating…' : success ? 'Done' : 'Change Password'}
        </button>
      </form>
    </div>
  )
}
