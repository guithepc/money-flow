import { useCallback, useEffect, useState } from 'react'
import { accountsApi } from '@/lib/api'
import type { Account } from '@/lib/types'

interface AccountsState {
  data: Account[]
  loading: boolean
  error: string | null
}

export function useAccounts(): AccountsState {
  const [state, setState] = useState<AccountsState>({
    data: [],
    loading: true,
    error: null,
  })

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const accounts = await accountsApi.getAll()
      setState({ data: accounts, loading: false, error: null })
    } catch (err) {
      setState({
        data: [],
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar contas',
      })
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return state
}
