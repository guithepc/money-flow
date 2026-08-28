import { ArrowDownLeft, ArrowLeftRight, ArrowUpRight } from 'lucide-react'
import type { Transaction } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

interface RecentTransactionsProps {
  data: Transaction[]
  loading?: boolean
  /** Máximo de itens exibidos (as mais recentes). */
  limit?: number
}

const ICON = {
  INCOME: ArrowUpRight,
  EXPENSE: ArrowDownLeft,
  TRANSFER: ArrowLeftRight,
} as const

function formatDate(iso: string): string {
  const d = new Date(iso)
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(d)
}

export function RecentTransactions({ data, loading = false, limit = 12 }: RecentTransactionsProps) {
  const { format } = useCurrency()
  // Mais recentes primeiro.
  const items = [...data]
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, limit)

  return (
    <section className="flex h-full flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Movimentações</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Transações recentes
        </h2>
      </div>

      {loading ? (
        <div className="flex flex-1 flex-col gap-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-14 flex-1 animate-pulse rounded-xl bg-surface-raised" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <p className="flex flex-1 items-center justify-center py-8 text-center text-sm text-fg-muted">
          Nenhuma transação neste período.
        </p>
      ) : (
        <ul className="flex flex-1 flex-col justify-between gap-2">
          {items.map((tx) => {
            const Icon = ICON[tx.operationType]
            const isIncome = tx.operationType === 'INCOME'
            const sign = isIncome ? '+' : tx.operationType === 'EXPENSE' ? '−' : ''
            return (
              <li
                key={tx.id}
                className="flex items-center justify-between gap-4 rounded-xl px-3 py-2.5 transition-colors hover:bg-surface-raised"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={cn(
                      'flex size-9 shrink-0 items-center justify-center rounded-lg',
                      isIncome ? 'bg-data/10 text-data' : 'bg-negative/10 text-negative',
                    )}
                  >
                    <Icon className="size-4" strokeWidth={1.8} />
                  </span>
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate text-sm font-medium text-fg">
                      {tx.description}
                    </span>
                    <span className="text-xs text-fg-muted">
                      {tx.categoryDescription} · {formatDate(tx.date)}
                    </span>
                  </div>
                </div>
                <span
                  className={cn(
                    'shrink-0 font-mono text-sm tabular-nums',
                    isIncome ? 'text-data' : 'text-fg',
                  )}
                >
                  {sign}
                  {format(tx.amount)}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
