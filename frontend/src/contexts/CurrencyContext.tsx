import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { formatCurrency, type Currency } from '@/lib/utils'

interface CurrencyContextValue {
  currency: Currency
  setCurrency: (c: Currency) => void
  /** Formata um valor na moeda atualmente selecionada. */
  format: (value: number | string) => string
  valuesVisible: boolean
  toggleValuesVisible: () => void
}

const CurrencyContext = createContext<CurrencyContextValue | null>(null)

const STORAGE_KEY = 'money-flow:currency'

function initialCurrency(): Currency {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'EUR' || stored === 'USD' ? stored : 'BRL'
}

/** Moeda de exibição global do dashboard (persistida em localStorage). */
const VISIBILITY_KEY = 'money-flow:values-visible'

export function CurrencyProvider({ children }: { children: ReactNode }) {
  const [currency, setCurrencyState] = useState<Currency>(initialCurrency)
  const [valuesVisible, setValuesVisible] = useState<boolean>(
    () => localStorage.getItem(VISIBILITY_KEY) !== 'false',
  )

  const setCurrency = useCallback((c: Currency) => {
    setCurrencyState(c)
    localStorage.setItem(STORAGE_KEY, c)
  }, [])

  const toggleValuesVisible = useCallback(() => {
    setValuesVisible((prev) => {
      const next = !prev
      localStorage.setItem(VISIBILITY_KEY, String(next))
      return next
    })
  }, [])

  const value = useMemo<CurrencyContextValue>(
    () => ({
      currency,
      setCurrency,
      format: (v) => (valuesVisible ? formatCurrency(v, currency) : '••••••'),
      valuesVisible,
      toggleValuesVisible,
    }),
    [currency, setCurrency, valuesVisible, toggleValuesVisible],
  )

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>
}

export function useCurrency(): CurrencyContextValue {
  const ctx = useContext(CurrencyContext)
  if (!ctx) throw new Error('useCurrency precisa estar dentro de <CurrencyProvider>')
  return ctx
}
