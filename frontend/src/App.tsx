import { useEffect, useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'
import { Accounts } from '@/pages/Accounts'
import { Settings } from '@/pages/Settings'
import { Admin } from '@/pages/Admin'
import { Login } from '@/pages/Login'
import { Register } from '@/pages/Register'
import { ForgotPassword } from '@/pages/ForgotPassword'
import { ResetPassword } from '@/pages/ResetPassword'
import { Legal } from '@/pages/Legal'
import { AcceptTerms } from '@/pages/AcceptTerms'
import { useAuth } from '@/hooks/useAuth'
import { CurrencyProvider } from '@/contexts/CurrencyContext'
import { ownerApi } from '@/lib/api'
import { LEGAL_PATHS } from '@/lib/legal'
import type { View } from '@/lib/types'

function Placeholder({ title }: { title: string }) {
  return (
    <main className="bg-page-grid flex flex-1 flex-col items-center justify-center gap-2 px-10 py-9">
      <p className="text-sm text-fg-muted">{title} — em breve.</p>
      <p className="font-mono text-xs text-fg-subtle">Essa seção ainda está sendo construída.</p>
    </main>
  )
}

/** Painel do usuário logado. Antes dele, pede o aceite dos termos se a versão atual ainda não foi aceita. */
function AuthenticatedApp({ onLogout }: { onLogout: () => void }) {
  const [view, setView] = useState<View>('dashboard')
  // null = verificando. Se a consulta falhar, não bloqueia o painel (o backend não depende do aceite).
  const [termsAccepted, setTermsAccepted] = useState<boolean | null>(null)
  const [admin, setAdmin] = useState(false)

  useEffect(() => {
    let active = true
    ownerApi
      .me()
      .then((profile) => {
        if (!active) return
        setTermsAccepted(profile.termsAccepted)
        setAdmin(profile.admin)
      })
      .catch(() => {
        if (active) setTermsAccepted(true)
      })
    return () => {
      active = false
    }
  }, [])

  if (termsAccepted === null) return <div className="flex h-screen bg-background" />

  if (!termsAccepted) {
    return (
      <div className="flex h-screen bg-background text-fg">
        <AcceptTerms onAccepted={() => setTermsAccepted(true)} onLogout={onLogout} />
      </div>
    )
  }

  return (
    <CurrencyProvider>
      <div className="flex h-screen bg-background text-fg">
        <Sidebar active={view} onNavigate={setView} onLogout={onLogout} admin={admin} />
        {view === 'dashboard' && <Dashboard />}
        {view === 'accounts' && <Accounts />}
        {view === 'reports' && <Placeholder title="Relatórios" />}
        {view === 'settings' && <Settings />}
        {view === 'admin' && admin && <Admin />}
      </div>
    </CurrencyProvider>
  )
}

type AuthView = 'login' | 'register' | 'forgot' | 'reset'

/** Único caminho com URL própria: o link que o bot manda (`/reset-password?token=…`). */
const RESET_PATH = '/reset-password'

export default function App() {
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

  // Termos e privacidade são públicos: abrem com ou sem sessão, sem esperar a restauração.
  const path = window.location.pathname.replace(/\/+$/, '')
  if (path === LEGAL_PATHS.terms) return <Legal kind="terms" />
  if (path === LEGAL_PATHS.privacy) return <Legal kind="privacy" />

  // Evita piscar a tela de login enquanto o cookie de refresh restaura a sessão.
  if (auth.restoring) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <p className="font-mono text-xs text-fg-subtle">Carregando…</p>
      </div>
    )
  }

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

  return <AuthenticatedApp onLogout={auth.logout} />
}
