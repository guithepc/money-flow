import { useMemo } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import type { AmountAndCategory } from '@/lib/types'
import { categoryColor } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'

interface CategoryDonutProps {
  data: AmountAndCategory[]
  loading?: boolean
}

export function CategoryDonut({ data, loading = false }: CategoryDonutProps) {
  const { format } = useCurrency()
  const total = useMemo(() => data.reduce((acc, d) => acc + d.total, 0), [data])

  return (
    <section className="flex flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Gastos por categoria</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Para onde foi o dinheiro
        </h2>
      </div>

      {loading ? (
        <div className="flex items-center gap-6">
          <div className="size-40 animate-pulse rounded-full bg-surface-raised" />
          <div className="flex flex-1 flex-col gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-6 animate-pulse rounded-md bg-surface-raised" />
            ))}
          </div>
        </div>
      ) : data.length === 0 ? (
        <p className="py-8 text-center text-sm text-fg-muted">
          Nenhum gasto registrado neste período.
        </p>
      ) : (
        <div className="flex flex-col items-center gap-8 sm:flex-row">
          {/* Donut com total no centro */}
          <div className="relative size-44 shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data}
                  dataKey="total"
                  nameKey="categoryDescription"
                  innerRadius={58}
                  outerRadius={84}
                  paddingAngle={2}
                  stroke="none"
                  isAnimationActive
                >
                  {data.map((_, i) => (
                    <Cell key={i} fill={categoryColor(i)} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
              <span className="eyebrow text-fg-muted">Total</span>
              <span className="font-rounded text-lg font-bold tabular-nums text-fg">
                {format(total)}
              </span>
            </div>
          </div>

          {/* Legenda com valor e % */}
          <ul className="flex flex-1 flex-col gap-3">
            {data.map((item, i) => {
              const pct = total > 0 ? (item.total / total) * 100 : 0
              return (
                <li
                  key={item.categoryDescription}
                  className="flex items-center justify-between gap-4"
                >
                  <div className="flex min-w-0 items-center gap-2.5">
                    <span
                      className="size-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: categoryColor(i) }}
                    />
                    <span className="truncate text-sm font-medium text-fg">
                      {item.categoryDescription}
                    </span>
                    <span className="shrink-0 font-mono text-xs text-fg-muted">
                      {pct.toFixed(0)}%
                    </span>
                  </div>
                  <span className="shrink-0 font-mono text-sm tabular-nums text-fg-muted">
                    {format(item.total)}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </section>
  )
}
