import { useCallback, useEffect, useState } from 'react'
import { reportsApi } from '@/lib/api'
import type { AmountAndCategory, DateRange } from '@/lib/types'

export interface ReportsData {
  totalSpent: number
  totalIncome: number
  totalRecurring: number
  balance: number
  ranking: AmountAndCategory[]
}

interface ReportsState {
  data: ReportsData | null
  loading: boolean
  error: string | null
}

/**
 * Agregações do dashboard pro período. `accountId` é repassado pro backend
 * (ignorado até o filtro por conta existir).
 */
export function useReports(range: DateRange, accountId?: number): ReportsState {
  const [state, setState] = useState<ReportsState>({
    data: null,
    loading: true,
    error: null,
  })

  const load = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const [spent, income, recurring, ranking] = await Promise.all([
        reportsApi.totalSpent(range, accountId),
        reportsApi.totalIncome(range, accountId),
        reportsApi.totalRecurring(range, accountId),
        reportsApi.spentRankedByCategory(range, accountId),
      ])
      setState({
        loading: false,
        error: null,
        data: {
          totalSpent: spent.total,
          totalIncome: income.total,
          totalRecurring: recurring.total,
          balance: income.total - spent.total,
          ranking,
        },
      })
    } catch (err) {
      setState({
        data: null,
        loading: false,
        error: err instanceof Error ? err.message : 'Erro ao carregar dados',
      })
    }
  }, [range.startDate, range.endDate, accountId])

  useEffect(() => {
    void load()
  }, [load])

  return state
}
