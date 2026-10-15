import type {
  Account,
  Currency,
  AmountAndCategory,
  DateRange,
  MonthlyIncomeExpense,
  OwnerProfile,
  TelegramInvite,
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
  let res: Response
  try {
    res = await fetch(url, {
      headers: { Accept: 'application/json', ...authHeaders() },
    })
  } catch {
    if (getToken()) {
      clearToken()
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    throw new Error(`Network error — ${path}`)
  }
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

/** Escopo das agregações: conta específica e/ou moeda (nunca soma moedas diferentes). */
export interface ReportScope {
  accountId?: number
  currency?: Currency
}

/**
 * Monta os query params das queries de agregação. `accountId` e `currency` são
 * opcionais e só entram no querystring quando presentes.
 */
function reportParams(range: DateRange, scope: ReportScope = {}): Record<string, string> {
  const params: Record<string, string> = { ...range }
  if (scope.accountId != null) params.accountId = String(scope.accountId)
  if (scope.currency != null) params.currency = scope.currency
  return params
}

export const reportsApi = {
  totalSpent: (range: DateRange, scope?: ReportScope) =>
    get<TotalAmount>('/transactions/total-spent-by-time', reportParams(range, scope)),

  totalIncome: (range: DateRange, scope?: ReportScope) =>
    get<TotalAmount>('/transactions/total-income-by-time', reportParams(range, scope)),

  totalRecurring: (range: DateRange, scope?: ReportScope) =>
    get<TotalAmount>('/transactions/total-recurring-by-time', reportParams(range, scope)),

  spentRankedByCategory: (range: DateRange, scope?: ReportScope) =>
    get<AmountAndCategory[]>(
      '/transactions/total-spent-ranked-by-category',
      reportParams(range, scope),
    ),

  incomeExpenseMonthly: (range: DateRange, scope?: ReportScope) =>
    get<MonthlyIncomeExpense[]>(
      '/transactions/income-expense-monthly',
      reportParams(range, scope),
    ),

  balanceHistory: async (range: DateRange, scope?: ReportScope) => {
    const raw = await get<Record<string, unknown>[]>(
      '/transactions/history-balance',
      reportParams(range, scope),
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
  list: (range: DateRange, scope?: ReportScope) =>
    get<Transaction[]>('/transactions', reportParams(range, scope)),
}

export const accountsApi = {
  getAll: () => get<Account[]>('/accounts'),
}

export const authApi = {
  login: (email: string, password: string) =>
    post<{ token: string }>('/auth/login', { email, password }),
  register: (name: string, email: string, password: string) =>
    post<{ token: string }>('/auth/register', { name, email, password }),
}

export const ownerApi = {
  // ownerId sai do JWT no backend — nada é enviado no corpo.
  me: () => get<OwnerProfile>('/owner/me'),
  telegramInvite: () => post<TelegramInvite>('/owner/telegram-invite', {}),
}
