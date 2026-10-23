import { useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'
import { Accounts } from '@/pages/Accounts'
import { Settings } from '@/pages/Settings'
import { Login } from '@/pages/Login'
import { Register } from '@/pages/Register'
import { ForgotPassword } from '@/pages/ForgotPassword'
import { ResetPassword } from '@/pages/ResetPassword'
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

type AuthView = 'login' | 'register' | 'forgot' | 'reset'

/** Único caminho com URL própria: o link que o bot manda (`/reset-password?token=…`). */
const RESET_PATH = '/reset-password'

export default function App() {
  const [view, setView] = useState<View>('dashboard')
  const [authView, setAuthView] = useState<AuthView>(() =>
    window.location.pathname === RESET_PATH ? 'reset' : 'login',
  )
  const auth = useAuth()

  function goToAuth(next: AuthView) {
    // Ao sair da tela de reset, tira o token da barra de endereço e do histórico.
    if (next !== 'reset' && window.location.pathname !== '/') {
      window.history.replaceState(null, '', '/')
    }
    auth.clearError()
    setAuthView(next)
  }

  // Esqueci/redefinir a senha aparecem mesmo logado: abrir o link do bot é uma ação deliberada.
  const inPasswordFlow = authView === 'forgot' || authView === 'reset'

  if (!auth.isAuthenticated || inPasswordFlow) {
    return (
      <div className="flex h-screen bg-background text-fg">
        {authView === 'login' && (
          <Login
            auth={auth}
            onSwitchToRegister={() => goToAuth('register')}
            onForgotPassword={() => goToAuth('forgot')}
          />
        )}
        {authView === 'register' && (
          <Register auth={auth} onSwitchToLogin={() => goToAuth('login')} />
        )}
        {authView === 'forgot' && <ForgotPassword onBackToLogin={() => goToAuth('login')} />}
        {authView === 'reset' && (
          <ResetPassword
            onGoToLogin={() => goToAuth('login')}
            onRequestNewLink={() => goToAuth('forgot')}
            // As sessões já foram revogadas no backend; descarta o token local também.
            onPasswordChanged={auth.logout}
          />
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
        {view === 'settings' && <Settings />}
      </div>
    </CurrencyProvider>
  )
}
