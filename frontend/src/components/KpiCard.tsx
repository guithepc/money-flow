import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { useCurrency } from '@/contexts/CurrencyContext'

type Tone = 'data' | 'negative' | 'neutral'

interface KpiCardProps {
  label: string
  value: number
  icon: LucideIcon
  tone?: Tone
  loading?: boolean
  /** Halo rainbow em volta do card (estilo botão ngrok). */
  glow?: boolean
}

const RAINBOW =
  'conic-gradient(from 180deg, #f6d365, #a1ffce, #7ee8fa, #b79df6, #f6a6c1, #f6d365)'

const toneClasses: Record<Tone, string> = {
  data: 'text-data',
  negative: 'text-negative',
  neutral: 'text-fg',
}

export function KpiCard({
  label,
  value,
  icon: Icon,
  tone = 'neutral',
  loading = false,
  glow = false,
}: KpiCardProps) {
  const { format } = useCurrency()
  return (
    <div className="relative">
      {glow && (
        <div
          aria-hidden
          className="pointer-events-none absolute -inset-[2px] rounded-2xl opacity-35 blur-md"
          style={{ background: RAINBOW }}
        />
      )}
      <div className="relative flex flex-col gap-4 rounded-2xl border border-edge bg-surface p-5">
      <div className="flex items-center justify-between">
        <span className="eyebrow text-fg-muted">{label}</span>
        <span className="flex size-8 items-center justify-center rounded-lg bg-surface-raised text-fg-muted">
          <Icon className="size-4" strokeWidth={1.8} />
        </span>
      </div>
      {loading ? (
        <div className="h-9 w-32 animate-pulse rounded-md bg-surface-raised" />
      ) : (
        <span
          className={cn(
            'font-rounded text-3xl font-bold tabular-nums tracking-tight',
            toneClasses[tone],
          )}
        >
          {format(value)}
        </span>
      )}
      </div>
    </div>
  )
}
