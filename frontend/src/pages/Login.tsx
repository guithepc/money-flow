import { useState, type FormEvent } from 'react'
import { LogIn, Sparkles } from 'lucide-react'
import type { UseAuth } from '@/hooks/useAuth'

interface LoginProps {
  auth: UseAuth
}

export function Login({ auth }: LoginProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    void auth.login(email, password)
  }

  return (
    <main className="flex flex-1 items-center justify-center px-8 py-8">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <div className="flex size-12 items-center justify-center rounded-xl bg-brand-soft">
            <Sparkles className="size-6 text-brand" strokeWidth={2.2} />
          </div>
          <span className="eyebrow text-brand">Money Flow</span>
          <h1 className="font-serif text-4xl font-semibold tracking-tight text-fg">
            Entrar
          </h1>
          <p className="text-sm text-fg-muted">
            Acesse suas contas e transações.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="flex flex-col gap-4 rounded-2xl border border-edge bg-surface p-6"
        >
          {auth.error && (
            <div className="rounded-xl border border-negative/40 bg-negative/10 px-4 py-3 text-sm text-negative">
              {auth.error}
            </div>
          )}

          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-fg-muted">Email</span>
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="rounded-xl border border-edge bg-surface-raised px-3 py-2 text-sm text-fg outline-none focus:border-brand"
              placeholder="voce@email.com"
            />
          </label>

          <label className="flex flex-col gap-2">
            <span className="text-sm font-medium text-fg-muted">Senha</span>
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="rounded-xl border border-edge bg-surface-raised px-3 py-2 text-sm text-fg outline-none focus:border-brand"
              placeholder="••••••••"
            />
          </label>

          <button
            type="submit"
            disabled={auth.loading}
            className="mt-2 flex items-center justify-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-sm font-medium text-background transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            <LogIn className="size-4" strokeWidth={2} />
            {auth.loading ? 'Entrando…' : 'Entrar'}
          </button>
        </form>
      </div>
    </main>
  )
}
