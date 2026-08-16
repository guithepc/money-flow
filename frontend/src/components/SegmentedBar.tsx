import { cn } from '@/lib/utils'

interface SegmentedBarProps {
  /** Proporção preenchida, 0..1 */
  ratio: number
  /** Quantidade de traços */
  segments?: number
}

/**
 * Barra segmentada — elemento-assinatura (ref. imagem 2).
 * Traços verticais: preenchidos em verde, vazios em cinza.
 */
export function SegmentedBar({ ratio, segments = 28 }: SegmentedBarProps) {
  const filled = Math.round(Math.min(Math.max(ratio, 0), 1) * segments)

  return (
    <div className="flex items-end gap-[3px]" aria-hidden>
      {Array.from({ length: segments }).map((_, i) => (
        <span
          key={i}
          className={cn(
            'h-4 w-[3px] rounded-full transition-colors',
            i < filled ? 'bg-data' : 'bg-edge',
          )}
        />
      ))}
    </div>
  )
}
