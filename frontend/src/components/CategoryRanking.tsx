import type { AmountAndCategory } from '@/lib/types'
import { formatCurrency } from '@/lib/utils'
import { SegmentedBar } from './SegmentedBar'

interface CategoryRankingProps {
  data: AmountAndCategory[]
  loading?: boolean
}

export function CategoryRanking({ data, loading = false }: CategoryRankingProps) {
  const max = data.reduce((acc, d) => Math.max(acc, d.total), 0)

  return (
    <section className="flex flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Gastos por categoria</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Para onde foi o dinheiro
        </h2>
      </div>

      {loading ? (
        <div className="flex flex-col gap-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-10 animate-pulse rounded-md bg-surface-raised" />
          ))}
        </div>
      ) : data.length === 0 ? (
        <p className="py-8 text-center text-sm text-fg-muted">
          Nenhum gasto registrado neste período.
        </p>
      ) : (
        <ul className="flex flex-col gap-5">
          {data.map((item) => {
            const ratio = max > 0 ? item.total / max : 0
            return (
              <li key={item.categoryDescription} className="flex flex-col gap-2">
                <div className="flex items-baseline justify-between gap-4">
                  <span className="text-sm font-medium text-fg">
                    {item.categoryDescription}
                  </span>
                  <span className="font-mono text-sm tabular-nums text-fg-muted">
                    {formatCurrency(item.total)}
                  </span>
                </div>
                <SegmentedBar ratio={ratio} />
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
