import { useEffect, useState } from 'react'
import { Wallet } from 'lucide-react'
import { AccountForm } from '@/components/AccountForm'
import { AuthError, AuthShell, AuthSubmit } from '@/components/AuthShell'
import { Capture } from '@/pages/Capture'
import { accountsApi, onboardingApi } from '@/lib/api'
import { useLanguage, type Lang } from '@/lib/i18n'
import type { Account, OnboardingStatus } from '@/lib/types'
import { cn, formatCurrency } from '@/lib/utils'

const UI = {
  pt: {
    step: (n: number) => `Passo ${n} de 3`,
    languageTitle: 'Bem-vindo ao Money Flow',
    languageSubtitle: 'Em qual idioma você prefere usar?',
    accountsTitle: 'Suas contas',
    accountsSubtitle: 'Onde está o seu dinheiro hoje? Adicione pelo menos uma conta com o saldo atual.',
    continue: 'Continuar',
    categoriesTitle: 'Suas categorias',
    categoriesSubtitle: 'Criei estas para você. Não precisa falar o nome: eu escolho pela frase.',
    expenses: 'Gastos',
    income: 'Entradas',
    firstTransaction: 'Registrar meu primeiro gasto',
    failed: 'Algo deu errado. Tente de novo.',
  },
  en: {
    step: (n: number) => `Step ${n} of 3`,
    languageTitle: 'Welcome to Money Flow',
    languageSubtitle: 'Which language do you prefer?',
    accountsTitle: 'Your accounts',
    accountsSubtitle: 'Where is your money today? Add at least one account with its current balance.',
    continue: 'Continue',
    categoriesTitle: 'Your categories',
    categoriesSubtitle: "I created these for you. No need to say the name: I pick it from the sentence.",
    expenses: 'Expenses',
    income: 'Income',
    firstTransaction: 'Register my first expense',
    failed: 'Something went wrong. Try again.',
  },
} as const

const LANGUAGES: { code: Lang; flag: string; label: string }[] = [
  { code: 'pt', flag: '🇧🇷', label: 'Português' },
  { code: 'en', flag: '🇬🇧', label: 'English' },
]

/**
 * Primeiro acesso pelo PWA, no mesmo estado do onboarding do bot: idioma → contas → categorias → primeiro gasto.
 * Retoma de onde a pessoa parou (aqui ou no Telegram).
 */
export function Onboarding({ initial, onDone }: { initial: OnboardingStatus; onDone: () => void }) {
  const { lang, setLang } = useLanguage()
  const ui = UI[lang]
  const [status, setStatus] = useState(initial)
  const [accounts, setAccounts] = useState<Account[]>([])
  const [capturing, setCapturing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (status.step === 'ACCOUNTS') accountsApi.getAll().then(setAccounts).catch(() => {})
  }, [status.step])

  async function run(call: () => Promise<OnboardingStatus>) {
    setBusy(true)
    setError(null)
    try {
      setStatus(await call())
    } catch {
      setError(ui.failed)
    } finally {
      setBusy(false)
    }
  }

  function chooseLanguage(code: Lang) {
    setLang(code)
    void run(() => onboardingApi.chooseLanguage(code))
  }

  if (status.step === 'FIRST_TRANSACTION' && capturing) {
    return (
      <Capture
        onOpenDashboard={onDone}
        onboarding={{ onFinished: onDone, onSkip: () => void onboardingApi.finish().then(onDone).catch(() => onDone()) }}
      />
    )
  }

  if (status.step === 'LANGUAGE') {
    return (
      <AuthShell eyebrow={ui.step(1)} title={ui.languageTitle} subtitle={ui.languageSubtitle} lang={lang}>
        <div className="flex flex-col gap-3">
          {LANGUAGES.map(({ code, flag, label }) => (
            <button
              key={code}
              type="button"
              disabled={busy}
              onClick={() => chooseLanguage(code)}
              className={cn(
                'flex items-center gap-4 border px-5 py-4 text-left text-base font-semibold transition-colors duration-150 disabled:opacity-60',
                lang === code ? 'border-ink bg-white text-ink' : 'border-ink/15 bg-white/60 text-ink/80 hover:border-ink/50',
              )}
            >
              <span className="text-2xl" aria-hidden>
                {flag}
              </span>
              {label}
            </button>
          ))}
          {error && <AuthError message={error} />}
        </div>
      </AuthShell>
    )
  }

  if (status.step === 'ACCOUNTS') {
    return (
      <AuthShell eyebrow={ui.step(2)} title={ui.accountsTitle} subtitle={ui.accountsSubtitle} lang={lang}>
        <div className="flex flex-col gap-5">
          {accounts.length > 0 && (
            <ul className="flex flex-col border border-ink/12 bg-white">
              {accounts.map((account) => (
                <li key={account.accountId} className="flex items-center gap-3 border-b border-ink/8 px-4 py-3 last:border-b-0">
                  <Wallet className="size-4 text-ink/50" strokeWidth={1.8} />
                  <span className="flex-1 text-sm font-semibold text-ink">{account.description}</span>
                  <span className="font-mono text-xs text-ink/60">{formatCurrency(account.amount, account.currency)}</span>
                </li>
              ))}
            </ul>
          )}
          <AccountForm onCreated={(account) => setAccounts((current) => [...current, account])} />
          {error && <AuthError message={error} />}
          <AuthSubmit disabled={busy || accounts.length === 0} onClick={() => void run(onboardingApi.accountsDone)}>
            {ui.continue}
          </AuthSubmit>
        </div>
      </AuthShell>
    )
  }

  const expenses = status.categories.filter((c) => c.type === 'EXPENSE')
  const income = status.categories.filter((c) => c.type === 'INCOME')
  return (
    <AuthShell eyebrow={ui.step(3)} title={ui.categoriesTitle} subtitle={ui.categoriesSubtitle} lang={lang}>
      <div className="flex flex-col gap-5">
        {[
          { label: ui.expenses, items: expenses },
          { label: ui.income, items: income },
        ].map(({ label, items }) => (
          <div key={label} className="flex flex-col gap-2">
            <span className="font-mono text-[11px] uppercase tracking-[0.07em] text-ink/50">{label}</span>
            <div className="flex flex-wrap gap-2">
              {items.map((c) => (
                <span key={c.name} className="border border-ink/15 bg-white px-3 py-1.5 text-sm text-ink">
                  {c.name}
                </span>
              ))}
            </div>
          </div>
        ))}
        <AuthSubmit onClick={() => setCapturing(true)}>{ui.firstTransaction}</AuthSubmit>
      </div>
    </AuthShell>
  )
}
