import { useMemo } from 'react'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MonthlyIncomeExpense } from '@/lib/types'
import { CHART } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'
import { WaitingBackend } from './BalanceHistoryChart'

interface IncomeExpenseChartProps {
  data?: MonthlyIncomeExpense[]
  loading?: boolean
}

/** "2026-08-01" ou "2026-08" → "Ago". */
function monthLabel(month: string): string {
  const iso = month.length === 7 ? `${month}-01` : month.slice(0, 10)
  const label = new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
  return label.charAt(0).toUpperCase() + label.slice(1)
}

/** 13600 → "+13,6k"; 950 → "+950". */
function compact(value: number): string {
  const sign = value < 0 ? '−' : '+'
  const abs = Math.abs(value)
  if (abs >= 1000) return `${sign}${(abs / 1000).toFixed(1).replace('.', ',').replace(',0', '')}k`
  return `${sign}${Math.round(abs)}`
}

interface MonthTickProps {
  x?: number
  y?: number
  index?: number
  payload?: { value: string }
  nets: number[]
  bestIndex: number
  valuesVisible: boolean
}

/** Tick do eixo X: mês em destaque + saldo do mês em mono embaixo. */
function MonthTick({ x = 0, y = 0, index = 0, payload, nets, bestIndex, valuesVisible }: MonthTickProps) {
  const net = nets[index] ?? 0
  return (
    <g transform={`translate(${x},${y})`}>
      <text y={18} textAnchor="middle" fill="#fff" fontSize={14} fontWeight={500} fontFamily="Space Grotesk Variable">
        {payload ? monthLabel(payload.value) : ''}
      </text>
      <text
        y={36}
        textAnchor="middle"
        fontSize={11}
        fontFamily="Space Mono"
        fill={index === bestIndex && net > 0 ? CHART.income : net < 0 ? CHART.expense : CHART.axis}
      >
        {valuesVisible ? compact(net) : '••'}
      </text>
    </g>
  )
}

export function IncomeExpenseChart({ data = [], loading = false }: IncomeExpenseChartProps) {
  const { format, valuesVisible } = useCurrency()
  const nets = useMemo(() => data.map((d) => d.income - d.expense), [data])
  const bestIndex = useMemo(
    () => (nets.length ? nets.indexOf(Math.max(...nets)) : -1),
    [nets],
  )

  return (
    <section className="flex h-full flex-col gap-5 border border-edge bg-surface p-6">
      <div className="flex items-end justify-between gap-4">
        <div className="flex flex-col gap-1.5">
          <span className="eyebrow">Comparativo</span>
          <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
            Renda e gastos
          </h2>
        </div>
        <div className="flex gap-4 font-mono text-[11px] text-fg-muted">
          <span className="flex items-center gap-1.5">
            <span className="size-2 bg-data" /> Entrada
          </span>
          <span className="flex items-center gap-1.5">
            <span className="size-2 bg-negative" /> Gasto
          </span>
        </div>
      </div>

      {data.length === 0 ? (
        <WaitingBackend loading={loading} height="min-h-56 flex-1" />
      ) : (
        <div className="h-[264px]">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 0, bottom: 0, left: 0 }} barSize={28} barGap={6}>
              <CartesianGrid stroke={CHART.grid} strokeDasharray="3 4" vertical={false} />
              <XAxis
                dataKey="month"
                tickLine={false}
                axisLine={{ stroke: 'rgba(255,255,255,0.2)' }}
                height={48}
                interval={0}
                tick={<MonthTick nets={nets} bestIndex={bestIndex} valuesVisible={valuesVisible} />}
              />
              <YAxis
                tickLine={false}
                axisLine={false}
                width={40}
                tick={{ fontFamily: 'Space Mono', fontSize: 11, fill: CHART.axis }}
                tickFormatter={(v: number) => (Math.abs(v) >= 1000 ? `${Math.round(v / 1000)}k` : String(v))}
              />
              <Tooltip
                cursor={{ fill: 'rgba(255,255,255,0.04)' }}
                contentStyle={{
                  background: CHART.surface,
                  border: `1px solid ${CHART.edge}`,
                  borderRadius: 0,
                  fontSize: 12,
                }}
                labelStyle={{ fontFamily: 'Space Mono', color: CHART.axis, marginBottom: 4 }}
                labelFormatter={(label) => monthLabel(String(label))}
                formatter={(value, name) => [format(Number(value)), name === 'income' ? 'Entrada' : 'Gasto']}
              />
              <Bar dataKey="income" fill={CHART.income} radius={0} />
              <Bar dataKey="expense" fill={CHART.expense} radius={0} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  )
}
