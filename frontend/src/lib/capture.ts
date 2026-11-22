import type { CaptureResponse } from './types'
import type { Lang } from './i18n'
import { formatCurrency } from './utils'

/**
 * Formato de gravação: Safari no iPhone só grava audio/mp4; Chrome e Firefox, webm/opus.
 * Vazio = deixa o navegador escolher.
 */
export function pickRecordingMimeType(isSupported: (mimeType: string) => boolean): string {
  for (const candidate of ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm']) {
    if (isSupported(candidate)) return candidate
  }
  return ''
}

export type CaptureTone = 'success' | 'warning' | 'error'

export interface CaptureMessage {
  tone: CaptureTone
  title: string
  detail?: string
}

const TEXTS = {
  pt: {
    example: 'Tente algo como “gastei 30 no supermercado”.',
    heard: (t: string) => `Ouvi: “${t}”. `,
    balance: 'Saldo',
    kind: { EXPENSE: 'gasto', INCOME: 'entrada' },
    notFoundTitle: 'Não achei uma categoria para isso.',
    notFoundDetail: (name: string, kind: string) => `Quer criar “${name}” (${kind}) e registrar?`,
    mismatchTitle: (name: string, kind: string) => `“${name}” é uma categoria de ${kind}.`,
    mismatchDetail: 'Diga de novo com outra categoria.',
    noAccountTitle: 'Você ainda não tem conta.',
    noAccountDetail: 'Crie uma conta e tente de novo.',
    limitTitle: 'Limite de hoje atingido.',
    limitDetail: 'Amanhã libera de novo.',
    notUnderstood: 'Não entendi.',
  },
  en: {
    example: 'Try something like “spent 30 at the supermarket”.',
    heard: (t: string) => `I heard: “${t}”. `,
    balance: 'Balance',
    kind: { EXPENSE: 'expense', INCOME: 'income' },
    notFoundTitle: "I couldn't find a category for that.",
    notFoundDetail: (name: string, kind: string) => `Create “${name}” (${kind}) and register it?`,
    mismatchTitle: (name: string, kind: string) => `“${name}” is an ${kind} category.`,
    mismatchDetail: 'Say it again with another category.',
    noAccountTitle: "You don't have an account yet.",
    noAccountDetail: 'Create an account and try again.',
    limitTitle: "Today's limit reached.",
    limitDetail: 'It resets tomorrow.',
    notUnderstood: "I didn't get that.",
  },
} as const

/** Texto do cartão de resultado. O backend manda só fatos; as palavras são daqui. */
export function describeCapture(response: CaptureResponse, lang: Lang = 'pt'): CaptureMessage {
  const t = TEXTS[lang]
  const heard = response.transcription ? t.heard(response.transcription) : ''
  const kind = t.kind[response.category?.type ?? 'EXPENSE']
  switch (response.status) {
    case 'REGISTERED': {
      const tx = response.transaction!
      const sign = tx.operationType === 'INCOME' ? '+' : '−'
      return {
        tone: 'success',
        title: `${sign}${formatCurrency(tx.amount, tx.currency)} · ${tx.category} · ${tx.account}`,
        detail: `${t.balance} ${formatCurrency(tx.newBalance, tx.currency)}`,
      }
    }
    case 'CATEGORY_NOT_FOUND':
      return { tone: 'warning', title: t.notFoundTitle, detail: `${heard}${t.notFoundDetail(response.category?.name ?? '', kind)}` }
    case 'CATEGORY_TYPE_MISMATCH':
      return { tone: 'warning', title: t.mismatchTitle(response.category?.name ?? '', kind), detail: `${heard}${t.mismatchDetail}` }
    case 'NO_ACCOUNT':
      return { tone: 'warning', title: t.noAccountTitle, detail: t.noAccountDetail }
    case 'LIMIT_REACHED':
      return { tone: 'warning', title: t.limitTitle, detail: t.limitDetail }
    case 'NOT_UNDERSTOOD':
    default:
      return { tone: 'error', title: t.notUnderstood, detail: `${heard}${t.example}` }
  }
}

/** "0:07" */
export function formatElapsed(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`
}

/** Data local (não UTC) no formato da API: perto da meia-noite, UTC já seria amanhã. */
export function todayIso(now: Date = new Date()): string {
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
