import { useMemo } from 'react'
import type { Account } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useCountUp } from '@/hooks/useCountUp'
import { useCurrency } from '@/contexts/CurrencyContext'

interface AccountSwitcherProps {
  accounts: Account[]
  /** null = "Todas as contas" (agregado). */
  selectedId: number | null
  onSelect: (id: number | null) => void
  loading?: boolean
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

  return (
    <section className="flex h-full min-h-[300px] flex-col justify-between gap-6 border border-edge bg-surface p-6">
      <div className="flex items-center justify-between gap-4">
        <span className="eyebrow">Saldo acumulado</span>
        <span className="font-mono text-[11px] text-fg-subtle">
          {accounts.length} {accounts.length === 1 ? 'conta' : 'contas'}
        </span>
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
