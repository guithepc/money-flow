import { useState } from 'react'
import { ArrowDownRight, ArrowUpRight, Eye, EyeOff, Scale } from 'lucide-react'
import { AccountSwitcher } from '@/components/AccountSwitcher'
import { KpiStrip } from '@/components/KpiStrip'
import { CategoryDonut } from '@/components/CategoryDonut'
import { BalanceHistoryChart } from '@/components/BalanceHistoryChart'
import { IncomeExpenseChart } from '@/components/IncomeExpenseChart'
import { RecentTransactions } from '@/components/RecentTransactions'
import { PeriodPicker } from '@/components/PeriodPicker'
import { CurrencySelector } from '@/components/CurrencySelector'
import { useCurrency } from '@/contexts/CurrencyContext'
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

  const { valuesVisible, toggleValuesVisible } = useCurrency()
  const accounts = useAccounts()
  const { data, loading, error } = useReports(appliedRange, accountId)
  const transactions = useTransactions(appliedRange, accountId)
  const balance = data?.balance ?? 0

  return (
    <main className="bg-page-grid flex-1 overflow-y-auto px-10 pt-9 pb-10">
      <header className="mb-7 flex flex-wrap items-end justify-between gap-6">
        <div className="flex flex-col gap-2.5">
          <span className="eyebrow">Visão geral</span>
          <div className="flex items-center gap-4">
            <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em] text-fg">
              Dashboard
            </h1>
            <button
              type="button"
              onClick={toggleValuesVisible}
              className="flex size-8 items-center justify-center border border-edge text-fg-muted transition-colors duration-150 hover:border-white/30 hover:text-fg"
              title={valuesVisible ? 'Ocultar valores' : 'Mostrar valores'}
              aria-label={valuesVisible ? 'Ocultar valores' : 'Mostrar valores'}
            >
              {valuesVisible ? (
                <Eye className="size-4" strokeWidth={1.8} />
              ) : (
                <EyeOff className="size-4" strokeWidth={1.8} />
              )}
            </button>
          </div>
          <p className="text-[15px] text-fg-muted">Suas finanças organizadas em 5 segundos.</p>
        </div>
        <div className="flex flex-wrap items-stretch gap-3">
          <PeriodPicker
            range={draftRange}
            onChange={setDraftRange}
            onSearch={() => setAppliedRange(draftRange)}
            onApply={(r) => {
              setDraftRange(r)
              setAppliedRange(r)
            }}
          />
          <CurrencySelector />
        </div>
      </header>

      {error && (
        <div className="mb-6 border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
          Não foi possível carregar os dados: {error}
        </div>
      )}

      {/* Saldo (switcher de contas) + histórico no tempo */}
      <section className="mb-7 grid grid-cols-1 gap-6 xl:grid-cols-[400px_1fr]">
        <AccountSwitcher
          accounts={accounts.data}
          selectedId={selectedAccountId}
          onSelect={setSelectedAccountId}
          loading={accounts.loading}
        />
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
      </section>

      <div className="mb-7">
        <KpiStrip
          loading={loading}
          items={[
            { label: 'Entrada', value: data?.totalIncome ?? 0, icon: ArrowUpRight, tone: 'data' },
            { label: 'Gasto', value: data?.totalSpent ?? 0, icon: ArrowDownRight, tone: 'negative' },
            {
              label: 'Saldo do período',
              value: balance,
              icon: Scale,
              tone: balance < 0 ? 'negative' : 'neutral',
            },
          ]}
        />
      </div>

      {/* Esquerda: categorias + comparativo · Direita: transações por dia */}
      <section className="grid grid-cols-1 gap-6 xl:grid-cols-[1fr_420px]">
        <div className="flex min-w-0 flex-col gap-6">
          <CategoryDonut data={data?.ranking ?? []} loading={loading} />
          <IncomeExpenseChart data={data?.monthlyIncomeExpense ?? []} loading={loading} />
        </div>
        <RecentTransactions data={transactions.data} loading={transactions.loading} />
      </section>
    </main>
  )
}
