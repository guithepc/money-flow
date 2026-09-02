import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Clock } from 'lucide-react'
import type { DateRange, TimeSeriesPoint } from '@/lib/types'
import { CHART } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'

interface BalanceHistoryChartProps {
  data?: TimeSeriesPoint[]
  loading?: boolean
  range?: DateRange
}

function fillDailyGaps(data: TimeSeriesPoint[], range?: DateRange): TimeSeriesPoint[] {
  if (!range) return data

  const lookup = new Map(data.map((p) => [p.date, p]))
  const filled: TimeSeriesPoint[] = []
  const cursor = new Date(range.startDate + 'T00:00:00')
  const end = new Date(range.endDate + 'T00:00:00')
  let runningBalance = 0

  while (cursor <= end) {
    const key = cursor.toISOString().slice(0, 10)
    const point = lookup.get(key)
    if (point) {
      runningBalance += point.net
    }
    filled.push({ date: key, income: point?.income ?? 0, expense: point?.expense ?? 0, net: runningBalance })
    cursor.setDate(cursor.getDate() + 1)
  }
  return filled
}

export function BalanceHistoryChart({ data = [], loading = false, range }: BalanceHistoryChartProps) {
  const { format } = useCurrency()
  const filled = useMemo(() => fillDailyGaps(data, range), [data, range])

  return (
    <section className="flex h-full flex-col gap-4 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Fluxo no tempo</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Histórico de saldo
        </h2>
      </div>

      {filled.length === 0 ? (
        <WaitingBackend loading={loading} height="flex-1 min-h-32" />
      ) : (
          <div className="flex-1 min-h-44">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={filled} margin={{ top: 4, right: 4, bottom: 0, left: 4 }}>
                <defs>
                  <linearGradient id="balance-net-fill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={CHART.income} stopOpacity={0.35} />
                    <stop offset="100%" stopColor={CHART.income} stopOpacity={0.03} />
                  </linearGradient>
                </defs>
                <CartesianGrid
                  stroke={CHART.grid}
                  strokeDasharray="3 3"
                  vertical={true}
                  strokeOpacity={0.6}
                />
                <XAxis
                  dataKey="date"
                  stroke={CHART.axis}
                  tickLine={false}
                  axisLine={false}
                  fontSize={10}
                  tickFormatter={(v: string) => {
                    const d = new Date(v + 'T00:00:00')
                    return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })
                  }}
                />
                <YAxis
                  stroke={CHART.axis}
                  tickLine={false}
                  axisLine={false}
                  fontSize={10}
                  width={52}
                  tickFormatter={(v: number) => {
                    if (Math.abs(v) >= 1000) return `${(v / 1000).toFixed(1)}k`
                    return String(v)
                  }}
                />
                <Tooltip
                  contentStyle={{
                    background: CHART.surface,
                    border: `1px solid ${CHART.grid}`,
                    borderRadius: 12,
                    fontSize: 12,
                  }}
                  labelFormatter={(label: string) => {
                    const d = new Date(label + 'T00:00:00')
                    return d.toLocaleDateString('pt-BR', {
                      day: '2-digit',
                      month: 'long',
                      year: 'numeric',
                    })
                  }}
                  formatter={(value: number) => [format(value), 'Saldo líquido']}
                />
                <Area
                  type="monotone"
                  dataKey="net"
                  name="Saldo líquido"
                  stroke={CHART.income}
                  fill="url(#balance-net-fill)"
                  strokeWidth={2.5}
                  dot={false}
                  activeDot={{ r: 4, strokeWidth: 0, fill: CHART.income }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
      )}
    </section>
  )
}

export function WaitingBackend({ loading, height }: { loading?: boolean; height: string }) {
  if (loading) {
    return <div className={`${height} animate-pulse rounded-xl bg-surface-raised`} />
  }
  return (
    <div
      className={`${height} flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-edge text-center`}
    >
      <Clock className="size-5 text-fg-muted" strokeWidth={1.6} />
      <p className="text-sm text-fg-muted">Aguardando endpoint de série temporal no backend.</p>
    </div>
  )
}
