import { useCallback, useEffect, useState } from 'react'
import { ApiError, authApi, refreshSession, UNAUTHORIZED_EVENT } from '@/lib/api'
import { clearToken, setToken } from '@/lib/auth'
import { PASSWORD_RULE_MESSAGE, validatePassword } from '@/lib/password'

interface AuthState {
  isAuthenticated: boolean
  /** true enquanto o app tenta restaurar a sessão pelo cookie de refresh. */
  restoring: boolean
  loading: boolean
  error: string | null
}

export interface UseAuth extends AuthState {
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string, acceptedTermsVersion: string) => Promise<void>
  logout: () => void
  clearError: () => void
}

/** Tentativas de restaurar a sessão quando a rede falha no carregamento. */
const RESTORE_RETRY_DELAYS_MS = [1000, 3000]

async function restoreSession(): Promise<boolean> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await refreshSession()
    } catch (e) {
      if (attempt >= RESTORE_RETRY_DELAYS_MS.length) throw e
      await new Promise((resolve) => setTimeout(resolve, RESTORE_RETRY_DELAYS_MS[attempt]))
    }
  }
}

export function useAuth(): UseAuth {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    restoring: true,
    loading: false,
    error: null,
  })

  // Ao abrir/recarregar o app, troca o cookie HttpOnly por um novo access token.
  useEffect(() => {
    let cancelled = false
    restoreSession()
      .then((ok) => {
        if (!cancelled) setState({ isAuthenticated: ok, restoring: false, loading: false, error: null })
      })
      .catch(() => {
        if (!cancelled) {
          setState({
            isAuthenticated: false,
            restoring: false,
            loading: false,
            error: 'Não foi possível conectar ao servidor. Tente novamente.',
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    function handleUnauthorized() {
      setState({ isAuthenticated: false, restoring: false, loading: false, error: null })
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const { token } = await authApi.login(email, password)
      setToken(token)
      setState({ isAuthenticated: true, restoring: false, loading: false, error: null })
    } catch {
      // Mensagem genérica de propósito: não revela se o email existe ou a senha está errada.
      setState({ isAuthenticated: false, restoring: false, loading: false, error: 'Email ou senha inválidos.' })
    }
  }, [])

  const register = useCallback(async (name: string, email: string, password: string, acceptedTermsVersion: string) => {
    const weak = validatePassword(password)
    if (weak) {
      setState({ isAuthenticated: false, restoring: false, loading: false, error: weak })
      return
    }
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const { token } = await authApi.register(name, email, password, acceptedTermsVersion)
      setToken(token)
      setState({ isAuthenticated: true, restoring: false, loading: false, error: null })
    } catch (e) {
      const error =
        e instanceof ApiError && e.code === 'WEAK_PASSWORD'
          ? PASSWORD_RULE_MESSAGE
          : 'Não foi possível criar a conta.'
      setState({ isAuthenticated: false, restoring: false, loading: false, error })
    }
  }, [])

  /** Limpa o erro ao trocar de tela, para não herdar a mensagem da anterior. */
  const clearError = useCallback(() => setState((s) => ({ ...s, error: null })), [])

  const logout = useCallback(() => {
    // Revoga no servidor sem travar a saída: mesmo offline o usuário volta ao login.
    void authApi.logout().catch(() => {})
    clearToken()
    setState({ isAuthenticated: false, restoring: false, loading: false, error: null })
  }, [])

  return { ...state, login, register, logout, clearError }
}
