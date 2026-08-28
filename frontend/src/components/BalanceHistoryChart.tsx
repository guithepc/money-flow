import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Clock } from 'lucide-react'
import type { TimeSeriesPoint } from '@/lib/types'
import { CHART } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'

interface BalanceHistoryChartProps {
  data?: TimeSeriesPoint[]
  loading?: boolean
}

/**
 * Histórico de renda × gasto no tempo. O endpoint (`balance-history`) ainda não
 * existe no backend — enquanto isso, exibe estado "aguardando backend".
 */
export function BalanceHistoryChart({ data = [], loading = false }: BalanceHistoryChartProps) {
  const { format } = useCurrency()
  return (
    <section className="flex flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Fluxo no tempo</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Histórico de saldo
        </h2>
      </div>

      {data.length === 0 ? (
        <WaitingBackend loading={loading} height="h-32" />
      ) : (
        <div className="h-32">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
              <defs>
                <linearGradient id="hist-income" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={CHART.income} stopOpacity={0.3} />
                  <stop offset="100%" stopColor={CHART.income} stopOpacity={0} />
                </linearGradient>
                <linearGradient id="hist-expense" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={CHART.expense} stopOpacity={0.3} />
                  <stop offset="100%" stopColor={CHART.expense} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke={CHART.grid} vertical={false} />
              <XAxis dataKey="date" stroke={CHART.axis} tickLine={false} fontSize={11} />
              <YAxis stroke={CHART.axis} tickLine={false} fontSize={11} width={48} />
              <Tooltip
                contentStyle={{
                  background: CHART.surface,
                  border: `1px solid ${CHART.grid}`,
                  borderRadius: 12,
                }}
                formatter={(value) => format(Number(value))}
              />
              <Area
                type="monotone"
                dataKey="income"
                name="Renda"
                stroke={CHART.income}
                fill="url(#hist-income)"
                strokeWidth={2}
              />
              <Area
                type="monotone"
                dataKey="expense"
                name="Gasto"
                stroke={CHART.expense}
                fill="url(#hist-expense)"
                strokeWidth={2}
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
