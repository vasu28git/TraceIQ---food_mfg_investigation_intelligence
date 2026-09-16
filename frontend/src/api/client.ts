import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 60000,
})

// ----- Request interceptor: attach JWT -----
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = useAuthStore.getState().token
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // Let browser set multipart boundary for FormData
    if (config.data instanceof FormData && config.headers) {
      delete (config.headers as Record<string, unknown>)['Content-Type']
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ----- Response interceptor: 401 / 403 handling -----
let isRedirectingToLogin = false

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status
    const originalUrl = error.config?.url || ''

    // Do not redirect on login failure itself – let LoginPage show error
    const isLoginRequest = originalUrl.includes('/auth/login')
    const isChangePwdRequest = originalUrl.includes('/auth/change-password')

    if (status === 401 && !isLoginRequest && !isChangePwdRequest) {
      useAuthStore.getState().clearAuth()
      if (!isRedirectingToLogin && window.location.pathname !== '/login') {
        isRedirectingToLogin = true
        // use hard navigation to ensure complete reset when outside router context
        window.location.href = '/login'
        // fallback reset after navigation
        setTimeout(() => {
          isRedirectingToLogin = false
        }, 1000)
      }
    }

    if (status === 403) {
      // Forbidden is authorization-level, not authentication. Do not logout.
      // Enrich error for UI; console for diagnostics
      console.warn('[API] 403 Forbidden:', error.response?.data)
    }

    return Promise.reject(error)
  }
)

// Helper to extract user-friendly message from AxiosError
export function getApiErrorMessage(err: unknown, fallback = 'Something went wrong'): string {
  const ax = err as { response?: { data?: { message?: string; error?: string }; status?: number }; code?: string; message?: string }
  if (ax?.response?.data?.message) return ax.response.data.message
  if (ax?.response?.data?.error) return ax.response.data.error
  // Axios timeout has code ECONNABORTED and message like "timeout of 60000ms exceeded"
  if (ax?.code === 'ECONNABORTED' || (ax?.message && ax.message.toLowerCase().includes('timeout'))) {
    return 'Request timed out – server is busy (Neo4j projection). Please wait a moment and retry.'
  }
  if (ax instanceof Error && ax.message) return ax.message
  return fallback
}
