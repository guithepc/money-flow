import { useMemo } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import type { AmountAndCategory } from '@/lib/types'
import { CATEGORY_PALETTE, OTHERS_COLOR, categoryColor } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'

interface CategoryDonutProps {
  data: AmountAndCategory[]
  loading?: boolean
}

interface Slice {
  name: string
  total: number
  color: string
  muted?: boolean
}

/** Mantém as maiores categorias com cor própria e agrupa a cauda em "Outros · N". */
function toSlices(data: AmountAndCategory[]): Slice[] {
  const sorted = [...data].sort((a, b) => b.total - a.total)
  if (sorted.length <= CATEGORY_PALETTE.length + 1) {
    return sorted.map((d, i) => ({ name: d.categoryDescription, total: d.total, color: categoryColor(i) }))
  }
  const head = sorted.slice(0, CATEGORY_PALETTE.length)
  const tail = sorted.slice(CATEGORY_PALETTE.length)
  return [
    ...head.map((d, i) => ({ name: d.categoryDescription, total: d.total, color: categoryColor(i) })),
    {
      name: `Outros · ${tail.length}`,
      total: tail.reduce((acc, d) => acc + d.total, 0),
      color: OTHERS_COLOR,
      muted: true,
    },
  ]
}

export function CategoryDonut({ data, loading = false }: CategoryDonutProps) {
  const { format } = useCurrency()
  const slices = useMemo(() => toSlices(data), [data])
  const total = useMemo(() => data.reduce((acc, d) => acc + d.total, 0), [data])

  return (
    <section className="flex flex-col gap-6 border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1.5">
        <span className="eyebrow">Gastos por categoria</span>
        <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
          Para onde foi o dinheiro
        </h2>
      </div>

      {loading ? (
        <div className="flex items-center gap-12">
          <div className="size-[200px] shrink-0 animate-pulse rounded-full bg-surface-raised" />
          <div className="flex flex-1 flex-col gap-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-5 animate-pulse bg-surface-raised" />
            ))}
          </div>
        </div>
      ) : slices.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-10 text-center">
          <p className="text-sm text-fg-muted">Nenhum gasto registrado neste período.</p>
          <p className="font-mono text-xs text-fg-subtle">
            Mande um áudio para o bot para ver seu primeiro gasto aqui.
          </p>
        </div>
      ) : (
        <div className="flex flex-col items-center gap-12 sm:flex-row">
          {/* Donut fino com total no centro */}
          <div className="relative size-[200px] shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={slices}
                  dataKey="total"
                  nameKey="name"
                  innerRadius={70}
                  outerRadius={100}
                  startAngle={90}
                  endAngle={-270}
                  paddingAngle={1.5}
                  stroke="none"
                  isAnimationActive
                >
                  {slices.map((s) => (
                    <Cell key={s.name} fill={s.color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-0.5">
              <span className="label-mono text-[11px] text-fg-subtle">Total</span>
              <span className="font-display text-xl font-medium tracking-[-0.02em] tabular-nums text-fg">
                {format(total)}
              </span>
            </div>
          </div>

          {/* Legenda em colunas fixas: cor · nome · % · valor */}
          <ul className="flex w-full flex-1 flex-col">
            {slices.map((s, i) => {
              const pct = total > 0 ? (s.total / total) * 100 : 0
              return (
                <li
                  key={s.name}
                  className={`flex h-[30px] items-center gap-3 ${i < slices.length - 1 ? 'border-b border-white/6' : ''}`}
                >
                  <span className="size-2 shrink-0" style={{ backgroundColor: s.color }} />
                  <span className={`min-w-0 flex-1 truncate text-sm ${s.muted ? 'text-fg-muted' : 'text-fg'}`}>
                    {s.name}
                  </span>
                  <span className="w-12 shrink-0 text-right font-mono text-xs text-fg-subtle">
                    {pct.toFixed(0)}%
                  </span>
                  <span className="w-28 shrink-0 text-right font-display text-sm font-medium tabular-nums text-fg">
                    {format(s.total)}
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
