import { useState } from 'react'
import { ArrowDownRight, ArrowUpRight, Repeat, Scale } from 'lucide-react'
import { AccountSwitcher } from '@/components/AccountSwitcher'
import { KpiCard } from '@/components/KpiCard'
import { CategoryDonut } from '@/components/CategoryDonut'
import { BalanceHistoryChart } from '@/components/BalanceHistoryChart'
import { IncomeExpenseChart } from '@/components/IncomeExpenseChart'
import { RecentTransactions } from '@/components/RecentTransactions'
import { PeriodPicker } from '@/components/PeriodPicker'
import { useReports } from '@/hooks/useReports'
import { useAccounts } from '@/hooks/useAccounts'
import { useTransactions } from '@/hooks/useTransactions'
import type { DateRange } from '@/lib/types'
import { currentMonthRange } from '@/lib/utils'

export function Dashboard() {
  // draft = o que está nos inputs; applied = o que dispara o fetch (ao clicar na lupa).
  const [draftRange, setDraftRange] = useState<DateRange>(currentMonthRange)
  const [appliedRange, setAppliedRange] = useState<DateRange>(draftRange)
  // null = "Todas as contas" (agregado). Ao selecionar, os hooks repassam accountId.
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(null)
  const accountId = selectedAccountId ?? undefined

  const accounts = useAccounts()
  const { data, loading, error } = useReports(appliedRange, accountId)
  const transactions = useTransactions(appliedRange, accountId)

  return (
    <main className="flex-1 overflow-y-auto px-8 py-8">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <span className="eyebrow text-brand">Visão geral</span>
          <h1 className="font-serif text-4xl font-semibold tracking-tight text-fg">
            Dashboard
          </h1>
          <p className="text-sm text-fg-muted">
            Suas finanças organizadas em 2 segundos.
          </p>
        </div>
        <PeriodPicker
          range={draftRange}
          onChange={setDraftRange}
          onSearch={() => setAppliedRange(draftRange)}
          onApply={(r) => {
            setDraftRange(r)
            setAppliedRange(r)
          }}
        />
      </header>

      {error && (
        <div className="mb-6 rounded-xl border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
          Não foi possível carregar os dados: {error}
        </div>
      )}

      {/* Contas (switcher) + histórico no tempo */}
      <section className="mb-6 grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="h-full xl:col-span-1">
          <AccountSwitcher
            accounts={accounts.data}
            selectedId={selectedAccountId}
            onSelect={setSelectedAccountId}
            loading={accounts.loading}
          />
        </div>
        <div className="xl:col-span-2">
          <BalanceHistoryChart
            data={data?.balanceHistory}
            loading={loading}
            range={appliedRange}
            currentBalance={
              selectedAccountId == null
                ? accounts.data.reduce((sum, a) => sum + a.amount, 0)
                : (accounts.data.find((a) => a.accountId === selectedAccountId)?.amount ?? 0)
            }
          />
        </div>
      </section>

      {/* KPIs — como antes */}
      <section className="mb-6 grid grid-cols-1 gap-6 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Entrada"
          value={data?.totalIncome ?? 0}
          icon={ArrowUpRight}
          tone="data"
          loading={loading}
        />
        <KpiCard
          label="Gasto"
          value={data?.totalSpent ?? 0}
          icon={ArrowDownRight}
          tone="negative"
          loading={loading}
        />
        <KpiCard
          label="Recorrente"
          value={data?.totalRecurring ?? 0}
          icon={Repeat}
          tone="neutral"
          loading={loading}
        />
        <KpiCard
          label="Saldo"
          value={data?.balance ?? 0}
          icon={Scale}
          tone={(data?.balance ?? 0) < 0 ? 'negative' : 'data'}
          loading={loading}
          glow
        />
      </section>

      {/* Esquerda: categorias (donut) + comparativo · Direita: transações */}
      <section className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="flex flex-col gap-6 xl:col-span-2">
          <CategoryDonut data={data?.ranking ?? []} loading={loading} />
          <div className="flex flex-1 flex-col">
            <IncomeExpenseChart data={data?.monthlyIncomeExpense ?? []} loading={loading} />
          </div>
        </div>
        <div className="h-full xl:col-span-1">
          <RecentTransactions data={transactions.data} loading={transactions.loading} />
        </div>
      </section>
    </main>
  )
}
