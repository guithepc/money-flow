import { useState, type FormEvent } from 'react'
import type { UseAuth } from '@/hooks/useAuth'
import { AuthError, AuthShell, AuthSubmit, TermsCheckbox, authInput, authLabel } from '@/components/AuthShell'
import { TERMS_VERSION } from '@/lib/legal'
import { PASSWORD_MIN_LENGTH } from '@/lib/password'

interface RegisterProps {
  auth: UseAuth
  onSwitchToLogin: () => void
}

export function Register({ auth, onSwitchToLogin }: RegisterProps) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [termsAccepted, setTermsAccepted] = useState(false)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    // O checkbox é `required`; a checagem aqui só garante que nunca sai cadastro sem aceite.
    if (!termsAccepted) return
    void auth.register(name, email, password, TERMS_VERSION)
  }

  return (
    <AuthShell eyebrow="Money Flow" title="Criar conta" subtitle="Comece a organizar suas finanças.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {auth.error && <AuthError message={auth.error} />}

        <label className="flex flex-col gap-2">
          <span className={authLabel}>Nome</span>
          <input
            type="text"
            required
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className={authInput}
            placeholder="Seu nome"
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className={authLabel}>Email</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={authInput}
            placeholder="voce@email.com"
          />
        </label>

        <label className="flex flex-col gap-2">
          <span className={authLabel}>Senha</span>
          <input
            type="password"
            required
            minLength={PASSWORD_MIN_LENGTH}
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={authInput}
            placeholder="••••••••"
          />
          <span className="text-xs text-ink/50">Mínimo de 8 caracteres</span>
        </label>

        <TermsCheckbox checked={termsAccepted} onChange={setTermsAccepted} />

        <AuthSubmit disabled={auth.loading || !termsAccepted}>
          {auth.loading ? 'Criando…' : 'Criar conta'}
        </AuthSubmit>

        <p className="text-sm text-ink/65">
          Já tem conta?{' '}
          <button
            type="button"
            onClick={onSwitchToLogin}
            className="font-semibold text-indigo underline-offset-4 hover:underline"
          >
            Entrar
          </button>
        </p>
      </form>
    </AuthShell>
  )
}
