import { Wallet } from 'lucide-react'
import { KpiStrip } from '@/components/KpiStrip'
import { useAccounts } from '@/hooks/useAccounts'
import { useCurrency } from '@/contexts/CurrencyContext'
import { cn } from '@/lib/utils'

export function Accounts() {
  const { data, loading, error } = useAccounts()
  const { format } = useCurrency()

  const total = data.reduce((acc, a) => acc + a.amount, 0)

  return (
    <main className="bg-page-grid flex-1 overflow-y-auto px-10 pt-9 pb-10">
      <header className="mb-7 flex flex-col gap-2.5">
        <span className="eyebrow">Carteira</span>
        <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em] text-fg">
          Contas
        </h1>
        <p className="text-[15px] text-fg-muted">Saldo consolidado de todas as suas contas.</p>
      </header>

      {error && (
        <div className="mb-6 border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
          Não foi possível carregar as contas: {error}
        </div>
      )}

      <div className="mb-7 max-w-xl">
        <KpiStrip
          loading={loading}
          items={[
            { label: 'Saldo total', value: total, icon: Wallet, tone: total < 0 ? 'negative' : 'neutral' },
          ]}
        />
      </div>

      <section className="flex flex-col border border-edge bg-surface">
        <div className="flex items-end justify-between gap-4 px-6 pt-6 pb-4">
          <div className="flex flex-col gap-1.5">
            <span className="eyebrow">Suas contas</span>
            <h2 className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
              Onde está o dinheiro
            </h2>
          </div>
          {!loading && data.length > 0 && (
            <span className="font-mono text-[11px] text-fg-subtle">
              {data.length} {data.length === 1 ? 'conta' : 'contas'}
            </span>
          )}
        </div>

        {loading ? (
          <div className="flex flex-col gap-px px-6 pb-6">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-16 animate-pulse bg-surface-raised" />
            ))}
          </div>
        ) : data.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
            <p className="text-sm text-fg-muted">Nenhuma conta cadastrada ainda.</p>
            <p className="font-mono text-xs text-fg-subtle">
              Mande um áudio para o bot para registrar sua primeira transação.
            </p>
          </div>
        ) : (
          <ul className="flex flex-col border-t border-white/8">
            {data.map((account) => {
              const share = total > 0 ? (account.amount / total) * 100 : 0
              return (
                <li
                  key={account.accountId}
                  className="flex h-16 items-center gap-4 border-b border-white/8 px-6 transition-colors duration-150 last:border-b-0 hover:bg-white/[0.03]"
                >
                  <span className="flex size-8 shrink-0 items-center justify-center border border-edge">
                    <Wallet className="size-4 text-fg-muted" strokeWidth={1.8} />
                  </span>
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="truncate text-sm font-semibold text-fg">{account.description}</span>
                    <span className="font-mono text-[11px] text-fg-subtle">
                      •••• {1000 + (account.accountId % 9000)}
                    </span>
                  </div>
                  <span className="hidden w-16 shrink-0 text-right font-mono text-xs text-fg-subtle sm:block">
                    {share.toFixed(0)}%
                  </span>
                  <span
                    className={cn(
                      'w-36 shrink-0 text-right font-display text-[15px] font-medium tabular-nums',
                      account.amount < 0 ? 'text-negative' : 'text-fg',
                    )}
                  >
                    {format(account.amount)}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </main>
  )
}
