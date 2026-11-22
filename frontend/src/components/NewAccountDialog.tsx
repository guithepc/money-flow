import { useEffect } from 'react'
import { X } from 'lucide-react'
import { AccountForm } from '@/components/AccountForm'
import type { Account } from '@/lib/types'

/** Contas → Nova conta: o mesmo formulário do onboarding, no visual dos diálogos do painel. */
export function NewAccountDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (account: Account) => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/70" onClick={onClose} aria-hidden />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-account-title"
        className="relative flex w-full max-w-md flex-col gap-5 border border-edge bg-surface p-6"
      >
        <div className="flex items-start justify-between gap-4">
          <div className="flex flex-col gap-1.5">
            <span className="eyebrow">Conta</span>
            <h2 id="new-account-title" className="font-display text-2xl font-normal tracking-[-0.02em] text-fg">
              Nova conta
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="flex size-8 shrink-0 items-center justify-center border border-edge text-fg-muted transition-colors duration-150 hover:border-white/30 hover:text-fg"
            aria-label="Fechar"
          >
            <X className="size-4" strokeWidth={1.8} />
          </button>
        </div>
        <AccountForm variant="dark" onCreated={onCreated} />
      </div>
    </div>
  )
}
