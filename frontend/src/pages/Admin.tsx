import { useEffect, useState } from 'react'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { adminApi } from '@/lib/api'
import { CHART } from '@/lib/chartColors'
import type { AdminMetrics } from '@/lib/types'

const AXIS_TICK = { fontFamily: 'Space Mono', fontSize: 11, fill: CHART.axis }
const nf = new Intl.NumberFormat('pt-BR')

function Stat({ label, value, sub }: { label: string; value: number; sub?: string }) {
  return (
    <div className="flex flex-col gap-1 border border-edge bg-surface px-5 py-4">
      <span className="label-mono text-[11px] text-fg-muted">{label}</span>
      <span className="font-display text-3xl font-normal tabular-nums text-fg">{nf.format(value)}</span>
      {sub && <span className="font-mono text-[11px] text-fg-subtle">{sub}</span>}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-3">
      <span className="eyebrow">{title}</span>
      <div className="grid grid-cols-2 gap-px sm:grid-cols-3 xl:grid-cols-4">{children}</div>
    </section>
  )
}

export function Admin() {
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    adminApi
      .metrics()
      .then((m) => active && setMetrics(m))
      .catch(() => active && setError('Não foi possível carregar a volumetria.'))
    return () => {
      active = false
    }
  }, [])

  return (
    <main className="bg-page-grid flex-1 overflow-y-auto px-10 pt-9 pb-10">
      <header className="mb-7 flex flex-col gap-2.5">
        <span className="eyebrow">Admin</span>
        <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em] text-fg">
          Volumetria
        </h1>
        <p className="text-[15px] text-fg-muted">Uso do produto — usuários, transações e contas.</p>
      </header>

      {error && (
        <div className="mb-6 border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">{error}</div>
      )}

      {!metrics && !error && (
        <div className="grid grid-cols-2 gap-px sm:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="h-24 animate-pulse bg-surface-raised" />
          ))}
        </div>
      )}

      {metrics && (
        <div className="flex flex-col gap-8">
          <Section title="Usuários">
            <Stat label="Total" value={metrics.totalUsers} />
            <Stat label="Novos · 7d" value={metrics.newUsers7d} sub={`${nf.format(metrics.newUsers30d)} em 30d`} />
            <Stat label="Ativos · 7d" value={metrics.activeUsers7d} sub={`${nf.format(metrics.activeUsers30d)} em 30d`} />
            <Stat label="Telegram vinculado" value={metrics.usersWithTelegram} sub={`${nf.format(metrics.usersAcceptedTerms)} aceitaram termos`} />
          </Section>

          <Section title="Transações">
            <Stat label="Total" value={metrics.totalTransactions} />
            <Stat label="Hoje" value={metrics.transactionsToday} />
            <Stat label="Últimos 7d" value={metrics.transactions7d} sub={`${nf.format(metrics.transactions30d)} em 30d`} />
            <Stat label="Média/dia · 30d" value={Math.round(metrics.transactions30d / 30)} />
            <Stat label="Gastos" value={metrics.expenseCount} />
            <Stat label="Entradas" value={metrics.incomeCount} />
            <Stat label="Transferências" value={metrics.transferCount} />
            <Stat label="Contas" value={metrics.totalAccounts} sub={metrics.accountsByCurrency.map((c) => `${c.count} ${c.currency}`).join(' · ') || undefined} />
          </Section>

          <section className="flex flex-col gap-3">
            <span className="eyebrow">Últimos 30 dias</span>
            <div className="border border-edge bg-surface p-5">
              <div className="mb-3 flex items-center gap-5 font-mono text-[11px] text-fg-muted">
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5" style={{ background: CHART.brand }} /> Transações
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5" style={{ background: CHART.income }} /> Cadastros
                </span>
              </div>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart data={metrics.last30Days} margin={{ top: 4, right: 8, bottom: 0, left: -16 }}>
                    <CartesianGrid stroke={CHART.grid} strokeDasharray="3 4" vertical={false} />
                    <XAxis
                      dataKey="date"
                      tick={AXIS_TICK}
                      tickLine={false}
                      axisLine={{ stroke: CHART.edge }}
                      tickFormatter={(d: string) => d.slice(8)}
                      interval={4}
                    />
                    <YAxis tick={AXIS_TICK} tickLine={false} axisLine={false} allowDecimals={false} width={32} />
                    <Tooltip
                      cursor={{ fill: 'rgba(255,255,255,0.04)' }}
                      contentStyle={{ background: CHART.surface, border: `1px solid ${CHART.edge}` }}
                      labelStyle={{ fontFamily: 'Space Mono', color: CHART.axis, marginBottom: 4 }}
                      itemStyle={{ color: '#fff' }}
                    />
                    <Bar dataKey="transactions" name="Transações" fill={CHART.brand} radius={[2, 2, 0, 0]} maxBarSize={18} />
                    <Line dataKey="signups" name="Cadastros" stroke={CHART.income} strokeWidth={2} dot={false} />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
            </div>
          </section>
        </div>
      )}
    </main>
  )
}
