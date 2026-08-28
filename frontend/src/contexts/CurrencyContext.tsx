import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { formatCurrency, type Currency } from '@/lib/utils'

interface CurrencyContextValue {
  currency: Currency
  setCurrency: (c: Currency) => void
  /** Formata um valor na moeda atualmente selecionada. */
  format: (value: number | string) => string
}

const CurrencyContext = createContext<CurrencyContextValue | null>(null)

const STORAGE_KEY = 'money-flow:currency'

function initialCurrency(): Currency {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'EUR' || stored === 'USD' ? stored : 'BRL'
}

/** Moeda de exibição global do dashboard (persistida em localStorage). */
export function CurrencyProvider({ children }: { children: ReactNode }) {
  const [currency, setCurrencyState] = useState<Currency>(initialCurrency)

  const setCurrency = useCallback((c: Currency) => {
    setCurrencyState(c)
    localStorage.setItem(STORAGE_KEY, c)
  }, [])

  const value = useMemo<CurrencyContextValue>(
    () => ({
      currency,
      setCurrency,
      format: (v) => formatCurrency(v, currency),
    }),
    [currency, setCurrency],
  )

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>
}

export function useCurrency(): CurrencyContextValue {
  const ctx = useContext(CurrencyContext)
  if (!ctx) throw new Error('useCurrency precisa estar dentro de <CurrencyProvider>')
  return ctx
}
