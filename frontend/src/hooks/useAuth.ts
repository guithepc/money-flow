import { useCallback, useEffect, useState } from 'react'
import { ApiError, authApi, UNAUTHORIZED_EVENT } from '@/lib/api'
import { clearToken, getToken, setToken } from '@/lib/auth'
import { PASSWORD_RULE_MESSAGE, validatePassword } from '@/lib/password'

interface AuthState {
  isAuthenticated: boolean
  loading: boolean
  error: string | null
}

export interface UseAuth extends AuthState {
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
  clearError: () => void
}

export function useAuth(): UseAuth {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: Boolean(getToken()),
    loading: false,
    error: null,
  })

  useEffect(() => {
    function handleUnauthorized() {
      setState({ isAuthenticated: false, loading: false, error: null })
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const { token } = await authApi.login(email, password)
      setToken(token)
      setState({ isAuthenticated: true, loading: false, error: null })
    } catch {
      // Mensagem genérica de propósito: não revela se o email existe ou a senha está errada.
      setState({ isAuthenticated: false, loading: false, error: 'Email ou senha inválidos.' })
    }
  }, [])

  const register = useCallback(async (name: string, email: string, password: string) => {
    const weak = validatePassword(password)
    if (weak) {
      setState({ isAuthenticated: false, loading: false, error: weak })
      return
    }
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const { token } = await authApi.register(name, email, password)
      setToken(token)
      setState({ isAuthenticated: true, loading: false, error: null })
    } catch (e) {
      const error =
        e instanceof ApiError && e.code === 'WEAK_PASSWORD'
          ? PASSWORD_RULE_MESSAGE
          : 'Não foi possível criar a conta.'
      setState({ isAuthenticated: false, loading: false, error })
    }
  }, [])

  /** Limpa o erro ao trocar de tela, para não herdar a mensagem da anterior. */
  const clearError = useCallback(() => setState((s) => ({ ...s, error: null })), [])

  const logout = useCallback(() => {
    clearToken()
    setState({ isAuthenticated: false, loading: false, error: null })
  }, [])

  return { ...state, login, register, logout, clearError }
}
