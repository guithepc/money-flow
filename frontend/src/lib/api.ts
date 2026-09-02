import type {
  Account,
  AmountAndCategory,
  DateRange,
  MonthlyIncomeExpense,
  TimeSeriesPoint,
  TotalAmount,
  Transaction,
} from './types'
import { clearToken, getToken } from './auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

/** Disparado quando qualquer chamada recebe 401 (token ausente/expirado). */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

function authHeaders(): HeadersInit {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function handleResponse<T>(res: Response, path: string): Promise<T> {
  if (res.status === 401) {
    clearToken()
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
  }
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText} — ${path}`)
  }
  return res.json() as Promise<T>
}

async function get<T>(path: string, params: Record<string, string> = {}): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`)
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value)
  }
  const res = await fetch(url, {
    headers: { Accept: 'application/json', ...authHeaders() },
  })
  return handleResponse<T>(res, path)
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`)
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(body),
  })
  return handleResponse<T>(res, path)
}

/**
 * Monta os query params das queries de agregação. `accountId` é opcional e só
 * entra no querystring quando presente.
 */
function reportParams(range: DateRange, accountId?: number): Record<string, string> {
  const params: Record<string, string> = { ...range }
  if (accountId != null) params.accountId = String(accountId)
  return params
}

export const reportsApi = {
  totalSpent: (range: DateRange, accountId?: number) =>
    get<TotalAmount>('/transactions/total-spent-by-time', reportParams(range, accountId)),

  totalIncome: (range: DateRange, accountId?: number) =>
    get<TotalAmount>('/transactions/total-income-by-time', reportParams(range, accountId)),

  totalRecurring: (range: DateRange, accountId?: number) =>
    get<TotalAmount>('/transactions/total-recurring-by-time', reportParams(range, accountId)),

  spentRankedByCategory: (range: DateRange, accountId?: number) =>
    get<AmountAndCategory[]>(
      '/transactions/total-spent-ranked-by-category',
      reportParams(range, accountId),
    ),

  incomeExpenseMonthly: (range: DateRange, accountId?: number) =>
    get<MonthlyIncomeExpense[]>(
      '/transactions/income-expense-monthly',
      reportParams(range, accountId),
    ),

  balanceHistory: async (range: DateRange, accountId?: number) => {
    const raw = await get<Record<string, unknown>[]>(
      '/transactions/history-balance',
      reportParams(range, accountId),
    )
    return raw.map((r) => ({
      date: String(r.date ?? r.day),
      income: Number(r.income),
      expense: Number(r.expense),
      net: Number(r.net),
    })) as TimeSeriesPoint[]
  },
}

export const transactionsApi = {
  list: (range: DateRange, accountId?: number) =>
    get<Transaction[]>('/transactions', reportParams(range, accountId)),
}

export const accountsApi = {
  getAll: () => get<Account[]>('/accounts'),
}

export const authApi = {
  login: (email: string, password: string) =>
    post<{ token: string }>('/auth/login', { email, password }),
}
