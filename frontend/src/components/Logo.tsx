import { cn } from '@/lib/utils'

/**
 * Marca do Money Flow (círculo com a onda), a mesma da landing e do favicon.
 * Usa `currentColor`: herda a cor do texto ao redor.
 */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 200 200" aria-hidden className={cn('size-7 shrink-0', className)}>
      <circle cx="100" cy="100" r="62" fill="none" stroke="currentColor" strokeWidth="18" />
      <path
        d="M41.2 112 C66 92 80 132 100 112 C120 92 134 132 158.8 112 A60 60 0 0 1 41.2 112 Z"
        fill="currentColor"
      />
    </svg>
  )
}

/** Marca + nome, como no topo da landing. */
export function Logo({ className }: { className?: string }) {
  return (
    <span className={cn('flex items-center gap-2.5', className)}>
      <LogoMark />
      <span className="font-display text-lg font-semibold tracking-[-0.02em]">money flow</span>
    </span>
  )
}
