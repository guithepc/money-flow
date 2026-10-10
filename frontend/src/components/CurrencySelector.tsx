import { useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import { CURRENCIES } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

/**
 * Seletor de moeda do header. Seleciona a moeda de exibição de todo o
 * dashboard — BRL / EUR / USD.
 */
export function CurrencySelector() {
  const { currency, setCurrency } = useCurrency()
  const [open, setOpen] = useState(false)

  return (
    <div className="relative flex">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="Moeda de exibição"
        aria-expanded={open}
        className={cn(
          'flex items-center gap-2 border px-3.5 font-mono text-xs transition-colors duration-150',
          open ? 'border-brand text-fg' : 'border-edge text-fg hover:border-white/30',
        )}
      >
        {currency}
        <ChevronDown className="size-3 text-fg-muted" strokeWidth={2} />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 top-[calc(100%+0.5rem)] z-20 flex w-44 flex-col border border-edge bg-surface-raised">
            {CURRENCIES.map((c) => (
              <button
                key={c.code}
                type="button"
                onClick={() => {
                  setCurrency(c.code)
                  setOpen(false)
                }}
                className={cn(
                  'flex items-center justify-between px-3.5 py-2.5 text-left text-sm transition-colors duration-150',
                  currency === c.code ? 'text-brand' : 'text-fg-muted hover:bg-white/5 hover:text-fg',
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
