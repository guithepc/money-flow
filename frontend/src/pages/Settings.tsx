import { useEffect, useState, type FormEvent } from 'react'
import { ArrowRight, Send, ExternalLink, Clock, Check, Download, Trash2 } from 'lucide-react'
import { ApiError, authApi, ownerApi } from '@/lib/api'
import { clearToken } from '@/lib/auth'
import { LANDING_URL, LEGAL_PATHS } from '@/lib/legal'

const outlineButton =
  'flex w-fit items-center gap-2.5 border border-edge px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-fg-muted transition-colors duration-150 hover:border-fg-subtle hover:text-fg disabled:opacity-50'

const fieldClass =
  'w-full border border-edge bg-surface-raised px-3.5 py-3 text-sm text-fg outline-none transition-colors duration-150 placeholder:text-fg-subtle focus:border-negative/60'

/** Baixa um objeto como arquivo .json. */
function downloadJson(data: unknown, fileName: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function Settings() {
  // null = ainda carregando (ou falhou): nesse caso mostra o botão normal de vincular.
  const [telegramLinked, setTelegramLinked] = useState<boolean | null>(null)
  const [link, setLink] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    ownerApi
      .me()
      .then((profile) => {
        if (active) setTelegramLinked(profile.telegramLinked)
      })
      .catch(() => {})
    return () => {
      active = false
    }
  }, [])

  async function handleExport() {
    setExporting(true)
    setExportError(null)
    try {
      const data = await ownerApi.exportData()
      downloadJson(data, `money-flow-dados-${new Date().toISOString().slice(0, 10)}.json`)
    } catch {
      setExportError('Não foi possível exportar seus dados. Tente novamente.')
    } finally {
      setExporting(false)
    }
  }

  async function handleDelete(e: FormEvent) {
    e.preventDefault()
    setDeleting(true)
    setDeleteError(null)
    try {
      await ownerApi.deleteAccount(deletePassword)
      // Conta apagada (as sessões foram junto, em cascata). A resposta já limpa o cookie de refresh;
      // o logout garante isso também em dev cross-origin, onde aquele Set-Cookie é ignorado.
      await authApi.logout().catch(() => {})
      clearToken()
      window.location.assign(LANDING_URL)
    } catch (err) {
      setDeleteError(
        err instanceof ApiError && err.status === 403
          ? 'Senha incorreta.'
          : 'Não foi possível excluir a conta. Tente novamente.',
      )
      setDeleting(false)
    }
  }

  function cancelDelete() {
    setConfirmingDelete(false)
    setDeletePassword('')
    setDeleteError(null)
  }

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
        <p className="text-[15px] text-fg-muted">Conecte seus serviços e gerencie seus dados.</p>
      </header>

      <div className="flex max-w-xl flex-col gap-6">
        <section className="flex flex-col gap-6 border border-edge bg-surface p-6">
          <div className="flex items-start gap-4">
            <span className="flex size-10 shrink-0 items-center justify-center border border-edge">
              <Send className="size-5 text-fg-muted" strokeWidth={1.8} />
            </span>
            <div className="flex flex-col gap-1">
              <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">Telegram</h2>
              <p className="text-sm text-fg-muted">
                {telegramLinked
                  ? 'Seu Telegram está vinculado. Registre transações por mensagem ou voz no bot.'
                  : 'Vincule o bot do Telegram para registrar transações por mensagem ou voz.'}
              </p>
            </div>
          </div>

          {telegramLinked && (
            <span className="flex w-fit items-center gap-2 border border-data/40 bg-data/10 px-3 py-1.5 font-mono text-xs uppercase tracking-[0.04em] text-data">
              <Check className="size-3.5" strokeWidth={2.2} />
              Telegram vinculado
            </span>
          )}

          <button
            type="button"
            onClick={handleGenerate}
            disabled={loading}
            className={
              telegramLinked
                ? 'flex w-fit items-center gap-2.5 border border-edge px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-fg-muted transition-colors duration-150 hover:border-fg-subtle hover:text-fg disabled:opacity-50'
                : 'flex w-fit items-center gap-2.5 bg-brand px-5 py-3.5 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50'
            }
          >
            {loading ? 'Gerando link…' : telegramLinked ? 'Vincular outro chat' : 'Vincular Telegram'}
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
                Você tem 15 minutos para abrir o link no Telegram antes que ele expire.
              </p>
            </div>
          )}
        </section>

        <section className="flex flex-col gap-6 border border-edge bg-surface p-6">
          <div className="flex items-start gap-4">
            <span className="flex size-10 shrink-0 items-center justify-center border border-edge">
              <Download className="size-5 text-fg-muted" strokeWidth={1.8} />
            </span>
            <div className="flex flex-col gap-1">
              <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">Seus dados</h2>
              <p className="text-sm text-fg-muted">
                Baixe uma cópia de tudo que o Money Flow guarda sobre você: perfil, contas, categorias e
                transações, em JSON.
              </p>
            </div>
          </div>

          <button type="button" onClick={handleExport} disabled={exporting} className={outlineButton}>
            {exporting ? 'Exportando…' : 'Exportar meus dados'}
            <Download className="size-4" strokeWidth={2.2} />
          </button>

          {exportError && (
            <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
              {exportError}
            </div>
          )}

          <p className="flex flex-wrap gap-x-5 gap-y-2 font-mono text-xs text-fg-subtle">
            <a href={LEGAL_PATHS.terms} target="_blank" rel="noopener noreferrer" className="hover:text-fg">
              Termos de uso
            </a>
            <a href={LEGAL_PATHS.privacy} target="_blank" rel="noopener noreferrer" className="hover:text-fg">
              Política de privacidade
            </a>
          </p>
        </section>

        <section className="flex flex-col gap-6 border border-negative/40 bg-surface p-6">
          <div className="flex items-start gap-4">
            <span className="flex size-10 shrink-0 items-center justify-center border border-negative/40">
              <Trash2 className="size-5 text-negative" strokeWidth={1.8} />
            </span>
            <div className="flex flex-col gap-1">
              <span className="label-mono text-[11px] text-negative">Zona de perigo</span>
              <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">Excluir conta</h2>
              <p className="text-sm text-fg-muted">
                Apaga sua conta e todos os seus dados (contas, categorias e transações) de forma permanente e
                desvincula o bot do Telegram. Essa ação não pode ser desfeita.
              </p>
            </div>
          </div>

          {!confirmingDelete ? (
            <button
              type="button"
              onClick={() => setConfirmingDelete(true)}
              className="flex w-fit items-center gap-2.5 border border-negative/50 px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-negative transition-colors duration-150 hover:bg-negative/10"
            >
              Excluir conta
              <Trash2 className="size-4" strokeWidth={2.2} />
            </button>
          ) : (
            <form onSubmit={handleDelete} className="flex flex-col gap-4">
              <label className="flex flex-col gap-2">
                <span className="label-mono text-[11px] text-fg-muted">Confirme com sua senha atual</span>
                <input
                  type="password"
                  required
                  autoFocus
                  autoComplete="current-password"
                  value={deletePassword}
                  onChange={(e) => setDeletePassword(e.target.value)}
                  className={fieldClass}
                  placeholder="••••••••"
                />
              </label>

              {deleteError && (
                <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
                  {deleteError}
                </div>
              )}

              <div className="flex flex-wrap gap-3">
                <button
                  type="submit"
                  disabled={deleting || deletePassword.length === 0}
                  className="flex items-center gap-2.5 bg-negative px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50"
                >
                  {deleting ? 'Excluindo…' : 'Excluir definitivamente'}
                </button>
                <button type="button" onClick={cancelDelete} disabled={deleting} className={outlineButton}>
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </main>
  )
}
