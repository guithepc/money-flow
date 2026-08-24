import { useCallback, useEffect, useState } from 'react'
import { authApi, UNAUTHORIZED_EVENT } from '@/lib/api'
import { clearToken, getToken, setToken } from '@/lib/auth'

interface AuthState {
  isAuthenticated: boolean
  loading: boolean
  error: string | null
}

export interface UseAuth extends AuthState {
  login: (email: string, password: string) => Promise<void>
  logout: () => void
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

  const logout = useCallback(() => {
    clearToken()
    setState({ isAuthenticated: false, loading: false, error: null })
  }, [])

  return { ...state, login, logout }
}
