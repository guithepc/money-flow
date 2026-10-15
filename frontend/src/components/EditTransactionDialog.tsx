import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
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
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const descriptionRef = useRef<HTMLInputElement>(null)

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
        if (!active) return
        setCategories(list)
        // Descrição é única por usuário, então dá pra achar a categoria atual pelo nome.
        const current = list.find((c) => c.categoryDescription === transaction.categoryDescription)
        setCategoryId(current?.categoryId ?? null)
      })
      .catch(() => {
        if (active) setError('Não foi possível carregar as categorias.')
      })
    return () => {
      active = false
    }
  }, [transaction.categoryDescription])

  useEffect(() => {
    descriptionRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const trimmed = description.trim()
    const value = parseAmountInput(amount)
    if (!trimmed) return setError('Informe uma descrição.')
    if (trimmed.length > 255) return setError('A descrição pode ter no máximo 255 caracteres.')
    if (categoryId == null) return setError('Escolha uma categoria.')
    if (value == null || value <= 0) return setError('Valor inválido. Use o formato 1.250,90.')

    setSaving(true)
    setError(null)
    try {
      await transactionsApi.update(transaction.id, { description: trimmed, categoryId, amount: value })
      onSaved()
    } catch {
      setError('Não foi possível salvar. Tente novamente.')
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70" onClick={onClose} aria-hidden />
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
            onClick={onClose}
            className="flex size-8 shrink-0 items-center justify-center border border-edge text-fg-muted transition-colors duration-150 hover:border-white/30 hover:text-fg"
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
            value={categoryId ?? ''}
            onChange={(e) => setCategoryId(e.target.value ? Number(e.target.value) : null)}
            disabled={categories == null}
            className={cn(fieldClass, 'appearance-none disabled:opacity-50')}
          >
            {categories == null && <option value="">Carregando…</option>}
            {categories != null && categoryId == null && <option value="">Escolha uma categoria</option>}
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
              inputMode="decimal"
              className="w-full bg-transparent px-3.5 py-3 font-display text-sm tabular-nums text-fg outline-none"
            />
          </div>
          <span className="font-mono text-[11px] text-fg-subtle">
            O saldo da conta é ajustado pela diferença.
          </span>
        </label>

        {error && (
          <div className="border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">{error}</div>
        )}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="border border-edge px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-fg-muted transition-colors duration-150 hover:border-fg-subtle hover:text-fg"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={saving || categories == null}
            className="bg-brand px-5 py-3 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50"
          >
            {saving ? 'Salvando…' : 'Salvar'}
          </button>
        </div>
      </form>
    </div>
  )
}
