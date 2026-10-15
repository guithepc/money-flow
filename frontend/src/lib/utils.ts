import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/** Moedas suportadas (espelha o enum `currency` do backend). */
export type Currency = 'BRL' | 'EUR' | 'USD'

export const CURRENCIES: { code: Currency; label: string; symbol: string }[] = [
  { code: 'BRL', label: 'Real', symbol: 'R$' },
  { code: 'EUR', label: 'Euro', symbol: '€' },
  { code: 'USD', label: 'Dólar', symbol: 'US$' },
]

const numberFmt = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/**
 * Formata um valor na moeda da conta — não converte câmbio. Mesma regra das
 * respostas do bot (`MoneyFormat.format` no backend): símbolo à esquerda +
 * número em notação pt-BR; o sinal vem antes do símbolo.
 * `R$ 1.250,90` · `€ 681,03` · `US$ 10,00` · `-R$ 12,00`
 */
export function formatCurrency(value: number | string, currency: Currency = 'BRL'): string {
  const n = typeof value === 'string' ? Number(value) : value
  const safe = Number.isFinite(n) ? n : 0
  const symbol = CURRENCIES.find((c) => c.code === currency)?.symbol ?? currency
  const sign = safe < 0 && numberFmt.format(Math.abs(safe)) !== '0,00' ? '-' : ''
  return `${sign}${symbol} ${numberFmt.format(Math.abs(safe))}`
}

/** Valor em notação pt-BR pro input de edição, sem símbolo: `1250.9` → `1.250,90`. */
export function formatAmountInput(value: number): string {
  return numberFmt.format(value)
}

/**
 * Lê o valor digitado no formato brasileiro (mesma regra do bot): ponto só como
 * separador de milhar, vírgula como decimal, até 2 casas. `null` se inválido.
 * `1.250,90` · `1250,90` · `1250` → ok · `25.90` · `1,999` · `abc` → null
 */
export function parseAmountInput(raw: string): number | null {
  const value = raw.trim()
  if (!/^(\d{1,3}(\.\d{3})+|\d+)(,\d{1,2})?$/.test(value)) return null
  const n = Number(value.replace(/\./g, '').replace(',', '.'))
  return Number.isFinite(n) ? n : null
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
