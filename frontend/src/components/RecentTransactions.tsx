import { useMemo } from 'react'
import { ArrowLeftRight, ArrowUpRight, ShoppingBag } from 'lucide-react'
import type { Transaction } from '@/lib/types'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

interface RecentTransactionsProps {
  /** Todas as transações do período — usadas também para o total de cada dia. */
  data: Transaction[]
  loading?: boolean
  /** Máximo de itens exibidos (as mais recentes). */
  limit?: number
}

const ICON = {
  INCOME: ArrowUpRight,
  EXPENSE: ShoppingBag,
  TRANSFER: ArrowLeftRight,
} as const

interface DayGroup {
  day: string
  total: number
  items: Transaction[]
}

const dayKey = (iso: string) => iso.slice(0, 10)

function signedAmount(tx: Transaction): number {
  if (tx.operationType === 'INCOME') return tx.amount
  if (tx.operationType === 'EXPENSE') return -tx.amount
  return 0
}

/** "Hoje · sáb 10 out", "Ontem · sex 09 out" ou "Qua · 23 set". */
function dayLabel(day: string): string {
  const d = new Date(`${day}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diff = Math.round((today.getTime() - d.getTime()) / 86_400_000)
  const weekday = d.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '')
  const date = d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).replace('.', '').replace(' de ', ' ')
  if (diff === 0) return `Hoje · ${weekday} ${date}`
  if (diff === 1) return `Ontem · ${weekday} ${date}`
  return `${weekday} · ${date}`
}

/**
 * Agrupa as transações mais recentes por dia. O total do dia considera todas as
 * transações daquele dia no período, não só as visíveis.
 */
function groupByDay(data: Transaction[], limit: number): DayGroup[] {
  const totals = new Map<string, number>()
  for (const tx of data) {
    const key = dayKey(tx.date)
    totals.set(key, (totals.get(key) ?? 0) + signedAmount(tx))
  }

  const visible = [...data].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id).slice(0, limit)
  const groups: DayGroup[] = []
  for (const tx of visible) {
    const key = dayKey(tx.date)
    const last = groups[groups.length - 1]
    if (last?.day === key) last.items.push(tx)
    else groups.push({ day: key, total: totals.get(key) ?? 0, items: [tx] })
  }
  return groups
}

export function RecentTransactions({ data, loading = false, limit = 12 }: RecentTransactionsProps) {
  const { format } = useCurrency()
  const groups = useMemo(() => groupByDay(data, limit), [data, limit])
  const newestId = groups[0]?.items[0]?.id
  const shown = Math.min(limit, data.length)

  return (
    <section className="flex h-full flex-col border border-edge bg-surface">
      <div className="flex items-end justify-between gap-4 px-6 pt-6 pb-4">
        <div className="flex flex-col gap-1.5">
          <span className="eyebrow">Movimentações</span>
          <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
            Transações recentes
          </h2>
        </div>
        {!loading && data.length > 0 && (
          <span className="font-mono text-[11px] text-fg-subtle">
            {shown} de {data.length}
          </span>
        )}
      </div>

      {loading ? (
        <div className="flex flex-col gap-px px-6 pb-6">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className="h-14 animate-pulse bg-surface-raised" />
          ))}
        </div>
      ) : groups.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6 py-10 text-center">
          <p className="text-sm text-fg-muted">Nenhuma transação neste período.</p>
          <p className="font-mono text-xs text-fg-subtle">
            Mande um áudio para o bot para ver seu primeiro gasto aqui.
          </p>
        </div>
      ) : (
        <div className="flex flex-col">
          {groups.map((group) => (
            <div key={group.day} className="flex flex-col">
              <div className="flex h-9 items-center justify-between border-y border-white/8 bg-white/[0.03] px-6">
                <span className="label-mono text-[11px] text-fg-muted">{dayLabel(group.day)}</span>
                <span
                  className={cn(
                    'font-mono text-xs tabular-nums',
                    group.total > 0 ? 'text-data' : 'text-fg-muted',
                  )}
                >
                  {group.total > 0 ? '+' : group.total < 0 ? '−' : ''}
                  {format(Math.abs(group.total))}
                </span>
              </div>

              <ul className="flex flex-col">
                {group.items.map((tx) => {
                  const Icon = ICON[tx.operationType]
                  const isIncome = tx.operationType === 'INCOME'
                  const isNew = tx.id === newestId
                  const sign = isIncome ? '+' : tx.operationType === 'EXPENSE' ? '−' : ''
                  return (
                    <li
                      key={tx.id}
                      className={cn(
                        'flex h-[60px] items-center gap-3 px-6 transition-colors duration-150',
                        isNew
                          ? '-mt-px border-y border-brand/50 bg-brand-soft'
                          : 'border-b border-white/8 last:border-b-0 hover:bg-white/[0.03]',
                      )}
                    >
                      <span
                        className={cn(
                          'flex size-8 shrink-0 items-center justify-center border',
                          isNew ? 'border-brand/50' : 'border-edge',
                        )}
                      >
                        <Icon
                          className={cn(
                            'size-4',
                            isNew ? 'text-brand' : isIncome ? 'text-data' : 'text-fg-muted',
                          )}
                          strokeWidth={1.8}
                        />
                      </span>
                      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <span className="truncate text-sm font-semibold text-fg first-letter:uppercase">
                          {tx.description}
                        </span>
                        <span className="truncate font-mono text-[11px] text-fg-subtle">
                          {tx.categoryDescription}
                        </span>
                      </div>
                      <span
                        className={cn(
                          'shrink-0 font-display text-[15px] font-medium tabular-nums whitespace-nowrap',
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
            </div>
          ))}
        </div>
      )}
    </section>
  )
}
