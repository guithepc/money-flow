import { useEffect, useState } from 'react'
import { Sidebar } from '@/components/Sidebar'
import { Dashboard } from '@/pages/Dashboard'
import { Capture } from '@/pages/Capture'
import { Onboarding } from '@/pages/Onboarding'
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
import { onboardingApi, ownerApi } from '@/lib/api'
import { LanguageProvider } from '@/components/LanguageProvider'
import { browserLang, toLang, type Lang } from '@/lib/i18n'
import { LEGAL_PATHS } from '@/lib/legal'
import type { OnboardingStatus, View } from '@/lib/types'
import { cn } from '@/lib/utils'

function Placeholder({ title }: { title: string }) {
  return (
    <main className="bg-page-grid flex flex-1 flex-col items-center justify-center gap-2 px-10 py-9">
      <p className="text-sm text-fg-muted">{title} — em breve.</p>
      <p className="font-mono text-xs text-fg-subtle">Essa seção ainda está sendo construída.</p>
    </main>
  )
}

/** Tela de gravar: é o `start_url` do PWA salvo na tela de início do iPhone. */
const CAPTURE_PATH = '/registrar'

/** Painel do usuário logado. Antes dele, pede o aceite dos termos se a versão atual ainda não foi aceita. */
function AuthenticatedApp({ onLogout }: { onLogout: () => void }) {
  const [view, setViewState] = useState<View>(() =>
    window.location.pathname.replace(/\/+$/, '') === CAPTURE_PATH ? 'capture' : 'dashboard',
  )

  // Só a tela de gravar tem endereço próprio: recarregar (ou abrir o ícone do PWA) volta pra ela.
  function setView(next: View) {
    const path = next === 'capture' ? CAPTURE_PATH : '/'
    if (window.location.pathname !== path) window.history.replaceState(null, '', path)
    setViewState(next)
  }
  // null = verificando. Se a consulta falhar, não bloqueia o painel (o backend não depende do aceite).
  const [termsAccepted, setTermsAccepted] = useState<boolean | null>(null)
  const [admin, setAdmin] = useState(false)
  const [language, setLanguage] = useState<Lang>(browserLang)
  // null = verificando. Falha na consulta não prende ninguém no onboarding.
  const [onboarding, setOnboarding] = useState<OnboardingStatus | null>(null)

  useEffect(() => {
    let active = true
    // Idioma das telas traduzidas: o escolhido no onboarding (bot ou PWA); antes disso, o do navegador.
    onboardingApi
      .status()
      .then((status) => {
        if (!active) return
        setLanguage(toLang(status.language) ?? browserLang())
        setOnboarding(status)
      })
      .catch(() => {
        if (active) setOnboarding({ step: 'DONE', language: null, accounts: 0, categories: [] })
      })
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

  if (termsAccepted === null || (termsAccepted && onboarding === null)) return <div className="flex h-screen bg-background" />

  if (!termsAccepted) {
    return (
      <div className="flex h-screen bg-background text-fg">
        <AcceptTerms onAccepted={() => setTermsAccepted(true)} onLogout={onLogout} />
      </div>
    )
  }

  const onboardingPending = onboarding !== null && onboarding.step !== 'DONE'

  // Um provider só para o onboarding e o app: o idioma escolhido no onboarding segue valendo depois.
  return (
    <LanguageProvider initial={language}>
      {onboardingPending ? (
        <div className="flex h-dvh bg-background text-fg">
          <Onboarding
            initial={onboarding}
            onDone={() => {
              setOnboarding({ ...onboarding, step: 'DONE' })
              setView('capture')
            }}
          />
        </div>
      ) : (
        <CurrencyProvider>
          <div className="flex h-dvh bg-background text-fg">
            {/* No celular a tela de gravar ocupa tudo; o botão "Painel" dela leva ao resto. */}
            <div className={cn('shrink-0', view === 'capture' ? 'hidden md:flex' : 'flex')}>
              <Sidebar active={view} onNavigate={setView} onLogout={onLogout} admin={admin} />
            </div>
            {view === 'capture' && <Capture onOpenDashboard={() => setView('dashboard')} />}
            {view === 'dashboard' && <Dashboard />}
            {view === 'accounts' && <Accounts />}
            {view === 'reports' && <Placeholder title="Relatórios" />}
            {view === 'settings' && <Settings />}
            {view === 'admin' && admin && <Admin />}
          </div>
        </CurrencyProvider>
      )}
    </LanguageProvider>
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
