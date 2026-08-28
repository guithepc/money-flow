import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Moedas de exibição suportadas no dashboard. */
export type Currency = 'BRL' | 'EUR' | 'USD'

export const CURRENCIES: { code: Currency; label: string; symbol: string }[] = [
  { code: 'BRL', label: 'Real', symbol: 'R$' },
  { code: 'EUR', label: 'Euro', symbol: '€' },
  { code: 'USD', label: 'Dólar', symbol: 'USD' },
]

const numberFmt = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * Formata um valor com a moeda escolhida. Só muda a disposição do símbolo
 * (não converte câmbio): BRL = `R$` à esquerda; EUR = `€` à direita;
 * USD = `USD` à direita.
 */
export function formatCurrency(value: number | string, currency: Currency = 'BRL'): string {
  const n = typeof value === 'string' ? Number(value) : value
  const num = numberFmt.format(Number.isFinite(n) ? n : 0)
  switch (currency) {
    case 'EUR':
      return `${num} €`
    case 'USD':
      return `${num} USD`
    case 'BRL':
    default:
      return `R$ ${num}`
  }
}

const iso = (d: Date) => d.toISOString().slice(0, 10)

/** Data ISO (yyyy-mm-dd) do primeiro e último dia do mês atual. */
export function currentMonthRange(): { startDate: string; endDate: string } {
  const now = new Date()
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  const last = new Date(now.getFullYear(), now.getMonth() + 1, 0)
  return { startDate: iso(first), endDate: iso(last) }
}

export type PresetKey = 'week' | 'last30' | 'month' | 'year'

export const PRESET_LABELS: Record<PresetKey, string> = {
  week: 'Esta semana',
  last30: 'Últimos 30 dias',
  month: 'Este mês',
  year: 'Este ano',
}

/** Range ISO (yyyy-mm-dd) para cada preset de período. */
export function presetRange(key: PresetKey): { startDate: string; endDate: string } {
  const now = new Date()
  const y = now.getFullYear()

  switch (key) {
    case 'month':
      return currentMonthRange()
    case 'year':
      return { startDate: iso(new Date(y, 0, 1)), endDate: iso(new Date(y, 11, 31)) }
    case 'last30': {
      const start = new Date(now)
      start.setDate(now.getDate() - 29)
      return { startDate: iso(start), endDate: iso(now) }
    }
    case 'week': {
      // Semana atual, segunda a domingo.
      const day = now.getDay() // 0 = domingo
      const diffToMonday = (day + 6) % 7
      const monday = new Date(now)
      monday.setDate(now.getDate() - diffToMonday)
      const sunday = new Date(monday)
      sunday.setDate(monday.getDate() + 6)
      return { startDate: iso(monday), endDate: iso(sunday) }
    }
  }
}
