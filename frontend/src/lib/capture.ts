import type { CaptureResponse } from './types'
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

const EXAMPLE = 'Tente algo como “gastei 30 no supermercado”.'

/** Texto do cartão de resultado. O backend manda só fatos; as palavras são daqui. */
export function describeCapture(response: CaptureResponse): CaptureMessage {
  const heard = response.transcription ? `Ouvi: “${response.transcription}”. ` : ''
  switch (response.status) {
    case 'REGISTERED': {
      const t = response.transaction!
      const sign = t.operationType === 'INCOME' ? '+' : '−'
      return {
        tone: 'success',
        title: `${sign}${formatCurrency(t.amount, t.currency)} · ${t.category} · ${t.account}`,
        detail: `Saldo ${formatCurrency(t.newBalance, t.currency)}`,
      }
    }
    case 'CATEGORY_NOT_FOUND': {
      const kind = response.category?.type === 'INCOME' ? 'entrada' : 'gasto'
      return {
        tone: 'warning',
        title: 'Não achei uma categoria para isso.',
        detail: `${heard}Quer criar “${response.category?.name}” (${kind}) e registrar?`,
      }
    }
    case 'CATEGORY_TYPE_MISMATCH': {
      const kind = response.category?.type === 'INCOME' ? 'entrada' : 'gasto'
      return {
        tone: 'warning',
        title: `“${response.category?.name}” é uma categoria de ${kind}.`,
        detail: `${heard}Diga de novo com outra categoria.`,
      }
    }
    case 'NO_ACCOUNT':
      return { tone: 'warning', title: 'Você ainda não tem conta.', detail: 'Crie uma conta e tente de novo.' }
    case 'LIMIT_REACHED':
      return { tone: 'warning', title: 'Limite de hoje atingido.', detail: 'Amanhã libera de novo.' }
    case 'NOT_UNDERSTOOD':
    default:
      return { tone: 'error', title: 'Não entendi.', detail: `${heard}${EXAMPLE}` }
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
