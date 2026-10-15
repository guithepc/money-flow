import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { formatCurrency, type Currency } from '@/lib/utils'
import type { Account } from '@/lib/types'
import { useAccounts } from '@/hooks/useAccounts'

interface CurrencyContextValue {
  /** Contas do usuário (carregadas uma vez e compartilhadas entre as páginas). */
  accounts: Account[]
  accountsLoading: boolean
  accountsError: string | null
  /** Rebusca as contas em segundo plano (saldos frescos ao abrir uma página). */
  refreshAccounts: () => void
  /** Moedas em que o usuário tem contas, na ordem da primeira conta de cada uma. */
  currencies: Currency[]
  /**
   * Moeda filtrada no dashboard. Não há câmbio: só contas dessa moeda entram
   * em saldo, KPIs, gráficos e categorias. Padrão = última escolha salva (se o
   * usuário ainda tiver conta nela) ou a moeda da primeira conta.
   */
  currency: Currency
  setCurrency: (c: Currency) => void
  /** Formata um valor na moeda selecionada (ou na moeda informada, ex.: a da conta). */
  format: (value: number | string, currency?: Currency) => string
  valuesVisible: boolean
  toggleValuesVisible: () => void
}

const CurrencyContext = createContext<CurrencyContextValue | null>(null)

const VISIBILITY_KEY = 'money-flow:values-visible'
const CURRENCY_KEY = 'money-flow:currency'

function readStoredCurrency(): Currency | null {
  try {
    const stored = localStorage.getItem(CURRENCY_KEY)
    return stored === 'BRL' || stored === 'EUR' || stored === 'USD' ? stored : null
  } catch {
    return null
  }
}

function readVisibility(): boolean {
  try {
    return localStorage.getItem(VISIBILITY_KEY) !== 'false'
  } catch {
    return true
  }
}

export function CurrencyProvider({ children }: { children: ReactNode }) {
  const { data: accountList, loading: accountsLoading, error: accountsError, refresh } = useAccounts()
  // Última escolha do usuário (localStorage). Só vale se ele ainda tiver conta
  // nessa moeda; senão (ou sem escolha) cai na moeda da primeira conta.
  const [chosen, setChosen] = useState<Currency | null>(readStoredCurrency)
  const [valuesVisible, setValuesVisible] = useState<boolean>(readVisibility)

  const currencies = useMemo<Currency[]>(() => {
    const seen: Currency[] = []
    for (const a of accountList) if (!seen.includes(a.currency)) seen.push(a.currency)
    return seen
  }, [accountList])

  const currency: Currency =
    chosen && currencies.includes(chosen) ? chosen : (currencies[0] ?? 'BRL')

  const setCurrency = useCallback((c: Currency) => {
    setChosen(c)
    try {
      localStorage.setItem(CURRENCY_KEY, c)
    } catch {
      // armazenamento indisponível — mantém só em memória
    }
  }, [])

  const toggleValuesVisible = useCallback(() => {
    setValuesVisible((prev) => {
      const next = !prev
      try {
        localStorage.setItem(VISIBILITY_KEY, String(next))
      } catch {
        // armazenamento indisponível — mantém só em memória
      }
      return next
    })
  }, [])

  const value = useMemo<CurrencyContextValue>(
    () => ({
      accounts: accountList,
      accountsLoading,
      accountsError,
      refreshAccounts: refresh,
      currencies,
      currency,
      setCurrency,
      format: (v, c) => (valuesVisible ? formatCurrency(v, c ?? currency) : '••••••'),
      valuesVisible,
      toggleValuesVisible,
    }),
    [
      accountList,
      accountsLoading,
      accountsError,
      refresh,
      currencies,
      currency,
      setCurrency,
      valuesVisible,
      toggleValuesVisible,
    ],
  )

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>
}

export function useCurrency(): CurrencyContextValue {
  const ctx = useContext(CurrencyContext)
  if (!ctx) throw new Error('useCurrency precisa estar dentro de <CurrencyProvider>')
  return ctx
}
