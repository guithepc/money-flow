import type { Account, AmountAndCategory, DateRange, TotalAmount } from './types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function get<T>(path: string, params: Record<string, string> = {}): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`)
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  const res = await fetch(url, { headers: { Accept: 'application/json' } })
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText} — ${path}`)
  }
  return res.json() as Promise<T>
}

export const reportsApi = {
  totalSpent: (range: DateRange) =>
    get<TotalAmount>('/transactions/total-spent-by-time', range),

  totalIncome: (range: DateRange) =>
    get<TotalAmount>('/transactions/total-income-by-time', range),

  totalRecurring: (range: DateRange) =>
    get<TotalAmount>('/transactions/total-recurring-by-time', range),

  spentRankedByCategory: (range: DateRange) =>
    get<AmountAndCategory[]>('/transactions/total-spent-ranked-by-category', range),
}

export const accountsApi = {
  getAll: () => get<Account[]>('/accounts'),
}
