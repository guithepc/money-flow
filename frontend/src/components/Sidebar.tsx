import {
  LayoutGrid,
  Wallet,
  BarChart3,
  Repeat,
  Settings,
  Sparkles,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type { View } from '@/lib/types'

const NAV: { icon: typeof LayoutGrid; label: string; view: View }[] = [
  { icon: LayoutGrid, label: 'Dashboard', view: 'dashboard' },
  { icon: Wallet, label: 'Contas', view: 'accounts' },
  { icon: BarChart3, label: 'Relatórios', view: 'reports' },
  { icon: Repeat, label: 'Recorrentes', view: 'recurring' },
]

interface SidebarProps {
  active: View
  onNavigate: (view: View) => void
}

export function Sidebar({ active, onNavigate }: SidebarProps) {
  return (
    <aside className="flex w-16 shrink-0 flex-col items-center gap-8 border-r border-edge bg-surface/40 py-6">
      {/* Logo — sparkle âmbar em badge, identidade da marca */}
      <div className="flex size-10 items-center justify-center rounded-xl bg-brand-soft">
        <Sparkles className="size-5 text-brand" strokeWidth={2.2} />
      </div>

      <nav className="flex flex-1 flex-col items-center gap-2">
        {NAV.map(({ icon: Icon, label, view }) => (
          <button
            key={label}
            title={label}
            onClick={() => onNavigate(view)}
            className={cn(
              'flex size-10 items-center justify-center rounded-xl transition-colors',
              active === view
                ? 'bg-surface-raised text-data'
                : 'text-fg-muted hover:bg-surface-raised hover:text-fg',
            )}
          >
            <Icon className="size-5" strokeWidth={1.8} />
          </button>
        ))}
      </nav>

      <button
        title="Configurações"
        className="flex size-10 items-center justify-center rounded-xl text-fg-muted transition-colors hover:bg-surface-raised hover:text-fg"
      >
        <Settings className="size-5" strokeWidth={1.8} />
      </button>
    </aside>
  )
}
