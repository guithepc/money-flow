import { useMemo } from 'react'
import { Wallet } from 'lucide-react'
import type { Account } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useCountUp } from '@/hooks/useCountUp'
import { useCurrency } from '@/contexts/CurrencyContext'
import { CurrencySelector } from './CurrencySelector'

interface AccountSwitcherProps {
  accounts: Account[]
  /** null = "Todas as contas" (agregado). */
  selectedId: number | null
  onSelect: (id: number | null) => void
  loading?: boolean
}

/** Número mascarado decorativo derivado do id — estética de "cartão". */
function maskedNumber(id: number | null): string {
  const tail = id == null ? '0000' : String(1000 + (id % 9000))
  return `•••• •••• •••• ${tail}`
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
    <section className="flex h-full flex-col gap-4">
      {/* Card hero com deck empilhado atrás */}
      <div className="relative flex-1">
        {/* Cluster de ícones (fora do overflow-hidden pra o dropdown não cortar) */}
        <div className="absolute right-6 top-6 z-20 flex flex-col items-center gap-1">
          <span className="flex size-8 items-center justify-center text-brand">
            <Wallet className="size-5" strokeWidth={1.8} />
          </span>
          <CurrencySelector />
        </div>

        {/* Cards decorativos empilhados */}
        <div
          aria-hidden
          className="absolute -right-3 top-2 h-full w-full rounded-2xl border border-edge bg-surface-raised opacity-40"
        />
        <div
          aria-hidden
          className="absolute -right-1.5 top-1 h-full w-full rounded-2xl border border-edge bg-surface-raised opacity-70"
        />

        <div
          key={selectedId ?? 'all'}
          className="relative flex h-full min-h-[190px] flex-col justify-between overflow-hidden rounded-2xl border border-edge p-6 animate-[fadeInUp_400ms_ease]"
          style={{
            background:
              'linear-gradient(135deg, #3a2f0a 0%, #1b201d 55%, #141816 100%)',
          }}
        >
          {loading ? (
            <>
              <div className="h-4 w-24 animate-pulse rounded bg-surface-raised" />
              <div className="h-9 w-40 animate-pulse rounded bg-surface-raised" />
              <div className="h-4 w-48 animate-pulse rounded bg-surface-raised" />
            </>
          ) : (
            <>
              <div className="flex items-center justify-between">
                <span className="eyebrow text-brand">Saldo acumulado</span>
              </div>
              <span
                className={cn(
                  'font-rounded text-4xl font-bold tabular-nums tracking-tight',
                  selected.amount < 0 ? 'text-negative' : 'text-fg',
                )}
              >
                {format(animatedAmount)}
              </span>
              <div className="flex items-end justify-between gap-4">
                <span className="font-mono text-sm tracking-widest text-fg-muted">
                  {maskedNumber(selectedId)}
                </span>
                <span className="max-w-[45%] truncate text-sm font-medium text-fg">
                  {selected.description}
                </span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Pills de seleção — clicar troca a conta e atualiza o dashboard */}
      <div className="flex flex-wrap gap-2">
        <AccountPill
          label="Todas"
          active={selectedId == null}
          onClick={() => onSelect(null)}
        />
        {accounts.map((account) => (
          <AccountPill
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

function AccountPill({
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
      className={cn(
        'rounded-full border px-4 py-1.5 text-sm transition-colors duration-200',
        active
          ? 'border-brand bg-brand-soft text-brand'
          : 'border-edge bg-surface text-fg-muted hover:text-fg',
      )}
    >
      {label}
    </button>
  )
}
