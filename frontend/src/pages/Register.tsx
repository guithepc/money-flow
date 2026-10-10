import { useState, type FormEvent } from 'react'
import type { UseAuth } from '@/hooks/useAuth'
import { AuthError, AuthShell, AuthSubmit, authInput, authLabel } from '@/components/AuthShell'

interface RegisterProps {
  auth: UseAuth
  onSwitchToLogin: () => void
}

export function Register({ auth, onSwitchToLogin }: RegisterProps) {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    void auth.register(name, email, password)
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
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={authInput}
            placeholder="••••••••"
          />
        </label>

        <AuthSubmit disabled={auth.loading}>{auth.loading ? 'Criando…' : 'Criar conta'}</AuthSubmit>

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
