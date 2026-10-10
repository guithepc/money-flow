import { useMemo } from 'react'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { DateRange, TimeSeriesPoint } from '@/lib/types'
import { CHART } from '@/lib/chartColors'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

interface BalanceHistoryChartProps {
  data?: TimeSeriesPoint[]
  loading?: boolean
  range?: DateRange
  currentBalance?: number
}

const AXIS_TICK = { fontFamily: 'Space Mono', fontSize: 11, fill: CHART.axis }

function fillDailyGaps(data: TimeSeriesPoint[], range?: DateRange, currentBalance?: number): TimeSeriesPoint[] {
  if (!range) return data

  const lookup = new Map(data.map((p) => [p.date, p]))
  const filled: TimeSeriesPoint[] = []
  const cursor = new Date(range.startDate + 'T00:00:00')
  const end = new Date(range.endDate + 'T00:00:00')

  const totalNet = data.reduce((sum, p) => sum + p.net, 0)
  let runningBalance = currentBalance != null ? currentBalance - totalNet : 0

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

/** Períodos longos mostram o mês; curtos, dia + mês. */
function tickLabel(iso: string, long: boolean): string {
  const d = new Date(iso + 'T00:00:00')
  return long
    ? d.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
    : d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).replace('.', '')
}

export function BalanceHistoryChart({ data = [], loading = false, range, currentBalance }: BalanceHistoryChartProps) {
  const { format } = useCurrency()
  const filled = useMemo(() => fillDailyGaps(data, range, currentBalance), [data, range, currentBalance])
  const periodNet = useMemo(() => data.reduce((sum, p) => sum + p.net, 0), [data])
  const longRange = filled.length > 62
  const lastIndex = filled.length - 1

  return (
    <section className="flex h-full min-h-[300px] flex-col gap-4 border border-edge bg-surface p-6">
      <div className="flex items-end justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <span className="eyebrow">Fluxo no tempo</span>
          <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
            Histórico de saldo
          </h2>
        </div>
        {data.length > 0 && (
          <span
            className={cn(
              'font-mono text-xs tabular-nums',
              periodNet < 0 ? 'text-negative' : 'text-data',
            )}
          >
            {periodNet < 0 ? '−' : '+'}
            {format(Math.abs(periodNet))} no período
          </span>
        )}
      </div>

      {filled.length === 0 ? (
        <WaitingBackend loading={loading} height="flex-1 min-h-32" />
      ) : (
        <div className="min-h-44 flex-1">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={filled} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={CHART.grid} strokeDasharray="3 4" vertical={false} />
              <XAxis
                dataKey="date"
                tick={AXIS_TICK}
                tickLine={false}
                axisLine={false}
                minTickGap={32}
                interval="preserveStartEnd"
                tickFormatter={(v: string) => tickLabel(v, longRange)}
              />
              <YAxis
                tick={AXIS_TICK}
                tickLine={false}
                axisLine={false}
                width={44}
                tickFormatter={(v: number) => {
                  if (Math.abs(v) >= 1000) return `${(v / 1000).toFixed(1).replace('.0', '')}k`
                  return String(v)
                }}
              />
              <Tooltip
                cursor={{ stroke: CHART.edge }}
                contentStyle={{
                  background: CHART.surface,
                  border: `1px solid ${CHART.edge}`,
                  borderRadius: 0,
                  fontSize: 12,
                }}
                labelStyle={{ fontFamily: 'Space Mono', color: CHART.axis, marginBottom: 4 }}
                itemStyle={{ color: '#fff' }}
                labelFormatter={(label) => {
                  const d = new Date(`${label}T00:00:00`)
                  return d.toLocaleDateString('pt-BR', {
                    day: '2-digit',
                    month: 'long',
                    year: 'numeric',
                  })
                }}
                formatter={(value) => [format(Number(value)), 'Saldo']}
              />
              <Area
                type="monotone"
                dataKey="net"
                name="Saldo"
                stroke={CHART.brand}
                fill={CHART.brand}
                fillOpacity={0.12}
                strokeWidth={2.5}
                dot={(props: { cx?: number; cy?: number; index?: number }) =>
                  props.index === lastIndex && props.cx != null && props.cy != null ? (
                    <rect
                      key="last"
                      x={props.cx - 4}
                      y={props.cy - 4}
                      width={8}
                      height={8}
                      fill={CHART.brand}
                    />
                  ) : (
                    <g key={props.index} />
                  )
                }
                activeDot={{ r: 0 }}
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
    return <div className={`${height} animate-pulse bg-surface-raised`} />
  }
  return (
    <div
      className={`${height} flex flex-col items-center justify-center gap-2 border border-dashed border-edge px-6 text-center`}
    >
      <p className="text-sm text-fg-muted">Sem movimentações neste período.</p>
      <p className="font-mono text-xs text-fg-subtle">
        Mande um áudio para o bot para ver seu primeiro gasto aqui.
      </p>
    </div>
  )
}
