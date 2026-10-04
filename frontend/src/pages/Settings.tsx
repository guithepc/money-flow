import { useState } from 'react'
import { Send, ExternalLink, Clock } from 'lucide-react'
import { ownerApi } from '@/lib/api'

export function Settings() {
  const [link, setLink] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleGenerate() {
    setLoading(true)
    setError(null)
    try {
      const { link } = await ownerApi.telegramInvite()
      setLink(link)
    } catch {
      setError('Não foi possível gerar o link. Tente novamente.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="flex-1 overflow-y-auto px-8 py-8">
      <header className="mb-8 flex flex-col gap-1">
        <span className="eyebrow text-brand">Preferências</span>
        <h1 className="font-serif text-4xl font-semibold tracking-tight text-fg">
          Configurações
        </h1>
        <p className="text-sm text-fg-muted">
          Conecte seus serviços à sua conta Money Flow.
        </p>
      </header>

      <section className="flex max-w-xl flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
        <div className="flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-xl bg-brand-soft text-brand">
            <Send className="size-5" strokeWidth={1.8} />
          </span>
          <div className="flex flex-col gap-0.5">
            <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
              Telegram
            </h2>
            <p className="text-sm text-fg-muted">
              Vincule o bot do Telegram para registrar transações por mensagem ou voz.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleGenerate}
          disabled={loading}
          className="flex w-fit items-center gap-2 rounded-xl bg-brand px-5 py-3 text-sm font-medium text-background transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          <Send className="size-4" strokeWidth={1.8} />
          {loading ? 'Gerando link...' : 'Vincular Telegram'}
        </button>

        {error && (
          <div className="rounded-xl border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
            {error}
          </div>
        )}

        {link && (
          <div className="flex flex-col gap-3 rounded-xl border border-edge bg-surface-raised px-5 py-4">
            <a
              href={link}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 break-all text-sm font-medium text-data hover:underline"
            >
              <ExternalLink className="size-4 shrink-0" strokeWidth={1.8} />
              {link}
            </a>
            <p className="flex items-center gap-2 text-xs text-fg-muted">
              <Clock className="size-3.5 shrink-0" strokeWidth={1.8} />
              Você tem 1 minuto para abrir o link no Telegram antes que ele expire.
            </p>
          </div>
        )}
      </section>
    </main>
  )
}
