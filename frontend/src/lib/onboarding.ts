import type { Lang } from './i18n'
import type { Currency } from './types'

/**
 * Saldo digitado na criação de conta: aceita negativo (cartão de crédito), o formato brasileiro
 * (`1.250,90`) e o decimal com ponto que quem escreve em inglês usa (`1250.90`). `null` se inválido.
 */
export function parseBalanceInput(raw: string): number | null {
  const value = raw.trim().replace(/\s/g, '')
  const match = /^(-?)(.+)$/.exec(value)
  if (!match || !match[2]) return null
  const [, sign, body] = match
  let normalized: string | null = null
  if (/^(\d{1,3}(\.\d{3})+|\d+)(,\d{1,2})?$/.test(body)) normalized = body.replace(/\./g, '').replace(',', '.')
  else if (/^(\d{1,3}(,\d{3})+|\d+)(\.\d{1,2})?$/.test(body)) normalized = body.replace(/,/g, '')
  if (normalized === null) return null
  const n = Number(sign + normalized)
  return Number.isFinite(n) ? n : null
}

/** Moeda sugerida na primeira conta: real para quem escolheu português, euro para inglês. */
export function defaultCurrency(lang: Lang): Currency {
  return lang === 'pt' ? 'BRL' : 'EUR'
}
