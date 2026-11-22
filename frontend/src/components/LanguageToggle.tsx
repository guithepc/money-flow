import type { Lang } from '@/lib/i18n'
import { cn } from '@/lib/utils'

/** Seletor PT | EN (Termos, Configurações). */
export function LanguageToggle({ value, onChange, disabled = false }: { value: Lang; onChange: (lang: Lang) => void; disabled?: boolean }) {
  return (
    <div role="group" aria-label="Idioma / Language" className="flex w-fit border border-edge">
      {(['pt', 'en'] as const).map((code) => (
        <button
          key={code}
          type="button"
          onClick={() => onChange(code)}
          disabled={disabled}
          aria-pressed={value === code}
          className={cn(
            'label-mono px-3 py-1.5 transition-colors duration-150 disabled:opacity-60',
            value === code ? 'bg-brand text-black' : 'text-fg-muted hover:text-fg',
          )}
        >
          {code}
        </button>
      ))}
    </div>
  )
}
