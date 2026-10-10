import { useState } from 'react'
import { ArrowRight, Send, ExternalLink, Clock } from 'lucide-react'
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
    <main className="bg-page-grid flex-1 overflow-y-auto px-10 pt-9 pb-10">
      <header className="mb-7 flex flex-col gap-2.5">
        <span className="eyebrow">Preferências</span>
        <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em] text-fg">
          Configurações
        </h1>
        <p className="text-[15px] text-fg-muted">Conecte seus serviços à sua conta Money Flow.</p>
      </header>

      <section className="flex max-w-xl flex-col gap-6 border border-edge bg-surface p-6">
        <div className="flex items-start gap-4">
          <span className="flex size-10 shrink-0 items-center justify-center border border-edge">
            <Send className="size-5 text-fg-muted" strokeWidth={1.8} />
          </span>
          <div className="flex flex-col gap-1">
            <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">Telegram</h2>
            <p className="text-sm text-fg-muted">
              Vincule o bot do Telegram para registrar transações por mensagem ou voz.
            </p>
          </div>
        </div>

        <button
          type="button"
          onClick={handleGenerate}
          disabled={loading}
          className="flex w-fit items-center gap-2.5 bg-brand px-5 py-3.5 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50"
        >
          {loading ? 'Gerando link…' : 'Vincular Telegram'}
          <ArrowRight className="size-4" strokeWidth={2.2} />
        </button>

        {error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
            {error}
          </div>
        )}

        {link && (
          <div className="flex flex-col gap-3 border border-edge bg-surface-raised px-5 py-4">
            <a
              href={link}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 break-all text-sm font-medium text-data hover:underline"
            >
              <ExternalLink className="size-4 shrink-0" strokeWidth={1.8} />
              {link}
            </a>
            <p className="flex items-center gap-2 font-mono text-xs text-fg-subtle">
              <Clock className="size-3.5 shrink-0" strokeWidth={1.8} />
              Você tem 1 minuto para abrir o link no Telegram antes que ele expire.
            </p>
          </div>
        )}
      </section>
    </main>
  )
}
