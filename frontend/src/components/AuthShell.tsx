import type { ReactNode } from 'react'
import { ArrowRight, ShoppingBag, TrendingUp } from 'lucide-react'
import { cn } from '@/lib/utils'

/** Classes compartilhadas pelos campos e botões das telas de auth (fundo claro). */
export const authInput =
  'w-full border border-ink/12 bg-white px-3.5 py-3 text-sm text-ink outline-none transition-colors duration-150 placeholder:text-ink/35 focus:border-ink'

export const authLabel = 'text-[13px] font-medium text-ink/70'

export function AuthSubmit({ children, disabled }: { children: ReactNode; disabled?: boolean }) {
  return (
    <button
      type="submit"
      disabled={disabled}
      className="mt-2 flex items-center justify-center gap-2.5 bg-brand px-5 py-3.5 text-sm font-semibold uppercase tracking-[0.04em] text-black transition-opacity duration-150 hover:opacity-90 disabled:opacity-50"
    >
      {children}
      <ArrowRight className="size-4" strokeWidth={2.2} />
    </button>
  )
}

export function AuthError({ message }: { message: string }) {
  return (
    <div className="border border-[#C2410C]/30 bg-[#C2410C]/8 px-4 py-3 text-sm text-[#9A3412]">
      {message}
    </div>
  )
}

interface AuthShellProps {
  eyebrow: string
  title: string
  subtitle: string
  children: ReactNode
}

/**
 * Tela dividida: formulário sobre papel à esquerda, painel "Tinta" com um card
 * de saldo de exemplo à direita.
 */
export function AuthShell({ eyebrow, title, subtitle, children }: AuthShellProps) {
  return (
    <div className="flex flex-1">
      <main className="flex flex-1 flex-col bg-paper px-8 py-8 text-ink sm:px-16">
        <div className="flex items-center gap-2.5">
          <span className="flex size-7 items-center justify-center bg-brand">
            <TrendingUp className="size-4 text-black" strokeWidth={2.6} />
          </span>
          <span className="font-display text-lg font-semibold tracking-[-0.02em]">money flow</span>
        </div>

        <div className="flex flex-1 items-center">
          <div className="w-full max-w-sm">
            <div className="mb-8 flex flex-col gap-3">
              <span className="eyebrow text-indigo">{eyebrow}</span>
              <h1 className="font-display text-5xl leading-[52px] font-normal tracking-[-0.02em]">
                {title}
              </h1>
              <p className="text-[15px] text-ink/65">{subtitle}</p>
            </div>
            {children}
          </div>
        </div>
      </main>

      <aside
        aria-hidden
        className="bg-page-grid relative hidden flex-1 items-center justify-center border-l border-edge bg-background px-12 lg:flex"
      >
        <SampleBalanceCard />
      </aside>
    </div>
  )
}

/** Card decorativo — mostra a promessa "falou, tá no painel". */
function SampleBalanceCard() {
  return (
    <div className="flex w-full max-w-md flex-col border border-edge bg-surface">
      <div className="flex flex-col gap-4 p-6">
        <div className="flex items-center justify-between">
          <span className="eyebrow">Gasto em outubro</span>
          <span className="bg-brand px-2 py-1 font-mono text-[11px] font-bold text-black">+ R$ 50</span>
        </div>
        <span className="font-display text-[44px] leading-[48px] font-medium tracking-[-0.02em] tabular-nums text-fg">
          R$ 1.284,90
        </span>
        <svg viewBox="0 0 300 80" className="h-20 w-full" preserveAspectRatio="none">
          <path
            d="M0 72 L30 70 L60 64 L90 66 L120 56 L150 58 L180 46 L210 44 L240 36 L270 34 L292 18 L292 80 L0 80 Z"
            fill="rgba(226,255,46,0.12)"
          />
          <path
            d="M0 72 L30 70 L60 64 L90 66 L120 56 L150 58 L180 46 L210 44 L240 36 L270 34 L292 18"
            fill="none"
            stroke="#E2FF2E"
            strokeWidth="2.5"
            vectorEffect="non-scaling-stroke"
          />
          <rect x="288" y="14" width="8" height="8" fill="#E2FF2E" />
        </svg>
      </div>
      <div className={cn('flex items-center gap-3 border-t border-brand/50 bg-brand-soft px-6 py-4')}>
        <span className="flex size-8 items-center justify-center border border-brand/50">
          <ShoppingBag className="size-4 text-brand" strokeWidth={1.8} />
        </span>
        <div className="flex flex-1 flex-col gap-0.5">
          <span className="text-sm font-semibold text-fg">Carne</span>
          <span className="font-mono text-[11px] text-fg-subtle">Mercado · Santander · agora, por áudio</span>
        </div>
        <span className="font-display text-[15px] font-medium tabular-nums text-fg">−R$ 50,00</span>
      </div>
    </div>
  )
}
