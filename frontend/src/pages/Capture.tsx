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

/** Mensagem de falha que não veio do leitor (rede, microfone, servidor). */
function failure(title: string, detail?: string): CaptureMessage {
  return { tone: 'error', title, detail }
}

/**
 * Tela principal do PWA: toca no microfone, fala o gasto e ele é registrado na hora,
 * com Desfazer e Editar à mão. Também aceita a frase digitada.
 */
export function Capture({ onOpenDashboard }: { onOpenDashboard: () => void }) {
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
      else setError(failure('Não consegui registrar agora.', 'Confira a internet e tente de novo.'))
    } finally {
      setPhase('idle')
    }
  }

  async function startRecording() {
    setError(null)
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError(failure('Este navegador não grava áudio aqui.', 'O microfone só funciona em HTTPS. Escreva a frase abaixo.'))
      return
    }
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      setError(failure('Microfone bloqueado.', 'Libere o microfone para este site nos ajustes do Safari, ou escreva a frase.'))
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
        setError(failure('Não captei nada.', 'Toque, fale o gasto e toque de novo para enviar.'))
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
      setError(failure('Não consegui desfazer.', 'Tente de novo ou apague pelo painel.'))
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
      else setError(failure('Não achei essa transação.', 'Ela pode ter sido apagada pelo painel.'))
    } catch {
      setError(failure('Não consegui abrir a edição.'))
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
      setError(failure(`Não consegui criar “${category.name}”.`))
    } finally {
      setBusy(false)
    }
  }

  const message = error ?? (result ? describeCapture(result.response) : null)
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
          Painel
        </button>
      </header>

      <section className="flex flex-1 flex-col items-center justify-center gap-7 px-5 py-8 text-center">
        <div className="flex flex-col items-center gap-2">
          <span className="eyebrow">Registrar</span>
          <h1 className="font-display text-4xl font-normal tracking-[-0.02em] text-fg">Fale o gasto</h1>
          <p className="max-w-xs text-[15px] text-fg-muted">
            “gastei 25 no supermercado”. A categoria e a conta eu descubro.
          </p>
        </div>

        <button
          type="button"
          onClick={toggleRecording}
          disabled={phase === 'sending' || busy}
          aria-label={phase === 'recording' ? 'Parar e enviar' : 'Gravar'}
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
            ? `Gravando ${formatElapsed(elapsed)} · toque para enviar`
            : phase === 'sending'
              ? 'Registrando…'
              : 'Toque para falar'}
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
              <p className="text-sm text-fg">Desfeito. O saldo voltou.</p>
            ) : (
              <>
                <p className="text-[15px] font-medium text-fg">{message.title}</p>
                {message.detail && <p className="mt-1 text-sm text-fg-muted">{message.detail}</p>}
                {registered && result?.response.transcription && (
                  <p className="mt-2 font-mono text-xs text-fg-subtle">Ouvi: “{result.response.transcription}”</p>
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
                  <Undo2 className="size-4" strokeWidth={1.8} /> Desfazer
                </button>
                <button
                  type="button"
                  onClick={openEdit}
                  disabled={busy}
                  className="flex flex-1 items-center justify-center gap-2 border border-edge px-3 py-2.5 text-sm text-fg transition-colors hover:border-brand/60 disabled:opacity-60"
                >
                  <Pencil className="size-4" strokeWidth={1.8} /> Editar
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
                Criar “{result.response.category?.name}” e registrar
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
          placeholder="ou escreva: gastei 25 no supermercado"
          aria-label="Escrever o gasto"
          className="min-w-0 flex-1 border border-edge bg-surface-raised px-3.5 py-3 text-base text-fg outline-none placeholder:text-fg-subtle focus:border-brand/60"
        />
        <button
          type="submit"
          disabled={!text.trim() || phase !== 'idle'}
          aria-label="Enviar"
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
