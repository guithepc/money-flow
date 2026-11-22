import {
  Mic,
  LayoutGrid,
  Wallet,
  BarChart3,
  Settings,
  LogOut,
  Activity,
} from 'lucide-react'
import { LogoMark } from '@/components/Logo'
import { useLanguage } from '@/lib/i18n'
import { cn } from '@/lib/utils'
import type { View } from '@/lib/types'

const NAV: { icon: typeof LayoutGrid; view: View }[] = [
  { icon: Mic, view: 'capture' },
  { icon: LayoutGrid, view: 'dashboard' },
  { icon: Wallet, view: 'accounts' },
  { icon: BarChart3, view: 'reports' },
]

const LABELS = {
  pt: { capture: 'Registrar', dashboard: 'Dashboard', accounts: 'Contas', reports: 'Relatórios', settings: 'Configurações', admin: 'Admin', logout: 'Sair' },
  en: { capture: 'Register', dashboard: 'Dashboard', accounts: 'Accounts', reports: 'Reports', settings: 'Settings', admin: 'Admin', logout: 'Log out' },
} as const

interface SidebarProps {
  active: View
  onNavigate: (view: View) => void
  onLogout: () => void
  /** Mostra a aba Admin (volumetria) só para quem é admin. */
  admin?: boolean
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

export function Sidebar({ active, onNavigate, onLogout, admin = false }: SidebarProps) {
  const labels = LABELS[useLanguage().lang]
  return (
    <aside className="flex w-[72px] shrink-0 flex-col items-center gap-8 border-r border-edge bg-background py-6">
      <LogoMark className="text-fg" />

      <nav className="flex w-full flex-1 flex-col">
        {NAV.map(({ icon, view }) => (
          <NavButton
            key={view}
            icon={icon}
            label={labels[view as keyof typeof labels]}
            active={active === view}
            onClick={() => onNavigate(view)}
          />
        ))}
      </nav>

      <div className="flex w-full flex-col">
        {admin && (
          <NavButton
            icon={Activity}
            label={labels.admin}
            active={active === 'admin'}
            onClick={() => onNavigate('admin')}
          />
        )}
        <NavButton
          icon={Settings}
          label={labels.settings}
          active={active === 'settings'}
          onClick={() => onNavigate('settings')}
        />
        <button
          type="button"
          title={labels.logout}
          aria-label={labels.logout}
          onClick={onLogout}
          className="flex h-12 w-full items-center justify-center text-fg-subtle transition-colors duration-150 hover:text-negative"
        >
          <LogOut className="size-5" strokeWidth={1.8} />
        </button>
      </div>
    </aside>
  )
}
