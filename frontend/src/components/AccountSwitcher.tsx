import { useMemo } from 'react'
import type { Account } from '@/lib/types'
import { CURRENCIES, cn, type Currency } from '@/lib/utils'
import { useCountUp } from '@/hooks/useCountUp'
import { useCurrency } from '@/contexts/CurrencyContext'

interface AccountSwitcherProps {
  accounts: Account[]
  /** null = "Todas as contas" (agregado). */
  selectedId: number | null
  onSelect: (id: number | null) => void
  loading?: boolean
  /** Todas as contas do usuário (todas as moedas) — usadas nas abas de moeda. */
  allAccounts: Account[]
  /** Moeda filtrada no dashboard. */
  currency: Currency
  onCurrencyChange: (currency: Currency) => void
}

/** Final mascarado decorativo derivado do id — estética de "cartão". */
function maskedTail(id: number | null): string {
  return id == null ? '0000' : String(1000 + (id % 9000))
}

export function AccountSwitcher({
  accounts,
  selectedId,
  onSelect,
  loading = false,
  allAccounts,
  currency,
  onCurrencyChange,
}: AccountSwitcherProps) {
  const total = useMemo(() => accounts.reduce((acc, a) => acc + a.amount, 0), [accounts])

  const selected =
    selectedId == null
      ? { description: 'Todas as contas', amount: total }
      : (accounts.find((a) => a.accountId === selectedId) ?? {
          description: 'Conta',
          amount: 0,
        })

  const { format } = useCurrency()
  const animatedAmount = useCountUp(selected.amount)

  // Sem câmbio, cada moeda é um "bolso" separado: as abas deixam visível que
  // existem contas em outras moedas (antes ficavam escondidas num seletor no topo).
  const currencyTabs = useMemo(
    () =>
      CURRENCIES.map((c) => ({
        ...c,
        count: allAccounts.filter((a) => a.currency === c.code).length,
      })).filter((c) => c.count > 0),
    [allAccounts],
  )

  return (
    <section className="flex h-full min-h-[300px] flex-col justify-between gap-6 border border-edge bg-surface p-6">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
        <span className="eyebrow">Saldo acumulado</span>
        {currencyTabs.length > 1 ? (
          <div role="tablist" aria-label="Moeda" className="flex flex-wrap gap-1.5">
            {currencyTabs.map((c) => {
              const active = c.code === currency
              return (
                <button
                  key={c.code}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => onCurrencyChange(c.code)}
                  title={`${c.count} ${c.count === 1 ? 'conta' : 'contas'} em ${c.label}`}
                  className={cn(
                    'flex items-center gap-1.5 border px-2.5 py-1 font-mono text-[11px] transition-colors duration-150',
                    active
                      ? 'border-brand bg-brand-soft text-brand'
                      : 'border-edge text-fg-muted hover:border-white/30 hover:text-fg',
                  )}
                >
                  <span>
                    {c.symbol} {c.code}
                  </span>
                  <span className={active ? 'text-brand/70' : 'text-fg-subtle'}>{c.count}</span>
                </button>
              )
            })}
          </div>
        ) : (
          <span className="font-mono text-[11px] text-fg-subtle">
            {accounts.length} {accounts.length === 1 ? 'conta' : 'contas'}
          </span>
        )}
      </div>

      {loading ? (
        <div className="flex flex-col gap-3">
          <div className="h-11 w-56 animate-pulse bg-surface-raised" />
          <div className="h-4 w-44 animate-pulse bg-surface-raised" />
        </div>
      ) : (
        <div key={selectedId ?? 'all'} className="flex flex-col gap-2 animate-[fadeInUp_400ms_ease]">
          <span
            className={cn(
              'font-display text-[44px] leading-[48px] font-medium tracking-[-0.02em] tabular-nums whitespace-nowrap',
              selected.amount < 0 ? 'text-negative' : 'text-fg',
            )}
          >
            {format(animatedAmount)}
          </span>
          <span className="truncate font-mono text-xs text-fg-subtle">
            •••• {maskedTail(selectedId)} / {selected.description}
          </span>
        </div>
      )}

      {/* Chips de seleção — clicar troca a conta e atualiza o dashboard */}
      <div className="flex flex-wrap gap-2">
        <AccountChip label="Todas" active={selectedId == null} onClick={() => onSelect(null)} />
        {accounts.map((account) => (
          <AccountChip
            key={account.accountId}
            label={account.description}
            active={selectedId === account.accountId}
            onClick={() => onSelect(account.accountId)}
          />
        ))}
      </div>
    </section>
  )
}

function AccountChip({
  label,
  active,
  onClick,
}: {
  label: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'border px-3 py-1.5 text-[13px] transition-colors duration-150',
        active
          ? 'border-brand bg-brand font-semibold text-black'
          : 'border-edge text-fg-muted hover:border-white/30 hover:text-fg',
      )}
    >
      {label}
    </button>
  )
}
