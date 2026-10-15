import { useCallback, useEffect, useRef, useState } from 'react'
import { transactionsApi, type ReportScope } from '@/lib/api'
import type { DateRange, Transaction } from '@/lib/types'

interface TransactionsState {
  data: Transaction[]
  loading: boolean
  error: string | null
}

/**
 * Lista as transações do período (`GET /api/transactions`).
 * Escopo `scope` (conta e/ou moeda) repassado pro backend; `enabled = false` adia a busca.
 */
export function useTransactions(
  range: DateRange,
  scope: ReportScope,
  enabled = true,
): TransactionsState & { reload: () => void } {
  const [state, setState] = useState<TransactionsState>({
    data: [],
    loading: true,
    error: null,
  })

  const { accountId, currency } = scope
  // Descarta respostas atrasadas: trocar de moeda rápido não pode exibir valores de outra moeda.
  const requestRef = useRef(0)

  const load = useCallback(async () => {
    const request = ++requestRef.current
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const transactions = await transactionsApi.list(range, { accountId, currency })
      if (request !== requestRef.current) return
      setState({ data: transactions, loading: false, error: null })
    } catch (err) {
      if (request !== requestRef.current) return
      setState({
        data: [],
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar transações',
      })
    }
  }, [range.startDate, range.endDate, accountId, currency])

  useEffect(() => {
    if (enabled) void load()
  }, [load, enabled])

  // Refaz a busca com os mesmos filtros (ex.: depois de editar uma transação).
  const reload = useCallback(() => {
    if (enabled) void load()
  }, [load, enabled])

  return { ...state, reload }
}
