import { Calendar, Search } from 'lucide-react'
import type { DateRange } from '@/lib/types'
import { cn, presetRange, type PresetKey } from '@/lib/utils'

interface PeriodPickerProps {
  range: DateRange
  onChange: (range: DateRange) => void
  onSearch: () => void
  /** Aplica um range direto (usado pelos presets), setando draft + applied. */
  onApply: (range: DateRange) => void
}

const PRESETS: { key: PresetKey; label: string }[] = [
  { key: 'week', label: 'Semana' },
  { key: 'last30', label: '30 dias' },
  { key: 'month', label: 'Mês' },
  { key: 'year', label: 'Ano' },
]

function isSameRange(a: DateRange, b: DateRange) {
  return a.startDate === b.startDate && a.endDate === b.endDate
}

export function PeriodPicker({ range, onChange, onSearch, onApply }: PeriodPickerProps) {
  return (
    <div className="flex flex-wrap items-stretch gap-3">
      {/* Presets — controle segmentado, ativo em lime */}
      <div className="flex border border-edge" role="group" aria-label="Períodos rápidos">
        {PRESETS.map(({ key, label }, i) => {
          const active = isSameRange(range, presetRange(key))
          return (
            <button
              key={key}
              type="button"
              aria-pressed={active}
              onClick={() => onApply(presetRange(key))}
              className={cn(
                'px-3.5 py-2.5 font-mono text-xs uppercase tracking-[0.04em] transition-colors duration-150',
                i > 0 && 'border-l border-edge',
                active ? 'bg-brand font-bold text-black' : 'text-fg-muted hover:text-fg',
              )}
            >
              {label}
            </button>
          )
        })}
      </div>

      {/* Datas */}
      <div className="flex items-center gap-2.5 border border-edge px-3.5">
        <Calendar className="size-3.5 text-fg-muted" strokeWidth={1.8} />
        <input
          type="date"
          aria-label="Data inicial"
          value={range.startDate}
          max={range.endDate}
          onChange={(e) => onChange({ ...range, startDate: e.target.value })}
          className="bg-transparent font-mono text-xs text-fg outline-none [color-scheme:dark]"
        />
        <span className="text-fg-subtle">—</span>
        <input
          type="date"
          aria-label="Data final"
          value={range.endDate}
          min={range.startDate}
          onChange={(e) => onChange({ ...range, endDate: e.target.value })}
          className="bg-transparent font-mono text-xs text-fg outline-none [color-scheme:dark]"
        />
        <button
          type="button"
          onClick={onSearch}
          title="Buscar período"
          aria-label="Buscar período"
          className="ml-1 flex items-center text-fg-muted transition-colors duration-150 hover:text-brand"
        >
          <Search className="size-3.5" strokeWidth={2.2} />
        </button>
      </div>
    </div>
  )
}
