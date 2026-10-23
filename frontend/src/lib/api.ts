import type {
  Account,
  Currency,
  AmountAndCategory,
  Category,
  DateRange,
  MonthlyIncomeExpense,
  OwnerProfile,
  TelegramInvite,
  TimeSeriesPoint,
  TotalAmount,
  Transaction,
  TransactionUpdate,
} from './types'
import { clearToken, getToken, setToken } from './auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

/** Disparado quando a sessão acabou de verdade: o refresh respondeu 401. */
export const UNAUTHORIZED_EVENT = 'auth:unauthorized'

/** Falha de rede (offline, troca de Wi-Fi, reset do proxy): nunca encerra a sessão. */
export class NetworkError extends Error {}

/** Erro de API com o status HTTP e o código do corpo `{ error }`, quando houver. */
export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function authHeaders(token: string | null): HeadersInit {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function signalUnauthorized(): void {
  clearToken()
  window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
}

async function authFetch(path: string, body?: unknown): Promise<Response> {
  try {
    // credentials: o cookie HttpOnly de refresh só vai e volta nos endpoints /auth.
    return await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers:
        body === undefined
          ? { Accept: 'application/json' }
          : { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new NetworkError(`Network error — ${path}`)
  }
}

async function doRefresh(): Promise<boolean> {
  const res = await authFetch('/auth/refresh')
  if (res.status === 401 || res.status === 403) {
    clearToken()
    return false
  }
  if (!res.ok) {
    // 429 / 5xx / deploy reiniciando: a sessão pode continuar válida, só não deu agora.
    throw new Error(`${res.status} ${res.statusText} — /auth/refresh`)
  }
  const { token } = (await res.json()) as { token: string }
  setToken(token)
  return true
}

let refreshInFlight: Promise<boolean> | null = null

/**
 * Troca o cookie de refresh por um novo access token. Chamadas simultâneas
 * compartilham a mesma requisição, e entre abas o Web Lock serializa os
 * refreshes: cada aba envia o cookie já rotacionado pela anterior, sem
 * reaproveitar um token antigo (o que revogaria a sessão toda).
 *
 * Resolve `false` só quando o servidor recusa a sessão (401); falha de rede
 * ou erro do servidor rejeita a promise e a sessão continua.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    const run = () => doRefresh()
    const locked =
      typeof navigator !== 'undefined' && navigator.locks
        ? navigator.locks.request('money-flow:refresh', run)
        : run()
    refreshInFlight = locked.finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

interface RequestOptions {
  params?: Record<string, string>
  body?: unknown
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const url = new URL(`${BASE_URL}${path}`)
  for (const [key, value] of Object.entries(options.params ?? {})) {
    url.searchParams.set(key, value)
  }

  const call = async (token: string | null): Promise<Response> => {
    try {
      return await fetch(url, {
        method,
        headers: {
          Accept: 'application/json',
          ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...authHeaders(token),
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      })
    } catch {
      // Sem resposta do servidor não dá pra saber se a sessão acabou: mantém o usuário logado.
      throw new NetworkError(`Network error — ${path}`)
    }
  }

  const sentWith = getToken()
  let res = await call(sentWith)

  if (res.status === 401) {
    // Outra chamada pode já ter renovado o token enquanto esta estava em voo.
    const current = getToken()
    const renewed = current !== null && current !== sentWith ? true : await refreshSession()
    if (!renewed) {
      signalUnauthorized()
      throw new ApiError(401, `401 Unauthorized — ${path}`)
    }
    res = await call(getToken())
  }

  if (!res.ok) {
    throw new ApiError(res.status, `${res.status} ${res.statusText} — ${path}`)
  }
  // 204 No Content: não há corpo para ler.
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

const get = <T>(path: string, params: Record<string, string> = {}) => request<T>('GET', path, { params })
const post = <T>(path: string, body: unknown) => request<T>('POST', path, { body })
const put = <T>(path: string, body: unknown) => request<T>('PUT', path, { body })
const del = <T>(path: string, body: unknown) => request<T>('DELETE', path, { body })

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

  /** Edita descrição, categoria e valor; o backend ajusta o saldo da conta pela diferença. */
  update: (id: number, body: TransactionUpdate) => put<Transaction>(`/transactions/${id}`, body),

  /** Exclui a transação; o backend reverte o saldo da conta (gasto devolve, entrada tira). */
  remove: (id: number) => del<void>(`/transactions/${id}`, undefined),
}

export const categoriesApi = {
  list: () => get<Category[]>('/categories'),
}

export interface AccountDeletionSummary {
  deletedTransactions: number
  deletedRecurringPayments: number
}

export const accountsApi = {
  getAll: () => get<Account[]>('/accounts'),

  /** Renomeia a conta. Lança ApiError 409 se já existir outra conta com esse nome. */
  rename: (id: number, description: string) => put<Account>(`/accounts/${id}`, { description }),

  /** Exclui a conta e, em cascata, todas as suas transações e recorrentes. */
  remove: (id: number) => del<AccountDeletionSummary>(`/accounts/${id}`, undefined),
}

/**
 * POST para os endpoints públicos de `/auth`: não manda Authorization, não
 * dispara logout no 401 e lança `ApiError` com o `code` do corpo. Respostas
 * sem corpo (202/204) retornam `undefined` em vez de tentar parsear JSON.
 */
async function authPost<T = void>(path: string, body: unknown): Promise<T> {
  const res = await authFetch(path, body)
  if (!res.ok) {
    let code: string | undefined
    try {
      const data = (await res.json()) as { error?: unknown }
      if (typeof data.error === 'string') code = data.error
    } catch {
      // Corpo vazio ou não-JSON: fica só o status.
    }
    throw new ApiError(res.status, `${res.status} ${res.statusText} — ${path}`, code)
  }
  if (res.status === 202 || res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const authApi = {
  // Além do access token, a resposta grava o cookie HttpOnly de refresh.
  login: (email: string, password: string) =>
    authPost<{ token: string }>('/auth/login', { email, password }),
  // authPost lança ApiError com o código WEAK_PASSWORD do backend.
  /** acceptedTermsVersion: versão dos termos aceita no checkbox do cadastro (obrigatória no backend). */
  register: (name: string, email: string, password: string, acceptedTermsVersion: string) =>
    authPost<{ token: string }>('/auth/register', { name, email, password, acceptedTermsVersion }),
  /** Revoga a sessão no servidor e apaga o cookie de refresh. */
  logout: async (): Promise<void> => {
    await authFetch('/auth/logout')
  },

  /** Sempre 202 (não revela se o email existe); 429 quando passa do limite. */
  requestPasswordReset: (email: string) => authPost('/auth/password-reset/request', { email }),

  /** 204 no sucesso; 400 com TOKEN_INVALID | TOKEN_EXPIRED | TOKEN_USED | WEAK_PASSWORD. */
  confirmPasswordReset: (token: string, newPassword: string) =>
    authPost('/auth/password-reset/confirm', { token, newPassword }),
}

export const ownerApi = {
  // ownerId sai do JWT no backend — nada é enviado no corpo.
  me: () => get<OwnerProfile>('/owner/me'),
  telegramInvite: () => post<TelegramInvite>('/owner/telegram-invite', {}),
  acceptTerms: (termsVersion: string) => post<void>('/owner/terms-acceptance', { termsVersion }),
  /** Tudo que o backend guarda do usuário (perfil, contas, categorias, transações), em JSON. */
  exportData: () => get<unknown>('/owner/export'),
  /**
   * Exclui a conta e todos os dados (as sessões vão junto, em cascata). 403 = senha errada.
   * Depois disso, chame `authApi.logout()` para apagar o cookie de refresh também em dev cross-origin.
   */
  deleteAccount: (password: string) => del<void>('/owner', { password }),
}
