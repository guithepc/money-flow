import type { Account, AmountAndCategory, DateRange, TotalAmount } from './types'
import { clearToken, getToken } from './auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

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

export const authApi = {
  login: (email: string, password: string) =>
    post<{ token: string }>('/auth/login', { email, password }),
}
