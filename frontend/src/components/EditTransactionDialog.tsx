import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Eye, X } from 'lucide-react'
import { useCurrency } from '@/contexts/CurrencyContext'
import { categoriesApi, transactionsApi } from '@/lib/api'
import type { Category, Transaction } from '@/lib/types'
import { CURRENCIES, cn, formatAmountInput, parseAmountInput } from '@/lib/utils'

interface EditTransactionDialogProps {
  transaction: Transaction
  onClose: () => void
  /** Chamado depois que o backend confirmou a edição (saldo já ajustado). */
  onSaved: () => void
}

const fieldClass =
  'w-full border border-edge bg-surface-raised px-3.5 py-3 text-sm text-fg outline-none transition-colors duration-150 placeholder:text-fg-subtle focus:border-brand/60'

export function EditTransactionDialog({ transaction, onClose, onSaved }: EditTransactionDialogProps) {
  const [description, setDescription] = useState(transaction.description)
  const [amount, setAmount] = useState(formatAmountInput(transaction.amount))
  const [categoryId, setCategoryId] = useState<number>(transaction.categoryId)
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const descriptionRef = useRef<HTMLInputElement>(null)
  // Com "ocultar valores" ligado, o valor fica mascarado até a pessoa pedir pra ver.
  const { valuesVisible } = useCurrency()
  const [revealed, setRevealed] = useState(false)
  const amountHidden = !valuesVisible && !revealed

  // Enquanto salva, o diálogo não fecha: o PUT já foi e o resultado precisa chegar.
  const savingRef = useRef(false)
  const onCloseRef = useRef(onClose)
  const onSavedRef = useRef(onSaved)
  useEffect(() => {
    onCloseRef.current = onClose
    onSavedRef.current = onSaved
  })
  const requestClose = () => {
    if (!savingRef.current) onCloseRef.current()
  }

  const isIncome = transaction.operationType === 'INCOME'
  const symbol = CURRENCIES.find((c) => c.code === transaction.currency)?.symbol ?? transaction.currency

  // Só categorias do mesmo tipo: gasto não vira entrada pela edição.
  const options = useMemo(
    () => (categories ?? []).filter((c) => c.type === (isIncome ? 'INCOME' : 'EXPENSE')),
    [categories, isIncome],
  )

  useEffect(() => {
    let active = true
    categoriesApi
      .list()
      .then((list) => {
        if (active) setCategories(list)
      })
      .catch(() => {
        if (active) setError('Não foi possível carregar as categorias.')
      })
    return () => {
      active = false
    }
  }, [])

  // Só no mount: re-renders do dashboard não podem roubar o foco de outro campo.
  useEffect(() => {
    descriptionRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !savingRef.current) onCloseRef.current()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = description.trim()
    const value = parseAmountInput(amount)
    if (!trimmed) return setError('Informe uma descrição.')
    if (trimmed.length > 255) return setError('A descrição pode ter no máximo 255 caracteres.')
    if (!options.some((c) => c.categoryId === categoryId)) return setError('Escolha uma categoria.')
    if (value == null || value <= 0) return setError('Valor inválido. Use o formato 1.250,90.')

    savingRef.current = true
    setSaving(true)
    setError(null)
    try {
      await transactionsApi.update(transaction.id, { description: trimmed, categoryId, amount: value })
      onSavedRef.current()
    } catch {
      setError('Não foi possível salvar. Tente novamente.')
    } finally {
      savingRef.current = false
      setSaving(false)
    }
  }

  async function handleDelete() {
    // Bloqueia o fechamento enquanto o DELETE está em voo (o saldo já pode ter mudado no backend).
    savingRef.current = true
    setDeleting(true)
    setError(null)
    try {
      await transactionsApi.remove(transaction.id)
      onSavedRef.current()
    } catch {
      setError('Não foi possível excluir. Tente novamente.')
      setConfirmDelete(false)
    } finally {
      savingRef.current = false
      setDeleting(false)
    }
  }

  const busy = saving || deleting

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70" onClick={requestClose} aria-hidden />
      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="edit-transaction-title"
        onSubmit={handleSubmit}
        className="relative flex w-full max-w-md flex-col gap-5 border border-edge bg-surface p-6"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <span className="eyebrow">{isIncome ? 'Entrada' : 'Gasto'}</span>
            <h2
              id="edit-transaction-title"
              className="font-display text-2xl font-normal tracking-[-0.02em] text-fg"
            >
              Editar transação
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
          <span className="label-mono text-[11px] text-fg-muted">Descrição</span>
          <input
            ref={descriptionRef}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={255}
            className={fieldClass}
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className="label-mono text-[11px] text-fg-muted">Categoria</span>
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(Number(e.target.value))}
            disabled={categories == null}
            className={cn(fieldClass, 'appearance-none disabled:opacity-50')}
          >
            {categories == null && <option value={categoryId}>{transaction.categoryDescription}</option>}
            {options.map((c) => (
              <option key={c.categoryId} value={c.categoryId}>
                {c.categoryDescription}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-2">
          <span className="label-mono text-[11px] text-fg-muted">Valor</span>
          <div className="flex items-stretch border border-edge bg-surface-raised focus-within:border-brand/60">
            <span className="flex items-center border-r border-edge px-3.5 font-mono text-sm text-fg-muted">
              {symbol}
            </span>
            <input
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              type={amountHidden ? 'password' : 'text'}
              inputMode="decimal"
              autoComplete="off"
              className="w-full bg-transparent px-3.5 py-3 font-display text-sm tabular-nums text-fg outline-none"
            />
            {amountHidden && (
              <button
                type="button"
                onClick={() => setRevealed(true)}
                className="flex items-center border-l border-edge px-3.5 text-fg-muted transition-colors duration-150 hover:text-fg"
                title="Mostrar valor"
                aria-label="Mostrar valor"
              >
                <Eye className="size-4" strokeWidth={1.8} />
              </button>
            )}
          </div>
          <span className="font-mono text-[11px] text-fg-subtle">
            O saldo da conta é ajustado pela diferença.
          </span>
        </label>

        {error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">{error}</div>
        )}

        {confirmDelete && !error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
            Excluir esta transação? O saldo da conta será revertido e a ação não pode ser desfeita.
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
                disabled={busy || categories == null}
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
