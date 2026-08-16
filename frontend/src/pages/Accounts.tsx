import { Wallet } from 'lucide-react'
import { KpiCard } from '@/components/KpiCard'
import { useAccounts } from '@/hooks/useAccounts'
import { formatCurrency } from '@/lib/utils'

export function Accounts() {
  const { data, loading, error } = useAccounts()

  const total = data.reduce((acc, a) => acc + a.amount, 0)

  return (
    <main className="flex-1 overflow-y-auto px-8 py-8">
      <header className="mb-8 flex flex-col gap-1">
        <span className="eyebrow text-brand">Carteira</span>
        <h1 className="font-serif text-4xl font-semibold tracking-tight text-fg">
          Contas
        </h1>
        <p className="text-sm text-fg-muted">
          Saldo consolidado de todas as suas contas.
        </p>
      </header>

      {error && (
        <div className="mb-6 rounded-xl border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
          Não foi possível carregar as contas: {error}
        </div>
      )}

      <section className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Saldo total"
          value={total}
          icon={Wallet}
          tone={total < 0 ? 'negative' : 'data'}
          loading={loading}
          glow
        />
      </section>

      <section className="flex flex-col gap-6 rounded-2xl border border-edge bg-surface p-6">
        <div className="flex flex-col gap-1">
          <span className="eyebrow text-brand">Suas contas</span>
          <h2 className="font-serif text-2xl font-semibold tracking-tight text-fg">
            Onde está o dinheiro
          </h2>
        </div>

        {loading ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse rounded-xl bg-surface-raised" />
            ))}
          </div>
        ) : data.length === 0 ? (
          <p className="py-8 text-center text-sm text-fg-muted">
            Nenhuma conta cadastrada ainda.
          </p>
        ) : (
          <ul className="flex flex-col gap-3">
            {data.map((account) => (
              <li
                key={account.accountId}
                className="flex items-center justify-between gap-4 rounded-xl border border-edge bg-surface-raised px-5 py-4"
              >
                <div className="flex items-center gap-3">
                  <span className="flex size-9 items-center justify-center rounded-lg bg-brand-soft text-brand">
                    <Wallet className="size-4" strokeWidth={1.8} />
                  </span>
                  <span className="text-sm font-medium text-fg">
                    {account.description}
                  </span>
                </div>
                <span className="font-mono text-sm tabular-nums text-fg-muted">
                  {formatCurrency(account.amount)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
