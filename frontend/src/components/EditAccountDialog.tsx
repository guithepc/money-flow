import { useEffect, useRef, useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
import { ApiError, accountsApi } from '@/lib/api'
import type { Account } from '@/lib/types'

interface EditAccountDialogProps {
  account: Account
  onClose: () => void
  /** Chamado depois que o backend confirmou o rename ou a exclusão. */
  onSaved: () => void
}

const fieldClass =
  'w-full border border-edge bg-surface-raised px-3.5 py-3 text-sm text-fg outline-none transition-colors duration-150 placeholder:text-fg-subtle focus:border-brand/60'

export function EditAccountDialog({ account, onClose, onSaved }: EditAccountDialogProps) {
  const [description, setDescription] = useState(account.description)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const descriptionRef = useRef<HTMLInputElement>(null)

  // Enquanto uma chamada está em voo o diálogo não fecha: o resultado precisa chegar.
  const busyRef = useRef(false)
  const onCloseRef = useRef(onClose)
  const onSavedRef = useRef(onSaved)
  useEffect(() => {
    onCloseRef.current = onClose
    onSavedRef.current = onSaved
  })
  const requestClose = () => {
    if (!busyRef.current) onCloseRef.current()
  }

  useEffect(() => {
    descriptionRef.current?.focus()
    descriptionRef.current?.select()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busyRef.current) onCloseRef.current()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = description.trim()
    if (!trimmed) return setError('Informe um nome.')
    if (trimmed.length > 255) return setError('O nome pode ter no máximo 255 caracteres.')
    if (trimmed === account.description) return onSavedRef.current()

    busyRef.current = true
    setSaving(true)
    setError(null)
    try {
      await accountsApi.rename(account.accountId, trimmed)
      onSavedRef.current()
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 409
          ? 'Já existe uma conta com esse nome.'
          : 'Não foi possível salvar. Tente novamente.',
      )
    } finally {
      busyRef.current = false
      setSaving(false)
    }
  }

  async function handleDelete() {
    busyRef.current = true
    setDeleting(true)
    setError(null)
    try {
      await accountsApi.remove(account.accountId)
      onSavedRef.current()
    } catch {
      setError('Não foi possível excluir. Tente novamente.')
      setConfirmDelete(false)
    } finally {
      busyRef.current = false
      setDeleting(false)
    }
  }

  const busy = saving || deleting
  const count = account.transactionCount
  const transactionsLabel = `${count} ${count === 1 ? 'transação' : 'transações'}`

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70" onClick={requestClose} aria-hidden />
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-account-title"
        onSubmit={handleSubmit}
        className="relative flex w-full max-w-md flex-col gap-5 border border-edge bg-surface p-6"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <span className="eyebrow">Conta</span>
            <h2
              id="edit-account-title"
              className="font-display text-2xl font-normal tracking-[-0.02em] text-fg"
            >
              Editar conta
            </h2>
          </div>
          <button
            type="button"
            onClick={requestClose}
            disabled={busy}
            className="flex size-8 shrink-0 items-center justify-center border border-edge text-fg-muted transition-colors duration-150 hover:border-white/30 hover:text-fg disabled:opacity-50"
            aria-label="Fechar"
          >
            <X className="size-4" strokeWidth={1.8} />
          </button>
        </div>

        <label className="flex flex-col gap-2">
          <span className="label-mono text-[11px] text-fg-muted">Nome</span>
          <input
            ref={descriptionRef}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={255}
            className={fieldClass}
          />
        </label>

        {error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">{error}</div>
        )}

        {confirmDelete && !error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
            Excluir esta conta? Isso apaga a conta e {transactionsLabel} (e os pagamentos recorrentes dela). Não pode
            ser desfeito.
          </div>
        )}

        <div className="flex items-center justify-between gap-3">
          {confirmDelete ? (
            <button
              type="button"
              onClick={handleDelete}
              disabled={deleting}
              className="border border-negative/50 bg-negative/10 px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-negative transition-colors duration-150 hover:bg-negative/20 disabled:opacity-50"
            >
              {deleting ? 'Excluindo…' : 'Confirmar exclusão'}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => setConfirmDelete(true)}
              disabled={busy}
              className="text-sm font-semibold uppercase tracking-[0.04em] text-negative transition-colors duration-150 hover:text-negative/80 disabled:opacity-50"
            >
              Excluir
            </button>
          )}

          <div className="flex gap-3">
            <button
              type="button"
              onClick={confirmDelete ? () => setConfirmDelete(false) : requestClose}
              disabled={busy}
              className="border border-edge px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-fg-muted transition-colors duration-150 hover:border-fg-subtle hover:text-fg disabled:opacity-50"
            >
              Cancelar
            </button>
            {!confirmDelete && (
              <button
                type="submit"
                disabled={busy}
                className="bg-brand px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50"
              >
                {saving ? 'Salvando…' : 'Salvar'}
              </button>
            )}
          </div>
        </div>
      </form>
    </div>
  )
}
