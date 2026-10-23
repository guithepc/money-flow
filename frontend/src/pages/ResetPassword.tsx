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
import { PASSWORD_MIN_LENGTH, PASSWORD_RULE_MESSAGE, validatePassword } from '@/lib/password'

const INVALID_LINK = 'Link inválido. Peça um novo.'

/** Erros de token: o link não serve mais, então o formulário some e oferecemos pedir outro. */
const TOKEN_ERRORS: Record<string, string> = {
  TOKEN_INVALID: INVALID_LINK,
  TOKEN_EXPIRED: 'Este link expirou. Peça um novo.',
  TOKEN_USED: 'Este link já foi usado. Peça um novo se precisar.',
}

interface ResetPasswordProps {
  onGoToLogin: () => void
  onRequestNewLink: () => void
  /** Chamado após o 204 — o backend já revogou as sessões; o app descarta o token local. */
  onPasswordChanged: () => void
}

/** Tela aberta pelo link do bot: `/reset-password?token=…`. */
export function ResetPassword({ onGoToLogin, onRequestNewLink, onPasswordChanged }: ResetPasswordProps) {
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token'))
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tokenError, setTokenError] = useState<string | null>(token ? null : INVALID_LINK)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!token) return
    if (password !== confirmation) {
      setError('As senhas não coincidem.')
      return
    }
    const weak = validatePassword(password)
    if (weak) {
      setError(weak)
      return
    }

    setLoading(true)
    setError(null)
    try {
      await authApi.confirmPasswordReset(token, password)
      setDone(true)
      onPasswordChanged()
    } catch (err) {
      const code = err instanceof ApiError ? err.code : undefined
      if (code && Object.hasOwn(TOKEN_ERRORS, code)) {
        setTokenError(TOKEN_ERRORS[code])
      } else if (code === 'WEAK_PASSWORD') {
        setError(PASSWORD_RULE_MESSAGE)
      } else {
        setError('Não foi possível alterar a senha agora. Tente de novo em instantes.')
      }
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <AuthShell eyebrow="Money Flow" title="Nova senha" subtitle="Tudo certo por aqui.">
        <div className="flex flex-col gap-4">
          <AuthNotice>Senha alterada. Entre com a nova senha.</AuthNotice>
          <AuthSubmit onClick={onGoToLogin}>Ir para o login</AuthSubmit>
        </div>
      </AuthShell>
    )
  }

  if (tokenError) {
    return (
      <AuthShell eyebrow="Money Flow" title="Nova senha" subtitle="Não deu para usar este link.">
        <div className="flex flex-col gap-4">
          <AuthError message={tokenError} />
          <AuthSubmit onClick={onRequestNewLink}>Pedir novo link</AuthSubmit>
          <p className="text-sm text-ink/65">
            <button type="button" onClick={onGoToLogin} className={authLink}>
              Voltar para o login
            </button>
          </p>
        </div>
      </AuthShell>
    )
  }

  return (
    <AuthShell eyebrow="Money Flow" title="Nova senha" subtitle="Escolha uma nova senha para sua conta.">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {error && <AuthError message={error} />}

        <label className="flex flex-col gap-2">
          <span className={authLabel}>Nova senha</span>
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

        <label className="flex flex-col gap-2">
          <span className={authLabel}>Confirme a nova senha</span>
          <input
            type="password"
            required
            minLength={PASSWORD_MIN_LENGTH}
            autoComplete="new-password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            className={authInput}
            placeholder="••••••••"
          />
        </label>

        <AuthSubmit disabled={loading}>{loading ? 'Salvando…' : 'Salvar nova senha'}</AuthSubmit>

        <p className="text-sm text-ink/65">
          <button type="button" onClick={onGoToLogin} className={authLink}>
            Voltar para o login
          </button>
        </p>
      </form>
    </AuthShell>
  )
}
