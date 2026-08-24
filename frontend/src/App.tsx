import { useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'
import { Accounts } from '@/pages/Accounts'
import { Login } from '@/pages/Login'
import { useAuth } from '@/hooks/useAuth'
import type { View } from '@/lib/types'

function Placeholder({ title }: { title: string }) {
  return (
    <main className="flex flex-1 items-center justify-center px-8 py-8">
      <p className="text-sm text-fg-muted">{title} — em breve.</p>
    </main>
  )
}

export default function App() {
  const [view, setView] = useState<View>('dashboard')
  const auth = useAuth()

  if (!auth.isAuthenticated) {
    return (
      <div className="flex h-screen bg-background text-fg">
        <Login auth={auth} />
      </div>
    )
  }

  return (
    <div className="flex h-screen bg-background text-fg">
      <Sidebar active={view} onNavigate={setView} onLogout={auth.logout} />
      {view === 'dashboard' && <Dashboard />}
      {view === 'accounts' && <Accounts />}
      {view === 'reports' && <Placeholder title="Relatórios" />}
      {view === 'recurring' && <Placeholder title="Recorrentes" />}
    </div>
  )
}
