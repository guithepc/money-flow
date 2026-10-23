import { useState, type FormEvent } from 'react'
import {
  AuthError,
  AuthNotice,
  AuthShell,
  AuthSubmit,
  authInput,
  authLabel,
  authLink,
} from '@/components/AuthShell'
import { ApiError, authApi } from '@/lib/api'

// `||` (e não `??`): um ARG vazio no build do Docker vira string vazia.
const SUPPORT_EMAIL = import.meta.env.VITE_SUPPORT_EMAIL || 'guilhermepcstore@gmail.com'

interface ForgotPasswordProps {
  onBackToLogin: () => void
}

/** Pede o link de redefinição — entregue pelo bot do Telegram, não por email. */
export function ForgotPassword({ onBackToLogin }: ForgotPasswordProps) {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    setSent(false)
    try {
      await authApi.requestPasswordReset(email)
      // O backend responde igual exista ou não a conta — a mensagem também é sempre a mesma.
      setSent(true)
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 429
          ? 'Muitas tentativas. Tente de novo em uma hora.'
          : 'Não foi possível enviar agora. Tente de novo em instantes.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Money Flow"
      title="Esqueci a senha"
      subtitle="Enviamos um link de redefinição pelo bot do Telegram."
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <AuthError message={error} />}
        {sent && (
          <AuthNotice>
            Se a conta existir e tiver o Telegram vinculado, enviamos o link de redefinição pelo
            bot. Ele vale por 30 minutos.
          </AuthNotice>
        )}

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

        <AuthSubmit disabled={loading}>{loading ? 'Enviando…' : 'Enviar link'}</AuthSubmit>

        {/* Sempre visível: o backend nunca revela se a conta tem Telegram vinculado. */}
        <p className="text-sm text-ink/65">
          Não vinculou o Telegram? Fale com o suporte em{' '}
          <a href={`mailto:${SUPPORT_EMAIL}`} className={authLink}>
            {SUPPORT_EMAIL}
          </a>
        </p>

        <p className="text-sm text-ink/65">
          Lembrou a senha?{' '}
          <button type="button" onClick={onBackToLogin} className={authLink}>
            Voltar para o login
          </button>
        </p>
      </form>
    </AuthShell>
  )
}
