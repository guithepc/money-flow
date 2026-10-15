import { useState } from 'react'
import { Check, ChevronDown } from 'lucide-react'
import { CURRENCIES } from '@/lib/utils'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

/**
 * Filtro de moeda do header. Não há câmbio: escolher BRL mostra só as contas
 * em BRL (saldo, KPIs, gráficos, categorias). Lista apenas as moedas em que o
 * usuário tem contas e some quando há uma moeda só.
 */
export function CurrencySelector() {
  const { currency, currencies, setCurrency } = useCurrency()
  const [open, setOpen] = useState(false)
  const options = CURRENCIES.filter((c) => currencies.includes(c.code))

  if (options.length <= 1) return null

  return (
    <div className="relative flex">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label="Filtrar por moeda"
        title="Filtrar contas por moeda"
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
            {options.map((c) => (
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
