import { useState } from 'react'
import { Check, Coins } from 'lucide-react'
import { CURRENCIES } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

/**
 * Botão de moeda (abaixo da carteira no card de saldo). Seleciona a moeda de
 * exibição de todo o dashboard — BRL / EUR / USD.
 */
export function CurrencySelector() {
  const { currency, setCurrency } = useCurrency()
  const [open, setOpen] = useState(false)

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="Moeda de exibição"
        aria-expanded={open}
        className={cn(
          'flex size-8 items-center justify-center rounded-lg transition-colors',
          open ? 'text-brand' : 'text-fg-muted hover:text-brand',
        )}
      >
        <Coins className="size-5" strokeWidth={1.8} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-[calc(100%+0.5rem)] z-20 flex w-40 flex-col rounded-xl border border-edge bg-surface p-1 shadow-xl">
            {CURRENCIES.map((c) => (
              <button
                key={c.code}
                type="button"
                onClick={() => {
                  setCurrency(c.code)
                  setOpen(false)
                }}
                className={cn(
                  'flex items-center justify-between rounded-lg px-3 py-2 text-left text-sm transition-colors',
                  currency === c.code
                    ? 'text-brand'
                    : 'text-fg-muted hover:bg-surface-raised hover:text-fg',
                )}
              >
                <span>
                  <span className="font-mono">{c.symbol}</span> · {c.label}
                </span>
                {currency === c.code && <Check className="size-4" strokeWidth={2} />}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
