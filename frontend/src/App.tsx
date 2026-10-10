import { useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'
import { Accounts } from '@/pages/Accounts'
import { Settings } from '@/pages/Settings'
import { Login } from '@/pages/Login'
import { Register } from '@/pages/Register'
import { useAuth } from '@/hooks/useAuth'
import { CurrencyProvider } from '@/contexts/CurrencyContext'
import type { View } from '@/lib/types'

function Placeholder({ title }: { title: string }) {
  return (
    <main className="bg-page-grid flex flex-1 flex-col items-center justify-center gap-2 px-10 py-9">
      <p className="text-sm text-fg-muted">{title} — em breve.</p>
      <p className="font-mono text-xs text-fg-subtle">Essa seção ainda está sendo construída.</p>
    </main>
  )
}

export default function App() {
  const [view, setView] = useState<View>('dashboard')
  const [authView, setAuthView] = useState<'login' | 'register'>('login')
  const auth = useAuth()

  if (!auth.isAuthenticated) {
    return (
      <div className="flex h-screen bg-background text-fg">
        {authView === 'login' ? (
          <Login auth={auth} onSwitchToRegister={() => setAuthView('register')} />
        ) : (
          <Register auth={auth} onSwitchToLogin={() => setAuthView('login')} />
        )}
      </div>
    )
  }

  return (
    <CurrencyProvider>
      <div className="flex h-screen bg-background text-fg">
        <Sidebar active={view} onNavigate={setView} onLogout={auth.logout} />
        {view === 'dashboard' && <Dashboard />}
        {view === 'accounts' && <Accounts />}
        {view === 'reports' && <Placeholder title="Relatórios" />}
        {view === 'recurring' && <Placeholder title="Recorrentes" />}
        {view === 'settings' && <Settings />}
      </div>
    </CurrencyProvider>
  )
}
