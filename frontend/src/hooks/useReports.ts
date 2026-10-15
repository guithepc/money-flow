import { useCallback, useEffect, useRef, useState } from 'react'
import { reportsApi, type ReportScope } from '@/lib/api'
import type { AmountAndCategory, DateRange, MonthlyIncomeExpense, TimeSeriesPoint } from '@/lib/types'

export interface ReportsData {
  totalSpent: number
  totalIncome: number
  balance: number
  ranking: AmountAndCategory[]
  monthlyIncomeExpense: MonthlyIncomeExpense[]
  balanceHistory: TimeSeriesPoint[]
}

interface ReportsState {
  data: ReportsData | null
  loading: boolean
  error: string | null
}

/**
 * Agregações do dashboard pro período, no escopo `scope` (conta e/ou moeda).
 * Com `enabled = false` não busca nada (ex.: enquanto a moeda padrão não é conhecida).
 */
export function useReports(
  range: DateRange,
  scope: ReportScope,
  enabled = true,
): ReportsState & { reload: () => void } {
  const [state, setState] = useState<ReportsState>({
    data: null,
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
      const [spent, income, ranking, monthlyIncomeExpense, balanceHistory] = await Promise.all([
        reportsApi.totalSpent(range, { accountId, currency }),
        reportsApi.totalIncome(range, { accountId, currency }),
        reportsApi.spentRankedByCategory(range, { accountId, currency }),
        reportsApi.incomeExpenseMonthly(range, { accountId, currency }),
        reportsApi
          .balanceHistory(range, { accountId, currency })
          .catch(() => [] as TimeSeriesPoint[]),
      ])
      if (request !== requestRef.current) return
      setState({
        loading: false,
        error: null,
        data: {
          totalSpent: spent.total,
          totalIncome: income.total,
          balance: income.total - spent.total,
          ranking,
          monthlyIncomeExpense,
          balanceHistory,
        },
      })
    } catch (err) {
      if (request !== requestRef.current) return
      setState({
        data: null,
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar dados',
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
