import { useCallback, useEffect, useState } from 'react'
import { transactionsApi } from '@/lib/api'
import type { DateRange, Transaction } from '@/lib/types'

interface TransactionsState {
  data: Transaction[]
  loading: boolean
  error: string | null
}

/**
 * Lista as transações do período (`GET /api/transactions`).
 * `accountId` é repassado pro backend (ignorado até o filtro por conta existir).
 */
export function useTransactions(range: DateRange, accountId?: number): TransactionsState {
  const [state, setState] = useState<TransactionsState>({
    data: [],
    loading: true,
    error: null,
  })

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const transactions = await transactionsApi.list(range, accountId)
      setState({ data: transactions, loading: false, error: null })
    } catch (err) {
      setState({
        data: [],
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar transações',
      })
    }
  }, [range.startDate, range.endDate, accountId])

  useEffect(() => {
    void load()
  }, [load])

  return state
}
