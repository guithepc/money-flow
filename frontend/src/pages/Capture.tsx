import { useEffect, useRef, useState, type FormEvent } from 'react'
import { LayoutGrid, Loader2, Mic, Pencil, SendHorizontal, Square, Undo2 } from 'lucide-react'
import { EditTransactionDialog } from '@/components/EditTransactionDialog'
import { LogoMark } from '@/components/Logo'
import { ApiError, captureApi, categoriesApi, transactionsApi } from '@/lib/api'
import {
  describeCapture,
  formatElapsed,
  pickRecordingMimeType,
  todayIso,
  type CaptureMessage,
} from '@/lib/capture'
import type { CaptureResponse, Transaction } from '@/lib/types'
import { useLanguage } from '@/lib/i18n'
import { cn } from '@/lib/utils'

/** Gravações mais longas param sozinhas: uma frase de gasto cabe folgada nisso. */
const MAX_RECORDING_SECONDS = 30

type Phase = 'idle' | 'recording' | 'sending'

interface Result {
  response: CaptureResponse
  /** A frase que gerou o resultado (digitada ou transcrita): reenviada depois de criar a categoria. */
  sentence: string
  undone?: boolean
}

const LIMIT_REACHED: CaptureResponse = {
  status: 'LIMIT_REACHED',
  transcription: null,
  transaction: null,
  category: null,
  remainingToday: 0,
}

const UI = {
  pt: {
    dashboard: 'Painel',
    eyebrow: 'Registrar',
    title: 'Fale o gasto',
    subtitle: '“gastei 25 no supermercado”. A categoria e a conta eu descubro.',
    record: 'Gravar',
    stop: 'Parar e enviar',
    recording: (time: string) => `Gravando ${time} · toque para enviar`,
    sending: 'Registrando…',
    tapToSpeak: 'Toque para falar',
    undone: 'Desfeito. O saldo voltou.',
    heard: (text: string) => `Ouvi: “${text}”`,
    undo: 'Desfazer',
    edit: 'Editar',
    createAndRegister: (name: string) => `Criar “${name}” e registrar`,
    placeholder: 'ou escreva: gastei 25 no supermercado',
    typeLabel: 'Escrever o gasto',
    send: 'Enviar',
    sendFailed: 'Não consegui registrar agora.',
    checkInternet: 'Confira a internet e tente de novo.',
    noRecorder: 'Este navegador não grava áudio aqui.',
    httpsOnly: 'O microfone só funciona em HTTPS. Escreva a frase abaixo.',
    micBlocked: 'Microfone bloqueado.',
    micBlockedHelp: 'Libere o microfone para este site nos ajustes do Safari, ou escreva a frase.',
    nothingHeard: 'Não captei nada.',
    nothingHeardHelp: 'Toque, fale o gasto e toque de novo para enviar.',
    undoFailed: 'Não consegui desfazer.',
    undoFailedHelp: 'Tente de novo ou apague pelo painel.',
    editMissing: 'Não achei essa transação.',
    editMissingHelp: 'Ela pode ter sido apagada pelo painel.',
    editFailed: 'Não consegui abrir a edição.',
    createFailed: (name: string) => `Não consegui criar “${name}”.`,
  },
  en: {
    dashboard: 'Dashboard',
    eyebrow: 'Register',
    title: 'Say the expense',
    subtitle: '“spent 25 at the supermarket”. I figure out the category and the account.',
    record: 'Record',
    stop: 'Stop and send',
    recording: (time: string) => `Recording ${time} · tap to send`,
    sending: 'Registering…',
    tapToSpeak: 'Tap to speak',
    undone: 'Undone. The balance is back.',
    heard: (text: string) => `I heard: “${text}”`,
    undo: 'Undo',
    edit: 'Edit',
    createAndRegister: (name: string) => `Create “${name}” and register`,
    placeholder: 'or type: spent 25 at the supermarket',
    typeLabel: 'Type the expense',
    send: 'Send',
    sendFailed: "Couldn't register it right now.",
    checkInternet: 'Check your connection and try again.',
    noRecorder: "This browser can't record audio here.",
    httpsOnly: 'The microphone only works over HTTPS. Type the sentence below.',
    micBlocked: 'Microphone blocked.',
    micBlockedHelp: 'Allow the microphone for this site in Safari settings, or type the sentence.',
    nothingHeard: "I didn't catch anything.",
    nothingHeardHelp: 'Tap, say the expense and tap again to send.',
    undoFailed: "Couldn't undo it.",
    undoFailedHelp: 'Try again or delete it from the dashboard.',
    editMissing: "Couldn't find that transaction.",
    editMissingHelp: 'It may have been deleted from the dashboard.',
    editFailed: "Couldn't open the editor.",
    createFailed: (name: string) => `Couldn't create “${name}”.`,
  },
} as const

/** Mensagem de falha que não veio do leitor (rede, microfone, servidor). */
function failure(title: string, detail?: string): CaptureMessage {
  return { tone: 'error', title, detail }
}

