import { beforeEach, describe, expect, it, vi } from 'vitest'
import { describeCapture, formatElapsed, pickRecordingMimeType, todayIso } from './capture'
import type { CaptureResponse } from './types'

function response(partial: Partial<CaptureResponse>): CaptureResponse {
  return { status: 'NOT_UNDERSTOOD', transcription: null, transaction: null, category: null, remainingToday: 10, ...partial }
}

describe('pickRecordingMimeType', () => {
  it('prefers mp4, the only format Safari on iPhone records', () => {
    expect(pickRecordingMimeType(() => true)).toBe('audio/mp4')
  })

  it('falls back to webm/opus on Chrome', () => {
    expect(pickRecordingMimeType((t) => t.startsWith('audio/webm'))).toBe('audio/webm;codecs=opus')
  })

  it('lets the browser choose when nothing is supported', () => {
    expect(pickRecordingMimeType(() => false)).toBe('')
  })
})

describe('describeCapture', () => {
  it('shows amount, category, account and the new balance of a registered expense', () => {
    const message = describeCapture(
      response({
        status: 'REGISTERED',
        transaction: { id: 9, operationType: 'EXPENSE', amount: 25, currency: 'BRL', account: 'Inter', category: 'Mercado', newBalance: 975 },
      }),
    )
    expect(message).toEqual({ tone: 'success', title: '−R$ 25,00 · Mercado · Inter', detail: 'Saldo R$ 975,00' })
  })

  it('marks income with a plus sign', () => {
    const message = describeCapture(
      response({
        status: 'REGISTERED',
        transaction: { id: 9, operationType: 'INCOME', amount: 300, currency: 'EUR', account: 'Revolut', category: 'Renda extra', newBalance: 1300 },
      }),
    )
    expect(message.title).toBe('+€ 300,00 · Renda extra · Revolut')
  })

  it('offers to create the suggested category', () => {
    const message = describeCapture(response({ status: 'CATEGORY_NOT_FOUND', category: { name: 'Saúde', type: 'EXPENSE' } }))
    expect(message.tone).toBe('warning')
    expect(message.detail).toBe('Quer criar “Saúde” (gasto) e registrar?')
  })

  it('repeats what it heard when it did not understand a voice note', () => {
    expect(describeCapture(response({ transcription: 'oi tudo bem' })).detail).toBe(
      'Ouvi: “oi tudo bem”. Tente algo como “gastei 30 no supermercado”.',
    )
  })

  it('explains the daily limit', () => {
    expect(describeCapture(response({ status: 'LIMIT_REACHED' })).title).toBe('Limite de hoje atingido.')
  })
})

describe('formatting', () => {
  it('formats elapsed recording time', () => {
    expect(formatElapsed(7.9)).toBe('0:07')
    expect(formatElapsed(65)).toBe('1:05')
  })

  it('uses the local date, not UTC', () => {
    expect(todayIso(new Date(2026, 10, 22, 23, 59))).toBe('2026-11-22')
  })
})

describe('captureApi', () => {
  const fetchMock = vi.fn<typeof fetch>()

  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('navigator', {})
    vi.stubGlobal('fetch', fetchMock)
  })

  async function loadApi() {
    vi.resetModules()
    const api = await import('./api')
    const auth = await import('./auth')
    auth.setToken('token')
    return api
  }

  it('uploads the voice note as multipart "audio" without forcing a Content-Type', async () => {
    const { captureApi } = await loadApi()
    fetchMock.mockResolvedValue(new Response(JSON.stringify(response({ status: 'REGISTERED' })), { status: 200 }))

    await captureApi.voice(new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/mp4' }))

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toMatch(/\/capture\/voice$/)
    const body = init?.body as FormData
    const audio = body.get('audio') as File
    expect(audio.name).toBe('voz.mp4')
    expect(audio.type).toBe('audio/mp4')
    const headers = (init?.headers ?? {}) as Record<string, string>
    expect(headers['Content-Type']).toBeUndefined()
    expect(headers.Authorization).toBe('Bearer token')
  })

  it('turns the daily limit into an ApiError 429', async () => {
    const { captureApi, ApiError } = await loadApi()
    fetchMock.mockResolvedValue(new Response(JSON.stringify(response({ status: 'LIMIT_REACHED' })), { status: 429 }))

    await expect(captureApi.text('gastei 25')).rejects.toMatchObject({ status: 429 })
    await expect(captureApi.text('gastei 25')).rejects.toBeInstanceOf(ApiError)
  })
})
