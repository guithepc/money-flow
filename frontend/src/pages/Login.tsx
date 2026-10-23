import { useState, type FormEvent } from 'react'
import type { UseAuth } from '@/hooks/useAuth'
import { AuthError, AuthShell, AuthSubmit, authInput, authLabel } from '@/components/AuthShell'

interface LoginProps {
  auth: UseAuth
  onSwitchToRegister: () => void
  onForgotPassword: () => void
}

export function Login({ auth, onSwitchToRegister, onForgotPassword }: LoginProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    void auth.login(email, password)
  }

  return (
    <AuthShell eyebrow="Money Flow" title="Entrar" subtitle="Acesse suas contas e transações.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {auth.error && <AuthError message={auth.error} />}

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
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={authInput}
            placeholder="••••••••"
          />
        </label>

        {/* Fora do <label> para não entrar no nome acessível do campo de senha. */}
        <button
          type="button"
          onClick={onForgotPassword}
          className="-mt-2 self-end text-[13px] font-semibold text-indigo underline-offset-4 hover:underline"
        >
          Esqueci minha senha
        </button>

        <AuthSubmit disabled={auth.loading}>{auth.loading ? 'Entrando…' : 'Entrar'}</AuthSubmit>

        <p className="text-sm text-ink/65">
          Não tem conta?{' '}
          <button
            type="button"
            onClick={onSwitchToRegister}
            className="font-semibold text-indigo underline-offset-4 hover:underline"
          >
            Criar conta
          </button>
        </p>
      </form>
    </AuthShell>
  )
}
