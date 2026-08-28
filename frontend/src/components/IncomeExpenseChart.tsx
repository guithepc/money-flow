import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MonthlyIncomeExpense } from '@/lib/types'
import { CHART } from '@/lib/chartColors'
import { useCurrency } from '@/contexts/CurrencyContext'
import { WaitingBackend } from './BalanceHistoryChart'

interface IncomeExpenseChartProps {
  data?: MonthlyIncomeExpense[]
  loading?: boolean
}

/**
 * Renda × gasto por mês. O endpoint (`income-expense-monthly`) ainda não existe
 * no backend — enquanto isso, exibe estado "aguardando backend".
 */
export function IncomeExpenseChart({ data = [], loading = false }: IncomeExpenseChartProps) {
  const { format } = useCurrency()
  return (
    <section className="flex h-full flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
      <div className="flex flex-col gap-1">
        <span className="eyebrow text-brand">Comparativo</span>
        <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
          Renda e gastos
        </h2>
      </div>

      {data.length === 0 ? (
        <WaitingBackend loading={loading} height="min-h-56 flex-1" />
      ) : (
        <div className="min-h-56 flex-1">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
              <CartesianGrid stroke={CHART.grid} vertical={false} />
              <XAxis dataKey="month" stroke={CHART.axis} tickLine={false} fontSize={11} />
              <YAxis stroke={CHART.axis} tickLine={false} fontSize={11} width={48} />
              <Tooltip
                cursor={{ fill: CHART.grid, opacity: 0.3 }}
                contentStyle={{
                  background: CHART.surface,
                  border: `1px solid ${CHART.grid}`,
                  borderRadius: 12,
                }}
                formatter={(value) => format(Number(value))}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Bar dataKey="income" name="Renda" fill={CHART.income} radius={[4, 4, 0, 0]} />
              <Bar dataKey="expense" name="Gasto" fill={CHART.expense} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  )
}
