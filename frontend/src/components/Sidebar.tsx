import {
  LayoutGrid,
  Wallet,
  BarChart3,
  Settings,
  LogOut,
  TrendingUp,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import type { View } from '@/lib/types'

const NAV: { icon: typeof LayoutGrid; label: string; view: View }[] = [
  { icon: LayoutGrid, label: 'Dashboard', view: 'dashboard' },
  { icon: Wallet, label: 'Contas', view: 'accounts' },
  { icon: BarChart3, label: 'Relatórios', view: 'reports' },
]

interface SidebarProps {
  active: View
  onNavigate: (view: View) => void
  onLogout: () => void
}

function NavButton({
  icon: Icon,
  label,
  active,
  onClick,
}: {
  icon: typeof LayoutGrid
  label: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      aria-current={active ? 'page' : undefined}
      onClick={onClick}
      className={cn(
        'relative flex h-12 w-full items-center justify-center transition-colors duration-150',
        active ? 'bg-brand-soft text-brand' : 'text-fg-subtle hover:text-fg',
      )}
    >
      {active && <span aria-hidden className="absolute inset-y-0 left-0 w-[3px] bg-brand" />}
      <Icon className="size-5" strokeWidth={1.8} />
    </button>
  )
}

export function Sidebar({ active, onNavigate, onLogout }: SidebarProps) {
  return (
    <aside className="flex w-[72px] shrink-0 flex-col items-center gap-8 border-r border-edge bg-background py-6">
      {/* Logo — quadrado lime com a seta de tendência preta */}
      <div className="flex size-7 items-center justify-center bg-brand">
        <TrendingUp className="size-4 text-black" strokeWidth={2.6} />
      </div>

      <nav className="flex w-full flex-1 flex-col">
        {NAV.map(({ icon, label, view }) => (
          <NavButton
            key={view}
            icon={icon}
            label={label}
            active={active === view}
            onClick={() => onNavigate(view)}
          />
        ))}
      </nav>

      <div className="flex w-full flex-col">
        <NavButton
          icon={Settings}
          label="Configurações"
          active={active === 'settings'}
          onClick={() => onNavigate('settings')}
        />
        <button
          type="button"
          title="Sair"
          aria-label="Sair"
          onClick={onLogout}
          className="flex h-12 w-full items-center justify-center text-fg-subtle transition-colors duration-150 hover:text-negative"
        >
          <LogOut className="size-5" strokeWidth={1.8} />
        </button>
      </div>
    </aside>
  )
}
