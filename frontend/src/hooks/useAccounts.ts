import { useCallback, useEffect, useRef, useState } from 'react'
import { accountsApi } from '@/lib/api'
import type { Account } from '@/lib/types'

interface AccountsState {
  data: Account[]
  loading: boolean
  error: string | null
}

export interface UseAccounts extends AccountsState {
  /** Rebusca as contas sem voltar pro estado de loading (saldos frescos ao trocar de página). */
  refresh: () => void
}

export function useAccounts(): UseAccounts {
  const [state, setState] = useState<AccountsState>({
    data: [],
    loading: true,
    error: null,
  })
  const inFlight = useRef(false)

  const load = useCallback(async (silent: boolean) => {
    if (inFlight.current) return
    inFlight.current = true
    if (!silent) setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const accounts = await accountsApi.getAll()
      setState({ data: accounts, loading: false, error: null })
    } catch (err) {
      setState((s) => ({
        data: silent ? s.data : [],
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar contas',
      }))
    } finally {
      inFlight.current = false
    }
  }, [])

  useEffect(() => {
    void load(false)
  }, [load])

  const refresh = useCallback(() => void load(true), [load])

  return { ...state, refresh }
}
