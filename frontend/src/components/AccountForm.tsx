import { useState, type FormEvent } from 'react'
import { Plus } from 'lucide-react'
import { authInput, authLabel } from '@/components/AuthShell'
import { ApiError, accountsApi } from '@/lib/api'
import { useLanguage } from '@/lib/i18n'
import { defaultCurrency, parseBalanceInput } from '@/lib/onboarding'
import type { Account, Currency } from '@/lib/types'
import { CURRENCIES, cn } from '@/lib/utils'

const UI = {
  pt: {
    name: 'Nome da conta',
    namePlaceholder: 'Nubank',
    balance: 'Saldo atual',
    balancePlaceholder: '1.250,90',
    balanceHelp: 'Pode ser negativo, como num cartão de crédito.',
    currency: 'Moeda',
    submit: 'Adicionar conta',
    nameRequired: 'Dê um nome para a conta.',
    invalidBalance: 'Não entendi o saldo. Use só números, ex.: 1.250,90 ou -300.',
    duplicate: (name: string) => `Você já tem uma conta chamada ${name}.`,
    failed: 'Não consegui criar a conta. Tente de novo.',
  },
  en: {
    name: 'Account name',
    namePlaceholder: 'Revolut',
    balance: 'Current balance',
    balancePlaceholder: '1250.90',
    balanceHelp: 'It can be negative, like a credit card.',
    currency: 'Currency',
    submit: 'Add account',
    nameRequired: 'Give the account a name.',
    invalidBalance: "I didn't get the balance. Use numbers only, e.g. 1250.90 or -300.",
    duplicate: (name: string) => `You already have an account called ${name}.`,
    failed: "Couldn't create the account. Try again.",
  },
} as const

const darkInput =
  'w-full border border-edge bg-surface-raised px-3.5 py-3 text-base text-fg outline-none transition-colors duration-150 placeholder:text-fg-subtle focus:border-brand/60'
const darkLabel = 'text-[13px] font-medium text-fg-muted'

/**
 * Nome, saldo e moeda → `POST /api/accounts`. Usado no onboarding do PWA (tema claro do cadastro)
 * e em Contas → Nova conta (tema escuro do painel).
 */
export function AccountForm({ onCreated, variant = 'light' }: { onCreated: (account: Account) => void; variant?: 'light' | 'dark' }) {
  const { lang } = useLanguage()
  const ui = UI[lang]
  const [name, setName] = useState('')
  const [balance, setBalance] = useState('')
  const [currency, setCurrency] = useState<Currency>(defaultCurrency(lang))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const input = variant === 'light' ? cn(authInput, 'text-base') : darkInput
  const label = variant === 'light' ? authLabel : darkLabel

  async function submit(event: FormEvent) {
    event.preventDefault()
    const description = name.trim()
    if (!description) return setError(ui.nameRequired)
    const amount = parseBalanceInput(balance)
    if (amount === null) return setError(ui.invalidBalance)
    setSaving(true)
    setError(null)
    try {
      const account = await accountsApi.create(description, amount, currency)
      setName('')
      setBalance('')
      onCreated(account)
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409 ? ui.duplicate(description) : ui.failed)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-1.5">
        <span className={label}>{ui.name}</span>
        <input value={name} onChange={(e) => setName(e.target.value)} maxLength={60} placeholder={ui.namePlaceholder} className={input} />
      </label>
      <div className="flex gap-3">
        <label className="flex min-w-0 flex-1 flex-col gap-1.5">
          <span className={label}>{ui.balance}</span>
          <input
            value={balance}
            onChange={(e) => setBalance(e.target.value)}
            inputMode="decimal"
            placeholder={ui.balancePlaceholder}
            className={input}
          />
        </label>
        <label className="flex w-28 flex-col gap-1.5">
          <span className={label}>{ui.currency}</span>
          <select value={currency} onChange={(e) => setCurrency(e.target.value as Currency)} className={input}>
            {CURRENCIES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.code}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className={cn('-mt-2 text-xs', variant === 'light' ? 'text-ink/50' : 'text-fg-subtle')}>{ui.balanceHelp}</p>
      {error && (
        <p role="alert" className={cn('text-sm', variant === 'light' ? 'text-[#9A3412]' : 'text-negative')}>
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={saving}
        className={cn(
          'flex items-center justify-center gap-2 border px-5 py-3 text-sm font-semibold transition-colors duration-150 disabled:opacity-50',
          variant === 'light' ? 'border-ink/20 bg-white text-ink hover:border-ink' : 'border-edge text-fg hover:border-brand/60',
        )}
      >
        <Plus className="size-4" strokeWidth={2.2} />
        {ui.submit}
      </button>
    </form>
  )
}