/**
 * Tela principal do PWA: toca no microfone, fala o gasto e ele é registrado na hora,
 * com Desfazer e Editar à mão. Também aceita a frase digitada.
 */
export function Capture({ onOpenDashboard }: { onOpenDashboard: () => void }) {
  const { lang } = useLanguage()
  const ui = UI[lang]
  const [phase, setPhase] = useState<Phase>('idle')
  const [elapsed, setElapsed] = useState(0)
  const [text, setText] = useState('')
  const [result, setResult] = useState<Result | null>(null)
  const [error, setError] = useState<CaptureMessage | null>(null)
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<Transaction | null>(null)

  const recorderRef = useRef<MediaRecorder | null>(null)
  const timerRef = useRef<number | null>(null)

  useEffect(
    () => () => {
      if (timerRef.current) window.clearInterval(timerRef.current)
      recorderRef.current?.stream.getTracks().forEach((track) => track.stop())
    },
    [],
  )

  async function submit(call: () => Promise<CaptureResponse>, typed?: string) {
    setPhase('sending')
    setError(null)
    try {
      const response = await call()
      setResult({ response, sentence: typed ?? response.transcription ?? '' })
      if (typed && response.status === 'REGISTERED') setText('')
    } catch (e) {
      setResult(null)
      if (e instanceof ApiError && e.status === 429) setResult({ response: LIMIT_REACHED, sentence: '' })
      else setError(failure(ui.sendFailed, ui.checkInternet))
    } finally {
      setPhase('idle')
    }
  }

  async function startRecording() {
    setError(null)
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError(failure(ui.noRecorder, ui.httpsOnly))
      return
    }
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      setError(failure(ui.micBlocked, ui.micBlockedHelp))
      return
    }
    const mimeType = pickRecordingMimeType((type) => MediaRecorder.isTypeSupported(type))
    const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
    const chunks: Blob[] = []
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    }
    recorder.onstop = () => {
      stream.getTracks().forEach((track) => track.stop())
      if (timerRef.current) window.clearInterval(timerRef.current)
      const audio = new Blob(chunks, { type: recorder.mimeType || mimeType || 'audio/webm' })
      if (audio.size === 0) {
        setPhase('idle')
        setError(failure(ui.nothingHeard, ui.nothingHeardHelp))
        return
      }
      void submit(() => captureApi.voice(audio))
    }
    recorderRef.current = recorder
    recorder.start()
    setResult(null)
    setElapsed(0)
    setPhase('recording')
    const startedAt = Date.now()
    timerRef.current = window.setInterval(() => {
      const seconds = (Date.now() - startedAt) / 1000
      setElapsed(seconds)
      if (seconds >= MAX_RECORDING_SECONDS) recorder.stop()
    }, 250)
  }

  function toggleRecording() {
    if (phase === 'recording') recorderRef.current?.stop()
    else if (phase === 'idle') void startRecording()
  }

  function sendText(event: FormEvent) {
    event.preventDefault()
    const sentence = text.trim()
    if (!sentence || phase !== 'idle') return
    void submit(() => captureApi.text(sentence), sentence)
  }

  async function undo() {
    const id = result?.response.transaction?.id
    if (!id) return
    setBusy(true)
    try {
      await transactionsApi.remove(id)
      setResult((current) => (current ? { ...current, undone: true } : current))
    } catch {
      setError(failure(ui.undoFailed, ui.undoFailedHelp))
    } finally {
      setBusy(false)
    }
  }

  async function openEdit() {
    const id = result?.response.transaction?.id
    if (!id) return
    setBusy(true)
    try {
      const today = todayIso()
      const found = (await transactionsApi.list({ startDate: today, endDate: today })).find((t) => t.id === id)
      if (found) setEditing(found)
      else setError(failure(ui.editMissing, ui.editMissingHelp))
    } catch {
      setError(failure(ui.editFailed))
    } finally {
      setBusy(false)
    }
  }

  async function createCategoryAndRegister() {
    const category = result?.response.category
    if (!category || !result?.sentence) return
    setBusy(true)
    try {
      await categoriesApi.create(category.name, category.type)
      await submit(() => captureApi.text(result.sentence), result.sentence)
    } catch {
      setError(failure(ui.createFailed(category.name)))
    } finally {
      setBusy(false)
    }
  }

  const message = error ?? (result ? describeCapture(result.response, lang) : null)
  const registered = result?.response.status === 'REGISTERED' ? result.response.transaction : null

  return (
    <main className="flex min-h-0 flex-1 flex-col overflow-y-auto bg-background">
      <header className="flex items-center justify-between px-5 pt-5">
        <span className="flex items-center gap-2.5">
          <LogoMark className="text-fg" />
          <span className="font-display text-lg font-semibold tracking-[-0.02em]">money flow</span>
        </span>
        <button
          type="button"
          onClick={onOpenDashboard}
          className="flex items-center gap-2 border border-edge px-3 py-2 text-xs text-fg-muted transition-colors hover:text-fg md:hidden"
        >
          <LayoutGrid className="size-4" strokeWidth={1.8} />
          {ui.dashboard}
        </button>
      </header>

      <section className="flex flex-1 flex-col items-center justify-center gap-7 px-5 py-8 text-center">
        <div className="flex flex-col items-center gap-2">
          <span className="eyebrow">{ui.eyebrow}</span>
          <h1 className="font-display text-4xl font-normal tracking-[-0.02em] text-fg">{ui.title}</h1>
          <p className="max-w-xs text-[15px] text-fg-muted">
            {ui.subtitle}
          </p>
        </div>

        <button
          type="button"
          onClick={toggleRecording}
          disabled={phase === 'sending' || busy}
          aria-label={phase === 'recording' ? ui.stop : ui.record}
          className={cn(
            'relative flex size-36 items-center justify-center rounded-full transition-transform duration-150 active:scale-95 disabled:opacity-60',
            phase === 'recording' ? 'bg-negative text-background' : 'bg-brand text-background',
          )}
        >
          {phase === 'recording' && (
            <span aria-hidden className="absolute inset-0 animate-ping rounded-full bg-negative/40" />
          )}
          {phase === 'sending' ? (
            <Loader2 className="size-12 animate-spin" strokeWidth={1.8} />
          ) : phase === 'recording' ? (
            <Square className="size-11" strokeWidth={1.8} fill="currentColor" />
          ) : (
            <Mic className="size-14" strokeWidth={1.8} />
          )}
        </button>

        <p className="font-mono text-xs text-fg-subtle" aria-live="polite">
          {phase === 'recording'
            ? ui.recording(formatElapsed(elapsed))
            : phase === 'sending'
              ? ui.sending
              : ui.tapToSpeak}
        </p>

        {message && (
          <div
            role="status"
            className={cn(
              'w-full max-w-sm border bg-surface p-4 text-left',
              message.tone === 'success' && !result?.undone && 'border-data/50',
              message.tone === 'warning' && 'border-brand/40',
              message.tone === 'error' && 'border-negative/50',
              result?.undone && 'border-edge',
            )}
          >
            {result?.undone ? (
              <p className="text-sm text-fg">{ui.undone}</p>
            ) : (
              <>
                <p className="text-[15px] font-medium text-fg">{message.title}</p>
                {message.detail && <p className="mt-1 text-sm text-fg-muted">{message.detail}</p>}
                {registered && result?.response.transcription && (
                  <p className="mt-2 font-mono text-xs text-fg-subtle">{ui.heard(result.response.transcription)}</p>
                )}
              </>
            )}

            {registered && !result?.undone && (
              <div className="mt-4 flex gap-2">
                <button
                  type="button"
                  onClick={undo}
                  disabled={busy}
                  className="flex flex-1 items-center justify-center gap-2 border border-edge px-3 py-2.5 text-sm text-fg transition-colors hover:border-negative/60 disabled:opacity-60"
                >
                  <Undo2 className="size-4" strokeWidth={1.8} /> {ui.undo}
                </button>
                <button
                  type="button"
                  onClick={openEdit}
                  disabled={busy}
                  className="flex flex-1 items-center justify-center gap-2 border border-edge px-3 py-2.5 text-sm text-fg transition-colors hover:border-brand/60 disabled:opacity-60"
                >
                  <Pencil className="size-4" strokeWidth={1.8} /> {ui.edit}
                </button>
              </div>
            )}

            {result?.response.status === 'CATEGORY_NOT_FOUND' && !error && (
              <button
                type="button"
                onClick={createCategoryAndRegister}
                disabled={busy || phase !== 'idle'}
                className="mt-4 w-full bg-brand px-3 py-2.5 text-sm font-medium text-background disabled:opacity-60"
              >
                {ui.createAndRegister(result.response.category?.name ?? '')}
              </button>
            )}
          </div>
        )}
      </section>

      <form
        onSubmit={sendText}
        className="sticky bottom-0 flex gap-2 border-t border-edge bg-background px-4 pt-3 pb-[max(12px,env(safe-area-inset-bottom))]"
      >
        <input
          value={text}
          onChange={(event) => setText(event.target.value)}
          maxLength={300}
          placeholder={ui.placeholder}
          aria-label={ui.typeLabel}
          className="min-w-0 flex-1 border border-edge bg-surface-raised px-3.5 py-3 text-base text-fg outline-none placeholder:text-fg-subtle focus:border-brand/60"
        />
        <button
          type="submit"
          disabled={!text.trim() || phase !== 'idle'}
          aria-label={ui.send}
          className="flex w-12 items-center justify-center bg-brand text-background disabled:opacity-40"
        >
          <SendHorizontal className="size-5" strokeWidth={1.8} />
        </button>
      </form>

      {editing && (
        <EditTransactionDialog
          transaction={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            setResult(null)
            setError(null)
          }}
        />
      )}
    </main>
  )
}
